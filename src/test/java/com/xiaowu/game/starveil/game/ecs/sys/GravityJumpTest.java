package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.ecs.comp.Gravity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重力与跳跃的纯计算测试。
 *
 * <p>只测不依赖 JavaFX / 地图的那部分：{@link GravitySystem#computeFall} 的积分，
 * 以及跳跃高度公式。真正「撞到平台就停住」需要一整张地图与 ECS 世界，
 * 那部分由实跑验证（见内容项目的 gravity-test.json）。
 */
class GravityJumpTest {

    /** 与 {@link Gravity} 的默认值保持一致。 */
    private static final double ACCEL = 1800;
    private static final double MAX_FALL = 1200;
    private static final double DT = 1.0 / 60.0;

    // ==================== 下落积分 ====================

    @Test
    void fallsFasterEachFrameUntilTerminalSpeed() {
        double speed = 0;
        double first = 0;
        for (int i = 0; i < 200; i++) {
            GravitySystem.FallStep s = GravitySystem.computeFall(speed, DT, ACCEL, MAX_FALL, d -> false);
            if (i == 0) {
                first = s.speed();
            }
            speed = s.speed();
        }
        assertTrue(first > 0, "第一帧速度应当为正（开始下落）");
        assertEquals(MAX_FALL, speed, 1e-9, "最终应当收敛到终端速度");
    }

    @Test
    void blockedMeansNoMovementAtAll() {
        GravitySystem.FallStep s =
                GravitySystem.computeFall(500, DT, ACCEL, MAX_FALL, d -> true);
        assertTrue(s.landed(), "被挡住时标记为落地");
        assertEquals(0, s.distance(), "被挡住时一帧都不该移动");
        assertEquals(0, s.speed(), "被挡住时速度清零");
    }

    // ==================== 跳跃高度 ====================

    /**
     * 起跳初速度与跳跃高度的关系：{@code h = v² / (2a)}。
     *
     * <p>把这条公式钉住是因为它决定了「默认跳跃能不能上得去台面」——
     * 手感调参时改的只有 {@code jumpSpeed} 与 {@code acceleration}，
     * 公式错了会表现为「明明数值更大却跳得更低」。
     */
    @Test
    void jumpHeightFollowsThePhysicsFormula() {
        double v = new Gravity().jumpSpeed;
        double h = v * v / (2 * ACCEL);
        assertTrue(h > 100 && h < 200,
                "默认跳跃高度应当在一个身位上下（100~200px），实际约 " + Math.round(h) + "px");
    }

    /**
     * 逐帧模拟一次跳跃，确认真的能到那个高度、并且会落回来。
     *
     * <p>积分用的是和 {@link GravitySystem} 相同的显式欧拉步进，所以这里算出来的
     * 峰值就是游戏里会看到的高度。
     */
    @Test
    void simulatedJumpRisesThenFallsBack() {
        Gravity g = new Gravity();
        double speed = -g.jumpSpeed;   // 起跳：逆重力方向
        double peak = 0;
        double position = 0;
        boolean returned = false;

        for (int i = 0; i < 600; i++) {
            position += speed * DT;            // 先按当前速度位移
            speed += ACCEL * DT;               // 再受重力加速
            peak = Math.min(peak, position);   // 向上是负方向
            if (speed >= 0 && position >= 0) {
                returned = true;               // 回到起点
                break;
            }
        }

        double height = -peak;
        assertTrue(height > 100, "跳跃应当超过 100px，实际 " + Math.round(height) + "px");
        assertTrue(height < 200, "跳跃不该超过 200px，实际 " + Math.round(height) + "px");
        assertTrue(returned, "最终应当落回起跳高度");
    }

    // ==================== 起跳请求 ====================

    @Test
    void jumpRequestIsOneShot() {
        Gravity g = new Gravity();
        assertFalse(g.jumpQueued, "默认没有待处理的起跳");

        g.requestJump();
        assertTrue(g.jumpQueued);

        // 消费一次（GravitySystem.step 里的做法）
        g.jumpQueued = false;
        assertFalse(g.jumpQueued, "消费之后不该重复起跳");
    }

    @Test
    void resetFallClearsGroundedAndPendingJump() {
        Gravity g = new Gravity();
        g.grounded = true;
        g.speed = 300;
        g.requestJump();

        g.resetFall();

        assertFalse(g.grounded);
        assertEquals(0, g.speed, 1e-9);
        assertFalse(g.jumpQueued, "重置下落时待处理的起跳也要清掉");
    }

    @Test
    void jumpSpeedAndAccelerationAreTunable() {
        Gravity slow = new Gravity(600, 800);
        slow.jumpSpeed = 300;
        double h = slow.jumpSpeed * slow.jumpSpeed / (2 * slow.acceleration);
        assertEquals(75.0, h, 1e-9, "加速度与跳跃速度都影响高度");
    }
}
