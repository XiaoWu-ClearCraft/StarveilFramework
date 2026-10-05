package com.xiaowu.game.starveil.ui.overlay;

import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.net.NetworkStatus;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.render.TextureNodeFactory;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Window;
import javafx.util.Duration;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 断网提示：强制挡在画面上，直到联网（或用「特殊键」跳过）。
 *
 * <h2>为什么要强制</h2>
 * 这东西只在内容主动开启后才会出现（
 * {@code ContentConfig.setOfflinePromptEnabled(true)}，默认关闭）。
 * 开启的场景通常是「这游戏不联网就没法正常玩」：那就没必要给一个「知道了」按钮
 * 让玩家点掉继续玩一个坏掉的游戏 —— 直接挡住，联网后自动消失。
 *
 * <h2>为什么还要留一个特殊键</h2>
 * 因为网络判断会有假阴性（公共 WiFi 的强制门户、公司代理、探测端点刚好被墙……），
 * 一旦判断错了，玩家就被一个永远不消失的弹窗关在门外 —— 那比断网本身严重得多。
 * 所以留一个不显眼的键作为逃生口：按下去这一次会话不再提示（联网后再断开仍会提示）。
 * 默认 {@code F8}，内容可用
 * {@link ContentConfig#setOfflineBypassKey(String)} 改。
 *
 * <h2>挂在哪</h2>
 * 挂到<b>场景根</b>（{@code GameManager} 的 rootContainer）而不是画布里的
 * modalHost：主菜单阶段还没有画布，而「没网」这件事在主菜单就该说。
 *
 * <h2>怎么做到「真的挡住」</h2>
 * 只靠遮罩节点自己吃事件是不够的，两个坑都踩过：
 *
 * <ol>
 *   <li><b>按键绕过遮罩。</b>按键事件送给的是「焦点节点」，遮罩没拿到焦点就永远收不到 ——
 *       于是回车/空格照旧翻页。而且框架里的 {@code InputHandler} 是在<b>场景</b>上注册的
 *       事件过滤器，场景过滤器按注册先后执行，遮罩后注册就抢不到它前面。</li>
 *   <li><b>场景切换把遮罩节点清掉。</b>{@code GameManager} 接管新场景时会
 *       {@code rootContainer.getChildren().clear()}，遮罩节点被摘掉但本类还记着
 *       {@code overlay != null}，于是「以为在挡、其实什么都没挡」。</li>
 * </ol>
 *
 * 所以拦截放在<b>窗口</b>级过滤器上（实测窗口过滤器先于场景过滤器执行，见下面的
 * {@link #bindWindow}），并且每次显示都确认节点还挂在宿主上。
 *
 * <p>另外还会顺手做两件事，属于「就算有事件漏过去也不会动」的兜底：
 * 按来源锁住 {@code InputHandler} 的操作（并清掉断开瞬间正按着的键），
 * 以及锁住视觉小说的剧情推进（{@link ChatManager#lockAdvance(Object)}）。
 */
public final class OfflinePrompt {

    private static final OfflinePrompt INSTANCE = new OfflinePrompt();

    /** 借用 InputHandler / ChatManager 的锁时用的来源标识（toString 只为了日志好读）。 */
    private static final Object LOCK_OWNER = new Object() {
        @Override
        public String toString() {
            return "断网提示";
        }
    };

    private StackPane root;
    private StackPane overlay;
    private Scene boundScene;
    private Window boundWindow;
    private Label hintLabel;

    /** 联网状态监听是否已经注册过（attach 会被调用多次）。 */
    private boolean listening;

    /** 本次会话是否已经用特殊键跳过。 */
    private boolean bypassed;

    private OfflinePrompt() {
    }

    public static OfflinePrompt getInstance() {
        return INSTANCE;
    }

    /**
     * 绑定到场景与一个全屏宿主（{@code GameManager.initialize/updateScene} 调）。
     *
     * <p>会顺带启动联网探测（仅在功能开启时），并注册特殊键的按键过滤。
     */
    public void attach(StackPane host, Scene scene) {
        if (host == null || scene == null) {
            return;
        }
        this.root = host;
        bindScene(scene);
        if (!ContentConfig.offlinePromptEnabled()) {
            return;
        }
        if (!listening) {
            listening = true;
            NetworkStatus.addListener(this::onNetworkStateChanged);
        }
        NetworkStatus.start();
        // 场景切换时宿主被 clear() 过：节点没了但状态还记着「在显示」，
        // 于是既看不见、也挡不住 —— 这里补挂回去
        if (overlay != null && !host.getChildren().contains(overlay)) {
            host.getChildren().add(overlay);
            overlay.setOpacity(1);
            Logger("INFO", "断网提示已重新挂到新场景（切场景时被摘掉了）");
        }
    }

    /** 切换场景时重新绑定（宿主仍是 rootContainer，跨场景复用）。 */
    private void bindScene(Scene scene) {
        if (scene == boundScene) {
            return;
        }
        boundScene = scene;
        // 窗口一般要等场景上屏才有，取不到就在每次显示时再试；遮罩节点自己也拦一道
        bindWindow(scene);
    }

    /**
     * 把「挡输入」的过滤器挂到<b>窗口</b>上。
     *
     * <p>为什么不是场景：按键事件的过滤器按注册顺序执行，框架的 {@code InputHandler}
     * 在场景建好时就注册了，遮罩永远排在它后面 —— 也就永远拦不住回车/空格。
     * 窗口在事件链上更靠外（实测窗口过滤器先于场景过滤器执行），所以在窗口上拦是可靠的。
     * 另外窗口跨场景不变，切场景不用重新挂。
     */
    private void bindWindow(Scene scene) {
        Window window = scene == null ? null : scene.getWindow();
        if (window == null || window == boundWindow) {
            return;
        }
        boundWindow = window;
        window.addEventFilter(KeyEvent.ANY, this::onGlobalKey);
        window.addEventFilter(javafx.scene.input.MouseEvent.ANY, this::onGlobalMouse);
        window.addEventFilter(javafx.scene.input.ScrollEvent.ANY, this::onGlobalScroll);
        Logger("DEBUG", "断网提示已接管窗口级输入拦截");
    }

    /** 功能是否开启。 */
    private boolean enabled() {
        return ContentConfig.offlinePromptEnabled();
    }

    /** 提示是否正显示在画面上。 */
    private boolean showing() {
        return overlay != null && enabled();
    }

    // ==================== 状态变化 ====================

    private void onNetworkStateChanged(NetworkStatus.State state) {
        Platform.runLater(() -> {
            if (state == NetworkStatus.State.OFFLINE && !bypassed && enabled()) {
                showOverlay();
            } else {
                hideOverlay();
                if (state == NetworkStatus.State.ONLINE) {
                    // 恢复联网后重新武装：下一次断开还要提示
                    bypassed = false;
                }
            }
        });
    }

    // ==================== 特殊键 / 输入拦截 ====================

    /**
     * 窗口级按键拦截：显示期间吃掉一切按键，只放行「跳过键」。
     *
     * <p>窗口过滤器在场景过滤器之前执行，所以这里 consume 之后，
     * 框架的 {@code InputHandler}、菜单、对话框都收不到按键。
     */
    private void onGlobalKey(KeyEvent event) {
        if (!showing()) {
            return;
        }
        if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == bypassKey()) {
            bypass();
            event.consume();
            return;
        }
        event.consume();
    }

    /** 窗口级鼠标拦截：遮罩期间点哪里都不算。 */
    private void onGlobalMouse(javafx.scene.input.MouseEvent event) {
        if (showing()) {
            event.consume();
        }
    }

    /** 窗口级滚轮拦截：否则还能滚出历史记录面板。 */
    private void onGlobalScroll(javafx.scene.input.ScrollEvent event) {
        if (showing()) {
            event.consume();
        }
    }

    /** 用跳过键解封：本次会话不再提示（联网后再断开仍会提示）。 */
    private void bypass() {
        if (bypassed) {
            return;
        }
        bypassed = true;
        Logger("INFO", "已用特殊键跳过断网提示（本次会话不再提示）");
        hideOverlay();
    }

    /** 配置里的特殊键；解析不出来就退回 F8。 */
    private KeyCode bypassKey() {
        String name = ContentConfig.offlineBypassKey();
        if (name != null) {
            try {
                return KeyCode.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                Logger("WARNING", "断网提示的跳过键无法识别: " + name + "，使用 F8");
            }
        }
        return KeyCode.F8;
    }

    // ==================== 显示 / 隐藏 ====================

    private void showOverlay() {
        if (root == null) {
            return;
        }
        if (overlay != null) {
            // 已经在显示：确认节点还在宿主上（切场景会把子节点清空）
            if (!root.getChildren().contains(overlay)) {
                root.getChildren().add(overlay);
                overlay.setOpacity(1);
            }
            return;
        }
        bindWindow(boundScene);      // 场景上屏前拿不到窗口，这里补一次
        overlay = buildOverlay();
        root.getChildren().add(overlay);
        lockInput(true);             // 节点挂上去之后再上锁：中途抛异常也不会留下锁
        FadeTransition fade = new FadeTransition(Duration.millis(200), overlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        Logger("INFO", "断网提示已显示（按 " + bypassKey() + " 可跳过）");
    }

    private void hideOverlay() {
        if (overlay == null) {
            return;
        }
        lockInput(false);
        StackPane node = overlay;
        overlay = null;
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            if (root != null) {
                root.getChildren().remove(node);
            }
        });
        fade.play();
    }

    /**
     * 锁 / 解锁底下的操作。
     *
     * <p>窗口级拦截已经能把事件挡住，这里是兜底：万一有事件从别的路径漏进去
     * （或者窗口还没拿到、拦截没挂上），底下的系统也不该动。
     * 两把锁都按来源记账，所以不会误解别的系统（教程、暂停菜单）加的锁。
     */
    private void lockInput(boolean locked) {
        InputHandler input = GameInstance.getInputHandlerStatic();
        if (input != null) {
            if (locked) {
                input.lockControls(InputHandler.LOCK_OFFLINE);
            } else {
                input.unlockControls(InputHandler.LOCK_OFFLINE);
            }
        }
        if (locked) {
            ChatManager.getInstance().lockAdvance(LOCK_OWNER);
        } else {
            ChatManager.getInstance().unlockAdvance(LOCK_OWNER);
        }
    }

    private StackPane buildOverlay() {
        StackPane pane = new StackPane();
        pane.setBackground(new Background(new BackgroundFill(
                Color.rgb(0, 0, 0, 0.82), CornerRadii.EMPTY, Insets.EMPTY)));
        // 吃掉所有鼠标与按键：这是「强制」的部分
        pane.setPickOnBounds(true);
        pane.addEventFilter(KeyEvent.ANY, KeyEvent::consume);
        pane.addEventFilter(javafx.scene.input.MouseEvent.ANY,
                javafx.scene.input.MouseEvent::consume);

        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28, 36, 28, 36));
        box.setMaxSize(560, javafx.scene.layout.Region.USE_PREF_SIZE);
        box.setBackground(new Background(new BackgroundFill(
                Color.rgb(20, 20, 20, 0.95),
                new CornerRadii(14), Insets.EMPTY)));

        Object iconPath = ContentConfig.appIcon();
        if (iconPath != null) {
            Image icon = TextureNodeFactory.loadImage(String.valueOf(iconPath));
            if (icon != null) {
                ImageView iv = new ImageView(icon);
                iv.setFitWidth(72);
                iv.setFitHeight(72);
                iv.setPreserveRatio(true);
                box.getChildren().add(iv);
            }
        }

        Label title = new Label("未检测到网络连接");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font(24));

        Label detail = new Label("这个游戏需要联网才能继续。\n请连接互联网 —— 连上后本提示会自动消失。");
        detail.setTextFill(Color.rgb(220, 220, 220));
        detail.setFont(Font.font(15));
        detail.setWrapText(true);
        detail.setMaxWidth(480);
        detail.setAlignment(Pos.CENTER);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        hintLabel = new Label("（实在连不上？按 " + bypassKey() + " 跳过本次提示）");
        hintLabel.setTextFill(Color.rgb(150, 150, 150));
        hintLabel.setFont(Font.font(12));

        box.getChildren().addAll(title, detail, hintLabel);

        Label state = new Label();
        state.setTextFill(Color.rgb(150, 150, 150));
        state.setFont(Font.font(11));
        state.setText("正在持续检测网络…");
        box.getChildren().add(state);

        pane.getChildren().add(box);
        return pane;
    }
}
