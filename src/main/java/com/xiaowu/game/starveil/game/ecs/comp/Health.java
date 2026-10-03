package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 生命值组件。
 */
public final class Health implements Component {
    public double max = 100;
    public double current = 100;
    /** maxHealth <= 0 时视为不死（immortal）。 */
    public boolean immortal;
    public boolean dead;
    public boolean deathAnimationStarted;

    public Health() {
    }

    public Health(double max, double current) {
        this.max = max;
        this.current = current;
        if (max <= 0) {
            immortal = true;
        }
    }

    public double percentage() {
        return max > 0 ? current / max : 0;
    }

    /** 掉血。返回是否死亡（血量归零）。 */
    public boolean takeDamage(double amount) {
        if (dead) {
            return true;
        }
        if (immortal) {
            return false;
        }
        current = Math.max(0, current - amount);
        if (current <= 0) {
            dead = true;
            return true;
        }
        return false;
    }

    public void heal(double amount) {
        current = Math.min(max, current + amount);
        if (dead && current > 0) {
            dead = false;
        }
    }

    public void reset() {
        current = max;
        dead = false;
        deathAnimationStarted = false;
    }
}
