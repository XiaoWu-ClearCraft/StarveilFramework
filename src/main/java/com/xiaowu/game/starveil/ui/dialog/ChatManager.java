package com.xiaowu.game.starveil.ui.dialog;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;
import com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver;

import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.game.story.DialogSequence;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.render.AnimationController;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import com.xiaowu.game.starveil.render.engine.TextSegment;
import com.xiaowu.game.starveil.render.engine.javafx.JavaFXRenderEngine;
import com.xiaowu.game.starveil.infrastructure.persistence.HistoryManager;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class ChatManager {

    private static ChatManager instance;

    public static ChatManager getInstance() {
        if (instance == null) instance = new ChatManager();
        return instance;
    }

    private CompletableFuture<String> currentInputFuture;
    private CompletableFuture<String> currentDateFuture;
    private CompletableFuture<Integer> currentChoiceFuture;
    private String pendingChoicePrompt;
    private List<String> pendingChoices;
    private CompletableFuture<Void> pendingChoiceDialogFuture;
    private String[] pendingMessageParts;
    private int pendingMessagePartIndex;

    private StackPane chatContainer;
    private StackPane storyImagePane;
    private ImageView storyImageView;
    private Pane overlay;
    private VBox historyPanel;
    private VBox currentDialog;
    private VBox choiceBox;

    private final AtomicBoolean isProcessingClick = new AtomicBoolean(false);
    private final AtomicBoolean isHiding = new AtomicBoolean(false);
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN = 300;

    private final BooleanProperty dialogActive = new SimpleBooleanProperty(false);

    /** 游戏暂停时冻结对话框：不响应 空格/回车推进、输入确认，并停掉输入光标。 */
    private boolean pausedByGame = false;

    /**
     * 教程等外部系统锁住剧情推进（点击 / 空格 / 回车不再翻页）。
     *
     * <p>刻意与 {@link #pausedByGame} <b>分开</b>：暂停菜单恢复时会清掉 pausedByGame，
     * 如果把教程的锁塞进同一个标志，玩家按一下 ESC 就能把教程期间的剧情解冻。
     */
    private boolean storyAdvanceLocked = false;

    /**
     * 全屏模态（如断网提示）借用的推进锁，<b>按来源记账</b>。
     *
     * <p>为什么不是一个 boolean：和 {@link InputHandler#lockControls(Object)} 同样的道理 ——
     * 断网提示恢复联网时会解一次锁，若是共用标志，就会把教程或暂停菜单加的锁一起解掉。
     * 各来源各记一笔，互不干扰；同一来源重复加锁也只算一次。
     */
    private final Set<Object> advanceLocks = new LinkedHashSet<>();

    /** 剧情层是否被教程临时收起过。用于精确还原，避免误开或误关对话框。 */
    private boolean storyLayerHandedOver = false;

    /**
     * 跨局世代号。每次{@link #resetCrossGameState()}自增，
     * 用来作废「已排队但属于上一局」的延迟清理（见 {@link #forceCloseAll()}）。
     */
    private final java.util.concurrent.atomic.AtomicLong crossGameGeneration =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * 对话监听器世代号。每次 {@link #setupContinueListener} 自增，
     * 用于让「已被提前完成的旧对话框」的延迟清理失效 ——
     * 否则它会清掉新对话框刚绑好的点击处理器，表现为对话框点不动。
     */
    private long listenerToken = 0;
    private javafx.scene.control.TextField currentInputField;

    private InputHandler.KeyEventHandler currentKeyHandler;
    private EventHandler<ScrollEvent> scrollHandler;

    private String currentVoicePath;
    private String currentStandeePath;
    private double currentStandeeWidth;

    /**
     * 立绘的显示样式（偏移 + 缩放）。
     *
     * <p>默认是「全身立绘、贴着左下角、高度 = 画布 68%」；内容可以让立绘偏移/放大来显示
     * 半身（{@link StandeeStyle} 里有坐标系说明）。
     *
     * <p><b>样式是粘性的</b>：设过一次就一直生效，之后只换立绘路径（不带偏移的写法）不会把它
     * 重置回默认；想回到默认就显式传 0、0、1。这样内容可以「开局定一次半身框，
     * 后面换表情图不用每次重复写偏移」。
     */
    private StandeeStyle standeeStyle = StandeeStyle.standard();

    /**
     * 立绘操作的世代号：滑入 / 淡入淡出 / 收起都是异步动画，
     * 回调跑完前内容可能已经设了下一张立绘 —— 旧动画的收尾<b>不能</b>动新的立绘，
     * 否则会出现「刚设的立绘被上一次的收起动画清掉」（表现为立绘没显示、但对话框还留着让位）。
     */
    private long standeeToken = 0;

    /** 最近一次立绘滑动动画（滑入 / 滑出）。用来判断「现在是不是动画在跑」，见 {@link #ensureStandeeOnScreen()}。 */
    private TranslateTransition standeeSlide;

    /** 最近一次立绘淡入淡出（切换立绘）。同上，避免自愈打断正在跑的淡入。 */
    private FadeTransition standeeFade;

    private StackPane standeePane;
    private ImageView standeeImageView;

    private AnimationController animationController;
    private RenderEngine renderEngine;
    private HistoryManager historyManager;
    private DialogUI dialogUI;
    private final Map<String, Anchor> anchors = new HashMap<>();

    private static class Anchor {
        final int stepIndex;
        final int stepsSize;
        Anchor(int stepIndex, int stepsSize) {
            this.stepIndex = stepIndex;
            this.stepsSize = stepsSize;
        }
    }

    public DialogSequence runningSequence;

    /**
     * 当前正在执行的章节脚本（新式剧情）。
     * 供调试窗口展示脚本进度；旧式 {@link DialogSequence} 仍走 {@link #runningSequence}。
     */
    private volatile Object activeStory;

    /** 释放被阻塞的剧情所需：记录所有「等待玩家操作」的 future。 */
    private CompletableFuture<Void> currentDialogFuture;
    private CompletableFuture<Void> currentStoryImageFuture;

    private ChatManager() {
        initialize();
    }

    private void initialize() {
        // 创建 JavaFX 渲染引擎
        renderEngine = new JavaFXRenderEngine();
        renderEngine.initialize(1200, 675);

        // 创建动画控制器,传入渲染引擎
        animationController = new AnimationController(renderEngine);
        buildUI();
    }

    private void buildUI() {
        chatContainer = new StackPane();
        chatContainer.setAlignment(javafx.geometry.Pos.CENTER);
        chatContainer.setMouseTransparent(true);
        chatContainer.setPickOnBounds(false);
        chatContainer.setVisible(false);
        // 最小尺寸必须显式归零：容器的高度是「布局算出来的」，而里面的立绘可以比容器还高
        // （放大 / 半身框就是靠溢出 + 画布裁剪实现的）。不归零的话，立绘的 min 会把容器顶大，
        // 容器一变高又触发按新高度重新缩放立绘 —— 于是每过一帧就更大一点，无限长大。
        chatContainer.setMinSize(0, 0);
        chatContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        standeePane = new StackPane();
        standeePane.setVisible(false);
        standeePane.setMouseTransparent(true);
        standeePane.setPickOnBounds(false);
        standeePane.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);
        // 同上：立绘溢出是刻意效果，不该反过来撑大任何容器
        standeePane.setMinSize(0, 0);
        standeePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        storyImagePane = new StackPane();
        storyImagePane.setVisible(false);
        storyImagePane.setMouseTransparent(true);
        storyImagePane.setPickOnBounds(false);
        storyImagePane.setMinSize(0, 0);

        overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.4);");
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);

        historyManager = new HistoryManager(chatContainer, overlay);
        historyPanel = historyManager.buildHistoryPanel();
        if (historyPanel != null) {
            historyPanel.setMinSize(0, 0);   // 面板再高也不许把容器顶大
        }

        dialogUI = new DialogUI(chatContainer, overlay, animationController, renderEngine, historyManager);

        chatContainer.getChildren().addAll(overlay, standeePane, historyPanel);
        bindSize();
    }

    private void bindSize() {
        // 监听真正承载它的容器（GameManager 的 16:9 内容区）尺寸变化；
        // 全屏切换 / 切场景 / 拖动窗口都会通过容器尺寸变化触发重排
        chatContainer.widthProperty().addListener((o, ov, nv) -> relayoutToCurrentArea());
        chatContainer.heightProperty().addListener((o, ov, nv) -> relayoutToCurrentArea());
    }

    /** 供 GameManager 在内容挂载 / 比例区变化后调用，确保对话框、历史与剧情图按最新区域尺寸布局。 */
    public void syncLayout() {
        if (Platform.isFxApplicationThread()) {
            relayoutToCurrentArea();
        } else {
            Platform.runLater(this::relayoutToCurrentArea);
        }
    }

    private void relayoutToCurrentArea() {
        double w = chatContainer.getWidth();
        double h = chatContainer.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        resizeChildren(w, h);
        // 对话框可见时重新计算最大宽度与边距，避免最大化→全屏等之后右侧留空
        if (dialogActive.get() && currentDialog != null) {
            layoutDialogForStandee();
        }
    }

    private void resizeChildren(double w, double h) {
        chatContainer.setPrefSize(w, h);
        overlay.setPrefSize(w, h);
        if (storyImageView != null) {
            storyImageView.setFitWidth(w);
            storyImageView.setFitHeight(h);
        }
        if (standeeImageView != null) {
            ImageView view = standeeImageView;
            applyStandeeStyle(view, h);
            Image img = view.getImage();
            currentStandeeWidth = (img == null || img.getHeight() <= 0)
                    ? 0
                    : img.getWidth() * (standeeFitHeight(h) / img.getHeight());
        }
        if (historyPanel != null) {
            historyPanel.setPrefWidth(w * 0.8);
            historyPanel.setMaxHeight(h * 0.7);
        }
    }

    public CompletableFuture<Void> showDialog(String speaker, String message) {
        return showDialog(speaker, message, null, "");
    }

    public CompletableFuture<Void> showDialog(String speaker, String message, String voicePath) {
        return showDialog(speaker, message, voicePath, "");
    }

    public CompletableFuture<Void> showDialog(String speaker, String message, String voicePath, String standeeImagePath) {
        // 不带样式的老签名：沿用当前样式（样式是粘性的）
        return showDialog(speaker, message, voicePath, standeeImagePath, standeeStyle);
    }

    /**
     * 带立绘样式参数的对话。
     *
     * @param standeeImagePath 立绘路径；{@code ""} 收起立绘，null 表示不变
     * @param style            立绘样式：偏移（逻辑画布像素）+ 缩放，见 {@link StandeeStyle}
     */
    public CompletableFuture<Void> showDialog(String speaker, String message, String voicePath,
                                              String standeeImagePath, StandeeStyle style) {
        Logger("DEBUG", "showDialog执行 speaker : " + speaker + " message : " + message);
        this.standeeStyle = style == null ? StandeeStyle.standard() : style;
        CompletableFuture<Void> future = new CompletableFuture<>();
        currentDialogFuture = future;
        future.whenComplete((r, ex) -> {
            if (currentDialogFuture == future) currentDialogFuture = null;
        });

        String userName = com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys
                .PLAYER_NAME.get();
        if (message != null) {
            message = message.replace("{NAME}", userName == null ? "" : userName);
        }
        if (speaker != null) {
            speaker = speaker.replace("{NAME}", userName == null ? "" : userName);
        }
        String displayMessage = message;
        if (message != null && message.contains("<more>")) {
            pendingMessageParts = message.split("<more>", -1);
            pendingMessagePartIndex = 0;
            displayMessage = pendingMessageParts[0];
        } else {
            pendingMessageParts = null;
            pendingMessagePartIndex = 0;
        }
        String finalMessage = message;
        String finalSpeaker = speaker;
        String finalDisplayMessage = displayMessage;
        String historyMessage = pendingMessageParts != null ? pendingMessageParts[0] : message;
        Platform.runLater(() -> {
            stopCurrentVoice();
            currentVoicePath = voicePath;
            historyManager.addToHistory(finalSpeaker, historyMessage);
            // 同步进调试窗口的聊天记录（内存环形缓冲，上限 1000 条）
            com.xiaowu.game.starveil.game.story.ChatHistory.getInstance()
                    .add(finalSpeaker, historyMessage);
            updateContainerSize();
            cleanupCurrentDialog();
            traceStandee("showDialog 之前(路径=" + standeeImagePath + ")");   // TEMP-TRACE
            double oldStandeeEdge = standeeRightEdge();
            String oldStandeePath = currentStandeePath;
            boolean slideIn = standeeImagePath != null && !standeeImagePath.isEmpty()
                    && oldStandeePath == null;
            boolean slideOut = standeeImagePath != null && standeeImagePath.isEmpty()
                    && oldStandeeEdge > 0;
            updateStandee(standeeImagePath);
            traceStandee("showDialog 之后(路径=" + standeeImagePath + ")");   // TEMP-TRACE
            // TEMP-TRACE: 等滑入动画该跑完的时候再看一眼
            javafx.animation.PauseTransition probe =
                    new javafx.animation.PauseTransition(Duration.millis(900));
            probe.setOnFinished(e -> traceStandee("900ms 后"));
            probe.play();
            dialogActive.set(true);
            overlay.setVisible(true);
            overlay.setOpacity(1);
            overlay.setMouseTransparent(false);
            chatContainer.setVisible(true);
            chatContainer.setMouseTransparent(false);
            chatContainer.toFront();
            currentDialog = dialogUI.createDialogPanel(finalSpeaker, finalDisplayMessage, voicePath,
                    () -> handleContinueClick(future), null);
            chatContainer.getChildren().add(currentDialog);
            ensureDialogAboveStandee();   // 有覆盖关系时对话框要压在立绘之上
            if (slideIn) {
                animateDialogForStandeeSlideIn();
            } else if (slideOut) {
                animateDialogForStandeeSlideOut(oldStandeeEdge);
            } else {
                layoutDialogForStandee();
            }
            if (voicePath != null && !voicePath.trim().isEmpty()) playVoice(voicePath);
            setupContinueListener(future);
        });
        return future;
    }

    private void layoutDialogForStandee() {
        if (currentDialog == null) return;
        ensureStandeeOnScreen();
        double vw = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
        double gap = 16;
        // 立绘让出的宽度（受内容设定的上限约束）；上限之外的部分让对话框压在立绘上
        double sw = standeeReservedWidth();
        double leftMar = sw > 0 ? sw + gap : gap;
        double rightMar = gap;
        double bottomMar = 50;
        currentDialog.setMaxWidth(vw - leftMar - rightMar);
        currentDialog.setTranslateX(0);
        StackPane.setAlignment(currentDialog, javafx.geometry.Pos.BOTTOM_LEFT);
        StackPane.setMargin(currentDialog, new javafx.geometry.Insets(0, rightMar, bottomMar, leftMar));
    }

    private void animateDialogForStandeeSlideIn() {
        if (currentDialog == null || currentStandeeWidth <= 0) return;
        double vw = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
        double sw = standeeReservedWidth();
        double gap = 16;
        double startW = vw - gap * 2;
        double endW = vw - sw - gap * 2;
        currentDialog.setMaxWidth(startW);
        currentDialog.setTranslateX(0);
        StackPane.setAlignment(currentDialog, javafx.geometry.Pos.BOTTOM_LEFT);
        StackPane.setMargin(currentDialog, new javafx.geometry.Insets(0, gap, 50, gap));
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(currentDialog.maxWidthProperty(), startW, Interpolator.EASE_OUT),
                        new KeyValue(currentDialog.translateXProperty(), 0, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(350),
                        new KeyValue(currentDialog.maxWidthProperty(), endW, Interpolator.EASE_OUT),
                        new KeyValue(currentDialog.translateXProperty(), sw, Interpolator.EASE_OUT)
                )
        );
        // 动画结束后把边距归到「让 sw」的一致状态（动画期间靠 translateX 顶上）
        timeline.setOnFinished(e -> layoutDialogForStandee());
        timeline.play();
    }

    private void animateDialogForStandeeSlideOut(double oldW) {
        if (currentDialog == null || oldW <= 0) {
            layoutDialogForStandee();
            return;
        }
        double vw = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
        double gap = 16;
        oldW = Math.min(oldW, vw * ContentConfig.standeeReservedMaxRatio());
        double startW = vw - oldW - gap * 2;
        double endW = vw - gap * 2;
        currentDialog.setMaxWidth(startW);
        currentDialog.setTranslateX(oldW);
        StackPane.setAlignment(currentDialog, javafx.geometry.Pos.BOTTOM_LEFT);
        StackPane.setMargin(currentDialog, new javafx.geometry.Insets(0, gap, 50, gap));
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(currentDialog.maxWidthProperty(), startW, Interpolator.EASE_IN),
                        new KeyValue(currentDialog.translateXProperty(), oldW, Interpolator.EASE_IN)
                ),
                new KeyFrame(Duration.millis(250),
                        new KeyValue(currentDialog.maxWidthProperty(), endW, Interpolator.EASE_IN),
                        new KeyValue(currentDialog.translateXProperty(), 0, Interpolator.EASE_IN)
                )
        );
        // 动画只动 maxWidth/translateX，让位的「边距」一直停在 gap；
        // 结束时按当前状态重算一次，把边距/宽度归到一致的状态。
        timeline.setOnFinished(e -> layoutDialogForStandee());
        timeline.play();
    }

    /**
     * 立绘加载失败时的可读诊断：写错路径是常见坑，别只丢一句「加载失败」。
     *
     * <p>立绘是贴图，内容只需要写类型目录下面的路径（{@code character/normal/relaxed.png}），
     * 类型由这里补上；显式写 {@code starveil:textures/...} 或 {@code /assets/...} 也行。
     */
    private String standeeLoadHint(String originalPath, String resolved) {
        if (resolved == null) {
            return "（路径解析不出来：" + originalPath + "）";
        }
        boolean exists = StarveilResourceResolver.exists(resolved);
        return "（" + originalPath + " → " + resolved + "："
                + (exists ? "文件在，但读不出图片内容" : "找不到该文件")
                + "；立绘写类型目录下的相对路径即可，例如 character/normal/relaxed.png）";
    }

    private void updateStandee(String newPath) {
        if (newPath == null) return;
        if (newPath.isEmpty()) {
            slideOutStandee();
            return;
        }
        if (newPath.equals(currentStandeePath) && standeePane.isVisible()) {
            // 同一个立绘，可能只是改了偏移/缩放：就地调整，不必淡入淡出
            restyleCurrentStandee();
            return;
        }
        if (currentStandeePath == null) {
            slideInStandee(newPath);
        } else {
            crossFadeStandee(newPath);
        }
    }

    /** 立绘的目标显示高度：画布高度的 68% × 内容指定的缩放。 */
    private double standeeFitHeight(double paneH) {
        return standeeStyle.fitHeight(paneH);
    }

    /**
     * 立绘「应该在画面上」却没显示时把它摆回来。
     *
     * <p>踩过的坑（两个，都是异步动画留下的残局）：
     * <ol>
     *   <li>滑出（收起立绘）动画被打断 → 层停在负 translateX，立绘在画面外；</li>
     *   <li>{@link #crossFadeStandee(String)} 把新图 opacity 设成 0 等淡入，
     *       淡入没跑完 → <b>图片</b>停在 opacity 0 —— 层可见、位置也对，就是看不见。</li>
     * </ol>
     * 两处的可见性标志都还是 true，所以任何「是否加载过 / 是否可见」的判断都会以为立绘在。
     * 这里在每次真正布局对话框时自愈：只要没有立绘动画在跑、层可见、图片还在，
     * 位置就该是 0、不透明度就该是 1。
     */
    private void ensureStandeeOnScreen() {
        if (standeeImageView == null || !standeePane.isVisible()) {
            return;
        }
        if (isRunning(standeeSlide) || isRunning(standeeFade)) {
            return;
        }
        String healed = null;
        if (standeePane.getTranslateX() < -0.5) {
            healed = "位置 x=" + Math.round(standeePane.getTranslateX());
            standeePane.setTranslateX(0);
        }
        if (standeeImageView.getOpacity() < 0.99) {
            healed = (healed == null ? "" : healed + "；")
                    + "不透明度=" + standeeImageView.getOpacity();
            standeeImageView.setOpacity(1);
        }
        if (healed != null) {
            Logger("DEBUG", "立绘被动画留在「看不见」的状态（" + healed + "），已恢复显示");
        }
    }

    /** 动画是不是正在跑（null 视为没在跑）。 */
    private static boolean isRunning(javafx.animation.Animation animation) {
        return animation != null
                && animation.getStatus() == javafx.animation.Animation.Status.RUNNING;
    }

    /** 当前画布高度（布局还没跑过时按逻辑画布 1080 算）。 */
    private double standeePaneHeight() {
        return chatContainer.getHeight() > 0 ? chatContainer.getHeight() : 1080;
    }

    /** TEMP-TRACE: 立绘层的真实状态，用来定位「立绘没显示但对话框还让位」。 */
    private void traceStandee(String tag) {
        ImageView view = standeeImageView;
        Logger("DEBUG", "【立绘状态】" + tag
                + " 层可见=" + standeePane.isVisible()
                + " 层不透明=" + standeePane.getOpacity()
                + " 层X=" + Math.round(standeePane.getTranslateX())
                + " 子节点=" + standeePane.getChildren().size()
                + " 图片不透明=" + (view == null ? "无" : Math.round(view.getOpacity()))
                + " 图X=" + (view == null ? "无" : Math.round(view.getTranslateX()))
                + " 高=" + (view == null ? "无" : Math.round(view.getFitHeight()))
                + " 路径=" + currentStandeePath);
    }

    /**
     * 对话框给立绘让出的宽度：立绘实际占到的宽度，但不超过内容设定的上限
     * （{@link ContentConfig#setStandeeReservedMaxRatio(double)}，默认画布宽的一半）。
     */
    private double standeeReservedWidth() {
        double vw = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
        return Math.min(standeeRightEdge(), vw * ContentConfig.standeeReservedMaxRatio());
    }

    /** 把图片按当前样式摆好（尺寸 + 偏移）。 */
    private void applyStandeeStyle(ImageView view, double paneH) {
        if (view == null) return;
        double fitH = standeeFitHeight(paneH);
        view.setFitHeight(fitH);
        view.setTranslateX(standeeStyle.offsetX());
        view.setTranslateY(standeeStyle.offsetY());
        // 立绘比「预留位置」还宽 → 会盖到对话框区域：这时把立绘<b>横向</b>居中到预留区中心
        // （也就是让左右两边超出的部分一样多），纵向仍按内容给的 offsetY 走。
        // 配合「对话框压在立绘之上」，重叠看起来才是有意为之。
        Image img = view.getImage();
        if (img == null || img.getHeight() <= 0) {
            return;
        }
        double naturalWidth = standeeStyle.displayWidth(paneH, img.getWidth(), img.getHeight());
        double vw = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
        double reserved = Math.min(Math.max(0, standeeStyle.offsetX()) + naturalWidth,
                vw * ContentConfig.standeeReservedMaxRatio());
        if (naturalWidth > reserved) {
            view.setTranslateX((reserved - naturalWidth) / 2);
        }
    }

    /**
     * 对话框必须压在立绘之上：有覆盖关系时（立绘比预留位置宽）玩家仍然要能读字。
     * 立绘层永远待在 overlay 之上、所有对话框之下。
     */
    private void ensureDialogAboveStandee() {
        if (currentDialog == null || standeePane == null || storyImagePane == null) {
            return;
        }
        int dialogIndex = chatContainer.getChildren().indexOf(currentDialog);
        int standeeIndex = chatContainer.getChildren().indexOf(standeePane);
        if (dialogIndex >= 0 && standeeIndex > dialogIndex) {
            chatContainer.getChildren().remove(standeePane);
            chatContainer.getChildren().add(dialogIndex, standeePane);
        }
    }

    /**
     * 立绘在画布上占到的右边界（供对话框让位）。
     *
     * <p>判定依据是「立绘<b>现在真的在画面上</b>」而不是「图片已经加载过」：
     * 只要立绘层不可见（或被滑出动画挪到了画面外），就不该让对话框留出一块空白。
     * 踩过的坑：图片还挂在字段上、但立绘已经不在屏幕上，对话框却一直缩在右边。
     */
    private double standeeRightEdge() {
        if (standeeImageView == null || standeeImageView.getImage() == null) {
            return 0;
        }
        if (!standeePane.isVisible()) {
            return 0;
        }
        Image img = standeeImageView.getImage();
        return standeeStyle.rightEdge(standeePaneHeight(), img.getWidth(), img.getHeight());
    }

    /** 只换样式不换图：重新算尺寸/宽度并让对话框重新让位。 */
    private void restyleCurrentStandee() {
        if (standeeImageView == null) return;
        double paneH = standeePaneHeight();
        applyStandeeStyle(standeeImageView, paneH);
        Image img = standeeImageView.getImage();
        currentStandeeWidth = img == null
                ? 0
                : standeeStyle.displayWidth(paneH, img.getWidth(), img.getHeight());
        layoutDialogForStandee();
    }

    private void slideInStandee(String path) {
        String resolved = StarveilResourceResolver.resolveTexture(path);
        try {
            javafx.scene.image.ImageView view = com.xiaowu.game.starveil.render.TextureNodeFactory.loadSheetView(resolved, -1, -1);
            if (view == null || view.getImage() == null || view.getImage().isError()) {
                Logger("ERROR", "Failed to load standee image: " + path
                        + standeeLoadHint(path, resolved));
                return;
            }
            long token = ++standeeToken;
            double paneH = standeePaneHeight();
            standeeImageView = view;
            standeeImageView.setPreserveRatio(true);
            applyStandeeStyle(standeeImageView, paneH);
            standeeImageView.setOpacity(1);   // 新图必须是可见的（别继承任何残留状态）
            standeePane.getChildren().setAll(standeeImageView);
            double standeeH = standeeFitHeight(paneH);
            double imgW = view.getImage().getWidth() * (standeeH / view.getImage().getHeight());
            currentStandeeWidth = imgW;
            standeePane.setTranslateX(-imgW);
            standeePane.setVisible(true);
            standeePane.setOpacity(1);
            TranslateTransition slide = new TranslateTransition(Duration.millis(350), standeePane);
            slide.setToX(0);
            slide.setInterpolator(Interpolator.EASE_OUT);
            standeeSlide = slide;
            slide.play();
            currentStandeePath = path;
            Logger("DEBUG", "立绘滑入: " + path + "（第 " + token + " 次立绘操作）");
        } catch (Exception e) {
            Logger("ERROR", "Failed to load standee: " + path + " - " + e.getMessage());
        }
    }

    private void slideOutStandee() {
        if (!standeePane.isVisible()) {
            // 已经不可见了：这里也得把图片清掉，否则 standeeRightEdge() 还会以为立绘在，
            // 后续对话框会一直按「有立绘」让位（表现为对话框一直缩着、右边空一大块）。
            standeeToken++;   // 作废在飞的动画，别让它回来清算新立绘
            standeePane.getChildren().clear();
            standeeImageView = null;
            currentStandeePath = null;
            currentStandeeWidth = 0;
            return;
        }
        long token = ++standeeToken;
        Logger("DEBUG", "立绘收起（滑出动画开始）");   // TEMP-TRACE
        double imgW = 400;
        if (standeeImageView != null && standeeImageView.getImage() != null) {
            Image img = standeeImageView.getImage();
            double ih = img.getHeight();
            if (ih > 0) {
                double fh = standeeImageView.getFitHeight();
                imgW = img.getWidth() * ((fh > 0 ? fh : 400) / ih);
            }
        }
        TranslateTransition slide = new TranslateTransition(Duration.millis(250), standeePane);
        slide.setToX(-imgW);
        slide.setInterpolator(Interpolator.EASE_IN);
        standeeSlide = slide;
        slide.setOnFinished(e -> {
            if (token != standeeToken) {
                // 动画跑完之前内容已经设了下一张立绘：这次收尾作废（否则会把新立绘清掉）
                Logger("DEBUG", "立绘收起动画的收尾已作废（期间换了立绘）");
                return;
            }
            standeePane.setVisible(false);
            standeePane.setTranslateX(0);
            standeePane.getChildren().clear();
            standeeImageView = null;
            currentStandeePath = null;
            currentStandeeWidth = 0;
        });
        slide.play();
    }

    private void crossFadeStandee(String newPath) {
        String resolved = StarveilResourceResolver.resolveTexture(newPath);
        try {
            javafx.scene.image.ImageView newView = com.xiaowu.game.starveil.render.TextureNodeFactory.loadSheetView(resolved, -1, -1);
            if (newView == null || newView.getImage() == null || newView.getImage().isError()) {
                Logger("ERROR", "Failed to load standee image: " + newPath
                        + standeeLoadHint(newPath, resolved));
                return;
            }
            long token = ++standeeToken;
            Logger("DEBUG", "立绘淡入切换: " + currentStandeePath + " → " + newPath);   // 换立绘走这条（滑入只在「从无到有」时走）
            double paneH = standeePaneHeight();
            newView.setPreserveRatio(true);
            applyStandeeStyle(newView, paneH);
            newView.setOpacity(0);
            double sh = standeeFitHeight(paneH);
            currentStandeeWidth = newView.getImage().getWidth() * (sh / newView.getImage().getHeight());
            standeePane.setTranslateX(0);
            standeePane.setVisible(true);
            ImageView oldView = standeeImageView;
            standeePane.getChildren().add(newView);
            FadeTransition fadeOut = new FadeTransition(Duration.millis(250), oldView);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(f -> {
                if (token == standeeToken) {
                    standeePane.getChildren().remove(oldView);
                }
            });
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), newView);
            fadeIn.setToValue(1);
            fadeIn.setOnFinished(f -> {
                if (token != standeeToken) {
                    // 期间又换了立绘：这张不会留在画面上，但也不能让它永远停在不透明 0
                    newView.setOpacity(1);
                    return;
                }
                standeeImageView = newView;
                standeePane.setOpacity(1);
            });
            // 淡入被打断时（例如动画被 stop）兜底：别让图片永远停在「看不见」
            fadeIn.statusProperty().addListener((o, ov, nv) -> {
                if (nv == javafx.animation.Animation.Status.STOPPED && token == standeeToken) {
                    newView.setOpacity(1);
                }
            });
            standeeFade = fadeIn;
            fadeOut.play();
            fadeIn.play();
            currentStandeePath = newPath;
        } catch (Exception ex) {
            Logger("ERROR", "Failed to cross-fade standee: " + ex.getMessage());
        }
    }

    private void removeFromContainer(Pane panel) {
        Platform.runLater(() -> {
            chatContainer.getChildren().remove(panel);
            boolean hasContent = chatContainer.getChildren().stream()
                    .anyMatch(n -> n != overlay && n != historyPanel && n != standeePane);
            if (!hasContent) cleanupAfterHide();
        });
    }

    public CompletableFuture<String> showInputDialog(String prompt) {
        return showInputDialog(prompt, null);
    }

    public CompletableFuture<String> showInputDialog(String prompt, String simulatedText) {
        currentInputFuture = new CompletableFuture<>();
        CompletableFuture<String> future = currentInputFuture;
        Platform.runLater(() -> {
            updateContainerSize();
            cleanupCurrentDialog();
            dialogActive.set(true);
            overlay.setVisible(true);
            overlay.setOpacity(1);
            overlay.setMouseTransparent(false);
            chatContainer.setVisible(true);
            chatContainer.setMouseTransparent(false);
            VBox inputPanel = dialogUI.createInputPanel(prompt, future, simulatedText);
            chatContainer.getChildren().add(inputPanel);
            future.whenComplete((r, ex) -> {
                removeFromContainer(inputPanel);
                currentInputField = null;
            });
            Platform.runLater(() -> {
                javafx.scene.control.TextField inputField = (javafx.scene.control.TextField) inputPanel.getChildren().get(1);
                currentInputField = inputField;
                if (advanceLocked()) {
                    currentInputField.setDisable(true);
                } else {
                    inputField.requestFocus();
                }
            });
        });
        return future;
    }

    public CompletableFuture<String> showDateInputDialog(String prompt) {
        currentDateFuture = new CompletableFuture<>();
        CompletableFuture<String> future = currentDateFuture;
        Platform.runLater(() -> {
            updateContainerSize();
            cleanupCurrentDialog();
            dialogActive.set(true);
            overlay.setVisible(true);
            overlay.setOpacity(1);
            overlay.setMouseTransparent(false);
            chatContainer.setVisible(true);
            chatContainer.setMouseTransparent(false);
            VBox datePanel = dialogUI.createDateInputPanel(prompt, future);
            chatContainer.getChildren().add(datePanel);
            captureInputFields(datePanel);
            future.whenComplete((r, ex) -> {
                removeFromContainer(datePanel);
                currentInputField = null;
            });
        });
        return future;
    }

    public CompletableFuture<Integer> showChoiceDialog(String speaker, String prompt, List<String> choices) {
        currentChoiceFuture = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (prompt != null && !prompt.trim().isEmpty()) {
                pendingChoicePrompt = prompt;
                pendingChoices = choices;
                pendingChoiceDialogFuture = currentChoiceFuture.thenCompose(result -> {
                    pendingChoicePrompt = null;
                    pendingChoices = null;
                    pendingChoiceDialogFuture = null;
                    return CompletableFuture.completedFuture(null);
                });
                showDialog(speaker, prompt);
            } else {
                showChoiceButtons(choices);
            }
        });
        return currentChoiceFuture;
    }

    private void showChoiceButtons(List<String> choices) {
        VBox choicePanel = new VBox(12);
        choicePanel.setAlignment(javafx.geometry.Pos.CENTER);
        choicePanel.setPadding(new javafx.geometry.Insets(15));
        choicePanel.setMaxWidth(600);
        choicePanel.setStyle("-fx-background-color:transparent;");
        choiceBox = new VBox(8);
        choiceBox.setAlignment(javafx.geometry.Pos.CENTER);
        choiceBox.setFillWidth(true);

        CompletableFuture<Integer> future = currentChoiceFuture;
        dialogUI.showChoiceButtons(choices, choiceBox, future);

        choicePanel.getChildren().add(choiceBox);
        StackPane.setAlignment(choicePanel, javafx.geometry.Pos.BOTTOM_CENTER);
        StackPane.setMargin(choicePanel, new javafx.geometry.Insets(0, 0, 100, 0));
        chatContainer.getChildren().add(choicePanel);
        VBox dialogToClean = currentDialog;
        future.whenComplete((r, ex) -> {
            removeFromContainer(choicePanel);
            if (dialogToClean != null) removeFromContainer(dialogToClean);
        });
    }

    /**
     * 对话层当前是否应当忽略推进类输入。
     * 游戏暂停、教程锁定、以及外部模态（断网提示）都会冻结推进，但互相独立、互不解除。
     */
    private boolean advanceLocked() {
        return pausedByGame || storyAdvanceLocked || !advanceLocks.isEmpty();
    }

    /**
     * 按来源锁住 / 解锁剧情推进。
     *
     * <p>给「盖住整个画面、期间不许翻页」的外部模态用（现在只有断网提示）。
     * 锁住时点击与空格/回车都不翻页，其它快捷键也不再由对话框处理；
     * 多个来源各自加锁互不影响，见 {@link #advanceLocks}。
     *
     * @param owner 来源标识（任意对象，调用方自己持有同一个实例来解锁）
     */
    public void lockAdvance(Object owner) {
        if (owner != null && advanceLocks.add(owner)) {
            Logger("DEBUG", "剧情推进已锁住（来源 " + owner + "）");
        }
    }

    /** 按来源解锁剧情推进。 */
    public void unlockAdvance(Object owner) {
        if (owner != null && advanceLocks.remove(owner)) {
            Logger("DEBUG", "剧情推进已解锁（来源 " + owner + "）");
        }
    }

    /** 当前是否处于「推进被锁住」状态（含暂停、教程锁、外部模态锁）。 */
    public boolean isAdvanceLocked() {
        return advanceLocked();
    }

    /**
     * 锁住 / 解锁剧情推进。
     *
     * <p>教程的模态阶段（介绍界面元素、弹说明弹窗）期间调用。锁住时点击与
     * 空格/回车都不再翻页，输入框也会被禁用。
     */
    public void setStoryAdvanceLocked(boolean locked) {
        this.storyAdvanceLocked = locked;
    }

    public boolean isStoryAdvanceLocked() {
        return storyAdvanceLocked;
    }

    /**
     * 教程介绍 HUD 元素时，临时收起对话框，让玩家看清底下的 HUD。
     *
     * <p>因为 HUD 现在全程可见（视觉小说模式不再隐藏它），这里只需要收起对话框，
     * 不必像以前那样把 GameUI 显示出来。
     *
     * @return 之前确实处于剧情层（即真的做了切换）
     */
    public boolean handOverToHudForTutorial() {
        boolean wasInStory = chatContainer.isVisible();
        storyLayerHandedOver = wasInStory;
        if (wasInStory) {
            Platform.runLater(() -> {
                chatContainer.setVisible(false);
                chatContainer.setMouseTransparent(true);
            });
        }
        return wasInStory;
    }

    /** 教程介绍完，把画面还给剧情层（仅在确实收起过时才还原）。 */
    public void takeBackFromHudAfterTutorial() {
        if (!storyLayerHandedOver) {
            return;
        }
        storyLayerHandedOver = false;
        Platform.runLater(() -> {
            // 只有剧情确实还在进行时才把对话框翻回来，
            // 否则会把一个空的聊天层（含半透明遮罩）盖在画面上
            if (dialogActive.get()) {
                chatContainer.setVisible(true);
                chatContainer.setMouseTransparent(false);
            }
        });
    }

    private void setupContinueListener(CompletableFuture<Void> future) {
        Logger("DEBUG", "setupContinueListener 被执行");
        cleanupEventListeners();
        // 本次注册的令牌：只有它仍然是最新的一次，才允许执行延迟清理。
        final long token = ++listenerToken;
        EventHandler<MouseEvent> overlayClick = e -> {
            if (advanceLocked()) {
                e.consume();
                return;
            }
            if (historyManager.isHistoryVisible()) {
                e.consume();
                return;
            }
            if (currentDialog != null && !e.getTarget().equals(currentDialog)) {
                handleContinueClick(future);
            }
        };
        EventHandler<MouseEvent> dialogClick = e -> {
            if (advanceLocked()) {
                e.consume();
                return;
            }
            if (historyManager.isHistoryVisible()) {
                e.consume();
                return;
            }
            if (currentDialog != null) handleContinueClick(future);
        };
        currentKeyHandler = e -> {
            // 游戏暂停时冻结：不推进对话，也不处理任何快捷键
            if (advanceLocked()) {
                e.consume();
                return;
            }
            if (historyManager.isHistoryVisible()) return;
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) {
                e.consume();
                if (currentDialog != null) handleContinueClick(future);
            } else if (e.getCode() == KeyCode.ESCAPE) {
                if (historyManager.isHistoryVisible()) {
                    e.consume();
                    historyManager.setHistoryVisible(false);
                }
                // 对话框打开时不接管 ESC：留给 GameInstance 唤起暂停菜单（暂停菜单会盖在对话框上方）
            }
        };
        scrollHandler = e -> {
            if (advanceLocked()) {
                e.consume();
                return;
            }
            if (e.getDeltaY() < 0 && !historyManager.isHistoryVisible() && dialogActive.get()) {
                e.consume();
                historyManager.setHistoryVisible(true);
            }
        };
        if (currentDialog != null) currentDialog.setOnMouseClicked(dialogClick);
        overlay.setOnMouseClicked(overlayClick);
        InputHandler ih = GameInstance.getInputHandlerStatic();
        if (ih != null) {
            ih.onKeyPressed(KeyCode.ENTER, currentKeyHandler);
            ih.onKeyPressed(KeyCode.SPACE, currentKeyHandler);
            // 不向 InputHandler 注册 ESC：避免占用 PAUSE_TOGGLE 计数，让 GameInstance 能唤起暂停
        } else {
            Scene sc = chatContainer.getScene();
            if (sc != null) {
                sc.addEventHandler(KeyEvent.KEY_PRESSED, currentKeyHandler::handle);
            }
        }
        Scene sc2 = chatContainer.getScene();
        if (sc2 != null) {
            sc2.addEventHandler(ScrollEvent.SCROLL, scrollHandler);
            future.whenComplete((r, ex) -> Platform.runLater(() -> {
                // ⚠️ 只有「仍然是最新一次注册」才允许清理。
                //    旧对话框的 future 可能被 releasePendingFutures 提前完成，
                //    它的回调若不加判断地跑 cleanupEventListeners()，
                //    会把新对话框刚绑好的 overlay/dialog 点击处理器清掉 —— 表现为点不动。
                if (listenerToken == token) {
                    cleanupEventListeners();
                }
            }));
        }
    }

    private void handleContinueClick(CompletableFuture<Void> future) {
        long now = System.currentTimeMillis();
        // 进入日志必须放在所有早退分支【之前】：冷却 / 已在处理 / 动画中
        // 都是静默 return，不先记一笔的话，现象和「点击没送达」完全一样。
        Logger("DEBUG", "handleContinueClick 进入: pausedByGame=" + pausedByGame
                + ", storyAdvanceLocked=" + storyAdvanceLocked
                + ", chatVisible=" + chatContainer.isVisible()
                + ", chatTransparent=" + chatContainer.isMouseTransparent()
                + ", overlayTransparent=" + overlay.isMouseTransparent());
        if (now - lastClickTime < CLICK_COOLDOWN) return;
        lastClickTime = now;
        if (isProcessingClick.getAndSet(true)) return;
        try {
            if (historyManager.isHistoryVisible()) return;

            if (pendingChoicePrompt != null) {
                handleChoiceDialogContinue();
                return;
            }

            if (animationController.isAnimating()) {
                HBox prompt = currentDialog != null ? findPromptContent(currentDialog) : null;
                animationController.skipAnimation(dialogUI.getDialogText(), prompt);
                return;
            }

            Logger("DEBUG", "handleContinueClick: moreParts=" +
                    (pendingMessageParts != null ? pendingMessagePartIndex + "/" + pendingMessageParts.length : "null") +
                    ", isAnimating=" + animationController.isAnimating() +
                    ", dialog=" + (currentDialog != null ? "ok" : "null"));

            if (pendingMessageParts != null && pendingMessagePartIndex < pendingMessageParts.length - 1) {
                while (pendingMessagePartIndex < pendingMessageParts.length - 1) {
                    pendingMessagePartIndex++;
                    String nextPart = pendingMessageParts[pendingMessagePartIndex];
                    Logger("DEBUG", "morePart[" + pendingMessagePartIndex + "]='" + nextPart + "'");
                    if (!nextPart.isEmpty()) {
                        appendTextToDialog(nextPart);
                        break;
                    }
                }
                return;
            }

            pendingMessageParts = null;
            hideCurrentDialog(() -> future.complete(null));
        } finally {
            isProcessingClick.set(false);
        }
    }

    private void appendTextToDialog(String text) {
        TextFlow textFlow = dialogUI.getDialogText();
        Logger("DEBUG", "appendTextToDialog: text='" + text + "', textFlow=" + (textFlow != null ? "ok" : "null"));
        if (textFlow == null) return;
        VBox dialog = dialogUI.getCurrentDialog();
        Logger("DEBUG", "appendTextToDialog: dialog=" + (dialog != null ? "ok" : "null"));
        if (dialog == null) return;

        HBox promptContent = findPromptContent(dialog);
        Logger("DEBUG", "appendTextToDialog: promptContent=" + (promptContent != null ? "ok" : "null"));
        historyManager.appendToLastEntry(text);

        animationController.resumeTextAnimation(text, textFlow, promptContent, null);
    }

    private HBox findPromptContent(VBox dialog) {
        if (dialog.getChildren().isEmpty()) return null;
        Node last = dialog.getChildren().get(dialog.getChildren().size() - 1);
        if (last instanceof HBox pc && !pc.getChildren().isEmpty()) {
            Node inner = pc.getChildren().get(0);
            if (inner instanceof HBox content) return content;
        }
        return null;
    }

    private void handleChoiceDialogContinue() {
        if (pendingChoicePrompt != null && pendingChoices != null) {
            showChoiceButtons(pendingChoices);
            if (currentDialog != null) {
                currentDialog.setMouseTransparent(true);
                dialogUI.updateDialogPromptToSelect();
            }
        }
    }

    private void cleanupEventListeners() {
        InputHandler ih = GameInstance.getInputHandlerStatic();
        if (ih != null) {
            if (currentKeyHandler != null) {
                ih.removeKeyPressedHandler(KeyCode.ENTER.ordinal(), currentKeyHandler);
                ih.removeKeyPressedHandler(KeyCode.SPACE.ordinal(), currentKeyHandler);
            }
        } else {
            Scene sc = chatContainer.getScene();
            if (sc != null && currentKeyHandler != null) {
                sc.removeEventHandler(KeyEvent.KEY_PRESSED, currentKeyHandler::handle);
            }
        }
        Scene sc2 = chatContainer.getScene();
        if (sc2 != null && scrollHandler != null) {
            sc2.removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        }
        if (currentDialog != null) currentDialog.setOnMouseClicked(null);
        overlay.setOnMouseClicked(null);
    }

    private void hideCurrentDialog(Runnable onDone) {
        if (currentDialog == null || isHiding.get()) {
            if (onDone != null) onDone.run();
            return;
        }
        if (!chatContainer.getChildren().contains(currentDialog)) {
            currentDialog = null;
            isHiding.set(false);
            if (onDone != null) onDone.run();
            return;
        }
        isHiding.set(true);
        animationController.stopAnimation();
        stopCurrentVoice();
        if (standeeImageView != null) {
            FadeTransition sf = new FadeTransition(Duration.millis(200), standeeImageView);
            sf.setToValue(0);
            sf.play();
        }
        Pane current = currentDialog;
        FadeTransition ft = new FadeTransition(Duration.millis(200), current);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setInterpolator(javafx.animation.Interpolator.EASE_IN);
        ft.setOnFinished(e -> {
            chatContainer.getChildren().remove(current);
            currentDialog = null;
            boolean hasOther = chatContainer.getChildren().stream()
                    .anyMatch(n -> n != overlay && n != historyPanel && n != storyImagePane && n != standeePane);
            if (!hasOther) cleanupAfterHide();
            isHiding.set(false);
            if (onDone != null) onDone.run();
        });
        ft.play();
    }

    private void clearDialogPanels() {
        chatContainer.getChildren().removeIf(n -> n != overlay && n != historyPanel && n != storyImagePane);
    }

    private void cleanupAfterHide() {
        if (!historyManager.isHistoryVisible()) {
            overlay.setVisible(false);
            overlay.setMouseTransparent(true);
        }
        currentDialog = null;
        boolean hasVis = chatContainer.getChildren().stream()
                .anyMatch(n -> n != overlay && n != historyPanel && n.isVisible());
        if (!hasVis && !historyManager.isHistoryVisible()) {
            chatContainer.setVisible(false);
            chatContainer.setMouseTransparent(true);
            dialogActive.set(false);
        }
    }

    private void cleanupCurrentDialog() {
        cleanupEventListeners();
    }

    private void playVoice(String path) {
        try {
            AudioManager.getInstance().playSound(path, false);
        } catch (Exception ex) {
            Logger("WARNING", "播放配音失败: " + ex.getMessage());
        }
    }

    private void stopCurrentVoice() {
        if (currentVoicePath != null && !currentVoicePath.trim().isEmpty()) {
            try {
                AudioManager.getInstance().stopSound(currentVoicePath);
            } catch (Exception ex) {
            }
            currentVoicePath = null;
        }
    }

    public StackPane getChatContainer() {
        return chatContainer;
    }

    /** 仅测试用：立绘层（布局回归测试要能注入「图片在、但立绘不在画面上」这种状态）。 */
    StackPane standeePaneForTest() {
        return standeePane;
    }

    public StackPane getStoryImagePane() {
        return storyImagePane;
    }

    public boolean isDialogActive() {
        return dialogActive.get();
    }

    public BooleanProperty dialogActiveProperty() {
        return dialogActive;
    }

    /** 是否因游戏暂停而冻结。 */
    public boolean isPausedByGame() {
        return pausedByGame;
    }

    /** 游戏暂停/恢复时冻结或解冻对话框（推进、输入确认与光标）。 */
    public void setPausedByGame(boolean paused) {
        pausedByGame = paused;
        if (paused && !Platform.isFxApplicationThread()) {
            boolean p = paused;
            Platform.runLater(() -> applyInputPause(p));
        } else {
            applyInputPause(paused);
        }
    }

    private void applyInputPause(boolean paused) {
        if (currentInputField == null) return;
        currentInputField.setDisable(paused);
        if (paused) {
            // 移开焦点，停止输入光标闪烁
            currentInputField.getParent().requestFocus();
        } else {
            currentInputField.requestFocus();
        }
    }

    /** 找到面板里的第一个输入框并记录（用于暂停时冻结）。 */
    private void captureInputFields(Pane panel) {
        if (panel == null) return;
        java.util.ArrayDeque<javafx.scene.Node> queue = new java.util.ArrayDeque<>();
        queue.add(panel);
        while (!queue.isEmpty()) {
            javafx.scene.Node n = queue.poll();
            if (n instanceof javafx.scene.control.TextField tf) {
                currentInputField = tf;
                return;
            }
            if (n instanceof javafx.scene.Parent p) {
                queue.addAll(p.getChildrenUnmodifiable());
            }
        }
    }

    public void setRunningSequence(DialogSequence sequence) {
        this.runningSequence = sequence;
    }

    /**
     * 切换立绘，不显示对话（供 {@code StoryScript.standee} 使用）。
     *
     * @param path 立绘路径；null 或空串表示收起立绘
     */
    public void setStandee(String path) {
        // 不带样式的老签名：沿用当前样式
        setStandee(path, standeeStyle);
    }

    /**
     * 切换立绘并指定显示样式（供 {@code StoryScript.standee} 使用）。
     *
     * <p>因为是同一个立绘时只改样式不会重新淡入，所以「把同一张立绘挪个位置」也走这里。
     *
     * @param path  立绘路径；null 或空串表示收起立绘
     * @param style 立绘样式（偏移 + 缩放），null 表示回到默认
     */
    public void setStandee(String path, StandeeStyle style) {
        Runnable task = () -> {
            this.standeeStyle = style == null ? StandeeStyle.standard() : style;
            if (path == null || path.isEmpty()) {
                updateStandee("");
                return;
            }
            updateStandee(path);
        };
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /** 记录当前正在执行的章节脚本（调试窗口展示用）。 */
    public void setActiveStory(Object script) {
        this.activeStory = script;
    }

    /** 当前正在执行的章节脚本，可能是 null。 */
    public Object getActiveStory() {
        return activeStory;
    }

    /**
     * 复位所有「跨局」状态。
     *
     * <p>{@code ChatManager} 是单例，会跨 {@code GameInstance} 存活（新游戏 / 读档 /
     * 回主菜单）。上一局残留的冻结标志会让<b>新档的对话框点不动</b> ——
     * 因为推进判定是 {@code advanceLocked() = pausedByGame || storyAdvanceLocked}。
     *
     * <p>最容易踩的路径：从暂停菜单进设置、再点「退出」回主菜单。这条路只调用了
     * {@code DialogCleanup.closeAllDialogs()}（那是清理 Win32 原生弹窗的），
     * 不会走 {@code resumeGame()}，于是 {@code pausedByGame} 一直留在 {@code true}。
     *
     * <p>与 {@link #forceCloseAll()} 的分工：本方法只动标志位，<b>不碰界面节点、
     * 不要求 JavaFX 线程</b>，可以在任何时刻同步调用；{@code forceCloseAll()}
     * 负责连界面一起收掉，并在内部复用本方法。
     */
    public void resetCrossGameState() {
        // 先自增世代号：让此前排队中的 forceCloseAll 失效，不再影响这一局。
        crossGameGeneration.incrementAndGet();
        setPausedByGame(false);
        storyAdvanceLocked = false;
        storyLayerHandedOver = false;
        dialogActive.set(false);
        isProcessingClick.set(false);
        isHiding.set(false);
        lastClickTime = 0;
        pendingChoicePrompt = null;
        pendingChoices = null;
        pendingChoiceDialogFuture = null;
        pendingMessageParts = null;
        pendingMessagePartIndex = 0;
        // 上一个存档遗留的输入框引用也必须丢掉：它已经不在场景里了，
        // 留着会让 applyInputPause 去操作一个已脱离场景的节点。
        currentInputField = null;
    }

    /**
     * 强制关闭所有对话界面，并 <b>释放所有等待中的剧情</b>。
     *
     * <p>这一点很关键：剧情脚本在剧情线程上阻塞等待玩家操作，
     * 如果只是把界面清掉而不完成 future，脚本会永远卡住、操作权也无法归还。
     */
    public void forceCloseAll() {
        // ⚠️ 已经在 FX 线程上时必须【同步】执行完。
        //    退出按钮的回调本就在 FX 线程上，若这里改用 runLater，
        //    这次清理会排到「回主菜单 → 开新档」之后才跑，于是它在新档里
        //    释放掉刚显示的对话框 future、再把 chatContainer/overlay 置为
        //    mouseTransparent —— 表现为「对话框还在但点不动」。
        if (Platform.isFxApplicationThread()) {
            teardownDialogs();
            return;
        }
        // 后台线程调用时仍需转投 FX 线程，但要带世代号：
        // 排队期间如果已经开了新档，这次清理属于上一局，直接丢弃。
        long generation = crossGameGeneration.get();
        Platform.runLater(() -> {
            if (crossGameGeneration.get() != generation) {
                Logger("DEBUG", "丢弃过期的 forceCloseAll（已开新档）");
                return;
            }
            teardownDialogs();
        });
    }

    /** {@link #forceCloseAll()} 的实际清理体，必须在 FX 线程上执行。 */
    private void teardownDialogs() {
        historyManager.setHistoryVisible(false);

        // ⚠️ 跨局状态必须一并清掉（含 pausedByGame / storyAdvanceLocked，详见该方法的注释）。
        resetCrossGameState();

        stopCurrentVoice();
        releasePendingFutures();
        standeeToken++;   // 作废在飞的立绘动画，防止它回头清算新立绘
        standeePane.setVisible(false);
        standeePane.getChildren().clear();
        standeeImageView = null;
        currentStandeePath = null;
        currentStandeeWidth = 0;
        chatContainer.getChildren().removeIf(n -> n != overlay && n != historyPanel && n != storyImagePane && n != standeePane);
        chatContainer.setVisible(false);
        chatContainer.setMouseTransparent(true);
        dialogActive.set(false);
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);
    }

    /**
     * 完成所有「等待玩家操作」的 future，让被阻塞的剧情线程能继续退出。
     */
    private void releasePendingFutures() {
        if (currentInputFuture != null && !currentInputFuture.isDone()) currentInputFuture.complete(null);
        if (currentChoiceFuture != null && !currentChoiceFuture.isDone()) currentChoiceFuture.complete(-1);
        if (currentDateFuture != null && !currentDateFuture.isDone()) currentDateFuture.complete(null);
        if (currentDialogFuture != null && !currentDialogFuture.isDone()) currentDialogFuture.complete(null);
        if (currentStoryImageFuture != null && !currentStoryImageFuture.isDone()) currentStoryImageFuture.complete(null);
        if (runningSequence != null) {
            runningSequence.abort();
            runningSequence = null;
        }
    }

    /**
     * 进入视觉小说模式：铺开对话框。
     *
     * <p>这里【不】隐藏 HUD —— chatContainer 挂在逻辑画布上，本身就盖在视口之上，
     * HUD 会被它自然覆盖。原来的 hide/show 会让 HUD 闪出闪入，
     * 也会把教程要高亮的 UI 元素从场景里摘掉。
     */
    public void blockGameLayer() {
        Platform.runLater(() -> {
            chatContainer.setVisible(true);
            chatContainer.setMouseTransparent(false);
        });
        // 冻结世界模拟（不弹任何界面）。教程里 awaitTutorial() 会走 giveControl()
        // → unblockGameLayer()，届时自动恢复，练习阶段照常能走动。
        setWorldStoryPaused(true);
        com.xiaowu.game.starveil.input.InputHandler ih =
                com.xiaowu.game.starveil.game.state.GameInstance.getInputHandlerStatic();
        if (ih != null) ih.lockControls(InputHandler.LOCK_STORY);
    }

    /** 退出视觉小说模式。HUD 全程都在，不需要恢复显示。 */
    public void unblockGameLayer() {
        setWorldStoryPaused(false);
        com.xiaowu.game.starveil.input.InputHandler ih =
                com.xiaowu.game.starveil.game.state.GameInstance.getInputHandlerStatic();
        if (ih != null) ih.unlockControls(InputHandler.LOCK_STORY);
    }

    /** 切换 ECS 世界的剧情暂停状态。世界可能尚未创建（视觉小说模式），故容错。 */
    private void setWorldStoryPaused(boolean paused) {
        com.xiaowu.game.starveil.game.ecs.World world =
                com.xiaowu.game.starveil.game.state.GameInstance.getEcsWorld();
        if (world != null) {
            world.setStoryPaused(paused);
        }
    }

    public CompletableFuture<Void> showStoryImage(String path) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        currentStoryImageFuture = future;
        future.whenComplete((r, ex) -> {
            if (currentStoryImageFuture == future) currentStoryImageFuture = null;
        });
        Platform.runLater(() -> {
            clearDialogPanels();
            // 剧情图布局在逻辑画布坐标系内（chatContainer 即逻辑画布区域）
            double w = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
            double h = chatContainer.getHeight() > 0 ? chatContainer.getHeight() : 1080;
            overlay.setVisible(true);
            overlay.setOpacity(0);
            overlay.setMouseTransparent(true);
            chatContainer.setVisible(true);
            chatContainer.setMouseTransparent(true);
            Runnable doShow = () -> {
                javafx.scene.image.Image img = new javafx.scene.image.Image(
                        ResourceResolver.getResourceAsStream(
                                StarveilResourceResolver.resolveTexture(path)));
                storyImageView = new javafx.scene.image.ImageView(img);

                // 等比缩放到「铺满」而不是「装下」：
                // 用 setPreserveRatio(true) + 同时设 fitWidth/fitHeight 是「装下」，
                // 图片比例和画布不一致时四周就会留出黑边。这里按较大的那个比例缩放，
                // 多出来的部分交给逻辑画布的 clip 裁掉 —— 视觉小说里这就是标准做法。
                double imgW = img.getWidth() > 0 ? img.getWidth() : w;
                double imgH = img.getHeight() > 0 ? img.getHeight() : h;
                double scale = Math.max(w / imgW, h / imgH);
                double drawW = imgW * scale;
                double drawH = imgH * scale;
                storyImageView.setPreserveRatio(false);
                storyImageView.setFitWidth(drawW);
                storyImageView.setFitHeight(drawH);
                // 居中（超出部分由画布 clip 裁掉）
                storyImageView.setLayoutX((w - drawW) / 2);
                storyImageView.setLayoutY((h - drawH) / 2);

                storyImagePane.getChildren().setAll(storyImageView);
                storyImagePane.setVisible(true);
                storyImagePane.setOpacity(0);
                javafx.animation.FadeTransition ftIn =
                        new javafx.animation.FadeTransition(
                                javafx.util.Duration.millis(800), storyImagePane);
                ftIn.setFromValue(0);
                ftIn.setToValue(1);
                ftIn.setOnFinished(e -> future.complete(null));
                ftIn.play();
            };
            if (storyImagePane.isVisible() && !storyImagePane.getChildren().isEmpty()) {
                javafx.animation.FadeTransition ftOut =
                        new javafx.animation.FadeTransition(
                                javafx.util.Duration.millis(600), storyImagePane);
                ftOut.setFromValue(storyImagePane.getOpacity());
                ftOut.setToValue(0);
                ftOut.setOnFinished(e -> {
                    storyImagePane.getChildren().clear();
                    storyImagePane.setOpacity(1);
                    doShow.run();
                });
                ftOut.play();
            } else {
                doShow.run();
            }
        });
        return future;
    }

    public CompletableFuture<Void> hideStoryImage() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        currentStoryImageFuture = future;
        future.whenComplete((r, ex) -> {
            if (currentStoryImageFuture == future) currentStoryImageFuture = null;
        });
        Platform.runLater(() -> {
            clearDialogPanels();
            javafx.animation.FadeTransition ft =
                    new javafx.animation.FadeTransition(
                            javafx.util.Duration.millis(600), storyImagePane);
            ft.setFromValue(storyImagePane.getOpacity());
            ft.setToValue(0);
            ft.setOnFinished(e -> {
                storyImagePane.setVisible(false);
                storyImagePane.getChildren().clear();
                storyImagePane.setOpacity(1);
                overlay.setOpacity(1);
                storyImageView = null;
                future.complete(null);
            });
            ft.play();
        });
        return future;
    }

    public DialogSequence createSequence() {
        String callerClass = null;
        String callerMethod = "";
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (int i = 2; i < st.length; i++) {
                String cls = st[i].getClassName();
                if (cls.startsWith("com.xiaowu.game.ui.chat.ChatManager")) continue;
                callerClass = cls.replaceAll("\\$\\d+", "").replaceAll("\\$lambda\\$.*", "");
                break;
            }
        } catch (Exception ignore) {
        }
        if (callerClass == null) callerClass = "Unknown";

        DialogSequence seq = new DialogSequence(callerClass, callerMethod, this);
        return seq;
    }

    private void updateContainerSize() {
        Platform.runLater(() -> {
            GameManager gameManager = GameManager.getInstance();
            if (gameManager != null && gameManager.getPrimaryStage() != null) {
                Scene scene = gameManager.getPrimaryStage().getScene();
                if (scene != null) {
                    double width = scene.getWidth();
                    double height = scene.getHeight();

                    chatContainer.setPrefSize(width, height);
                    overlay.setPrefSize(width, height);

                    if (historyPanel != null) {
                        historyPanel.setPrefWidth(width * 0.8);
                        historyPanel.setMaxHeight(height * 0.7);
                    }
                }
            }
        });
    }

    public void setAnchor(String name, int stepIndex, int stepsSize) {
        anchors.put(name, new Anchor(stepIndex, stepsSize));
    }

    public void setAnchor(String name) {
        DialogSequence seq = runningSequence;
        if (seq == null) return;
        setAnchor(name, seq.currentStep, seq.steps.size());
    }

    public void returnAnchor(String name) {
        Anchor anchor = anchors.get(name);
        if (anchor == null) return;
        DialogSequence seq = runningSequence;
        if (seq == null) return;
        seq.steps.removeIf(s -> s.insertedByChain);
        DialogSequence.DialogStep anchored = seq.steps.get(anchor.stepIndex);
        anchored.result = null;
        anchored.skipUI = false;
        jumpToStep(anchor.stepIndex);
    }

    public void jumpToStep(int index) {
        DialogSequence seq = runningSequence;
        if (seq == null) return;
        if (index < 0 || index > seq.steps.size()) return;
        seq.jumpRequested = true;
        seq.currentStep = index;
        Platform.runLater(seq::executeNextStep);
    }

    public List<DialogSequence.DialogStep> getSequenceDebugInfo() {
        if (runningSequence == null) return Collections.emptyList();
        return Collections.unmodifiableList(runningSequence.steps);
    }
}
