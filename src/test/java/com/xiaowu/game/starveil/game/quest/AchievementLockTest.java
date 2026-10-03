package com.xiaowu.game.starveil.game.quest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 成就锁定测试。
 *
 * <p>只覆盖「无操作」路径：这些分支在改状态前就返回，<b>不写磁盘</b>。
 * 真正解锁/锁定会调用 {@code saveUnlockedAchievements()} 覆写玩家的
 * {@code achievements.dat}，在单测里跑等于破坏真实存档，因此不在这里测。
 */
class AchievementLockTest {

    @Test
    void lockingANonUnlockedAchievementIsANoOp() {
        assertFalse(AchievementManager.lockAchievement("definitely_not_unlocked_id"),
                "本来就没解锁，应返回 false 且不做任何写入");
    }

    @Test
    void lockingNullOrBlankIsSafe() {
        assertFalse(AchievementManager.lockAchievement(null));
        assertFalse(AchievementManager.lockAchievement(""),
                "空 ID 不应抛异常，否则调试窗口点一下就崩");
    }

    @Test
    void lockIsIdempotentForUnknownIds() {
        assertFalse(AchievementManager.lockAchievement("nope_1"));
        assertFalse(AchievementManager.lockAchievement("nope_1"));
    }
}
