package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 标签句柄
 */
public class LabelHandle {
    private final Object nativeHandle;

    public LabelHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
}
