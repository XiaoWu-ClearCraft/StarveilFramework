package com.xiaowu.game.starveil.plugin;

import net.bytebuddy.ByteBuddy;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.nio.file.Path;

import javafx.scene.image.Image;

/**
 * 插件上下文接口
 * 提供插件访问游戏服务和字节码注入的能力
 */
public interface PluginContext {

    /**
     * 输出信息级别日志（自动带插件标识前缀）
     */
    void logInfo(String message);

    /**
     * 输出警告级别日志（自动带插件标识前缀）
     */
    void logWarning(String message);

    /**
     * 输出错误级别日志（自动带插件标识前缀）
     */
    void logError(String message);

    /**
     * 获取插件 ID（来自 plugin.yml）
     */
    String getPluginId();

    /**
     * 获取插件名称（来自 plugin.yml）
     */
    String getPluginName();

    /**
     * 获取插件版本（来自 plugin.yml）
     */
    String getPluginVersion();

    /**
     * 获取插件图标（来自 plugin.yml 或插件实现）
     * @return 图标 Image 对象，如果没有图标则返回 null
     */
    Image getPluginIcon();

    /**
     * 获取插件目录路径
     */
    Path getPluginsDirectory();

    /**
     * 获取 ByteBuddy 实例（用于自由字节码替换）
     */
    ByteBuddy getByteBuddy();

    /**
     * 获取 Instrumentation 实例（用于自由字节码替换）
     */
    Instrumentation getInstrumentation();

    /**
     * 注册注入点回调（推荐用法）。
     *
     * <p>必须在插件 {@code onLoad} 期间调用——核心会在所有插件加载完成后统一应用注入点。
     *
     * <p>目前仅支持 {@link InjectionTiming#REPLACE}：把标注了
     * {@link InjectPoint} 的游戏方法整体替换为你的实现。
     * 这是平台适配插件接管核心 Windows 实现的入口，
     * 可用的注入点 ID 见
     * {@link com.xiaowu.game.starveil.platform.api.PlatformInjectPoints}。
     *
     * @param pointId 注入点 ID
     * @param timing  注入时机（当前只支持 REPLACE）
     * @param handler 回调处理器；{@code invoke(proxy, method, args)} 的 {@code proxy}
     *                是目标实例（静态方法为 {@code null}），{@code method} 是目标方法
     */
    void registerInjection(String pointId, InjectionTiming timing, InvocationHandler handler);
}
