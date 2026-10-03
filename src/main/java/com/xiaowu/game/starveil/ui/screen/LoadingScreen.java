package com.xiaowu.game.starveil.ui.screen;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.render.engine.*;
import com.xiaowu.game.starveil.render.engine.handles.*;

/**
 * 加载屏幕类 - 处理地图切换时的加载动画
 * 已迁移到使用统一的渲染引擎
 */
public class LoadingScreen {
    private final RenderEngine renderEngine;
    private Object transitionOverlay;
    private Object loadingIndicator;
    private AnimationHandle loadingAnimation;
    private Object gameContainer;
    private boolean isVisible = false;

    public LoadingScreen() {
        this.renderEngine = RenderEngineProvider.getInstance().getEngine();
        createTransitionOverlay();
    }

    /**
     * 创建过渡遮罩和加载指示器
     */
    private void createTransitionOverlay() {
        transitionOverlay = renderEngine.createStackPane();
        renderEngine.setBackground(transitionOverlay, "-fx-background-color: rgba(0, 0, 0, 0.8);");
        renderEngine.setVisible(transitionOverlay, false);
        renderEngine.setOpacity(transitionOverlay, 0);

        LoggerManager.Logger("DEBUG", "创建过渡遮罩");

        // 创建加载指示器
        loadingIndicator = createLoadingIndicator();
        renderEngine.setVisible(loadingIndicator, false);

        renderEngine.addChild(transitionOverlay, loadingIndicator);

        LoggerManager.Logger("DEBUG", "加载指示器已添加到容器");
    }

    /**
     * 创建加载指示器（使用圆形旋转动画）
     */
    private Object createLoadingIndicator() {
        Object container = renderEngine.createStackPane();
        renderEngine.setSize(container, 60, 60);

        int dotCount = 5;
        double radius = 18;
        double dotSize = 5;

        ColorDef whiteColor = renderEngine.createColor("white");
        
        // 创建简单的加载指示器 - 使用单个圆形
        Object dot = renderEngine.createCircle(30, 30, dotSize, whiteColor, null, 0);
        renderEngine.addChild(container, dot);

        // 创建旋转动画（简化版）
        // 注意：这里需要一个旋转动画，暂时使用淡入淡出代替
        loadingAnimation = renderEngine.createFadeIn(dot, 1000, 0.3, 1.0);

        return container;
    }

    /**
     * 设置游戏容器
     */
    public void setGameContainer(Object gameContainer) {
        this.gameContainer = gameContainer;
    }

    /**
     * 显示加载屏幕
     * @param showLoading 是否显示加载指示器
     */
    public void show(boolean showLoading) {
        if (gameContainer == null) {
            LoggerManager.Logger("WARN", "gameContainer 未设置，无法显示加载屏幕");
            return;
        }

        // 直接添加，引擎会处理重复检查
        renderEngine.addChild(gameContainer, transitionOverlay);
        LoggerManager.Logger("DEBUG", "过渡遮罩已添加到容器");

        // 确保遮罩在最上层
        renderEngine.bringToFront(transitionOverlay);

        // 显示遮罩并设置不透明度为0（准备渐入）
        renderEngine.setVisible(transitionOverlay, true);
        renderEngine.setOpacity(transitionOverlay, 0);

        // 渐入效果
        AnimationHandle fadeIn = renderEngine.createFadeIn(transitionOverlay, 200, 0, 1);
        renderEngine.playAnimation(fadeIn);

        // 显示/隐藏加载指示器
        if (loadingIndicator != null) {
            renderEngine.setVisible(loadingIndicator, showLoading);
            if (showLoading && loadingAnimation != null) {
                renderEngine.playAnimation(loadingAnimation);
                LoggerManager.Logger("DEBUG", "加载指示器开始动画");
            }
        }

        isVisible = true;
    }

    /**
     * 隐藏加载屏幕
     */
    public void hide() {
        if (transitionOverlay == null) return;

        // 渐出效果
        AnimationHandle fadeOut = renderEngine.createFadeOut(transitionOverlay, 300, 1, 0);
        
        // 简化处理：延迟后隐藏
        renderEngine.runLater(() -> {
            renderEngine.setVisible(transitionOverlay, false);

            if (loadingIndicator != null) {
                renderEngine.setVisible(loadingIndicator, false);
                if (loadingAnimation != null) {
                    renderEngine.stopAnimation(loadingAnimation);
                }
            }
        });

        isVisible = false;
    }

    /**
     * 获取过渡遮罩层
     */
    public Object getTransitionOverlay() {
        return transitionOverlay;
    }

    /**
     * 检查是否可见
     */
    public boolean isVisible() {
        return isVisible;
    }
}
