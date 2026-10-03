package com.xiaowu.game.starveil.ui.core;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.game.state.RPGManager;
import com.xiaowu.game.starveil.game.quest.AchievementManager;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.ui.screen.StartupTipsManager;
import com.xiaowu.game.starveil.ui.settings.SettingManager;
import com.xiaowu.game.starveil.ui.achievement.AchievementOverviewUI;
import com.xiaowu.game.starveil.ui.screen.SaveLoadUI;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.ui.core.UITools;
import com.xiaowu.game.starveil.platform.windows.WindowsDarkModeUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Objects;

public class Menu extends Application {

    private GameManager gameManager;
    private InputHandler inputHandler;

    @Override
    public void start(Stage stage) {
        start(stage, true);
    }

    /**
     * 启动菜单
     *
     * @param stage           舞台
     * @param showStartupTips 是否显示启动提示，默认为 true
     */
    public void start(Stage stage, boolean showStartupTips) {
        try {
            // RenderEngine 只在最早期初始化一次（返回菜单时不再重建，避免管理器持有旧引擎）
            if (!com.xiaowu.game.starveil.render.engine.RenderEngineProvider.getInstance().isInitialized()) {
                com.xiaowu.game.starveil.render.engine.javafx.JavaFXRenderEngine renderEngine =
                        new com.xiaowu.game.starveil.render.engine.javafx.JavaFXRenderEngine();
                renderEngine.initialize(1200, 675);
                com.xiaowu.game.starveil.render.engine.RenderEngineProvider.getInstance().setEngine(renderEngine);
            }

            gameManager = GameManager.getInstance();
            if (inputHandler == null) {
                inputHandler = new InputHandler();
            }

            BorderPane root = new BorderPane();
            Background bGround = getBackground();
            // BGM播放移到提示显示完成后
            root.setBackground(bGround);

            VBox leftPanel = createLeftPanel();
            root.setLeft(leftPanel);

            Scene scene = new Scene(root, 1200, 675);

            // 设置按键事件处理
            scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> inputHandler.handleKeyPressed(e));
            scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> inputHandler.handleKeyReleased(e));

            // 主菜单也支持 F11 全屏切换
            inputHandler.onKeyPressed(javafx.scene.input.KeyCode.F11, event -> {
                com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger("DEBUG",
                        "F11 pressed in Menu, 触发全屏切换");
                GameManager.getInstance().toggleFullscreen();
                event.consume();
            }, true);

            // 只在首次启动完整初始化 GameManager；之后都挂到同一个容器里（避免重复建容器/监听/全屏状态错乱）
            if (!gameManager.isInitialized()) {
                gameManager.initialize(stage, scene);
            } else {
                gameManager.updateScene(scene);
            }

            // 以 GameManager 当前 Stage 为准（全屏切换可能替换过 Stage）
            Stage current = gameManager.getPrimaryStage() != null ? gameManager.getPrimaryStage() : stage;

            // 应用用户显示设置（比例 + 全屏）——内容已挂入 GameManager 容器
            gameManager.applyWindowSettings();

            current.setScene(scene);
            current.setTitle(GameConstants.MAIN_MENU_TITLE);
            if (current.getIcons().isEmpty()) {
                // 窗口图标由内容提供（content.init.init → ContentConfig.setAppIcon）。
                // 未提供时保持系统默认图标 —— 原实现用 Objects.requireNonNull 包着，
                // 一旦内容不带图标就直接 NPE，整个主菜单起不来。
                String iconPath = com.xiaowu.game.starveil.infrastructure.ContentConfig.appIcon();
                if (iconPath != null && !iconPath.isEmpty()) {
                    try (java.io.InputStream iconStream = ResourceResolver.getResourceAsStream(iconPath)) {
                        if (iconStream != null) {
                            current.getIcons().add(new Image(iconStream));
                        }
                    } catch (Exception ignored) {
                        // 图标加载失败不该拦住启动
                    }
                }
            }
            current.show();

            // 根据系统主题设置标题栏颜色
            Platform.runLater(() -> {
                WindowsDarkModeUtil.enableForJavaFXAuto(current);
            });

            // 根据参数决定是否显示启动提示
            if (showStartupTips) {
                // 显示启动提示，然后显示菜单内容并播放BGM
                StartupTipsManager tipsManager = StartupTipsManager.getInstance();
                tipsManager.showTips(current, scene, inputHandler, () -> {
                    // 提示显示完成后，播放BGM并显示菜单内容
                    playMenuMusic();
                    showMenuContent();
                });
            } else {
                // 不显示启动提示，直接播放BGM并显示菜单内容
                playMenuMusic();
                showMenuContent();
            }
        } catch (Exception e) {
            System.err.println("Menu.start方法发生异常:");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 从游戏返回菜单（不重新初始化GameManager和Scene）
     */
    public void startWithoutInit() {
        gameManager = GameManager.getInstance();
        if (inputHandler == null) {
            inputHandler = new InputHandler();
        }

        // 创建菜单UI
        BorderPane menuRoot = new BorderPane();
        Background bGround = getBackground();
        menuRoot.setBackground(bGround);

        VBox leftPanel = createLeftPanel();
        menuRoot.setLeft(leftPanel);

        // 使用switchToNewContent切换到菜单内容
        gameManager.switchToNewContent(menuRoot);

        // 播放菜单BGM
        playMenuMusic();

        // 显示菜单内容（成就解锁等）
        showMenuContent();
    }

    /**
     * 显示菜单内容（提示显示完成后调用）
     */
    private void showMenuContent() {
        Platform.runLater(() -> {
            AchievementManager achievementManager = AchievementManager.getInstance();
            if (!achievementManager.isAchievementUnlocked("welcome")) {
                AchievementManager.unlockAchievement("welcome");
            }
        });
    }

    /**
     * 能否开始游戏。
     *
     * <p>{@code -no-content} 是用来验证「缺资源时降级是否正常」的开发开关：
     * 框架本身没有章节、地图、物品、成就，点进去只会是一个玩不了的空壳。
     * 明确拒绝并说明原因，比让它假装能玩要好。
     */
    private static boolean canStartGame() {
        if (!com.xiaowu.game.starveil.config.LauncherConfig.getInstance().isNoContent()) {
            return true;
        }
        com.xiaowu.game.starveil.platform.api.SystemManagerFactory.getInstance().showWarning(
                "无法开始游戏",
                "当前正在【无内容模式】下运行（-no-content）。\n\n"
                        + "该模式仅用于验证框架在缺少美术/音频资源时的降级行为，"
                        + "不包含任何游戏内容，因此无法开始游戏。\n\n"
                        + "请去掉 -no-content 参数，或使用包含内容的完整版本。");
        return false;
    }

    /**
     * 内容是否定义了成就。
     *
     * <p>没有定义时主菜单不显示成就入口 —— 打开一个空列表没有意义，
     * 还会让人以为「成就系统坏了」。成就定义由内容项目提供
     * （{@code starveil:data/config/achievements.json}）。
     */
    private static boolean hasAchievements() {
        return !com.xiaowu.game.starveil.game.quest.AchievementManager.getInstance()
                .getAllAchievements().isEmpty();
    }

    /**
     * 播放主菜单 BGM。
     *
     * <p>音乐由内容通过 {@code ContentConfig.setMenuMusic(...)} 指定。
     * <b>内容没指定就什么都不播</b> —— 框架不该自作主张去加载一个默认音乐文件，
     * 那只会让「没装音频」在日志里刷满错误。
     */
    private static void playMenuMusic() {
        String music = com.xiaowu.game.starveil.infrastructure.ContentConfig.menuMusic();
        if (music != null && !music.isEmpty()) {
            AudioManager.playBackgroundMusic(music, true);
        }
    }

    /**
     * 安全加载字体：文件缺失或内容非法时回退到系统默认字体，<b>绝不返回 null</b>。
     *
     * <p>{@code Font.loadFont} 在流为 null 或内容不是合法字体时返回 null，
     * 直接 {@code setFont(null)} 会让 JavaFX 在布局阶段抛
     * {@code Cannot invoke "Font.getNativeFont()" because "<parameter1>" is null}
     * —— 表现为窗口一显示就崩。框架不带字体时必须走这里。
     */
    static Font safeFont(String resourcePath, double size) {
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Font f = Font.loadFont(is, size);
                if (f != null) {
                    return f;
                }
            }
        } catch (Exception ignored) {
            // 落到系统默认字体
        }
        return Font.font(size);
    }

    private static Background getBackground() {
        BackgroundSize backgroundSize = new BackgroundSize(
                BackgroundSize.AUTO,
                BackgroundSize.AUTO,
                false,
                false,
                true,
                true
        );

        // 背景图由内容提供（ContentConfig.setMenuBackground）。
        // 内容没提供时用纯黑 —— 原实现用 Objects.requireNonNull 包着，
        // 缺图直接 NPE，主菜单整个起不来。
        String bgPath = com.xiaowu.game.starveil.infrastructure.ContentConfig.menuBackground();
        if (bgPath != null && !bgPath.isEmpty()) {
            try (java.io.InputStream is = ResourceResolver.getResourceAsStream(bgPath)) {
                if (is != null) {
                    Image img = new Image(is);
                    if (!img.isError() && img.getWidth() > 0) {
                        BackgroundImage bImg = new BackgroundImage(img, BackgroundRepeat.NO_REPEAT,
                                BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT, backgroundSize);
                        return new Background(bImg);
                    }
                }
            } catch (Exception ignored) {
                // 图坏了也不该拦住启动，退回纯黑
            }
        }
        return new Background(new javafx.scene.layout.BackgroundFill(
                javafx.scene.paint.Color.BLACK,
                javafx.scene.layout.CornerRadii.EMPTY,
                javafx.geometry.Insets.EMPTY));
    }

    private VBox createLeftPanel() {
        VBox leftPanel = new VBox(20);
        leftPanel.setPrefWidth(300);
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setPadding(new Insets(50));

        BackgroundFill panelFill = new BackgroundFill(
                Color.rgb(0, 0, 0, 0.5), new CornerRadii(0), Insets.EMPTY
        );
        leftPanel.setBackground(new Background(panelFill));
        Font customFont = safeFont(com.xiaowu.game.starveil.infrastructure.ContentConfig.decorFont(), 20);

        Label gameTitle = new Label("雾隐星阑");
        gameTitle.setTextFill(Color.WHITE);
        gameTitle.setFont(safeFont(com.xiaowu.game.starveil.infrastructure.ContentConfig.bodyFont(), 24));
        Button startButton;
        startButton = createStyledButton("开始游戏");

        Button achievementButton = createStyledButton("成就");

        achievementButton.setFont(customFont);

        achievementButton.setOnAction(_ -> showAchievementUI());


        Button loadButton = createStyledButton("加载存档");

        loadButton.setFont(customFont);

        loadButton.setOnAction(_ -> showLoadUI());


        Button settingButton = createStyledButton("设置");

        settingButton.setFont(customFont);

        settingButton.setOnAction(_ -> showSettingUI());

        Button exitButton = createStyledButton("退出游戏");

        exitButton.setTextFill(Color.RED);


        startButton.setFont(customFont);


        exitButton.setFont(customFont);


        startButton.setOnAction(_ -> startGame());

        exitButton.setOnAction(_ -> System.exit(0));

        leftPanel.getChildren().addAll(gameTitle, startButton, loadButton);
        // 内容没有定义任何成就时不显示成就入口 —— 点进去是个空页面，没有意义
        if (hasAchievements()) {
            leftPanel.getChildren().add(achievementButton);
        }
        leftPanel.getChildren().add(settingButton);
        // 剧情禁止退出时（starveil:cant_exit）不显示退出入口
        if (!com.xiaowu.game.starveil.infrastructure.persistence.DataManager.isExitBlocked()) {
            leftPanel.getChildren().add(exitButton);
        }
        return leftPanel;
    }

    private Button createStyledButton(String text) {
        Button button = UITools.createStyledButton(text);
        // 禁用按钮的焦点，防止键盘事件触发
        button.setFocusTraversable(false);
        return button;
    }

    private void showAchievementUI() {
        AchievementOverviewUI ui = new AchievementOverviewUI();
        GameManager gm = GameManager.getInstance();
        gm.getContentWithOverlays().getChildren().add((javafx.scene.Node) ui.getRoot());

        // 使用数组包装ESC事件处理器引用，以便在lambda中使用
        final InputHandler.KeyEventHandler[] achievementEscHandler = new InputHandler.KeyEventHandler[1];

        // 创建ESC事件处理器
        achievementEscHandler[0] = _ -> {
            // 只有成就面板在场景图里才处理
            if (((javafx.scene.Node) ui.getRoot()).getParent() != null) {
                ui.close();
                // 成就面板关闭后，取消注册ESC事件处理器
                javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
                delay.setOnFinished(_ -> inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, achievementEscHandler[0]));
                delay.play();
            }
        };

        // 注册ESC事件处理器
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, achievementEscHandler[0]);
    }

    private void showLoadUI() {
        SaveLoadUI ui = new SaveLoadUI(false); // false = 读档模式
        GameManager gm = GameManager.getInstance();
        gm.getContentWithOverlays().getChildren().add((javafx.scene.Node) ui.getRoot());

        // 创建ESC事件处理器引用
        InputHandler.KeyEventHandler loadEscHandler = _ -> ui.close();

        // 注册ESC事件处理器
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, loadEscHandler);

        // 设置关闭回调
        ui.setOnClose(() -> {
            // 取消注册ESC事件处理器
            inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, loadEscHandler);
            gm.getContentWithOverlays().getChildren().remove((javafx.scene.Node) ui.getRoot());
        });

        // 绑定大小到场景
        if (ui.getRoot() instanceof javafx.scene.layout.Pane pane) {
            pane.prefWidthProperty().bind(gameManager.getPrimaryStage().getScene().widthProperty());
            pane.prefHeightProperty().bind(gameManager.getPrimaryStage().getScene().heightProperty());
        }
    }

    private void showSettingUI() {
        // 使用Menu的InputHandler
        SettingManager settingManager = new SettingManager(inputHandler);

        Object settingRoot = settingManager.createRoot();

        // 绑定大小到场景
        if (settingRoot instanceof javafx.scene.layout.Region) {
            javafx.scene.layout.Region region = (javafx.scene.layout.Region) settingRoot;
            region.prefWidthProperty().bind(gameManager.getPrimaryStage().getScene().widthProperty());
            region.prefHeightProperty().bind(gameManager.getPrimaryStage().getScene().heightProperty());
        }

        // 添加到内容层
        if (settingRoot instanceof javafx.scene.Node) {
            gameManager.getContentWithOverlays().getChildren().add((javafx.scene.Node) settingRoot);
        }

        // 创建ESC事件处理器引用
        InputHandler.KeyEventHandler settingEscHandler = _ -> settingManager.closeWithoutSave();

        // 注册ESC事件处理器
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, settingEscHandler);

        // 设置关闭回调
        settingManager.setOnClose(() -> {
            // 取消注册ESC事件处理器
            inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, settingEscHandler);
            if (settingRoot instanceof javafx.scene.Node) {
                gameManager.getContentWithOverlays().getChildren().remove((javafx.scene.Node) settingRoot);
            }
        });

        // 设置场景（用于键盘事件监听）
        settingManager.setupScene(gameManager.getPrimaryStage().getScene());
    }

    private void startGame() {
        if (!canStartGame()) {
            return;
        }
        AudioManager.stopBackgroundMusic();

        Scene scene = gameManager.getPrimaryStage().getScene();

        Pane blackOverlay = new Pane();
        blackOverlay.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        blackOverlay.setOpacity(0.0);


        blackOverlay.prefWidthProperty().bind(scene.widthProperty());
        blackOverlay.prefHeightProperty().bind(scene.heightProperty());


        StackPane newRoot = new StackPane();
        newRoot.getChildren().addAll(scene.getRoot(), blackOverlay);
        scene.setRoot(newRoot);


        javafx.animation.FadeTransition fadeTransition = new javafx.animation.FadeTransition(
                Duration.seconds(2.0), blackOverlay
        );
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);


        fadeTransition.setOnFinished(_ -> {
            newRoot.getChildren().remove(blackOverlay);
            Stage primaryStage = GameManager.getInstance().getPrimaryStage();
            new RPGManager().start(primaryStage);
        });


        fadeTransition.play();
    }

    /**
     * 创建菜单内容（用于从游戏返回菜单）
     *
     * @return 菜单的UI组件
     */
    public static Parent createMenuContent() {
        BorderPane root = new BorderPane();
        Background bGround = getBackground();
        root.setBackground(bGround);

        VBox leftPanel = createMenuLeftPanel();
        root.setLeft(leftPanel);

        return root;
    }

    /**
     * 创建菜单左侧面板
     *
     * @return 菜单左侧面板
     */
    private static VBox createMenuLeftPanel() {
        VBox leftPanel = new VBox(20);
        leftPanel.setPrefWidth(300);
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setPadding(new Insets(50));

        BackgroundFill panelFill = new BackgroundFill(
                Color.rgb(0, 0, 0, 0.5), new CornerRadii(0), Insets.EMPTY
        );
        leftPanel.setBackground(new Background(panelFill));

        Font customFont = safeFont(com.xiaowu.game.starveil.infrastructure.ContentConfig.decorFont(), 20);

        Label gameTitle = new Label("雾隐星阑");
        gameTitle.setTextFill(Color.WHITE);
        gameTitle.setFont(safeFont(com.xiaowu.game.starveil.infrastructure.ContentConfig.bodyFont(), 24));

        Button startButton = createStyledButtonStatic("开始游戏");
        Button loadButton = createStyledButtonStatic("加载存档");
        Button achievementButton = createStyledButtonStatic("成就");
        Button settingButton = createStyledButtonStatic("设置");
        Button exitButton = createStyledButtonStatic("退出游戏");

        achievementButton.setFont(customFont);
        achievementButton.setOnAction(_ -> showAchievementUIMenu());
        loadButton.setFont(customFont);
        loadButton.setOnAction(_ -> showLoadUIMenu());
        settingButton.setFont(customFont);
        settingButton.setOnAction(_ -> showSettingUIMenu());
        exitButton.setTextFill(Color.RED);
        exitButton.setFont(customFont);
        exitButton.setOnAction(_ -> System.exit(0));
        startButton.setFont(customFont);
        startButton.setOnAction(_ -> startGameMenu());

        leftPanel.getChildren().addAll(gameTitle, startButton, loadButton);
        // 内容没有定义任何成就时不显示成就入口 —— 点进去是个空页面，没有意义
        if (hasAchievements()) {
            leftPanel.getChildren().add(achievementButton);
        }
        leftPanel.getChildren().add(settingButton);
        // 剧情禁止退出时（starveil:cant_exit）不显示退出入口
        if (!com.xiaowu.game.starveil.infrastructure.persistence.DataManager.isExitBlocked()) {
            leftPanel.getChildren().add(exitButton);
        }

        return leftPanel;
    }

    private static Button createStyledButtonStatic(String text) {
        Button button = UITools.createStyledButton(text);
        button.setFocusTraversable(false);
        return button;
    }

    private static void showAchievementUIMenu() {
        AchievementOverviewUI ui = new AchievementOverviewUI();
        GameManager gm = GameManager.getInstance();
        gm.getContentWithOverlays().getChildren().add((javafx.scene.Node) ui.getRoot());

        final InputHandler.KeyEventHandler[] achievementEscHandler = new InputHandler.KeyEventHandler[1];

        achievementEscHandler[0] = _ -> {
            if (((javafx.scene.Node) ui.getRoot()).getParent() != null) {
                ui.close();
                javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
                delay.setOnFinished(_ -> {
                    if (GameInstance.getCurrentInstance() != null && GameInstance.getCurrentInstance().getInputHandler() != null) {
                        GameInstance.getCurrentInstance().getInputHandler().removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, achievementEscHandler[0]);
                    }
                });
                delay.play();
            }
        };

        if (GameInstance.getCurrentInstance() != null && GameInstance.getCurrentInstance().getInputHandler() != null) {
            GameInstance.getCurrentInstance().getInputHandler().onKeyPressed(InputHandler.PAUSE_TOGGLE, achievementEscHandler[0]);
        }
    }

    private static void showLoadUIMenu() {
        SaveLoadUI ui = new SaveLoadUI(false);
        GameManager gm = GameManager.getInstance();
        gm.getContentWithOverlays().getChildren().add((javafx.scene.Node) ui.getRoot());

        InputHandler.KeyEventHandler loadEscHandler = _ -> ui.close();

        if (GameInstance.getCurrentInstance() != null && GameInstance.getCurrentInstance().getInputHandler() != null) {
            GameInstance.getCurrentInstance().getInputHandler().onKeyPressed(InputHandler.PAUSE_TOGGLE, loadEscHandler);
        }

        ui.setOnClose(() -> {
            if (GameInstance.getCurrentInstance() != null && GameInstance.getCurrentInstance().getInputHandler() != null) {
                GameInstance.getCurrentInstance().getInputHandler().removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, loadEscHandler);
            }
            gm.getContentWithOverlays().getChildren().remove((javafx.scene.Node) ui.getRoot());
        });

        if (ui.getRoot() instanceof javafx.scene.layout.Pane pane) {
            pane.prefWidthProperty().bind(gm.getPrimaryStage().getScene().widthProperty());
            pane.prefHeightProperty().bind(gm.getPrimaryStage().getScene().heightProperty());
        }
    }

    private static void showSettingUIMenu() {
        if (GameInstance.getCurrentInstance() == null || GameInstance.getCurrentInstance().getInputHandler() == null) {
            return;
        }

        InputHandler inputHandler = GameInstance.getCurrentInstance().getInputHandler();
        SettingManager settingManager = new SettingManager(inputHandler);

        Object settingRoot = settingManager.createRoot();

        if (settingRoot instanceof javafx.scene.layout.Region) {
            javafx.scene.layout.Region region = (javafx.scene.layout.Region) settingRoot;
            region.prefWidthProperty().bind(GameManager.getInstance().getPrimaryStage().getScene().widthProperty());
            region.prefHeightProperty().bind(GameManager.getInstance().getPrimaryStage().getScene().heightProperty());
        }

        if (settingRoot instanceof javafx.scene.Node) {
            GameManager.getInstance().getContentWithOverlays().getChildren().add((javafx.scene.Node) settingRoot);
        }

        InputHandler.KeyEventHandler settingEscHandler = _ -> settingManager.closeWithoutSave();

        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, settingEscHandler);

        settingManager.setOnClose(() -> {
            inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, settingEscHandler);
            if (settingRoot instanceof javafx.scene.Node) {
                GameManager.getInstance().getContentWithOverlays().getChildren().remove((javafx.scene.Node) settingRoot);
            }
        });

        settingManager.setupScene(GameManager.getInstance().getPrimaryStage().getScene());
    }

    private static void startGameMenu() {
        if (!canStartGame()) {
            return;
        }
        AudioManager.stopBackgroundMusic();

        Scene scene = GameManager.getInstance().getPrimaryStage().getScene();

        Pane blackOverlay = new Pane();
        blackOverlay.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        blackOverlay.setOpacity(0.0);

        blackOverlay.prefWidthProperty().bind(scene.widthProperty());
        blackOverlay.prefHeightProperty().bind(scene.heightProperty());

        StackPane newRoot = new StackPane();
        newRoot.getChildren().addAll(scene.getRoot(), blackOverlay);
        scene.setRoot(newRoot);

        javafx.animation.FadeTransition fadeTransition = new javafx.animation.FadeTransition(
                Duration.seconds(2.0), blackOverlay
        );
        fadeTransition.setFromValue(0.0);
        fadeTransition.setToValue(1.0);

        fadeTransition.setOnFinished(_ -> {
            newRoot.getChildren().remove(blackOverlay);
            Stage primaryStage = GameManager.getInstance().getPrimaryStage();
            new RPGManager().start(primaryStage);
        });

        fadeTransition.play();
    }
}