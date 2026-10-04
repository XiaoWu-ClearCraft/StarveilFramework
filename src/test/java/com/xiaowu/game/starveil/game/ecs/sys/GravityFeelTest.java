package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.ecs.comp.Gravity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台手感测试：土狼时间、跳跃缓冲、向下穿越。
 *
 * <p>三者的共同点是「时序」—— 单独看某一帧都看不出问题，错的是<b>帧与帧之间</b>
 * 的边界（提前一帧按、晚一帧按、起跳后的下一帧）。所以这里按帧模拟，
 * 而不是只断言几个布尔值。
 *
 * <p>判定逻辑本身在 {@link GravitySystem#consumeJump}（纯状态机，不需要地图），
 * 计时器在 {@link Gravity#tickTimers}，两者合起来就是游戏里跑的那条路径。
 */
class GravityFeelTest {

    private static final double DT = 1.0 / 60;

    /** 模拟 n 帧过去。 */
    private static void frames(Gravity g, int n) {
        for (int i = 0; i < n; i++) {
            g.tickTimers(DT);
        }
    }

    // ==================== 土狼时间 ====================

    @Test
    void coyoteTimeAllowsJumpRightAfterLeavingGround() {
        Gravity g = new Gravity();
        g.grounded = true;
        frames(g, 1);
        assertTrue(g.canJump(), "踩在地上当然能跳");

        // 走出平台边缘：不再接地
        g.grounded = false;
        int withinWindow = (int) Math.floor(g.coyoteTime / DT);
        frames(g, withinWindow);
        assertTrue(g.canJump(),
                "离开地面 " + withinWindow + " 帧（" + (withinWindow * DT) + "s）内仍然算「刚离开地面」");
    }

    @Test
    void coyoteTimeExpires() {
        Gravity g = new Gravity();
        g.grounded = true;
        frames(g, 1);
        g.grounded = false;
        frames(g, (int) Math.ceil(g.coyoteTime / DT) + 2);
        assertFalse(g.canJump(), "超出土狼时间就真的在空中了");
    }

    @Test
    void freshGravityCannotJumpInMidAir() {
        // 初始 timeSinceGrounded 是无穷大 —— 刚出生的实体不该在天上白跳一次
        Gravity g = new Gravity();
        assertFalse(g.canJump());
        g.grounded = false;
        frames(g, 1);
        assertFalse(g.canJump());
    }

    // ==================== 跳跃缓冲 ====================

    @Test
    void jumpBufferedBeforeLandingFiresOnLanding() {
        Gravity g = new Gravity();
        g.grounded = false;
        frames(g, 10);
        g.pressJumpKey();          // 还在空中就按了跳
        frames(g, 1);
        assertFalse(GravitySystem.consumeJump(g), "空中不该起跳");

        g.grounded = true;         // 这一帧落地
        frames(g, 1);
        assertTrue(GravitySystem.consumeJump(g),
                "落地前的输入要被记住，落地那一帧立刻生效 —— 不然玩家会觉得在吞键");
    }

    @Test
    void jumpBufferExpires() {
        Gravity g = new Gravity();
        g.grounded = false;
        g.pressJumpKey();
        frames(g, (int) Math.ceil(g.jumpBuffer / DT) + 2);
        g.grounded = true;
        frames(g, 1);
        assertFalse(GravitySystem.consumeJump(g), "按得太早就不该再算数");
    }

    // ==================== 二者组合：不能二段跳 ====================

    @Test
    void cannotDoubleJumpUsingCoyoteWindow() {
        Gravity g = new Gravity();
        g.grounded = true;
        frames(g, 1);
        g.pressJumpKey();
        frames(g, 1);
        assertTrue(GravitySystem.consumeJump(g), "第一次起跳应当成功");

        // 起跳后仍在空中，且土狼窗口还没走完 —— 若不清楚窗口就会再跳一次
        g.grounded = false;
        frames(g, 1);
        assertFalse(GravitySystem.consumeJump(g),
                "起跳时必须把土狼窗口一起作废，否则同一段窗口里能跳出二段跳");
    }

    /**
     * 空中被拒绝的那一次输入，只要还在缓冲期内就仍然有效。
     *
     * <p>这不是漏判，而正是缓冲的意义：玩家看着快落地了就提前按跳，
     * 按下的那一瞬间还在空中（所以当帧起跳失败），但紧接着落地时应当立刻跳起来。
     */
    @Test
    void rejectedMidAirPressStillFiresWhenLandingWithinBuffer() {
        Gravity g = new Gravity();
        g.grounded = false;
        frames(g, 100);            // 离地很久了，土狼时间早就过了
        g.pressJumpKey();
        frames(g, 1);
        assertFalse(GravitySystem.consumeJump(g), "还在空中，当帧不该起跳");

        g.grounded = true;         // 下一帧落地
        frames(g, 1);
        assertTrue(GravitySystem.consumeJump(g),
                "还在缓冲期内的提前输入应当在落地时补上");
    }

    // ==================== 剧情/脚本接口 ====================

    @Test
    void requestJumpBypassesBuffer() {
        Gravity g = new Gravity();
        g.grounded = true;
        frames(g, 1);
        g.requestJump();           // 等价于剧情脚本直接让角色跳
        assertTrue(GravitySystem.consumeJump(g));
    }

    @Test
    void requestJumpInMidAirIsDropped() {
        Gravity g = new Gravity();
        g.grounded = false;
        frames(g, 100);
        g.requestJump();
        assertFalse(GravitySystem.consumeJump(g), "空中请求起跳一样要被丢掉");
        assertFalse(g.jumpQueued, "请求无论成没成都要被消费掉");
    }

    // ==================== 向下穿越单向平台 ====================

    @Test
    void dropThroughWindowOpensAndClosesByItself() {
        Gravity g = new Gravity();
        assertFalse(g.isDroppingThrough(), "默认不在穿越窗口里");

        g.startDropThrough();
        assertTrue(g.isDroppingThrough());
        frames(g, 1);
        assertTrue(g.isDroppingThrough(), "窗口刚开的一帧当然还在");

        frames(g, 30);             // 约 0.5s，远超 0.2s 的窗口
        assertFalse(g.isDroppingThrough(), "窗口必须自己结束，否则会一直往下掉");
    }

    @Test
    void resetFallClearsTimers() {
        Gravity g = new Gravity();
        g.grounded = true;
        frames(g, 1);
        g.speed = -300;
        g.requestJump();
        g.startDropThrough();

        g.resetFall();

        assertFalse(g.grounded);
        assertFalse(g.jumpQueued, "被击飞后不该还留着起跳请求");
        assertFalse(g.isDroppingThrough());
        assertFalse(g.canJump(), "被击飞后不该还能用土狼时间起跳");
    }
}
