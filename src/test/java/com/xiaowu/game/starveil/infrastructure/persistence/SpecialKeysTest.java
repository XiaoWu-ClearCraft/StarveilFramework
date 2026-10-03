package com.xiaowu.game.starveil.infrastructure.persistence;

import com.xiaowu.game.starveil.config.GameConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 特殊键前缀规范测试。
 *
 * <p>规则：内置键带 {@code starveil:} 前缀；第三方注册必须带自己的命名空间前缀，
 * 且不得占用 {@code starveil:}。
 */
class SpecialKeysTest {

    @BeforeEach
    @AfterEach
    void reset() {
        SpecialKeys.resetForTest();
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
    void legacyBareNameIsNeitherSpecialNorMigrated() {
        // 框架测试阶段不做历史迁移
        assertFalse(SpecialKeys.isSpecial("CantExit"), "裸名不是特殊键");
        assertEquals("CantExit", SpecialKeys.migrate("CantExit"),
                "migrate 只做去空白，不再做别名映射");
        assertEquals(GameConstants.INERTIA_KEY, SpecialKeys.migrate(GameConstants.INERTIA_KEY));
    }

    // ==================== 第三方注册 ====================

    @Test
    void thirdPartyKeyWithNamespaceIsAccepted() {
        String key = SpecialKeys.register("myplugin:some_key");
        assertEquals("myplugin:some_key", key);
        assertTrue(SpecialKeys.isSpecial("myplugin:some_key"));
        assertFalse(SpecialKeys.isBuiltIn("myplugin:some_key"), "第三方键不是内置键");
    }

    @Test
    void bareNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SpecialKeys.register("some_key"),
                "没有命名空间前缀的裸名必须被拒绝");
        assertThrows(IllegalArgumentException.class, () -> SpecialKeys.register("CantExit"));
    }

    @Test
    void engineNamespaceIsReserved() {
        assertThrows(IllegalArgumentException.class,
                () -> SpecialKeys.register("starveil:my_key"),
                "starveil: 是引擎保留命名空间，第三方不得占用");
    }

    @Test
    void emptyOrMalformedIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SpecialKeys.register(null));
        assertThrows(IllegalArgumentException.class, () -> SpecialKeys.register("   "));
        assertThrows(IllegalArgumentException.class, () -> SpecialKeys.register("myplugin:"));
        assertThrows(IllegalArgumentException.class, () -> SpecialKeys.register(":name"));
        assertThrows(IllegalArgumentException.class,
                () -> SpecialKeys.register("a:b:c"));
    }

    @Test
    void duplicateAndBuiltInRegistrationAreRejected() {
        SpecialKeys.register("myplugin:some_key");
        assertThrows(IllegalArgumentException.class,
                () -> SpecialKeys.register("myplugin:some_key"));
        assertThrows(IllegalArgumentException.class,
                () -> SpecialKeys.register(GameConstants.INERTIA_KEY));
    }

    @Test
    void tryRegisterReportsInsteadOfThrowing() {
        assertTrue(SpecialKeys.tryRegister("myplugin:some_key"));
        assertFalse(SpecialKeys.tryRegister("myplugin:some_key"));
        assertFalse(SpecialKeys.tryRegister("bare_name"));
        assertFalse(SpecialKeys.tryRegister("starveil:x"));
    }

    @Test
    void builtInKeysSurviveReset() {
        SpecialKeys.register("myplugin:some_key");
        SpecialKeys.resetForTest();

        assertFalse(SpecialKeys.isSpecial("myplugin:some_key"), "第三方注册被清掉");
        assertTrue(SpecialKeys.isSpecial(GameConstants.CANT_EXIT_KEY), "内置键必须保留");
        assertTrue(SpecialKeys.isSpecial(GameConstants.INERTIA_KEY));
    }
}
