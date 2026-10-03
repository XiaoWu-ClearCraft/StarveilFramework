// Camera.java
package com.xiaowu.game.starveil.render;

import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * 摄像机类 - 使用 RenderEngine 接口进行摄像机控制
 * 
 * 重构后不再直接依赖 JavaFX 的 DoubleProperty,
 * 而是通过 RenderEngine 接口进行摄像机操作。
 */
public class Camera {
    private final RenderEngine renderEngine;
    
    // 保留 JavaFX 属性以保持向后兼容
    private final DoubleProperty viewportWidthProp = new SimpleDoubleProperty(1200);
    private final DoubleProperty viewportHeightProp = new SimpleDoubleProperty(675);
    
    private double viewportWidth = 1200;
    private double viewportHeight = 675;
    private final double deadZoneRadius = 100; // 圆形死区半径
    private final double cameraSmoothing = 0.3; // 摄像机平滑系数

    // 摄像机移动平滑参数
    private double targetCameraMoveX = 0;
    private double targetCameraMoveY = 0;

    /**
     * 创建摄像机 (需要传入 RenderEngine)
     */
    public Camera(RenderEngine renderEngine) {
        this.renderEngine = renderEngine;
    }

    /**
     * 使用圆形死区更新摄像机（带平滑移动，基于 deltaTime）
     */
    public void updateWithCircularDeadZone(WorldMap worldMap, World ecsWorld, int playerEntity, double deltaTime) {
        if (renderEngine == null) return;

        Transform t = ecsWorld != null && playerEntity >= 0
                ? ecsWorld.get(playerEntity, Transform.class) : null;
        if (t == null) return;

        double playerCenterX = t.centerX();
        double playerCenterY = t.centerY();

        // 使用 RenderEngine 进行摄像机更新
        renderEngine.updateCamera(worldMap.getWorld(), playerCenterX, playerCenterY, deltaTime);
    }

    /**
     * 限制摄像机位置，使其不超出地图边界
     * 当地图小于视口时，地图在视口中居中显示
     */
    private double[] clampCameraPosition(double worldX, double worldY, WorldMap worldMap) {
        double clampedX = worldX;
        double clampedY = worldY;

        int mapWidth = worldMap.getWorldWidth();
        int mapHeight = worldMap.getWorldHeight();
        double viewportW = viewportWidth;
        double viewportH = viewportHeight;

        // 如果地图宽度小于视口宽度，地图在水平方向居中
        if (mapWidth < viewportW) {
            clampedX = (viewportW - mapWidth) / 2;
        } else {
            // 地图宽度大于视口宽度，限制摄像机偏移
            double maxOffsetX = 0;  // 摄像机最左位置（世界右移）
            double minOffsetX = viewportW - mapWidth;  // 摄像机最右位置（世界左移）

            if (clampedX > maxOffsetX) clampedX = maxOffsetX;
            if (clampedX < minOffsetX) clampedX = minOffsetX;
        }

        // 如果地图高度小于视口高度，地图在垂直方向居中
        if (mapHeight < viewportH) {
            clampedY = (viewportH - mapHeight) / 2;
        } else {
            // 地图高度大于视口高度，限制摄像机偏移
            double maxOffsetY = 0;  // 摄像机最上位置（世界下移）
            double minOffsetY = viewportH - mapHeight;  // 摄像机最下位置（世界上移）

            if (clampedY > maxOffsetY) clampedY = maxOffsetY;
            if (clampedY < minOffsetY) clampedY = minOffsetY;
        }

        return new double[]{clampedX, clampedY};
    }

    /**
     * 初始居中摄像机
     */
    public void centerOnPlayer(WorldMap worldMap, World ecsWorld, int playerEntity) {
        if (renderEngine == null) return;

        Transform t = ecsWorld != null && playerEntity >= 0
                ? ecsWorld.get(playerEntity, Transform.class) : null;
        if (t == null) return;

        double playerCenterX = t.centerX();
        double playerCenterY = t.centerY();

        // 使用 RenderEngine 居中摄像机
        renderEngine.centerCamera(worldMap.getWorld(), playerCenterX, playerCenterY);

        // 初始化摄像机目标移动
        targetCameraMoveX = 0;
        targetCameraMoveY = 0;
    }

    public double getViewportWidth() {
        return viewportWidth;
    }

    public double getViewportHeight() {
        return viewportHeight;
    }

    public double getDeadZoneRadius() {
        return deadZoneRadius;
    }

    public void setViewportSize(double width, double height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
        this.viewportWidthProp.set(width);
        this.viewportHeightProp.set(height);
        
        // 同步更新 RenderEngine 的视口
        if (renderEngine != null) {
            renderEngine.setViewportSize(width, height);
        }
    }

    /**
     * 获取视口宽度属性 (用于 JavaFX 绑定)
     */
    public DoubleProperty viewportWidthProperty() {
        return viewportWidthProp;
    }

    /**
     * 获取视口高度属性 (用于 JavaFX 绑定)
     */
    public DoubleProperty viewportHeightProperty() {
        return viewportHeightProp;
    }

    /**
     * 获取渲染引擎实例
     */
    public RenderEngine getRenderEngine() {
        return renderEngine;
    }
}
