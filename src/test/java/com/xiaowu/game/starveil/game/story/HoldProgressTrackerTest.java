package com.xiaowu.game.starveil.game.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「按住足够久才算通过」进度记录器测试。
 *
 * <p>重点验证：碰一下键<b>不能</b>过关（这是旧版教程的问题），
 * 必须持续累加到阈值；以及达标后不会溢出、松开不计时、全部槽位都要完成。
 */
class HoldProgressTrackerTest {

    // ==================== 核心：按住才有进度 ====================

    @Test
    void singleTapIsNotEnough() {
        // 旧版教程「按一下就过」的行为：这里必须过不了
        HoldProgressTracker t = new HoldProgressTracker(4, 1.5);
        t.tick(0, true, 1.0 / 60);   // 只按住了一帧（约 16.7ms）
        assertFalse(t.isDone(0), "点一下就通过就失去教学意义了");
        assertFalse(t.allDone());
    }

    @Test
    void accumulatesAcrossFrames() {
        HoldProgressTracker t = new HoldProgressTracker(1, 1.0);
        t.tick(0, true, 0.25);
        assertEquals(0.25, t.heldSeconds(0), 1e-9);
        t.tick(0, true, 0.25);
        assertEquals(0.5, t.heldSeconds(0), 1e-9);
        assertFalse(t.isDone(0), "还没到 1.0 秒不该达标");
        t.tick(0, true, 0.25);
        assertFalse(t.isDone(0), "0.75 秒仍不该达标");
        t.tick(0, true, 0.25);
        assertTrue(t.isDone(0), "累计到 1.0 秒应当达标");
    }

    @Test
    void releasesDoNotAccumulate() {
        HoldProgressTracker t = new HoldProgressTracker(1, 1.0);
        t.tick(0, false, 0.9);
        assertEquals(0.0, t.heldSeconds(0), 1e-9, "没按住就不该累计");
        t.tick(0, true, 0.9);
        assertFalse(t.isDone(0));
    }

    @Test
    void nonPositiveDeltaTimeIsIgnored() {
        HoldProgressTracker t = new HoldProgressTracker(1, 1.0);
        t.tick(0, true, 0);
        t.tick(0, true, -0.5);
        assertEquals(0.0, t.heldSeconds(0), 1e-9);
    }

    // ==================== 边界 ====================

    @Test
    void clampsAtRequiredSeconds() {
        HoldProgressTracker t = new HoldProgressTracker(1, 1.0);
        t.tick(0, true, 5.0);   // 一帧就远超阈值
        assertEquals(1.0, t.heldSeconds(0), 1e-9, "不应超过阈值");
        assertEquals(1.0, t.progress(0), 1e-9, "进度不应超过 100%");
    }

    @Test
    void doneSlotStopsAccumulating() {
        HoldProgressTracker t = new HoldProgressTracker(1, 1.0);
        t.tick(0, true, 1.0);
        t.tick(0, true, 1.0);
        assertEquals(1.0, t.heldSeconds(0), 1e-9);
    }

    @Test
    void outOfBoundsSlotIsIgnoredInsteadOfThrowing() {
        HoldProgressTracker t = new HoldProgressTracker(2, 1.0);
        t.tick(-1, true, 5.0);
        t.tick(9, true, 5.0);
        assertEquals(0, t.doneCount());
        assertFalse(t.allDone());
        assertEquals(0.0, t.heldSeconds(99), 1e-9);
        assertEquals(0.0, t.progress(-3), 1e-9);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new HoldProgressTracker(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new HoldProgressTracker(-2, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new HoldProgressTracker(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new HoldProgressTracker(1, -1.5));
    }

    // ==================== 多槽位 ====================

    @Test
    void everySlotMustBeCompleted() {
        HoldProgressTracker t = new HoldProgressTracker(4, 0.5);
        for (int i = 0; i < 3; i++) {
            t.tick(i, true, 0.5);
        }
        assertEquals(3, t.doneCount());
        assertFalse(t.allDone(), "四个方向必须全部练过");

        t.tick(3, true, 0.5);
        assertEquals(4, t.doneCount());
        assertTrue(t.allDone());
    }

    @Test
    void progressIsPerSlot() {
        HoldProgressTracker t = new HoldProgressTracker(4, 1.0);
        t.tick(1, true, 0.5);
        assertEquals(0.0, t.progress(0), 1e-9);
        assertEquals(0.5, t.progress(1), 1e-9);
        assertEquals(0.0, t.progress(2), 1e-9);
    }

    @Test
    void resetClearsEverything() {
        HoldProgressTracker t = new HoldProgressTracker(4, 0.5);
        for (int i = 0; i < 4; i++) {
            t.tick(i, true, 0.5);
        }
        assertTrue(t.allDone());

        t.reset();
        assertFalse(t.allDone());
        assertEquals(0, t.doneCount());
        assertEquals(0.0, t.heldSeconds(0), 1e-9);
    }

    // ==================== 合计进度（单根总进度条用） ====================

    @Test
    void totalProgressCoversAllSlots() {
        HoldProgressTracker t = new HoldProgressTracker(4, 1.0);
        assertEquals(0.0, t.totalProgress(), 1e-9);

        // 练满一个方向只到 1/4 —— 不能靠猛按一个键刷满
        t.tick(0, true, 1.0);
        assertEquals(0.25, t.totalProgress(), 1e-9);

        t.tick(1, true, 1.0);
        assertEquals(0.5, t.totalProgress(), 1e-9);

        t.tick(2, true, 0.5);
        assertEquals(0.625, t.totalProgress(), 1e-9);
    }

    @Test
    void totalProgressReachesOneOnlyWhenAllDone() {
        HoldProgressTracker t = new HoldProgressTracker(4, 1.0);
        for (int i = 0; i < 3; i++) {
            t.tick(i, true, 1.0);
        }
        assertTrue(t.totalProgress() < 1.0, "还差一个方向不该满");
        assertEquals(0.75, t.totalProgress(), 1e-9);

        t.tick(3, true, 1.0);
        assertTrue(t.allDone());
        assertEquals(1.0, t.totalProgress(), 1e-9);
    }

    @Test
    void totalProgressNeverExceedsOne() {
        // 单格超长按住（时长被钳到阈值）也不该把总进度顶爆
        HoldProgressTracker t = new HoldProgressTracker(4, 1.0);
        for (int i = 0; i < 4; i++) {
            t.tick(i, true, 99.0);
        }
        assertEquals(1.0, t.totalProgress(), 1e-9);
        assertEquals(4.0, t.totalHeldSeconds(), 1e-9);
    }

    @Test
    void totalSecondsHelpersAreConsistent() {
        HoldProgressTracker t = new HoldProgressTracker(4, 1.5);
        assertEquals(6.0, t.totalRequiredSeconds(), 1e-9);
        t.tick(0, true, 0.5);
        t.tick(2, true, 0.25);
        assertEquals(0.75, t.totalHeldSeconds(), 1e-9);
        assertEquals(0.75 / 6.0, t.totalProgress(), 1e-9);
    }

    // ==================== 真实帧率下的行为 ====================

    @Test
    void sixtyFpsHoldCompletesInExpectedFrameCount() {
        double required = 1.5;
        double dt = 1.0 / 60;
        HoldProgressTracker t = new HoldProgressTracker(1, required);

        int frames = 0;
        while (!t.isDone(0) && frames < 300) {
            t.tick(0, true, dt);
            frames++;
        }

        assertTrue(t.isDone(0), "60fps 下按住 1.5 秒应当达标");
        // 1.5s / (1/60) = 90 帧；允许 1 帧的浮点误差余量
        assertTrue(frames >= 90, "不应提前达标，实际 " + frames + " 帧");
        assertTrue(frames <= 92, "不应明显滞后，实际 " + frames + " 帧");
    }

    @Test
    void lowFrameRateStillRequiresSameWallClockTime() {
        // 30fps：帧数减半，但实际秒数必须一致 —— 时长与帧率无关
        double dt = 1.0 / 30;
        HoldProgressTracker t = new HoldProgressTracker(1, 2.0);
        int frames = 0;
        while (!t.isDone(0) && frames < 300) {
            t.tick(0, true, dt);
            frames++;
        }
        assertTrue(t.isDone(0));
        assertTrue(frames >= 60 && frames <= 62, "2 秒 @30fps 约 60 帧，实际 " + frames);
    }
}
