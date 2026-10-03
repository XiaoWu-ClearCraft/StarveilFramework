package com.xiaowu.game.starveil.infrastructure.persistence;

import com.xiaowu.game.starveil.config.GameConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 键注册与命名空间规范测试。
 *
 * <p>规则：键必须注册；内置键带 {@code starveil:} 前缀；第三方必须带自己的
 * 命名空间前缀，且不得占用 {@code starveil:}。
 */
class SpecialKeysTest {

    @BeforeEach
    @AfterEach
    void reset() {
        DataKeyRegistry.resetForTest();
    }

    // ==================== 内置键 ====================

    @Test
    void builtInKeysAreSpecialAndPrefixed() {
        assertTrue(SpecialKeys.isSpecial(GameConstants.CANT_EXIT_KEY));
        assertTrue(SpecialKeys.isSpecial(GameConstants.INERTIA_KEY));
        assertTrue(SpecialKeys.isBuiltIn(GameConstants.INERTIA_KEY));

        assertTrue(GameConstants.CANT_EXIT_KEY.startsWith("starveil:"),
                "内置特殊键必须带 starveil: 前缀");
        assertTrue(GameConstants.INERTIA_KEY.startsWith("starveil:"),
                "惯性开关也是内置特殊键");
    }

    @Test
    void globalFrameworkKeysAreRegisteredButNotSpecial() {
        // 画面设置这类键是全局的，不是「特殊键」
        assertTrue(DataKeyRegistry.isRegistered(GameConstants.SETTING_ASPECT_RATIO));
        assertFalse(SpecialKeys.isSpecial(GameConstants.SETTING_ASPECT_RATIO),
                "全局键不该被当作特殊键");
    }

    @Test
    void legacyBareNameIsNeitherSpecialNorMigrated() {
        // 框架测试阶段不做历史迁移
        assertFalse(SpecialKeys.isSpecial("CantExit"), "裸名不是特殊键");
        assertEquals("CantExit", SpecialKeys.migrate("CantExit"),
                "migrate 只做去空白，不再做别名映射");
        assertEquals(GameConstants.INERTIA_KEY, SpecialKeys.migrate(GameConstants.INERTIA_KEY));
        assertThrows(IllegalArgumentException.class,
                () -> DataKey.of(null, "CantExit", false),
                "裸名连注册都进不去");
    }

    // ==================== 第三方注册 ====================

    @Test
    void thirdPartyKeyWithNamespaceIsAccepted() {
        DataKey<Boolean> key = DataKey.of("myplugin", "some_key", false);
        assertEquals("myplugin:some_key", key.qualified());
        assertTrue(DataKeyRegistry.isRegistered("myplugin:some_key"));
        assertFalse(SpecialKeys.isBuiltIn("myplugin:some_key"), "第三方键不是内置键");
        assertFalse(SpecialKeys.isSpecial("myplugin:some_key"),
                "没声明 PER_SAVE 的第三方键是全局键");
    }

    @Test
    void perSaveFlagMakesAKeySpecial() {
        DataKey<Boolean> key = DataKey.of("myplugin", "save_flag", false, DataKeyFlag.PER_SAVE);
        assertTrue(SpecialKeys.isSpecial(key.qualified()));
        assertEquals(DataKeyScope.PER_SAVE, key.scope());
    }

    @Test
    void bareNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DataKey.of(null, "some_key", false),
                "没有命名空间前缀的裸名必须被拒绝");
        assertThrows(IllegalArgumentException.class,
                () -> DataKey.of("  ", "some_key", false));
        assertThrows(IllegalArgumentException.class,
                () -> DataKey.of(null, null, false));
    }

    @Test
    void emptyOrMalformedIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DataKey.of(null, "   ", "x"));
        assertThrows(IllegalArgumentException.class, () -> DataKey.of(null, "myplugin:", "x"));
        assertThrows(IllegalArgumentException.class, () -> DataKey.of(null, ":name", "x"));
        assertThrows(IllegalArgumentException.class, () -> DataKey.of(null, "a:b:c", "x"));
        assertThrows(IllegalArgumentException.class,
                () -> DataKey.of("starveil", "myplugin:other", "x"),
                "已经是完整键名时不该再单独传命名空间");
    }

    @Test
    void engineNamespaceBelongsToTheFramework() {
        // 引擎命名空间允许内容重定义内置键，但键名必须真的是内置的那个；
        // 新造一个 starveil: 键不属于内容该做的事，注册表会放行但不标为内置
        DataKey<String> k = DataKey.of("starveil", "framework_version", "9.9.9");
        assertTrue(DataKeyRegistry.isBuiltIn(k.qualified()),
                "重定义不会取消它的内置身份");
    }

    @Test
    void sameKeyCannotChangeType() {
        DataKey.of("myplugin", "value", 1);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DataKey.of("myplugin", "value", "字符串"),
                "同一个键被声明成两种类型时必须报错");
        assertTrue(e.getMessage().contains("value"));
    }

    @Test
    void reRegisteringSameTypeReplacesDefinition() {
        DataKey<Integer> first = DataKey.of("myplugin", "value", 1);
        DataKey<Integer> second = DataKey.of("myplugin", "value", 42);
        assertEquals(42, second.get(), "后注册的默认值生效");
        assertEquals(1, first.getInt(), "旧句柄不可变，仍持有自己的默认值");
    }

    @Test
    void nullDefaultIsRejectedBecauseTypeCannotBeInferred() {
        assertThrows(IllegalArgumentException.class,
                () -> DataKey.of("myplugin", "no_type", null));
    }

    @Test
    void unsupportedTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DataKey.of("myplugin", "bytes", new byte[]{1, 2}));
    }

    @Test
    void registeredKeysAreResolvableByName() {
        DataKey<Integer> k = DataKey.of("myplugin", "counter", 3);
        assertNotNull(DataKeyRegistry.lookup("myplugin:counter"));
        assertEquals(DataType.INT, DataKeyRegistry.typeOf("myplugin:counter"));
        assertNull(DataKeyRegistry.lookup("myplugin:absent"));
        assertEquals(k, DataKeyRegistry.require("myplugin:counter"));
    }

    @Test
    void builtInKeysSurviveReset() {
        DataKey.of("myplugin", "some_key", false);
        DataKeyRegistry.resetForTest();

        assertFalse(DataKeyRegistry.isRegistered("myplugin:some_key"), "第三方注册被清掉");
        assertTrue(SpecialKeys.isSpecial(GameConstants.CANT_EXIT_KEY), "内置键必须保留");
        assertTrue(SpecialKeys.isSpecial(GameConstants.INERTIA_KEY));
        assertNotNull(DataKeyRegistry.lookup(GameConstants.SETTING_ASPECT_RATIO),
                "全局部内置键同样要被恢复");
    }
}
