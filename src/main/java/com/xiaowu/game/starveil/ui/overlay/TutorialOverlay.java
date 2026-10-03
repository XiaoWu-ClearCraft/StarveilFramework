package com.xiaowu.game.starveil.ui.overlay;

import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.input.InputHandler;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.concurrent.CompletableFuture;

/**
 * 教程遮罩层 —— 「高亮某个 UI 元素 + 其余变暗 + 屏幕中央说明文字」。
 *
 * <p>用于新手教程介绍界面元素（生命值、体力值……）：把要介绍的那个控件从遮罩中
 * 「挖」出来保持明亮，其余区域压暗，屏幕中央显示它是什么，玩家按空格 / 回车 /
 * 点击屏幕继续。
 *
 * <h3>两个容易踩的坑（本类就是为了处理它们）</h3>
 *
 * <ol>
 *   <li><b>坐标系</b>：游戏内容画在带缩放的 {@code logicalCanvas} 里，
 *       而 {@code PopupManager} 的容器挂在<b>未缩放</b>的宿主层上，两者坐标系不同。
 *       所以本层必须挂到 <b>GameUI 所在的同一个容器</b>里（见 {@link #attach}），
 *       元素包围盒才能直接对应。</li>
 *
 *   <li><b>点击穿透</b>：带洞的 {@link Shape} 在洞的位置<b>不参与拾取</b>，
 *       点击会直接漏到游戏里。所以必须额外铺一层透明但可拾取的
 *       {@code clickCatcher} 把整个屏幕的点击都接住。</li>
 * </ol>
 */
public final class TutorialOverlay {

    private static TutorialOverlay instance;

    private static final String FONT_PATH = "starveil:fonts/xiaolai-sc-regular.ttf";
    private static final double DIM_ALPHA = 0.72;
    private static final double HOLE_PADDING = 10;
    private static final Color ACCENT = Color.web("#FF6B9D");

    private Pane root;
    private Rectangle clickCatcher;
    private Shape dimMask;
    private Rectangle ring;
    private StackPane infoLayer;

    private Font titleFont;
    private Font bodyFont;

    private boolean attached;
    private boolean infoShowing;
    private CompletableFuture<Void> pendingDismiss;
    private javafx.event.EventHandler<KeyEvent> keyFilter;
    private Timeline ringPulse;

    /** 当前高亮的目标。窗口尺寸变化时要按它重算遮罩的洞，否则会错位。 */
    private Node currentHighlight;

    private TutorialOverlay() {
    }

    public static TutorialOverlay getInstance() {
        if (instance == null) {
            instance = new TutorialOverlay();
        }
        return instance;
    }

    // ==================== 挂载 ====================

    /**
     * 把本层挂到逻辑画布的 <b>模态宿主</b>（modalHost）上。
     *
     * <p>为什么是这个位置：
     * <ul>
     *   <li>它在 {@code logicalCanvas} 内 —— 与血条/体力条<b>共用同一坐标系</b>，
     *       元素包围盒可以直接对应；</li>
     *   <li>它在 {@code chatContainer} 之上 —— 视觉小说模式下对话框铺满屏幕时依然可见；</li>
     *   <li>它是画布内的最顶层 —— 不会被游戏内容或剧情图盖住。</li>
     * </ul>
     *
     * <p>宿主会随 {@code mountContent}（切场景）重建，所以这里<b>不缓存</b>父节点，
     * 每次 show 都重新确认一次，宿主换了会自动迁移过去。
     */
    public void attach() {
        Platform.runLater(this::ensureAttached);
    }

    /** 确认挂载；宿主还没创建就等下次 show* 再试。 */
    private void ensureAttached() {
        StackPane host = GameManager.getInstance().getModalHost();
        if (host == null) {
            return;
        }
        buildIfNeeded();

        if (root.getParent() != host) {
            if (root.getParent() instanceof Pane oldParent) {
                oldParent.getChildren().remove(root);
            }
            host.getChildren().add(root);
            if (root.prefWidthProperty().isBound()) {
                root.prefWidthProperty().unbind();
            }
            root.prefWidthProperty().bind(host.widthProperty());
            if (root.prefHeightProperty().isBound()) {
                root.prefHeightProperty().unbind();
            }
            root.prefHeightProperty().bind(host.heightProperty());
            // 重新挂载时先确保是隐藏的 —— 否则上一次残留的可见遮罩会被带进新一局，
            // 而它的 clickCatcher 不透明，会把所有点击都吞掉（表现为「对话框点不动」）。
            root.setVisible(false);
            LoggerManager.Logger("DEBUG", "TutorialOverlay 已挂载到模态宿主");
        }
        // 盖住同层的暂停/设置等面板
        root.toFront();
        attached = true;
    }

    private void buildIfNeeded() {
        if (root != null) {
            return;
        }
        loadFonts();

        root = new Pane();
        // 注意：root 本身不要 mouseTransparent —— 它是拦截游戏点击的第一道防线
        root.setPickOnBounds(false);

        // ① 全屏点击拦截层（透明但可拾取）—— 防止点击穿透到游戏
        clickCatcher = new Rectangle();
        clickCatcher.setFill(Color.TRANSPARENT);
        clickCatcher.setOnMouseClicked(e -> {
            if (infoShowing) {
                dismiss();
            }
            e.consume();
        });

        // ② 其余区域压暗（带一个洞）
        dimMask = new Rectangle();
        dimMask.setFill(Color.rgb(0, 0, 0, DIM_ALPHA));
        dimMask.setMouseTransparent(true);

        // ③ 高亮描边
        ring = new Rectangle();
        ring.setFill(Color.TRANSPARENT);
        ring.setStroke(ACCENT);
        ring.setStrokeWidth(3);
        ring.setArcWidth(12);
        ring.setArcHeight(12);
        ring.setMouseTransparent(true);
        ring.setVisible(false);
        ringPulse = new Timeline(
                new KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(ring.opacityProperty(), 0.55)),
                new KeyFrame(Duration.millis(700), new javafx.animation.KeyValue(ring.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(1400), new javafx.animation.KeyValue(ring.opacityProperty(), 0.55))
        );
        ringPulse.setCycleCount(Animation.INDEFINITE);

        // ④ 居中说明文字
        infoLayer = new StackPane();
        infoLayer.setAlignment(Pos.CENTER);
        infoLayer.setMouseTransparent(true);
        // ⚠️ root 是 Pane —— Pane 只按【首选尺寸】摆放子节点，不会把它拉伸到满。
        //    不绑定的话 StackPane 会塌缩成卡片本身的大小、停在 (0,0)，
        //    Pos.CENTER 也就无从居中（表现为文字跑到左上角）。
        infoLayer.prefWidthProperty().bind(root.widthProperty());
        infoLayer.prefHeightProperty().bind(root.heightProperty());

        root.getChildren().addAll(clickCatcher, dimMask, ring, infoLayer);

        root.widthProperty().addListener((o, ov, nv) -> refreshMaskOnResize());
        root.heightProperty().addListener((o, ov, nv) -> refreshMaskOnResize());
    }

    /** 窗口/容器尺寸变化时重算遮罩，否则高亮的洞会和元素错开。 */
    private void refreshMaskOnResize() {
        if (!infoShowing) {
            return;
        }
        showHighlight(currentHighlight);
    }

    private void loadFonts() {
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream(FONT_PATH)) {
            titleFont = Font.loadFont(is, 30);
        } catch (Exception e) {
            titleFont = null;
        }
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream(FONT_PATH)) {
            bodyFont = Font.loadFont(is, 18);
        } catch (Exception e) {
            bodyFont = null;
        }
        if (titleFont == null) titleFont = Font.font(30);
        if (bodyFont == null) bodyFont = Font.font(18);
    }

    // ==================== 聚光灯介绍 ====================

    /**
     * 高亮某个元素并显示说明，返回一个在玩家「继续」后完成的 future。
     *
     * @param title     标题（显示元素名称）
     * @param body      说明正文
     * @param highlight 要高亮的元素；传 {@code null} 表示不做高亮（纯说明）
     */
    public CompletableFuture<Void> showInfo(String title, String body, Node highlight) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            buildIfNeeded();
            ensureAttached();
            if (!attached) {
                // 没挂上就别把教程卡死
                LoggerManager.Logger("WARN", "TutorialOverlay 未挂载，跳过本次 UI 介绍");
                future.complete(null);
                return;
            }
            // 上一张还没关就先生效掉，避免教程卡住
            if (pendingDismiss != null && !pendingDismiss.isDone()) {
                pendingDismiss.complete(null);
            }
            pendingDismiss = future;
            infoShowing = true;
            currentHighlight = highlight;

            infoLayer.getChildren().setAll(buildInfoCard(title, body));
            // 练习阶段会把 clickCatcher 设成穿透（那时玩家要操作游戏），
            // 这里必须恢复，否则介绍阶段点击会漏进游戏
            clickCatcher.setMouseTransparent(false);
            showHighlight(highlight);
            installKeyFilter();
            root.setVisible(true);
            root.toFront();
            if (highlight != null) {
                ringPulse.play();
            }
            // 介绍期间锁定操作（只锁本层来源，不会冲掉剧情层的锁）
            lockGameControls();
        });
        return future;
    }

    private VBox buildInfoCard(String title, String body) {
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(780);
        // 按需求：直接在遮罩层上显示白色文本，不套弹窗外框。
        // 深色遮罩本身已经提供对比度，再叠一层框只会显得像"又一个弹窗"。
        card.setStyle("-fx-background-color: transparent;");
        card.setMouseTransparent(true);

        // 文字加投影，保证压在任何游戏画面上都清晰
        javafx.scene.effect.DropShadow shadow =
                new javafx.scene.effect.DropShadow(10, 0, 2, Color.rgb(0, 0, 0, 0.9));

        Label titleLabel = new Label(title);
        titleLabel.setFont(titleFont);
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setEffect(shadow);

        Text bodyText = new Text(body);
        bodyText.setFont(bodyFont);
        bodyText.setFill(Color.web("#F4F2F7"));
        bodyText.setWrappingWidth(740);
        bodyText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        bodyText.setEffect(new javafx.scene.effect.DropShadow(8, 0, 2, Color.rgb(0, 0, 0, 0.9)));

        Label hint = new Label("空格 / Enter / 点击屏幕    继续");
        hint.setFont(Font.font(bodyFont.getFamily(), 14));
        hint.setTextFill(Color.web("#C9C4D4"));
        hint.setEffect(new javafx.scene.effect.DropShadow(6, 0, 1, Color.rgb(0, 0, 0, 0.9)));
        FadeTransition blink = new FadeTransition(Duration.millis(1100), hint);
        blink.setFromValue(1.0);
        blink.setToValue(0.35);
        blink.setCycleCount(Animation.INDEFINITE);
        blink.setAutoReverse(true);
        blink.play();

        card.getChildren().addAll(titleLabel, bodyText, hint);
        return card;
    }

    /** 计算高亮区域并重建压暗遮罩。 */
    private void showHighlight(Node target) {
        if (target == null || root == null) {
            ring.setVisible(false);
            rebuildMask(null);
            return;
        }
        Bounds b = boundsIn(target, root);
        if (b == null || b.getWidth() <= 0 || b.getHeight() <= 0) {
            // 元素还没布局好（例如魔力条被系统隐藏）——退化成纯说明
            ring.setVisible(false);
            rebuildMask(null);
            return;
        }
        Rectangle hole = new Rectangle(
                b.getMinX() - HOLE_PADDING,
                b.getMinY() - HOLE_PADDING,
                b.getWidth() + HOLE_PADDING * 2,
                b.getHeight() + HOLE_PADDING * 2);
        rebuildMask(hole);

        ring.setX(hole.getX());
        ring.setY(hole.getY());
        ring.setWidth(hole.getWidth());
        ring.setHeight(hole.getHeight());
        ring.setVisible(true);
    }

    /** 用整屏矩形减去 hole 得到「带洞的压暗层」；hole 为 null 时整屏压暗。 */
    private void rebuildMask(Rectangle hole) {
        if (root == null) {
            return;
        }
        double w = Math.max(1, root.getWidth());
        double h = Math.max(1, root.getHeight());
        clickCatcher.setWidth(w);
        clickCatcher.setHeight(h);

        if (hole == null) {
            if (dimMask != null) {
                dimMask.setVisible(false);
            }
            Rectangle plain = new Rectangle(0, 0, w, h);
            plain.setFill(Color.rgb(0, 0, 0, DIM_ALPHA));
            plain.setMouseTransparent(true);
            replaceDimMask(plain);
            return;
        }

        Rectangle full = new Rectangle(0, 0, w, h);
        Shape masked = Shape.subtract(full, hole);
        masked.setFill(Color.rgb(0, 0, 0, DIM_ALPHA));
        masked.setMouseTransparent(true);
        replaceDimMask(masked);
    }

    private void replaceDimMask(Shape replacement) {
        int idx = root.getChildren().indexOf(dimMask);
        if (idx >= 0) {
            root.getChildren().set(idx, replacement);
        }
        dimMask = replacement;
    }

    /** 把 node 的包围盒转换到 targetSpace 的坐标系。 */
    private static Bounds boundsIn(Node node, Node targetSpace) {
        try {
            Bounds sceneBounds = node.localToScene(node.getBoundsInLocal());
            return targetSpace.sceneToLocal(sceneBounds);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 继续 / 关闭 ====================

    private void installKeyFilter() {
        if (root.getScene() == null || keyFilter != null) {
            return;
        }
        keyFilter = event -> {
            if (!infoShowing) {
                return;
            }
            KeyCode code = event.getCode();
            if (code == KeyCode.SPACE || code == KeyCode.ENTER) {
                dismiss();
                event.consume();
            }
        };
        root.getScene().addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
    }

    private void removeKeyFilter() {
        if (keyFilter != null && root != null && root.getScene() != null) {
            root.getScene().removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        }
        keyFilter = null;
    }

    /** 玩家点击 / 按键继续。可从任意线程调用。 */
    public void dismiss() {
        if (!infoShowing) {
            return;
        }
        // 状态立刻置位：既防止一次点击被处理两次，也避免非 FX 线程调用时的竞态
        infoShowing = false;
        currentHighlight = null;
        CompletableFuture<Void> f = pendingDismiss;
        pendingDismiss = null;

        // 场景图操作统一丢到 FX 线程 —— reset() 等入口可能不在 FX 线程上
        Platform.runLater(() -> {
            if (ringPulse != null) {
                ringPulse.stop();
            }
            removeKeyFilter();
            if (root != null) {
                root.setVisible(false);
            }
            if (infoLayer != null) {
                infoLayer.getChildren().clear();
            }
            if (ring != null) {
                ring.setVisible(false);
            }
            // 归还本层的锁（剧情层等其它系统的锁不受影响）
            unlockGameControls();
        });

        if (f != null && !f.isDone()) {
            f.complete(null);
        }
    }

    public boolean isInfoShowing() {
        return infoShowing;
    }

    /** 强制收起（例如教程被中断、或开新游戏时）。不依赖 infoShowing，可无条件调用。 */
    public void forceHide() {
        // dismiss() 在 infoShowing=false 时会提前返回，那样就收不干净了；
        // 这里把所有状态一次性复位，保证跨局不会留下一个「隐形但吞点击」的遮罩。
        infoShowing = false;
        currentHighlight = null;
        CompletableFuture<Void> f = pendingDismiss;
        pendingDismiss = null;

        Platform.runLater(() -> {
            if (ringPulse != null) {
                ringPulse.stop();
            }
            removeKeyFilter();
            if (root != null) {
                root.setVisible(false);
            }
            if (infoLayer != null) {
                infoLayer.getChildren().clear();
            }
            if (ring != null) {
                ring.setVisible(false);
            }
            // 点击拦截层恢复穿透 —— 否则它会一直吞掉后续所有点击
            if (clickCatcher != null) {
                clickCatcher.setMouseTransparent(true);
            }
            unlockGameControls();
        });

        if (f != null && !f.isDone()) {
            f.complete(null);
        }
    }

    // ==================== 工具 ====================

    /** 锁定游戏操作（移动/交互），只锁本层自己的来源，不影响剧情层等其它系统。 */
    private void lockGameControls() {
        try {
            InputHandler ih = GameInstance.getInputHandlerStatic();
            if (ih != null) {
                ih.lockControls(InputHandler.LOCK_TUTORIAL);
            }
        } catch (Exception e) {
            // 游戏可能还没启动，忽略
        }
    }

    private void unlockGameControls() {
        try {
            InputHandler ih = GameInstance.getInputHandlerStatic();
            if (ih != null) {
                ih.unlockControls(InputHandler.LOCK_TUTORIAL);
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}
