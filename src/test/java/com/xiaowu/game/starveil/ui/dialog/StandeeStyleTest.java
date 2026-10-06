package com.xiaowu.game.starveil.ui.dialog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 立绘样式（偏移 / 缩放）的算式。
 *
 * <p>图片渲染没法在单测里看，但「偏移多少、占多宽、对话框要不要让位」是纯数学，
 * 正是内容调半身立绘时最容易出错的地方，所以单独钉住。
 */
class StandeeStyleTest {

    /** 一张 800×1200 的全身立绘（宽高比 2:3）。 */
    private static final double IMG_W = 800;
    private static final double IMG_H = 1200;
    private static final double PANE_H = 1080;

    @Test
    void defaultStyleKeepsTheOldBehaviour() {
        StandeeStyle s = StandeeStyle.standard();
        assertEquals(0, s.offsetX());
        assertEquals(0, s.offsetY());
        assertEquals(1.0, s.scale());
        assertEquals(PANE_H * 0.68, s.fitHeight(PANE_H), 1e-9, "默认高度还是画布的 68%");
        // 800×1200 按 0.68 画布高显示 → 宽 = 800 * (734.4 / 1200)
        assertEquals(IMGW(PANE_H * 0.68), s.displayWidth(PANE_H, IMG_W, IMG_H), 1e-9);
    }

    @Test
    void offsetShiftsTheRightEdgeSoTheDialogMakesRoom() {
        // 只挪水平：右边界 = 偏移 + 显示宽度（对话框据此让位）
        StandeeStyle s = StandeeStyle.of(120, 0);
        double width = s.displayWidth(PANE_H, IMG_W, IMG_H);
        assertEquals(120 + width, s.rightEdge(PANE_H, IMG_W, IMG_H), 1e-9);

        // 挪到画布左边外面：右边界仍然可能 > 0（还有一部分露在画布内）
        StandeeStyle left = StandeeStyle.of(-width / 2, 0);
        assertEquals(width / 2, left.rightEdge(PANE_H, IMG_W, IMG_H), 1e-9);
    }

    @Test
    void pushingTheStandeeMostlyOutOfTheCanvasStopsReservingSpace() {
        double width = StandeeStyle.standard().displayWidth(PANE_H, IMG_W, IMG_H);
        StandeeStyle outside = StandeeStyle.of(-width - 50, 0);
        assertEquals(0, outside.rightEdge(PANE_H, IMG_W, IMG_H), 1e-9,
                "整张立绘都在画布外时，对话框不该再让位");
    }

    @Test
    void verticalOffsetDoesNotChangeTheReservedWidth() {
        double plain = StandeeStyle.standard().rightEdge(PANE_H, IMG_W, IMG_H);
        double pushedDown = StandeeStyle.of(0, 320).rightEdge(PANE_H, IMG_W, IMG_H);
        assertEquals(plain, pushedDown, 1e-9, "往下推（露半身）不影响对话框让位");
    }

    @Test
    void scaleGrowsBothTheHeightAndTheReservedWidth() {
        StandeeStyle s = StandeeStyle.of(0, 0, 1.5);
        assertEquals(PANE_H * 0.68 * 1.5, s.fitHeight(PANE_H), 1e-9);
        assertEquals(IMGW(PANE_H * 0.68 * 1.5), s.displayWidth(PANE_H, IMG_W, IMG_H), 1e-9);
    }

    @Test
    void aNonsenseScaleFallsBackToOne() {
        assertEquals(1.0, StandeeStyle.of(0, 0, 0).scale());
        assertEquals(1.0, StandeeStyle.of(0, 0, -2).scale());
        assertEquals(1.0, StandeeStyle.of(0, 0, Double.NaN).scale());
    }

    @Test
    void aBrokenImageMeasuresZeroInsteadOfBlowingUp() {
        StandeeStyle s = StandeeStyle.standard();
        assertEquals(0, s.displayWidth(PANE_H, 0, IMG_H));
        assertEquals(0, s.displayWidth(PANE_H, IMG_W, 0));
        assertTrue(s.rightEdge(PANE_H, 0, 0) >= 0);
    }

    /** 显示宽度：图片宽 × (显示高 / 图片高)。 */
    private static double IMGW(double fitHeight) {
        return IMG_W * (fitHeight / IMG_H);
    }
}
