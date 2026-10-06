package com.xiaowu.game.starveil.ui.dialog;

/**
 * 立绘的显示样式：偏移 + 缩放。
 *
 * <p>坐标系是<b>逻辑画布</b>（1920×1080）：X 向右为正、Y 向下为正。
 * 立绘默认贴着画布左下角、高度为画布高度的 {@value #DEFAULT_HEIGHT_RATIO}，
 * 对应的是全身立绘。想显示<b>半身</b>就往下挪（{@code offsetY} 为正）让下半身落到画布外 ——
 * 逻辑画布自带裁剪，超出的部分不会被画出来；嫌人物小就同时放大 {@code scale}：
 *
 * <pre>{@code
 * StandeeStyle.of(0, 260)        // 只露上半身
 * StandeeStyle.of(0, 320, 1.4)   // 半身 + 放大 40%
 * StandeeStyle.standard()        // 回到默认（全身、贴左下角）
 * }</pre>
 *
 * <p>样式是<b>粘性</b>的：设过一次就一直生效，之后只换立绘路径不会把它重置回默认。
 *
 * @param offsetX 水平偏移（逻辑像素，向右为正）
 * @param offsetY 垂直偏移（逻辑像素，向下为正）
 * @param scale   缩放，1.0 = 默认大小；非正数一律当 1.0
 */
public record StandeeStyle(double offsetX, double offsetY, double scale) {

    /** 默认立绘高度 = 画布高度的这个比例。 */
    static final double DEFAULT_HEIGHT_RATIO = 0.68;

    public StandeeStyle {
        if (scale <= 0 || Double.isNaN(scale)) {
            scale = 1.0;
        }
    }

    /** 默认样式：不偏移、不缩放（= 老行为）。 */
    public static StandeeStyle standard() {
        return new StandeeStyle(0, 0, 1.0);
    }

    /** 只给偏移，缩放用默认。 */
    public static StandeeStyle of(double offsetX, double offsetY) {
        return new StandeeStyle(offsetX, offsetY, 1.0);
    }

    /** 偏移 + 缩放。 */
    public static StandeeStyle of(double offsetX, double offsetY, double scale) {
        return new StandeeStyle(offsetX, offsetY, scale);
    }

    /** 立绘在画布上的显示高度。 */
    double fitHeight(double paneH) {
        return paneH * DEFAULT_HEIGHT_RATIO * scale;
    }

    /** 立绘在画布上的显示宽度（按图片原始比例）。 */
    double displayWidth(double paneH, double imageWidth, double imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            return 0;
        }
        return imageWidth * (fitHeight(paneH) / imageHeight);
    }

    /**
     * 立绘实际占到的右边界（供对话框让位）。
     *
     * <p>立绘被挪到画布左边外面时返回 0：那时候它不占地方，对话框不必让位。
     */
    double rightEdge(double paneH, double imageWidth, double imageHeight) {
        return Math.max(0, offsetX + displayWidth(paneH, imageWidth, imageHeight));
    }
}
