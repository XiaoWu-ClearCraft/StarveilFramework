package com.xiaowu.game.starveil.render.engine.handles;

import javafx.animation.Animation;

/**
 * 动画句柄
 */
public class AnimationHandle {
    private final Object nativeHandle;
    private Runnable onComplete;

    public AnimationHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public Runnable getOnComplete() { return onComplete; }

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
        // 如果原生句柄是JavaFX Animation,直接设置完成回调
        if (nativeHandle instanceof Animation animation && onComplete != null) {
            animation.setOnFinished(e -> onComplete.run());
        }
    }
}
