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
 * 带命名空间的存档变量测试。
 *
 * <p>核心规则：键必须注册；完整键名（{@code chapter3:123}）保持自身，
 * 只给键名时必须补上命名空间；未注册的键读写都被拒绝。
 */
class SaveDataManagerScopedTest {

    private SaveDataManager mgr;

    @BeforeEach
    @AfterEach
    void reset() {
        mgr = SaveDataManager.getInstance();
        mgr.resetForTest();
        DataKeyRegistry.resetForTest();
    }

    // ==================== 键限定 ====================

    @Test
    void nonSpecialKeyGetsChapterPrefix() {
        assertEquals("chapter3:123",
                new SaveDataManager.SaveKey("chapter3", "123").qualified());
    }

    @Test
    void alreadyQualifiedNameIsLeftAlone() {
        assertEquals(GameConstants.INERTIA_KEY,
                new SaveDataManager.SaveKey("chapter3", GameConstants.INERTIA_KEY).qualified());
        assertEquals("chapter3:123",
                new SaveDataManager.SaveKey(null, "chapter3:123").qualified());
    }

    @Test
    void bareNameWithoutNamespaceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaveDataManager.SaveKey(null, "123").qualified());
        assertThrows(IllegalArgumentException.class,
                () -> new SaveDataManager.SaveKey("  ", "123").qualified());
    }

    @Test
    void emptyNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SaveDataManager.SaveKey("chapter3", null));
        assertThrows(IllegalArgumentException.class, () -> new SaveDataManager.SaveKey("chapter3", "  "));
    }

    // ==================== 强制注册 ====================

    @Test
    void usingUnregisteredKeyIsRejectedOnWrite() {
        // 未注册时写入被拒绝且不落值；读取拿到调用方给的默认值
        mgr.setInt("chapter3:123", 5);
        assertFalse(mgr.contains("chapter3:123"));
        assertEquals(0, mgr.getInt("chapter3:123", 0));
        assertFalse(mgr.isKeyRegistered(new SaveDataManager.SaveKey("chapter3", "123")));
    }

    @Test
    void frameworkKeyNeedsNoExtraRegistration() {
        DataKey<Boolean> inertia = FrameworkDataKeys.INERTIA;
        assertTrue(mgr.isKeyRegistered(
                new SaveDataManager.SaveKey("chapter3", GameConstants.INERTIA_KEY)),
                "框架内置键已在注册表里，无需再登记");
        assertTrue(SpecialKeys.isSpecial(inertia.qualified()));
    }

    // ==================== 读写 ====================

    @Test
    void allTypesRoundTrip() {
        DataKey<String> s = DataKey.of("chapter3", "s", "", String.class, DataKeyFlag.PER_SAVE);
        DataKey<Integer> i = DataKey.of("chapter3", "i", 0, Integer.class, DataKeyFlag.PER_SAVE);
        DataKey<Long> l = DataKey.of("chapter3", "l", 0L, Long.class, DataKeyFlag.PER_SAVE);
        DataKey<Double> d = DataKey.of("chapter3", "d", 0.0, Double.class, DataKeyFlag.PER_SAVE);
        DataKey<Float> f = DataKey.of("chapter3", "f", 0f, Float.class, DataKeyFlag.PER_SAVE);
        DataKey<Boolean> b = DataKey.of("chapter3", "b", false, Boolean.class, DataKeyFlag.PER_SAVE);

        SaveDataManager.setActive(true);

        s.set("hello");
        i.set(7);
        l.set(9_000_000_000L);
        d.set(1.5);
        f.set(2.5f);
        b.set(true);

        assertEquals("hello", s.get());
        assertEquals(7, i.getInt());
        assertEquals(9_000_000_000L, l.getLong());
        assertEquals(1.5, d.getDouble());
        assertEquals(2.5f, f.getFloat());
        assertTrue(b.getBool());
    }

    @Test
    void missingKeyYieldsDefault() {
        DataKey<Integer> k = DataKey.of("chapter3", "absent", 99, Integer.class, DataKeyFlag.PER_SAVE);
        SaveDataManager.setActive(true);

        assertEquals(99, k.getInt());
        assertFalse(k.isSet());
    }

    @Test
    void namespacesAreIsolated() {
        DataKey<Integer> c3 = DataKey.of("chapter3", "123", 0, Integer.class, DataKeyFlag.PER_SAVE);
        DataKey<Integer> c4 = DataKey.of("chapter4", "123", 0, Integer.class, DataKeyFlag.PER_SAVE);
        SaveDataManager.setActive(true);

        c3.set(1);
        c4.set(2);

        assertEquals(1, c3.getInt());
        assertEquals(2, c4.getInt(), "同名变量在不同命名空间下必须互不影响");
    }

    @Test
    void removeDeletesValue() {
        DataKey<Integer> k = DataKey.of("chapter3", "123", 0, Integer.class, DataKeyFlag.PER_SAVE);
        SaveDataManager.setActive(true);
        k.set(1);
        assertTrue(k.isSet());

        k.remove();
        assertFalse(k.isSet());
        assertEquals(0, k.getInt());
    }

    // ==================== 与清档的关系 ====================

    @Test
    void clearWipesValuesButKeepsRegistration() {
        DataKey<Integer> k = DataKey.of("chapter3", "123", 0, Integer.class, DataKeyFlag.PER_SAVE);
        SaveDataManager.setActive(true);
        k.set(1);

        mgr.clear();

        assertFalse(k.isSet(), "新游戏要清掉变量值");
        assertTrue(DataKeyRegistry.isRegistered(k.qualified()),
                "但键注册是模式而非数据，清掉会导致之后所有访问都失败");
        assertEquals(0, k.getInt());
    }
}
