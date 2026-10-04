package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.infrastructure.persistence.DataKey;
import com.xiaowu.game.starveil.infrastructure.persistence.DataKeyFlag;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.SaveDataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剧情状态键必须先注册 —— 这是一个真实 bug 的回归测试。
 *
 * <h3>那个 bug</h3>
 * 第一章里写着 {@code if (s.counter("jiwu.patience") >= 3)}，
 * 而 {@code jiwu.patience} 从来没有注册过（键的完整名是
 * {@code 命名空间:键名}，冒号而不是点号）。当时框架的处理是：
 * 读 → 打一条 WARNING 并返回默认值，写 → 打一条 WARNING 并丢弃。
 * 结果是<b>这个分支永远不会成立</b>，而脚本本身看起来毫无问题 ——
 * 只有把三种写法都试一遍的人才会发现「第 4 次」和「第 1 次」没区别。
 *
 * <h3>现在的约定</h3>
 * 未注册的键直接抛 {@link StoryScriptException}，消息里说明该注册成什么，
 * 并在「点号写法」这种最常见的笔误上给出冒号形式的提示。
 * 同时提供收 {@code DataKey} 的重载：命名空间与类型都由声明处保证，
 * 从根上不会再写错。
 */
class StoryScriptStateTest {

    private StoryScript script;

    @BeforeEach
    void setUp() {
        DataManager.resetForTest();
        SaveDataManager.getInstance().clear();
        SaveDataManager.setActive(true);
        script = new StoryScript("test");
    }

    @AfterEach
    void tearDown() {
        SaveDataManager.setActive(false);
        SaveDataManager.getInstance().clear();
        DataManager.resetForTest();
    }

    // ==================== 未注册的键 ====================

    @Test
    void unregisteredKeyOnReadThrowsWithAColonHint() {
        StoryScriptException e = assertThrows(StoryScriptException.class,
                () -> script.counter("jiwu.patience"),
                "未注册的键必须报错，而不是静默返回 0 —— 静默返回 0 就是「分支永远不成立」");

        String msg = e.getMessage();
        assertTrue(msg.contains("未注册"), "消息要说清是没注册：" + msg);
        assertTrue(msg.contains("jiwu:patience"),
                "点号写法是最常见的笔误，消息里要给出冒号形式的正确键：" + msg);
    }

    @Test
    void unregisteredKeyOnWriteThrows() {
        assertThrows(StoryScriptException.class, () -> script.setFlag("jiwu.trust", false));
        assertThrows(StoryScriptException.class, () -> script.addCounter("jiwu.patience", 1));
        assertThrows(StoryScriptException.class, () -> script.setCounter("jiwu.patience", 0));
        assertThrows(StoryScriptException.class, () -> script.set("jiwu.name", "x"));
        assertThrows(StoryScriptException.class, () -> script.bool("jiwu.trust", false));
        assertThrows(StoryScriptException.class, () -> script.flag("jiwu.trust"));
        assertThrows(StoryScriptException.class, () -> script.data("jiwu.something"));
    }

    @Test
    void nullKeyThrowsInsteadOfExplodingLater() {
        assertThrows(StoryScriptException.class, () -> script.counter((String) null));
        assertThrows(StoryScriptException.class, () -> script.counter((DataKey<Integer>) null));
    }

    // ==================== 注册过的键：第一章那段逻辑 ====================

    /**
     * 把第一章「写错名字三次就放过玩家」那段逻辑原样跑一遍。
     *
     * <p>这是这次修复真正要保证的事：<b>第 4 次必须能进那个分支</b>。
     */
    @Test
    void patienceCounterActuallyReachesThree() {
        DataKey<Integer> patience =
                DataManager.defineInt("jiwu", "patience", 0, DataKeyFlag.PER_SAVE);
        String playerName = "雾念";

        int attempts = 0;
        boolean forgave = false;
        for (int i = 1; i <= 4; i++) {
            attempts++;
            String signed = "写错的名字" + i;                 // 玩家每次都写错
            if (signed.equals(playerName)) {
                break;                                        // 写对了就结束
            }
            if (script.counter(patience) >= 3) {              // ← 原来永远不成立的那一行
                forgave = true;
                break;
            }
            script.addCounter(patience, 1);
        }

        assertEquals(4, attempts, "第 4 次尝试才该被放过");
        assertTrue(forgave, "写错三次之后必须走「直接预填答案」的分支");
        assertEquals(3, script.counter(patience));
        assertEquals(3, script.counter("jiwu:patience"),
                "字符串写法（完整键名）读的是同一个变量");
    }

    @Test
    void registeredFlagRoundTripsThroughBothOverloads() {
        DataKey<Boolean> trust =
                DataManager.defineBool("jiwu", "trust", true, DataKeyFlag.PER_SAVE);

        assertTrue(script.bool(trust), "没设过时读到的是声明处的默认值");
        script.setFlag(trust, false);
        assertFalse(script.bool(trust));
        assertFalse(script.bool("jiwu:trust", true), "字符串写法读同一个变量");
    }

    @Test
    void registeredStringStateRoundTrips() {
        DataKey<String> name = DataManager.defineStr("wuyin", "hero_name", "霁雾");
        assertEquals("霁雾", script.flag(name));
        script.set(name, "雾念");
        assertEquals("雾念", script.flag("wuyin:hero_name"));
    }
}
