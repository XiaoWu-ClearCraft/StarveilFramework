package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 进度条句柄
 */
public class ProgressBarHandle {
    private final Object nativeHandle;
    private double currentProgress = 0;

    public ProgressBarHandle(Object nativeHandle) {
        this.nativeHandle = nativeHandle;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public double getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(double progress) { this.currentProgress = progress; }
}
