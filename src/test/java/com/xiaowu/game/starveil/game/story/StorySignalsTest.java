package com.xiaowu.game.starveil.game.story;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剧情信号测试。
 *
 * <p>这些语义直接决定了「剧情等教程/等事件」会不会卡死或漏触发：
 * 信号是一次性闩锁——先到先等、后到立即通过、超时不会丢状态。
 */
class StorySignalsTest {

    @AfterEach
    void cleanup() {
        StorySignals.resetAll();
    }

    @Test
    void awaitReturnsImmediatelyWhenSignalAlreadyFired() {
        StorySignals.signal("already");
        long start = System.nanoTime();
        StorySignals.await("already");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 200, "信号已触发时 await 应立即返回，实际耗时 " + elapsedMs + "ms");
    }

    @Test
    void signalWakesWaitingThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean returned = new AtomicBoolean(false);

        Thread waiter = new Thread(() -> {
            started.countDown();
            StorySignals.await("go");
            returned.set(true);
        }, "test-waiter");
        waiter.setDaemon(true);
        waiter.start();

        assertTrue(started.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        assertFalse(returned.get(), "信号未触发前 await 应保持阻塞");

        StorySignals.signal("go");
        waiter.join(2000);
        assertTrue(returned.get(), "信号触发后 await 应立即返回");
    }

    @Test
    void awaitTimesOutWithoutLosingPendingState() {
        assertFalse(StorySignals.await("never", 100), "超时应返回 false");
        assertFalse(StorySignals.hasFired("never"));

        // 迟到的信号依然有效（闩锁语义）
        StorySignals.signal("never");
        assertTrue(StorySignals.await("never", 100));
    }

    @Test
    void resetClearsFiredState() {
        StorySignals.signal("again");
        assertTrue(StorySignals.hasFired("again"));
        StorySignals.reset("again");
        assertFalse(StorySignals.hasFired("again"));
        assertFalse(StorySignals.await("again", 100));
    }

    @Test
    void resetAllClearsEverything() {
        StorySignals.signal("a");
        StorySignals.signal("b");
        StorySignals.resetAll();
        assertFalse(StorySignals.hasFired("a"));
        assertFalse(StorySignals.hasFired("b"));
        assertTrue(StorySignals.pendingSignals().isEmpty());
    }

    @Test
    void tutorialSignalConstantIsWired() {
        // TutorialManager 触发教程完成时会 signal 这个名字
        StorySignals.signal(StorySignals.TUTORIAL_COMPLETED);
        assertTrue(StorySignals.hasFired(StorySignals.TUTORIAL_COMPLETED));
    }
}
