package com.xiaowu.game.starveil.render.engine;

/**
 * 渲染引擎提供者 - 全局单例,提供对当前 RenderEngine 实例的访问
 * 
 * 这个类作为过渡方案,让现有代码可以逐步迁移到使用 RenderEngine 接口,
 * 而不需要在每个类中都传入 RenderEngine 实例。
 */
public class RenderEngineProvider {
    private static RenderEngineProvider instance;
    private RenderEngine engine;

    private RenderEngineProvider() {}

    public static RenderEngineProvider getInstance() {
        if (instance == null) {
            instance = new RenderEngineProvider();
        }
        return instance;
    }

    /**
     * 设置当前的渲染引擎实例
     */
    public void setEngine(RenderEngine engine) {
        this.engine = engine;
    }

    /**
     * 获取当前的渲染引擎实例
     * @throws IllegalStateException 如果引擎未初始化
     */
    public RenderEngine getEngine() {
        if (engine == null) {
            throw new IllegalStateException("RenderEngine 未初始化。请先调用 setEngine() 方法。");
        }
        return engine;
    }

    /**
     * 检查渲染引擎是否已初始化
     */
    public boolean isInitialized() {
        return engine != null;
    }
}
