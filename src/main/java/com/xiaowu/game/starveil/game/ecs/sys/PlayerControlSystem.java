package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.ecs.EcsSystem;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Charge;
import com.xiaowu.game.starveil.game.ecs.comp.PlayerController;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Stamina;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.ui.core.GameUI;

/**
 * 玩家控制系统：读取输入 → 更新体力 → 按空气墙/边界移动 → 更新朝向。
 * 蓄力攻击期间禁止移动。
 */
public final class PlayerControlSystem implements EcsSystem {
    private static final double SPRINT_CONSUMPTION_RATE = 100 / 30.0;
    private static final double STAMINA_RECOVERY_RATE = 100 / 45.0;
    private static final double RECOVERY_DELAY = 5.0;

    /** 速度低于此值即视为静止（惯性滑行的收尾）。 */
    private static final double MOVING_SPEED_EPSILON = 1e-3;

    /** 急停结束时，速度需降到「触发时速度」的这个比例以下。 */
    private static final double SKID_END_FRACTION = 0.15;

    @Override
    public void update(World world, double deltaTime) {
        int[] ps = world.view(PlayerController.class);
        if (ps.length == 0) {
            return;
        }
        int pe = ps[0];

        WorldMap map = world.getResource(WorldMap.class);
        InputHandler ih = world.getResource(InputHandler.class);
        if (map == null || ih == null) {
            return;
        }

        Transform t = world.get(pe, Transform.class);
        Sprite spr = world.get(pe, Sprite.class);
        Stamina st = world.get(pe, Stamina.class);
        PlayerController ctl = world.get(pe, PlayerController.class);
        if (t == null || spr == null || st == null || ctl == null) {
            return;
        }

        Charge charge = world.get(pe, Charge.class);
        if (charge != null && charge.active) {
            // 蓄力是「其他动作」，会打断急停
            spr.skidding = false;
            ctl.skidReferenceSpeed = 0;
            spr.setMoving(false);
            return;
        }

        boolean wantsSprint = ih.isKeyPressed("ACCELERATE");
        boolean hasInput = ih.isKeyPressed("MOVE_UP") || ih.isKeyPressed("MOVE_DOWN")
                || ih.isKeyPressed("MOVE_LEFT") || ih.isKeyPressed("MOVE_RIGHT");

        boolean wasSprinting = st.isSprinting;
        boolean isAccelerating = wantsSprint && st.canSprint();
        boolean isSprinting = isAccelerating && hasInput;
        st.isSprinting = isSprinting;

        updateStamina(st, deltaTime, isSprinting, wasSprinting);

        // 输入给出的是「这一帧的期望位移」，除以 dt 换算成期望速度。
        // 惯性并不改变目标速度，只是让实际速度去逼近它。
        double[] movement = ih.getMovementVector(ctl.moveSpeed, isAccelerating, deltaTime);
        double desiredVx = deltaTime > 0 ? movement[0] / deltaTime : 0;
        double desiredVy = deltaTime > 0 ? movement[1] / deltaTime : 0;
        boolean wantsMove = desiredVx != 0 || desiredVy != 0;

        boolean inertia = DataManager.isInertiaEnabled();
        updateVelocity(ctl, desiredVx, desiredVy, wantsMove, inertia, deltaTime);
        updateSkid(spr, ctl, inertia, wasSprinting, isSprinting, wantsMove);

        double deltaX = ctl.velocityX * deltaTime;
        double deltaY = ctl.velocityY * deltaTime;
        if (deltaX != 0 || deltaY != 0) {
            movePlayer(t, spr, deltaX, deltaY, map);
        }
        // 是否算「在移动」按实际速度判定，而不是按有没有按键 ——
        // 否则惯性滑行期间会错误地显示待机姿态。
        spr.setMoving(ctl.speed() > MOVING_SPEED_EPSILON);
    }

    /**
     * 速度积分。
     *
     * <p>惯性关闭时速度等于输入给出的期望速度，行为与改动前完全一致
     * （瞬时启停）；开启时按 {@code accelRate}/{@code decelRate} 逼近目标，
     * 松开方向键即进入滑行减速。
     */
    private static void updateVelocity(PlayerController ctl, double desiredVx, double desiredVy,
                                       boolean wantsMove, boolean inertia, double dt) {
        if (!inertia) {
            ctl.velocityX = desiredVx;
            ctl.velocityY = desiredVy;
            return;
        }
        double targetVx = wantsMove ? desiredVx : 0;
        double targetVy = wantsMove ? desiredVy : 0;
        double rate = wantsMove ? ctl.accelRate : ctl.decelRate;
        ctl.velocityX = approach(ctl.velocityX, targetVx, rate * dt);
        ctl.velocityY = approach(ctl.velocityY, targetVy, rate * dt);
    }

    private static double approach(double current, double target, double maxDelta) {
        double diff = target - current;
        if (Math.abs(diff) <= maxDelta) {
            return target;
        }
        return current + Math.signum(diff) * maxDelta;
    }

    /**
     * 急停状态：疾跑结束、速度尚未降下来时播放，可被其他动作打断。
     *
     * <p>结束阈值取「触发时速度」的比例而非绝对速度 —— 速度单位取决于
     * {@code moveSpeed} 与 {@code getMovementVector} 的实现约定，
     * 写死绝对值会在换单位后悄悄失效。
     */
    private static void updateSkid(Sprite spr, PlayerController ctl, boolean inertia,
                                  boolean wasSprinting, boolean isSprinting, boolean wantsMove) {
        if (!inertia) {
            spr.skidding = false;
            ctl.skidReferenceSpeed = 0;
            return;
        }
        // 打断条件 1：重新有输入（可能已经反向加速）
        if (wantsMove) {
            spr.skidding = false;
            ctl.skidReferenceSpeed = 0;
            return;
        }
        double speed = ctl.speed();
        if (spr.skidding) {
            // 打断条件 2：已经停稳
            if (speed <= ctl.skidReferenceSpeed * SKID_END_FRACTION) {
                spr.skidding = false;
                ctl.skidReferenceSpeed = 0;
            }
            return;
        }
        // 触发：疾跑刚结束，且还在滑行
        if (wasSprinting && !isSprinting && speed > 0) {
            spr.skidding = true;
            ctl.skidReferenceSpeed = speed;
        }
    }

    private static void updateStamina(Stamina st, double dt, boolean isSprinting, boolean wasSprinting) {
        if (isSprinting) {
            st.current -= SPRINT_CONSUMPTION_RATE * dt;
            if (st.current <= 0) {
                st.current = 0;
                st.isExhausted = true;
            }
            pushStaminaUI(st);
        } else {
            if (wasSprinting && !isSprinting) {
                st.timeSinceSprintEnd = 0;
            }
            if (st.timeSinceSprintEnd < RECOVERY_DELAY) {
                st.timeSinceSprintEnd += dt;
            } else {
                st.current = Math.min(st.max, st.current + STAMINA_RECOVERY_RATE * dt);
                if (st.current >= st.minToSprint) {
                    st.isExhausted = false;
                }
                pushStaminaUI(st);
            }
        }
    }

    private static void pushStaminaUI(Stamina st) {
        GameUI.getInstance().setStamina(st.max > 0 ? st.current / st.max : 0, st.isExhausted);
    }

    /**
     * 移植自 Player.move：X/Y 轴分离的空气墙碰撞 + 边界限制 + 朝向更新。
     */
    private static void movePlayer(Transform t, Sprite spr, double deltaX, double deltaY, WorldMap worldMap) {
        double x = t.x;
        double y = t.y;
        double targetX = x + deltaX;
        double targetY = y + deltaY;

        double tryX = targetX;
        if (worldMap.isBlockedByAirWall(tryX, y, t.width, t.height)) {
            tryX = x;
        }

        double tryY = targetY;
        if (worldMap.isBlockedByAirWall(tryX, tryY, t.width, t.height)) {
            tryY = y;
        }

        double newX = tryX;
        double newY = tryY;

        if (worldMap.isPlayerOutOfBounds(newX, newY, t.width, t.height)) {
            double[] clampedPos = worldMap.clampPlayerPosition(newX, newY, t.width, t.height);
            newX = clampedPos[0];
            newY = clampedPos[1];
        }

        double actualDeltaX = newX - x;
        double actualDeltaY = newY - y;

        if (Math.abs(actualDeltaX) > Math.abs(actualDeltaY)) {
            if (actualDeltaX > 0.01) {
                spr.facing = Facing.RIGHT;
                spr.lastHorizontalFacing = Facing.RIGHT;
            } else if (actualDeltaX < -0.01) {
                spr.facing = Facing.LEFT;
                spr.lastHorizontalFacing = Facing.LEFT;
            }
        } else {
            if (actualDeltaY > 0.01) {
                spr.facing = Facing.DOWN;
            } else if (actualDeltaY < -0.01) {
                spr.facing = Facing.UP;
            }
        }

        if (actualDeltaX > 0.01) {
            spr.lastHorizontalFacing = Facing.RIGHT;
        } else if (actualDeltaX < -0.01) {
            spr.lastHorizontalFacing = Facing.LEFT;
        }

        if (Math.abs(actualDeltaX) > 0.01) {
            spr.displayFacing = spr.lastHorizontalFacing;
        } else if (Math.abs(actualDeltaY) > 0.01) {
            spr.displayFacing = spr.facing;
        }

        t.x = newX;
        t.y = newY;
    }
}
