package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 位置/包围盒组件。碰撞与移动逻辑都以此为准。
 */
public final class Transform implements Component {
    public double x;
    public double y;
    public double width = 100;
    public double height = 100;

    public Transform() {
    }

    public Transform(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double centerX() {
        return x + width / 2;
    }

    public double centerY() {
        return y + height / 2;
    }
}
