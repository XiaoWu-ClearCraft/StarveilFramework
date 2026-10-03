package com.xiaowu.game.starveil.plugin;

/**
 * Starveil 插件接口
 * 所有插件必须实现此接口
 *
 * 插件的 id / name / version 优先从代码获取（getId/getName/getVersion），
 * 如果代码未提供（返回 null），则 fallback 到 plugin.yml 中的声明。
 * 这样插件可以选择在代码中动态生成这些信息，也可以完全依赖 plugin.yml。
 */
public interface StarveilPlugin {

    /**
     * 插件加载时调用（必须实现）
     * @param context 插件上下文，提供游戏服务和字节码注入能力
     */
    void onLoad(PluginContext context);

    /**
     * 游戏关闭时调用，用于释放资源（必须实现）
     */
    void onUnload();

    /**
     * 插件唯一标识符（可选实现，返回 null 时使用 plugin.yml 中的 id）
     */
    default String getId() {
        return null;
    }

    /**
     * 插件显示名称（可选实现，返回 null 时使用 plugin.yml 中的 name）
     */
    default String getName() {
        return null;
    }

    /**
     * 插件版本号（可选实现，返回 null 时使用 plugin.yml 中的 version）
     */
    default String getVersion() {
        return null;
    }

    /**
     * 插件图标路径（可选实现，返回 null 时使用 plugin.yml 中的 icon）
     * 支持网络 URL（http/https）或 JAR 内资源路径（以 / 开头）
     */
    default String getIcon() {
        return null;
    }
}
