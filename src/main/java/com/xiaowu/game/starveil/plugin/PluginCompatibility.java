package com.xiaowu.game.starveil.plugin;

/**
 * 插件与框架的版本兼容判定。
 *
 * <p>框架有自己独立的版本号（见 {@code GameConstants.FRAMEWORK_VERSION}），
 * 插件可以在 {@code plugin.yml} 里用 {@code framework-version} 声明自己适配的版本，
 * 以便「单独适配某些特定版本」。
 *
 * <p>判定规则：
 * <ul>
 *   <li>未声明、空串或 {@code *} → 兼容（不挑版本）；</li>
 *   <li>完全相同 → 兼容；</li>
 *   <li><b>主版本号相同</b> → 兼容（1.2.0 的插件可在 1.9.3 上跑）；</li>
 *   <li>否则 → 不兼容。</li>
 * </ul>
 *
 * <p>只比主版本号是刻意的：要求逐位相同会让每次发版都作废所有插件，
 * 而完全不管又会让跨大版本的插件带着失效的注入点静默跑起来。
 */
public final class PluginCompatibility {

    /** 声明「不挑版本」。 */
    public static final String ANY = "*";

    private PluginCompatibility() {
    }

    /** 插件声明的框架版本与当前框架是否兼容。 */
    public static boolean isCompatible(String declared, String current) {
        String d = normalize(declared);
        if (d == null || ANY.equals(d)) {
            return true;
        }
        String c = normalize(current);
        if (c == null) {
            return true;
        }
        if (d.equals(c)) {
            return true;
        }
        return majorOf(d).equals(majorOf(c));
    }

    /**
     * 取主版本号：{@code "v1.2.3" → "1"}。
     *
     * <p>没有点号时整串就是主版本；完全没有数字时按原样比较
     * （这样 {@code "beta"} 只与 {@code "beta"} 兼容，不会意外放行）。
     */
    public static String majorOf(String version) {
        String v = normalize(version);
        if (v == null) {
            return "";
        }
        int dot = v.indexOf('.');
        return dot < 0 ? v : v.substring(0, dot);
    }

    private static String normalize(String version) {
        if (version == null) {
            return null;
        }
        String v = version.trim();
        if (v.isEmpty()) {
            return null;
        }
        // 容忍 v1.0.0 这种写法
        if (v.charAt(0) == 'v' || v.charAt(0) == 'V') {
            v = v.substring(1).trim();
        }
        return v.isEmpty() ? null : v;
    }

    /** 供日志使用的不兼容说明。 */
    public static String describeMismatch(String pluginName, String declared, String current) {
        return "插件 " + pluginName + " 声明适配框架版本 " + declared
                + "，当前框架版本为 " + current + "（主版本不一致）";
    }
}
