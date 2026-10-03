package com.xiaowu.game.starveil.game.ecs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 变体注册与键解析回退测试。
 *
 * <p>两条容易写错的语义在这里钉死：
 * <ol>
 *   <li>变体整体优先于效果回退 —— 变体只定义基础姿态时，不该被默认模型集抢走；</li>
 *   <li>SKID 不回退到 WALK —— 急停用行走贴图是错的。</li>
 * </ol>
 */
class AnimKeyRegistryTest {

    @BeforeEach
    @AfterEach
    void reset() {
        AnimKeyRegistry.clearRegistry();
    }

    // ==================== 注册 ====================

    @Test
    void registersCustomVariant() {
        AnimKeyRegistry.registerVariant("SWIMMING");
        assertTrue(AnimKeyRegistry.isVariantRegistered("SWIMMING"));
        assertTrue(AnimKeyRegistry.registeredVariants().contains("SWIMMING"));
    }

    @Test
    void engineOwnedVariantCannotBeClaimed() {
        // LENGTHWAYS 由 GameplayMode.GRAVITY 声明，插件抢注会让人分不清
        // 某个键到底指向引擎模式还是插件
        assertTrue(AnimKeyRegistry.isBuiltInVariant("LENGTHWAYS"));
        assertThrows(IllegalArgumentException.class,
                () -> AnimKeyRegistry.registerVariant("LENGTHWAYS"));
        assertThrows(IllegalArgumentException.class,
                () -> AnimKeyRegistry.registerVariant("lengthways"));
        assertTrue(AnimKeyRegistry.builtInVariants().contains("LENGTHWAYS"));
    }

    @Test
    void refusesBuiltInNames() {
        Set<String> before = AnimKeyRegistry.registeredVariants();
        for (String reserved : AnimState.builtInNames()) {
            assertThrows(IllegalArgumentException.class,
                    () -> AnimKeyRegistry.registerVariant(reserved),
                    reserved + " 是内置保留名，不允许注册");
            assertThrows(IllegalArgumentException.class,
                    () -> AnimKeyRegistry.registerVariant(reserved.toLowerCase()),
                    "保留名判断必须忽略大小写");
        }
        assertEquals(before, AnimKeyRegistry.registeredVariants(),
                "失败的内置名注册不应改动注册表");
    }

    @Test
    void refusesEmptyAndColon() {
        assertThrows(IllegalArgumentException.class, () -> AnimKeyRegistry.registerVariant(null));
        assertThrows(IllegalArgumentException.class, () -> AnimKeyRegistry.registerVariant("  "));
        assertThrows(IllegalArgumentException.class, () -> AnimKeyRegistry.registerVariant("A:B"));
    }

    @Test
    void refusesDuplicate() {
        AnimKeyRegistry.registerVariant("SWIMMING");
        assertThrows(IllegalArgumentException.class,
                () -> AnimKeyRegistry.registerVariant("SWIMMING"));
    }

    @Test
    void tryRegisterReportsInsteadOfThrowing() {
        assertTrue(AnimKeyRegistry.tryRegisterVariant("SWIMMING"));
        assertFalse(AnimKeyRegistry.tryRegisterVariant("SWIMMING"), "重复注册应返回 false");
        assertFalse(AnimKeyRegistry.tryRegisterVariant("WALK"), "内置名应返回 false");
        assertFalse(AnimKeyRegistry.tryRegisterVariant("LENGTHWAYS"), "引擎自带变体应返回 false");
    }

    // ==================== 回退顺序 ====================

    @Test
    void nullWhenNothingDefined() {
        assertNull(AnimKeyRegistry.resolve(k -> false, Facing.LEFT, null, AnimState.WALK));
    }

    @Test
    void exactKeyWins() {
        Set<AnimKey> defined = Set.of(AnimKey.parse("LEFT:WALK"));
        assertEquals(AnimKey.parse("LEFT:WALK"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, null, AnimState.WALK));
    }

    @Test
    void walkFallsBackToIdleThenBasePose() {
        assertEquals(AnimKey.parse("LEFT:IDLE"),
                AnimKeyRegistry.resolve(Set.of(AnimKey.parse("LEFT:IDLE"))::contains,
                        Facing.LEFT, null, AnimState.WALK));
        assertEquals(AnimKey.parse("LEFT"),
                AnimKeyRegistry.resolve(Set.of(AnimKey.parse("LEFT"))::contains,
                        Facing.LEFT, null, AnimState.WALK));
    }

    @Test
    void variantBasePoseBeatsDefaultWalk() {
        // 只定义了 LEFT:LENGTHWAYS 和 LEFT:WALK，请求变体的行走
        Set<AnimKey> defined = new HashSet<>(Set.of(
                AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKey.parse("LEFT:WALK")));

        assertEquals(AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, "LENGTHWAYS", AnimState.WALK),
                "变体的基础姿态比默认模型集的行走更具体，必须优先命中");
    }

    @Test
    void variantEffectWinsOverVariantBasePose() {
        Set<AnimKey> defined = new HashSet<>(Set.of(
                AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKey.parse("LEFT:LENGTHWAYS:WALK")));

        assertEquals(AnimKey.parse("LEFT:LENGTHWAYS:WALK"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, "LENGTHWAYS", AnimState.WALK));
    }

    @Test
    void fallsBackToDefaultModelSetWhenVariantMissing() {
        Set<AnimKey> defined = new HashSet<>(Set.of(AnimKey.parse("LEFT:WALK")));

        assertEquals(AnimKey.parse("LEFT:WALK"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, "LENGTHWAYS", AnimState.WALK),
                "变体完全没定义时回退到普通模型集");
    }

    @Test
    void skidNeverFallsBackToWalk() {
        Set<AnimKey> defined = new HashSet<>(Set.of(AnimKey.parse("LEFT:WALK")));

        assertNull(AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, null, AnimState.SKID),
                "急停没有专门贴图时不能拿行走贴图顶上");

        assertEquals(AnimKey.parse("LEFT"),
                AnimKeyRegistry.resolve(
                        Set.of(AnimKey.parse("LEFT"))::contains,
                        Facing.LEFT, null, AnimState.SKID),
                "应退回基础姿态");
    }

    @Test
    void idleDoesNotFallBackToWalk() {
        Set<AnimKey> defined = new HashSet<>(Set.of(AnimKey.parse("LEFT:WALK")));

        assertNull(AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, null, AnimState.IDLE),
                "站着不动时不该拿行走贴图顶上；没有基础姿态就交给调用方的兜底逻辑");

        assertEquals(AnimKey.parse("LEFT:IDLE"),
                AnimKeyRegistry.resolve(
                        Set.of(AnimKey.parse("LEFT:IDLE"))::contains,
                        Facing.LEFT, null, AnimState.IDLE));
    }

    // ==================== 回退链本身 ====================

    @Test
    void variantChainPutsSpecificFirst() {
        assertEquals(java.util.Arrays.asList("LENGTHWAYS", null),
                AnimKeyRegistry.variantChain("LENGTHWAYS"));
        assertEquals(java.util.Collections.singletonList((String) null),
                AnimKeyRegistry.variantChain(null));
        assertEquals(java.util.Arrays.asList("LENGTHWAYS", null),
                AnimKeyRegistry.variantChain("  LENGTHWAYS  "));
    }

    @Test
    void effectChainMatchesDocumentedRules() {
        assertEquals(java.util.Arrays.asList(AnimState.IDLE, null),
                AnimKeyRegistry.effectChain(AnimState.IDLE));
        assertEquals(java.util.Arrays.asList(AnimState.WALK, AnimState.IDLE, null),
                AnimKeyRegistry.effectChain(AnimState.WALK));
        assertEquals(java.util.Arrays.asList(AnimState.SKID, null),
                AnimKeyRegistry.effectChain(AnimState.SKID));
        assertEquals(java.util.Collections.singletonList((AnimState) null),
                AnimKeyRegistry.effectChain(null));
    }

    // ==================== 角度回退 ====================

    @Test
    void refusesPureDigitVariantName() {
        // 纯数字段会被解析成角度，所以变体名不能是纯数字
        assertThrows(IllegalArgumentException.class,
                () -> AnimKeyRegistry.registerVariant("90"));
        assertThrows(IllegalArgumentException.class,
                () -> AnimKeyRegistry.registerVariant("-45"));
        assertFalse(AnimKeyRegistry.tryRegisterVariant("180"));
    }

    @Test
    void angleChainPutsAngleSpecificFirst() {
        assertEquals(java.util.Arrays.asList(90, null),
                AnimKeyRegistry.angleChain(90));
        assertEquals(java.util.Arrays.asList(90, null),
                AnimKeyRegistry.angleChain(450));
        assertEquals(java.util.Collections.singletonList((Integer) null),
                AnimKeyRegistry.angleChain(0), "0° 等价于无角度");
        assertEquals(java.util.Collections.singletonList((Integer) null),
                AnimKeyRegistry.angleChain(null));
    }

    @Test
    void angleSpecificSheetWins() {
        Set<AnimKey> defined = new HashSet<>(Set.of(
                AnimKey.parse("LEFT:WALK"),
                AnimKey.parse("LEFT:WALK:90")));

        assertEquals(AnimKey.parse("LEFT:WALK:90"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, null, AnimState.WALK, 90));
    }

    @Test
    void fallsBackToNonAngleSheetWhenNoAngleSpecificOneExists() {
        Set<AnimKey> defined = new HashSet<>(Set.of(AnimKey.parse("LEFT:WALK")));

        assertEquals(AnimKey.parse("LEFT:WALK"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, null, AnimState.WALK, 90),
                "没有 90° 专用贴图时应回退到普通贴图（由调用方旋转）");
    }

    @Test
    void angleOfZeroIsEquivalentToNoAngle() {
        // 0° 归一化成「无角度」。若不做归一化，同一张贴图会有两个表项，
        // 且 Set.of 会因为「重复元素」直接抛异常（下面第一条断言就是它的来源）。
        assertEquals(AnimKey.parse("LEFT:WALK"), AnimKey.parse("LEFT:WALK:0"));

        Set<AnimKey> defined = Set.of(AnimKey.parse("LEFT:WALK"));
        assertEquals(AnimKey.parse("LEFT:WALK"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, null, AnimState.WALK, 0));
    }

    @Test
    void variantStillBeatsAngleSpecificSheetInOtherModelSet() {
        // LENGTHWAYS 变体只有基础姿态；默认模型集有 90° 专用行走。
        // 变体整体优先于角度，否则重力模式下会突然跳回普通贴图。
        Set<AnimKey> defined = new HashSet<>(Set.of(
                AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKey.parse("LEFT:WALK:90")));

        assertEquals(AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, "LENGTHWAYS",
                        AnimState.WALK, 90));
    }

    @Test
    void variantWithItsOwnAngleSheetWins() {
        Set<AnimKey> defined = new HashSet<>(Set.of(
                AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKey.parse("LEFT:LENGTHWAYS:WALK:90")));

        assertEquals(AnimKey.parse("LEFT:LENGTHWAYS:WALK:90"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, "LENGTHWAYS",
                        AnimState.WALK, 90));
    }

    @Test
    void resolveWithoutAngleBehavesAsBefore() {
        Set<AnimKey> defined = new HashSet<>(Set.of(
                AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKey.parse("LEFT:WALK")));

        assertEquals(AnimKey.parse("LEFT:LENGTHWAYS"),
                AnimKeyRegistry.resolve(defined::contains, Facing.LEFT, "LENGTHWAYS",
                        AnimState.WALK));
    }
}
