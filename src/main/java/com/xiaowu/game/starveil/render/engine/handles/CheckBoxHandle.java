package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 复选框句柄
 */
public class CheckBoxHandle {
    private final Object nativeHandle;
    private java.util.function.BiConsumer<Boolean, Boolean> listener;

    public CheckBoxHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public java.util.function.BiConsumer<Boolean, Boolean> getListener() { return listener; }
    public void setListener(java.util.function.BiConsumer<Boolean, Boolean> listener) { 
        this.listener = listener; 
    }
}
