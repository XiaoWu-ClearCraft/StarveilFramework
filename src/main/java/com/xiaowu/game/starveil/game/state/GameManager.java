package com.xiaowu.game.starveil.game.state;

import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;
import com.xiaowu.game.starveil.game.quest.AchievementManager;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import com.xiaowu.game.starveil.ui.overlay.NotificationManager;
import com.xiaowu.game.starveil.ui.overlay.PopupManager;
import com.xiaowu.game.starveil.render.InteractiveEffectManager;
import com.xiaowu.game.starveil.platform.windows.WindowsDarkModeUtil;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;


public class GameManager {
    private static GameManager instance;
    private Stage primaryStage;
    private Scene currentScene;

    private StackPane chatContainer;
    private final BooleanProperty fullscreenProperty = new SimpleBooleanProperty(false);
    private StackPane rootContainer;
    private StackPane contentWithOverlays;

    // 比例控制
    private double aspectRatio = 16.0 / 9.0;
    private Pane originalContent;

    /** 逻辑画布高度（固定，默认对应 1080p；宽度 = 高度 × 宽高比）。窗口更小则整体缩小，更大则整体放大。 */
    private static final double LOGICAL_HEIGHT = 1080;
    /** true = 等比填充整窗（不留黑边/空区，超出方向裁剪）；false = 等比包含（保留黑边）。 */
    private static final boolean SCALE_FILL_WINDOW = false;
    /** 当前承载“逻辑内容 + 覆盖层”的画布（经 Scale 缩放到窗口）。 */
    private StackPane logicalCanvas;
    /** 包裹画布的 Group：不参与父容器 resize，保证父容器 min 尺寸不被逻辑画布撑大。 */
    private javafx.scene.Group scaleWrapper;
    /** 逻辑画布内的“最顶层”宿主：暂停/存档/死亡/弹窗等盖在最上（含对话框上方）。 */
    private StackPane modalHost;
    private double lastScale = -1;

    // 保存窗口状态（用于无边框窗口模式）
    private double previousWindowX = 0;
    private double previousWindowY = 0;
    private double previousWindowWidth = 0;
    private double previousWindowHeight = 0;

    // 保存Stage配置（用于重新创建Stage）
    private String stageTitle = "";
    private javafx.scene.image.Image stageIcon = null;

    // 监听器初始化标志
    private boolean listenersInitialized = false;

    /** 是否已完成一次完整初始化（引擎/容器/监听只做一次，避免重复）。 */
    private boolean initialized = false;

    private GameManager() {

    }

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public boolean isInitialized() {
        return initialized;
    }


    public void initialize(Stage stage, Scene scene) {
        this.primaryStage = stage;
        this.currentScene = scene;

        // 保存Stage配置（用于重新创建Stage）
        this.stageTitle = stage.getTitle();
        if (!stage.getIcons().isEmpty()) {
            this.stageIcon = stage.getIcons().get(0);
        }

        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(675);


        rootContainer = new StackPane();
        rootContainer.setStyle("-fx-background-color: black;");


        // 注意: 通知和弹窗容器不在这里初始化,因为此时RenderEngine还未设置
        // 它们会在updateScene()或switchToNewContent()时第一次使用时才获取

        ChatManager chatManager = ChatManager.getInstance();
        chatContainer = chatManager.getChatContainer();


        InteractiveEffectManager effectManager = InteractiveEffectManager.getInstance();
        effectManager.setGameManager(this);


        if (scene.getRoot() instanceof Pane originalRoot) {
            // 保存原始内容
            originalContent = originalRoot;

            contentWithOverlays = new StackPane();
            contentWithOverlays.setStyle("-fx-background-color: black;");

            rootContainer.getChildren().add(contentWithOverlays);
            scene.setRoot(rootContainer);

            // 挂逻辑画布（内容与覆盖层都布局在固定逻辑坐标，按窗口缩放）
            mountContent(originalRoot, true);

            Logger("DEBUG", "游戏管理器初始化完成 - 逻辑画布 + 窗口缩放");
        }
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        initializeCloseHandler();
        setupGlobalKeyListeners(scene);
        setupWindowListeners();

        initialized = true;

        // 注意：初始全屏设置在Menu.java中处理，根据全屏模式使用不同的实现方式
    }

    /**
     * 重新应用用户保存的显示设置（比例 + 全屏）。
     * 用于每次回到“顶层界面”（如主菜单）时，确保不会停留在游戏期间的全屏状态。
     */
    public void applyWindowSettings() {
        String aspectRatio = com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys
                .ASPECT_RATIO.get();
        if (aspectRatio != null && !aspectRatio.isEmpty()) {
            setAspectRatio(aspectRatio);
        }
        Boolean fullscreen = com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys
                .FULLSCREEN.get();
        if (fullscreenProperty.get() != fullscreen) {
            fullscreenProperty.set(fullscreen);
        }
    }


    /**
     * 构建“逻辑分辨率画布”：固定为 LOGICAL_HEIGHT 高的逻辑尺寸，
     * 内容根与剧情/聊天/效果/通知/弹窗都布局在这个固定坐标系内。
     * 之后整层被等比缩放到窗口，实现“窗口变大 = 内容等比放大”，而不是增加空白布局区。
     */
    private StackPane buildLogicalCanvas(Pane content) {
        double logicalWidth = Math.round(LOGICAL_HEIGHT * aspectRatio);
        StackPane canvas = new StackPane();
        canvas.setStyle("-fx-background-color: black;");
        canvas.setPrefSize(logicalWidth, LOGICAL_HEIGHT);
        canvas.setMinSize(logicalWidth, LOGICAL_HEIGHT);
        canvas.setMaxSize(logicalWidth, LOGICAL_HEIGHT);

        // 裁剪到逻辑画布边界：超出逻辑内容（黑边区）的绘制一律不显示
        javafx.scene.shape.Rectangle clipRect =
                new javafx.scene.shape.Rectangle(logicalWidth, LOGICAL_HEIGHT);
        canvas.setClip(clipRect);

        // 层级：内容在最下，后加的显示在上（全部在逻辑坐标内）
        canvas.getChildren().add(content);
        canvas.getChildren().add(ChatManager.getInstance().getStoryImagePane());
        canvas.getChildren().add(chatContainer);
        canvas.getChildren().add(InteractiveEffectManager.getInstance().getEffectContainer());
        canvas.getChildren().add((StackPane) NotificationManager.getInstance().getNotificationContainer());

        // 最顶层：模态宿主（暂停/存档/死亡/设置/弹窗容器都放这里，可盖在对话框上方）
        modalHost = new StackPane();
        modalHost.setPickOnBounds(false);
        canvas.getChildren().add(modalHost);
        return canvas;
    }

    /** 逻辑画布内的最顶层宿主。 */
    public StackPane getModalHost() {
        return modalHost;
    }

    /**
     * 逻辑画布本身（1728x1080、带裁剪的那个 StackPane）。
     *
     * <p>需要与游戏内容共用坐标系、又不该盖住模态面板的东西（例如 HUD）应当挂在这里。
     * 注意画布会在每次 {@code mountContent} 时重建，引用不能长期缓存。
     */
    public StackPane getLogicalCanvas() {
        return logicalCanvas;
    }

    /** 把 contentRoot 作为新内容挂到 contentWithOverlays 内的逻辑画布上，并接好缩放。
     *  @param clearExtras true = 整屏切换内容（清空其它覆盖层）；false = 仅替换画布（保留设置等面板） */
    private void mountContent(Pane contentRoot, boolean clearExtras) {
        if (contentWithOverlays == null) return;

        // 父容器不要被逻辑画布的最小尺寸撑大，否则窗口无法缩到比逻辑尺寸小
        contentWithOverlays.setMinSize(0, 0);

        if (clearExtras) {
            contentWithOverlays.getChildren().clear();
        } else if (scaleWrapper != null) {
            // 只替换旧的画布包装，保留宿主上其它由 UI 临时添加的覆盖层（如设置面板）
            contentWithOverlays.getChildren().remove(scaleWrapper);
        }

        logicalCanvas = buildLogicalCanvas(contentRoot);
        scaleWrapper = new javafx.scene.Group(logicalCanvas);
        // 画布始终放最底层，弹窗/菜单等覆盖层保持在它上方
        contentWithOverlays.getChildren().add(0, scaleWrapper);

        // 弹窗容器：全窗覆盖层（画布之上），show 时 toFront 盖过任何先打开的页面
        Object popupObj = PopupManager.getInstance().getPopupContainer();
        if (popupObj instanceof javafx.scene.Node p && !contentWithOverlays.getChildren().contains(p)) {
            contentWithOverlays.getChildren().add(p);
        }

        // 只为同一个 host 绑定一次尺寸监听（mount 可能多次调用）
        if (contentWithOverlays.getUserData() != Boolean.TRUE) {
            contentWithOverlays.setUserData(Boolean.TRUE);
            contentWithOverlays.widthProperty().addListener((o, ov, nv) -> updateContentScale());
            contentWithOverlays.heightProperty().addListener((o, ov, nv) -> updateContentScale());
        }
        updateContentScale();
        contentWithOverlays.layout();
    }

    /** 按窗口可用区域等比缩放逻辑画布（经 Group 缩放，父容器不受画布尺寸约束）。 */
    private void updateContentScale() {
        if (scaleWrapper == null || logicalCanvas == null || contentWithOverlays == null) return;
        double availW = contentWithOverlays.getWidth();
        double availH = contentWithOverlays.getHeight();
        double lw = logicalCanvas.getPrefWidth();
        double lh = logicalCanvas.getPrefHeight();
        if (availW <= 0 || availH <= 0 || lw <= 0 || lh <= 0) return;

        double s = SCALE_FILL_WINDOW
                ? Math.max(availW / lw, availH / lh)   // 等比填充：内容随窗口任意一维变大而放大
                : Math.min(availW / lw, availH / lh);  // 等比包含：保留黑边
        scaleWrapper.getTransforms().setAll(new javafx.scene.transform.Scale(s, s, lw / 2, lh / 2));
        if (Math.abs(s - lastScale) > 0.001) {
            lastScale = s;
            Logger("DEBUG", "内容缩放: avail=" + (int) availW + "x" + (int) availH
                    + " 逻辑=" + (int) lw + "x" + (int) lh + " scale=" + String.format("%.3f", s));
        }
    }


    private void setupGlobalKeyListeners(Scene scene) {
        // F11 全屏切换已迁移至 GameInstance 通过 InputHandler 处理
    }


    private void setupWindowListeners() {
        // 只在第一次添加 fullscreenProperty 监听器
        if (!listenersInitialized) {
            fullscreenProperty.addListener((_, _, newValue) -> Platform.runLater(() -> {
                Logger("DEBUG", "fullscreenProperty监听器被触发: " + newValue);

                // 获取用户选择的全屏模式
                String fullscreenMode = FrameworkDataKeys.FULLSCREEN_MODE.get();

                // 根据全屏模式使用不同的实现方式
                if (FrameworkDataKeys.FULLSCREEN_MODE_BORDERLESS
                        .equals(FrameworkDataKeys.normalizeFullscreenMode(fullscreenMode))) {
                    // 无边框窗口模式：调用applyFullscreen处理全屏逻辑
                    applyFullscreen(newValue);
                } else {
                    // 传统全屏模式
                    primaryStage.setFullScreenExitHint("");
                    primaryStage.setFullScreen(newValue);
                    // 更新DataManager中的全屏设置
                    FrameworkDataKeys.FULLSCREEN.set(newValue);
                }
            }));

            listenersInitialized = true;
        }

        // 每次都添加新Stage的 fullScreenProperty 监听器（因为Stage对象变了）
        primaryStage.fullScreenProperty().addListener((_, _, newValue) -> {
            Logger("DEBUG", "primaryStage.fullScreenProperty监听器被触发: " + newValue + ", 当前fullscreenProperty: " + fullscreenProperty.get());
            if (!newValue && fullscreenProperty.get()) {
                Logger("DEBUG", "同步状态：将fullscreenProperty设置为false");
                fullscreenProperty.set(false);
            }
        });

        // 添加窗口失去焦点时的监听器，重置所有按键状态
        primaryStage.focusedProperty().addListener((_, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                // 窗口从获得焦点变为失去焦点
                resetAllKeyStates();
            }
        });
    }

    /**
     * 设置Stage相关的监听器（用于Stage替换后重新绑定）
     */
    private void setupStageListeners() {
        // 添加新Stage的 fullScreenProperty 监听器（因为Stage对象变了）
        primaryStage.fullScreenProperty().addListener((_, _, newValue) -> {
            Logger("DEBUG", "primaryStage.fullScreenProperty监听器被触发: " + newValue + ", 当前fullscreenProperty: " + fullscreenProperty.get());
            if (!newValue && fullscreenProperty.get()) {
                Logger("DEBUG", "同步状态：将fullscreenProperty设置为false");
                fullscreenProperty.set(false);
            }
        });

        // 添加窗口失去焦点时的监听器，重置所有按键状态
        primaryStage.focusedProperty().addListener((_, oldValue, newValue) -> {
            if (oldValue && !newValue) {
                // 窗口从获得焦点变为失去焦点
                resetAllKeyStates();
            }
        });
    }

    /**
     * 重置所有按键状态（防止窗口失去焦点后按键状态卡住）
     */
    private void resetAllKeyStates() {
        if (GameInstance.getInputHandlerStatic() != null) {
            GameInstance.getInputHandlerStatic().resetAllKeys();
            Logger("DEBUG", "窗口失去焦点，已重置所有按键状态");
        }
    }


    private void replaceStage(StageStyle newStyle, Runnable afterReplace) {
        Logger("DEBUG", "replaceStage被调用: " + newStyle);

        // 保存当前Scene和旧Stage的配置
        Scene savedScene = currentScene;
        String savedTitle = primaryStage.getTitle();
        javafx.scene.image.Image savedIcon = primaryStage.getIcons().isEmpty() ? null : primaryStage.getIcons().get(0);

        // 关闭旧Stage
        primaryStage.close();

        // 创建新Stage
        Stage newStage = new Stage(newStyle);
        newStage.setTitle(savedTitle);
        if (savedIcon != null) {
            newStage.getIcons().add(savedIcon);
        }
        newStage.setMinWidth(1200);
        newStage.setMinHeight(675);

        // 应用保存的Scene
        newStage.setScene(savedScene);

        // 重新设置Stage相关配置
        newStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);

        // 更新primaryStage引用和配置
        this.primaryStage = newStage;
        this.stageTitle = savedTitle;
        this.stageIcon = savedIcon;

        // 重新绑定事件监听器
        setupGlobalKeyListeners(savedScene);
        setupStageListeners();
        initializeCloseHandler();

        // 执行回调
        if (afterReplace != null) {
            afterReplace.run();
        }

        // 显示新Stage
        newStage.show();

        // 根据系统主题设置标题栏颜色
        Platform.runLater(() -> {
            WindowsDarkModeUtil.enableForJavaFXAuto(newStage);
        });

        // 强制刷新布局，确保内容比例正确
        Platform.runLater(() -> {
            if (contentWithOverlays != null) {
                contentWithOverlays.requestLayout();
                Logger("DEBUG", "已强制刷新布局");
            }
        });

        Logger("DEBUG", "Stage已替换，新StageStyle: " + newStyle);
    }

    /** 返回窗口当前所在屏幕的边界（按窗口中心判定；找不到则退回主屏）。 */
    private javafx.geometry.Rectangle2D currentScreenBounds() {
        if (primaryStage != null) {
            double cx = primaryStage.getX() + primaryStage.getWidth() / 2;
            double cy = primaryStage.getY() + primaryStage.getHeight() / 2;
            for (javafx.stage.Screen s : javafx.stage.Screen.getScreens()) {
                if (s.getBounds().contains(cx, cy)) {
                    return s.getBounds();
                }
            }
            var list = javafx.stage.Screen.getScreensForRectangle(
                    primaryStage.getX(), primaryStage.getY(), primaryStage.getWidth(), primaryStage.getHeight());
            if (!list.isEmpty()) {
                return list.get(0).getBounds();
            }
        }
        return javafx.stage.Screen.getPrimary().getBounds();
    }

    private void applyFullscreen(boolean isFullscreen) {
        Logger("DEBUG", "applyFullscreen被调用: " + isFullscreen);

        // 获取用户选择的全屏模式
        String fullscreenMode = FrameworkDataKeys.FULLSCREEN_MODE.get();

        // 根据全屏模式使用不同的实现方式
        if (FrameworkDataKeys.FULLSCREEN_MODE_BORDERLESS
                .equals(FrameworkDataKeys.normalizeFullscreenMode(fullscreenMode))) {
            // 无边框窗口模式：使用replaceStage切换窗口样式
            if (isFullscreen) {
                // 保存当前窗口状态
                previousWindowX = primaryStage.getX();
                previousWindowY = primaryStage.getY();
                previousWindowWidth = primaryStage.getWidth();
                previousWindowHeight = primaryStage.getHeight();

                // 如果窗口宽高为0（初始化时可能的情况），使用默认值
                if (previousWindowWidth == 0) {
                    previousWindowWidth = 1200;
                }
                if (previousWindowHeight == 0) {
                    previousWindowHeight = 675;
                }

                // 获取窗口当前所在屏幕的可视区域（多显示器/不同缩放时跟随所在屏）
                Rectangle2D visualBounds = currentScreenBounds();
                replaceStage(StageStyle.UNDECORATED, () -> {
                    primaryStage.setX(visualBounds.getMinX());
                    primaryStage.setY(visualBounds.getMinY());
                    primaryStage.setWidth(visualBounds.getWidth());
                    primaryStage.setHeight(visualBounds.getHeight());
                });
            } else {
                // 恢复窗口状态
                final double restoreWidth = previousWindowWidth > 0 ? previousWindowWidth : 1200;
                final double restoreHeight = previousWindowHeight > 0 ? previousWindowHeight : 675;
                final double restoreX = previousWindowX;
                final double restoreY = previousWindowY;

                // 替换为有边框Stage并恢复窗口大小
                replaceStage(StageStyle.DECORATED, () -> {
                    if (restoreWidth > 0 && restoreHeight > 0) {
                        primaryStage.setX(restoreX);
                        primaryStage.setY(restoreY);
                        primaryStage.setWidth(restoreWidth);
                        primaryStage.setHeight(restoreHeight);
                    } else {
                        // 如果没有保存的状态，使用默认值，并在当前所在屏幕居中
                        primaryStage.setWidth(1200);
                        primaryStage.setHeight(675);
                        Rectangle2D screenBounds = currentScreenBounds();
                        primaryStage.setX(screenBounds.getMinX() + (screenBounds.getWidth() - 1200) / 2);
                        primaryStage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - 675) / 2);
                    }
                });
            }
        } else {
            // 传统全屏模式
            Platform.runLater(() -> {
                primaryStage.setFullScreenExitHint("");
                primaryStage.setFullScreen(isFullscreen);
            });
        }

        // 更新DataManager中的全屏设置
        FrameworkDataKeys.FULLSCREEN.set(isFullscreen);
    }

    public void toggleFullscreen() {
        boolean newFullscreen = !fullscreenProperty.get();
        fullscreenProperty.set(newFullscreen);
        // fullscreenProperty的监听器会自动调用applyFullscreen
    }


    public void setFullscreen(boolean fullscreen) {
        boolean currentFullscreen = fullscreenProperty.get();
        if (currentFullscreen == fullscreen) {
            Logger("DEBUG", "setFullscreen被调用但状态未改变，跳过: " + fullscreen);
            return; // 状态未改变，不做任何操作
        }
        Logger("DEBUG", "setFullscreen被调用: " + currentFullscreen + " -> " + fullscreen);
        fullscreenProperty.set(fullscreen);
        // fullscreenProperty的监听器会自动调用applyFullscreen
    }


    public boolean isFullscreen() {
        return fullscreenProperty.get();
    }


    public BooleanProperty fullscreenProperty() {
        return fullscreenProperty;
    }

    public StackPane getAchievementContainer() {
        return (StackPane) NotificationManager.getInstance().getNotificationContainer();
    }

    public StackPane getContentWithOverlays() {
        return contentWithOverlays;
    }


    /**
     * 把新内容（如主菜单 / 游戏根面板）挂到同一个 GameManager 容器中。
     * 需在 JavaFX Application 线程调用；同步执行，随后场景根即 rootContainer。
     */
    public void updateScene(Scene newScene) {
        this.currentScene = newScene;

        if (rootContainer == null || !(newScene.getRoot() instanceof Pane newRoot)) {
            System.err.println("错误: rootContainer 为 null 或新根节点不是 Pane");
            setupGlobalKeyListeners(newScene);
            return;
        }

        // 先释放 rootContainer 的旧归属，再挂到新场景，避免 “already set as root of another scene”
        Parent currentParent = rootContainer.getParent();
        if (currentParent instanceof Pane) {
            ((Pane) currentParent).getChildren().remove(rootContainer);
        }
        Scene oldScene = rootContainer.getScene();
        if (oldScene != null && oldScene != newScene) {
            // rootContainer 仍是旧场景的根（无 parent），换成占位根以解除占用
            oldScene.setRoot(new StackPane());
            Logger("DEBUG", "已从旧场景解除 rootContainer 的根占用");
        }

        rootContainer.getChildren().clear();

        StackPane newContentWithOverlays = new StackPane();
        newContentWithOverlays.setStyle("-fx-background-color: black;");

        // 更新字段引用，让 getContentWithOverlays()/switchToNewContent()/refreshAspectRatio() 指向新的覆盖层
        this.contentWithOverlays = newContentWithOverlays;
        this.originalContent = newRoot;

        rootContainer.getChildren().add(newContentWithOverlays);
        // 挂逻辑画布（含缩放）
        mountContent(newRoot, true);
        newScene.setRoot(rootContainer);

        // 强制布局，避免切换瞬间出现白边/内容未就位（需窗口 resize 才恢复）
        rootContainer.layout();
        Platform.runLater(() -> {
            if (contentWithOverlays != null) contentWithOverlays.requestLayout();
            if (rootContainer != null) rootContainer.requestLayout();
            // 让聊天/对话框按新的逻辑区域尺寸重新布局
            ChatManager.getInstance().syncLayout();
        });

        Logger("DEBUG", "场景更新完成 - 内容挂入逻辑画布并按窗口缩放");
        setupGlobalKeyListeners(newScene);
    }


    public void switchToNewContent(Parent newContent) {
        Platform.runLater(() -> {
            // 内容（含覆盖层）收进逻辑画布并按窗口缩放
            mountContent((Pane) newContent, true);
            Platform.runLater(() -> {
                if (contentWithOverlays != null) contentWithOverlays.requestLayout();
                ChatManager.getInstance().syncLayout();
            });

            Logger("DEBUG", "内容切换完成 - 内容挂入逻辑画布并按窗口缩放");
        });
    }

    private void initializeCloseHandler() {
        if (primaryStage != null) {
            Logger("DEBUG", "设置关闭窗口监听器 - Stage: " + primaryStage);
            primaryStage.setOnCloseRequest(event -> {
                Logger("DEBUG", "关闭窗口事件被触发");
                boolean preventExit = shouldPreventExit();
                Logger("DEBUG", "shouldPreventExit: " + preventExit);

                if (preventExit) {
                    event.consume();

                    Platform.runLater(() -> {
                        SystemManagerFactory.getInstance().shakeWindow(primaryStage, 2);
                        AchievementManager.unlockAchievement("exit");
                    });
                } else {
                    performNormalExit();
                }
            });
        }
    }

    private boolean shouldPreventExit() {
        try {
            return DataManager.isExitBlocked();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 执行正常的退出操作
     * 清理资源并结束游戏进程
     */
    private void performNormalExit() {
        Logger("INFO", "用户请求关闭游戏窗口，执行正常退出程序");

        // 清除所有章节实例和事件监听器
        clearAllChapterInstances();

        Platform.exit();
        System.exit(0);
    }

    /**
     * 清除所有章节实例和事件监听器
     */
    private void clearAllChapterInstances() {
        try {
            // 停掉正在播放 / 排队中的剧情
            com.xiaowu.game.starveil.game.story.StoryScheduler.getInstance().cancelAll();

            // 获取 WorldMap 实例
            WorldMap worldMap = GameInstance.getWorldMap();
            if (worldMap != null) {
                // 清除所有事件监听器
                worldMap.clearAllEventListeners();
                Logger("INFO", "已清除所有事件监听器");
            }

            Logger("INFO", "已清除所有章节实例");

        } catch (Exception e) {
            Logger("ERROR", "清除章节实例时出错: " + e.getMessage());
        }
    }


    public Stage getPrimaryStage() {
        return primaryStage;
    }


    public Scene getCurrentScene() {
        return currentScene;
    }

    public ChatManager getChatManager() {
        return ChatManager.getInstance();
    }


    public InteractiveEffectManager getEffectManager() {
        return InteractiveEffectManager.getInstance();
    }

    /**
     * 获取当前比例
     */
    public double getAspectRatio() {
        return aspectRatio;
    }

    /**
     * 设置比例
     */
    public void setAspectRatio(double ratio) {
        this.aspectRatio = ratio;

        // 获取当前窗口的高度
        double currentHeight = primaryStage.getHeight();

        // 根据新的比例计算新的宽度（保持高度不变）
        double newWidth = currentHeight * ratio;

        // 更新最小窗口大小以适应新比例
        double minHeight = 600; // 最小高度
        double minWidth = minHeight * ratio;
        primaryStage.setMinWidth(minWidth);
        primaryStage.setMinHeight(minHeight);

        // 如果新宽度超出当前窗口大小，调整窗口大小
        if (newWidth > primaryStage.getWidth()) {
            primaryStage.setWidth(newWidth);
        }

        // 重新创建比例容器以应用新比例
        refreshAspectRatio();

        Logger("INFO", "比例已设置为: " + ratio + ", 窗口大小: " + primaryStage.getWidth() + "x" + primaryStage.getHeight());
    }

    /**
     * 根据比例字符串设置比例
     */
    public void setAspectRatio(String ratioString) {
        switch (ratioString) {
            case "4:3":
                setAspectRatio(4.0 / 3.0);
                break;
            case "16:10":
                setAspectRatio(16.0 / 10.0);
                break;
            case "21:9":
                setAspectRatio(21.0 / 9.0);
                break;
            case "16:9":
            default:
                setAspectRatio(16.0 / 9.0);
        }
    }

    /**
     * 获取当前比例字符串
     */
    public String getAspectRatioString() {
        double ratio = aspectRatio;
        double tolerance = 0.01;

        if (Math.abs(ratio - 4.0 / 3.0) < tolerance) {
            return "4:3";
        } else if (Math.abs(ratio - 16.0 / 9.0) < tolerance) {
            return "16:9";
        } else if (Math.abs(ratio - 16.0 / 10.0) < tolerance) {
            return "16:10";
        } else if (Math.abs(ratio - 21.0 / 9.0) < tolerance) {
            return "21:9";
        } else {
            return "16:9";
        }
    }

    /**
     * 刷新比例容器
     */
    private void refreshAspectRatio() {
        if (contentWithOverlays != null && originalContent != null) {
            // 按新的 aspectRatio 重建逻辑画布并重新缩放
            mountContent(originalContent, false);

            // 触发布局更新以通知GameInstance更新视口
            Platform.runLater(() -> {
                if (contentWithOverlays != null) contentWithOverlays.requestLayout();
                ChatManager.getInstance().syncLayout();
                // 等待一帧后再次请求布局，确保视口正确更新
                Platform.runLater(() -> {
                    if (contentWithOverlays != null) contentWithOverlays.requestLayout();
                });
            });
        }
    }
}