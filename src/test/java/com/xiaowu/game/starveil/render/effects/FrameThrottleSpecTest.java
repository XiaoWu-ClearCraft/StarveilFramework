package com.xiaowu.game.starveil.render.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 降帧参数解析测试。
 *
 * <p>重点验证「只允许降低帧数，不允许提升」这条硬规则，
 * 以及未提供/非法参数时按 WARNING 忽略而不是悄悄生效。
 */
class FrameThrottleSpecTest {

    private static final double MEASURED = 60.0;

    // ==================== 解析 ====================

    @Test
    void disabledWhenMissing() {
        assertDisabled(FrameThrottleSpec.parse(null));
        assertDisabled(FrameThrottleSpec.parse(""));
        assertDisabled(FrameThrottleSpec.parse("   "));
    }

    @Test
    void disabledOnExplicitOffSwitch() {
        assertDisabled(FrameThrottleSpec.parse("off"));
        assertDisabled(FrameThrottleSpec.parse("NONE"));
        assertDisabled(FrameThrottleSpec.parse("false"));
        assertDisabled(FrameThrottleSpec.parse("disable"));
    }

    @Test
    void parsesPercentage() {
        FrameThrottleSpec spec = FrameThrottleSpec.parse("50%");
        assertTrue(spec.isEnabled());
        assertTrue(spec.isPercent());
        assertEquals(50.0, spec.rawValue());
        assertNull(spec.problem(), "启用状态下不应带失败原因");
    }

    @Test
    void parsesAbsoluteFps() {
        FrameThrottleSpec spec = FrameThrottleSpec.parse("30");
        assertTrue(spec.isEnabled());
        assertFalse(spec.isPercent());
        assertEquals(30.0, spec.rawValue());
    }

    @Test
    void rejectsGarbage() {
        assertDisabled(FrameThrottleSpec.parse("abc"));
        assertDisabled(FrameThrottleSpec.parse("30fps"));
        assertDisabled(FrameThrottleSpec.parse("%"));
        assertDisabled(FrameThrottleSpec.parse("0"));
        assertDisabled(FrameThrottleSpec.parse("-30"));
        assertDisabled(FrameThrottleSpec.parse("0.5"));
    }

    @Test
    void rejectsPercentageAtOrAbove100() {
        // 100% 是不升不降，>100% 属于提升帧数，一律拒绝
        assertDisabled(FrameThrottleSpec.parse("100%"));
        assertDisabled(FrameThrottleSpec.parse("150%"));
    }

    // ==================== 解析 -> 生效帧率 ====================

    @Test
    void percentageResolvesAgainstMeasuredFps() {
        assertEquals(30.0, FrameThrottleSpec.parse("50%").resolve(MEASURED), 0.001);
        assertEquals(18.0, FrameThrottleSpec.parse("30%").resolve(MEASURED), 0.001);
        assertEquals(6.0, FrameThrottleSpec.parse("10%").resolve(MEASURED), 0.001);
    }

    @Test
    void absoluteFpsIsUsedAsIs() {
        assertEquals(30.0, FrameThrottleSpec.parse("30").resolve(MEASURED), 0.001);
        assertEquals(10.0, FrameThrottleSpec.parse("10").resolve(MEASURED), 0.001);
    }

    @Test
    void neverRaisesFrameRate() {
        // 实测 60fps 时请求 90fps 会「提升帧数」，必须拒绝
        assertEquals(-1, FrameThrottleSpec.parse("90").resolve(MEASURED), 0.001);
        // 等于实测帧率同样没有降帧意义，也拒绝
        assertEquals(-1, FrameThrottleSpec.parse("60").resolve(MEASURED), 0.001);
        // 实测只有 24fps 时，请求 30fps 同样拒绝
        assertEquals(-1, FrameThrottleSpec.parse("30").resolve(24.0), 0.001);
        // 但请求 15fps 是合法降帧
        assertEquals(15.0, FrameThrottleSpec.parse("15").resolve(24.0), 0.001);
    }

    @Test
    void clampsToMinimumFps() {
        // 1% of 60 = 0.6 -> 抬到最小可用帧率
        assertEquals(FrameThrottleSpec.MIN_FPS,
                FrameThrottleSpec.parse("1%").resolve(MEASURED), 0.001);
    }

    @Test
    void disabledSpecNeverResolves() {
        assertEquals(-1, FrameThrottleSpec.parse(null).resolve(MEASURED), 0.001);
        assertEquals(-1, FrameThrottleSpec.parse("150%").resolve(MEASURED), 0.001);
    }

    @Test
    void measuredFpsFallsBackWhenUnknown() {
        // 实测值不可用时按 60fps 估算，避免除零/放大
        assertEquals(30.0, FrameThrottleSpec.parse("50%").resolve(0), 0.001);
        assertEquals(30.0, FrameThrottleSpec.parse("50%").resolve(-1), 0.001);
    }

    private static void assertDisabled(FrameThrottleSpec spec) {
        assertFalse(spec.isEnabled(), "应被判定为未启用: " + spec);
        assertNotNull(spec.problem(), "未启用时必须给出原因供 WARNING 日志使用");
    }
}
