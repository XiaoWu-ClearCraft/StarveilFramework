package com.xiaowu.game.starveil.plugin;

/**
 * 插件加载时机。
 *
 * <p>不同时机会被放在 AppEntry 启动流程的不同位置，目的是让插件能在
 * 目标代码<b>首次加载之前</b>完成字节码注入：
 *
 * <ul>
 *   <li>{@link #LAUNCHER}（默认）—— 在 AppEntry 主要流程中、
 *       {@code Launcher} 类首次加载<b>之前</b>。此时
 *       {@code content.init.init} 已经跑完，插件可以正常读取它写入 DataManager 的配置项。
 *       平台适配插件应当选这个，因为平台支持校验发生在插件加载之后。</li>
 *   <li>{@link #INIT} —— 在 {@code com.xiaowu.game.starveil.content.init.init}
 *       执行<b>之前</b>，用于注入 init。这类插件风险更高：
 *       init 由非框架的开发者定义，不同游戏 / 不同版本可能完全不同。</li>
 * </ul>
 */
public enum PluginLoadTiming {

    /** 在 Launcher 首次加载之前（默认）。 */
    LAUNCHER,

    /** 在 content.init.init 执行之前，用于注入 init。 */
    INIT;

    /** 解析时机名（忽略大小写，容忍 {@code load-at} 这类键值写法）。 */
    public static PluginLoadTiming fromName(String name) {
        if (name == null) {
            return LAUNCHER;
        }
        String n = name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        return switch (n) {
            case "INIT", "PRE_INIT", "BEFORE_INIT" -> INIT;
            default -> LAUNCHER;
        };
    }

    /** 声明的文本是否是合法时机名（用于给配置写错时报警）。 */
    public static boolean isValidName(String name) {
        if (name == null) return false;
        String n = name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        return n.equals("LAUNCHER") || n.equals("INIT")
                || n.equals("PRE_INIT") || n.equals("BEFORE_INIT");
    }
}
