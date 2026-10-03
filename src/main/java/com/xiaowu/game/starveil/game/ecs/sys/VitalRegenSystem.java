package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.MagicSystem;
import com.xiaowu.game.starveil.game.ecs.EcsSystem;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.AutoHeal;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Magic;
import com.xiaowu.game.starveil.game.ecs.comp.PlayerController;
import com.xiaowu.game.starveil.ui.core.GameUI;

/**
 * 生命恢复系统：魔力自然恢复 + 低血量自动回血（移植自 MagicManager / AutoHealPassive）。
 */
public final class VitalRegenSystem implements EcsSystem {
    private static final double MAGIC_RECOVERY_RATE = 100 / 60.0;
    private static final double MAGIC_RECOVERY_DELAY = 3.0;

    private static final double HP_THRESHOLD = 0.20;
    private static final double HEAL_PER_TICK = 2.5;
    private static final double MAGIC_COST_PER_TICK = 5.0;
    private static final double TICK_INTERVAL = 0.5;

    @Override
    public void update(World world, double deltaTime) {
        int[] ps = world.view(PlayerController.class);
        if (ps.length == 0) {
            return;
        }
        int pe = ps[0];
        Magic magic = world.get(pe, Magic.class);
        Health health = world.get(pe, Health.class);
        AutoHeal autoHeal = world.get(pe, AutoHeal.class);
        if (magic == null || health == null || autoHeal == null) {
            return;
        }

        if (MagicSystem.isEnabled()) {
            updateMagic(magic, deltaTime);
            updateAutoHeal(autoHeal, health, magic, deltaTime);
        }
    }

    private static void updateMagic(Magic magic, double dt) {
        if (magic.timeSinceLastCast < MAGIC_RECOVERY_DELAY) {
            magic.timeSinceLastCast += dt;
        } else {
            magic.current = Math.min(magic.max, magic.current + MAGIC_RECOVERY_RATE * dt);
            if (magic.current >= magic.max) {
                magic.isDepleted = false;
            }
            pushMagicUI(magic);
        }
    }

    private static void updateAutoHeal(AutoHeal autoHeal, Health health, Magic magic, double dt) {
        if (health.max <= 0) {
            return;
        }
        if (health.current / health.max >= HP_THRESHOLD) {
            return;
        }
        if (health.current <= 0) {
            return;
        }
        autoHeal.tickAccumulator += dt;
        if (autoHeal.tickAccumulator < TICK_INTERVAL) {
            return;
        }
        autoHeal.tickAccumulator = 0;
        if (magic.consume(MAGIC_COST_PER_TICK)) {
            health.current = Math.min(health.max, health.current + HEAL_PER_TICK);
            GameUI.getInstance().setHealth(health.percentage());
        }
    }

    public static void pushMagicUI(Magic magic) {
        GameUI.getInstance().setMagic(magic.max > 0 ? magic.current / magic.max : 0, magic.isDepleted);
    }
}
