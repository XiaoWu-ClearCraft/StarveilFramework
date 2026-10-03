// GameInstance.java
package com.xiaowu.game.starveil.game.state;
import com.xiaowu.game.starveil.infrastructure.Fonts;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.infrastructure.audio.BGMManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.SaveDataManager;
import com.xiaowu.game.starveil.game.story.TutorialManager;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.game.attack.MagicCircleAttack;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Magic;
import com.xiaowu.game.starveil.game.ecs.comp.PlayerController;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Stamina;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.ecs.factory.EntityFactory;
import com.xiaowu.game.starveil.game.ecs.sys.NpcDeathSystem;
import com.xiaowu.game.starveil.game.ecs.sys.NpcSystem;
import com.xiaowu.game.starveil.game.ecs.sys.PlayerControlSystem;
import com.xiaowu.game.starveil.game.ecs.sys.SpriteSyncSystem;
import com.xiaowu.game.starveil.game.ecs.sys.VitalRegenSystem;
import com.xiaowu.game.starveil.game.item.Inventory;
import com.xiaowu.game.starveil.game.item.Item;
import com.xiaowu.game.starveil.game.item.ItemRegistry;
import com.xiaowu.game.starveil.render.Camera;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import com.xiaowu.game.starveil.render.engine.RenderEngineProvider;
import com.xiaowu.game.starveil.render.engine.javafx.JavaFXRenderEngine;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.ui.inventory.BackpackUI;
import com.xiaowu.game.starveil.debug.DebugUI;
import com.xiaowu.game.starveil.ui.screen.CreditsManager;
import com.xiaowu.game.starveil.ui.core.Menu;
import com.xiaowu.game.starveil.ui.screen.SaveLoadUI;
import com.xiaowu.game.starveil.ui.core.GameUI;
import com.xiaowu.game.starveil.ui.pickup.PickupPromptUI;
import com.xiaowu.game.starveil.ui.container.ContainerUI;
import com.xiaowu.game.starveil.game.quest.QuestManager;
import com.xiaowu.game.starveil.game.story.ChapterMode;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import com.xiaowu.game.starveil.ui.settings.SettingManager;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.platform.common.DialogCleanup;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class GameInstance {
    private final BorderPane root;
    private final Scene scene;
    // 移除stage字段，改为从GameManager获取当前stage
    // private final Stage stage;  // 不再保存stage引用

    // 游戏组件
    private WorldMap worldMap;
    private com.xiaowu.game.starveil.game.ecs.World ecsWorld;
    private int playerEntity = -1;
    private Camera camera;
    private RenderEngine renderEngine;
    private InputHandler inputHandler;
    private DebugUI debugUI;
    private MagicCircleAttack magicCircleAttack;

    // 视口容器
    private Pane viewport;
    private StackPane gameContainer;

    // 暂停菜单
    private boolean isPaused = false;
    private BorderPane pauseMenu;
    private javafx.scene.text.Text questDetailsText;

    /**
     * 世界是否已加载。
     *
     * <p>视觉小说模式（{@code ChapterMode.VISUAL_NOVEL}）下<b>不会</b>加载世界、
     * 也不会创建玩家实体 —— 此时 game loop 里所有依赖世界/玩家的更新都要跳过，
     * 否则会空指针。屏幕是纯黑的，背景由章节用 {@code s.image(...)} 自己铺。
     */
    private boolean worldLoaded = false;

    // 死亡界面
    private boolean isDead = false;
    private Pane deathOverlay;

    // 暂停防抖
    private long lastPauseToggleTime = 0;
    private static final long PAUSE_DEBOUNCE_TIME = 300; // 暂停切换防抖时间（毫秒）

    // 拾取防抖
    private long lastPickupTime = 0;
    private static final long PICKUP_DEBOUNCE_TIME = 300; // 拾取防抖时间（毫秒）

    // 防止在同一次按键处理中重复进入暂停
    private boolean isResuming = false;

    // 背包系统
    private Inventory inventory;
    private BackpackUI backpackUI;
    private boolean isBackpackOpen = false;
    private InputHandler.KeyEventHandler backpackEscHandler;

    // 拾取提示 UI
    private PickupPromptUI pickupPrompt;

    // 容器 UI
    private ContainerUI containerUI;
    private boolean isContainerOpen = false;
    private InputHandler.KeyEventHandler containerEscHandler;

    // ESC事件处理器引用（用于取消注册）
    private InputHandler.KeyEventHandler settingEscHandler;
    private InputHandler.KeyEventHandler saveLoadEscHandler;
    private InputHandler.KeyEventHandler deathSaveLoadEscHandler;
    private InputHandler.KeyEventHandler pauseEscHandler;

    // 设置界面

    private boolean isSettingOpen = false;

    private Pane settingRoot;

    private SettingManager setting;    // 性能监控
    private long lastFrameTime = 0;
    private long lastFPSUpdate = 0;
    private int framesSinceLastFPSUpdate = 0;
    private double currentFPS = 60.0;

    // 帧率独立的 deltaTime（以秒为单位）
    private double deltaTime = 0.0;

    public GameInstance(BorderPane root, Scene scene) {
        this.root = root;
        this.scene = scene;
        currentInstance = this;
    }

    public void initialize() {
        // ⚠️ 开档第一件事：复位对话层的跨局状态。
        //    ChatManager 是单例，会跨 GameInstance 存活；上一局如果是从
        //    「设置 → 退出」这类不走 resumeGame() 的路径离开的，
        //    pausedByGame / storyAdvanceLocked 会残留下来，导致新档的对话框点不动。
        ChatManager.getInstance().resetCrossGameState();

        // 使用已存在的RenderEngine实例(如果Menu阶段已初始化)
        // 如果没有,则创建新的实例
        if (RenderEngineProvider.getInstance().isInitialized()) {
            renderEngine = RenderEngineProvider.getInstance().getEngine();
        } else {
            renderEngine = new JavaFXRenderEngine();
            renderEngine.initialize(1200, 675);
            RenderEngineProvider.getInstance().setEngine(renderEngine);
        }

        // 初始化组件
        worldMap = new WorldMap();
        camera = new Camera(renderEngine);
        inputHandler = new InputHandler();

        // 加载保存的设置
        loadSavedSettings();

        // 构建 ECS 世界（系统管线稍后按章节模式决定要不要注册）
        ecsWorld = new World();
        ecsWorld.putResource(WorldMap.class, worldMap);
        ecsWorld.putResource(InputHandler.class, inputHandler);

        // ⚠️ 在加载世界【之前】先问第 1 章的模式 —— 纯视觉小说章节不需要世界。
        //    这一步只读章节类的 mode()，不会触发章节内容。
        ChapterMode startMode = com.xiaowu.game.starveil.game.story.ChapterDirector
                .getInstance().resolveStartMode();
        boolean wantWorld = startMode == ChapterMode.NORMAL;
        worldLoaded = false;

        worldMap.setEcsWorld(ecsWorld);
        worldMap.setInputHandler(inputHandler);

        // 创建视口
        createViewport();
        worldMap.setGameContainer(gameContainer);

        // 清空存档变量（新游戏时）并进入「有活跃存档」状态 ——
        // 后者决定存档作用域的数据键（如 starveil:cant_exit）读写的是本存档而不是全局。
        SaveDataManager.getInstance().clear();
        SaveDataManager.setActive(true);

        // 教程进度的保存范围（随存档 / 跨存档）在存档会话开始时确定，之后不再变动
        com.xiaowu.game.starveil.infrastructure.persistence.TutorialState.applyPersistMode();

        // 初始化游戏UI（血条、渐变背景、消息显示）
        GameUI.getInstance().attachToGame();

        // 初始化屏幕特效覆盖层（故障、伪蓝屏、终端文本等）
        // 默认随游戏启动自动显示；DataManager 里 overlay.enabled=false 时跳过
        com.xiaowu.game.starveil.render.effects.ScreenEffectsManager.getInstance().startWithGame(
            com.xiaowu.game.starveil.game.state.GameManager.getInstance().getPrimaryStage());

        // 初始化物品系统（背包与手持物品在两种模式下都存在）
        ItemRegistry.getInstance(); // 确保加载物品配置
        inventory = new Inventory();
        setupBackpackInput();

        // 不再硬编码挂载某张地图。
        //
        // 世界由章节自己决定：章节脚本调用 s.enterWorld(path) 时才加载。
        // 框架写死 test-world 有两个问题：换游戏必须改框架；
        // 而且「章节还没跑」和「章节跑完了」两种状态看起来是同一张图，
        // 分不清世界到底是章节挂上的还是框架自作主张挂的。
        viewport.setStyle("-fx-background-color: black;");
        if (!wantWorld) {
            Logger("INFO", "章节模式 = 仅视觉小说（VISUAL_NOVEL）：不加载世界");
        }

        // 注册容器事件监听
        GameInstance current = this;
        worldMap.addEventListener("open_container", (event, args) -> {
            // 从 args 获取 container_id，默认从 event.id 推导
            String containerId = event.id.replace("open_", "");
            current.openContainer(containerId);
            event.completeCurrentEvent();
        });

        // 设置键盘事件处理
        setupInputHandling();

        // 设置窗口事件监听
        setupWindowListeners();

        // 初始摄像机位置（视觉小说模式没有玩家可居中）
        if (worldLoaded) {
            camera.centerOnPlayer(worldMap, ecsWorld, playerEntity);
        }

        // 开始游戏循环
        startGameLoop();

        // 章节由总导演从第 1 章开始跑 —— 与「进了哪张地图」无关。
        // 放在循环启动之后，保证剧情开始时 UI / 渲染都已就绪。
        com.xiaowu.game.starveil.game.story.ChapterDirector.getInstance().start();
    }

    /**
     * 把世界挂起来：注册 ECS 系统、加载地图、创建玩家、重建依赖世界的 UI。
     *
     * <p>可以在「当前没有世界」（仅视觉小说模式）时调用 —— 这正是
     * 「从视觉小说切回普通模式」的实现方式：与游戏启动时建世界<b>走同一条代码路径</b>，
     * 所以两条路不会各自漂移。
     *
     * <p>调用方负责过场动画；本方法只做「挂载」这件事。
     *
     * @param mapFile 地图 JSON 路径
     * @param spawnX  出生点 X；{@code null} 表示用地图自带的 spawn
     * @param spawnY  出生点 Y；{@code null} 表示用地图自带的 spawn
     */
    // ==================== 玩法模式 ====================

    /** 当前玩法模式（NORMAL / GRAVITY）。 */
    public com.xiaowu.game.starveil.game.world.GameplayMode getGameplayMode() {
        return ecsWorld != null
                ? ecsWorld.gameplayMode()
                : com.xiaowu.game.starveil.game.world.GameplayMode.NORMAL;
    }

    /**
     * 切换玩法模式。
     *
     * <p>重力模式给玩家挂上 {@code Gravity} 组件并立即开始下落；
     * NORMAL 则摘掉。模式本身存在 ECS 世界之外也读得到，
     * 因为 {@code World} 会跨「卸下 → 重新挂载」保持同一个实例。
     *
     * <p>模型变体由 {@code SpriteSyncSystem} 每帧从模式同步到精灵，
     * 所以切换后不需要重新创建实体。
     */
    public void setGameplayMode(com.xiaowu.game.starveil.game.world.GameplayMode mode) {
        com.xiaowu.game.starveil.game.world.GameplayMode m =
                mode == null ? com.xiaowu.game.starveil.game.world.GameplayMode.NORMAL : mode;
        if (ecsWorld == null) {
            return;
        }
        ecsWorld.setGameplayMode(m);
        applyGravityComponent(m);
        Logger("INFO", "玩法模式已切换: " + m.name()
                + (m.modelVariant() != null ? "（模型变体 " + m.modelVariant() + "）" : ""));
    }

    /**
     * 设置重力方向，精确到角度。
     *
     * <p>约定 {@code 0° = 向下}，顺时针为正（90° 右 / 180° 上 / 270° 左）。
     * 方向是全局的，改变它不需要重建实体；贴图若没有该角度的专用资源，
     * 会自动按此角度旋转。
     */
    public void setGravityDirection(double degrees) {
        if (ecsWorld == null) {
            return;
        }
        ecsWorld.setGravityAngleDegrees(degrees);
        Logger("INFO", "重力方向已设置为: " + degrees + "°");
    }

    /** 当前重力方向（角度）。 */
    public double getGravityDirection() {
        return ecsWorld != null ? ecsWorld.gravityAngleDegrees() : 0;
    }

    private void applyGravityComponent(com.xiaowu.game.starveil.game.world.GameplayMode mode) {
        if (ecsWorld == null || playerEntity < 0) {
            return;
        }
        if (mode.hasGravity()) {
            if (ecsWorld.get(playerEntity,
                    com.xiaowu.game.starveil.game.ecs.comp.Gravity.class) == null) {
                ecsWorld.add(playerEntity, new com.xiaowu.game.starveil.game.ecs.comp.Gravity());
            }
        } else {
            ecsWorld.remove(playerEntity,
                    com.xiaowu.game.starveil.game.ecs.comp.Gravity.class);
        }
    }

    private void mountWorld(String mapFile, Double spawnX, Double spawnY) {
        // ⚠️ 先清空系统管线再注册。
        //    卸下世界时不会移除系统，所以「VN → 世界」再挂一次就会重复注册，
        //    同一个系统每帧跑两次 —— 表现为玩家和 NPC 移速翻倍。
        //    先清后建让本方法幂等，无论调用前是什么状态。
        ecsWorld.clearSystems();

        // 顺序即执行顺序
        ecsWorld.addSystem(new PlayerControlSystem());
        // 重力系统在 NORMAL 模式下自身空转，因此可以无条件注册，
        // 不必在切模式时增删系统（增删很容易和上面的重复注册问题纠缠在一起）。
        ecsWorld.addSystem(new com.xiaowu.game.starveil.game.ecs.sys.GravitySystem());
        ecsWorld.addSystem(new VitalRegenSystem());
        ecsWorld.addSystem(new NpcSystem());
        ecsWorld.addSystem(new NpcDeathSystem());
        ecsWorld.addSystem(new SpriteSyncSystem());

        // 视口底色从「视觉小说用的纯黑」恢复成正常地图底色
        viewport.setStyle("-fx-background-color: #2a2a2a;");

        worldMap.initialize();
        worldMap.setPaused(false);
        worldMap.setEcsWorld(ecsWorld);
        worldMap.setInputHandler(inputHandler);
        worldMap.setGameContainer(gameContainer);
        worldMap.loadFromFile(mapFile);
        // 地图声明本图的玩法模式与重力方向；写进 ECS 世界供各系统读取。
        // 必须在创建玩家之前设置，applyGravityComponent 才能挂上 Gravity 组件。
        ecsWorld.setGameplayMode(worldMap.getGameplayMode());
        ecsWorld.setGravityAngleDegrees(worldMap.getGravityAngleDegrees());

        double sx = spawnX != null ? spawnX
                : (worldMap.getWorldSpawn() != null ? worldMap.getWorldSpawn().x : 500);
        double sy = spawnY != null ? spawnY
                : (worldMap.getWorldSpawn() != null ? worldMap.getWorldSpawn().y : 500);
        playerEntity = EntityFactory.createPlayer(ecsWorld, worldMap.getWorld(), sx, sy);
        // 玩法模式可能要求玩家受重力（模式存在 World 上，跨挂载保持）
        applyGravityComponent(ecsWorld.gameplayMode());
        SpriteSyncSystem.reattachAll(ecsWorld);
        resetPlayerVitals();
        ensurePlayerInValidPosition();

        worldMap.setOnMapSwitchComplete(() ->
                camera.centerOnPlayer(worldMap, ecsWorld, playerEntity));

        worldLoaded = true;

        // 依赖玩家实体的 UI
        debugUI = new DebugUI(viewport, camera, worldMap, ecsWorld, playerEntity);
        debugUI.initialize();

        Pane world = worldMap.getWorld();
        Sprite pSpr = ecsWorld.get(playerEntity, Sprite.class);
        magicCircleAttack = new MagicCircleAttack(world, pSpr.view, ecsWorld, playerEntity, worldMap);
        setupMagicAttackInput();

        if (inventory == null) {
            inventory = new Inventory();
        }
        pickupPrompt = new PickupPromptUI(worldMap, inventory);
        GameUI.getInstance().addCustomUI(pickupPrompt.getRoot());

        camera.centerOnPlayer(worldMap, ecsWorld, playerEntity);

        // 世界回来了 → HUD 也应该回来（VN 模式下被隐藏过）
        GameUI.getInstance().show();
        Logger("INFO", "世界已挂载: " + mapFile);
    }

    /**
     * 从「仅视觉小说」模式进入世界 —— 走与切换地图相同的过场。
     *
     * <p>地图由<b>调用方（章节）</b>决定，引擎不需要猜「该回到哪张图」，
     * 因此也不需要做世界状态快照。
     *
     * @return 过场结束、可以继续跑章节时完成的 future
     */
    public java.util.concurrent.CompletableFuture<Void> enterNormalMode(
            String mapFile, Double spawnX, Double spawnY) {
        if (worldLoaded) {
            // 已经有世界：交给 switchMap 处理（它自带同一套过场）
            return worldMap.switchMap(mapFile, spawnX, spawnY);
        }
        if (modeTransitioning) {
            Logger("WARN", "模式切换进行中，忽略重复请求");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        modeTransitioning = true;
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();

        javafx.application.Platform.runLater(() -> {
            try {
                if (inputHandler != null) {
                    inputHandler.lockControls(InputHandler.LOCK_MAP_TRANSITION);
                }
                // 1. 渐入黑幕（与切地图一致）
                worldMap.fadeToBlack();

                // 2. 停顿，让渐入动画走完
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
                pause.setOnFinished(e -> {
                    try {
                        // 3. 挂载世界
                        mountWorld(mapFile, spawnX, spawnY);
                        Logger("INFO", "已进入世界: " + mapFile);

                        // 4. 稍等一下再拉开黑幕
                        javafx.animation.PauseTransition reveal =
                                new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
                        reveal.setOnFinished(e2 -> {
                            worldMap.fadeFromBlack();
                            if (inputHandler != null) {
                                inputHandler.unlockControls(InputHandler.LOCK_MAP_TRANSITION);
                            }
                            modeTransitioning = false;
                            future.complete(null);
                        });
                        reveal.play();
                    } catch (Exception ex) {
                        Logger("ERROR", "进入世界失败: " + ex.getMessage());
                        modeTransitioning = false;
                        future.completeExceptionally(ex);
                    }
                });
                pause.play();
            } catch (Exception ex) {
                Logger("ERROR", "进入世界失败: " + ex.getMessage());
                modeTransitioning = false;
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    /** 世界是否已加载（仅视觉小说模式为 false）。 */
    public boolean isWorldLoaded() {
        return worldLoaded;
    }

    /** 模式切换过场是否正在进行。 */
    private boolean modeTransitioning = false;

    /**
     * 从普通模式切到「仅视觉小说」模式。
     *
     * <p>走的是<b>和切换地图完全相同的过场</b>（锁操作 → 渐入黑幕 → 停顿 500ms
     * → 干活 → 停顿 300ms → 渐出黑幕 → 解锁），唯一的区别是
     * <b>不加载新地图</b>：把当前世界卸下之后黑幕底下是空的（视口黑底），
     * 由视觉小说层接着演。
     *
     * <p>返回的 future 在过场结束、可以继续跑章节时完成。
     */
    public java.util.concurrent.CompletableFuture<Void> enterVisualNovelMode() {
        if (!worldLoaded) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        if (modeTransitioning) {
            Logger("WARN", "模式切换进行中，忽略重复请求");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        modeTransitioning = true;
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();

        javafx.application.Platform.runLater(() -> {
            try {
                if (inputHandler != null) {
                    inputHandler.lockControls(InputHandler.LOCK_MAP_TRANSITION);
                }
                // 1. 渐入黑幕（与切地图一致）；keepHudHidden=true 让拉幕时不要把 HUD 显示回来
                worldMap.fadeToBlack(true);

                // 2. 停顿，让渐入动画走完
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(500));
                pause.setOnFinished(e -> {
                    try {
                        // 3. 卸下世界（不加载新地图）
                        worldMap.unloadWorld();
                        playerEntity = -1;
                        worldLoaded = false;
                        viewport.setStyle("-fx-background-color: black;");
                        // HUD（血条/体力条…）在「仅视觉小说」模式下没有意义 —— 没有世界、
                        // 没有玩家。注意这和「对话层盖住 HUD」是两回事：那个场景下世界还在。
                        GameUI.getInstance().hide();
                        Logger("INFO", "已切换到仅视觉小说模式（未加载新世界）");

                        // 4. 稍等一下再拉开黑幕，让章节的 s.image() 能显示出来
                        javafx.animation.PauseTransition reveal =
                                new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
                        reveal.setOnFinished(e2 -> {
                            worldMap.fadeFromBlack();
                            if (inputHandler != null) {
                                inputHandler.unlockControls(InputHandler.LOCK_MAP_TRANSITION);
                            }
                            modeTransitioning = false;
                            future.complete(null);
                        });
                        reveal.play();
                    } catch (Exception ex) {
                        Logger("ERROR", "切换到视觉小说模式失败: " + ex.getMessage());
                        modeTransitioning = false;
                        future.completeExceptionally(ex);
                    }
                });
                pause.play();
            } catch (Exception ex) {
                Logger("ERROR", "切换到视觉小说模式失败: " + ex.getMessage());
                modeTransitioning = false;
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    /** 玩家三项资源重置（新游戏 / 复活）。 */
    private void resetPlayerVitals() {
        if (ecsWorld == null || playerEntity < 0) return;
        Health h = ecsWorld.get(playerEntity, Health.class);
        if (h != null) h.reset();
        Stamina st = ecsWorld.get(playerEntity, Stamina.class);
        if (st != null) st.reset();
        Magic mg = ecsWorld.get(playerEntity, Magic.class);
        if (mg != null) mg.reset();
    }

    /**
     * 创建视口
     */
    private void createViewport() {
        viewport = new Pane();
        viewport.setStyle("-fx-background-color: #2a2a2a;");

        // 添加世界到视口
        viewport.getChildren().add(worldMap.getWorld());

        // 裁剪，超出视口部分不可见
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(camera.viewportWidthProperty());
        clip.heightProperty().bind(camera.viewportHeightProperty());
        viewport.setClip(clip);

        // 创建用于GameManager的容器
        gameContainer = new StackPane(viewport);
        gameContainer.setStyle("-fx-background-color: transparent;");

        // 当容器大小变化时，更新视口大小
        gameContainer.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (newBounds.getWidth() > 0 && newBounds.getHeight() > 0) {
                updateViewportSize(newBounds.getWidth(), newBounds.getHeight());
            }
        });

        root.setCenter(gameContainer);
    }

    /**
     * 更新视口大小
     */
    private void updateViewportSize(double containerWidth, double containerHeight) {
        // 使用GameManager中的当前比例
        double targetRatio = GameManager.getInstance().getAspectRatio();
        double currentRatio = containerWidth / containerHeight;

        double viewportWidth, viewportHeight;

        if (currentRatio > targetRatio) {
            // 太宽，以高度为准
            viewportHeight = containerHeight;
            viewportWidth = containerHeight * targetRatio;
        } else {
            // 太高，以宽度为准
            viewportWidth = containerWidth;
            viewportHeight = containerWidth / targetRatio;
        }

        // 更新摄像机视口大小
        camera.setViewportSize(viewportWidth, viewportHeight);

        // 更新视口位置使其居中
        viewport.setLayoutX((containerWidth - viewportWidth) / 2);
        viewport.setLayoutY((containerHeight - viewportHeight) / 2);

        // 更新视口大小
        viewport.setPrefSize(viewportWidth, viewportHeight);
        viewport.setMaxSize(viewportWidth, viewportHeight);

        // 重新计算摄像机位置，确保地图不会超出边界
        if (worldMap != null && ecsWorld != null && playerEntity >= 0) {
            camera.centerOnPlayer(worldMap, ecsWorld, playerEntity);
        }
    }

    /**
     * 设置输入处理
     */

    /**
     * 设置法阵攻击按键（Q键）
     */
    private void setupMagicAttackInput() {
        // 法阵攻击按键按下 — 开始蓄力
        inputHandler.onKeyPressed("MAGIC_ATTACK", e -> {
            if (!com.xiaowu.game.starveil.game.MagicSystem.isEnabled()) {
                return;
            }
            if (!isPaused && !isDead && !isSettingOpen && !isBackpackOpen && !magicCircleAttack.isActive()) {
                magicCircleAttack.startCharging();
            }
        });

        // 法阵攻击按键释放 — 释放攻击
        inputHandler.onKeyReleased("MAGIC_ATTACK", e -> {
            if (!com.xiaowu.game.starveil.game.MagicSystem.isEnabled()) {
                return;
            }
            if (magicCircleAttack.isActive()) {
                magicCircleAttack.releaseAttack();
            }
        });
    }

    /**
     * 设置背包按键（B键）
     */
    private void setupBackpackInput() {
        inputHandler.onKeyPressed("BACKPACK", e -> {
            if (!isPaused && !isDead && !isSettingOpen && !isBackpackOpen) {
                openBackpack();
            } else if (isBackpackOpen) {
                closeBackpack();
            }
        });
    }

    /**
     * 切换背包打开/关闭
     */
    private void toggleBackpack() {
        if (isBackpackOpen) {
            closeBackpack();
        } else {
            openBackpack();
        }
    }

    private void openBackpack() {
        isBackpackOpen = true;
        if (backpackUI == null) {
            backpackUI = new BackpackUI(inventory);
            backpackUI.setOnCloseCallback(this::closeBackpack);
            backpackUI.setUseItemCallback(this::useItem);
            backpackUI.setWorldMap(worldMap);
        } else {
            // 刷新背包格子显示（确保拾取后的物品图标正确显示）
            backpackUI.refreshAllCells();
        }
        Pane root = backpackUI.getRoot();
        root.prefWidthProperty().bind(gameContainer.widthProperty());
        root.prefHeightProperty().bind(gameContainer.heightProperty());
        overlayHost().getChildren().add(root);
        GameUI.getInstance().hide();
        worldMap.setPaused(true);
        magicCircleAttack.setPaused(true);
        BGMManager.getInstance().pauseBGM();

        // 注册ESC关闭背包
        backpackEscHandler = e -> {
            if (isBackpackOpen) closeBackpack();
        };
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, backpackEscHandler);

        // 更新手上物品显示
        updateHandSlotDisplay();
    }

    private void closeBackpack() {
        isBackpackOpen = false;
        if (backpackUI != null && overlayHost().getChildren().contains(backpackUI.getRoot())) {
            overlayHost().getChildren().remove(backpackUI.getRoot());
        }
        // 取消注册ESC
        if (backpackEscHandler != null) {
            inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, backpackEscHandler);
            backpackEscHandler = null;
        }
        worldMap.setPaused(false);
        magicCircleAttack.setPaused(false);
        BGMManager.getInstance().resumeBGM();
        if (!isPaused && !isDead && !isSettingOpen) {
            GameUI.getInstance().attachToGame();
            GameUI.getInstance().show();
        }
        // 更新手上物品显示
        updateHandSlotDisplay();
    }

    /**
     * 打开容器
     */
    public void openContainer(String containerId) {
        com.xiaowu.game.starveil.game.world.Container container = worldMap.getContainer(containerId);
        if (container == null) return;

        isContainerOpen = true;
        containerUI = new ContainerUI(container, inventory);
        containerUI.setOnCloseCallback(this::closeContainer);
        Pane root = containerUI.getRoot();
        root.prefWidthProperty().bind(gameContainer.widthProperty());
        root.prefHeightProperty().bind(gameContainer.heightProperty());
        overlayHost().getChildren().add(root);

        // ESC关闭
        containerEscHandler = e -> {
            if (isContainerOpen) closeContainer();
        };
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, containerEscHandler);
    }

    /**
     * 关闭容器
     */
    private void closeContainer() {
        isContainerOpen = false;
        if (containerUI != null && overlayHost().getChildren().contains(containerUI.getRoot())) {
            overlayHost().getChildren().remove(containerUI.getRoot());
        }
        if (containerEscHandler != null) {
            inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, containerEscHandler);
            containerEscHandler = null;
        }
        containerUI = null;
    }

    /**
     * 更新手上物品显示和鼠标样式
     */
    private void updateHandSlotDisplay() {
        String handItemId = inventory.getHandSlot();
        GameUI.getInstance().updateHandSlot(handItemId);

        // 如果手上物品改变鼠标，更新场景光标
        if (handItemId != null) {
            Item item = ItemRegistry.getInstance().getItem(handItemId);
            if (item != null && item.isChangesCursor() && scene != null) {
                if (item.getTexturePath() != null) {
                    try {
                        Image cursorImg = new Image(ResourceResolver.getResourceAsStream(item.getTexturePath()));
                        scene.setCursor(new javafx.scene.ImageCursor(cursorImg, cursorImg.getWidth() / 2, cursorImg.getHeight() / 2));
                        return;
                    } catch (Exception ignored) {}
                }
                scene.setCursor(Cursor.HAND);
                return;
            }
        }
        // 恢复默认光标
        if (scene != null) {
            scene.setCursor(Cursor.DEFAULT);
        }
    }

    private void setupInputHandling() {
        // 使用事件过滤器
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            inputHandler.handleKeyPressed(e);
            // 处理F3调试开关（视觉小说模式没有世界，DebugUI 未创建）
            if (e.getCode().ordinal() == InputHandler.DEBUG_TOGGLE && debugUI != null) {
                debugUI.toggleDebug();
            }
            // ESC按键处理现在由事件回调系统自动处理
            // 如果没有注册任何ESC处理器，则处理暂停功能
            // 检查isResuming标志，防止在同一次按键处理中重复进入暂停
            // 同时检查致谢名单是否正在显示，避免在致谢名单中暂停游戏
            boolean isCreditsPlaying = false;
            try {
                isCreditsPlaying = CreditsManager.getInstance().isPlaying();
            } catch (Exception ex) {
                // CreditsManager可能未初始化，忽略
            }

            if (e.getCode().ordinal() == InputHandler.PAUSE_TOGGLE && !isResuming && inputHandler.getKeyPressedHandlerCount(InputHandler.PAUSE_TOGGLE) == 0 && !isCreditsPlaying) {
                togglePause();
            }
        });
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> {
            inputHandler.handleKeyReleased(e);
        });

        // F11 全屏切换（通过 InputHandler 注册）
        inputHandler.onKeyPressed(javafx.scene.input.KeyCode.F11, event -> {
            com.xiaowu.game.starveil.game.state.GameManager.getInstance().toggleFullscreen();
            event.consume();
        }, true);

        // 全局滚轮事件 - 用于拾取提示切换
        scene.addEventFilter(javafx.scene.input.ScrollEvent.ANY, e -> {
            if (pickupPrompt != null && pickupPrompt.hasNearbyItems()) {
                pickupPrompt.handleScrollGlobal(e);
            }
        });
    }


    /**
     * 请求切换暂停状态（供场景过滤器/对话框等调用）。
     */
    public void requestPauseToggle() {
        // 如果玩家死亡，不允许暂停
        if (isDead) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // 防抖：如果在防抖时间内，忽略此次按键
        if (currentTime - lastPauseToggleTime < PAUSE_DEBOUNCE_TIME) {
            return;
        }

        lastPauseToggleTime = currentTime;

        if (isPaused) {
            resumeGame();
        } else {
            pauseGame();
        }
    }

    private void togglePause() {
        requestPauseToggle();
    }


    /**
     * 暂停游戏
     */
    private void pauseGame() {
        isPaused = true;
        worldMap.setPaused(true); // 设置WorldMap的暂停状态
        magicCircleAttack.setPaused(true);
        ChatManager.getInstance().setPausedByGame(true);
        Logger("DEBUG", "游戏已暂停");
        createPauseMenu();
        overlayHost().getChildren().add(pauseMenu);
        // 确保暂停界面盖住对话框等已在 modalHost 里的内容
        overlayHost().toFront();
        // 注意：这里【不】隐藏 HUD。
        // 暂停菜单加在 overlayHost()（逻辑画布最顶层）上，本身就盖在视口之上，
        // HUD 自然被覆盖。原来的 hide + 350ms 后 detach 会让 HUD 闪出闪入，
        // 还会把教程要高亮的 UI 元素一起从场景里摘掉。

        // 注册暂停菜单的ESC事件处理器（使用新的事件回调系统）
        pauseEscHandler = e -> {
            if (isPaused && !isSettingOpen) {
                resumeGame();
            }
        };
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, pauseEscHandler);

        // 暂停 BGM
        BGMManager.getInstance().pauseBGM();
    }

    /**
     * 恢复游戏
     */

    private void resumeGame() {
        // 设置恢复标志，防止在同一次按键处理中再次进入暂停
        isResuming = true;

        isPaused = false;
        worldMap.setPaused(false); // 设置WorldMap的暂停状态为false
        magicCircleAttack.setPaused(false);
        ChatManager.getInstance().setPausedByGame(false);
        Logger("DEBUG", "游戏已恢复");

        // 取消注册暂停菜单的ESC事件处理器
        if (pauseEscHandler != null) {
            inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, pauseEscHandler);
            pauseEscHandler = null;
        }

        if (pauseMenu != null && overlayHost().getChildren().contains(pauseMenu)) {

            overlayHost().getChildren().remove(pauseMenu);

        }

        // 如果设置界面还打开，也关闭它

        if (isSettingOpen) {

            closeSetting();

        }


        // HUD 全程都在（暂停时只是被菜单盖住），不需要重新挂载或淡入

        // 恢复 BGM
        BGMManager.getInstance().resumeBGM();

        // 延迟清除恢复标志
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
        delay.setOnFinished(e -> {
            isResuming = false;
        });
        delay.play();

    }

    /**
     * 创建暂停菜单
     */
    private void createPauseMenu() {
        if (pauseMenu != null) {
            // 每次打开时刷新任务面板（任务可能在暂停后接受）
            if (questDetailsText != null) updateQuestDetails(questDetailsText);
            return;
        }

        // 使用BorderPane替代StackPane，右侧保持透明
        pauseMenu = new BorderPane();
        pauseMenu.setStyle("-fx-background-color: transparent;");
        pauseMenu.setMouseTransparent(false);
        pauseMenu.setFocusTraversable(true);
        // 移除键盘事件过滤器，允许ESC事件传递到scene的事件过滤器
        // pauseMenu.addEventFilter(javafx.scene.input.KeyEvent.ANY, e -> e.consume());

        // 绑定大小到gameContainer
        pauseMenu.prefWidthProperty().bind(gameContainer.widthProperty());
        pauseMenu.prefHeightProperty().bind(gameContainer.heightProperty());

        // 创建左侧面板，参考Menu.java中的样式
        javafx.scene.layout.VBox leftPanel = new javafx.scene.layout.VBox(20);
        leftPanel.setPrefWidth(300);
        leftPanel.setAlignment(javafx.geometry.Pos.CENTER);
        leftPanel.setPadding(new javafx.geometry.Insets(50));

        javafx.scene.layout.BackgroundFill panelFill = new javafx.scene.layout.BackgroundFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.5), new javafx.scene.layout.CornerRadii(0), javafx.geometry.Insets.EMPTY);
        leftPanel.setBackground(new javafx.scene.layout.Background(panelFill));

        // 加载自定义字体
        javafx.scene.text.Font customFont = Fonts.safeFont("starveil:fonts/handwriting.ttf", 20);

        // 添加标题
        javafx.scene.control.Label gameTitle = new javafx.scene.control.Label("雾隐星阑");
        gameTitle.setTextFill(javafx.scene.paint.Color.WHITE);
        gameTitle.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 24));

        // 创建按钮
        javafx.scene.control.Button resumeButton = createStyledButton("继续游戏");
        resumeButton.setFont(customFont);

        javafx.scene.control.Button saveButton = createStyledButton("保存游戏");
        saveButton.setFont(customFont);

        javafx.scene.control.Button loadButton = createStyledButton("加载游戏");
        loadButton.setFont(customFont);

        javafx.scene.control.Button settingButton = createStyledButton("设置");
        settingButton.setFont(customFont);

        javafx.scene.control.Button exitButton = createStyledButton("退出游戏");
        exitButton.setTextFill(javafx.scene.paint.Color.RED);
        exitButton.setFont(customFont);

        // 设置按钮事件
        resumeButton.setOnAction(e -> resumeGame());

        saveButton.setOnAction(e -> {
            // 打开保存游戏界面
            openSaveLoadUI(true);
        });

        loadButton.setOnAction(e -> {
            // 打开加载游戏界面
            openSaveLoadUI(false);
        });

        settingButton.setOnAction(e -> {
            // 打开设置界面
            openSetting();
        });

        exitButton.setOnAction(e -> {
            try {
                DialogCleanup.closeAllDialogs("章节");

                // 收掉对话层并复位跨局状态。这条路不会走 resumeGame()，
                // 不清理的话 pausedByGame 会一直留在 true，下一局对话框点不动。
                ChatManager.getInstance().forceCloseAll();

                AudioManager.stopBackgroundMusic();

                // 离开存档会话：此后存档作用域的数据键（如 starveil:cant_exit）
                // 回落到全局配置，而不是继续沿用刚刚那一局的存档值。
                SaveDataManager.setActive(false);

                Menu menu = new Menu();
                // 从GameManager获取当前stage，确保使用的是最新的stage
                Stage currentStage = GameManager.getInstance().getPrimaryStage();
                menu.start(currentStage, false);

                // 暂停游戏世界
                if (worldMap != null) {
                    worldMap.setPaused(true);
                }
            } catch (Exception ey) {
                ey.printStackTrace();
            }
        });

        leftPanel.getChildren().addAll(gameTitle, resumeButton, saveButton, loadButton, settingButton);
        // 剧情禁止退出时（starveil:cant_exit）不显示退出入口。
        // 暂停菜单每次打开都会重建，所以这里读到的总是最新的标记值。
        if (!com.xiaowu.game.starveil.infrastructure.persistence.DataManager.isExitBlocked()) {
            leftPanel.getChildren().add(exitButton);
        }

        // 创建右侧任务面板
        javafx.scene.layout.VBox rightPanel = createQuestPanel();

        // 将左侧面板放在BorderPane的左边，右侧任务面板放在右边
        pauseMenu.setLeft(leftPanel);
        pauseMenu.setRight(rightPanel);
    }

    /**
     * 创建任务面板
     */
    private javafx.scene.layout.VBox createQuestPanel() {
        javafx.scene.layout.VBox questPanel = new javafx.scene.layout.VBox(15);
        questPanel.setPrefWidth(400);
        questPanel.setAlignment(javafx.geometry.Pos.CENTER);
        questPanel.setPadding(new javafx.geometry.Insets(50));

        javafx.scene.layout.BackgroundFill questPanelFill = new javafx.scene.layout.BackgroundFill(javafx.scene.paint.Color.rgb(0, 0, 0, 0.5), new javafx.scene.layout.CornerRadii(0), javafx.geometry.Insets.EMPTY);
        questPanel.setBackground(new javafx.scene.layout.Background(questPanelFill));

        // 任务标题
        javafx.scene.control.Label questTitle = new javafx.scene.control.Label("当前任务");
        questTitle.setTextFill(javafx.scene.paint.Color.rgb(255, 105, 180));
        questTitle.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 24));

        // 任务详情显示区域
        questDetailsText = new javafx.scene.text.Text();
        questDetailsText.setFill(javafx.scene.paint.Color.WHITE);
        questDetailsText.setFont(Fonts.safeFont("starveil:fonts/zhengjing.ttf", 14));
        questDetailsText.setWrappingWidth(300);

        // 更新任务详情
        updateQuestDetails(questDetailsText);

        questPanel.getChildren().addAll(questTitle, questDetailsText);

        return questPanel;
    }

    /**
     * 更新任务详情显示
     */
    private void updateQuestDetails(javafx.scene.text.Text questDetails) {
        QuestManager.QuestState currentQuest = QuestManager.getInstance().getCurrentQuest();

        if (currentQuest == null) {
            questDetails.setText("当前没有进行中的任务");
            return;
        }

        String details = "任务名称: " + currentQuest.definition.name + "\n\n" + "描述: " + currentQuest.definition.description + "\n\n" + "详细内容: " + currentQuest.definition.details + "\n\n" + "进度: " + currentQuest.currentProgress + " / " + currentQuest.definition.maxProgress;

        questDetails.setText(details);
    }

    /**
     * 打开设置界面
     */
    private void openSetting() {
        if (isSettingOpen) return; // 避免重复打开
        isSettingOpen = true;
        setting = new SettingManager(inputHandler);
        settingRoot = (Pane) setting.createRoot();
        // 绑定大小到gameContainer
        if (settingRoot != null) {
            javafx.scene.layout.Region region = (javafx.scene.layout.Region) settingRoot;
            region.prefWidthProperty().bind(gameContainer.widthProperty());
            region.prefHeightProperty().bind(gameContainer.heightProperty());
        }
        // 设置关闭回调
        setting.setOnClose(this::closeSetting);
        // 创建并保存ESC处理回调
        settingEscHandler = e -> {
            if (isSettingOpen) {
                setting.closeWithoutSave();
            }
        };
        // 注册ESC处理回调（使用新的事件回调系统，后注册的优先级更高）
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, settingEscHandler);
        // 设置场景（用于键盘事件监听）
        setting.setupScene(scene);
        // 添加到gameContainer
        if (settingRoot instanceof javafx.scene.Node) {
            overlayHost().getChildren().add((javafx.scene.Node) settingRoot);
        }
        // 隐藏 GameUI
        GameUI.getInstance().hide();
    }

    /**
     * 打开存档/读档界面
     */
    private void openSaveLoadUI(boolean isSaveMode) {
        SaveLoadUI saveLoadUI = new SaveLoadUI(isSaveMode);
        // 创建ESC处理回调
        saveLoadEscHandler = e -> saveLoadUI.close();
        saveLoadUI.setOnClose(() -> {
            // 取消注册ESC处理回调
            if (saveLoadEscHandler != null) {
                inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, saveLoadEscHandler);
                saveLoadEscHandler = null;
            }
            // 关闭存档/读档界面
            overlayHost().getChildren().remove((javafx.scene.Node) saveLoadUI.getRoot());
            // 重新显示 GameUI（仅在游戏未暂停、未死亡且未打开设置时）
            if (!isPaused && !isDead && !isSettingOpen) {
                GameUI.getInstance().attachToGame();
                GameUI.getInstance().show();
            }
        });
        // 注册ESC处理回调（使用新的事件回调系统，后注册的优先级更高）
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, saveLoadEscHandler);
        // 绑定大小到gameContainer - 通过直接设置尺寸而非属性绑定
        // saveLoadUI.getRoot().prefWidthProperty().bind(gameContainer.widthProperty());
        // saveLoadUI.getRoot().prefHeightProperty().bind(gameContainer.heightProperty());
        // 添加到gameContainer
        overlayHost().getChildren().add((javafx.scene.Node) saveLoadUI.getRoot());
        // 隐藏 GameUI
        GameUI.getInstance().hide();
    }

    /**
     * 在死亡界面上打开存档/读档界面
     */
    private void openSaveLoadUIOnDeath(boolean isSaveMode) {
        SaveLoadUI saveLoadUI = new SaveLoadUI(isSaveMode);
        // 创建ESC处理回调
        deathSaveLoadEscHandler = e -> {
            saveLoadUI.close();
            // 关闭存档界面后，如果还在死亡状态，恢复 BGM 暂停状态
            if (isDead) {
                BGMManager.getInstance().pauseBGM();
            }
        };
        saveLoadUI.setOnClose(() -> {
            // 取消注册ESC处理回调
            if (deathSaveLoadEscHandler != null) {
                inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, deathSaveLoadEscHandler);
                deathSaveLoadEscHandler = null;
            }
            // 关闭存档/读档界面
            overlayHost().getChildren().remove((javafx.scene.Node) saveLoadUI.getRoot());
        });
        // 注册ESC处理回调（使用新的事件回调系统，后注册的优先级更高）
        inputHandler.onKeyPressed(InputHandler.PAUSE_TOGGLE, deathSaveLoadEscHandler);
        // 绑定大小到gameContainer - 通过直接设置尺寸而非属性绑定
        // saveLoadUI.getRoot().prefWidthProperty().bind(gameContainer.widthProperty());
        // saveLoadUI.getRoot().prefHeightProperty().bind(gameContainer.heightProperty());
        // 添加到gameContainer（会自动显示在死亡界面的上面，因为后添加的节点在上面）
        overlayHost().getChildren().add((javafx.scene.Node) saveLoadUI.getRoot());
    }

    /**
     * 显示死亡界面
     */
    private void showDeathScreen() {
        if (isDead) return; // 避免重复显示
        isDead = true;
        worldMap.setPaused(true); // 设置WorldMap的暂停状态为true

        // 关闭背包（如果打开）
        if (isBackpackOpen) {
            closeBackpack();
        }

        // 在动画开始前禁用用户移动输入（按来源加锁，不会影响其它系统的锁）
        inputHandler.lockControls(com.xiaowu.game.starveil.input.InputHandler.LOCK_DEATH);

        // 创建半透明遮罩
        deathOverlay = new Pane();
        deathOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8);");
        deathOverlay.setMouseTransparent(false);
        deathOverlay.setFocusTraversable(true);
        deathOverlay.setOpacity(0); // 初始不透明度为0，用于渐入动画

        // 创建中央容器
        VBox centerBox = new VBox(30);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(50));

        // 标题
        javafx.scene.text.Text titleText = new javafx.scene.text.Text("你失去了生命");
        titleText.setFill(javafx.scene.paint.Color.WHITE);
        titleText.setFont(Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 48));

        // 按钮容器
        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);

        // 从存档点继续按钮
        javafx.scene.control.Button continueButton = createStyledButton("从存档点继续");
        continueButton.setOnAction(e -> {
            // 打开存档界面（显示在死亡界面的上面）
            openSaveLoadUIOnDeath(false);
        });

        // 返回主菜单按钮
        javafx.scene.control.Button menuButton = createStyledButton("返回主菜单");
        menuButton.setOnAction(e -> {
            DialogCleanup.closeAllDialogs("章节");
            // 同上：复活失败回主菜单也要复位，否则残留标志会带进下一局。
            ChatManager.getInstance().forceCloseAll();
            closeDeathScreen();
            // 离开存档会话，存档作用域的数据键回落到全局配置
            SaveDataManager.setActive(false);
            javafx.application.Platform.runLater(() -> {
                try {
                    Menu menu = new Menu();
                    // 从GameManager获取当前stage
                    Stage currentStage = GameManager.getInstance().getPrimaryStage();
                    menu.start(currentStage);
                } catch (Exception ex) {
                    Logger("ERROR", "返回主菜单失败: " + ex.getMessage());
                }
            });
        });

        buttonBox.getChildren().addAll(continueButton, menuButton);
        centerBox.getChildren().addAll(titleText, buttonBox);

        // 居中显示
        deathOverlay.getChildren().add(centerBox);
        centerBox.layoutXProperty().bind(deathOverlay.widthProperty().subtract(centerBox.widthProperty()).divide(2));
        centerBox.layoutYProperty().bind(deathOverlay.heightProperty().subtract(centerBox.heightProperty()).divide(2));

        // 绑定大小到gameContainer
        deathOverlay.prefWidthProperty().bind(gameContainer.widthProperty());
        deathOverlay.prefHeightProperty().bind(gameContainer.heightProperty());

        // 添加到gameContainer
        overlayHost().getChildren().add(deathOverlay);

        // 添加渐入动画
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(javafx.util.Duration.millis(800), deathOverlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // 暂停 BGM
        BGMManager.getInstance().pauseBGM();
        // 隐藏 GameUI
        GameUI.getInstance().hide();
    }

    /**
     * 关闭死亡界面
     */
    private void closeDeathScreen() {
        isDead = false;

        // 移除死亡界面
        if (deathOverlay != null && overlayHost().getChildren().contains(deathOverlay)) {
            overlayHost().getChildren().remove(deathOverlay);
        }
        deathOverlay = null;

        // 释放死亡界面的输入锁。
        // 注意：原代码在 showDeathScreen 里 disableControls 后从来没有对应的 enable，
        // 死亡复生后操作会一直锁着 —— 按来源记账后这里必须显式解锁。
        InputHandler ih = GameInstance.getInputHandlerStatic();
        if (ih != null) {
            ih.unlockControls(InputHandler.LOCK_DEATH);
        }

        // 重新显示 GameUI（仅在游戏未暂停且未打开设置时）
        if (!isPaused && !isSettingOpen) {
            GameUI.getInstance().attachToGame();
            GameUI.getInstance().show();
        }

        // 恢复 BGM
        BGMManager.getInstance().resumeBGM();
        worldMap.setPaused(false); // 设置WorldMap的暂停状态为false
        if (deathOverlay != null && overlayHost().getChildren().contains(deathOverlay)) {
            overlayHost().getChildren().remove(deathOverlay);
        }
        deathOverlay = null;

        // 恢复 BGM
        BGMManager.getInstance().resumeBGM();
    }

    /**
     * 关闭设置界面
     */
    private void closeSetting() {
        // 取消注册ESC处理回调（使用新的事件回调系统）
        if (settingEscHandler != null) {
            inputHandler.removeKeyPressedHandler(InputHandler.PAUSE_TOGGLE, settingEscHandler);
            settingEscHandler = null;
        }

        isSettingOpen = false;
        if (settingRoot != null && settingRoot instanceof javafx.scene.Node) {
            javafx.scene.Node node = (javafx.scene.Node) settingRoot;
            if (overlayHost().getChildren().contains(node)) {
                overlayHost().getChildren().remove(node);
            }
        }
        settingRoot = null;
        setting = null;

        // 重新显示 GameUI（仅在游戏未暂停且未死亡时）
        if (!isPaused && !isDead) {
            GameUI.getInstance().attachToGame();
            GameUI.getInstance().show();
        }
    }

    /**
     * 创建样式化的按钮
     */

    private javafx.scene.control.Button createStyledButton(String text) {
        javafx.scene.control.Button button = new javafx.scene.control.Button(text);
        button.setPrefSize(200, 50);
        button.setTextFill(javafx.scene.paint.Color.WHITE);
        button.setFont(javafx.scene.text.Font.font(16));
        // 使用粉色系
        javafx.scene.layout.BackgroundFill buttonFill = new javafx.scene.layout.BackgroundFill(javafx.scene.paint.Color.web(ContentConfig.primaryColor()), new javafx.scene.layout.CornerRadii(10), javafx.geometry.Insets.EMPTY
        );
        button.setBackground(new javafx.scene.layout.Background(buttonFill));
        button.setOnMouseEntered(e -> {
            // 悬停效果 - 更深的粉色
            javafx.scene.layout.BackgroundFill hoverFill = new javafx.scene.layout.BackgroundFill(
                    javafx.scene.paint.Color.web(ContentConfig.secondaryColor()), new javafx.scene.layout.CornerRadii(10), javafx.geometry.Insets.EMPTY
            );
            button.setBackground(new javafx.scene.layout.Background(hoverFill));
        });
        button.setOnMouseExited(e -> {
            // 恢复原色
            javafx.scene.layout.BackgroundFill originalFill = new javafx.scene.layout.BackgroundFill(

                    javafx.scene.paint.Color.web(ContentConfig.primaryColor()), new javafx.scene.layout.CornerRadii(10), javafx.geometry.Insets.EMPTY
            );
            button.setBackground(new javafx.scene.layout.Background(originalFill));
        });
        return button;
    }


    /**
     * 设置窗口监听
     */
    private void setupWindowListeners() {
        // 使用GameManager的primaryStage，因为全屏切换可能导致stage被替换
        Stage currentStage = GameManager.getInstance().getPrimaryStage();
        currentStage.fullScreenProperty().addListener((obs, oldValue, newValue) -> {
            if (debugUI != null) {
                debugUI.setFullscreen(newValue);
            }

            // 全屏切换时，重新计算视口大小
            if (gameContainer.getWidth() > 0 && gameContainer.getHeight() > 0) {
                updateViewportSize(gameContainer.getWidth(), gameContainer.getHeight());

                // 全屏切换后，重新限制摄像机位置
                // 这确保在全屏/窗口切换时摄像机不会超出边界
                camera.centerOnPlayer(worldMap, ecsWorld, playerEntity);
            }
        });
    }

    /**
     * 开始游戏循环
     */
    private void startGameLoop() {
        lastFrameTime = System.nanoTime();
        lastFPSUpdate = lastFrameTime;

        AnimationTimer loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 计算 deltaTime（以秒为单位）
                long currentTime = now;
                deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0;
                lastFrameTime = currentTime;

                // 限制 deltaTime 范围，防止异常值
                if (deltaTime < 0.001) {
                    deltaTime = 0.001; // 最小值，防止除零或过小
                } else if (deltaTime > 0.1) {
                    deltaTime = 0.1; // 最大值，防止窗口切换时跳跃
                }

                framesSinceLastFPSUpdate++;

                // 读取玩家实体组件（供本帧使用）
                Health ph = ecsWorld.get(playerEntity, Health.class);
                Transform pt = ecsWorld.get(playerEntity, Transform.class);
                Sprite pSpr = ecsWorld.get(playerEntity, Sprite.class);
                Facing pf = pSpr != null ? pSpr.facing : Facing.LEFT;

                // 检查玩家是否死亡
                if (!isDead && ph != null && ph.current <= 0) {
                    showDeathScreen();
                }

                // 如果游戏暂停、设置界面打开、背包打开或玩家死亡，则只更新FPS显示，不更新游戏逻辑
                if (isPaused || isSettingOpen || isBackpackOpen || isDead) {
                    deltaTime = 0; // 确保暂停时deltaTime为0
                    updateFPS(now);
                    if (debugUI != null) {
                        debugUI.setCurrentFPS(currentFPS);
                        debugUI.updateDebugInfo(now);
                    }
                    return;
                }

                // 1. 更新FPS
                updateFPS(now);

                // 2. 驱动 ECS 系统管线：玩家移动/体力、魔力恢复/自动回血、NPC AI、NPC 死亡、精灵同步
                //    仅视觉小说模式（worldLoaded=false）不跑 —— 那时没有世界也没有实体
                if (worldLoaded) {
                    ecsWorld.update(deltaTime);
                }

                // 重新读取本帧修改后的组件
                ph = ecsWorld.get(playerEntity, Health.class);
                pt = ecsWorld.get(playerEntity, Transform.class);
                pSpr = ecsWorld.get(playerEntity, Sprite.class);
                pf = pSpr != null ? pSpr.facing : Facing.LEFT;

                // 2.6. 法阵攻击期间锁定事件（防止切换地图等操作）
                if (magicCircleAttack != null) {
                    if (magicCircleAttack.isActive()) {
                        worldMap.lockEvents();
                    } else {
                        worldMap.unlockEvents();
                    }
                }

                // 2.7. 更新受击闪烁效果
                if (ph != null) {
                    GameUI.getInstance().updateHitFlash(ph.percentage());
                }

                // 3. 世界相关的更新。
                //    两种情况要跳过：
                //    ① 视觉小说模式（worldLoaded=false）—— 世界和玩家都不存在，直接跳过避免空指针；
                //    ② 剧情暂停中 —— 对话期间不该触发地图事件、交互提示或拾取，
                //       否则玩家点对话就把地图事件点出来了。注意 ECS 上面已经跑过，
                //       它内部只会驱动被章节显式豁免的系统（例如 NPC 移动）。
                boolean worldPaused = !worldLoaded || ecsWorld.isStoryPaused();
                if (!worldPaused) {
                    // 更新摄像机（使用 deltaTime 使平滑效果与帧率无关）
                    camera.updateWithCircularDeadZone(worldMap, ecsWorld, playerEntity, deltaTime);
                    // 检查地图事件（带玩家朝向和交互按钮状态）
                    boolean fKeyEventTriggered = worldMap.checkEvents(pt.x, pt.y, pf, inputHandler.isInteractPressed());
                    // 检查并显示交互提示
                    worldMap.checkAndShowPrompts(pt.x, pt.y, pf);

                    // 更新拾取提示 UI
                    pickupPrompt.update(pt.x, pt.y);

                    // F键交互优先级：1.地图事件  2.拾取掉落物品  3.使用手上物品
                    if (inputHandler.isInteractPressed()
                            && !worldMap.isPlayerInInteractZone(pt.x, pt.y)
                            && !worldMap.isTransitioning()) {
                        if (!fKeyEventTriggered) {
                            // 优先拾取掉落物品（带防抖）
                            long currentTimeMs = currentTime / 1_000_000;
                            if (pickupPrompt.hasSelected() && (currentTimeMs - lastPickupTime) >= PICKUP_DEBOUNCE_TIME) {
                                lastPickupTime = currentTimeMs;
                                pickupPrompt.pickUpSelected();
                            } else {
                                // 其次使用手上物品
                                String handItem = inventory.getHandSlot();
                                if (handItem != null) {
                                    Item handItemObj = ItemRegistry.getInstance().getItem(handItem);
                                    if (handItemObj != null && handItemObj.getEvent("onUse") != null) {
                                        useItem(handItem);
                                    }
                                }
                            }
                        }
                    }
                }

                // 更新教程系统（仅在游戏正常运行时）
                if (!isPaused && !isDead && !isSettingOpen && !isBackpackOpen) {
                    // 传入 deltaTime：教程里「按住 N 秒」的进度靠它累加
                    TutorialManager.getInstance().update(deltaTime);
                }

                // 更新致谢名单系统（检查待启动的致谢名单）
                CreditsManager.getInstance().update();

                // 4. 更新调试信息（视觉小说模式没有世界，DebugUI 未创建）
                if (debugUI != null) {
                    debugUI.setCurrentFPS(currentFPS);
                    debugUI.updateDebugInfo(now);
                }
            }
        };
        loop.start();
    }

    /**
     * 更新FPS
     */
    private void updateFPS(long now) {
        // 0.5秒
        long FPS_UPDATE_INTERVAL = 500_000_000;
        if (now - lastFPSUpdate >= FPS_UPDATE_INTERVAL) {
            currentFPS = framesSinceLastFPSUpdate / ((now - lastFPSUpdate) / 1_000_000_000.0);
            framesSinceLastFPSUpdate = 0;
            lastFPSUpdate = now;
        }
    }

    /**
     * 确保玩家在合法位置（不在空气墙内）
     */
    private void ensurePlayerInValidPosition() {
        if (worldMap == null || ecsWorld == null || playerEntity < 0) return;

        Transform t = ecsWorld.get(playerEntity, Transform.class);
        if (t == null) return;

        // 检查玩家是否在空气墙内
        if (worldMap.isBlockedByAirWall(t.x, t.y, t.width, t.height)) {
            Logger("WARNING", "玩家在非法位置（空气墙内），正在调整位置");

            // 优先使用地图出生点
            if (worldMap.getWorldSpawn() != null) {
                t.x = worldMap.getWorldSpawn().x;
                t.y = worldMap.getWorldSpawn().y;
                Logger("INFO", "玩家位置已重置到地图出生点");
            } else {
                // 如果没有配置 spawn 点，尝试找到最近的合法位置
                double[] validPos = findNearestValidPosition(t.x, t.y, t.width, t.height);
                if (validPos != null) {
                    t.x = validPos[0];
                    t.y = validPos[1];
                    Logger("INFO", "玩家位置已调整到合法位置: (" + validPos[0] + ", " + validPos[1] + ")");
                } else {
                    // 使用默认位置
                    t.x = 500;
                    t.y = 500;
                    Logger("INFO", "玩家位置已重置到默认位置 (500, 500)");
                }
            }
        }
    }

    /**
     * 查找最近的合法位置
     */
    private double[] findNearestValidPosition(double startX, double startY, double width, double height) {
        // 搜索半径
        double searchRadius = 200;
        double step = 10;

        // 从中心向外搜索
        for (double radius = step; radius <= searchRadius; radius += step) {
            // 检查圆周上的点
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                double testX = startX + radius * Math.cos(radians);
                double testY = startY + radius * Math.sin(radians);

                // 检查是否在边界内
                if (!worldMap.isPlayerOutOfBounds(testX, testY, width, height)) {
                    // 检查是否在空气墙内
                    if (!worldMap.isBlockedByAirWall(testX, testY, width, height)) {
                        return new double[]{testX, testY};
                    }
                }
            }
        }

        return null;
    }

    /**
     * 加载保存的设置
     */
    private void loadSavedSettings() {
        // 加载显示设置
        String aspectRatio = DataManager.get("starveil:setting.aspect_ratio", "16:9");
        if (aspectRatio != null) {
            GameManager.getInstance().setAspectRatio(aspectRatio);
        }

        String fullscreen = DataManager.get("starveil:setting.fullscreen", "false");
        if (fullscreen != null) {
            GameManager.getInstance().setFullscreen(Boolean.parseBoolean(fullscreen));
        }

        // 加载渲染设置（已在Launcher中应用，这里不需要重复）
        // String vsync = com.xiaowu.game.data.DataManager.get("starveil:setting.vsync", "true");
        // if (vsync != null) {
        //     System.setProperty("prism.vsync", vsync);
        // }

        // String gpu = com.xiaowu.game.data.DataManager.get("starveil:setting.gpu", "true");
        // if (gpu != null) {
        //     System.setProperty("prism.forceGPU", gpu);
        // }

        // String shaderCache = com.xiaowu.game.data.DataManager.get("starveil:setting.shader_cache", "true");
        // if (shaderCache != null) {
        //     System.setProperty("prism.cacheshaders", shaderCache);
        // }

        // 加载音频设置（已在Launcher中应用，这里不需要重复）
        // String bgmVolume = com.xiaowu.game.data.DataManager.get("starveil:setting.bgm_volume", "0.3");
        // if (bgmVolume != null) {
        //     com.xiaowu.game.audio.AudioManager.setBackgroundMusicVolumeGlobal(Double.parseDouble(bgmVolume));
        // }

        // String sfxVolume = com.xiaowu.game.data.DataManager.get("starveil:setting.sfx_volume", "0.5");
        // if (sfxVolume != null) {
        //     com.xiaowu.game.audio.AudioManager.setSoundEffectsVolumeGlobal(Double.parseDouble(sfxVolume));
        // }

        Logger("INFO", "游戏设置已加载");
    }

    /** 当前 ECS 世界（无游戏实例时为 null）。 */
    public static World getEcsWorld() {
        return currentInstance != null ? currentInstance.ecsWorld : null;
    }

    /** 玩家实体 id（无游戏实例时为 -1）。 */
    public static int getPlayerEntityId() {
        return currentInstance != null ? currentInstance.playerEntity : -1;
    }

    public static Health getPlayerHealthComp() {
        World w = getEcsWorld();
        int e = getPlayerEntityId();
        return w != null && e >= 0 ? w.get(e, Health.class) : null;
    }

    public static Stamina getPlayerStaminaComp() {
        World w = getEcsWorld();
        int e = getPlayerEntityId();
        return w != null && e >= 0 ? w.get(e, Stamina.class) : null;
    }

    public static Magic getPlayerMagicComp() {
        World w = getEcsWorld();
        int e = getPlayerEntityId();
        return w != null && e >= 0 ? w.get(e, Magic.class) : null;
    }

    public static Transform getPlayerTransformComp() {
        World w = getEcsWorld();
        int e = getPlayerEntityId();
        return w != null && e >= 0 ? w.get(e, Transform.class) : null;
    }

    public static Sprite getPlayerSpriteComp() {
        World w = getEcsWorld();
        int e = getPlayerEntityId();
        return w != null && e >= 0 ? w.get(e, Sprite.class) : null;
    }

    public static WorldMap getWorldMap() {
        if (currentInstance != null) {
            return currentInstance.getWorldMapInstance();
        }
        return null;
    }

    /**
     * 获取InputHandler实例（用于弹窗管理等需要访问输入控制的地方）
     */
    private static GameInstance currentInstance;

    public static InputHandler getInputHandlerStatic() {
        if (currentInstance != null) {
            return currentInstance.getInputHandler();
        }
        return null;
    }

    /**
     * 游戏当前实测帧率。
     *
     * <p>供屏幕特效（例如「降帧保持」）判断请求的帧率是否高于实际帧率——
     * 遮罩层只允许降低可见帧率，不允许提升。
     *
     * @return 实测 FPS；游戏实例尚未创建时返回 60.0
     */
    public static double getCurrentFPS() {
        GameInstance instance = currentInstance;
        return instance != null && instance.currentFPS > 0 ? instance.currentFPS : 60.0;
    }

    /**
     * 获取当前游戏实例
     */
    public static GameInstance getCurrentInstance() {
        return currentInstance;
    }

    /**
     * 检查游戏是否暂停
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * 检查玩家是否死亡
     */
    public boolean isDead() {
        return isDead;
    }

    /**
     * 检查设置界面是否打开
     */
    public boolean isSettingOpen() {
        return isSettingOpen;
    }

    public boolean isBackpackOpen() {
        return isBackpackOpen;
    }

    public Inventory getInventory() {
        return inventory;
    }

    /**
     * 使用物品（解析 events.onUse 并执行效果）
     * @return 是否成功使用
     */
    public boolean useItem(String itemId) {
        if (itemId == null) return false;
        Item item = ItemRegistry.getInstance().getItem(itemId);
        if (item == null) return false;

        String onUse = item.getEvent("onUse");
        if (onUse == null || onUse.isEmpty()) {
            GameUI.getInstance().addMessage(item.getName() + " 无法使用");
            return false;
        }

        String[] parts = onUse.split(":");
        String type = parts[0];
        boolean success = true;

        Health health = getPlayerHealthComp();
        Stamina stamina = getPlayerStaminaComp();
        Magic magic = getPlayerMagicComp();

        switch (type) {
            case "heal":
                double healAmount = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
                if (health != null) {
                    health.current = Math.min(health.max, health.current + healAmount);
                    GameUI.getInstance().setHealth(health.percentage());
                }
                break;
            case "magic":
                double magicAmount = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
                if (magic != null) {
                    magic.current = Math.min(magic.max, magic.current + magicAmount);
                    magic.isDepleted = false;
                    GameUI.getInstance().setMagic(magic.max > 0 ? magic.current / magic.max : 0, magic.isDepleted);
                }
                break;
            case "stamina":
                if ("full".equals(parts[1])) {
                    if (stamina != null) stamina.reset();
                } else {
                    double staminaAmount = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
                    if (stamina != null) {
                        stamina.current = Math.min(stamina.max, stamina.current + staminaAmount);
                        if (stamina.current >= stamina.minToSprint) {
                            stamina.isExhausted = false;
                        }
                    }
                }
                if (stamina != null) {
                    GameUI.getInstance().setStamina(stamina.max > 0 ? stamina.current / stamina.max : 0, stamina.isExhausted);
                }
                break;
            default:
                success = false;
                break;
        }

        if (success) {
            // 消耗物品：从背包或手上移除
            consumeItem(itemId);
            GameUI.getInstance().addMessage("使用了 " + item.getName());
        }
        return success;
    }

    /**
     * 从背包或手上移除指定物品（消耗）
     */
    private void consumeItem(String itemId) {
        // 先检查手上
        if (itemId.equals(inventory.getHandSlot())) {
            inventory.setHandSlot(null);
            GameUI.getInstance().updateHandSlot(null);
            return;
        }
        // 再检查背包
        for (int i = 0; i < inventory.getBackpackSize(); i++) {
            if (itemId.equals(inventory.getBackpackSlot(i))) {
                inventory.setBackpackSlot(i, null);
                return;
            }
        }
    }

    /**
     * 获取世界地图（实例版本）
     */
    public static WorldMap getWorldMapInstance() {
        return currentInstance != null ? currentInstance.worldMap : null;
    }

    /**
     * 清除旧实例的死亡UI（用于切换游戏实例时）
     */
    public static void clearOldInstanceDeathUI() {
        if (currentInstance != null && currentInstance.isDead) {
            currentInstance.closeDeathScreen();
        }
    }

    /**
     * 覆盖层宿主：优先用 GameManager 的逻辑画布最顶层（可盖在聊天对话框上方），
     * 回退到游戏容器。
     */
    private javafx.scene.layout.Pane overlayHost() {
        StackPane m = GameManager.getInstance().getModalHost();
        return m != null ? m : gameContainer;
    }

    /**
     * 实际游戏视口（16:9、居中、带裁剪的那个 Pane），世界就挂在它上面。
     *
     * <p>HUD 应当挂在这里 ——
     * <ul>
     *   <li>与世界<b>共用同一坐标系</b>，不会被逻辑画布的缩放/黑边影响；</li>
     *   <li>暂停菜单、死亡界面等加在 {@code gameContainer} 或 {@code modalHost} 上，
     *       都是视口的<b>兄弟或更上层</b>，天然盖住 HUD ——
     *       于是不再需要「暂停/剧情时隐藏 HUD」那套 hide/show/detach 操作。</li>
     * </ul>
     */
    public static javafx.scene.layout.Pane getViewportPane() {
        GameInstance inst = getCurrentInstance();
        return inst != null ? inst.viewport : null;
    }

    /**
     * 获取InputHandler实例（用于设置界面）
     */

    public InputHandler getInputHandler() {
        return inputHandler;
    }
}