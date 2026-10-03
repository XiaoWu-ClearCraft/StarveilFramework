package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 低血量自动恢复被动（数据：仅计时器）。
 */
public final class AutoHeal implements Component {
    public double tickAccumulator = 0;

    public AutoHeal() {
    }
}
