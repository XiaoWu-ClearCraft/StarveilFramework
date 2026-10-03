package com.xiaowu.game.starveil.game.ecs.sys;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重力核心计算测试。
 *
 * <p>{@code computeFall} 与 {@code directionVector} 被刻意抽成不依赖 JavaFX
 * 与地图的纯函数，否则「加速 / 终端速度 / 落地 / 角度约定」这些最容易写错的点
 * 只能靠跑游戏目测。
 */
class GravitySystemTest {

    private static final double ACCEL = 1800;
    private static final double MAX_FALL = 1200;
    private static final double DT = 1.0 / 60;

    // ==================== 方向约定 ====================

    @Test
    void zeroDegreesPointsDown() {
        double[] d = GravitySystem.directionVector(0);
        assertEquals(0, d[0], 1e-9);
        assertEquals(1, d[1], 1e-9, "0° 必须是向下（+y）");
    }

    @Test
    void anglesAreClockwiseOnScreenCoordinates() {
        double[] right = GravitySystem.directionVector(90);
        assertEquals(1, right[0], 1e-9, "90° 应指向右（+x）");
        assertEquals(0, right[1], 1e-9);

        double[] up = GravitySystem.directionVector(180);
        assertEquals(0, up[0], 1e-9);
        assertEquals(-1, up[1], 1e-9, "180° 应指向上（-y）");

        double[] left = GravitySystem.directionVector(270);
        assertEquals(-1, left[0], 1e-9, "270° 应指向左（-x）");
        assertEquals(0, left[1], 1e-9);
    }

    @Test
    void directionVectorIsNormalized() {
        for (double angle : new double[]{0, 30, 45, 90, 137, 180, 270, 359}) {
            double[] d = GravitySystem.directionVector(angle);
            assertEquals(1.0, Math.hypot(d[0], d[1]), 1e-9,
                    "方向必须是单位向量，否则速度与实际位移会脱钩");
        }
    }

    @Test
    void negativeAndOverflowAnglesWrapAround() {
        double[] neg = GravitySystem.directionVector(-90);
        assertEquals(-1, neg[0], 1e-9, "-90° 等价于 270°");

        double[] over = GravitySystem.directionVector(450);
        assertEquals(1, over[0], 1e-9, "450° 等价于 90°");
    }

    // ==================== 下落推进 ====================

    @Test
    void firstStepAcceleratesFromRest() {
        GravitySystem.FallStep s = GravitySystem.computeFall(
                0, DT, ACCEL, MAX_FALL, d -> false);

        double expectedSpeed = ACCEL * DT;
        assertEquals(expectedSpeed, s.speed(), 1e-9, "初速度为零时应按加速度增长");
        assertEquals(expectedSpeed * DT, s.distance(), 1e-9);
        assertFalse(s.landed());
    }

    @Test
    void speedKeepsGrowingUntilTerminal() {
        double speed = 0;
        for (int i = 0; i < 200; i++) {
            speed = GravitySystem.computeFall(speed, DT, ACCEL, MAX_FALL, d -> false).speed();
        }
        assertEquals(MAX_FALL, speed, 1e-9, "必须收敛到终端速度，否则会一帧穿过墙体");
    }

    @Test
    void reachingTerminalSpeedTakesExpectedTime() {
        double speed = 0;
        int steps = 0;
        while (speed < MAX_FALL && steps < 10_000) {
            speed = GravitySystem.computeFall(speed, DT, ACCEL, MAX_FALL, d -> false).speed();
            steps++;
        }
        // v = a*t → t = 1200/1800 ≈ 0.667s ≈ 40 帧
        assertEquals(40, steps, 1, "终端速度应在约 40 帧后达到");
    }

    @Test
    void alreadyAtTerminalSpeedStaysThere() {
        GravitySystem.FallStep s = GravitySystem.computeFall(
                MAX_FALL, DT, ACCEL, MAX_FALL, d -> false);
        assertEquals(MAX_FALL, s.speed(), 1e-9);
        assertEquals(MAX_FALL * DT, s.distance(), 1e-9);
    }

    // ==================== 落地 ====================

    @Test
    void blockedStepLandsInPlace() {
        GravitySystem.FallStep s = GravitySystem.computeFall(
                500, DT, ACCEL, MAX_FALL, distance -> distance > 8);

        assertEquals(0, s.distance(), 1e-9, "撞到阻挡时不应产生位移");
        assertEquals(0, s.speed(), 1e-9, "落地后速度必须清零，否则会持续累积");
        assertTrue(s.landed());
    }

    @Test
    void fallingEntityIsNotLanded() {
        assertFalse(GravitySystem.computeFall(
                10, DT, ACCEL, MAX_FALL, d -> false).landed(), "还在空中时不应标记落地");
    }

    @Test
    void blockedCheckUsesThisFrameDistanceNotCurrentSpeed() {
        final double[] probed = {Double.NaN};
        GravitySystem.computeFall(600, DT, ACCEL, MAX_FALL, d -> {
            probed[0] = d;
            return false;
        });

        assertTrue(probed[0] > 0, "碰撞判定必须针对本帧将要前进的距离");
        assertEquals(Math.min(600 + ACCEL * DT, MAX_FALL) * DT, probed[0], 1e-9);
    }

    @Test
    void zeroDeltaTimeDoesNotMoveButStillAccelerates() {
        GravitySystem.FallStep s = GravitySystem.computeFall(100, 0, ACCEL, MAX_FALL, d -> false);
        assertEquals(0, s.distance(), 1e-9, "dt=0 时不应产生位移");
        assertEquals(100, s.speed(), 1e-9);
    }
}
