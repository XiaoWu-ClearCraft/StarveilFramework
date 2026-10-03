package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 文本动画句柄 - 用于控制逐字动画
 */
public class TextAnimationHandle {
    private final Object nativeHandle;
    private final Object textContainer;
    private final String fullMessage;

    public TextAnimationHandle(Object nativeHandle, Object textContainer, String fullMessage) {
        this.nativeHandle = nativeHandle;
        this.textContainer = textContainer;
        this.fullMessage = fullMessage;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public Object getTextContainer() { return textContainer; }
    public String getFullMessage() { return fullMessage; }
}
