package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 体力组件（数据）。消耗/恢复规则见对应系统。
 */
public final class Stamina implements Component {
    public double max = 100;
    public double current = 100;
    /** 疾跑需要的最小体力。 */
    public double minToSprint = 20;
    public boolean isExhausted;
    public boolean isSprinting;
    public double timeSinceSprintEnd;

    public double percentage() {
        return max > 0 ? current / max : 0;
    }

    public boolean canSprint() {
        return !isExhausted;
    }

    public void stopSprinting() {
        timeSinceSprintEnd = 0;
    }

    public void reset() {
        current = max;
        isExhausted = false;
        isSprinting = false;
        timeSinceSprintEnd = 0;
    }
}
