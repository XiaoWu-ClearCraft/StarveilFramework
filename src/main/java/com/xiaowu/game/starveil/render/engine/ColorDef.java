package com.xiaowu.game.starveil.render.engine;

/**
 * 颜色定义类 - 抽象颜色表示,不依赖具体渲染引擎
 */
public class ColorDef {
    private final String value;
    private final boolean isHex;

    private ColorDef(String value, boolean isHex) {
        this.value = value;
        this.isHex = isHex;
    }

    /**
     * 从十六进制颜色创建
     */
    public static ColorDef hex(String hex) {
        return new ColorDef(hex, true);
    }

    /**
     * 从颜色名称创建
     */
    public static ColorDef name(String name) {
        return new ColorDef(name, false);
    }

    /**
     * 从 RGB 创建
     */
    public static ColorDef rgb(int r, int g, int b) {
        return hex(String.format("#%02X%02X%02X", r, g, b));
    }

    /**
     * 从 RGBA 创建
     */
    public static ColorDef rgba(int r, int g, int b, double alpha) {
        int a = (int) (alpha * 255);
        return hex(String.format("#%02X%02X%02X%02X", r, g, b, a));
    }

    /**
     * 获取颜色值
     */
    public String getValue() {
        return value;
    }

    /**
     * 是否为十六进制颜色
     */
    public boolean isHex() {
        return isHex;
    }

    // ==================== 预设颜色 ====================

    public static final ColorDef WHITE = name("white");
    public static final ColorDef BLACK = name("black");
    public static final ColorDef RED = name("red");
    public static final ColorDef GREEN = name("green");
    public static final ColorDef BLUE = name("blue");
    public static final ColorDef YELLOW = name("yellow");
    public static final ColorDef CYAN = name("cyan");
    public static final ColorDef PINK = name("pink");
    public static final ColorDef ORANGE = name("orange");
    public static final ColorDef PURPLE = name("purple");
    public static final ColorDef TRANSPARENT = name("transparent");

    /**
     * 获取较暗的颜色版本
     */
    public ColorDef darker() {
        if (isHex && value.startsWith("#") && value.length() >= 7) {
            try {
                int r = Integer.parseInt(value.substring(1, 3), 16);
                int g = Integer.parseInt(value.substring(3, 5), 16);
                int b = Integer.parseInt(value.substring(5, 7), 16);
                double factor = 0.7;
                r = (int) (r * factor);
                g = (int) (g * factor);
                b = (int) (b * factor);
                return rgb(r, g, b);
            } catch (NumberFormatException e) {
                return this;
            }
        }
        return this;
    }

    /**
     * 转换为渲染引擎特定的颜色对象
     * 由具体渲染引擎实现
     */
    public Object toNativeColor() {
        return value;
    }
}
