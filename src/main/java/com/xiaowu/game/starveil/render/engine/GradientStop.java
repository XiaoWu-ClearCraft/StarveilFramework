package com.xiaowu.game.starveil.render.engine;

import java.util.Arrays;
import java.util.List;

/**
 * 渐变停止点
 */
public class GradientStop {
    public final double offset;
    public final ColorDef color;

    public GradientStop(double offset, ColorDef color) {
        this.offset = offset;
        this.color = color;
    }

    /**
     * 从 JavaFX Stop 风格创建
     */
    public static GradientStop of(double offset, ColorDef color) {
        return new GradientStop(offset, color);
    }

    /**
     * 创建渐变停止点列表
     */
    @SafeVarargs
    public static List<GradientStop> of(GradientStop... stops) {
        return Arrays.asList(stops);
    }
}
