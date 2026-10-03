package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 魔力组件（数据）。系统负责恢复与消耗。
 */
public final class Magic implements Component {
    public double max = 100;
    public double current = 100;
    public boolean isDepleted;
    public double timeSinceLastCast;

    public double percentage() {
        return max > 0 ? current / max : 0;
    }

    /** 尝试消耗，不足返回 false。 */
    public boolean consume(double amount) {
        if (current < amount) {
            return false;
        }
        current -= amount;
        timeSinceLastCast = 0;
        if (current <= 0) {
            isDepleted = true;
        }
        return true;
    }

    public boolean canCast(double cost) {
        return current >= cost;
    }

    public void reset() {
        current = max;
        isDepleted = false;
        timeSinceLastCast = 0;
    }
}
