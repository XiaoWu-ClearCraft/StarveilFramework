package com.xiaowu.game.starveil.game.event;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.google.gson.Gson;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 世界地图事件管理器
 * 负责处理游戏场景中的地图事件、空气墙、交互对象等
 */
public class WorldEventManager {

    private static WorldEventManager instance;

    public static WorldEventManager getInstance() {
        if (instance == null) instance = new WorldEventManager();
        return instance;
    }

    // 地图数据
    private WorldMapData currentMapData;
    private String currentMapId;

    // 事件监听器
    private final Map<String, List<Consumer<Map<String, Object>>>> eventListeners = new ConcurrentHashMap<>();

    // 当前玩家位置
    private double playerX;
    private double playerY;

    // UI组件
    private StackPane worldRoot;
    private Pane eventLayer;
    private ImageView backgroundView;

    // 空气墙检测
    private final List<AirWall> airWalls = new ArrayList<>();

    // 事件触发状态
    private final Map<String, Boolean> triggeredEvents = new ConcurrentHashMap<>();

    private WorldEventManager() {
        Logger("INFO", "WorldEventManager 初始化");
    }

    /**
     * 加载世界地图
     * @param mapId 地图ID（对应 world/map.json）
     */
    public void loadWorldMap(String mapId) {
        try {
            this.currentMapId = mapId;
            String jsonPath = "starveil:data/worlds/" + mapId + ".json";

            Logger("INFO", "加载世界地图: " + jsonPath);

            try (InputStream in = ResourceResolver.getResourceAsStream(jsonPath);
                 Reader reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {

                Gson gson = new Gson();
                currentMapData = gson.fromJson(reader, WorldMapData.class);

                Logger("INFO", "地图加载成功 - 背景: " + currentMapData.background +
                        ", 事件数: " + (currentMapData.event != null ? currentMapData.event.size() : 0) +
                        ", 空气墙数: " + (currentMapData.airWall != null ? currentMapData.airWall.size() : 0));

                // 初始化空气墙
                initAirWalls();

                // 初始化事件
                initEvents();

            } catch (Exception e) {
                Logger("ERROR", "加载世界地图失败: " + e.getMessage());
                throw new RuntimeException("无法加载地图配置文件: " + mapId, e);
            }
        } catch (Exception e) {
            Logger("ERROR", "加载世界地图异常: " + e.getMessage());
        }
    }

    /**
     * 初始化空气墙
     */
    private void initAirWalls() {
        airWalls.clear();
        if (currentMapData.airWall != null) {
            for (AirWallData wallData : currentMapData.airWall) {
                AirWall wall = new AirWall(
                        wallData.x,
                        wallData.y,
                        wallData.x_to,
                        wallData.y_to
                );
                airWalls.add(wall);
                Logger("DEBUG", "添加空气墙: (" + wallData.x + "," + wallData.y + ") -> (" + wallData.x_to + "," + wallData.y_to + ")");
            }
        }
    }

    /**
     * 初始化事件
     */
    private void initEvents() {
        triggeredEvents.clear();
        if (currentMapData.event != null) {
            for (EventData eventData : currentMapData.event) {
                Logger("DEBUG", "注册事件: " + eventData.id);
            }
        }

        // 初始化覆盖层事件
        if (currentMapData.overlay != null) {
            for (Map.Entry<String, OverlayData> entry : currentMapData.overlay.entrySet()) {
                Logger("DEBUG", "注册覆盖层对象: " + entry.getKey());
            }
        }
    }

    /**
     * 构建世界地图UI
     */
    public StackPane buildWorldMap() {
        if (currentMapData == null) {
            Logger("ERROR", "未加载地图数据");
            return null;
        }

        worldRoot = new StackPane();
        worldRoot.setStyle("-fx-background-color: #0f172a;");

        // 背景层
        Pane backgroundLayer = new Pane();
        backgroundView = loadBackground();
        if (backgroundView != null) {
            backgroundLayer.getChildren().add(backgroundView);
        }
        worldRoot.getChildren().add(backgroundLayer);

        // 事件层（用于显示可交互对象）
        eventLayer = new Pane();
        worldRoot.getChildren().add(eventLayer);

        // 添加覆盖层对象（如门等）
        addOverlayObjects();

        return worldRoot;
    }

    /**
     * 加载背景图片
     */
    private ImageView loadBackground() {
        try {
            String bgPath = currentMapData.background;
            if (bgPath == null || bgPath.trim().isEmpty()) {
                Logger("WARN", "背景路径为空");
                return null;
            }

            InputStream stream = ResourceResolver.getResourceAsStream(bgPath);
            if (stream == null) {
                Logger("ERROR", "无法加载背景: " + bgPath);
                return null;
            }

            Image bgImage = new Image(stream);
            stream.close();

            if (bgImage.isError()) {
                Logger("ERROR", "背景图片加载错误: " + bgPath);
                return null;
            }

            ImageView imageView = new ImageView(bgImage);
            imageView.setFitWidth(bgImage.getWidth());
            imageView.setFitHeight(bgImage.getHeight());

            Logger("INFO", String.format("背景加载成功: %.0fx%.0f", bgImage.getWidth(), bgImage.getHeight()));

            return imageView;
        } catch (Exception e) {
            Logger("ERROR", "加载背景异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 添加覆盖层对象
     */
    private void addOverlayObjects() {
        if (currentMapData.overlay == null) return;

        for (Map.Entry<String, OverlayData> entry : currentMapData.overlay.entrySet()) {
            String key = entry.getKey();
            OverlayData overlay = entry.getValue();

            if (overlay.location != null && overlay.texture != null) {
                ImageView overlayView = new ImageView();
                try {
                    InputStream stream = ResourceResolver.getResourceAsStream(overlay.texture);
                    if (stream != null) {
                        Image texture = new Image(stream);
                        stream.close();

                        if (!texture.isError()) {
                            overlayView.setImage(texture);
                            overlayView.setX(overlay.location.x);
                            overlayView.setY(overlay.location.y);
                            overlayView.setFitWidth(texture.getWidth());
                            overlayView.setFitHeight(texture.getHeight());

                            // 添加点击事件
                            overlayView.setOnMouseClicked(e -> {
                                Logger("INFO", "点击覆盖层对象: " + key);
                                handleOverlayEvent(key, overlay);
                            });

                            eventLayer.getChildren().add(overlayView);
                            Logger("DEBUG", "添加覆盖层对象: " + key + " at (" + overlay.location.x + "," + overlay.location.y + ")");
                        }
                    }
                } catch (Exception ex) {
                    Logger("ERROR", "加载覆盖层纹理失败: " + ex.getMessage());
                }
            }
        }
    }

    /**
     * 处理覆盖层事件
     */
    private void handleOverlayEvent(String overlayKey, OverlayData overlay) {
        if (overlay.event != null) {
            // 处理 onLeft 事件
            if (overlay.event.onLeft != null) {
                for (EventData eventData : overlay.event.onLeft) {
                    if (checkCondition(eventData.condition)) {
                        triggerEvent(eventData.id, eventData.args);
                    }
                }
            }
        }
    }

    /**
     * 更新玩家位置
     */
    public void updatePlayerPosition(double x, double y) {
        this.playerX = x;
        this.playerY = y;

        // 检查位置事件
        checkPositionEvents();
    }

    /**
     * 检查位置事件
     */
    private void checkPositionEvents() {
        if (currentMapData.event == null) return;

        for (EventData eventData : currentMapData.event) {
            // 避免重复触发
            if (triggeredEvents.containsKey(eventData.id)) {
                continue;
            }

            if (checkCondition(eventData.condition)) {
                triggerEvent(eventData.id, eventData.args);
            }
        }
    }

    /**
     * 检查条件
     */
    private boolean checkCondition(ConditionData condition) {
        if (condition == null) return true;

        // 检查玩家X坐标
        if (condition.player_x != null) {
            if (!checkRangeCondition(condition.player_x, playerX)) {
                return false;
            }
        }

        // 检查玩家Y坐标
        if (condition.player_y != null) {
            if (!checkRangeCondition(condition.player_y, playerY)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查范围条件
     * 格式: "=min-max" 表示在 min 和 max 之间
     */
    private boolean checkRangeCondition(String condition, double value) {
        if (condition == null || !condition.startsWith("=")) {
            return false;
        }

        try {
            String range = condition.substring(1);
            String[] parts = range.split("-");
            if (parts.length == 2) {
                double min = Double.parseDouble(parts[0]);
                double max = Double.parseDouble(parts[1]);
                return value >= min && value <= max;
            }
        } catch (Exception e) {
            Logger("ERROR", "解析范围条件失败: " + condition);
        }

        return false;
    }

    /**
     * 触发事件
     */
    private void triggerEvent(String eventId, List<String> args) {
        Logger("INFO", "触发事件: " + eventId + ", 参数: " + args);

        // 标记事件已触发
        triggeredEvents.put(eventId, true);

        // 通知监听器
        List<Consumer<Map<String, Object>>> listeners = eventListeners.get(eventId);
        if (listeners != null) {
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("eventId", eventId);
            eventData.put("args", args);
            eventData.put("playerX", playerX);
            eventData.put("playerY", playerY);

            for (Consumer<Map<String, Object>> listener : listeners) {
                try {
                    listener.accept(eventData);
                } catch (Exception e) {
                    Logger("ERROR", "事件监听器执行失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 注册事件监听器
     * @param eventId 事件ID
     * @param listener 监听器回调
     */
    public void addEventListener(String eventId, Consumer<Map<String, Object>> listener) {
        eventListeners.computeIfAbsent(eventId, k -> new ArrayList<>()).add(listener);
        Logger("DEBUG", "注册事件监听器: " + eventId);
    }

    /**
     * 移除事件监听器
     * @param eventId 事件ID
     * @param listener 监听器回调
     */
    public void removeEventListener(String eventId, Consumer<Map<String, Object>> listener) {
        List<Consumer<Map<String, Object>>> listeners = eventListeners.get(eventId);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                eventListeners.remove(eventId);
            }
        }
    }

    /**
     * 检查位置是否在空气墙内
     * @param x X坐标
     * @param y Y坐标
     * @return 如果在空气墙内返回true
     */
    public boolean isInAirWall(double x, double y) {
        for (AirWall wall : airWalls) {
            if (wall.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查移动路径是否穿过空气墙
     * @param fromX 起始X
     * @param fromY 起始Y
     * @param toX 目标X
     * @param toY 目标Y
     * @return 如果穿过空气墙返回true
     */
    public boolean isBlockedByAirWall(double fromX, double fromY, double toX, double toY) {
        for (AirWall wall : airWalls) {
            if (wall.intersectsLine(fromX, fromY, toX, toY)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 重置触发状态
     */
    public void resetTriggeredEvents() {
        triggeredEvents.clear();
        Logger("INFO", "重置事件触发状态");
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        triggeredEvents.clear();
        eventListeners.clear();
        airWalls.clear();
        currentMapData = null;
        currentMapId = null;
        Logger("INFO", "WorldEventManager 清理完成");
    }

    // ========== 数据类 ==========

    /**
     * 世界地图数据
     */
    public static class WorldMapData {
        public String background;
        public List<EventData> event;
        public Map<String, OverlayData> overlay;
        public List<AirWallData> airWall;
    }

    /**
     * 事件数据
     */
    public static class EventData {
        public String id;
        public ConditionData condition;
        public List<String> args;
    }

    /**
     * 条件数据
     */
    public static class ConditionData {
        public String player_x;
        public String player_y;
    }

    /**
     * 覆盖层数据
     */
    public static class OverlayData {
        public LocationData location;
        public String texture;
        public OverlayEventData event;
    }

    /**
     * 位置数据
     */
    public static class LocationData {
        public double x;
        public double y;
    }

    /**
     * 覆盖层事件数据
     */
    public static class OverlayEventData {
        public List<EventData> onLeft;
    }

    /**
     * 空气墙数据
     */
    public static class AirWallData {
        public double x;
        public double y;
        public double x_to;
        public double y_to;
    }

    /**
     * 空气墙类
     */
    private static class AirWall {
        private final double x1, y1, x2, y2;
        private final double minX, maxX, minY, maxY;

        public AirWall(double x1, double y1, double x2, double y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.minX = Math.min(x1, x2);
            this.maxX = Math.max(x1, x2);
            this.minY = Math.min(y1, y2);
            this.maxY = Math.max(y1, y2);
        }

        public boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        public boolean intersectsLine(double fromX, double fromY, double toX, double toY) {
            // 简单的线段相交检测
            return lineIntersectsLine(fromX, fromY, toX, toY, x1, y1, x2, y1) ||
                   lineIntersectsLine(fromX, fromY, toX, toY, x2, y1, x2, y2) ||
                   lineIntersectsLine(fromX, fromY, toX, toY, x2, y2, x1, y2) ||
                   lineIntersectsLine(fromX, fromY, toX, toY, x1, y2, x1, y1) ||
                   (contains(fromX, fromY) && contains(toX, toY));
        }

        private boolean lineIntersectsLine(double x1, double y1, double x2, double y2,
                                           double x3, double y3, double x4, double y4) {
            double denominator = (y4 - y3) * (x2 - x1) - (x4 - x3) * (y2 - y1);
            if (denominator == 0) return false;

            double ua = ((x4 - x3) * (y1 - y3) - (y4 - y3) * (x1 - x3)) / denominator;
            double ub = ((x2 - x1) * (y1 - y3) - (y2 - y1) * (x1 - x3)) / denominator;

            return ua >= 0 && ua <= 1 && ub >= 0 && ub <= 1;
        }
    }
}