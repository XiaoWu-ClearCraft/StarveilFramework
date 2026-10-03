package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 输入框句柄
 */
public class TextFieldHandle {
    private final Object nativeHandle;

    public TextFieldHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
}
