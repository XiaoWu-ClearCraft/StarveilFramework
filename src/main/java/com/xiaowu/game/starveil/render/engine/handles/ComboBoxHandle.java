package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 下拉框句柄
 */
public class ComboBoxHandle {
    private final Object nativeHandle;
    private Runnable onAction;

    public ComboBoxHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public Runnable getOnAction() { return onAction; }
    public void setOnAction(Runnable onAction) { this.onAction = onAction; }
}
