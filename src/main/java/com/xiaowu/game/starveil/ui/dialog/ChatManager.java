package com.xiaowu.game.starveil.ui.dialog;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

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

        standeePane = new StackPane();
        standeePane.setVisible(false);
        standeePane.setMouseTransparent(true);
        standeePane.setPickOnBounds(false);
        standeePane.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT);

        storyImagePane = new StackPane();
        storyImagePane.setVisible(false);
        storyImagePane.setMouseTransparent(true);
        storyImagePane.setPickOnBounds(false);

        overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.4);");
        overlay.setVisible(false);
        overlay.setMouseTransparent(true);

        historyManager = new HistoryManager(chatContainer, overlay);
        historyPanel = historyManager.buildHistoryPanel();

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
            double oldStandeeEdge = standeeRightEdge();
            String oldStandeePath = currentStandeePath;
            boolean slideIn = standeeImagePath != null && !standeeImagePath.isEmpty()
                    && oldStandeePath == null;
            boolean slideOut = standeeImagePath != null && standeeImagePath.isEmpty()
                    && oldStandeeEdge > 0;
            updateStandee(standeeImagePath);
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
        double vw = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
        double gap = 16;
        // 立绘可能被内容挪过位置/放大：按它实际占到的右边界让位。
        // 上限 80% 画布宽：立绘特别宽（或放大很多）时也要给对话框留出可读的宽度。
        double sw = Math.min(standeeRightEdge(), vw * 0.8);
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
        double sw = Math.min(standeeRightEdge(), vw * 0.8);
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
        timeline.play();
    }

    private void animateDialogForStandeeSlideOut(double oldW) {
        if (currentDialog == null || oldW <= 0) {
            layoutDialogForStandee();
            return;
        }
        double vw = chatContainer.getWidth() > 0 ? chatContainer.getWidth() : 1920;
        double gap = 16;
        oldW = Math.min(oldW, vw * 0.8);
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
        timeline.play();
    }

    /**
     * 立绘加载失败时的可读诊断：写错路径是常见坑，别只丢一句「加载失败」。
     *
     * <p>路径形如 {@code starveil:textures/character/normal/relaxed.png}：
     * 第一段是资源类型（textures / sounds / fonts / data / lang），末尾要带文件后缀。
     */
    private String standeeLoadHint(String path) {
        String resolved = com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver.resolve(path);
        if (resolved == null) {
            return "（这个路径解析不出来：第一段应该是 textures / sounds / fonts / data / lang，"
                    + "正确写法形如 starveil:textures/character/normal/relaxed.png）";
        }
        boolean exists = com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver.exists(path);
        return "（解析为 " + resolved + "：" + (exists ? "文件在，但读不出图片内容" : "找不到该文件")
                + "；路径要带后缀，形如 starveil:textures/character/normal/relaxed.png）";
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

    /** 当前画布高度（布局还没跑过时按逻辑画布 1080 算）。 */
    private double standeePaneHeight() {
        return chatContainer.getHeight() > 0 ? chatContainer.getHeight() : 1080;
    }

    /** 把图片按当前样式摆好（尺寸 + 偏移）。 */
    private void applyStandeeStyle(ImageView view, double paneH) {
        if (view == null) return;
        view.setFitHeight(standeeFitHeight(paneH));
        view.setTranslateX(standeeStyle.offsetX());
        view.setTranslateY(standeeStyle.offsetY());
    }

    /** 立绘在画布上占到的右边界（供对话框让位；被挪到画布外时算 0）。 */
    private double standeeRightEdge() {
        if (standeeImageView == null || standeeImageView.getImage() == null) {
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
        try {
            javafx.scene.image.ImageView view = com.xiaowu.game.starveil.render.TextureNodeFactory.loadSheetView(path, -1, -1);
            if (view == null || view.getImage() == null || view.getImage().isError()) {
                Logger("ERROR", "Failed to load standee image: " + path + standeeLoadHint(path));
                return;
            }
            double paneH = standeePaneHeight();
            standeeImageView = view;
            standeeImageView.setPreserveRatio(true);
            applyStandeeStyle(standeeImageView, paneH);
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
            slide.play();
            currentStandeePath = path;
        } catch (Exception e) {
            Logger("ERROR", "Failed to load standee: " + path + " - " + e.getMessage());
        }
    }

    private void slideOutStandee() {
        if (!standeePane.isVisible()) {
            currentStandeePath = null;
            currentStandeeWidth = 0;
            return;
        }
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
        slide.setOnFinished(e -> {
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
        try {
            javafx.scene.image.ImageView newView = com.xiaowu.game.starveil.render.TextureNodeFactory.loadSheetView(newPath, -1, -1);
            if (newView == null || newView.getImage() == null || newView.getImage().isError()) {
                Logger("ERROR", "Failed to load standee image: " + newPath + standeeLoadHint(newPath));
                return;
            }
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
            fadeOut.setOnFinished(f -> standeePane.getChildren().remove(oldView));
            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), newView);
            fadeIn.setToValue(1);
            fadeIn.setOnFinished(f -> {
                standeeImageView = newView;
                standeePane.setOpacity(1);
            });
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
                        ResourceResolver.getResourceAsStream(path));
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
