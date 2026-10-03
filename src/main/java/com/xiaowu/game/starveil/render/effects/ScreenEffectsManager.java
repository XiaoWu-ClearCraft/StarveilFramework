package com.xiaowu.game.starveil.render.effects;

import com.sun.jna.platform.WindowUtils;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.config.LauncherConfig;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.platform.api.PlatformInjectPoints;
import com.xiaowu.game.starveil.platform.common.SystemDetector;
import com.xiaowu.game.starveil.platform.windows.Win32ScreenCapture;
import com.xiaowu.game.starveil.platform.windows.WinOpsManager;
import com.xiaowu.game.starveil.plugin.InjectPoint;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import javax.swing.SwingUtilities;
import java.util.HashSet;
import java.util.Set;

/**
 * 屏幕特效管理器 - 管理全屏透明覆盖层，并提供故障、伪蓝屏、终端文本等特效的调用入口。
 *
 * <p>本类取代原先的 DebugOverlay，作为主游戏特效系统的对外门面。
 * 各特效的渲染逻辑被拆分到 {@link GlitchEffect}、{@link BlueScreenEffect}、
 * {@link TerminalOverlay} 中，本类负责覆盖层窗口的生命周期与键盘拦截。
 *
 * <p>另外提供两项「只改遮罩层内容」的能力（不动系统设置）：
 * <ul>
 *   <li>{@link #setFrameThrottle(String)} —— 把下层画面捕获到遮罩层并按目标帧率刷新，
 *       从而压低可见帧率（只允许降低，不允许提升）；</li>
 *   <li>基于同一张捕获画面的像素级花屏，见 {@link #glitch(double)}。</li>
 * </ul>
 */
public class ScreenEffectsManager {

    private static ScreenEffectsManager instance;

    public static ScreenEffectsManager getInstance() {
        if (instance == null) instance = new ScreenEffectsManager();
        return instance;
    }

    // ==================== JavaFX ====================

    /** 遮罩层窗口标题：Win32 侧靠它反查 HWND（见 {@code WinOpsManager.getStageHandle}）。 */
    private static final String OVERLAY_WINDOW_TITLE = "StarveilScreenEffects";

    /**
     * 整屏抓取的最长边上限（可用 {@code -Dstarveil.overlay.captureMaxDim=...} 覆盖）。
     *
     * <p>抓屏是「源分辨率 → 输出分辨率」的缩放：输出小于源就会被放大回去，
     * 画面会变糊（尤其是文字）。所以默认给得比较宽松——2560 及以下的屏幕（含
     * 1080p / 1440p）都是原分辨率抓取，只有 4K 才会缩放。
     *
     * <p>用 {@code CreateDIBSection} 之后单帧开销主要在 BitBlt 回读，
     * 2560x1600 大约 10-18ms，30fps 目标下还有余量。如果你的机器吃不消，
     * 调小这个值即可（画面会软一些）。
     */
    private static final int CAPTURE_MAX_DIMENSION = Integer.getInteger(
            "starveil.overlay.captureMaxDim", 2560);

    private Stage stage;
    /** 覆盖层所依附的游戏主窗口，用于 initOwner（拥有者窗口不会出现在任务栏）与画面捕获。 */
    private Stage ownerStage;
    private volatile Pane root;
    private volatile boolean active = false;
    private boolean showQueued = false;
    private volatile boolean pendingBlueScreen = false;

    private final GlitchEffect glitchEffect = new GlitchEffect();
    private final BlueScreenEffect blueScreenEffect = new BlueScreenEffect();
    private final TerminalOverlay terminalOverlay = new TerminalOverlay();
    /** 捕获帧层：降帧与像素级花屏共用的一张下层画面。 */
    private final CapturedFrameLayer capturedFrame = new CapturedFrameLayer();

    private AnimationTimer frameTimer;

    /** 花屏保持到该时刻（nanoTime）；到点后自动恢复实时画面。 */
    private volatile long glitchHoldUntilNanos;
    /** 一次花屏在没有后续调用时保持多久。 */
    private static final long GLITCH_HOLD_MILLIS = 130;
    /** 覆盖层尚未显示时暂存的降帧参数。 */
    private volatile String pendingThrottleSpec;
    /** 遮罩层是否已成功从屏幕捕获中排除（整屏抓取的前提）。 */
    private volatile boolean captureExcluded;
    /** 「无法排除捕获」的警告只打一次，避免每 100ms 刷屏。 */
    private volatile boolean captureExclusionWarned;
    /** 当前是否已给遮罩层加了鼠标穿透样式。 */
    private boolean clickThroughApplied;
    /** 当前是否已隐藏真实光标。 */
    private boolean cursorHiddenApplied;
    /** 「抓屏太贵」只提醒一次。 */
    private boolean captureCostWarned;

    private final Set<KeyCode> blockedKeys = new HashSet<>();
    private volatile boolean blockAllKeys = false;

    // ==================== 构造 ====================

    private ScreenEffectsManager() {}

    // ==================== 生命周期 ====================

    /**
     * 随游戏启动自动显示遮罩层。
     *
     * <p>是否显示由配置决定：{@code DataManager} 里
     * {@link GameConstants#SETTING_OVERLAY_ENABLED}（{@code overlay.enabled}）
     * 显式设为 {@code false} 时才跳过，默认开启。
     *
     * <p>重复调用是安全的。
     *
     * @param owner 游戏主窗口
     * @return 是否已显示（或已经处于显示状态）
     */
    public boolean startWithGame(Stage owner) {
        if (!isEnabledByConfig()) {
            LoggerManager.Logger("INFO", "[ScreenEffects] 遮罩层已按配置关闭（"
                    + GameConstants.SETTING_OVERLAY_ENABLED + "=false），跳过创建");
            return false;
        }
        show(owner);
        return true;
    }

    /** 配置是否允许创建遮罩层（默认允许）。 */
    public static boolean isEnabledByConfig() {
        return DataManager.getBoolean(GameConstants.SETTING_OVERLAY_ENABLED, true);
    }

    /**
     * 在游戏主窗口所在屏幕显示覆盖层
     * @param owner 主窗口
     */
    public void show(Stage owner) {
        if (active || showQueued) return;
        showQueued = true;
        Platform.runLater(() -> {
            showQueued = false;
            try {
                this.ownerStage = owner;
                captureExcluded = false;
                captureExclusionWarned = false;

                stage = new Stage(StageStyle.TRANSPARENT);
                // 给遮罩层一个独一无二的标题：Win32 侧靠它稳定地反查窗口句柄
                // （JavaFX 25 的 glass 已经拿不到 Stage ↔ HWND 的对应关系了）
                stage.setTitle(OVERLAY_WINDOW_TITLE);
                stage.setAlwaysOnTop(true);
                // 让覆盖层成为主窗口的「拥有者窗口」：
                // Windows 下拥有者窗口不会出现在任务栏，这是最可靠的一道保险。
                if (owner != null) {
                    try {
                        stage.initOwner(owner);
                    } catch (Exception e) {
                        LoggerManager.Logger("DEBUG", "覆盖层 initOwner 失败（忽略）: " + e.getMessage());
                    }
                }

                root = new Pane();
                root.setMouseTransparent(true);
                root.setStyle("-fx-background-color: transparent;");

                Scene scene = new Scene(root);
                scene.setFill(null);

                scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (blockAllKeys || (!blockedKeys.isEmpty() && blockedKeys.contains(e.getCode()))) {
                        e.consume();
                    }
                    if (blueScreenEffect.isBlueScreenActive() && e.getCode() == KeyCode.F12) {
                        dismissBlueScreen();
                    }
                });

                stage.setScene(scene);

                javafx.stage.Screen targetScreen = getScreenForStage(owner);
                stage.setX(targetScreen.getBounds().getMinX());
                stage.setY(targetScreen.getBounds().getMinY());
                stage.setWidth(targetScreen.getBounds().getWidth());
                stage.setHeight(targetScreen.getBounds().getHeight());

                stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
                stage.setFullScreen(true);
                stage.show();
                active = true;
                LoggerManager.Logger("INFO", "[ScreenEffects] 遮罩层已显示: "
                        + (int) targetScreen.getBounds().getWidth() + "x"
                        + (int) targetScreen.getBounds().getHeight()
                        + " @" + (int) targetScreen.getBounds().getMinX()
                        + "," + (int) targetScreen.getBounds().getMinY());

                // 捕获源 = 游戏场景根节点（不含覆盖层自身，避免自我捕获出现回授）
                capturedFrame.setSource(resolveCaptureSource());
                updateCaptureViewport();
                capturedFrame.reassert(root);

                applyPlatformWindowSetup();
                startFrameTimer();

                if (pendingBlueScreen) {
                    pendingBlueScreen = false;
                    showBlueScreen();
                }

                // 覆盖层显示前就设置过的降帧，现在补上
                String pending = pendingThrottleSpec;
                if (pending != null) {
                    setFrameThrottle(pending);
                }

                maybeRunSelfTest();
            } catch (Exception e) {
                LoggerManager.Logger("ERROR", "屏幕特效覆盖层创建失败: " + e.getMessage());
                showQueued = false;
            }
        });
    }

    /**
     * 显示覆盖层（使用主显示器）
     */
    public void show() {
        show(null);
    }

    /**
     * 隐藏覆盖层
     */
    public void hide() {
        if (!active) return;
        active = false;
        blockedKeys.clear();
        blockAllKeys = false;
        Platform.runLater(() -> {
            stopFrameTimer();
            // 关掉遮罩层前务必恢复光标，否则用户会以为鼠标坏了
            if (cursorHiddenApplied) {
                cursorHiddenApplied = false;
                Win32ScreenCapture.setCursorVisible(true);
            }
            capturedFrame.detach(root);
            capturedFrame.clear();
            capturedFrame.releaseCaptureStrategy();
            if (stage != null) {
                restoreCaptureVisibility(stage);
                glitchEffect.clear(root);
                terminalOverlay.clear(root);
                blueScreenEffect.reset();
                stage.close();
                stage = null;
                root = null;
            }
            ownerStage = null;
            captureExcluded = false;
            captureExclusionWarned = false;
        });
    }

    /** 关闭时把「从捕获中排除」的标记撤掉，避免影响窗口句柄的后续使用。 */
    private void restoreCaptureVisibility(Stage current) {
        if (!SystemDetector.isWindows() || !captureExcluded) {
            return;
        }
        try {
            var hwnd = WinOpsManager.getStageHandle(current);
            if (hwnd != null) {
                Win32ScreenCapture.includeInCapture(hwnd);
            }
        } catch (Throwable ignored) {
        }
        captureExcluded = false;
    }

    public boolean isActive() {
        return active;
    }

    // ==================== 捕获帧 / 降帧保持 ====================

    /**
     * 覆盖层要捕获的节点 = 游戏场景根节点。
     *
     * <p>只有在外部队面来源（Windows 整屏抓取）不可用时才会用到它。
     */
    private Node resolveCaptureSource() {
        Stage owner = ownerStage;
        if (owner == null || owner.getScene() == null) {
            return null;
        }
        return owner.getScene().getRoot();
    }

    /**
     * 让捕获图正好盖住遮罩层。
     *
     * <p>两种来源的贴图方式不同：
     * <ul>
     *   <li>整屏抓取（Windows）：抓的就是遮罩层覆盖的那块屏幕，直接铺满遮罩层；</li>
     *   <li>节点快照（兜底）：抓的是游戏窗口，按窗口在屏幕上的位置摆放，
     *       否则窗口化运行时画面会被拉伸到整屏。</li>
     * </ul>
     */
    private void updateCaptureViewport() {
        Stage owner = ownerStage;
        if (owner == null || stage == null) {
            return;
        }
        Pane layer = root;
        if (capturedFrame.getCaptureStrategy() != null) {
            double lw = layer != null ? layer.getWidth() : 0;
            double lh = layer != null ? layer.getHeight() : 0;
            if (lw <= 0 || lh <= 0) {
                // 遮罩层还没完成布局：先按捕获尺寸铺满，否则 ImageView 的 fit 尺寸是 0，
                // 图设进去了却什么都看不到
                lw = capturedFrame.getCaptureStrategy().width();
                lh = capturedFrame.getCaptureStrategy().height();
            }
            capturedFrame.setViewport(0, 0, lw, lh);
            return;
        }
        javafx.stage.Screen screen = getScreenForStage(owner);
        double originX = screen.getBounds().getMinX();
        double originY = screen.getBounds().getMinY();
        double x = owner.getX() - originX;
        double y = owner.getY() - originY;
        double w = owner.getWidth() > 0 ? owner.getWidth() : screen.getBounds().getWidth();
        double h = owner.getHeight() > 0 ? owner.getHeight() : screen.getBounds().getHeight();
        capturedFrame.setViewport(x, y, w, h);
    }

    // ==================== 整屏抓取（Windows） ====================

    /**
     * 安装「抓整个屏幕」的画面来源。
     *
     * <p>遮罩层覆盖整屏，所以它下面不止是游戏窗口，还有任务栏和后面的其它窗口；
     * 用 GDI BitBlt 抓屏幕才是真正意义上的「遮罩层下方画面」。
     *
     * <p>前提是先把遮罩层从捕获里排除掉（{@code WDA_EXCLUDEFROMCAPTURE}），
     * 否则会抓到自己贴上去的那张图，形成回授。
     */
    private void ensureDesktopCapture() {
        if (capturedFrame.getCaptureStrategy() != null) {
            return;
        }
        if (!SystemDetector.isWindows() || !Win32ScreenCapture.isAvailable() || !captureExcluded) {
            return;
        }
        javafx.stage.Screen screen = getScreenForStage(ownerStage);
        int[] rect = physicalScreenRect(screen);
        if (rect == null || rect[2] <= 0 || rect[3] <= 0) {
            LoggerManager.Logger("WARNING", "[ScreenEffects] 无法计算屏幕物理像素范围，"
                    + "降帧/花屏将只捕获游戏窗口内容");
            return;
        }

        // 抓全屏 32 位像素很贵：先按上限降采样，遮罩层再放大回去。
        // 对「卡住的降质画面」来说这点模糊反而更自然，也才能在 30fps 目标下跑得动。
        double scale = Math.min(1.0, CAPTURE_MAX_DIMENSION / (double) Math.max(rect[2], rect[3]));
        final int outW = Math.max(1, (int) Math.round(rect[2] * scale));
        final int outH = Math.max(1, (int) Math.round(rect[3] * scale));

        Win32ScreenCapture capture = new Win32ScreenCapture();
        capturedFrame.setCaptureStrategy(new OverlayFrameCapture() {
            @Override
            public int width() {
                return outW;
            }

            @Override
            public int height() {
                return outH;
            }

            @Override
            public boolean captureInto(int[] argb) {
                if (!capture.prepare(outW, outH, rect[0], rect[1], rect[2], rect[3])) {
                    return false;
                }
                return capture.captureInto(argb);
            }

            @Override
            public double lastCaptureMillis() {
                return capture.getLastCaptureMillis();
            }

            @Override
            public void release() {
                capture.release();
            }
        });
        LoggerManager.Logger("INFO", "[ScreenEffects] 已启用整屏抓取（GDI）: 源 "
                + rect[2] + "x" + rect[3] + " @" + rect[0] + "," + rect[1]
                + " -> 输出 " + outW + "x" + outH
                + (outW == rect[2] && outH == rect[3] ? "（原分辨率）" : "（已缩放，画面会略软）"));
    }

    /**
     * 把 JavaFX 的「逻辑屏幕矩形」换算成虚拟桌面的物理像素矩形。
     *
     * <p>用主显示器的 物理宽度 / 逻辑宽度 得到缩放比，再套到目标屏幕上。
     * 单显示器（以及各屏缩放一致的多显示器）下结果是精确的；
     * 混合缩放的多显示器下会有偏差，此时抓到的画面会略偏，但不会崩。
     *
     * @return {@code [x, y, w, h]}；不可用时返回 null
     */
    private int[] physicalScreenRect(javafx.stage.Screen screen) {
        if (screen == null) {
            return null;
        }
        int[] virtual = Win32ScreenCapture.virtualScreenBounds();
        int primaryPhysicalW = Win32ScreenCapture.primaryScreenWidth();
        if (virtual == null || virtual[2] <= 0 || primaryPhysicalW <= 0) {
            return null;
        }
        javafx.geometry.Rectangle2D primaryLogical = javafx.stage.Screen.getPrimary().getBounds();
        if (primaryLogical.getWidth() <= 0) {
            return null;
        }
        double scale = primaryPhysicalW / primaryLogical.getWidth();

        javafx.geometry.Rectangle2D logical = screen.getBounds();
        int x = virtual[0] + (int) Math.round((logical.getMinX() - primaryLogical.getMinX()) * scale);
        int y = virtual[1] + (int) Math.round((logical.getMinY() - primaryLogical.getMinY()) * scale);
        int w = (int) Math.round(logical.getWidth() * scale);
        int h = (int) Math.round(logical.getHeight() * scale);

        // 夹到虚拟桌面范围内，避免越界导致 BitBlt 拿到黑边
        int maxX = virtual[0] + virtual[2];
        int maxY = virtual[1] + virtual[3];
        x = Math.max(virtual[0], Math.min(x, maxX - 1));
        y = Math.max(virtual[1], Math.min(y, maxY - 1));
        w = Math.max(1, Math.min(w, maxX - x));
        h = Math.max(1, Math.min(h, maxY - y));
        return new int[]{x, y, w, h};
    }

    private void startFrameTimer() {
        if (frameTimer != null) {
            return;
        }
        frameTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                Pane layer = root;
                if (layer == null) {
                    return;
                }
                // 窗口可能被拖动/缩放，每次刷新前校正捕获图位置
                if (capturedFrame.isThrottling() || glitchHoldUntilNanos != 0) {
                    updateCaptureViewport();
                }

                // 降帧保持：到点才重新捕获并刷新遮罩层内容
                capturedFrame.tick();
                warnIfCaptureTooExpensive();

                // 花屏保持到期：回到实时画面（或降帧保持的那张图）
                long holdUntil = glitchHoldUntilNanos;
                if (holdUntil != 0 && now >= holdUntil) {
                    glitchHoldUntilNanos = 0;
                    glitchEffect.clear(layer);
                    if (capturedFrame.isThrottling()) {
                        capturedFrame.publish(capturedFrame.getBase());
                    } else {
                        capturedFrame.clear();
                    }
                }

                reconcileOverlayPresentation(layer);
            }
        };
        frameTimer.start();
    }

    /**
     * 遮罩层一旦显示不透明内容（降帧截图 / 花屏），需要同步两件事：
     * <ul>
     *   <li>{@code WS_EX_TRANSPARENT}：把鼠标点击让给下层游戏，否则点不动；</li>
     *   <li>隐藏真实光标：光标由 DWM 合成在所有窗口之上，截图里画了它也盖不住它，
     *       只会变成「冻结的鬼影 + 流畅的真光标」。把真的藏起来之后，屏幕上唯一的
     *       光标就是捕获帧里那个，也就跟着一起降帧了。</li>
     * </ul>
     * 两者都跟着「是否有画面在显示」一起切换。
     */
    private void reconcileOverlayPresentation(Pane layer) {
        if (!SystemDetector.isWindows()) {
            return;
        }
        boolean want = capturedFrame.isShowing() && layer != null;
        Stage current = stage;

        if (want != clickThroughApplied) {
            clickThroughApplied = want;
            if (current != null) {
                WinOpsManager.setClickThrough(current, want);
            }
        }
        if (want != cursorHiddenApplied) {
            cursorHiddenApplied = want;
            boolean supported = capturedFrame.getCaptureStrategy() != null;
            if (supported) {
                Win32ScreenCapture.setCursorVisible(!want);
            }
        }
    }

    private void stopFrameTimer() {
        if (frameTimer != null) {
            frameTimer.stop();
            frameTimer = null;
        }
    }

    /**
     * 设置「降帧保持」：把下层画面捕获到遮罩层，并按目标帧率刷新这张图。
     *
     * <p>只改变遮罩层显示的内容，不修改任何系统 / 驱动设置。
     * <b>只允许降低帧数，不允许提升</b>：若目标帧率不低于游戏实测帧率，
     * 会输出 WARNING 并忽略本次设置。
     *
     * @param spec {@code "50%"}（原帧率的百分比）、{@code "30"} / {@code "10"}（具体帧率）、
     *             或 {@code null} / {@code ""} / {@code "off"}（关闭；调用时输出 WARNING）
     */
    public void setFrameThrottle(String spec) {
        FrameThrottleSpec parsed = FrameThrottleSpec.parse(spec);
        if (!parsed.isEnabled()) {
            LoggerManager.Logger("WARNING", "[ScreenEffects] 忽略降帧设置（" + parsed.problem()
                    + "）。用法: setFrameThrottle(\"50%\") 或 setFrameThrottle(\"30\")；"
                    + "只允许降低帧数，不会提升。");
            clearFrameThrottle();
            return;
        }

        double measured = GameInstance.getCurrentFPS();
        double target = parsed.resolve(measured);
        if (target <= 0) {
            LoggerManager.Logger("WARNING", "[ScreenEffects] 拒绝降帧设置 " + parsed
                    + "：目标帧率不低于当前实测帧率 " + String.format("%.1f", measured)
                    + "，不允许提升帧数，已忽略");
            clearFrameThrottle();
            return;
        }

        // 覆盖层还没显示时先记下来，等它显示后自动生效
        pendingThrottleSpec = spec;
        if (root == null) {
            LoggerManager.Logger("INFO", "[ScreenEffects] 覆盖层尚未激活，降帧设置 " + parsed
                    + " 将在覆盖层显示后生效");
            return;
        }

        Platform.runLater(() -> {
            Pane layer = root;
            if (layer == null) {
                return;
            }
            capturedFrame.setSource(resolveCaptureSource());
            updateCaptureViewport();
            capturedFrame.reassert(layer);
            capturedFrame.setThrottleFps(target);
            if (!capturedFrame.capture()) {
                LoggerManager.Logger("WARNING", "[ScreenEffects] 降帧已设置但首次捕获失败，"
                        + "遮罩层将保持透明");
                return;
            }
            capturedFrame.publish(capturedFrame.getBase());
            LoggerManager.Logger("INFO", "[ScreenEffects] 降帧保持已启用: " + parsed
                    + " -> " + String.format("%.1f", target) + " fps（当前实测 "
                    + String.format("%.1f", measured) + " fps）");
        });
    }

    /** 关闭降帧保持，让遮罩层重新透出下层实时画面。 */
    public void clearFrameThrottle() {
        pendingThrottleSpec = null;
        Platform.runLater(() -> {
            capturedFrame.setThrottleFps(0);
            capturedFrame.clear();
        });
    }

    /** 当前生效的降帧帧率；0 表示未启用。 */
    public double getFrameThrottleFps() {
        return capturedFrame.getThrottleFps();
    }

    public boolean isFrameThrottleActive() {
        return capturedFrame.isThrottling();
    }

    /**
     * 遮罩层当前状态摘要（诊断 / 日志用）。
     *
     * <p>自检和排查「抓屏到底有没有生效」时看这一行就够了。
     */
    public String describeState() {
        OverlayFrameCapture strategy = capturedFrame.getCaptureStrategy();
        String source = strategy != null
                ? ("整屏抓取 " + strategy.width() + "x" + strategy.height())
                : "游戏窗口快照";
        double cost = strategy != null ? strategy.lastCaptureMillis() : -1;
        return "显示=" + active
                + ", 画面来源=" + source
                + ", 已抓到画面=" + (capturedFrame.getBase() != null)
                + (cost >= 0 ? (", 抓屏耗时=" + String.format("%.1f", cost) + "ms") : "")
                + ", 降帧=" + (capturedFrame.isThrottling()
                        ? String.format("%.1f", capturedFrame.getThrottleFps()) + "fps" : "关")
                + ", 鼠标穿透=" + clickThroughApplied
                + ", 光标已隐藏=" + cursorHiddenApplied
                + ", 捕获排除=" + captureExcluded;
    }

    /**
     * 抓屏是在 FX 线程上做的，耗时接近目标帧间隔时会把游戏一起拖慢。
     *
     * <p>这里只提醒一次，并给出不用改代码的调节方式。
     */
    private void warnIfCaptureTooExpensive() {
        if (captureCostWarned || !capturedFrame.isThrottling()) {
            return;
        }
        OverlayFrameCapture strategy = capturedFrame.getCaptureStrategy();
        if (strategy == null) {
            return;
        }
        double cost = strategy.lastCaptureMillis();
        if (cost < 0) {
            return;
        }
        double interval = 1000.0 / capturedFrame.getThrottleFps();
        if (cost < interval * 0.6) {
            return;
        }
        captureCostWarned = true;
        LoggerManager.Logger("WARNING", "[ScreenEffects] 单帧抓屏耗时 "
                + String.format("%.1f", cost) + "ms，已接近 " 
                + String.format("%.0f", capturedFrame.getThrottleFps()) + "fps 的帧间隔 "
                + String.format("%.1f", interval) + "ms，游戏会被一起拖慢。"
                + "若要降低开销，用 -Dstarveil.overlay.captureMaxDim=1920 启动"
                + "（代价是画面会略软）。");
    }

    /**
     * {@code -overlay-test} 自检：自动跑一遍降帧保持 → 花屏 → 恢复，
     * 每个阶段都把 {@link #describeState()} 写进日志。
     */
    private void maybeRunSelfTest() {
        if (!LauncherConfig.getInstance().isOverlayTest()) {
            return;
        }
        LoggerManager.Logger("INFO", "[ScreenEffects] === 遮罩层自检开始 === 初始状态: " + describeState());
        LoggerManager.Logger("INFO", "[ScreenEffects] 自检会依次把可见帧率压到 30 -> 10 -> 4 fps。"
                + "注意：画面本身静止时是看不出区别的——请点对话看文字逐字显示，"
                + "或让角色走动、让通知滑出，那些才是能看出卡顿的地方。");

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> {
                    LoggerManager.Logger("INFO", "[ScreenEffects] 自检: 请求降帧 30fps（轻微）");
                    setFrameThrottle("30");
                }),
                new KeyFrame(Duration.seconds(5), e -> {
                    LoggerManager.Logger("INFO", "[ScreenEffects] 自检: 请求降帧 10fps（明显）");
                    setFrameThrottle("10");
                }),
                new KeyFrame(Duration.seconds(9), e -> {
                    LoggerManager.Logger("INFO", "[ScreenEffects] 自检: 请求降帧 4fps（非常明显）");
                    setFrameThrottle("4");
                }),
                new KeyFrame(Duration.seconds(13), e ->
                        LoggerManager.Logger("INFO", "[ScreenEffects] 自检 状态: " + describeState())),
                new KeyFrame(Duration.seconds(14), e -> {
                    LoggerManager.Logger("INFO", "[ScreenEffects] 自检: 连续花屏 2 秒");
                    runGlitchBurst(0.9, 40, 45);
                }),
                new KeyFrame(Duration.seconds(17), e -> {
                    LoggerManager.Logger("INFO", "[ScreenEffects] 自检: 清除全部特效");
                    clearFrameThrottle();
                    clearEffects();
                }),
                new KeyFrame(Duration.seconds(19), e -> {
                    LoggerManager.Logger("INFO",
                            "[ScreenEffects] === 遮罩层自检结束 === 最终状态: " + describeState());
                    LoggerManager.Logger("INFO",
                            "[ScreenEffects] 顺便验证「只降不升」：请求 120fps（游戏内部帧率很高，这是合法降帧）");
                    setFrameThrottle("120");
                    clearFrameThrottle();
                })
        );
        timeline.play();
    }

    /** 连续触发花屏，用于自检 / 需要持续故障的剧情段。 */
    private void runGlitchBurst(double fraction, int times, long intervalMillis) {
        Timeline burst = new Timeline();
        for (int i = 0; i < times; i++) {
            burst.getKeyFrames().add(new KeyFrame(
                    Duration.millis(intervalMillis * i),
                    e -> glitch(fraction)));
        }
        burst.play();
    }

    // ==================== 平台窗口配置 ====================

    /**
     * 覆盖层的平台化窗口属性（置顶、跳过任务栏、输入穿透、全屏）。
     *
     * <p>注入点 {@link PlatformInjectPoints#OVERLAY_WINDOW_SETUP}：
     * 非 Windows 平台（X11 / Wayland）的窗口属性由平台适配插件用 REPLACE 接管。
     */
    @InjectPoint(PlatformInjectPoints.OVERLAY_WINDOW_SETUP)
    private void applyPlatformWindowSetup() {
        if (!SystemDetector.isWindows()) {
            LoggerManager.Logger("DEBUG", "当前平台非 Windows，跳过内置覆盖层窗口配置（由平台适配插件负责）");
            return;
        }

        // 立即尝试一次，再在窗口/全屏状态真正稳定后补几次：
        // Windows 在进入全屏时可能重建窗口，扩展样式会被重置。
        applyOverlayWin32Style();
        new Timeline(
                new KeyFrame(Duration.millis(120), e -> applyOverlayWin32Style()),
                new KeyFrame(Duration.millis(400), e -> applyOverlayWin32Style()),
                new KeyFrame(Duration.millis(900), e -> applyOverlayWin32Style()),
                new KeyFrame(Duration.millis(1600), e -> applyOverlayWin32Style())
        ).play();
        applyWindowStylesAsync();
    }

    /**
     * 应用「置顶 + 不在任务栏显示 + 从屏幕捕获中排除」。
     *
     * <p>会重复调用：任务栏按钮是窗口创建时才登记的，改扩展样式必须补
     * {@code SWP_FRAMECHANGED} 才会真正生效（见 {@code WinOpsManager.setSkipTaskbar}）。
     */
    private void applyOverlayWin32Style() {
        Stage current = stage;
        if (current == null || !SystemDetector.isWindows()) {
            return;
        }
        try {
            WinOpsManager.setAlwaysOnTop(current, true);
            WinOpsManager.setSkipTaskbar(current, true);
        } catch (Exception ex) {
            LoggerManager.Logger("DEBUG", "Windows 覆盖层配置失败: " + ex.getMessage());
        }

        // 把遮罩层从抓屏里排除掉，之后才能安全地抓「它下面的桌面」
        if (captureExcluded || !Win32ScreenCapture.isAvailable()) {
            return;
        }
        try {
            var hwnd = WinOpsManager.getStageHandle(current);
            if (hwnd == null) {
                if (!captureExclusionWarned) {
                    captureExclusionWarned = true;
                    LoggerManager.Logger("WARNING",
                            "[ScreenEffects] 拿不到遮罩层窗口句柄：任务栏隐藏与整屏抓取都不可用"
                                    + "（降帧/花屏将只捕获游戏窗口内容）");
                }
                return;
            }
            if (Win32ScreenCapture.excludeFromCapture(hwnd)) {
                captureExcluded = true;
                LoggerManager.Logger("INFO",
                        "[ScreenEffects] 遮罩层已从屏幕捕获中排除（WDA_EXCLUDEFROMCAPTURE）");
                ensureDesktopCapture();
            } else if (!captureExclusionWarned) {
                captureExclusionWarned = true;
                LoggerManager.Logger("WARNING",
                        "[ScreenEffects] 无法把遮罩层从屏幕捕获中排除（需要 Windows 10 2004+），"
                                + "降帧/花屏将退化为只捕获游戏窗口内容");
            }
        } catch (Throwable t) {
            LoggerManager.Logger("DEBUG", "设置捕获排除失败: " + t);
        }
    }

    private void applyWindowStylesAsync() {
        new Thread(() -> {
            try {
                Thread.sleep(200);
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        for (java.awt.Window w : java.awt.Window.getWindows()) {
                            if (w.isVisible() && w.getWidth() > 500) {
                                applyWindowsStyles(w);
                                return;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }, "ScreenEffects-Styler").start();
    }

    private void applyWindowsStyles(java.awt.Window w) {
        try {
            WindowUtils.setWindowTransparent(w, true);
        } catch (Exception ignored) {
        }
    }

    private javafx.stage.Screen getScreenForStage(Stage owner) {
        if (owner == null) return Screen.getPrimary();
        double cx = owner.getX() + owner.getWidth() / 2;
        double cy = owner.getY() + owner.getHeight() / 2;
        for (javafx.stage.Screen s : Screen.getScreens()) {
            if (s.getBounds().contains(cx, cy)) {
                return s;
            }
        }
        return Screen.getPrimary();
    }

    // ==================== 键盘拦截 ====================

    /**
     * 拦截指定按键（在覆盖层场景内 consume 掉）
     */
    public void blockKeys(KeyCode... keys) {
        blockedKeys.clear();
        if (keys != null) {
            for (KeyCode k : keys) {
                if (k != null) blockedKeys.add(k);
            }
        }
    }

    /**
     * 清除按键拦截
     */
    public void clearBlockedKeys() {
        blockedKeys.clear();
        blockAllKeys = false;
    }

    /**
     * 拦截所有按键
     */
    public void blockAllKeys() {
        blockAllKeys = true;
    }

    // ==================== 特效 ====================

    /**
     * 模拟卡顿（阻塞当前线程）
     */
    public void lag(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 全屏故障效果。
     *
     * <p>优先走像素级花屏：先捕获一张下层画面，再在它上面做行撕裂、块错位、
     * RGB 色散、扫描线滚动——比在透明层上堆随机矩形真实得多。
     * 捕获不可用时自动退化为矢量花屏。
     *
     * <p>效果是短暂保持的：约 {@value #GLITCH_HOLD_MILLIS} ms 内没有新的调用就自动恢复实时画面。
     */
    public void glitch(double fraction) {
        if (!active || root == null || blueScreenEffect.isBlueScreenActive()) return;
        glitch(fraction, 0, 0, stage.getWidth(), stage.getHeight());
    }

    /**
     * 指定区域的故障效果
     */
    public void glitch(double fraction, double x, double y, double w, double h) {
        if (!active || root == null || blueScreenEffect.isBlueScreenActive()) return;
        if (Platform.isFxApplicationThread()) {
            doGlitch(fraction, x, y, w, h);
        } else {
            Platform.runLater(() -> doGlitch(fraction, x, y, w, h));
        }
    }

    private void doGlitch(double fraction, double x, double y, double w, double h) {
        if (!active || root == null || blueScreenEffect.isBlueScreenActive()) return;
        ensureFrameForGlitch();
        if (!glitchEffect.renderPixelGlitch(root, fraction, capturedFrame, x, y, w, h)) {
            glitchEffect.render(root, fraction, x, y, w, h);
        }
        glitchHoldUntilNanos = System.nanoTime() + GLITCH_HOLD_MILLIS * 1_000_000L;
    }

    /**
     * 花屏需要一个「底下是什么」的底图。
     *
     * <p>降帧保持已经在按目标帧率刷新时直接复用；否则抓一帧当前的画面当作底图。
     */
    private void ensureFrameForGlitch() {
        if (capturedFrame.getBase() != null) {
            return;
        }
        capturedFrame.setSource(resolveCaptureSource());
        updateCaptureViewport();
        capturedFrame.reassert(root);
        if (capturedFrame.capture()) {
            capturedFrame.publish(capturedFrame.getBase());
        }
    }

    /**
     * 清除故障效果，并让遮罩层回到「透出实时画面」的状态。
     */
    public void clearEffects() {
        if (root == null) return;
        Runnable task = () -> {
            glitchHoldUntilNanos = 0;
            glitchEffect.clear(root);
            if (capturedFrame.isThrottling()) {
                capturedFrame.publish(capturedFrame.getBase());
            } else {
                capturedFrame.clear();
            }
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /**
     * 显示伪蓝屏
     */
    public void showBlueScreen() {
        if (blueScreenEffect.isBlueScreenActive()) return;
        if (!active || root == null) {
            pendingBlueScreen = true;
            return;
        }
        blockAllKeys = true;
        blueScreenEffect.show(root, stage);
        // 蓝屏会 clear 整个层，把捕获底图重新垫回最底下
        capturedFrame.reassert(root);
    }

    /**
     * 关闭伪蓝屏
     */
    public void dismissBlueScreen() {
        blockAllKeys = false;
        blueScreenEffect.dismiss(root, stage);
        capturedFrame.reassert(root);
    }

    /**
     * 当前是否处于蓝屏状态
     */
    public boolean isBlueScreenActive() {
        return blueScreenEffect.isBlueScreenActive();
    }

    /**
     * 打印一行终端文本（默认浅绿色）
     */
    public void ttyPrint(String line) {
        ttyPrint(line, Color.LIGHTGREEN);
    }

    /**
     * 打印一行终端文本
     */
    public void ttyPrint(String line, Color color) {
        if (!active || root == null || blueScreenEffect.isBlueScreenActive()) return;
        terminalOverlay.print(root, line, color, stage.getHeight());
    }

    /**
     * 清除终端文本
     */
    public void clearTTY() {
        if (root == null) return;
        terminalOverlay.clear(root);
    }

    // 兼容旧用法：暴露子特效组件
    public GlitchEffect getGlitchEffect() {
        return glitchEffect;
    }

    public BlueScreenEffect getBlueScreenEffect() {
        return blueScreenEffect;
    }

    public TerminalOverlay getTerminalOverlay() {
        return terminalOverlay;
    }
}
