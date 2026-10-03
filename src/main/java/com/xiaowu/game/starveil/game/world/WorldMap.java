// WorldMap.java
package com.xiaowu.game.starveil.game.world;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaowu.game.starveil.infrastructure.persistence.SaveManager;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Attributes;
import com.xiaowu.game.starveil.game.ecs.comp.Npc;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.ecs.factory.EntityFactory;
import com.xiaowu.game.starveil.game.event.EventCallbackManager;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.render.AnimationSheet;
import com.xiaowu.game.starveil.ui.core.GameUI;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class WorldMap {
    private final Pane world;
    private int worldWidth = 3000;
    private int worldHeight = 3000;
    private boolean wordMarkers = false; // 是否显示世界坐标标记（可从JSON配置）

    private final List<Rectangle> airWalls = new ArrayList<>();
    private final List<MapEvent> events = new ArrayList<>();
    private final Map<String, List<MapEventListener>> listeners = new HashMap<>();
    /** 按所有者记录的绑定关系（见 {@link #bindEvent}）。 */
    private final Map<String, Map<String, MapEventListener>> boundOwners = new HashMap<>();
    private final List<Animation> deathAnimations = new ArrayList<>();
    private static final Map<String, Boolean> triggeredEvents = new HashMap<>(); // 记录已触发的事件
    private long mapSwitchTime = 0; // 记录地图切换时间（用于重生冷却）
    private static final long RESPAWN_COOLDOWN = 500; // 重生冷却时间（毫秒）
    private final Map<String, Boolean> eventWasMatching = new HashMap<>(); // 记录事件是否曾经匹配过（用于重复执行事件）
    private boolean mapLoaded = false; // 地图是否已加载完成（用于触发join事件）

    // 事件锁定（法阵攻击期间阻止事件触发）
    private boolean eventLocked = false;

    // Overlay 视图映射（key = overlay ID）
    private final Map<String, ImageView> overlayViews = new HashMap<>();

    // 容器映射（key = container ID）
    private final Map<String, Container> containers = new HashMap<>();

    public void lockEvents() { this.eventLocked = true; }
    public void unlockEvents() { this.eventLocked = false; }
    public boolean isEventLocked() { return eventLocked; }

    // 地图切换相关
    private String currentMapFile;
    private String currentMapName;  // 地图名称
    private Integer currentWorldId; // 地图ID
    private String chapterClassName; // 所属章节类名

    /**
     * 本图声明的玩法模式（地图 JSON 的 {@code "gameplayMode"} 字段）。
     *
     * <p>缺省 / 无法识别时为 {@link GameplayMode#NORMAL}。地图是玩法的天然归属单位——
     * 「在原地图基础上加重力」正是给某张图换一个玩法模式。
     */
    private GameplayMode gameplayMode = GameplayMode.NORMAL;

    /** 重力方向（角度）。0° 向下，顺时针为正。 */
    private double gravityAngleDegrees = 0;
    private SpawnPoint worldSpawn;
    private Pane transitionOverlay;
    private Pane loadingIndicator;
    private Timeline loadingAnimation;
    private boolean isTransitioning = false;

    // 内部管理的玩家和容器（玩家实体通过 ECS World 查询）
    private World ecsWorld;
    private InputHandler inputHandler;
    private Pane gameContainer;

    // 暂停状态
    private boolean isPaused = false;

    // 已死亡的 NPC ID 集合（跨存档持久化）
    private final Set<String> deadNpcIds = new HashSet<>();

    // 地图切换完成回调
    private Runnable onMapSwitchComplete;

    public WorldMap() {
        world = new Pane();
        world.setPrefSize(worldWidth, worldHeight);
        String backgroundColor = "#1e1e1e";
        world.setStyle("-fx-background-color: " + backgroundColor + ";");
    }

    public void initialize() {
        // 初始化时添加边界标记
        addMapBoundaries();
    }

    /**
     * 绑定 ECS 世界（NPC 实体与玩家查询均通过它）。
     */
    public void setEcsWorld(World ecsWorld) {
        this.ecsWorld = ecsWorld;
    }

    public World getEcsWorld() {
        return ecsWorld;
    }

    private int playerEntity() {
        return ecsWorld != null ? EntityFactory.findPlayer(ecsWorld) : -1;
    }

    private Transform playerTransform() {
        int pe = playerEntity();
        return pe >= 0 ? ecsWorld.get(pe, Transform.class) : null;
    }

    private Sprite playerSprite() {
        int pe = playerEntity();
        return pe >= 0 ? ecsWorld.get(pe, Sprite.class) : null;
    }

    private boolean hasPlayer() {
        return playerEntity() >= 0;
    }

    public void setInputHandler(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    /**
     * 检查交互按钮是否被按下
     */
    public boolean isInteractPressed() {
        return inputHandler != null && inputHandler.isInteractPressed();
    }

    public void setGameContainer(Pane gameContainer) {
        this.gameContainer = gameContainer;
    }

    public Pane getGameContainer() {
        return gameContainer;
    }

    /**
     * 设置地图切换完成回调
     *
     * @param callback 回调函数
     */
    public void setOnMapSwitchComplete(Runnable callback) {
        this.onMapSwitchComplete = callback;
    }

    /**
     * 设置暂停状态
     */
    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (paused) {
            for (Animation a : deathAnimations) a.pause();
        } else {
            for (Animation a : deathAnimations) a.play();
        }
    }

    /**
     * 获取暂停状态
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * 清空地图数据
     */
    private void clearMapData() {
        // 保存玩家视图引用
        javafx.scene.Node playerView = null;
        Sprite playerSpr = playerSprite();
        if (playerSpr != null && playerSpr.view != null) {
            playerView = playerSpr.view;
        }

        // 清空事件（不清空triggeredEvents，因为它是跨地图保存的事件状态）
        events.clear();

        // 不清空事件监听器，因为它们是全局的，不应该因为切换地图而被删除

        // 销毁本图 NPC 实体（视图随 children 清空移除）
        if (ecsWorld != null) {
            for (int e : ecsWorld.view(Npc.class)) {
                ecsWorld.destroy(e);
            }
        }

        // 销毁本图掉落物实体
        clearDroppedItems();

        // 清空死亡 NPC 记录（地图切换时重置，存档保存/加载由外部处理）
        deadNpcIds.clear();

        // 清空空气墙
        airWalls.clear();

        // 清空世界节点（保留基本结构）
        if (world != null) {
            world.getChildren().clear();
        }

        // 重新添加玩家视图
        if (playerView != null) {
            world.getChildren().add(playerView);
        }

        // 重置地图加载标志
        mapLoaded = false;

        LoggerManager.Logger("DEBUG", "地图数据已清空");
    }

    /**
     * 从 JSON 文件加载地图定义
     */
    public void loadFromFile(String filePath) {
        currentMapFile = filePath;

        // 清空现有地图数据
        clearMapData();

        InputStream in = ResourceResolver.getResourceAsStream(filePath);
        try {
            // 如果类路径找不到，再尝试直接从文件系统读取（例如 TestGame/resources）。
            //
            // 注意：命名空间路径（starveil:...）不能拿去做文件系统查找 ——
            // 里面的冒号在 Windows 上是非法字符，Path.of 会抛 InvalidPathException。
            // 框架不带内容时地图必然查不到、兜底必然被走到，所以必须先判掉。
            if (in == null
                    && !com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver
                            .isNamespaceReference(filePath)) {
                try {
                    Path p = Path.of(filePath);
                    if (Files.exists(p)) in = Files.newInputStream(p);
                } catch (Exception e) {
                    LoggerManager.Logger("DEBUG",
                            "文件系统回退失败: " + filePath + " - " + e.getMessage());
                }
            }

            if (in == null) {
                // 地图由内容项目提供；没有就跳过加载（空地图）而不是当成错误。
                LoggerManager.Logger("INFO", "地图文件不存在，跳过加载: " + filePath);
                return;
            }

            try (InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8); java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                String json = sb.toString();
                loadFromJson(json);
            }
        } catch (IOException e) {
            LoggerManager.Logger("ERROR", "尝试加载JSON地图资源时失败" + e.getMessage());
        }
    }

    /**
     * 从 JSON 字符串加载地图定义
     */
    public void loadFromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        // 解析地图名称和ID
        currentMapName = root.has("name") ? root.get("name").getAsString() : null;
        currentWorldId = root.has("id") ? root.get("id").getAsInt() : null;

        // 解析所属章节
        // 地图 JSON 里遗留的 "chapter" 字段：读取但不再产生效果（章节改由 ChapterDirector 驱动）
        chapterClassName = root.has("chapter") ? root.get("chapter").getAsString() : null;

        // 玩法模式：缺省 NORMAL。写 "GRAVITY" 即把 y 当作 z、实体竖直自动下落。
        GameplayMode parsedMode = root.has("gameplayMode")
                ? GameplayMode.fromName(root.get("gameplayMode").getAsString())
                : GameplayMode.NORMAL;
        if (root.has("gameplayMode") && parsedMode == null) {
            LoggerManager.Logger("WARNING",
                    "地图 gameplayMode 无法识别: " + root.get("gameplayMode").getAsString()
                            + "，回退到 NORMAL");
        }
        gameplayMode = parsedMode != null ? parsedMode : GameplayMode.NORMAL;

        // 重力方向（角度）。0° 向下，顺时针为正；缺省 0。
        gravityAngleDegrees = root.has("gravityDirection")
                ? root.get("gravityDirection").getAsDouble()
                : 0;

        // 世界坐标标记
        wordMarkers = root.has("wordMarkers") && root.get("wordMarkers").getAsBoolean();

        // 背景
        if (root.has("background")) {
            String bg = root.get("background").getAsString();
            setBackgroundImage(bg);
        } else {
            // 如果没有背景图片，检查是否有 width 和 height 来设置地图大小
            if (root.has("width") && root.has("height")) {
                worldWidth = root.get("width").getAsInt();
                worldHeight = root.get("height").getAsInt();
                world.setPrefSize(worldWidth, worldHeight);
                // 清除旧的标记和边界
                clearMarkersAndBoundaries();
                // 添加标记和边界
                addWorldMarkers();
                addMapBoundaries();
                LoggerManager.Logger("INFO", "世界大小已通过 width/height 设置为: " + worldWidth + "x" + worldHeight);
            }
        }

        // 事件
        if (root.has("event") && root.get("event").isJsonArray()) {
            JsonArray evts = root.getAsJsonArray("event");
            for (JsonElement e : evts) {
                JsonObject o = e.getAsJsonObject();
                MapEvent me = MapEvent.fromJson(o);
                events.add(me);
            }
        }

        // 覆盖层（overlay） 支持对象或数组形式
        if (root.has("overlay")) {
            JsonElement overlayEl = root.get("overlay");
            if (overlayEl.isJsonObject()) {
                JsonObject overlayObj = overlayEl.getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : overlayObj.entrySet()) {
                    String key = entry.getKey();
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    createOverlay(key, obj);
                }
            } else if (overlayEl.isJsonArray()) {
                for (JsonElement el : overlayEl.getAsJsonArray()) {
                    JsonObject obj = el.getAsJsonObject();
                    // 期望有 id 字段
                    String key = obj.has("id") ? obj.get("id").getAsString() : "overlay";
                    createOverlay(key, obj);
                }
            }
        }

        // 空气墙
        if (root.has("airWall") && root.get("airWall").isJsonArray()) {
            JsonArray aw = root.getAsJsonArray("airWall");
            for (JsonElement e : aw) {
                JsonObject o = e.getAsJsonObject();
                int x = o.has("x") ? o.get("x").getAsInt() : 0;
                int y = o.has("y") ? o.get("y").getAsInt() : 0;
                int x_to = o.has("x_to") ? o.get("x_to").getAsInt() : x;
                int y_to = o.has("y_to") ? o.get("y_to").getAsInt() : y;
                // 归一化两个对角点：允许 (x,y) / (x_to,y_to) 以任意顺序书写。
                // 旧写法 Math.max(1, x_to - x) 在反向书写时会静默退化成 1px 宽的线，
                // 空气墙形同虚设（wu-home.json 的斜墙条目就踩了这个坑）。
                // 逻辑与回归测试见 AirWallGeometry。
                AirWallGeometry.Rect box = AirWallGeometry.of(x, y, x_to, y_to);
                Rectangle r = new Rectangle(box.x(), box.y(), box.width(), box.height());
                r.setFill(Color.TRANSPARENT);
                r.setStroke(null);
                r.setMouseTransparent(true);
                airWalls.add(r);
                world.getChildren().add(r);
            }
        }

        // NPC（简单占位）
        if (root.has("npc") && root.get("npc").isJsonObject()) {
            JsonObject npcObj = root.getAsJsonObject("npc");
            for (Map.Entry<String, JsonElement> e : npcObj.entrySet()) {
                String id = e.getKey();
                JsonObject def = e.getValue().getAsJsonObject();
                createNPC(id, def);
            }
        }

        // 世界出生点
        if (root.has("spawn") && root.get("spawn").isJsonObject()) {
            JsonObject spawn = root.getAsJsonObject("spawn");
            worldSpawn = new SpawnPoint();
            worldSpawn.x = spawn.has("x") ? spawn.get("x").getAsDouble() : 0;
            worldSpawn.y = spawn.has("y") ? spawn.get("y").getAsDouble() : 0;
            LoggerManager.Logger("INFO", "世界出生点: (" + worldSpawn.x + ", " + worldSpawn.y + ")");
        }

        // 标记地图已加载
        mapLoaded = true;

        // 章节不再由地图 JSON 驱动。
        // 旧逻辑读地图里的 "chapter" 字段、靠字符串猜类名（见已删除的 createChapterInstance），
        // 猜不到就静默跳过 —— 结果「章节」和「进了哪张地图」被绑死，
        // 想单纯放一段对话也必须先有一张地图。
        // 现在由 ChapterDirector 从第 1 章开始、按章节代码自行推进。
        // 地图 JSON 里遗留的 "chapter" 字段会被读取但不再产生任何效果。

        // 如果玩家存在，立即触发 join 事件（防止存档加载时事件不触发）
        if (hasPlayer()) {
            LoggerManager.Logger("DEBUG", "地图加载完成，立即触发 join 事件");
            triggerJoinEvents();
        }
    }

    private void setBackgroundImage(String resourcePath) {
        try {
            Image img = loadImage(resourcePath);
            if (img != null) {
                // 根据图片尺寸设置世界大小
                worldWidth = (int) img.getWidth();
                worldHeight = (int) img.getHeight();

                // 更新world的大小
                world.setPrefSize(worldWidth, worldHeight);

                // 清除旧的标记和边界
                clearMarkersAndBoundaries();

                // 移除旧的背景图片（如果有），但保留玩家视图
                Sprite pSpr = playerSprite();
                javafx.scene.Node playerView = pSpr != null ? pSpr.view : null;
                int beforeSize = world.getChildren().size();
                world.getChildren().removeIf(node -> node instanceof ImageView && node != playerView);
                int afterSize = world.getChildren().size();
                LoggerManager.Logger("DEBUG", "移除旧背景: 移除前=" + beforeSize + ", 移除后=" + afterSize + ", 保留玩家=" + (playerView != null));

                // 创建ImageView并设置大小
                ImageView iv = new ImageView(img);
                iv.setFitWidth(worldWidth);
                iv.setFitHeight(worldHeight);
                iv.setPreserveRatio(false);

                // 放在最底层
                world.getChildren().addFirst(iv);

                // 添加标记和边界
                addWorldMarkers();
                addMapBoundaries();

                LoggerManager.Logger("INFO", "世界大小已根据图片设置为: " + worldWidth + "x" + worldHeight);
            }
        } catch (Exception ex) {
            // 忽略，保持原背景色
            LoggerManager.Logger("ERROR", "加载背景图片失败: " + ex.getMessage());
        }
    }

    private Image loadImage(String resourcePath) {
        // try classpath first
        try (InputStream is = ResourceResolver.getResourceAsStream(resourcePath)) {
            if (is != null) return new Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
        } catch (Exception ignored) {
        }
        // try file
        try {
            Path p = Path.of(resourcePath);
            if (Files.exists(p)) {
                try (InputStream is = Files.newInputStream(p)) {
                    return new Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void createOverlay(String key, JsonObject obj) {
        JsonObject loc = obj.has("location") ? obj.getAsJsonObject("location") : null;
        double x = loc != null && loc.has("x") ? loc.get("x").getAsDouble() : 0;
        double y = loc != null && loc.has("y") ? loc.get("y").getAsDouble() : 0;
        String texture = obj.has("texture") ? obj.get("texture").getAsString() : null;
        javafx.scene.Node node = texture != null
                ? com.xiaowu.game.starveil.render.TextureNodeFactory.load(texture, -1, -1) : null;
        ImageView iv = (node instanceof ImageView i) ? i : new ImageView();
        iv.setLayoutX(x);
        iv.setLayoutY(y);
        if (iv.getImage() != null) {
            iv.setFitWidth(iv.getImage().getWidth());
            iv.setFitHeight(iv.getImage().getHeight());
        }

        // 保存 overlay 视图引用
        overlayViews.put(key, iv);

        // 解析容器字段
        if (obj.has("container") && obj.get("container").isJsonObject()) {
            JsonObject containerObj = obj.getAsJsonObject("container");
            String containerId = containerObj.has("id") ? containerObj.get("id").getAsString() : key;
            List<String> items = new ArrayList<>();
            if (containerObj.has("items") && containerObj.get("items").isJsonArray()) {
                JsonArray itemsArr = containerObj.getAsJsonArray("items");
                for (JsonElement e : itemsArr) {
                    if (e.isJsonPrimitive()) {
                        items.add(e.getAsString());
                    }
                }
            }
            containers.put(containerId, new Container(containerId, items));
        }

        // 事件支持（onLeft, onRight）
        if (obj.has("event") && obj.get("event").isJsonObject()) {
            JsonObject ev = obj.getAsJsonObject("event");
            if (ev.has("onLeft") && ev.get("onLeft").isJsonArray()) {
                JsonArray arr = ev.getAsJsonArray("onLeft");
                iv.addEventHandler(MouseEvent.MOUSE_CLICKED, me -> {
                    if (me.getButton() == MouseButton.PRIMARY) {
                        for (JsonElement e : arr) {
                            MapEvent mapEvent = MapEvent.fromJson(e.getAsJsonObject());
                            triggerEventIfMatches(mapEvent, null);
                        }
                    }
                });
            }
            if (ev.has("onRight") && ev.get("onRight").isJsonArray()) {
                JsonArray arr = ev.getAsJsonArray("onRight");
                iv.addEventHandler(MouseEvent.MOUSE_CLICKED, me -> {
                    if (me.getButton() == MouseButton.SECONDARY) {
                        for (JsonElement e : arr) {
                            MapEvent mapEvent = MapEvent.fromJson(e.getAsJsonObject());
                            triggerEventIfMatches(mapEvent, null);
                        }
                    }
                });
            }
        }

        world.getChildren().add(iv);
    }

    /**
     * 从 JSON def 创建 NPC 实体（组件装配见 EntityFactory）。
     */
    private void createNPC(String id, JsonObject def) {
        if (ecsWorld == null) {
            LoggerManager.Logger("WARN", "ECS World 未绑定，跳过 NPC 生成: " + id);
            return;
        }
        EntityFactory.createNpc(ecsWorld, world, this, id, def);
    }

    private void triggerEventIfMatches(MapEvent evt, Map<String, Object> contextualArgs) {
        // 检查事件是否已完成（triggered && !isExecuting）
        if (triggeredEvents.containsKey(evt.id) && triggeredEvents.get(evt.id) && !evt.isExecuting) {
            return;
        }

        // 检查事件是否正在执行中
        if (evt.isExecuting) {
            return;
        }

        // 使用默认 player_x/player_y 检查，contextualArgs 可为空
        double px = contextualArgs != null && contextualArgs.containsKey("player_x") ? ((Number) contextualArgs.get("player_x")).doubleValue() : 0;
        double py = contextualArgs != null && contextualArgs.containsKey("player_y") ? ((Number) contextualArgs.get("player_y")).doubleValue() : 0;
        if (evt.matches(px, py)) {
            // 标记事件为正在执行中（但不标记为已完成）
            evt.isExecuting = true;
            // notify listeners
            List<MapEventListener> ls = listeners.get(evt.id);
            if (ls != null) {
                LoggerManager.Logger("INFO", "覆盖层事件触发: " + evt.id);

                // 如果是非重复事件，先删除监听器（防止重复触发）
                if (!evt.repeat) {
                    listeners.remove(evt.id);
                }

                // 使用副本执行，避免在迭代过程中修改原列表
                List<MapEventListener> lsCopy = new ArrayList<>(ls);
                for (MapEventListener l : lsCopy) {
                    try {
                        l.onEvent(evt, evt.argsAsMap(px, py));
                    } catch (Exception e) {
                        LoggerManager.Logger("ERROR", "事件执行异常: " + evt.id + " - " + e.getMessage());
                    }
                }
            }
        }
    }

    public boolean checkEvents(double playerX, double playerY, Facing playerFacing, boolean interactPressed) {
        // 法阵攻击期间锁定事件，防止切换地图等操作
        if (eventLocked) {
            return false;
        }

        boolean fKeyEventTriggered = false;

        // 首次调用checkEvents时，自动触发join事件并重新触发未完成的事件
        if (mapLoaded) {
            // 记录刚刚触发的 join 事件，防止在 retryIncompleteEvents 中重复触发
            List<String> newlyTriggeredJoinEvents = new ArrayList<>();
            for (MapEvent evt : events) {
                if (evt.enabled && "join".equalsIgnoreCase(evt.on)) {
                    newlyTriggeredJoinEvents.add(evt.id);
                }
            }

            triggerJoinEvents();
            retryIncompleteEvents(playerX, playerY, newlyTriggeredJoinEvents);
            mapLoaded = false; // 防止重复触发
        }

        // 检查是否在重生冷却期内
        long timeSinceSwitch = System.currentTimeMillis() - mapSwitchTime;
        boolean inRespawnCooldown = timeSinceSwitch < RESPAWN_COOLDOWN;

        for (MapEvent evt : events) {
            // 检查事件是否启用
            if (!evt.enabled) {
                continue;
            }

            boolean matches = evt.checkTriggerCondition(playerX, playerY, playerFacing, interactPressed);

            if (evt.repeat) {
                // 重复执行模式：需要先不满足条件，再次满足时才能触发
                boolean wasMatching = eventWasMatching.containsKey(evt.id) && eventWasMatching.get(evt.id);

                // 如果在重生冷却期内，不触发事件
                if (inRespawnCooldown && matches && !wasMatching) {
                    LoggerManager.Logger("DEBUG", "重生冷却期内，跳过事件: " + evt.id);
                    eventWasMatching.put(evt.id, matches);
                    continue;
                }

                if (matches && !wasMatching && !evt.cooldown) {
                    // 首次满足条件，触发事件并进入冷却
                    List<MapEventListener> ls = listeners.get(evt.id);
                    if (ls != null) {
                        LoggerManager.Logger("INFO", "重复事件触发: " + evt.id);
                        evt.cooldown = true;

                        // 使用副本执行
                        List<MapEventListener> lsCopy = new ArrayList<>(ls);
                        for (MapEventListener l : lsCopy) {
                            try {
                                l.onEvent(evt, evt.argsAsMap(playerX, playerY));
                            } catch (Exception e) {
                                LoggerManager.Logger("ERROR", "事件执行异常: " + evt.id + " - " + e.getMessage());
                            }
                        }
                    }
                }

                // 更新匹配状态
                eventWasMatching.put(evt.id, matches);

                // 如果不满足条件，退出冷却
                if (!matches && evt.cooldown) {
                    evt.cooldown = false;
                }
            } else {
                // 单次执行模式
                // 如果事件已完成（triggered && !isExecuting），则跳过
                if (triggeredEvents.containsKey(evt.id) && triggeredEvents.get(evt.id) && !evt.isExecuting) {
                    continue;
                }

                // 如果事件正在执行中（isExecuting），则跳过
                if (evt.isExecuting) {
                    continue;
                }

                if (matches) {
                    List<MapEventListener> ls = listeners.get(evt.id);
                    if (ls != null) {
                        LoggerManager.Logger("INFO", "事件触发: " + evt.id);
                        if ("interact".equalsIgnoreCase(evt.on)) {
                            fKeyEventTriggered = true;
                        }
                        // 标记事件为正在执行中（但不标记为已完成）
                        evt.isExecuting = true;

                        // 如果是非重复事件，先删除监听器（防止重复触发）
                        if (!evt.repeat) {
                            listeners.remove(evt.id);
                        }

                        // 使用副本执行，避免在迭代过程中修改原列表
                        List<MapEventListener> lsCopy = new ArrayList<>(ls);
                        for (MapEventListener l : lsCopy) {
                            try {
                                l.onEvent(evt, evt.argsAsMap(playerX, playerY));
                            } catch (Exception e) {
                                LoggerManager.Logger("ERROR", "事件执行异常: " + evt.id + " - " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
        return fKeyEventTriggered;
    }

    /**
     * 检查玩家是否在某个 interact 事件的触发范围内
     */
    public boolean isPlayerInInteractZone(double playerX, double playerY) {
        for (MapEvent evt : events) {
            if ("interact".equalsIgnoreCase(evt.on) && evt.enabled && evt.matches(playerX, playerY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查并显示交互提示消息
     */
    public void checkAndShowPrompts(double playerX, double playerY, Facing playerFacing) {
        String currentPrompt = null;

        for (MapEvent evt : events) {
            // 检查是否应该显示提示
            if (evt.shouldShowPrompt(playerX, playerY, playerFacing)) {
                // 替换<message>中的<input>为按键名称
                String prompt = evt.message.replace("<input>", "F");
                currentPrompt = prompt;
                break;  // 只显示第一个匹配的提示
            }
        }

        // 使用与repeat事件相同的逻辑：需要先不满足条件，再次满足时才显示
        if (currentPrompt != null) {
            String promptKey = "prompt:" + currentPrompt;

            // 检查之前是否显示过这个提示
            boolean wasShowing = eventWasMatching.containsKey(promptKey) && eventWasMatching.get(promptKey);

            if (!wasShowing) {
                // 首次满足条件，检查GameUI是否正在显示消息
                GameUI gameUI = GameUI.getInstance();
                if (!gameUI.isDisplayingMessage()) {
                    // 没有正在显示的消息，可以显示提示
                    gameUI.addMessage(currentPrompt);
                    eventWasMatching.put(promptKey, true);
                }
            }
        } else {
            // 不满足条件时，清除所有提示的匹配状态
            for (String key : eventWasMatching.keySet()) {
                if (key.startsWith("prompt:")) {
                    eventWasMatching.put(key, false);
                }
            }
        }
    }

    /**
     * NPC 引导步骤事件完成回调（由 MapEvent.completeCurrentEvent 触发）。
     */
    public void completeNpcGuideStep(WorldMap.MapEvent event) {
        if (ecsWorld == null) return;
        for (int e : ecsWorld.view(Npc.class)) {
            Npc npc = ecsWorld.get(e, Npc.class);
            if (npc == null) continue;
            for (Npc.GuideStep step : npc.guideSteps) {
                if (step.event == event && step.eventTriggered && !step.eventCompleted) {
                    step.eventCompleted = true;
                    LoggerManager.Logger("DEBUG", "NPC " + npc.id + " 当前步骤的事件已完成");
                    return;
                }
            }
        }
    }

    /** 记录死亡 NPC id（死亡动画结束后由系统回调）。 */
    public void markNpcDead(String npcId) {
        deadNpcIds.add(npcId);
    }

    public List<Animation> getDeathAnimations() {
        return deathAnimations;
    }

    /** 通用安全位置查询（脱离 NPC 类型）。 */
    public double[] findSafePosition(double x, double y, double w, double h) {
        if (!isBlockedByAirWall(x, y, w, h)) return new double[]{x, y};

        final int maxRadius = 800; // 最大搜索半径
        final int step = 8; // 搜索步长像素

        for (int r = step; r <= maxRadius; r += step) {
            int checks = Math.max(8, r / step * 8);
            for (int i = 0; i < checks; i++) {
                double ang = 2.0 * Math.PI * i / checks;
                double cx = x + r * Math.cos(ang);
                double cy = y + r * Math.sin(ang);
                // 边界限制
                if (cx < 0 || cy < 0 || cx + w > worldWidth || cy + h > worldHeight)
                    continue;
                if (!isBlockedByAirWall(cx, cy, w, h)) return new double[]{cx, cy};
            }
        }

        return new double[]{x, y};
    }

    /**
     * 查找沿从(sx,sy)到(tx,ty)方向最近的可通行口（NPC放置后不会卡住）
     * 返回可通行点坐标 [x,y]，找不到则返回 null
     */
    public double[] findNearestPassage(double sx, double sy, double tx, double ty, double nw, double nh) {
        double dx = tx - sx;
        double dy = ty - sy;
        double len = Math.hypot(dx, dy);
        if (len < 1) return null;
        double step = 8.0;
        int steps = Math.max(2, (int) (len / step));

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double px = sx + dx * t;
            double py = sy + dy * t;
            if (isBlockedByAirWall(px, py, nw, nh)) {
                // 在此处尝试横向搜索通道
                double baseAng = Math.atan2(dy, dx);
                double maxLateral = 400; // 减少最大横向搜索距离以提高性能
                double lateralStep = 16.0; // 增大步长以减少搜索次数
                for (double r = lateralStep; r <= maxLateral; r += lateralStep) {
                    // 搜索两侧（左右）
                    for (int sign : new int[]{1, -1}) {
                        double ang = baseAng + sign * Math.PI / 2; // 正交方向开始
                        // 在这一圆弧上尝试多个点（向左右展开）
                        int samples = Math.max(3, (int) (r / lateralStep) * 3); // 减少采样点数
                        for (int s = 0; s < samples; s++) {
                            double theta = -Math.PI / 2 + (Math.PI * s / samples);
                            double cx = px + Math.cos(baseAng + theta) * r;
                            double cy = py + Math.sin(baseAng + theta) * r;
                            // 检查边界
                            if (cx < 0 || cy < 0 || cx + nw > worldWidth || cy + nh > worldHeight)
                                continue;
                            if (!isBlockedByAirWall(cx, cy, nw, nh)) {
                                // 额外检查：从该通道点到目标前方一定距离内是否有直接可通行路径的样本
                                boolean pathClear = true;
                                double checkLen = Math.min(100, Math.hypot(tx - cx, ty - cy)); // 减少检查距离
                                int chkSteps = Math.max(1, (int) (checkLen / 16)); // 增大检查步长
                                for (int k = 1; k <= chkSteps; k++) {
                                    double tt = (double) k / chkSteps;
                                    double ix = cx + (tx - cx) * tt;
                                    double iy = cy + (ty - cy) * tt;
                                    if (isBlockedByAirWall(ix, iy, nw, nh)) {
                                        pathClear = false;
                                        break;
                                    }
                                }
                                if (pathClear) return new double[]{cx, cy};
                                // 若 pathClear 为 false，则仍可将该 cx,cy 作为候选通道（供 FOLLOW 停留）
                                return new double[]{cx, cy};
                            }
                        }
                    }
                }

                // 如果横向搜索没有找到通道，尝试更广泛的搜索
                // 先检查直接的横向移动
                double[] lateralDirections = {Math.PI / 2, -Math.PI / 2, Math.PI, -Math.PI};
                for (double dir : lateralDirections) {
                    double cx = px + Math.cos(dir) * 64;
                    double cy = py + Math.sin(dir) * 64;
                    // 检查边界
                    if (cx < 0 || cy < 0 || cx + nw > worldWidth || cy + nh > worldHeight)
                        continue;
                    if (!isBlockedByAirWall(cx, cy, nw, nh)) {
                        return new double[]{cx, cy};
                    }
                }
            }
        }
        return null;
    }

    public void addEventListener(String eventId, MapEventListener listener) {
        List<MapEventListener> list = listeners.computeIfAbsent(eventId, k -> new ArrayList<>());

        // 如果列表为空，直接添加
        if (list.isEmpty()) {
            list.add(listener);
            LoggerManager.Logger("DEBUG", "添加事件监听器: " + eventId + " (总数: " + list.size() + ")");
            return;
        }

        // 检查是否已有该事件的监听器
        // 对于同一事件的监听器，我们只允许一个存在
        // 这样可以防止章节重复初始化导致的重复注册
        LoggerManager.Logger("DEBUG", "事件 " + eventId + " 已有 " + list.size() + " 个监听器，跳过添加新的");
    }

    /**
     * 获取指定事件的监听器列表
     */
    public List<MapEventListener> getListenersForEvent(String eventId) {
        return listeners.get(eventId);
    }

    /**
     * 按「所有者」绑定事件监听器（章节脚本推荐用法）。
     *
     * <p>与 {@link #addEventListener} 的区别：同一个 {@code ownerKey} 重复绑定会
     * <b>替换</b> 旧监听器，而不是被忽略。这样地图反复加载（或章节被重复创建）
     * 也不会叠加出多份剧情，同时允许多个不同所有者监听同一个事件。
     *
     * @param eventId  地图 JSON 里的事件 id
     * @param ownerKey 所有者标识，约定为 {@code 章节id#事件id}
     * @param listener 监听器
     */
    public synchronized void bindEvent(String eventId, String ownerKey, MapEventListener listener) {
        if (eventId == null || listener == null) {
            return;
        }
        String key = ownerKey == null ? listener.getClass().getName() : ownerKey;
        Map<String, MapEventListener> owners =
                boundOwners.computeIfAbsent(eventId, k -> new HashMap<>());
        MapEventListener previous = owners.put(key, listener);

        List<MapEventListener> list = listeners.computeIfAbsent(eventId, k -> new ArrayList<>());
        if (previous != null) {
            list.remove(previous);
        }
        list.add(listener);
        LoggerManager.Logger("DEBUG", "绑定事件监听器: " + eventId
                + " (owner=" + key + ", 总数=" + list.size() + ")");
    }

    /**
     * 解除 {@link #bindEvent} 注册的监听器。
     */
    public synchronized void unbindEvent(String eventId, String ownerKey) {
        Map<String, MapEventListener> owners = boundOwners.get(eventId);
        if (owners == null) {
            return;
        }
        MapEventListener removed = owners.remove(ownerKey);
        if (owners.isEmpty()) {
            boundOwners.remove(eventId);
        }
        if (removed == null) {
            return;
        }
        List<MapEventListener> list = listeners.get(eventId);
        if (list != null) {
            list.remove(removed);
            if (list.isEmpty()) {
                listeners.remove(eventId);
            }
        }
        LoggerManager.Logger("DEBUG", "解除事件监听器: " + eventId + " (owner=" + ownerKey + ")");
    }

    /**
     * 设置事件是否启用
     *
     * @param eventId 事件ID
     * @param enabled 是否启用
     */
    public void setEventEnabled(String eventId, boolean enabled) {
        for (MapEvent evt : events) {
            if (evt.id.equals(eventId)) {
                evt.enabled = enabled;
                LoggerManager.Logger("INFO", "事件 " + eventId + " 已" + (enabled ? "启用" : "禁用"));
                return;
            }
        }
        LoggerManager.Logger("WARNING", "未找到事件: " + eventId);
    }

    /**
     * 获取事件是否启用
     *
     * @param eventId 事件ID
     * @return 是否启用
     */
    public boolean isEventEnabled(String eventId) {
        for (MapEvent evt : events) {
            if (evt.id.equals(eventId)) {
                return evt.enabled;
            }
        }
        return false;
    }

    /**
     * 获取事件是否正在执行中（尚未调用completeCurrentEvent）
     *
     * @param eventId 事件ID
     * @return 是否正在执行中
     */
    public boolean isEventExecuting(String eventId) {
        for (MapEvent evt : events) {
            if (evt.id.equals(eventId)) {
                return evt.isExecuting;
            }
        }
        return false;
    }

    public boolean isBlockedByAirWall(double x, double y, double w, double h) {
        double minX = x;
        double minY = y;
        double maxX = x + w;
        double maxY = y + h;

        for (Rectangle r : airWalls) {
            javafx.geometry.Bounds b = r.getBoundsInParent();
            double ax1 = b.getMinX();
            double ay1 = b.getMinY();
            double ax2 = b.getMaxX();
            double ay2 = b.getMaxY();

            boolean intersects = !(ax2 <= minX || ax1 >= maxX || ay2 <= minY || ay1 >= maxY);
            if (intersects) return true;
        }
        return false;
    }

    /**
     * 检测两个矩形区域是否重叠
     */
    public boolean isOverlapping(double x1, double y1, double w1, double h1, double x2, double y2, double w2, double h2) {
        return !(x1 + w1 <= x2 || x1 >= x2 + w2 || y1 + h1 <= y2 || y1 >= y2 + h2);
    }

    /**
     * 在调试模式下显示或隐藏空气墙轮廓（便于调试）
     */
    public void showAirWalls(boolean show) {
        for (Rectangle r : airWalls) {
            if (show) {
                r.setFill(Color.rgb(0, 255, 255, 0.12));
                r.setStroke(Color.CYAN);
                r.setStrokeWidth(1);
                r.setMouseTransparent(true);
            } else {
                r.setFill(Color.TRANSPARENT);
                r.setStroke(null);
            }
        }
    }

    /**
     * 在世界中添加标记，帮助可视化世界移动
     */
    private void addWorldMarkers() {
        if (!wordMarkers) return;
        // 优化：显著减少标记数量以提高性能
        // 添加网格线 - 间隔更大
        int gridSpacing = Math.max(300, worldWidth / 10); // 根据世界大小动态调整间隔
        for (int x = 0; x < worldWidth; x += gridSpacing) {
            Rectangle line = new Rectangle(1, worldHeight);
            line.setFill(Color.rgb(50, 50, 50, 0.5)); // 半透明
            line.setLayoutX(x);
            world.getChildren().add(line);
        }

        for (int y = 0; y < worldHeight; y += gridSpacing) {
            Rectangle line = new Rectangle(worldWidth, 1);
            line.setFill(Color.rgb(50, 50, 50, 0.5)); // 半透明
            line.setLayoutY(y);
            world.getChildren().add(line);
        }

        // 添加中心标记
        Rectangle centerMark = new Rectangle(10, 10);
        centerMark.setFill(Color.RED);
        centerMark.setLayoutX((double) worldWidth / 2 - 5);
        centerMark.setLayoutY((double) worldHeight / 2 - 5);
        world.getChildren().add(centerMark);

        // 优化：大幅减少坐标文本数量
        int coordSpacing = Math.max(1500, worldWidth / 2); // 根据世界大小动态调整坐标间隔
        for (int x = 0; x < worldWidth; x += coordSpacing) {
            for (int y = 0; y < worldHeight; y += coordSpacing) {
                if (x == 0 && y == 0) continue; // 跳过(0,0)
                Text coord = new Text(x + "," + y);
                coord.setFont(Font.font(8)); // 更小的字体
                coord.setFill(Color.rgb(150, 150, 150, 0.7)); // 更淡的颜色
                coord.setLayoutX(x);
                coord.setLayoutY(y);
                world.getChildren().add(coord);
            }
        }
    }

    /**
     * 添加地图边界标记
     */
    private void addMapBoundaries() {
        // 上边界
        Rectangle topBoundary = new Rectangle(worldWidth, 5);
        topBoundary.setFill(Color.rgb(255, 0, 0, 0.5));
        topBoundary.setLayoutY(-5);
        world.getChildren().add(topBoundary);

        // 下边界
        Rectangle bottomBoundary = new Rectangle(worldWidth, 5);
        bottomBoundary.setFill(Color.rgb(255, 0, 0, 0.5));
        bottomBoundary.setLayoutY(worldHeight);
        world.getChildren().add(bottomBoundary);

        // 左边界
        Rectangle leftBoundary = new Rectangle(5, worldHeight);
        leftBoundary.setFill(Color.rgb(255, 0, 0, 0.5));
        leftBoundary.setLayoutX(-5);
        world.getChildren().add(leftBoundary);

        // 右边界
        Rectangle rightBoundary = new Rectangle(5, worldHeight);
        rightBoundary.setFill(Color.rgb(255, 0, 0, 0.5));
        rightBoundary.setLayoutX(worldWidth);
        world.getChildren().add(rightBoundary);
    }

    public Pane getWorld() {
        return world;
    }

    public int getWorldWidth() {
        return worldWidth;
    }

    public int getWorldHeight() {
        return worldHeight;
    }

    /**
     * 触发所有 on='join' 的事件（当加入世界时触发）
     */
    private void triggerJoinEvents() {
        for (MapEvent evt : events) {
            // 检查事件是否启用
            if (!evt.enabled) {
                continue;
            }

            // 检查是否是 join 事件
            if ("join".equalsIgnoreCase(evt.on)) {
                // 如果事件正在执行中，则跳过（防止同一事件在同一时刻重复触发）
                if (evt.isExecuting) {
                    continue;
                }

                // 如果不是重复事件（repeat=false），检查是否已经真正完成过
                if (!evt.repeat && triggeredEvents.containsKey(evt.id) && triggeredEvents.get(evt.id)) {
                    continue;
                }

                List<MapEventListener> ls = listeners.get(evt.id);
                if (ls != null) {
                    LoggerManager.Logger("INFO", "触发 join 事件: " + evt.id);
                    // 标记事件为正在执行中（但不标记为已完成）
                    evt.isExecuting = true;

                    // 如果是非重复事件，先删除监听器（防止重复触发）
                    if (!evt.repeat) {
                        listeners.remove(evt.id);
                    }

                    // 获取玩家位置作为参数
                    Transform pt = playerTransform();
                    double playerX = pt != null ? pt.x : 0;
                    double playerY = pt != null ? pt.y : 0;

                    // 使用副本执行，避免在迭代过程中修改原列表
                    List<MapEventListener> lsCopy = new ArrayList<>(ls);
                    for (MapEventListener l : lsCopy) {
                        try {
                            l.onEvent(evt, evt.argsAsMap(playerX, playerY));
                        } catch (Exception e) {
                            LoggerManager.Logger("ERROR", "事件执行异常: " + evt.id + " - " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * 重新触发未完成的事件（加载存档时调用）
     * 如果事件的 isExecuting == true（表示事件未完成），则重新触发它
     * @param excludeEvents 要排除的事件ID列表（例如刚刚触发的 join 事件）
     */
    private void retryIncompleteEvents(double playerX, double playerY, List<String> excludeEvents) {
        for (MapEvent evt : events) {
            // 检查事件是否启用
            if (!evt.enabled) {
                continue;
            }

            // 跳过刚刚触发的 join 事件，防止重复触发
            if (excludeEvents != null && excludeEvents.contains(evt.id)) {
                continue;
            }

            // 检查事件是否未完成（isExecuting == true）
            if (evt.isExecuting) {
                List<MapEventListener> ls = listeners.get(evt.id);
                if (ls != null) {
                    LoggerManager.Logger("INFO", "重新触发未完成的事件: " + evt.id);
                    // 保持 isExecuting = true（因为事件还未完成）

                    // 使用副本执行
                    List<MapEventListener> lsCopy = new ArrayList<>(ls);
                    for (MapEventListener l : lsCopy) {
                        try {
                            l.onEvent(evt, evt.argsAsMap(playerX, playerY));
                        } catch (Exception e) {
                            LoggerManager.Logger("ERROR", "事件执行异常: " + evt.id + " - " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * 手动设置世界大小（用于没有背景图片的情况）
     */
    public void setWorldSize(int width, int height) {
        this.worldWidth = width;
        this.worldHeight = height;
        world.setPrefSize(worldWidth, worldHeight);

        // 清除旧的标记和边界
        clearMarkersAndBoundaries();

        // 重新添加标记以适应新的世界大小
        addWorldMarkers();
        addMapBoundaries();

        LoggerManager.Logger("INFO", "世界大小已手动设置为: " + worldWidth + "x" + worldHeight);
    }

    /**
     * 清除标记和边界
     */
    private void clearMarkersAndBoundaries() {
        // 移除所有标记和边界（保留背景图片、空气墙、NPC、事件等）
        world.getChildren().removeIf(node -> {
            if (node instanceof Rectangle rect) {
                // 保留空气墙（透明且无描边）
                return rect.getFill() != Color.TRANSPARENT || rect.getStroke() != null;
                // 移除标记和边界
            }
            // 移除坐标文本
            return node instanceof Text;
        });
    }

    /** 获取全部 NPC 实体 id。 */
    public int[] getNpcEntityIds() {
        return ecsWorld != null ? ecsWorld.view(Npc.class) : new int[0];
    }

    /** 按 NPC id 查找实体，找不到返回 -1。 */
    public int findNpcEntity(String npcId) {
        if (ecsWorld == null) return -1;
        for (int e : ecsWorld.view(Npc.class)) {
            Npc npc = ecsWorld.get(e, Npc.class);
            if (npc != null && npc.id.equals(npcId)) {
                return e;
            }
        }
        return -1;
    }

    public Set<String> getDeadNpcIds() {
        return new HashSet<>(deadNpcIds);
    }

    public void setDeadNpcIds(Set<String> ids) {
        deadNpcIds.clear();
        if (ids != null) deadNpcIds.addAll(ids);
    }

    /**
     * 移除所有已标记死亡的 NPC（从存档加载后调用）
     */
    public void removeDeadNpcs() {
        if (deadNpcIds.isEmpty() || ecsWorld == null) return;
        List<Integer> toRemove = new java.util.ArrayList<>();
        for (int e : ecsWorld.view(Npc.class)) {
            Npc npc = ecsWorld.get(e, Npc.class);
            if (npc != null && deadNpcIds.contains(npc.id)) {
                toRemove.add(e);
            }
        }
        for (int e : toRemove) {
            Sprite spr = ecsWorld.get(e, Sprite.class);
            if (spr != null) {
                world.getChildren().remove(spr.view);
            }
            ecsWorld.destroy(e);
        }
    }

    public void setLayoutX(double x) {
        world.setLayoutX(x);
    }

    public void setLayoutY(double y) {
        world.setLayoutY(y);
    }

    public double getLayoutX() {
        return world.getLayoutX();
    }

    public double getLayoutY() {
        return world.getLayoutY();
    }

    /**
     * 检查玩家是否超出地图边界
     */
    public boolean isPlayerOutOfBounds(double playerX, double playerY, double playerWidth, double playerHeight) {
        return playerX < 0 || playerY < 0 || playerX + playerWidth > worldWidth || playerY + playerHeight > worldHeight;
    }

    /**
     * 将玩家位置限制在地图边界内
     */
    public double[] clampPlayerPosition(double playerX, double playerY, double playerWidth, double playerHeight) {
        double clampedX = playerX;
        double clampedY = playerY;

        if (clampedX < 0) clampedX = 0;
        if (clampedY < 0) clampedY = 0;
        if (clampedX + playerWidth > worldWidth) clampedX = worldWidth - playerWidth;
        if (clampedY + playerHeight > worldHeight) clampedY = worldHeight - playerHeight;

        return new double[]{clampedX, clampedY};
    }

    /**
     * 将任意实体位置限制在地图边界内（通用版）
     */
    public double[] clampEntityPosition(double x, double y, double w, double h) {
        double clampedX = x;
        double clampedY = y;
        if (clampedX < 0) clampedX = 0;
        if (clampedY < 0) clampedY = 0;
        if (clampedX + w > worldWidth) clampedX = worldWidth - w;
        if (clampedY + h > worldHeight) clampedY = worldHeight - h;
        return new double[]{clampedX, clampedY};
    }

    // --- 事件相关 ---
    public interface MapEventListener {
        void onEvent(MapEvent event, Map<String, Object> args);
    }

    /**
     * 获取所有事件（用于调试）
     */
    public List<MapEvent> getEvents() {
        return new ArrayList<>(events);
    }

    /**
     * 获取已注册的监听器数量（用于调试）
     */
    public int getListenerCount(String eventId) {
        List<MapEventListener> ls = listeners.get(eventId);
        return ls != null ? ls.size() : 0;
    }

    /**
     * 获取所有事件状态（用于存档）
     */
    public List<SaveManager.EventState> getEventStates() {
        List<SaveManager.EventState> states = new ArrayList<>();

        for (MapEvent evt : events) {
            SaveManager.EventState state = new SaveManager.EventState();
            state.id = evt.id;
            state.triggered = triggeredEvents.containsKey(evt.id) && triggeredEvents.get(evt.id);
            state.enabled = evt.enabled;
            state.isExecuting = evt.isExecuting;
            states.add(state);
        }

        return states;
    }

    /**
     * 设置所有事件状态（用于读档）
     */
    public void setEventStates(List<SaveManager.EventState> states) {
        if (states == null) return;

        for (SaveManager.EventState state : states) {
            // 查找对应的事件
            for (MapEvent evt : events) {
                if (evt.id.equals(state.id)) {
                    // 恢复触发状态
                    triggeredEvents.put(evt.id, state.triggered);

                    // 恢复启用状态
                    evt.enabled = state.enabled;

                    // 不恢复执行状态，因为它是当前进程的临时状态
                    // 读档后应该为false，表示没有事件正在执行中
                    evt.isExecuting = false;

                    // 如果事件已触发且不是重复事件，则移除监听器（防止重复触发）
                    // 这样可以正确恢复读档前的事件状态
                    if (state.triggered && !evt.repeat) {
                        listeners.remove(evt.id);
                        LoggerManager.Logger("DEBUG", "事件 " + evt.id + " 已触发，移除监听器");
                    }

                    break;
                }
            }
        }
    }

    /**
     * 重置已触发的事件
     */
    public void resetTriggeredEvents() {
        triggeredEvents.clear();
    }

    /**
     * 清除所有事件监听器（用于游戏退出时清理）
     */
    public void clearAllEventListeners() {
        listeners.clear();
        boundOwners.clear();
        LoggerManager.Logger("INFO", "已清除所有事件监听器");
    }

    /**
     * 重置指定事件的触发状态
     *
     * @param eventId 事件ID
     */
    public void resetTriggeredEvent(String eventId) {
        triggeredEvents.remove(eventId);
        eventWasMatching.remove(eventId);
        for (MapEvent evt : events) {
            if (evt.id.equals(eventId)) {
                evt.cooldown = false;
                break;
            }
        }
        LoggerManager.Logger("INFO", "事件 " + eventId + " 触发状态已重置");
    }

    /**
     * 检查事件是否已触发
     *
     * @param eventId 事件ID
     * @return 如果已触发返回true
     */
    public boolean isEventTriggered(String eventId) {
        return triggeredEvents.containsKey(eventId) && triggeredEvents.get(eventId);
    }

    /**
     * 检查事件是否在冷却中（仅对重复执行事件有效）
     *
     * @param eventId 事件ID
     * @return 如果在冷却中返回true
     */
    public boolean isEventInCooldown(String eventId) {
        for (MapEvent evt : events) {
            if (evt.id.equals(eventId)) {
                return evt.cooldown;
            }
        }
        return false;
    }

    public static class MapEvent {
        public String id;
        JsonObject condition;
        JsonArray args;
        boolean repeat = false; // 是否重复执行
        boolean cooldown = false; // 是否在冷却中
        boolean enabled = true; // 事件默认是否开启
        boolean isExecuting = false; // 事件是否正在执行中（尚未调用completeCurrentEvent）

        // 新增：触发条件和提示消息
        public String on = "enter";  // 触发条件：enter（进入区域）、interact（按键交互）、left（朝左）、right（朝右）
        public String message = "";  // 交互提示消息

        // 关联的 NPC（用于引导步骤事件，事件对象即 NPC.guideSteps 中引用同一实例）
        private WorldMap owner;
        private int npcEntity = -1;

        public static MapEvent fromJson(JsonObject o) {
            MapEvent m = new MapEvent();
            if (o.has("id")) m.id = o.get("id").getAsString();
            if (o.has("condition") && o.get("condition").isJsonObject()) m.condition = o.getAsJsonObject("condition");
            if (o.has("args") && o.get("args").isJsonArray()) m.args = o.getAsJsonArray("args");
            if (o.has("repeat")) m.repeat = o.get("repeat").getAsBoolean();
            if (o.has("on")) m.on = o.get("on").getAsString();
            if (o.has("message")) m.message = o.get("message").getAsString();
            if (o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
            return m;
        }

        /**
         * 关联到所属 NPC 实体（引导步骤用）
         */
        public void setNpcOwner(WorldMap owner, int npcEntity) {
            this.owner = owner;
            this.npcEntity = npcEntity;
        }

        /**
         * 获取事件ID
         */
        public String getId() {
            return id;
        }

        /**
         * 声明事件已完成（用于NPC事件）
         * 外部监听器在事件处理完毕后调用此方法
         */
        public void completeCurrentEvent() {
            if (owner != null && npcEntity >= 0) {
                owner.completeNpcGuideStep(this);
            }
            // 标记事件执行完成
            isExecuting = false;
            // 标记事件为已完成（只有调用 completeCurrentEvent() 才算真正完成）
            triggeredEvents.put(id, true);
        }

        boolean matches(double playerX, double playerY) {
            if (condition == null) return true;
            for (Map.Entry<String, JsonElement> e : condition.entrySet()) {
                String key = e.getKey();
                String expr = e.getValue().getAsString();
                if (key.equalsIgnoreCase("player_x")) {
                    if (!matchesExpr(expr, playerX)) return false;
                } else if (key.equalsIgnoreCase("player_y")) {
                    if (!matchesExpr(expr, playerY)) return false;
                } else {
                    // 未知条件，暂时忽略
                }
            }
            return true;
        }

        /**
         * 检查触发条件是否满足
         *
         * @param playerX         玩家X坐标
         * @param playerY         玩家Y坐标
         * @param playerFacing    玩家朝向
         * @param interactPressed 交互按钮是否按下
         * @return 是否满足触发条件
         */
        public boolean checkTriggerCondition(double playerX, double playerY, Facing playerFacing, boolean interactPressed) {
            // 首先检查位置条件
            if (!matches(playerX, playerY)) {
                return false;
            }

            // 检查触发条件
            switch (on.toLowerCase()) {
                case "enter":
                    // 进入区域即触发
                    return true;
                case "interact":
                    // 需要按键交互
                    return interactPressed;
                case "left":
                    // 需要朝左
                    return playerFacing == Facing.LEFT;
                case "right":
                    // 需要朝右
                    return playerFacing == Facing.RIGHT;
                case "up":
                    // 需要朝上
                    return playerFacing == Facing.UP;
                case "down":
                    // 需要朝下
                    return playerFacing == Facing.DOWN;
                case "join":
                    // 加入世界时触发（不通过位置条件检查，需要特殊处理）
                    return true;
                default:
                    // 未知条件，默认为enter
                    return true;
            }
        }

        /**
         * 检查是否应该显示交互提示
         *
         * @param playerX      玩家X坐标
         * @param playerY      玩家Y坐标
         * @param playerFacing 玩家朝向
         * @return 是否应该显示提示
         */
        boolean shouldShowPrompt(double playerX, double playerY, Facing playerFacing) {
            // 检查位置条件
            if (!matches(playerX, playerY)) {
                return false;
            }

            // 检查触发条件
            switch (on.toLowerCase()) {
                case "enter":
                    // enter类型不需要提示
                    return false;
                case "interact":
                    // interact类型需要提示
                    return true;
                case "left":
                    // 需要朝左时提示
                    return playerFacing == Facing.LEFT;
                case "right":
                    // 需要朝右时提示
                    return playerFacing == Facing.RIGHT;
                case "up":
                    // 需要朝上时提示
                    return playerFacing == Facing.UP;
                case "down":
                    // 需要朝下时提示
                    return playerFacing == Facing.DOWN;
                default:
                    return false;
            }
        }

        private boolean matchesExpr(String expr, double val) {
            expr = expr.trim();
            if (expr.startsWith("=")) expr = expr.substring(1);
            // range: 100-200
            if (expr.contains("-")) {
                String[] sp = expr.split("-");
                try {
                    double a = Double.parseDouble(sp[0]);
                    double b = Double.parseDouble(sp[1]);
                    double min = Math.min(a, b);
                    double max = Math.max(a, b);
                    return val >= min && val <= max;
                } catch (Exception ex) {
                    return false;
                }
            }
            try {
                double target = Double.parseDouble(expr);
                return Double.compare(val, target) == 0;
            } catch (Exception ex) {
                return false;
            }
        }

        public Map<String, Object> argsAsMap(double playerX, double playerY) {
            Map<String, Object> out = new HashMap<>();
            if (args != null) {
                for (JsonElement e : args) {
                    String k = e.getAsString();
                    if (k.equalsIgnoreCase("player_x")) out.put(k, playerX);
                    else if (k.equalsIgnoreCase("player_y")) out.put(k, playerY);
                    else out.put(k, null);
                }
            }
            return out;
        }
    }

    // ========== 地图切换功能 ==========

    /**
     * 切换到指定地图（简化版本，使用内部管理的player和gameContainer）
     *
     * @param mapFile 地图JSON文件路径（如 "starveil:data/worlds/wu-home.json"）
     * @return CompletableFuture，在切换完成后完成
     */
    public CompletableFuture<Void> switchMap(String mapFile) {
        return switchMap(mapFile, null, null);
    }

    /**
     * 切换到指定地图（指定出生点）
     *
     * @param mapFile 地图JSON文件路径
     * @param spawnX  指定玩家出生点X坐标（可选，如果为null则使用地图的spawn点）
     * @param spawnY  指定玩家出生点Y坐标（可选，如果为null则使用地图的spawn点）
     * @return CompletableFuture，在切换完成后完成
     */
    public CompletableFuture<Void> switchMap(String mapFile, Double spawnX, Double spawnY) {
        if (isTransitioning) {
            LoggerManager.Logger("WARN", "地图切换中，请勿重复操作");
            return CompletableFuture.completedFuture(null);
        }

        isTransitioning = true;
        CompletableFuture<Void> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                // 1. 禁用用户控制
                if (inputHandler != null) {
                    inputHandler.lockControls(com.xiaowu.game.starveil.input.InputHandler.LOCK_MAP_TRANSITION);
                    LoggerManager.Logger("DEBUG", "用户控制已禁用");
                }

                // 2. 显示渐入效果和加载指示器（立即显示，不等待）
                showTransitionOverlay(null, true);
                LoggerManager.Logger("DEBUG", "过渡遮罩已显示");

                // 2. 延迟至少500ms后切换地图（确保动画可见）
                javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.millis(500));
                pause.setOnFinished(e -> {
                    try {
                        LoggerManager.Logger("DEBUG", "开始执行地图切换");

                        // 3. 清理当前地图
                        clearMap();

                        // 4. 加载新地图
                        currentMapFile = mapFile;
                        initialize();
                        loadFromFile(mapFile);

                        // 记录地图切换时间（用于重生冷却）
                        mapSwitchTime = System.currentTimeMillis();
                        LoggerManager.Logger("DEBUG", "记录地图切换时间，重生冷却开始");

                        LoggerManager.Logger("INFO", "地图切换完成: " + mapFile);

                        // 5. 设置玩家位置
                        Transform pt = playerTransform();
                        if (pt != null) {
                            double finalX = (spawnX != null) ? spawnX : (worldSpawn != null ? worldSpawn.x : 0);
                            double finalY = (spawnY != null) ? spawnY : (worldSpawn != null ? worldSpawn.y : 0);
                            pt.x = finalX;
                            pt.y = finalY;
                            Sprite ps = playerSprite();
                            LoggerManager.Logger("INFO", "玩家位置设置为: (" + finalX + ", " + finalY + ")");
                            if (ps != null) {
                                LoggerManager.Logger("DEBUG", "玩家视图位置: (" + ps.view.getLayoutX() + ", " + ps.view.getLayoutY() + ")");
                                LoggerManager.Logger("DEBUG", "玩家视图可见性: " + ps.view.isVisible());
                                LoggerManager.Logger("DEBUG", "玩家视图不透明度: " + ps.view.getOpacity());
                            }
                        }

                        // 6. 隐藏渐出效果（延迟300ms后隐藏，确保玩家能看到新地图）
                        javafx.animation.PauseTransition hidePause = new javafx.animation.PauseTransition(Duration.millis(300));
                        hidePause.setOnFinished(hideEvent -> {
                            hideTransitionOverlay();
                            LoggerManager.Logger("DEBUG", "过渡遮罩已隐藏");

                            // 7. 启用用户控制
                            if (inputHandler != null) {
                                inputHandler.unlockControls(com.xiaowu.game.starveil.input.InputHandler.LOCK_MAP_TRANSITION);
                                LoggerManager.Logger("DEBUG", "用户控制已启用");
                            }

                            // 8. 触发完成回调
                            if (onMapSwitchComplete != null) {
                                onMapSwitchComplete.run();
                            }

                            // 9. 触发世界加载完成事件
                            EventCallbackManager.getInstance().triggerWorldLoaded(currentWorldId);
                            LoggerManager.Logger("DEBUG", "已触发 WorldLoaded 事件 (worldId=" + currentWorldId + ")");
                        });
                        hidePause.play();

                        future.complete(null);
                    } catch (Exception ex) {
                        LoggerManager.Logger("ERROR", "地图切换异常: " + ex.getMessage());
                        future.completeExceptionally(ex);
                    }
                });
                pause.play();

            } catch (Exception ex) {
                LoggerManager.Logger("ERROR", "地图切换异常: " + ex.getMessage());
                isTransitioning = false;
                future.completeExceptionally(ex);
            }
        });

        return future.whenComplete((r, ex) -> {
            isTransitioning = false;
        });
    }

    /**
     * 渐入黑幕（复用切地图用的过渡遮罩）。
     *
     * <p>调用方负责在合适的时候 {@link #fadeFromBlack()} 把幕拉开 ——
     * 这样可以「先黑幕、再做事、最后才亮」。
     */
    public void fadeToBlack() {
        fadeToBlack(false);
    }

    /**
     * 渐入黑幕。
     *
     * @param keepHudHidden 拉幕时是否<b>不要</b>把 HUD 自动显示回来。
     *                      切到「仅视觉小说」模式时为 true —— 那时没有世界，
     *                      HUD 不该出现；而 {@link #hideTransitionOverlay()} 默认会
     *                      在拉幕后把 HUD 显示回来（切地图时正需要这个行为）。
     */
    public void fadeToBlack(boolean keepHudHidden) {
        suppressHudOnCurtainLift = keepHudHidden;
        showTransitionOverlay(null, false);
    }

    /** 渐出黑幕。 */
    public void fadeFromBlack() {
        hideTransitionOverlay();
    }

    /** 拉幕时是否抑制「自动显示 HUD」。 */
    private boolean suppressHudOnCurtainLift = false;

    /**
     * 卸下当前世界：清空地图、销毁全部实体，并把玩家视图也摘掉。
     *
     * <p>与 {@link #switchMap(String)} 的区别是<b>不加载新地图</b> ——
     * 用于从普通模式切到「仅视觉小说」模式：过场和切地图一模一样，
     * 但黑幕拉开后底下是空的（视口黑底），由视觉小说层接管画面。
     *
     * <p>调用后必须让 game loop 停止依赖世界（{@code GameInstance.worldLoaded = false}），
     * 否则会去操作已经销毁的实体。
     */
    public void unloadWorld() {
        // clearMap 会清 world 子节点并把玩家视图重新加回去
        clearMap();
        if (ecsWorld != null) {
            for (int e : ecsWorld.entities()) {
                ecsWorld.destroy(e);
            }
        }
        // 连玩家视图也彻底摘掉 —— 视觉小说模式下不该有角色残留在黑底上
        if (world != null) {
            world.getChildren().clear();
        }
        LoggerManager.Logger("INFO", "世界已卸下（视口保留，等待视觉小说层接管）");
    }

    /**
     * 过渡遮罩和加载指示器
     *
     * @param parentContainer 父容器
     * @param showLoading     是否显示加载指示器
     */
    private void showTransitionOverlay(Pane parentContainer, boolean showLoading) {
        if (transitionOverlay == null) {
            createTransitionOverlay();
        }

        // 使用 gameContainer，因为它有正确的比例和大小设置
        Pane container = gameContainer != null ? gameContainer : parentContainer;

        LoggerManager.Logger("DEBUG", "显示过渡遮罩，容器: " + (container != null ? "存在" : "不存在"));
        LoggerManager.Logger("DEBUG", "容器大小: " + (container != null ? container.getWidth() + "x" + container.getHeight() : "N/A"));

        if (container != null) {
            if (!container.getChildren().contains(transitionOverlay)) {
                // 将遮罩添加到容器的最上层
                container.getChildren().add(transitionOverlay);
                LoggerManager.Logger("DEBUG", "过渡遮罩已添加到容器");
            }

            // 确保遮罩在最上层
            transitionOverlay.toFront();
            LoggerManager.Logger("DEBUG", "过渡遮罩已移到最上层");
        }

        // 显示遮罩并设置不透明度为0（准备渐入）
        transitionOverlay.setVisible(true);
        transitionOverlay.setOpacity(0);

        // 隐藏GameUI
        GameUI.getInstance().hide();

        LoggerManager.Logger("DEBUG", "过渡遮罩可见性: " + transitionOverlay.isVisible() + ", 不透明度: " + transitionOverlay.getOpacity());
        LoggerManager.Logger("DEBUG", "过渡遮罩大小: " + transitionOverlay.getWidth() + "x" + transitionOverlay.getHeight());

        // 渐入效果
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), transitionOverlay);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // 监听动画完成
        fadeIn.setOnFinished(e -> {
            LoggerManager.Logger("DEBUG", "渐入动画完成，不透明度: " + transitionOverlay.getOpacity());
            LoggerManager.Logger("DEBUG", "渐入动画完成，遮罩大小: " + transitionOverlay.getWidth() + "x" + transitionOverlay.getHeight());
        });

        // 显示/隐藏加载指示器
        if (loadingIndicator != null) {
            loadingIndicator.setVisible(showLoading);
            if (showLoading && loadingAnimation != null) {
                // 设置遮罩的宽高与容器一致
                transitionOverlay.setPrefWidth(container.getWidth());
                transitionOverlay.setPrefHeight(container.getHeight());

                // 计算右下角位置
                double containerWidth = container.getWidth();
                double containerHeight = container.getHeight();
                double indicatorWidth = loadingIndicator.getPrefWidth();
                double indicatorHeight = loadingIndicator.getPrefHeight();

                // 设置位置：距离右边和底部各 30px
                double x = containerWidth - indicatorWidth - 30;
                double y = containerHeight - indicatorHeight - 30;

                // 使用绝对定位
                loadingIndicator.setLayoutX(x);
                loadingIndicator.setLayoutY(y);

                loadingAnimation.play();
                LoggerManager.Logger("DEBUG", "加载指示器开始动画，位置: " + x + "," + y + " (容器: " + containerWidth + "x" + containerHeight + ")");
            }
        }
    }

    /**
     * 隐藏过渡遮罩
     */
    private void hideTransitionOverlay() {
        if (transitionOverlay == null) return;

        // 渐出效果
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), transitionOverlay);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            transitionOverlay.setVisible(false);

            // 延迟显示GameUI，确保加载动画完全消失
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.millis(100));
            pause.setOnFinished(pauseEvent -> {
                // 切到「仅视觉小说」模式时不要把它显示回来
                if (!suppressHudOnCurtainLift) {
                    GameUI.getInstance().show();
                }
                suppressHudOnCurtainLift = false;
            });
            pause.play();

            // 从容器中移除遮罩
            if (gameContainer != null && gameContainer.getChildren().contains(transitionOverlay)) {
                gameContainer.getChildren().remove(transitionOverlay);
                LoggerManager.Logger("DEBUG", "过渡遮罩已从容器中移除");
            }

            // 停止加载动画
            if (loadingIndicator != null) {
                loadingIndicator.setVisible(false);
                if (loadingAnimation != null) {
                    loadingAnimation.stop();
                }
                // 重置所有圆点的不透明度
                for (javafx.scene.Node node : loadingIndicator.getChildren()) {
                    if (node instanceof Circle) {
                        Circle circle = (Circle) node;
                        circle.setOpacity(0.3);
                    }
                }
            }
        });
        fadeOut.play();
    }

    /**
     * 创建过渡遮罩和加载指示器
     */
    private void createTransitionOverlay() {
        // 使用 Pane 替代 StackPane，以便使用绝对定位
        transitionOverlay = new Pane();
        transitionOverlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8);");
        transitionOverlay.setVisible(false);
        transitionOverlay.setOpacity(0);

        // 设置遮罩大小为最大，确保覆盖整个场景
        transitionOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        transitionOverlay.setPrefSize(Double.MAX_VALUE, Double.MAX_VALUE);

        LoggerManager.Logger("DEBUG", "创建过渡遮罩，大小设置为 MAX");

        // 创建简单的加载指示器
        loadingIndicator = createWindows10LoadingIndicator();
        loadingIndicator.setVisible(false);

        // 将加载指示器添加到遮罩层
        transitionOverlay.getChildren().add(loadingIndicator);

        LoggerManager.Logger("DEBUG", "加载指示器已添加到容器");
    }

    /**
     * 创建 Windows 10 风格的加载指示器（多个圆点旋转）
     */
    private Pane createWindows10LoadingIndicator() {
        Pane container = new Pane();
        container.setPrefSize(60, 60); // 增大容器尺寸到 60x60

        int dotCount = 5;
        double radius = 18; // 增大半径到 18
        double dotSize = 5; // 增大圆点尺寸到 5

        Circle[] dots = new Circle[dotCount];
        Rotate[] rotates = new Rotate[dotCount];

        for (int i = 0; i < dotCount; i++) {
            Circle dot = new Circle(dotSize);
            dot.setFill(Color.WHITE);

            // 计算每个圆点的位置
            double angle = 360.0 / dotCount * i;
            double centerX = 30; // 容器中心 X
            double centerY = 30; // 容器中心 Y
            double x = centerX + radius * Math.cos(Math.toRadians(angle));
            double y = centerY + radius * Math.sin(Math.toRadians(angle));

            dot.setCenterX(x);
            dot.setCenterY(y);

            // 创建旋转中心
            Rotate rotate = new Rotate(0, 30, 30);
            dot.getTransforms().add(rotate);

            dots[i] = dot;
            rotates[i] = rotate;
            container.getChildren().add(dot);
        }

        // 创建旋转动画
        loadingAnimation = new Timeline();
        loadingAnimation.setCycleCount(Timeline.INDEFINITE);

        // 为每个圆点创建关键帧，实现波浪效果
        for (int i = 0; i < dotCount; i++) {
            int index = i;
            double delay = i * 0.15; // 每个圆点延迟 0.15 秒

            // 不透明度动画（淡入淡出）
            KeyValue opacityStart = new KeyValue(dots[index].opacityProperty(), 0.3);
            KeyValue opacityMiddle = new KeyValue(dots[index].opacityProperty(), 1.0);
            KeyValue opacityEnd = new KeyValue(dots[index].opacityProperty(), 0.3);

            KeyFrame kf1 = new KeyFrame(Duration.seconds(delay), opacityStart);
            KeyFrame kf2 = new KeyFrame(Duration.seconds(delay + 0.3), opacityMiddle);
            KeyFrame kf3 = new KeyFrame(Duration.seconds(delay + 0.6), opacityEnd);

            loadingAnimation.getKeyFrames().addAll(kf1, kf2, kf3);
        }

        // 整体旋转动画
        RotateTransition rotateTransition = new RotateTransition(Duration.seconds(2), container);
        rotateTransition.setByAngle(360);
        rotateTransition.setCycleCount(RotateTransition.INDEFINITE);
        rotateTransition.setInterpolator(javafx.animation.Interpolator.LINEAR);

        // 组合动画：先播放不透明度动画，同时旋转
        loadingAnimation.setOnFinished(e -> {
            rotateTransition.play();
        });

        return container;
    }

    /**
     * 获取过渡遮罩层（需要添加到场景的最上层）
     *
     * @return Pane 过渡遮罩层
     */
    public Pane getTransitionOverlay() {
        if (transitionOverlay == null) {
            createTransitionOverlay();
        }
        return transitionOverlay;
    }

    /**
     * 清理当前地图
     */
    private void clearMap() {
        // 保留玩家视图（如果存在）
        Sprite pSpr = playerSprite();
        javafx.scene.Node playerView = pSpr != null ? pSpr.view : null;
        LoggerManager.Logger("DEBUG", "保留玩家视图: " + (playerView != null ? "存在" : "不存在"));

        // 清空所有子元素
        world.getChildren().clear();

        // 销毁本图 NPC 实体
        if (ecsWorld != null) {
            for (int e : ecsWorld.view(Npc.class)) {
                ecsWorld.destroy(e);
            }
        }

        // 重新添加玩家视图
        if (playerView != null) {
            world.getChildren().add(playerView);
            LoggerManager.Logger("DEBUG", "重新添加了玩家视图，当前子元素数量: " + world.getChildren().size());
        }

        // 清理数据（包括 triggerEvents，因为这是新地图的开始）
        airWalls.clear();
        events.clear();
        eventWasMatching.clear();
        triggeredEvents.clear();

        LoggerManager.Logger("INFO", "当前地图已清理");
    }

    /**
     * 获取当前地图文件
     *
     * @return 当前地图文件路径
     */
    public String getCurrentMapFile() {
        return currentMapFile;
    }

    /**
     * 获取当前地图名称
     *
     * @return 当前地图名称
     */
    public String getCurrentMapName() {
        return currentMapName;
    }

    /**
     * 获取当前地图ID
     *
     * @return 当前地图ID
     */
    public Integer getCurrentWorldId() {
        return currentWorldId;
    }

    /**
     * 根据地图ID加载地图
     *
     * @param mapId 地图ID
     */
    public void loadById(int mapId) {
        String mapPath = findMapPathById(mapId);
        if (mapPath == null) {
            LoggerManager.Logger("ERROR", "找不到地图ID: " + mapId);
            return;
        }

        loadFromFile(mapPath);
    }

    /**
     * 根据地图ID查找对应的JSON文件路径
     *
     * @param mapId 地图ID
     * @return JSON文件路径，找不到返回null
     */
    private String findMapPathById(int mapId) {
        try {
            // 常见的地图文件路径列表
            String[] commonMapFiles = {
                "starveil:data/worlds/wu-home.json",
                "starveil:data/worlds/wu-home-floor2.json",
                "starveil:data/worlds/test-world.json"
            };

            // 首先检查常用文件
            for (String path : commonMapFiles) {
                try (InputStream in = ResourceResolver.getResourceAsStream(path)) {
                    if (in != null) {
                        // 读取JSON并检查ID
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        if (json.contains("\"id\":" + mapId) || json.contains("\"id\": " + mapId)) {
                            return path;
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            // 尝试从文件系统读取
            Path worldDir = Path.of("assets/json/world");
            if (Files.exists(worldDir) && Files.isDirectory(worldDir)) {
                try (var stream = Files.list(worldDir)) {
                    for (Path file : stream.toList()) {
                        if (file.toString().endsWith(".json")) {
                            String json = Files.readString(file, StandardCharsets.UTF_8);
                            if (json.contains("\"id\":" + mapId) || json.contains("\"id\": " + mapId)) {
                                return file.toString();
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "查找地图路径失败: " + e.getMessage());
        }

        return null;
    }

    /**
     * 获取世界出生点
     *
     * @return 出生点，如果没有设置则返回null
     */
    public SpawnPoint getWorldSpawn() {
        return worldSpawn;
    }

    /**
     * 本图声明的玩法模式（地图 JSON 的 {@code "gameplayMode"}，缺省 NORMAL）。
     *
     * <p>在 {@code mountWorld} 里读一次并写入 ECS 世界，之后由各系统读取。
     */
    public GameplayMode getGameplayMode() {
        return gameplayMode;
    }

    /** 本图声明的重力方向（角度）。0° 向下，顺时针为正。 */
    public double getGravityAngleDegrees() {
        return gravityAngleDegrees;
    }

    /**
     * 检查是否正在切换地图
     *
     * @return 如果正在切换返回true
     */
    public boolean isTransitioning() {
        return isTransitioning;
    }

    // ==================== Overlay 控制 API ====================

    /**
     * 通过 ID 获取 overlay 的 ImageView
     */
    public ImageView getOverlay(String id) {
        return overlayViews.get(id);
    }

    /**
     * 设置 overlay 可见性
     */
    public void setOverlayVisible(String id, boolean visible) {
        ImageView iv = overlayViews.get(id);
        if (iv != null) {
            iv.setVisible(visible);
        }
    }

    /**
     * 设置 overlay 位置
     */
    public void setOverlayPosition(String id, double x, double y) {
        ImageView iv = overlayViews.get(id);
        if (iv != null) {
            iv.setLayoutX(x);
            iv.setLayoutY(y);
        }
    }

    // ==================== 容器 API ====================

    /**
     * 通过 ID 获取容器
     */
    public Container getContainer(String id) {
        return containers.get(id);
    }

    /**
     * 获取所有容器
     */
    public Map<String, Container> getAllContainers() {
        return new HashMap<>(containers);
    }

    /**
     * 清空容器数据（地图切换时）
     */
    public void clearContainers() {
        containers.clear();
    }

    // ==================== 掉落物品 API（ECS 实体） ====================

    /**
     * 在地图上掉落物品 —— 创建一个 ECS 实体（DroppedItem + Transform + Attributes + Sprite）。
     * @return 新掉落物实体 id（无 ECS 世界时为 -1）
     */
    public int dropItem(String itemId, double x, double y) {
        if (ecsWorld == null) return -1;

        DroppedItem dropped = new DroppedItem(itemId);
        int e = ecsWorld.newEntity();
        ecsWorld.add(e, dropped);
        ecsWorld.add(e, new Transform(x, y, 32, 32));

        com.xiaowu.game.starveil.game.item.Item item =
            com.xiaowu.game.starveil.game.item.ItemRegistry.getInstance().getItem(itemId);
        String iconPath = item != null ? item.getIconPath() : null;
        if (iconPath != null) {
            try {
                AnimationSheet sheet = AnimationSheet.load(iconPath);
                if (sheet != null) {
                    Sprite spr = new Sprite();
                    spr.view.setMouseTransparent(true);
                    spr.view.setFitWidth(32);
                    spr.view.setFitHeight(32);
                    spr.view.setPreserveRatio(false);
                    spr.view.playSheet(sheet);
                    ecsWorld.add(e, spr);
                }
            } catch (Exception ex) {
                LoggerManager.Logger("WARNING", "掉落物品图标加载失败: " + itemId);
            }
        }

        Attributes attrs = new Attributes()
            .set(Attributes.ITEM_NAME, item != null ? item.getName() : itemId)
            .set("starveil:item_id", itemId)
            .set("starveil:dropped_id", dropped.id);
        ecsWorld.add(e, attrs);
        return e;
    }

    /**
     * 拾取指定掉落物实体：移除视图并销毁实体。
     * @return 物品 id，失败返回 null
     */
    public String pickUpDroppedEntity(int entity) {
        if (ecsWorld == null) return null;
        Sprite spr = ecsWorld.get(entity, Sprite.class);
        if (spr != null) {
            world.getChildren().remove(spr.view);
        }
        DroppedItem dropped = ecsWorld.remove(entity, DroppedItem.class);
        ecsWorld.destroy(entity);
        return dropped != null ? dropped.itemId : null;
    }

    /**
     * 按掉落物 UUID 拾取（兼容旧 API）。
     */
    public String pickUpItem(String droppedId) {
        if (ecsWorld == null) return null;
        for (int e : ecsWorld.view(DroppedItem.class)) {
            DroppedItem d = ecsWorld.get(e, DroppedItem.class);
            if (d != null && d.id.equals(droppedId)) {
                return pickUpDroppedEntity(e);
            }
        }
        return null;
    }

    /**
     * 获取玩家附近的掉落物实体 id。
     */
    public int[] getNearbyDroppedEntities(double playerX, double playerY, double radius) {
        if (ecsWorld == null) return new int[0];
        List<Integer> nearby = new ArrayList<>();
        for (int e : ecsWorld.view(DroppedItem.class, Transform.class)) {
            Transform t = ecsWorld.get(e, Transform.class);
            DroppedItem d = ecsWorld.get(e, DroppedItem.class);
            if (t == null || d == null) continue;
            double dx = t.x - playerX;
            double dy = t.y - playerY;
            if (dx * dx + dy * dy <= radius * radius) {
                nearby.add(e);
            }
        }
        int[] out = new int[nearby.size()];
        for (int i = 0; i < out.length; i++) out[i] = nearby.get(i);
        return out;
    }

    /**
     * 获取全部掉落物实体 id。
     */
    public int[] getAllDroppedEntities() {
        return ecsWorld != null ? ecsWorld.view(DroppedItem.class) : new int[0];
    }

    /**
     * 清空掉落物品（地图切换时）—— 销毁对应 ECS 实体。
     */
    public void clearDroppedItems() {
        if (ecsWorld == null) return;
        for (int e : ecsWorld.view(DroppedItem.class)) {
            Sprite spr = ecsWorld.get(e, Sprite.class);
            if (spr != null) {
                world.getChildren().remove(spr.view);
            }
            ecsWorld.destroy(e);
        }
    }

    /**
     * 出生点数据类
     */
    public static class SpawnPoint {
        public double x;
        public double y;

        public SpawnPoint() {
        }

        public SpawnPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
}