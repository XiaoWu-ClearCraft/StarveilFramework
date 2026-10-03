package com.xiaowu.game.starveil.game.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 剧情信号总线。
 *
 * <p>解决「剧情要等某件事发生」这个最常见的编写需求：
 * 章节脚本在剧情线程上直接 {@code await("xxx")} 阻塞等待，
 * 其它任何地方（地图事件、教程、UI 回调）调用 {@code signal("xxx")} 唤醒它。
 *
 * <p>信号是 <b>一次性闩锁</b> 语义：一旦 signal 过，之后所有 await 都立即返回，
 * 不会被“错过”。需要复用时调用 {@link #reset(String)}。
 */
public final class StorySignals {

    /** 新手教程完成（由 {@code TutorialManager} 触发）。 */
    public static final String TUTORIAL_COMPLETED = "starveil:tutorial_completed";

    /** 世界地图加载完成（由 {@code EventCallbackManager} 触发）。 */
    public static final String WORLD_LOADED = "world.loaded";

    private static final Map<String, List<CountDownLatch>> WAITERS = new ConcurrentHashMap<>();
    private static final Set<String> FIRED = ConcurrentHashMap.newKeySet();

    private StorySignals() {}

    /**
     * 触发信号：唤醒所有等待者，并让信号保持「已触发」状态。
     */
    public static void signal(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        FIRED.add(name);
        List<CountDownLatch> waiters = WAITERS.remove(name);
        if (waiters == null) {
            Logger("DEBUG", "[StorySignals] 触发信号（当前无等待者）: " + name);
            return;
        }
        Logger("DEBUG", "[StorySignals] 触发信号: " + name + "（唤醒 " + waiters.size() + " 个等待者）");
        for (CountDownLatch latch : waiters) {
            latch.countDown();
        }
    }

    /**
     * 该信号是否已经触发过。
     */
    public static boolean hasFired(String name) {
        return name != null && FIRED.contains(name);
    }

    /**
     * 阻塞等待信号。若信号此前已触发过则立即返回。
     *
     * @throws StoryCancelledException 当前线程被中断（剧情被取消）
     */
    public static void await(String name) {
        await(name, 0L);
    }

    /**
     * 阻塞等待信号，超时返回 false。
     *
     * @param timeoutMillis 0 或负数表示一直等待
     * @return 是否等到信号
     */
    public static boolean await(String name, long timeoutMillis) {
        if (name == null || name.isEmpty()) {
            return true;
        }
        if (FIRED.contains(name)) {
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        WAITERS.computeIfAbsent(name, k -> new ArrayList<>()).add(latch);
        try {
            if (timeoutMillis > 0) {
                return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            latch.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoryCancelledException("等待信号被中断: " + name);
        } finally {
            List<CountDownLatch> waiters = WAITERS.get(name);
            if (waiters != null) {
                waiters.remove(latch);
                if (waiters.isEmpty()) {
                    WAITERS.remove(name, waiters);
                }
            }
        }
    }

    /**
     * 清除某个信号的「已触发」状态（等待者不受影响）。
     */
    public static void reset(String name) {
        if (name != null) {
            FIRED.remove(name);
        }
    }

    /**
     * 清除所有信号状态（新开游戏 / 读档时调用）。
     */
    public static void resetAll() {
        FIRED.clear();
        WAITERS.clear();
        Logger("INFO", "[StorySignals] 已重置所有剧情信号");
    }

    /** 当前等待中的信号名（调试用）。 */
    public static Set<String> pendingSignals() {
        return Set.copyOf(WAITERS.keySet());
    }
}
