package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 线条句柄
 */
public class LineHandle {
    private final Object nativeHandle;
    private double startX, startY, endX, endY;

    public LineHandle(Object nativeHandle, double startX, double startY, double endX, double endY) {
        this.nativeHandle = nativeHandle;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public double getStartX() { return startX; }
    public double getStartY() { return startY; }
    public double getEndX() { return endX; }
    public double getEndY() { return endY; }
}
