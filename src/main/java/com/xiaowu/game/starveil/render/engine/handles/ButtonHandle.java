package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 按钮句柄
 */
public class ButtonHandle {
    private final Object nativeHandle;
    private Runnable action;

    public ButtonHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public Runnable getAction() { return action; }
    public void setAction(Runnable action) { this.action = action; }
}
