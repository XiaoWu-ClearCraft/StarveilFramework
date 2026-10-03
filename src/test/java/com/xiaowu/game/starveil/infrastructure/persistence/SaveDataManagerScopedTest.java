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
 * <p>核心规则：非特殊键自动补章节前缀（{@code chapter3 + 123 → chapter3:123}），
 * 特殊键保持自身（不加前缀），且所有键都必须提前登记。
 */
class SaveDataManagerScopedTest {

    private SaveDataManager mgr;

    @BeforeEach
    @AfterEach
    void reset() {
        mgr = SaveDataManager.getInstance();
        mgr.clear();
        mgr.clearRegisteredKeys();
    }

    // ==================== 键限定 ====================

    @Test
    void nonSpecialKeyGetsChapterPrefix() {
        assertEquals("chapter3:123",
                new SaveDataManager.SaveKey("chapter3", "123").qualified());
    }

    @Test
    void specialKeyKeepsItselfWithoutPrefix() {
        assertEquals(GameConstants.INERTIA_KEY,
                new SaveDataManager.SaveKey("chapter3", GameConstants.INERTIA_KEY).qualified(),
                "特殊键不能被打上章节前缀");
    }

    @Test
    void alreadyQualifiedNameIsLeftAlone() {
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

    // ==================== 强制预注册 ====================

    @Test
    void usingUnregisteredKeyThrows() {
        SaveDataManager.SaveKey k = new SaveDataManager.SaveKey("chapter3", "123");

        assertThrows(IllegalArgumentException.class, () -> mgr.setScopedInt(k, 5),
                "未注册就用必须报错，而不是静默丢数据");
        assertThrows(IllegalArgumentException.class, () -> mgr.getScopedInt(k, 0));
        assertFalse(mgr.isKeyRegistered(k));
    }

    @Test
    void registeredKeyIsUsable() {
        SaveDataManager.SaveKey k = new SaveDataManager.SaveKey("chapter3", "123");
        mgr.registerKey(k);
        assertTrue(mgr.isKeyRegistered(k));

        mgr.setScopedInt(k, 42);
        assertEquals(42, mgr.getScopedInt(k, 0));
        assertTrue(mgr.containsScoped(k));

        // 真的写进了带前缀的键
        assertEquals("42", mgr.getString("chapter3:123"));
    }

    @Test
    void specialKeysNeedNoRegistration() {
        SaveDataManager.SaveKey k =
                new SaveDataManager.SaveKey("chapter3", GameConstants.INERTIA_KEY);
        assertTrue(mgr.isKeyRegistered(k), "特殊键由 SpecialKeys 统一登记");

        mgr.setScopedBoolean(k, true);
        assertTrue(mgr.getScopedBoolean(k, false));
        assertEquals("true", mgr.getString(GameConstants.INERTIA_KEY));
    }

    // ==================== 读写 ====================

    @Test
    void allTypesRoundTrip() {
        SaveDataManager.SaveKey s = new SaveDataManager.SaveKey("chapter3", "s");
        SaveDataManager.SaveKey i = new SaveDataManager.SaveKey("chapter3", "i");
        SaveDataManager.SaveKey l = new SaveDataManager.SaveKey("chapter3", "l");
        SaveDataManager.SaveKey d = new SaveDataManager.SaveKey("chapter3", "d");
        SaveDataManager.SaveKey f = new SaveDataManager.SaveKey("chapter3", "f");
        SaveDataManager.SaveKey b = new SaveDataManager.SaveKey("chapter3", "b");
        for (SaveDataManager.SaveKey k : new SaveDataManager.SaveKey[]{s, i, l, d, f, b}) {
            mgr.registerKey(k);
        }

        mgr.setScopedString(s, "hello");
        mgr.setScopedInt(i, 7);
        mgr.setScopedLong(l, 9_000_000_000L);
        mgr.setScopedDouble(d, 1.5);
        mgr.setScopedFloat(f, 2.5f);
        mgr.setScopedBoolean(b, true);

        assertEquals("hello", mgr.getScopedString(s, ""));
        assertEquals(7, mgr.getScopedInt(i, 0));
        assertEquals(9_000_000_000L, mgr.getScopedLong(l, 0L));
        assertEquals(1.5, mgr.getScopedDouble(d, 0.0));
        assertEquals(2.5f, mgr.getScopedFloat(f, 0f));
        assertTrue(mgr.getScopedBoolean(b, false));
    }

    @Test
    void missingKeyYieldsDefault() {
        SaveDataManager.SaveKey k = new SaveDataManager.SaveKey("chapter3", "absent");
        mgr.registerKey(k);

        assertEquals(99, mgr.getScopedInt(k, 99));
        assertEquals("dv", mgr.getScopedString(k, "dv"));
        assertFalse(mgr.containsScoped(k));
    }

    @Test
    void incrementScopedIntStartsFromZero() {
        SaveDataManager.SaveKey k = new SaveDataManager.SaveKey("chapter3", "kills");
        mgr.registerKey(k);

        mgr.incrementScopedInt(k, 1);
        mgr.incrementScopedInt(k, 1);
        mgr.incrementScopedInt(k, 3);
        assertEquals(5, mgr.getScopedInt(k, 0));
    }

    @Test
    void namespacesAreIsolated() {
        SaveDataManager.SaveKey c3 = new SaveDataManager.SaveKey("chapter3", "123");
        SaveDataManager.SaveKey c4 = new SaveDataManager.SaveKey("chapter4", "123");
        mgr.registerKey(c3);
        mgr.registerKey(c4);

        mgr.setScopedInt(c3, 1);
        mgr.setScopedInt(c4, 2);

        assertEquals(1, mgr.getScopedInt(c3, 0));
        assertEquals(2, mgr.getScopedInt(c4, 0),
                "同名变量在不同章节下必须互不影响");
    }

    @Test
    void removeScopedDeletesValue() {
        SaveDataManager.SaveKey k = new SaveDataManager.SaveKey("chapter3", "123");
        mgr.registerKey(k);
        mgr.setScopedInt(k, 1);
        mgr.removeScoped(k);
        assertFalse(mgr.containsScoped(k));
    }

    // ==================== 与清档的关系 ====================

    @Test
    void clearWipesValuesButKeepsRegistration() {
        SaveDataManager.SaveKey k = new SaveDataManager.SaveKey("chapter3", "123");
        mgr.registerKey(k);
        mgr.setScopedInt(k, 1);

        mgr.clear();

        assertFalse(mgr.containsScoped(k), "新游戏要清掉变量值");
        assertTrue(mgr.isKeyRegistered(k),
                "但键登记是模式而非数据，清掉会导致之后所有访问都抛异常");
        assertEquals(0, mgr.getScopedInt(k, 0));
    }
}
