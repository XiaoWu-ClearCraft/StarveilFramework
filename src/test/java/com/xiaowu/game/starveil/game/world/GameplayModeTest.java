package com.xiaowu.game.starveil.game.world;

import com.xiaowu.game.starveil.game.ecs.AnimKey;
import com.xiaowu.game.starveil.game.ecs.AnimState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 玩法模式测试。 */
class GameplayModeTest {

    @Test
    void normalHasNoGravityAndNoVariant() {
        assertFalse(GameplayMode.NORMAL.hasGravity());
        assertNull(GameplayMode.NORMAL.modelVariant(), "普通模式不改变贴图查找");
    }

    @Test
    void gravityUsesLengthwaysVariant() {
        assertTrue(GameplayMode.GRAVITY.hasGravity());
        assertEquals("LENGTHWAYS", GameplayMode.GRAVITY.modelVariant());
    }

    @Test
    void fromNameIsCaseInsensitive() {
        assertEquals(GameplayMode.GRAVITY, GameplayMode.fromName("gravity"));
        assertEquals(GameplayMode.GRAVITY, GameplayMode.fromName("  GRAVITY  "));
        assertEquals(GameplayMode.NORMAL, GameplayMode.fromName("normal"));
    }

    @Test
    void unknownNameIsNullButFallsBackToNormal() {
        assertNull(GameplayMode.fromName("nope"));
        assertNull(GameplayMode.fromName(null));
        assertEquals(GameplayMode.NORMAL, GameplayMode.fromNameOrNormal("nope"));
        assertEquals(GameplayMode.NORMAL, GameplayMode.fromNameOrNormal(null));
        assertEquals(GameplayMode.GRAVITY, GameplayMode.fromNameOrNormal("GRAVITY"));
    }

    /**
     * 跨模块不变式：模式声明的变体名不能是内置效果名。
     *
     * <p>{@code LEFT:WALK} 是二段键，靠「第二段是不是内置效果名」来区分
     * 效果与变体。若某个模式把变体取名 WALK，这个键就同时有两种含义。
     */
    @Test
    void modeVariantsMustNotCollideWithBuiltInEffectNames() {
        for (GameplayMode m : GameplayMode.values()) {
            String variant = m.modelVariant();
            if (variant == null) {
                continue;
            }
            assertFalse(AnimState.isBuiltIn(variant),
                    "模式 " + m.name() + " 的变体名 " + variant
                            + " 与内置效果名冲突，会让动画键语法产生歧义");
            assertNotNull(AnimKey.parse("LEFT:" + variant + ":WALK"),
                    "模式变体必须能写出合法的三段键");
            assertTrue(com.xiaowu.game.starveil.game.ecs.AnimKeyRegistry
                            .isBuiltInVariant(variant),
                    "模式 " + m.name() + " 的变体名 " + variant
                            + " 必须在 AnimKeyRegistry 里登记为引擎自带变体，"
                            + "否则第三方可以抢注同名变体");
        }
    }
}
