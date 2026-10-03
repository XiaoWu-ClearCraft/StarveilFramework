package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 事件处理器句柄
 */
public class EventHandlerHandle {
    private final Object nativeHandle;

    public EventHandlerHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
}
