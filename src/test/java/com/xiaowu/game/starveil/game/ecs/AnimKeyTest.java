package com.xiaowu.game.starveil.game.ecs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动画键语法测试。
 *
 * <p>语法：{@code <方向>[:<变体>][:<效果>]}。二段键的含义取决于第二段
 * 是不是内置效果名 —— 这正是「变体名不得占用内置名」这条规则存在的理由。
 */
class AnimKeyTest {

    // ==================== 解析 ====================

    @Test
    void basePoseHasNoVariantAndNoEffect() {
        AnimKey k = AnimKey.parse("LEFT");
        assertEquals(Facing.LEFT, k.facing());
        assertNull(k.variant(), "LEFT 是普通模型集的基础姿态");
        assertNull(k.effect());
        assertTrue(k.isBasePose());
    }

    @Test
    void twoSegmentWithBuiltInNameIsAnEffect() {
        AnimKey k = AnimKey.parse("LEFT:WALK");
        assertEquals(Facing.LEFT, k.facing());
        assertNull(k.variant());
        assertEquals(AnimState.WALK, k.effect());
    }

    @Test
    void twoSegmentWithOtherNameIsAVariant() {
        AnimKey k = AnimKey.parse("LEFT:LENGTHWAYS");
        assertEquals(Facing.LEFT, k.facing());
        assertEquals("LENGTHWAYS", k.variant());
        assertNull(k.effect(), "变体的基础姿态没有效果段");
    }

    @Test
    void threeSegmentIsVariantPlusEffect() {
        AnimKey k = AnimKey.parse("LEFT:LENGTHWAYS:WALK");
        assertEquals(Facing.LEFT, k.facing());
        assertEquals("LENGTHWAYS", k.variant());
        assertEquals(AnimState.WALK, k.effect());
    }

    @Test
    void allFacingsParse() {
        for (Facing f : Facing.values()) {
            assertEquals(f, AnimKey.parse(f.name()).facing());
            assertEquals(f, AnimKey.parse(f.name() + ":SKID").facing());
        }
    }

    @Test
    void parsingIsCaseInsensitiveAndTrims() {
        AnimKey k = AnimKey.parse("  left : lengthways : walk  ");
        assertEquals(Facing.LEFT, k.facing());
        assertEquals("LENGTHWAYS", k.variant(),
                "变体名大小写不敏感并规范化为大写，否则配置写法与查找用名会对不上");
        assertEquals(AnimState.WALK, k.effect());
        assertEquals(AnimKey.parse("LEFT:LENGTHWAYS:WALK"), k);
    }

    // ==================== 格式化 ====================

    @Test
    void formatRoundTrips() {
        for (String text : new String[]{
                "LEFT", "LEFT:WALK", "LEFT:LENGTHWAYS", "LEFT:LENGTHWAYS:WALK",
                "RIGHT:SKID", "UP:IDLE"}) {
            assertEquals(text, AnimKey.parse(text).format());
        }
    }

    @Test
    void formatNormalizesCase() {
        assertEquals("LEFT:LENGTHWAYS:WALK", AnimKey.parse("left:lengthways:walk").format());
    }

    // ==================== 非法输入 ====================

    @Test
    void rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> AnimKey.parse(null));
        assertThrows(IllegalArgumentException.class, () -> AnimKey.parse(""));
        assertThrows(IllegalArgumentException.class, () -> AnimKey.parse("   "));
    }

    @Test
    void rejectsKeyNotStartingWithFacing() {
        assertThrows(IllegalArgumentException.class, () -> AnimKey.parse("WALK"));
        assertThrows(IllegalArgumentException.class, () -> AnimKey.parse("LENGTHWAYS:WALK"));
    }

    @Test
    void rejectsTooManySegments() {
        assertThrows(IllegalArgumentException.class,
                () -> AnimKey.parse("LEFT:LENGTHWAYS:WALK:EXTRA"));
    }

    @Test
    void rejectsBuiltInNameUsedAsVariant() {
        // 这是整套语法的安全阀：LEFT:WALK:WALK 无意义，且会让 {LEFT:WALK} 产生歧义
        assertThrows(IllegalArgumentException.class,
                () -> AnimKey.parse("LEFT:WALK:WALK"));
        assertThrows(IllegalArgumentException.class,
                () -> AnimKey.parse("LEFT:SKID:IDLE"));
    }

    @Test
    void rejectsNonBuiltInThirdSegment() {
        assertThrows(IllegalArgumentException.class,
                () -> AnimKey.parse("LEFT:LENGTHWAYS:FLY"));
    }

    @Test
    void rejectsEmptySegments() {
        assertThrows(IllegalArgumentException.class, () -> AnimKey.parse("LEFT:"));
        assertThrows(IllegalArgumentException.class, () -> AnimKey.parse("LEFT::WALK"));
    }

    @Test
    void isValidMirrorsParseWithoutThrowing() {
        assertTrue(AnimKey.isValid("LEFT:LENGTHWAYS:WALK"));
        assertFalse(AnimKey.isValid("WALK"));
        assertFalse(AnimKey.isValid("LEFT:WALK:WALK"));
        assertFalse(AnimKey.isValid(null));
    }

    // ==================== 相等性 ====================

    @Test
    void equalityIsStructural() {
        assertEquals(AnimKey.parse("LEFT:WALK"), AnimKey.parse("left:walk"));
        assertEquals(AnimKey.parse("LEFT:WALK").hashCode(), AnimKey.parse("left:walk").hashCode());

        assertNotEquals(AnimKey.parse("LEFT:WALK"), AnimKey.parse("LEFT:LENGTHWAYS"));
        assertNotEquals(AnimKey.parse("LEFT:WALK"), AnimKey.parse("RIGHT:WALK"));
        assertNotEquals(AnimKey.parse("LEFT"), AnimKey.parse("LEFT:IDLE"));
    }

    @Test
    void ofRejectsNullFacing() {
        assertThrows(IllegalArgumentException.class,
                () -> AnimKey.of(null, null, AnimState.WALK));
    }

    @Test
    void builtInNamesAreReserved() {
        assertTrue(AnimState.isBuiltIn("WALK"));
        assertTrue(AnimState.isBuiltIn("walk"));
        assertTrue(AnimState.isBuiltIn("SKID"));
        assertFalse(AnimState.isBuiltIn("LENGTHWAYS"));
    }

    // ==================== 角度段 ====================

    @Test
    void trailingNumberIsAnAngle() {
        assertEquals(90, AnimKey.parse("LEFT:90").angle());
        assertNull(AnimKey.parse("LEFT:90").effect());
        assertNull(AnimKey.parse("LEFT:90").variant());
    }

    @Test
    void angleAfterVariant() {
        AnimKey k = AnimKey.parse("LEFT:LENGTHWAYS:90");
        assertEquals(Facing.LEFT, k.facing());
        assertEquals("LENGTHWAYS", k.variant());
        assertNull(k.effect(), "角度占了第三段，效果仍然为空");
        assertEquals(90, k.angle());
        assertTrue(k.hasAngle());
    }

    @Test
    void angleAfterEffect() {
        AnimKey k = AnimKey.parse("LEFT:WALK:90");
        assertNull(k.variant());
        assertEquals(AnimState.WALK, k.effect());
        assertEquals(90, k.angle());
    }

    @Test
    void fullFourSegmentKey() {
        AnimKey k = AnimKey.parse("LEFT:LENGTHWAYS:WALK:90");
        assertEquals(Facing.LEFT, k.facing());
        assertEquals("LENGTHWAYS", k.variant());
        assertEquals(AnimState.WALK, k.effect());
        assertEquals(90, k.angle());
    }

    @Test
    void angleIsNormalizedModulo360() {
        assertEquals(90, AnimKey.parse("LEFT:450").angle(), "450° 等价于 90°");
        assertEquals(270, AnimKey.parse("LEFT:-90").angle(), "-90° 等价于 270°");
        assertEquals(180, AnimKey.parse("LEFT:180").angle());
        assertNull(AnimKey.parse("LEFT:360").angle(), "360° 等价于不旋转");
        assertNull(AnimKey.parse("LEFT:0").angle(), "0° 等价于不旋转");
        assertFalse(AnimKey.parse("LEFT:0").hasAngle());
    }

    @Test
    void formatKeepsAngle() {
        assertEquals("LEFT:90", AnimKey.parse("LEFT:90").format());
        assertEquals("LEFT:LENGTHWAYS:90", AnimKey.parse("LEFT:LENGTHWAYS:90").format());
        assertEquals("LEFT:WALK:90", AnimKey.parse("LEFT:WALK:90").format());
        assertEquals("LEFT:LENGTHWAYS:WALK:90",
                AnimKey.parse("LEFT:LENGTHWAYS:WALK:90").format());
        assertEquals("LEFT:LENGTHWAYS", AnimKey.parse("left:lengthways").format());
    }

    @Test
    void angleSegmentIsDetectedByDigits() {
        assertTrue(AnimKey.isAngleSegment("90"));
        assertTrue(AnimKey.isAngleSegment("-90"));
        assertTrue(AnimKey.isAngleSegment("  270  "));
        assertFalse(AnimKey.isAngleSegment("WALK"));
        assertFalse(AnimKey.isAngleSegment("LENGTHWAYS"));
        assertFalse(AnimKey.isAngleSegment("9a"));
        assertFalse(AnimKey.isAngleSegment(""));
        assertFalse(AnimKey.isAngleSegment(null));
    }

    @Test
    void fourSegmentsWithNonNumericTailIsRejected() {
        // 早期实现在这里静默丢掉多余段，等于悄悄忽略配置里的错字
        assertThrows(IllegalArgumentException.class,
                () -> AnimKey.parse("LEFT:LENGTHWAYS:WALK:EXTRA"));
        assertThrows(IllegalArgumentException.class,
                () -> AnimKey.parse("LEFT:A:B:C:D"));
    }

    @Test
    void angleDistinguishesKeysFromEachOther() {
        assertNotEquals(AnimKey.parse("LEFT:90"), AnimKey.parse("LEFT:180"));
        assertNotEquals(AnimKey.parse("LEFT:LENGTHWAYS"), AnimKey.parse("LEFT:LENGTHWAYS:90"));
        assertEquals(AnimKey.parse("LEFT:90"), AnimKey.parse("left:090"));
    }
}
