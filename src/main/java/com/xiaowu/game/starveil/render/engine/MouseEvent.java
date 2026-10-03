package com.xiaowu.game.starveil.render.engine;

/**
 * 鼠标事件抽象类
 */
public class MouseEvent {
    public enum MouseButton {
        PRIMARY, SECONDARY, MIDDLE
    }

    private final MouseButton button;
    private final double x, y;
    private boolean consumed = false;

    public MouseEvent(MouseButton button, double x, double y) {
        this.button = button;
        this.x = x;
        this.y = y;
    }

    public MouseButton getButton() { return button; }
    public double getX() { return x; }
    public double getY() { return y; }
    public void consume() { consumed = true; }
    public boolean isConsumed() { return consumed; }
}
