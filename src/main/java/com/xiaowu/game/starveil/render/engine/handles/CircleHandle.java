package com.xiaowu.game.starveil.render.engine.handles;

/**
 * 圆形句柄
 */
public class CircleHandle {
    private final Object nativeHandle;
    private double centerX, centerY, radius;

    public CircleHandle(Object nativeHandle, double centerX, double centerY, double radius) {
        this.nativeHandle = nativeHandle;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
    }

    public Object getNativeHandle() { return nativeHandle; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getRadius() { return radius; }

    public void setCenterX(double centerX) { this.centerX = centerX; }
    public void setCenterY(double centerY) { this.centerY = centerY; }
    public void setRadius(double radius) { this.radius = radius; }
}
