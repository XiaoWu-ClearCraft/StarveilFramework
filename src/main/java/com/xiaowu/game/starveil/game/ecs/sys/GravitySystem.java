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
 *
 * <h2>手感三件套</h2>
 * <ul>
 *   <li><b>土狼时间</b>（{@link Gravity#coyoteTime}）：刚走出平台边缘的一小段时间里
 *       仍然允许起跳，避免「明明按了却没跳」；</li>
 *   <li><b>跳跃缓冲</b>（{@link Gravity#jumpBuffer}）：落地前提前按下的跳跃键会被记住，
 *       落地那一帧立刻起跳；</li>
 *   <li><b>单向平台</b>：只在下落方向接住实体，按「下」还能穿下去。
 *       判据的几何部分在 {@link com.xiaowu.game.starveil.game.world.AirWall} 上。</li>
 * </ul>
 * 计时器统一在 {@link Gravity#tickTimers} 里推进，判定则在 {@link #step} 开头，
 * 所以「按下的那一瞬间」与「能不能跳」是全系统唯一的一份实现。
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
        boolean vertical = isVertical(ux, uy);

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
            step(g, t, map, ux, uy, deltaTime, vertical);
        }
    }

    /**
     * 重力方向是否竖直向下（0°）。
     *
     * <p>只有这种情形下「水平台面」才有意义，单向平台也才成立；
     * 侧向重力下单向平台退化成普通空气墙，见 {@link #blockedFalling}。
     */
    private static boolean isVertical(double ux, double uy) {
        return Math.abs(ux) < 1e-9 && uy > 0;
    }

    /**
     * 推进一个实体一帧。
     *
     * <p>速度是<b>带符号的</b>标量：正 = 沿重力方向（下落），负 = 逆重力方向（起跳上升）。
     * 两种情况都只在被挡住时停下，所以「撞天花板」和「落地」是同一套逻辑。
     */
    private static void step(Gravity g, Transform t, WorldMap map,
                             double ux, double uy, double deltaTime, boolean vertical) {
        // 0) 计时器先行：土狼时间、跳跃缓冲、向下穿越窗口都靠它们计量。
        //    必须在判定跳跃之前推进 —— 判据要读的是「到这一刻为止过了多久」。
        g.tickTimers(deltaTime);

        // 1) 起跳：缓冲里有一次按下 + 现在跳得起来（落地或还在土狼时间内）
        if (consumeJump(g)) {
            g.speed = -g.jumpSpeed;
        }

        boolean rising = g.speed < 0;

        // 2) 这一帧沿重力方向要走多远（下落为正值，上升为负值）
        double distance;
        if (rising) {
            distance = g.speed * deltaTime;
            // 上升阶段用同一个加速度减速；越过 0 就自然转为下落
            g.speed = Math.min(0, g.speed + g.acceleration * deltaTime);
        } else {
            // 下落判据需要「开始下落前的脚底高度」：它是这一整步的常量，
            // 用来区分「从上面落到台面上」与「从下面穿上来」
            double startFeetY = t.y + t.height;
            boolean passOneWay = g.isDroppingThrough();
            FallStep fs = computeFall(g.speed, deltaTime, g.acceleration, g.maxFallSpeed,
                    d -> blockedFalling(map, vertical, passOneWay, startFeetY,
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
        if (rising && blockedRising(map, vertical, nx, ny, t.width, t.height)) {
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

    /**
     * 这一帧要不要起跳 —— 手感三件套里「跳跃缓冲 + 土狼时间」的唯一实现。
     *
     * <p>抽出来是因为它是纯粹的「状态 → 决定」，不碰地图也不碰 JavaFX，
     * 可以脱离整张地图直接测；留在 {@link #step} 里就只能靠跑游戏来验证。
     *
     * <p>无论跳没跳成，待处理的起跳请求都会被消费掉 —— 一次按键只对应一次机会。
     */
    static boolean consumeJump(Gravity g) {
        boolean wantsJump = g.jumpQueued || g.timeSinceJumpPress <= g.jumpBuffer;
        g.jumpQueued = false;
        if (wantsJump && g.canJump()) {
            // 缓冲与土狼窗口一起作废，否则起跳后的头几帧会再放行一次（二段跳）
            g.consumeJumpWindow();
            return true;
        }
        return false;
    }

    /**
     * 下落是否被挡住（含单向平台的完整语义）。
     *
     * <p>非竖直重力下「水平台面」没有意义，单向平台退回成普通空气墙 ——
     * 漏过去比莫名其妙挡住更糟（玩家会直接掉出地图）。
     */
    private static boolean blockedFalling(WorldMap map, boolean vertical, boolean passOneWay,
                                          double startFeetY,
                                          double x, double y, double w, double h) {
        if (!vertical) {
            return map.isBlockedByAirWall(x, y, w, h);
        }
        return map.isBlockedFalling(startFeetY, x, y, w, h, passOneWay);
    }

    /** 上升/横向是否被挡住：竖直重力下单向平台不挡（能从下方穿过）。 */
    private static boolean blockedRising(WorldMap map, boolean vertical,
                                         double x, double y, double w, double h) {
        if (!vertical) {
            return map.isBlockedByAirWall(x, y, w, h);
        }
        return map.isBlockedSideways(x, y, w, h);
    }

    /** 一次下落推进的结果。 */
    record FallStep(double speed, double distance, boolean landed) {
    }

    /**
     * 下落时每小步前进的像素数。
     *
     * <p>取值是「精度 vs 判定次数」的折中：越小越贴合表面，但每帧的碰撞判定次数
     * 越多（终端速度下一帧 20px，取 2px 就是 10 次判定 —— 一个实体一帧十次很便宜）。
     * 2px 在 60FPS 下是肉眼看不出的误差，足以消掉「悬停再抖」的观感。
     */
    private static final double MAX_SUB_STEP = 2;

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
     * <h2>为什么要子步进</h2>
     * 终端速度下（{@code 1200 × 1/60 = 20px}）一帧要走 20 像素。
     * 如果只判定「整帧走完的位置」，被挡住时只能原地不动 ——
     * 而那时角色离地面还差最多 20px，于是看起来像<b>悬在地面上方</b>，
     * 之后几帧再一点点挪下来（连带 {@code grounded} 反复横跳）。
     * 拆成不超过 {@link #MAX_SUB_STEP} 像素的小步前进，就会停在紧贴表面的位置。
     *
     * @param blockedAtDistance 给定沿重力方向前进的距离，判断该处是否被阻挡
     */
    static FallStep computeFall(double speed, double deltaTime,
                                double acceleration, double maxFallSpeed,
                                DoublePredicate blockedAtDistance) {
        // 限制在终端速度内，否则一帧位移可能直接跳过整片墙体
        double v = Math.min(speed + acceleration * deltaTime, maxFallSpeed);
        double distance = v * deltaTime;

        double moved = 0;
        while (moved < distance) {
            double next = Math.min(distance, moved + MAX_SUB_STEP);
            if (blockedAtDistance.test(next)) {
                // 停在最后一个没被挡住的位置：离表面最多 MAX_SUB_STEP，肉眼看不出来
                return new FallStep(0, moved, true);
            }
            moved = next;
        }
        return new FallStep(v, distance, false);
    }
}
