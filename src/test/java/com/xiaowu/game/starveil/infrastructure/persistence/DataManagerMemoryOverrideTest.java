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
 * <p>覆盖层是<b>唯一允许使用未注册键</b>的入口 —— 调试窗口需要能覆盖任意键。
 * 因此这里刻意用未注册的键名，顺带把这条规则钉住。
 *
 * <p>注意这里<b>不</b>测试「代码写入会顶掉覆盖」那条路径 ——
 * {@code DataKey.set()} 会真的调用 {@code saveData()} 写磁盘，
 * 在单测里跑等于去改写开发者/玩家的真实配置文件。
 * 该规则由 {@code writeRaw()} 统一实现（所有 setter 都走它）。
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
        DataManager.setMemoryOverride("starveil:cant_exit", "true");
        assertTrue(DataManager.hasMemoryOverride("starveil:cant_exit"));
        assertEquals("true", DataManager.getString("starveil:cant_exit"),
                "覆盖值必须能被读出来，否则调试改值毫无意义");
        assertTrue(DataManager.isExitBlocked(), "内置特殊键同样能被覆盖");
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
        DataManager.setMemoryOverride("starveil:setting.text_speed", "77");
        assertEquals(77, DataManager.getInt("starveil:setting.text_speed", 0));

        DataManager.setMemoryOverride("starveil:inertia", "true");
        assertTrue(DataManager.isInertiaEnabled(),
                "内置特殊键同样能被覆盖（惯性开关）");

        DataManager.setMemoryOverride("starveil:setting.bgm_volume", "0.25");
        assertEquals(0.25, DataManager.getDouble("starveil:setting.bgm_volume", 0.0), 1e-9);
    }

    @Test
    void clearSingleOverride() {
        DataManager.setMemoryOverride("starveil:cant_exit", "true");
        DataManager.setMemoryOverride("starveil:inertia", "true");

        DataManager.clearMemoryOverride("starveil:cant_exit");

        assertFalse(DataManager.hasMemoryOverride("starveil:cant_exit"));
        assertFalse(DataManager.isExitBlocked(), "清掉覆盖后回到默认值 false");
        assertTrue(DataManager.hasMemoryOverride("starveil:inertia"), "不该误伤其它键");
    }

    @Test
    void clearAllOverrides() {
        DataManager.setMemoryOverride("starveil:cant_exit", "true");
        DataManager.setMemoryOverride("starveil:inertia", "false");

        DataManager.clearAllMemoryOverrides();

        assertTrue(DataManager.getMemoryOverrides().isEmpty());
        assertFalse(DataManager.hasMemoryOverride("starveil:cant_exit"));
    }

    @Test
    void overridesSnapshotIsACopy() {
        DataManager.setMemoryOverride("starveil:cant_exit", "true");
        DataManager.getMemoryOverrides().put("starveil:inertia", "false");

        assertFalse(DataManager.hasMemoryOverride("starveil:inertia"),
                "取到的必须是快照，外部改动不能影响内部状态");
    }

    @Test
    void keyIsTrimmed() {
        DataManager.setMemoryOverride("  starveil:inertia  ", "false");
        assertTrue(DataManager.hasMemoryOverride("starveil:inertia"));
        assertFalse(DataManager.isInertiaEnabled());
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
        assertFalse(DataManager.getBoolean("CantExit", false),
                "老裸名不应再被映射到新键 —— 老配置直接删掉即可");
    }

    // ==================== 未注册键的规则 ====================

    @Test
    void unregisteredKeyReadsFallBackAndWritesAreRejected() {
        assertNull(DataManager.getString("myplugin:never_declared"),
                "未注册键读出来必须是「没有」");
        assertEquals(42, DataManager.getInt("myplugin:never_declared", 42),
                "未注册键读取回退到调用方给的默认值");

        DataManager.setInt("myplugin:never_declared", 7);
        assertFalse(DataManager.getAll().containsKey("myplugin:never_declared"),
                "未注册键的写入必须被拒绝，不能悄悄写进配置");
    }

    @Test
    void bareNameIsNotAValidKey() {
        assertNull(DataManager.getString("CantExit"),
                "裸名既未注册也不带命名空间，读出来是「没有」");
        DataManager.setBoolean("CantExit", true);
        assertFalse(DataManager.getAll().containsKey("CantExit"));
    }
}
