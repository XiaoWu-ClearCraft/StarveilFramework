package com.xiaowu.game.starveil.platform.console;

/**
 * 跨平台控制台颜色定义
 * 替代原 ConsoleUI.Color，提供统一的颜色抽象
 */
public class ConsoleColor {
    public final boolean red;
    public final boolean green;
    public final boolean blue;

    public static final ConsoleColor BLACK   = new ConsoleColor(false, false, false);
    public static final ConsoleColor RED     = new ConsoleColor(true,  false, false);
    public static final ConsoleColor GREEN   = new ConsoleColor(false, true,  false);
    public static final ConsoleColor BLUE    = new ConsoleColor(false, false, true);
    public static final ConsoleColor YELLOW  = new ConsoleColor(true,  true,  false);
    public static final ConsoleColor MAGENTA = new ConsoleColor(true,  false, true);
    public static final ConsoleColor CYAN    = new ConsoleColor(false, true,  true);
    public static final ConsoleColor WHITE   = new ConsoleColor(true,  true,  true);

    public ConsoleColor(boolean red, boolean green, boolean blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    /**
     * 获取 ANSI 前景色代码
     */
    public String toAnsiForeground() {
        if (!red && !green && !blue) return "\033[30m";  // black
        if (red  && !green && !blue) return "\033[31m";  // red
        if (!red && green  && !blue) return "\033[32m";  // green
        if (red  && green  && !blue) return "\033[33m";  // yellow
        if (!red && !green && blue)  return "\033[34m";  // blue
        if (red  && !green && blue)  return "\033[35m";  // magenta
        if (!red && green  && blue)  return "\033[36m";  // cyan
        return "\033[37m";                                // white
    }

    /**
     * 获取 ANSI 背景色代码
     */
    public String toAnsiBackground() {
        if (!red && !green && !blue) return "\033[40m";
        if (red  && !green && !blue) return "\033[41m";
        if (!red && green  && !blue) return "\033[42m";
        if (red  && green  && !blue) return "\033[43m";
        if (!red && !green && blue)  return "\033[44m";
        if (red  && !green && blue)  return "\033[45m";
        if (!red && green  && blue)  return "\033[46m";
        return "\033[47m";
    }

    /**
     * 获取 Windows 控制台前景色属性
     */
    public short toWindowsForeground() {
        return (short) ((red ? 0x0004 : 0) | (green ? 0x0002 : 0) | (blue ? 0x0001 : 0));
    }

    /**
     * 获取 Windows 控制台背景色属性
     */
    public short toWindowsBackground() {
        return (short) ((red ? 0x0040 : 0) | (green ? 0x0020 : 0) | (blue ? 0x0010 : 0));
    }
}
