package com.xiaowu.game.starveil.platform.common;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;

/**
 * 平台支持校验。
 *
 * <p>本版本的游戏核心只支持 Windows。校验必须发生在 <b>插件加载之后</b>：
 * 平台适配插件会在自己的 {@code onLoad} 中注册
 * {@link com.xiaowu.game.starveil.platform.api.PlatformInjectPoints#PLATFORM_SUPPORTED}
 * 注入点，声明它已经接管了当前平台；此时校验才会通过。
 */
public final class PlatformSupport {

    private PlatformSupport() {}

    /**
     * 当前平台是否可用（Windows，或被平台适配插件接管）。
     */
    public static boolean isSupported() {
        return SystemDetector.isPlatformSupported();
    }

    public static boolean isWindows() {
        return SystemDetector.isWindows();
    }

    /**
     * 显示「平台不受支持」的提示。调用方负责在需要时终止进程。
     */
    public static void showUnsupportedMessage() {
        String platform = SystemDetector.getPlatformDisplayName();
        LoggerManager.Logger("ERROR", "当前平台不受支持: " + platform);

        String message = "当前操作系统不受支持：" + platform + "\n\n"
                + "本版本的游戏核心仅支持 Windows。\n"
                + "如需在其它平台运行，请安装对应的「平台适配插件」：\n"
                + "  1. 获取适配插件 JAR（官方示例见 plugin-examples/linux-adapter）\n"
                + "  2. 将 JAR 放入用户目录下的 .starveil/plugins 文件夹\n"
                + "  3. 重新启动游戏\n\n"
                + "适配插件会通过注入点接管对话框、窗口、硬件信息、音频等平台能力。";

        try {
            SystemManagerFactory.getInstance().showError("不支持的平台", message);
        } catch (Throwable t) {
            // 连错误对话框都弹不出来时，至少把完整提示写进日志 ——
            // 用户在控制台看到的也是同一份内容（LoggerManager 接管了 System.out）
            LoggerManager.Logger("ERROR", "无法显示不支持的平台提示: " + t + "\n" + message);
        }
    }
}
