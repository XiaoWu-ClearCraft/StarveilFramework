package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 法阵攻击蓄力状态。
 */
public final class Charge implements Component {
    public boolean active;
    public long startTimeMs;

    /** 蓄力时长（秒）；未蓄力返回 0。 */
    public double duration() {
        if (!active) {
            return 0;
        }
        return (System.currentTimeMillis() - startTimeMs) / 1000.0;
    }

    /** 蓄力时间映射到 10~50 的伤害。 */
    public double calculateDamage() {
        double clamped = Math.min(duration(), 3.0);
        return 10.0 + (clamped / 3.0) * 40.0;
    }

    public void start() {
        active = true;
        startTimeMs = System.currentTimeMillis();
    }

    public void stop() {
        active = false;
    }
}
