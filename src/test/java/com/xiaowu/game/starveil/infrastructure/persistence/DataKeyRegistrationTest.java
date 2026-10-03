package com.xiaowu.game.starveil.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 键注册制度的核心行为测试。
 *
 * <p>覆盖四件事：默认值来自注册处、类型不符被拒、只读锁定在默认值、
 * 临时键不落盘。
 */
class DataKeyRegistrationTest {

    @BeforeEach
    @AfterEach
    void reset() {
        DataManager.resetForTest();
    }

    // ==================== 默认值来自注册处 ====================

    @Test
    void defaultValueComesFromRegistration() {
        DataKey<Integer> k = DataManager.defineInt("t", "speed", 50);
        assertEquals(50, k.getInt(), "没设过值时读到注册时的默认值");
        assertFalse(k.isSet());

        k.set(80);
        assertEquals(80, k.getInt());
        assertTrue(k.isSet());

        k.remove();
        assertEquals(50, k.getInt(), "删除后回到默认值");
        assertFalse(k.isSet());
    }

    @Test
    void defaultIsNotNeededAtCallSites() {
        // 同一个键只有一份默认值：调用点不再各自传一份
        DataKey<String> name = DataManager.defineStr("t", "hero", "无名");
        assertEquals("无名", name.get());
        assertEquals("无名", name.getString());
    }

    // ==================== 类型校验 ====================

    @Test
    void writingWrongTypeIsRejected() {
        DataKey<Integer> level = DataManager.defineInt("t", "level", 1);
        level.setInt(5);
        assertEquals(5, level.getInt());

        // 只读的类型不符写入被拒
        DataManager.set("t:level", "abc");
        assertEquals(5, level.getInt(), "非法值不该覆盖已写入的值");

        DataManager.setBoolean("t:level", true);
        assertEquals(5, level.getInt(), "布尔值也不是 int，同样被拒");
    }

    @Test
    void numericWideningIsAllowed() {
        DataKey<Long> l = DataManager.defineLong("t", "ts", 0L);
        l.set(7);                       // int → long：无损，允许
        assertEquals(7L, l.getLong());

        DataKey<Double> d = DataManager.defineDouble("t", "ratio", 0.0);
        d.set(3);                       // int → double：无损，允许
        assertEquals(3.0, d.getDouble());
    }

    @Test
    void stringKeyAcceptsStringsOnly() {
        DataKey<String> s = DataManager.defineStr("t", "mode", "默认");
        s.set("自定义");
        assertEquals("自定义", s.get());

        // 数字写进字符串键：类型不符，拒绝
        DataManager.setInt("t:mode", 9);
        assertEquals("自定义", s.get());
    }

    @Test
    void booleanParsingRejectsGarbage() {
        DataKey<Boolean> b = DataManager.defineBool("t", "flag", false);
        DataManager.set("t:flag", "yes");
        assertFalse(b.getBool(), "yes 不是合法布尔值，回退到默认值");
    }

    // ==================== 只读 ====================

    @Test
    void readOnlyKeyIsLockedToDefault() {
        DataKey<String> version = DataManager.defineStr(
                "t", "version", "1.0.0", DataKeyFlag.READ_ONLY);

        version.set("9.9.9");
        assertEquals("1.0.0", version.get(), "只读键的写入不生效");

        DataManager.set("t:version", "8.8.8");
        assertEquals("1.0.0", version.get());

        version.remove();
        assertEquals("1.0.0", version.get(), "只读键也删不掉");
        assertFalse(version.isSet());
    }

    @Test
    void frameworkVersionIsReadOnly() {
        FrameworkDataKeys.FRAMEWORK_VERSION.set("hacked");
        assertEquals(com.xiaowu.game.starveil.config.GameConstants.FRAMEWORK_VERSION,
                FrameworkDataKeys.FRAMEWORK_VERSION.get());
    }

    // ==================== 临时键 ====================

    @Test
    void temporaryKeyNeverTouchesTheFile() {
        DataKey<String> temp = DataManager.defineStr(
                "t", "toast", "", DataKeyFlag.TEMPORARY);

        temp.set("加载中");
        assertEquals("加载中", temp.get());
        assertTrue(DataManager.isTemporary("t:toast"));
        assertFalse(DataManager.getAll().containsKey("t:toast"),
                "临时键绝不能出现在落盘的数据里");
        assertTrue(DataManager.getTemporaryKeys().containsKey("t:toast"));
    }

    @Test
    void temporaryKeyCanBeCleared() {
        DataKey<String> temp = DataManager.defineStr(
                "t", "temp", "默认", DataKeyFlag.TEMPORARY);
        temp.set("值");
        DataManager.clearTemporary("t:temp");
        assertEquals("默认", temp.get());
    }

    @Test
    void illegallyShutdownIsATemporaryKey() {
        FrameworkDataKeys.ILLEGALLY_SHUTDOWN.set(true);
        assertTrue(DataManager.isTemporary(
                FrameworkDataKeys.ILLEGALLY_SHUTDOWN.qualified()));
        assertFalse(DataManager.getAll()
                        .containsKey(FrameworkDataKeys.ILLEGALLY_SHUTDOWN.qualified()),
                "异常关闭标记不该落盘，被强杀也不会留残留");
    }

    // ==================== 类型不符的 getX ====================

    @Test
    void typedGettersOnWrongTypeFallBackToDefault() {
        DataKey<String> s = DataManager.defineStr("t", "text", "1");
        // 声明是字符串，用 getInt 读只是「取字符串的整数含义」，不算错；
        // 但明确用错类型的方法（getBool）必须回退到默认值而不是瞎猜
        assertFalse(s.getBool());
    }

    // ==================== 元信息 ====================

    @Test
    void flagsAreReflectedOnTheHandle() {
        DataKey<Integer> ro = DataManager.defineInt(
                "t", "locked", 1, DataKeyFlag.READ_ONLY);
        assertTrue(ro.readOnly());
        assertFalse(ro.temporary());
        assertEquals(DataKeyScope.GLOBAL, ro.scope());
        assertTrue(ro.is(DataType.INT));
        assertTrue(ro.is(DataKeyScope.GLOBAL));

        DataKey<Integer> ps = DataManager.defineInt(
                "t", "save", 1, DataKeyFlag.PER_SAVE);
        assertEquals(DataKeyScope.PER_SAVE, ps.scope());
        assertFalse(ps.readOnly());
    }

    @Test
    void registeredKeysAreListed() {
        DataManager.defineStr("t", "one", "1");
        DataManager.defineStr("t", "two", "2");
        assertTrue(DataManager.registeredKeys().contains("t:one"));
        assertTrue(DataManager.registeredKeys().contains("t:two"));
        assertTrue(DataManager.isKeyRegistered("t:one"));
        assertFalse(DataManager.isKeyRegistered("t:absent"));
    }

    // ==================== 存档作用域 ====================

    @Test
    void perSaveKeyFallsBackToGlobalWhenNoSaveIsLoaded() {
        DataKey<Boolean> flag = DataManager.defineBool(
                "t", "per_save", false, DataKeyFlag.PER_SAVE);
        SaveDataManager.setActive(false);

        // 没有活跃存档时写进全局
        flag.set(true);
        assertTrue(flag.get());
        assertTrue(DataManager.getAll().containsKey("t:per_save"));
    }

    @Test
    void perSaveKeyPrefersTheActiveSave() {
        DataKey<Boolean> flag = DataManager.defineBool(
                "t", "per_save2", false, DataKeyFlag.PER_SAVE);
        SaveDataManager mgr = SaveDataManager.getInstance();
        mgr.resetForTest();

        // 全局设为 false，存档里设为 true
        SaveDataManager.setActive(false);
        flag.set(false);
        SaveDataManager.setActive(true);
        flag.set(true);

        assertTrue(flag.get(), "存档里的值优先于全局");
        SaveDataManager.setActive(false);
    }

    @Test
    void saveLookupWorksEvenWithoutConfigFile() {
        // dataMap == null（尚未 initialize）时，存档作用域的键也要读得到
        DataKey<String> flag = DataManager.defineStr(
                "t", "no_cfg", "默认", DataKeyFlag.PER_SAVE);
        SaveDataManager mgr = SaveDataManager.getInstance();
        mgr.resetForTest();
        SaveDataManager.setActive(true);
        mgr.setString("t:no_cfg", "存档值");

        assertEquals("存档值", flag.get());
        SaveDataManager.setActive(false);
    }

    // ==================== 未注册键 ====================

    @Test
    void unregisteredSaveWriteIsRejected() {
        SaveDataManager mgr = SaveDataManager.getInstance();
        mgr.resetForTest();
        mgr.setInt("nobody:declared_this", 1);
        assertNull(mgr.getString("nobody:declared_this"),
                "未注册的存档变量写不进去");
    }

    @Test
    void debugOverrideWorksForUnregisteredKeys() {
        DataManager.setMemoryOverride("nobody:key", "42");
        assertEquals("42", DataManager.getString("nobody:key"),
                "调试覆盖是唯一能为未注册键提供值的入口");
        assertEquals(42, DataManager.getInt("nobody:key", 0));
    }

    @Test
    void temporaryUntypedWriteRequiresRegistration() {
        DataManager.setTemporary("nobody:temp", "v");
        assertFalse(DataManager.isTemporary("nobody:temp"),
                "未注册就写临时值同样被拒绝");

        DataManager.defineStr("t", "ok", "");
        DataManager.setTemporary("t:ok", "v");
        assertTrue(DataManager.isTemporary("t:ok"));
    }

    // ==================== 框架内置键一致性 ====================

    @Test
    void gameConstantsMatchRegisteredKeys() {
        assertNotNull(DataKeyRegistry.lookup(
                com.xiaowu.game.starveil.config.GameConstants.CANT_EXIT_KEY),
                "GameConstants 里的键名必须与注册的键一致");
        assertNotNull(DataKeyRegistry.lookup(
                com.xiaowu.game.starveil.config.GameConstants.INERTIA_KEY));
        assertNotNull(DataKeyRegistry.lookup(
                com.xiaowu.game.starveil.config.GameConstants.SETTING_TEXT_SPEED));
        assertNotNull(DataKeyRegistry.lookup(
                com.xiaowu.game.starveil.config.GameConstants.SETTING_OVERLAY_ENABLED));
    }
}
