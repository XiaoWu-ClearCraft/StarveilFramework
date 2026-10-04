package com.xiaowu.game.starveil.ui.core;
import com.xiaowu.game.starveil.infrastructure.Fonts;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.render.engine.ColorDef;
import com.xiaowu.game.starveil.render.engine.RenderEngineProvider;
import com.xiaowu.game.starveil.render.engine.TextSegment;
import com.xiaowu.game.starveil.ui.screen.CreditsManager;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * 游戏UI类 - 显示血条、魔力条、体力条、渐变背景和文本消息
 */
public class GameUI {
    private static GameUI instance;

    private Pane uiContainer;
    private Rectangle leftGradient;
    private Rectangle rightGradient;
    private ProgressBar healthBar;
    private ProgressBar staminaBar;
    private ProgressBar magicBar;
    private TextFlow messageDisplay;
    private Pane messageContainer;

    // 受击闪烁效果（常亮模式 + 平滑过渡）
    private Rectangle hitFlashOverlay;
    private double hitFlashCurrentAlpha = 0.0;  // 当前实际alpha（平滑过渡用）
    private double hitFlashDamageSpike = 0.0;   // 受击瞬间的额外alpha峰值

    // 手上物品显示
    private Pane handSlotPane;
    private ImageView handSlotIcon;
    private Label handSlotName;

    private Queue<String> messageQueue;
    private boolean isDisplayingMessage;
    private SequentialTransition currentMessageAnimation;
    private String currentMessage;
    private FadeTransition currentHideTransition;

    private Font messageFont;  // 消息字体

    /** 尺寸监听器只挂一次 —— attachToGame 会被多次调用，重复挂会累积。 */
    private boolean uiLayoutListenersAttached = false;

    private GameUI() {
        messageQueue = new LinkedList<>();
        isDisplayingMessage = false;
        initializeUI();
    }

    public static GameUI getInstance() {
        if (instance == null) {
            instance = new GameUI();
        }
        return instance;
    }

    /**
     * 初始化UI组件
     */
    private void initializeUI() {
        uiContainer = new Pane();
        uiContainer.setMouseTransparent(true);
        uiContainer.setOpacity(0);

        loadMessageFont();
        createGradientBackgrounds();
        createHealthBar();
        createStaminaBar();
        createMagicBar();
        createHitFlashOverlay();
        createMessageDisplay();
        createHandSlotDisplay();
        refreshMagicVisibility();

        LoggerManager.Logger("DEBUG", "GameUI 初始化完成");
    }

    private void loadMessageFont() {
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream("starveil:fonts/xiaolai-sc-regular.ttf")) {
            if (is != null) {
                messageFont = Font.loadFont(is, 24);
                LoggerManager.Logger("DEBUG", "已加载自定义字体: " + messageFont.getFamily());
            } else {
                messageFont = Font.font(24);
                LoggerManager.Logger("WARN", "无法加载自定义字体，使用系统字体");
            }
        } catch (Exception e) {
            messageFont = Font.font(24);
            LoggerManager.Logger("WARN", "加载字体失败: " + e.getMessage());
        }
    }

    private void createGradientBackgrounds() {
        leftGradient = new Rectangle();
        leftGradient.setWidth(350);
        leftGradient.setHeight(280);
        RadialGradient leftRadial = new RadialGradient(
                0, 0, 0, 1, 1.2, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 182, 193, 0.5)),
                new Stop(0.25, Color.rgb(255, 182, 193, 0.15)),
                new Stop(0.5, Color.rgb(255, 182, 193, 0.03)),
                new Stop(0.7, Color.rgb(255, 182, 193, 0.005)),
                new Stop(0.85, Color.rgb(255, 182, 193, 0.001)),
                new Stop(1, Color.TRANSPARENT)
        );
        leftGradient.setFill(leftRadial);
        leftGradient.setLayoutX(0);
        leftGradient.setLayoutY(0);

        rightGradient = new Rectangle();
        rightGradient.setWidth(350);
        rightGradient.setHeight(280);
        RadialGradient rightRadial = new RadialGradient(
                0, 0, 1, 1, 1.2, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 182, 193, 0.5)),
                new Stop(0.25, Color.rgb(255, 182, 193, 0.15)),
                new Stop(0.5, Color.rgb(255, 182, 193, 0.03)),
                new Stop(0.7, Color.rgb(255, 182, 193, 0.005)),
                new Stop(0.85, Color.rgb(255, 182, 193, 0.001)),
                new Stop(1, Color.TRANSPARENT)
        );
        rightGradient.setFill(rightRadial);
        rightGradient.setLayoutX(0);
        rightGradient.setLayoutY(0);

        uiContainer.getChildren().addAll(leftGradient, rightGradient);
    }

    private void createHealthBar() {
        healthBar = new ProgressBar();
        healthBar.setProgress(1.0);
        healthBar.setPrefWidth(200);
        healthBar.setPrefHeight(20);
        healthBar.setStyle(
                "-fx-accent: #ff6b6b;" +
                "-fx-background-insets: 0;" +
                "-fx-padding: 0;"
        );
        healthBar.setLayoutX(20);
        healthBar.setLayoutY(0);
        uiContainer.getChildren().add(healthBar);
    }

    private void createStaminaBar() {
        staminaBar = new ProgressBar();
        staminaBar.setProgress(1.0);
        staminaBar.setPrefWidth(200);
        staminaBar.setPrefHeight(20);
        staminaBar.setStyle(
                "-fx-accent: #4ade80;" +
                "-fx-background-insets: 0;" +
                "-fx-padding: 0;"
        );
        staminaBar.setLayoutX(20);
        staminaBar.setLayoutY(0);
        uiContainer.getChildren().add(staminaBar);
    }

    private void createMagicBar() {
        magicBar = new ProgressBar();
        magicBar.setProgress(1.0);
        magicBar.setPrefWidth(200);
        magicBar.setPrefHeight(20);
        magicBar.setStyle(
                "-fx-accent: #a855f7;" +
                "-fx-background-insets: 0;" +
                "-fx-padding: 0;"
        );
        magicBar.setLayoutX(20);
        magicBar.setLayoutY(0);
        uiContainer.getChildren().add(magicBar);
    }

    /**
     * 根据法阵（魔力）系统开关刷新魔力条可见性。
     * 关闭时仅隐藏魔力条，体力条保持在生命值与魔力值之间的原槽位，避免中部留白。
     */
    public void refreshMagicVisibility() {
        boolean enabled = com.xiaowu.game.starveil.game.MagicSystem.isEnabled();
        Platform.runLater(() -> {
            if (magicBar != null) {
                magicBar.setVisible(enabled);
                magicBar.setManaged(enabled);
            }
            updateLayout();
        });
    }

    private void createHitFlashOverlay() {
        hitFlashOverlay = new Rectangle();
        hitFlashOverlay.setMouseTransparent(true);
        hitFlashOverlay.setVisible(false);
        uiContainer.getChildren().add(hitFlashOverlay);
    }

    private void createMessageDisplay() {
        messageContainer = new Pane();
        messageDisplay = new TextFlow();
        messageContainer.getChildren().add(messageDisplay);
        messageContainer.setLayoutX(0);
        messageContainer.setLayoutY(0);
        uiContainer.getChildren().add(messageContainer);
    }

    private void createHandSlotDisplay() {
        // 外层VBox：图标在上，名称在下居中
        VBox container = new VBox(2);
        container.setAlignment(Pos.CENTER);

        // 图标槽位（背景 + 图标）
        StackPane slotPane = new StackPane();
        slotPane.setPrefSize(56, 56);
        slotPane.setMinSize(56, 56);
        slotPane.setMaxSize(56, 56);

        Rectangle handBg = new Rectangle(56, 56);
        handBg.setFill(javafx.scene.paint.Color.rgb(30, 30, 30, 0.8));
        handBg.setStroke(javafx.scene.paint.Color.rgb(120, 120, 120));
        handBg.setStrokeWidth(1.5);
        handBg.setArcWidth(8);
        handBg.setArcHeight(8);

        handSlotIcon = new ImageView();
        handSlotIcon.setFitWidth(48);
        handSlotIcon.setFitHeight(48);
        handSlotIcon.setPreserveRatio(true);
        handSlotIcon.setVisible(false);

        slotPane.getChildren().addAll(handBg, handSlotIcon);

        // 物品名称（在图标下方居中）
        handSlotName = new Label();
        handSlotName.setTextFill(javafx.scene.paint.Color.rgb(220, 220, 220));
        handSlotName.setStyle("-fx-font-size: 11px; -fx-text-alignment: center;");
        handSlotName.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 11));
        handSlotName.setMaxWidth(80);
        handSlotName.setWrapText(true);
        handSlotName.setVisible(false);
        handSlotName.setAlignment(Pos.CENTER);

        container.getChildren().addAll(slotPane, handSlotName);
        handSlotPane = container;
        uiContainer.getChildren().add(handSlotPane);
    }

    /**
     * 显示 HUD。
     *
     * <p><b>没有世界就不显示</b>：血条、体力条、背包槽、拾取提示全都是围着
     * 「地图上有个玩家」这件事存在的。加载页面期间、纯视觉小说章节里都没有世界，
     * 这类「把 HUD 显示回来」的调用散落在十来个地方（切地图、关设置、读档……），
     * 与其逐个加判断、漏一个就冒出半截 HUD，不如在这里统一拦一道。
     */
    public void show() {
        Platform.runLater(() -> {
            if (!worldLoaded()) {
                LoggerManager.Logger("DEBUG", "当前没有世界，不显示 HUD");
                return;
            }
            if (currentHideTransition != null) {
                currentHideTransition.stop();
                currentHideTransition = null;
            }
            if (CreditsManager.getInstance().isPlaying()) {
                LoggerManager.Logger("DEBUG", "GameUI");
                return;
            }
            uiContainer.setVisible(true);
            uiContainer.setOpacity(0);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(500), uiContainer);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();
        });
    }

    /** 是否已经有一个加载好的世界（没有就不该显示 HUD）。 */
    private static boolean worldLoaded() {
        com.xiaowu.game.starveil.game.state.GameInstance gi =
                com.xiaowu.game.starveil.game.state.GameInstance.getCurrentInstance();
        return gi != null && gi.isWorldLoaded();
    }

    public void hide() {
        Platform.runLater(() -> {
            if (currentHideTransition != null) {
                currentHideTransition.stop();
            }
            currentHideTransition = new FadeTransition(Duration.millis(300), uiContainer);
            currentHideTransition.setFromValue(1);
            currentHideTransition.setToValue(0);
            currentHideTransition.setOnFinished(e -> {
                uiContainer.setVisible(false);
                currentHideTransition = null;
            });
            currentHideTransition.play();
        });
    }

    public void setHealth(double health) {
        Platform.runLater(() -> {
            healthBar.setProgress(Math.max(0.0, Math.min(1.0, health)));
        });
    }

    public void setStamina(double stamina, boolean isExhausted) {
        Platform.runLater(() -> {
            staminaBar.setProgress(Math.max(0.0, Math.min(1.0, stamina)));
            if (isExhausted) {
                staminaBar.setStyle("-fx-accent: #ef4444;-fx-background-insets: 0;-fx-padding: 0;");
            } else {
                staminaBar.setStyle("-fx-accent: #4ade80;-fx-background-insets: 0;-fx-padding: 0;");
            }
        });
    }

    public void setMagic(double magic, boolean isDepleted) {
        Platform.runLater(() -> {
            if (!com.xiaowu.game.starveil.game.MagicSystem.isEnabled()) {
                magicBar.setVisible(false);
                magicBar.setManaged(false);
                return;
            }
            magicBar.setVisible(true);
            magicBar.setManaged(true);
            magicBar.setProgress(Math.max(0.0, Math.min(1.0, magic)));
            if (isDepleted) {
                magicBar.setStyle("-fx-accent: #7c3aed;-fx-background-insets: 0;-fx-padding: 0;");
            } else {
                magicBar.setStyle("-fx-accent: #a855f7;-fx-background-insets: 0;-fx-padding: 0;");
            }
        });
    }

    public void triggerHitFlash() {
        Platform.runLater(() -> {
            // 受击瞬间增加额外 alpha 峰值
            hitFlashDamageSpike = 0.4;
        });
    }

    public void updateHitFlash(double healthPercentage) {
        // 1. 血量基础 alpha（血量越低越高）
        double healthBasedAlpha = (1.0 - healthPercentage) * 0.5;

        // 2. 衰减受击峰值
        hitFlashDamageSpike *= 0.85;
        if (hitFlashDamageSpike < 0.01) hitFlashDamageSpike = 0;

        // 3. 目标 alpha
        double targetAlpha = healthBasedAlpha + hitFlashDamageSpike;

        // 4. 平滑插值（lerp）
        hitFlashCurrentAlpha += (targetAlpha - hitFlashCurrentAlpha) * 0.15;

        // 5. 低于阈值则隐藏
        if (hitFlashCurrentAlpha < 0.01) {
            hitFlashOverlay.setVisible(false);
            return;
        }

        // 6. 显示并更新渐变
        hitFlashOverlay.setVisible(true);
        double a = hitFlashCurrentAlpha;
        RadialGradient flash = new RadialGradient(
                0, 0, 0.5, 0.5, 1.0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.TRANSPARENT),
                new Stop(0.3, Color.rgb(255, 0, 0, a * 0.1)),
                new Stop(0.6, Color.rgb(200, 0, 0, a * 0.4)),
                new Stop(0.85, Color.rgb(180, 0, 0, a * 0.7)),
                new Stop(1.0, Color.rgb(150, 0, 0, a))
        );
        hitFlashOverlay.setFill(flash);
    }

    public void addMessage(String text) {
        // 如果正在显示相同文本，重置计时而非重复入队
        if (isDisplayingMessage && text.equals(currentMessage)) {
            Platform.runLater(() -> resetMessageDisplay(text));
            return;
        }
        messageQueue.add(text);
        processNextMessage();
    }

    private void processNextMessage() {
        if (isDisplayingMessage || messageQueue.isEmpty()) return;
        isDisplayingMessage = true;
        String message = messageQueue.poll();
        currentMessage = message;
        Platform.runLater(() -> displayMessage(message));
    }

    private void displayMessage(String message) {
        currentMessage = message;
        messageDisplay.getChildren().clear();
        parseAndAddText(message);
        centerMessage();
        FadeTransition fadeIn = new FadeTransition(Duration.millis(500), messageContainer);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.seconds(3));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), messageContainer);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            isDisplayingMessage = false;
            currentMessage = null;
            processNextMessage();
        });
        currentMessageAnimation = new SequentialTransition(fadeIn, pause, fadeOut);
        currentMessageAnimation.play();
    }

    private void resetMessageDisplay(String message) {
        if (currentMessageAnimation != null) currentMessageAnimation.stop();
        messageContainer.setOpacity(1);
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.seconds(3));
        FadeTransition fadeOut = new FadeTransition(Duration.millis(500), messageContainer);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            isDisplayingMessage = false;
            currentMessage = null;
            processNextMessage();
        });
        currentMessageAnimation = new SequentialTransition(pause, fadeOut);
        currentMessageAnimation.play();
    }

    private void parseAndAddText(String message) {
        List<TextSegment> segments = RenderEngineProvider.getInstance().getEngine().parseColoredText(message);
        for (TextSegment seg : segments) {
            Text textNode = new Text(seg.text);
            textNode.setFill(toJavaFXColor(seg.color));
            textNode.setFont(messageFont);
            messageDisplay.getChildren().add(textNode);
        }
    }

    private Color toJavaFXColor(ColorDef colorDef) {
        if (colorDef == null) return Color.WHITE;
        String value = colorDef.getValue();
        // 未显式指定颜色时（引擎默认色）保持与旧版一致的白色
        if ("#FFE4E1".equalsIgnoreCase(value)) return Color.WHITE;
        try {
            return Color.web(value);
        } catch (Exception e) {
            LoggerManager.Logger("WARN", "无效的颜色值: " + value);
            return Color.WHITE;
        }
    }

    private void centerMessage() {
        double containerWidth = uiContainer.getWidth();
        double containerHeight = uiContainer.getHeight();
        if (containerWidth > 0 && containerHeight > 0) {
            double messageWidth = messageDisplay.prefWidth(-1);
            double x = (containerWidth - messageWidth) / 2;
            double y = containerHeight - 60;
            messageContainer.setLayoutX(x);
            messageContainer.setLayoutY(y);
        }
    }

    public void clearAllMessages() {
        Platform.runLater(() -> {
            if (currentMessageAnimation != null) currentMessageAnimation.stop();
            messageQueue.clear();
            messageDisplay.getChildren().clear();
            messageContainer.setOpacity(0);
            isDisplayingMessage = false;
            currentMessage = null;
            LoggerManager.Logger("DEBUG", "所有消息已清除");
        });
    }

    public boolean isDisplayingMessage() { return isDisplayingMessage; }

    public void updateLayout() {
        Platform.runLater(() -> {
            double width = uiContainer.getWidth();
            double height = uiContainer.getHeight();
            rightGradient.setLayoutX(width - rightGradient.getWidth());
            rightGradient.setLayoutY(height - rightGradient.getHeight());
            leftGradient.setLayoutY(height - leftGradient.getHeight());
            healthBar.setLayoutX(20);
            healthBar.setLayoutY(height - 20 - healthBar.getPrefHeight());
            boolean magicEnabled = com.xiaowu.game.starveil.game.MagicSystem.isEnabled();
            staminaBar.setLayoutX(20);
            staminaBar.setLayoutY(height - 45 - staminaBar.getPrefHeight());
            if (magicEnabled) {
                magicBar.setLayoutX(20);
                magicBar.setLayoutY(height - 70 - magicBar.getPrefHeight());
            }
            if (hitFlashOverlay != null) {
                hitFlashOverlay.setWidth(width);
                hitFlashOverlay.setHeight(height);
                hitFlashOverlay.setLayoutX(0);
                hitFlashOverlay.setLayoutY(0);
            }
            // 手上物品显示在右下角
            if (handSlotPane != null) {
                handSlotPane.setLayoutX(width - 76);
                handSlotPane.setLayoutY(height - 76);
            }
            centerMessage();
        });
    }

    /**
     * 更新手上物品显示
     */
    public void updateHandSlot(String itemId) {
        Platform.runLater(() -> {
            if (itemId == null) {
                handSlotIcon.setVisible(false);
                handSlotIcon.setImage(null);
                handSlotName.setVisible(false);
                handSlotName.setText("");
                return;
            }

            com.xiaowu.game.starveil.game.item.Item item =
                com.xiaowu.game.starveil.game.item.ItemRegistry.getInstance().getItem(itemId);
            if (item == null) {
                handSlotIcon.setVisible(false);
                handSlotName.setVisible(false);
                return;
            }

            // 图标
            if (item.getIconPath() != null) {
                try {
                    Image img = new Image(ResourceResolver.getResource(item.getIconPath()).toExternalForm());
                    handSlotIcon.setImage(img);
                    handSlotIcon.setVisible(true);
                } catch (Exception e) {
                    handSlotIcon.setVisible(false);
                }
            } else {
                handSlotIcon.setVisible(false);
            }

            // 名称
            handSlotName.setText(item.getName());
            handSlotName.setVisible(true);
        });
    }

    public Pane getUIContainer() { return uiContainer; }

    // ==================== 新手教程可介绍的 UI 元素 ====================

    /**
     * 可被新手教程逐个介绍的 HUD 元素。
     *
     * <p><b>新增 HUD 元素时只需要在这里加一项</b> —— 教程会自动把它纳入介绍流程，
     * 不需要改 {@code TutorialManager}。元素文案也集中放在这里，
     * 避免散落在教程状态机里。
     */
    public enum TutorialTarget {
        HEALTH("生命值",
                "左下角的红条是你的生命值。\n受到伤害时会减少，归零就会死亡。"),
        STAMINA("体力值",
                "绿条是体力值。\n疾跑会持续消耗体力，耗尽后需要等它恢复才能继续疾跑。"),
        MAGIC("魔力值",
                "紫条是魔力值，用于释放法阵攻击。"),
        HAND_SLOT("手持物品",
                "右下角显示当前手持的物品。\n按交互键可以使用它。");

        private final String title;
        private final String body;

        TutorialTarget(String title, String body) {
            this.title = title;
            this.body = body;
        }

        public String title() { return title; }

        public String body() { return body; }
    }

    /** 该元素对应的节点（教程用它计算高亮区域）。 */
    public javafx.scene.Node getTutorialNode(TutorialTarget target) {
        return switch (target) {
            case HEALTH -> healthBar;
            case STAMINA -> staminaBar;
            case MAGIC -> magicBar;
            case HAND_SLOT -> handSlotPane;
        };
    }

    /**
     * 该元素当前是否可见、值得介绍。
     * 例如法阵系统被关闭时魔力条本身是隐藏的，就不该去介绍它。
     */
    public boolean isTutorialTargetAvailable(TutorialTarget target) {
        if (target == TutorialTarget.MAGIC
                && !com.xiaowu.game.starveil.game.MagicSystem.isEnabled()) {
            return false;
        }
        javafx.scene.Node n = getTutorialNode(target);
        return n != null && n.isVisible() && n.getParent() != null;
    }

    /** 按视觉顺序（从下往上）返回当前可见的、需要介绍的元素。 */
    public List<TutorialTarget> availableTutorialTargets() {
        List<TutorialTarget> out = new java.util.ArrayList<>();
        for (TutorialTarget t : new TutorialTarget[]{
                TutorialTarget.HEALTH, TutorialTarget.STAMINA,
                TutorialTarget.MAGIC, TutorialTarget.HAND_SLOT}) {
            if (isTutorialTargetAvailable(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * 添加自定义 UI 元素到游戏 UI 容器
     */
    public void addCustomUI(javafx.scene.Node node) {
        uiContainer.getChildren().add(node);
    }

    /**
     * 把 HUD 挂到【实际游戏视口】上（16:9、居中、带裁剪的那个 Pane）。
     *
     * <p>为什么不挂在逻辑画布上：
     * <ul>
     *   <li>视口才是<b>与世界同坐标系</b>的那一层，不受画布缩放/黑边影响；</li>
     *   <li>暂停菜单/死亡界面/存档界面加在 {@code gameContainer} 或 {@code modalHost} 上，
     *       对话框加在逻辑画布上 —— 它们都是视口的<b>兄弟或更上层</b>，
     *       天生就盖住 HUD。于是「暂停 / 视觉小说模式时隐藏 HUD」那套
     *       hide/show/detach 操作全都不需要了。</li>
     * </ul>
     */
    public void attachToGame() {
        Platform.runLater(() -> {
            javafx.scene.layout.Pane viewport = GameInstance.getViewportPane();
            if (viewport == null) {
                LoggerManager.Logger("WARN", "游戏视口尚未创建，GameUI 挂载延后");
                return;
            }
            if (uiContainer.getParent() == viewport) {
                return;   // 已经挂在视口上
            }
            // 切场景 / 换实例时视口会被重建，先从前一个父容器摘掉
            if (uiContainer.getParent() instanceof javafx.scene.layout.Pane oldParent) {
                oldParent.getChildren().remove(uiContainer);
            }
            viewport.getChildren().add(uiContainer);

            // ⚠️ 父容器是 Pane —— 它只按【首选尺寸】摆放子节点，不会拉伸。
            //    不绑定的话 HUD 会塌缩成 0 尺寸，血条根本看不见。
            if (uiContainer.prefWidthProperty().isBound()) {
                uiContainer.prefWidthProperty().unbind();
            }
            uiContainer.prefWidthProperty().bind(viewport.widthProperty());
            if (uiContainer.prefHeightProperty().isBound()) {
                uiContainer.prefHeightProperty().unbind();
            }
            uiContainer.prefHeightProperty().bind(viewport.heightProperty());

            if (!uiLayoutListenersAttached) {
                uiLayoutListenersAttached = true;
                uiContainer.widthProperty().addListener((obs, oldVal, newVal) -> updateLayout());
                uiContainer.heightProperty().addListener((obs, oldVal, newVal) -> updateLayout());
            }
            updateLayout();
            LoggerManager.Logger("DEBUG", "GameUI 已挂载到游戏视口");

            show();
        });
    }

    public void detachFromGame() {
        Platform.runLater(() -> {
            if (uiContainer.getParent() instanceof javafx.scene.layout.Pane parent) {
                parent.getChildren().remove(uiContainer);
                LoggerManager.Logger("DEBUG", "GameUI 已从逻辑画布移除");
            }
        });
    }
}
