package com.xiaowu.game.starveil.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DataManager 内存覆盖层测试。
 *
 * <p>覆盖层的语义：<b>只改内存、不落盘</b>，读取时优先级最高。
 *
 * <p>注意这里<b>不</b>测试「代码写入会顶掉覆盖」那条路径 ——
 * {@code DataManager.set*} 会真的调用 {@code saveData()} 写磁盘，
 * 在单测里跑等于去改写开发者/玩家的真实配置文件。
 * 该规则由 {@code putAndSave()} 统一实现（所有 setter 都走它）。
 */
class DataManagerMemoryOverrideTest {

    @BeforeEach
    @AfterEach
    void clear() {
        DataManager.clearAllMemoryOverrides();
    }

    @Test
    void noOverrideByDefault() {
        assertFalse(DataManager.hasMemoryOverride("some.key"));
        assertTrue(DataManager.getMemoryOverrides().isEmpty());
    }

    @Test
    void overrideIsReadBack() {
        DataManager.setMemoryOverride("some.key", "42");
        assertTrue(DataManager.hasMemoryOverride("some.key"));
        assertEquals("42", DataManager.getString("some.key"),
                "覆盖值必须能被读出来，否则调试改值毫无意义");
    }

    @Test
    void overrideWorksEvenBeforeConfigIsLoaded() {
        // 配置文件没加载成功（dataMap == null）时也应能读到覆盖 ——
        // 单测环境恰好就是这种情况，顺带把这条语义钉住
        DataManager.setMemoryOverride("k", "v");
        assertEquals("v", DataManager.getString("k"));
    }

    @Test
    void typedAccessorsSeeTheOverride() {
        DataManager.setMemoryOverride("num", "7");
        assertEquals(7, DataManager.getInt("num", 0));

        DataManager.setMemoryOverride("flag", "true");
        assertTrue(DataManager.getBoolean("flag", false));
        assertTrue(DataManager.isInertiaEnabled(),
                "内置特殊键同样能被覆盖（惯性开关）");

        DataManager.setMemoryOverride("d", "1.5");
        assertEquals(1.5, DataManager.getDouble("d", 0.0), 1e-9);
        assertEquals(1.5f, DataManager.getFloat("d", 0f), 1e-6f);
        assertEquals(7L, DataManager.getLong("num", 0L));
    }

    @Test
    void clearSingleOverride() {
        DataManager.setMemoryOverride("a", "1");
        DataManager.setMemoryOverride("b", "2");

        DataManager.clearMemoryOverride("a");

        assertFalse(DataManager.hasMemoryOverride("a"));
        assertNull(DataManager.getString("a"), "清掉覆盖后应回到「文件里没有」的状态");
        assertTrue(DataManager.hasMemoryOverride("b"), "不该误伤其它键");
        assertEquals("2", DataManager.getString("b"));
    }

    @Test
    void clearAllOverrides() {
        DataManager.setMemoryOverride("a", "1");
        DataManager.setMemoryOverride("b", "2");

        DataManager.clearAllMemoryOverrides();

        assertTrue(DataManager.getMemoryOverrides().isEmpty());
        assertFalse(DataManager.hasMemoryOverride("a"));
    }

    @Test
    void overridesSnapshotIsACopy() {
        DataManager.setMemoryOverride("a", "1");
        DataManager.getMemoryOverrides().put("b", "2");

        assertFalse(DataManager.hasMemoryOverride("b"),
                "取到的必须是快照，外部改动不能影响内部状态");
    }

    @Test
    void keyIsTrimmed() {
        DataManager.setMemoryOverride("  padded  ", "v");
        assertTrue(DataManager.hasMemoryOverride("padded"));
        assertEquals("v", DataManager.getString("padded"));
    }

    @Test
    void blankKeyIsIgnored() {
        DataManager.setMemoryOverride(null, "v");
        DataManager.setMemoryOverride("   ", "v");
        assertTrue(DataManager.getMemoryOverrides().isEmpty());
    }

    @Test
    void noLegacyKeyMigration() {
        // 框架测试阶段不做历史键迁移：裸名不再被当作任何键的别名
        DataManager.setMemoryOverride("starveil:cant_exit", "true");
        assertNull(DataManager.getString("CantExit"),
                "老裸名不应再被映射到新键 —— 老配置直接删掉即可");
    }
}
