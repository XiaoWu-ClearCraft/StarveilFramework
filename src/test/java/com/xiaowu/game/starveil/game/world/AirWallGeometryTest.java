package com.xiaowu.game.starveil.game.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 空气墙矩形归一化测试。
 *
 * <p>回归保护：旧实现 {@code Math.max(1, x_to - x)} 在反向书写对角点时
 * 会静默退化成 <b>1 像素</b>，空气墙失效且不报任何错。
 * 这类 bug 最容易在重构时重新长回来，所以这里逐种顺序都钉住。
 */
class AirWallGeometryTest {

    @Test
    void keepsForwardOrderedCorners() {
        AirWallGeometry.Rect r = AirWallGeometry.of(0, 0, 834, 300);
        assertEquals(0, r.x());
        assertEquals(0, r.y());
        assertEquals(834, r.width());
        assertEquals(300, r.height());
    }

    @Test
    void normalizesReversedVerticalCorner() {
        // wu-home.json 的真实条目 —— 旧实现在这里得到 height = 1
        AirWallGeometry.Rect r = AirWallGeometry.of(599, 810, 834, 736);
        assertEquals(599, r.x());
        assertEquals(736, r.y(), "y 应取较小值");
        assertEquals(235, r.width());
        assertEquals(74, r.height(), "旧实现 Math.max(1, -74) 会得到 1");
    }

    @Test
    void normalizesBothCornersReversed() {
        AirWallGeometry.Rect r = AirWallGeometry.of(300, 200, 100, 50);
        assertEquals(100, r.x());
        assertEquals(50, r.y());
        assertEquals(200, r.width());
        assertEquals(150, r.height());
    }

    @Test
    void cornerOrderDoesNotMatter() {
        AirWallGeometry.Rect forward = AirWallGeometry.of(599, 736, 834, 810);
        AirWallGeometry.Rect reversed = AirWallGeometry.of(834, 810, 599, 736);
        assertEquals(forward, reversed, "对角点的书写顺序不应影响结果");
    }

    @Test
    void degenerateCornerCollapsesToSinglePixel() {
        // 两个点重合：保持最小 1×1，不做成 0 尺寸（JavaFX 不接受负/零尺寸）
        AirWallGeometry.Rect r = AirWallGeometry.of(10, 20, 10, 20);
        assertEquals(10, r.x());
        assertEquals(20, r.y());
        assertEquals(1, r.width());
        assertEquals(1, r.height());
    }

    @Test
    void neverProducesNonPositiveSize() {
        // 穷举一小片坐标空间，确认宽高恒 >= 1 —— 旧实现只在反向时退化，
        // 这里保证任何输入顺序都不会产出非法尺寸
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                AirWallGeometry.Rect r = AirWallGeometry.of(a, b, b, a);
                assertEquals(true, r.width() >= 1 && r.height() >= 1,
                        "宽高必须 >= 1，实际 " + r.width() + "x" + r.height());
            }
        }
    }
}
