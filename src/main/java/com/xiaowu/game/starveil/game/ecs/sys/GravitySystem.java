package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.ecs.EcsSystem;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Gravity;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;

import java.util.function.DoublePredicate;

/**
 * 重力系统 —— 让带 {@link Gravity} 组件的实体沿重力方向自动下落。
 *
 * <p>只在 {@code GameplayMode.GRAVITY} 下工作；{@code NORMAL} 下整个系统空转，
 * 因此可以无条件注册，不必在切模式时增删系统。
 *
 * <p>碰撞复用既有的空气墙与边界逻辑（与玩家移动同一套判定），
 * 所以不需要新的地形数据 —— 这正是「把 y 当作 z」的最小实现。
 *
 * <p>重力方向由 {@link World#gravityAngleDegrees()} 提供，可以是任意角度
 * （0° 向下，顺时针为正）。
 */
public final class GravitySystem implements EcsSystem {

    @Override
    public void update(World world, double deltaTime) {
        if (world == null || !world.gameplayMode().hasGravity()) {
            return;
        }
        WorldMap map = world.getResource(WorldMap.class);
        if (map == null) {
            return;
        }

        double angle = world.gravityAngleDegrees();
        double[] dir = directionVector(angle);
        double ux = dir[0];
        double uy = dir[1];

        int[] entities = world.view(Transform.class);
        for (int e : entities) {
            Gravity g = world.get(e, Gravity.class);
            if (g == null || !g.enabled) {
                continue;
            }
            Transform t = world.get(e, Transform.class);
            if (t == null) {
                continue;
            }
            step(g, t, map, ux, uy, deltaTime);
        }
    }

    /**
     * 推进一个实体一帧。
     *
     * <p>速度是<b>带符号的</b>标量：正 = 沿重力方向（下落），负 = 逆重力方向（起跳上升）。
     * 两种情况都只在被挡住时停下，所以「撞天花板」和「落地」是同一套逻辑。
     */
    private static void step(Gravity g, Transform t, WorldMap map,
                             double ux, double uy, double deltaTime) {
        // 1) 起跳：只有落地时才算数（空中按跳跃键无效，不能连跳）
        if (g.jumpQueued) {
            g.jumpQueued = false;
            if (g.grounded) {
                g.speed = -g.jumpSpeed;
            }
        }

        boolean rising = g.speed < 0;

        // 2) 这一帧沿重力方向要走多远（下落为正值，上升为负值）
        double distance;
        if (rising) {
            distance = g.speed * deltaTime;
            // 上升阶段用同一个加速度减速；越过 0 就自然转为下落
            g.speed = Math.min(0, g.speed + g.acceleration * deltaTime);
        } else {
            FallStep fs = computeFall(g.speed, deltaTime, g.acceleration, g.maxFallSpeed,
                    d -> map.isBlockedByAirWall(
                            t.x + ux * d, t.y + uy * d, t.width, t.height));
            if (fs.landed()) {
                // 撞到阻挡：原地停住。不贴到墙沿是因为空气墙的精确边界由形状几何决定，
                // 玩家移动系统同样只做「原地不动」处理，两边保持一致。
                g.speed = 0;
                g.grounded = true;
                return;
            }
            g.speed = fs.speed();
            distance = fs.distance();
        }

        double nx = t.x + ux * distance;
        double ny = t.y + uy * distance;

        // 3) 上升撞到天花板：停住并开始下落，不能继续往上顶
        if (rising && map.isBlockedByAirWall(nx, ny, t.width, t.height)) {
            g.speed = 0;
            g.grounded = false;
            return;
        }

        if (map.isPlayerOutOfBounds(nx, ny, t.width, t.height)) {
            double[] clamped = map.clampPlayerPosition(nx, ny, t.width, t.height);
            t.x = clamped[0];
            t.y = clamped[1];
            g.speed = 0;
            // 被地图边界接住也算落地：不然在底边贴着墙会一直「下落」而无法起跳
            g.grounded = !rising;
            return;
        }

        t.x = nx;
        t.y = ny;
        // 落地 = 「沿重力方向的速度已经归零」；还在上升或下落中都不算
        g.grounded = !rising && g.speed == 0;
    }

    /** 一次下落推进的结果。 */
    record FallStep(double speed, double distance, boolean landed) {
    }

    /**
     * 重力方向的单位向量。
     *
     * <p>角度约定：{@code 0° = 向下（+y）}，顺时针为正 ——
     * 90° = 向右（+x）、180° = 向上（-y）、270° = 向左（-x）。
     * 屏幕坐标 y 轴向下，所以 (sin, cos) 而非 (cos, sin)。
     *
     * @return {@code [ux, uy]}
     */
    static double[] directionVector(double angleDegrees) {
        double rad = Math.toRadians(angleDegrees);
        return new double[]{Math.sin(rad), Math.cos(rad)};
    }

    /**
     * 下落一步的纯计算 —— 不依赖 JavaFX 或地图，便于单元测试。
     *
     * <p>以「沿重力方向前进的距离」为单位，因此天然支持任意方向：
     * 调用方把距离换算成实际坐标再判定碰撞即可。
     *
     * @param blockedAtDistance 给定沿重力方向前进的距离，判断该处是否被阻挡
     */
    static FallStep computeFall(double speed, double deltaTime,
                                double acceleration, double maxFallSpeed,
                                DoublePredicate blockedAtDistance) {
        // 限制在终端速度内，否则一帧位移可能直接跳过整片墙体
        double v = Math.min(speed + acceleration * deltaTime, maxFallSpeed);
        double distance = v * deltaTime;

        if (blockedAtDistance.test(distance)) {
            return new FallStep(0, 0, true);
        }
        return new FallStep(v, distance, false);
    }
}
