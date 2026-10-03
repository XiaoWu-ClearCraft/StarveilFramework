// DebugUI.java
package com.xiaowu.game.starveil.debug;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;

import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Attributes;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.render.Camera;
import com.xiaowu.game.starveil.config.GameConstants;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DebugUI {
    private final Pane viewport;
    private final Camera camera;
    private final WorldMap worldMap;
    private final World ecsWorld;
    private final int playerEntity;
    
    private boolean showDebug = false;
    private Circle deadZoneVisual;
    private Text debugInfo;
    private Rectangle viewportBorder;
    private Rectangle debugBg;
    
    // 性能监控
    private long lastDebugUpdate = 0;
    private final long DEBUG_UPDATE_INTERVAL = 100_000_000; // 0.1秒
    private double currentFPS = 60.0;
    private boolean isFullscreen = false;

    // ==================== 实体头顶属性标签（Minecraft 式） ====================
    private final Map<Integer, VBox> entityTags = new HashMap<>();
    private static final String TAG_BG_STYLE =
            "-fx-background-color: rgba(38,38,44,0.74); -fx-background-radius: 3; -fx-padding: 0 3 0 3;";
    private static final Font TAG_FONT = Font.font(11);
    private static final String COLOR_KEY = "#9a9a9a";
    private static final String COLOR_NUMBER = "#f1e08a";
    private static final String COLOR_BOOL = "#b5cea8";
    private static final String COLOR_STRING = "#9dd6f7";
    private static final String COLOR_PLAYER = "#ff9cc0";
    private static final String COLOR_NPC = "#7cd6d6";

    public DebugUI(Pane viewport, Camera camera, WorldMap worldMap, World ecsWorld, int playerEntity) {
        this.viewport = viewport;
        this.camera = camera;
        this.worldMap = worldMap;
        this.ecsWorld = ecsWorld;
        this.playerEntity = playerEntity;
    }
    
    public void initialize() {
        createDebugUI();
        updateVisibility();
    }
    
    /**
     * 创建调试UI元素
     */
    private void createDebugUI() {
        // 创建死区可视化（圆形）- 使用粉色系
        deadZoneVisual = new Circle(camera.getDeadZoneRadius());
        deadZoneVisual.setFill(Color.rgb(255, 105, 180, 0.2));
        deadZoneVisual.setStroke(Color.rgb(255, 105, 180, 0.8));
        deadZoneVisual.setStrokeWidth(2);
        deadZoneVisual.setMouseTransparent(true);

        // 将死区可视化放置在视口中心
        deadZoneVisual.centerXProperty().bind(camera.viewportWidthProperty().divide(2));
        deadZoneVisual.centerYProperty().bind(camera.viewportHeightProperty().divide(2));

        // 创建调试信息文本 - 使用粉色
        debugInfo = new Text();
        debugInfo.setFont(Font.font("Consolas", 12));
        debugInfo.setFill(Color.web(ContentConfig.primaryColor()));
        debugInfo.setStroke(Color.web(ContentConfig.primaryColor()));
        debugInfo.setStrokeWidth(0.5);
        debugInfo.setX(20);
        debugInfo.setY(30);

        // 创建调试信息背景 - 使用粉色系
        debugBg = new Rectangle(380, 250);
        debugBg.setFill(Color.rgb(0, 0, 0, 0.5));
        debugBg.setStroke(Color.web(ContentConfig.primaryColor()));
        debugBg.setStrokeWidth(1);
        debugBg.setMouseTransparent(true);
        debugBg.setX(10);
        debugBg.setY(10);

        // 创建视窗边框 - 使用粉色
        viewportBorder = new Rectangle();
        viewportBorder.fillProperty().set(Color.TRANSPARENT);
        viewportBorder.setStroke(Color.web(ContentConfig.primaryColor()));
        viewportBorder.setStrokeWidth(2);
        viewportBorder.setMouseTransparent(true);
        viewportBorder.widthProperty().bind(camera.viewportWidthProperty());
        viewportBorder.heightProperty().bind(camera.viewportHeightProperty());

        // 将调试元素添加到视口
        viewport.getChildren().addAll(debugBg, deadZoneVisual, debugInfo, viewportBorder);
    }
    
    /**
     * 更新调试信息显示状态
     */
    public void updateVisibility() {
        if (deadZoneVisual != null) {
            boolean shouldShow = showDebug;
            deadZoneVisual.setVisible(shouldShow);
            viewportBorder.setVisible(shouldShow);
            debugInfo.setVisible(shouldShow);
            debugBg.setVisible(shouldShow);
            // 让 WorldMap 在调试时显示空气墙轮廓
            if (worldMap != null) worldMap.showAirWalls(shouldShow);
        }
        if (!showDebug) {
            clearEntityTags();
        }
    }
    
     /**
     * 更新调试信息
     */
    public void updateDebugInfo(long now) {
        if (!showDebug) return;

        // 实体头顶属性标签：位置与内容都动态刷新（每帧重定位，内容变更才重建）
        updateEntityTags();

        if (now - lastDebugUpdate >= DEBUG_UPDATE_INTERVAL) {
            Transform pt = ecsWorld != null && playerEntity >= 0
                    ? ecsWorld.get(playerEntity, Transform.class) : null;
            if (pt == null) return;

            double playerCenterX = pt.centerX();
            double playerCenterY = pt.centerY();
            
            // 计算玩家在屏幕上的位置
            double playerScreenX = playerCenterX + worldMap.getLayoutX();
            double playerScreenY = playerCenterY + worldMap.getLayoutY();
            
            // 屏幕中心
            double screenCenterX = camera.getViewportWidth() / 2;
            double screenCenterY = camera.getViewportHeight() / 2;
            
            // 计算玩家到屏幕中心的距离
            double dx = playerScreenX - screenCenterX;
            double dy = playerScreenY - screenCenterY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            
            // 检查玩家是否在边界
            boolean isPlayerAtBoundary = worldMap.isPlayerOutOfBounds(
                pt.x, pt.y, pt.width, pt.height);
            
            // 这里简化计算，实际应该从Camera类获取限制状态
            boolean isCameraAtBoundary = false; // 可以根据需要添加详细计算
            
            // 更新死区颜色
            if (distance > camera.getDeadZoneRadius()) {
                deadZoneVisual.setFill(Color.rgb(255, 0, 0, 0.3));
            } else {
                deadZoneVisual.setFill(Color.rgb(0, 255, 0, 0.2));
            }
            
            // 构建调试信息
            String info = String.format(
                "=== 摄像机跟随调试 ===\n" +
                "视口大小: %.0f x %.0f\n" +
                "玩家世界坐标: (%.0f, %.0f)\n" +
                "玩家屏幕坐标: (%.0f, %.0f)\n" +
                "摄像机偏移: (%.0f, %.0f)\n" +
                "距中心距离: %.0f / %.0f\n" +
                "玩家边界: %s\n" +
                "摄像机边界: %s\n" +
                "FPS: %.0f\n" +
                "全屏: %s\n" +
                "调试: %s (F3开关)",
                camera.getViewportWidth(), camera.getViewportHeight(),
                pt.x, pt.y,
                playerScreenX, playerScreenY,
                worldMap.getLayoutX(), worldMap.getLayoutY(),
                distance, camera.getDeadZoneRadius(),
                isPlayerAtBoundary ? "触及" : "正常",
                isCameraAtBoundary ? "触及" : "正常",
                currentFPS,
                isFullscreen ? "是" : "否",
                showDebug ? "开启" : "关闭"
            );
            
            debugInfo.setText(info);
            lastDebugUpdate = now;
        }
    }

    // ==================== 实体头顶属性标签 ====================

    private final Map<Integer, String> tagSignatures = new HashMap<>();

    /** 清理所有头顶标签。 */
    private void clearEntityTags() {
        if (worldMap == null) return;
        Pane pane = worldMap.getWorld();
        for (VBox box : new java.util.ArrayList<>(entityTags.values())) {
            if (box.getParent() == pane) {
                pane.getChildren().remove(box);
            }
        }
        entityTags.clear();
        tagSignatures.clear();
    }

    /** 每帧刷新：确保每个带属性的实体头上都有标签，并定位到其正上方。 */
    private void updateEntityTags() {
        if (ecsWorld == null || worldMap == null) return;
        Pane pane = worldMap.getWorld();
        if (pane == null) return;

        Set<Integer> alive = new HashSet<>();
        for (int e : ecsWorld.view(Attributes.class, Transform.class, Sprite.class)) {
            alive.add(e);
            refreshEntityTag(e, pane);
        }

        // 移除已销毁（死亡）实体的标签
        java.util.Iterator<Map.Entry<Integer, VBox>> it = entityTags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, VBox> entry = it.next();
            if (!alive.contains(entry.getKey())) {
                if (entry.getValue().getParent() == pane) {
                    pane.getChildren().remove(entry.getValue());
                }
                it.remove();
                tagSignatures.remove(entry.getKey());
            }
        }
    }

    private void refreshEntityTag(int e, Pane pane) {
        Transform t = ecsWorld.get(e, Transform.class);
        Attributes attrs = ecsWorld.get(e, Attributes.class);
        Health health = ecsWorld.get(e, Health.class);
        if (t == null || attrs == null) return;

        boolean isPlayer = Attributes.ENTITY_TYPE_PLAYER.equals(attrs.get(Attributes.ENTITY_TYPE));
        String headerColor = isPlayer ? COLOR_PLAYER : COLOR_NPC;

        // 生成内容签名（变化时才重建子节点，减少抖动）
        StringBuilder sb = new StringBuilder();
        String header = attrs.getString(Attributes.DISPLAY_NAME, "");
        sb.append(header).append('\u0000');
        for (String key : attrs.keys()) {
            if (key.equals(Attributes.DISPLAY_NAME) || key.equals(Attributes.ENTITY_TYPE)) continue;
            Object v = attrs.get(key);
            sb.append(key).append('=').append(String.valueOf(v)).append('\u0000');
        }
        if (health != null) {
            sb.append("starveil:health=").append((int) health.current).append('/').append((int) health.max);
        }
        String signature = sb.toString();

        VBox box = entityTags.get(e);
        if (box == null) {
            VBox nb = new VBox();
            nb.setStyle(TAG_BG_STYLE);
            nb.setMouseTransparent(true);
            nb.layoutBoundsProperty().addListener((obs, o, n) -> {
                if (n != null) {
                    nb.setTranslateX(-n.getWidth() / 2);
                    nb.setTranslateY(-n.getHeight() - 2);
                }
            });
            entityTags.put(e, nb);
            tagSignatures.put(e, signature);
            rebuildTagRows(nb, header, headerColor, attrs, health);
            pane.getChildren().add(nb);
            box = nb;
        } else if (box.getParent() != pane) {
            pane.getChildren().add(box);
        } else if (!signature.equals(tagSignatures.get(e))) {
            tagSignatures.put(e, signature);
            box.getChildren().clear();
            rebuildTagRows(box, header, headerColor, attrs, health);
        }

        // 定位到头顶：x = 实体中心，y = 实体上沿
        box.setLayoutX(t.centerX());
        box.setLayoutY(t.y);
    }

    private void rebuildTagRows(VBox box, String header, String headerColor, Attributes attrs, Health health) {
        if (header != null && !header.isEmpty()) {
            box.getChildren().add(tagRow(header, headerColor, true));
        }
        for (String key : attrs.keys()) {
            if (key.equals(Attributes.DISPLAY_NAME) || key.equals(Attributes.ENTITY_TYPE)) {
                continue;
            }
            Object v = attrs.get(key);
            String text = key + " = " + v;
            box.getChildren().add(tagRow(text, colorFor(v), false));
        }
        if (health != null) {
            String text = "starveil:health = " + (int) health.current + " / " + (int) health.max;
            boolean low = health.max > 0 && health.current / health.max < 0.4;
            box.getChildren().add(tagRow(text, low ? "#ff6b6b" : COLOR_NUMBER, false));
        }
    }

    private Text tagRow(String text, String colorHex, boolean bold) {
        Text line = new Text(text);
        line.setFont(bold ? Font.font(TAG_FONT.getFamily(), javafx.scene.text.FontWeight.BOLD, TAG_FONT.getSize())
                : TAG_FONT);
        line.setFill(Color.web(colorHex));
        return line;
    }

    private static String colorFor(Object v) {
        if (v instanceof Boolean) {
            return COLOR_BOOL;
        }
        if (v instanceof Number) {
            return COLOR_NUMBER;
        }
        return COLOR_STRING;
    }

    // 在调试绘制 NPC 视野和攻击半径
    private void drawNpcDebug() {
        if (!showDebug) return;
        if (worldMap == null) return;
        // 简单实现：为每个 NPC 在其位置绘制两个圆形（视野/攻击），暂用 Rectangle 的 stroke 来表示
        // 这里直接在 viewport 添加小圆以示意
        // 清理上次的标记（通过id判断不做复杂清理以保持简单）
        for (javafx.scene.Node n : worldMap.getWorld().getChildren()) {
            // no-op
        }
    }
    
    public boolean isShowDebug() {
        return showDebug;
    }
    
    public void setShowDebug(boolean showDebug) {
        this.showDebug = showDebug;
    }
    
    public void setCurrentFPS(double currentFPS) {
        this.currentFPS = currentFPS;
    }
    
    public void setFullscreen(boolean isFullscreen) {
        this.isFullscreen = isFullscreen;
    }
    
    public void toggleDebug() {
        showDebug = !showDebug;
        updateVisibility();
    }
}