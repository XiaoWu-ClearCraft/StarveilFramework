package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 滑块句柄
 */
public class SliderHandle {
    private final Object nativeHandle;
    private java.util.function.BiConsumer<Double, Double> listener;

    public SliderHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public java.util.function.BiConsumer<Double, Double> getListener() { return listener; }
    public void setListener(java.util.function.BiConsumer<Double, Double> listener) { 
        this.listener = listener; 
    }
}
