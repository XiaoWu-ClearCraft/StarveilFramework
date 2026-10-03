package com.xiaowu.game.starveil.platform.common;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.platform.api.PlatformInjectPoints;
import com.xiaowu.game.starveil.plugin.InjectPoint;

/**
 * 操作系统检测。
 *
 * <p>本版本游戏核心 <b>只支持 Windows</b>。这里保留完整的操作系统识别能力
 * （包括 Linux / macOS），是为了让平台适配插件能够判断自己该不该接管，
 * 而不是因为核心还会走其它平台的分支。
 *
 * <p>判定「当前平台是否被支持」的唯一入口是 {@link #isPlatformSupported()}，
 * 它同时是一个注入点：适配插件把它替换成返回 {@code true} 即可让核心继续启动。
 */
public class SystemDetector {

    public enum OSType {
        WINDOWS,
        LINUX,
        MACOS,
        UNKNOWN
    }

    private static final OSType CURRENT_OS = detectOperatingSystem();

    static {
        LoggerManager.Logger("INFO", "检测到操作系统: " + CURRENT_OS
                + " (" + System.getProperty("os.name") + " " + System.getProperty("os.version") + ")");
    }

    private SystemDetector() {}

    public static OSType getOSType() {
        return CURRENT_OS;
    }

    public static boolean isWindows() {
        return CURRENT_OS == OSType.WINDOWS;
    }

    public static boolean isLinux() {
        return CURRENT_OS == OSType.LINUX;
    }

    public static boolean isMacOS() {
        return CURRENT_OS == OSType.MACOS;
    }

    /**
     * 当前平台是否被支持。
     *
     * <p>核心实现只认 Windows。平台适配插件应当在 {@code onLoad} 中注册
     * {@link PlatformInjectPoints#PLATFORM_SUPPORTED} 的 REPLACE 注入点并返回 {@code true}，
     * 表示它已经接管了当前平台的对话框、窗口、音频等能力。
     * 该检查发生在插件加载之后（{@code AppEntry}），因此插件有机会在此之前完成注入。
     */
    @InjectPoint(PlatformInjectPoints.PLATFORM_SUPPORTED)
    public static boolean isPlatformSupported() {
        return CURRENT_OS == OSType.WINDOWS;
    }

    /**
     * 人类可读的平台名称，用于提示信息。
     */
    public static String getPlatformDisplayName() {
        return switch (CURRENT_OS) {
            case WINDOWS -> "Windows";
            case LINUX -> "Linux";
            case MACOS -> "macOS";
            case UNKNOWN -> System.getProperty("os.name", "未知系统");
        };
    }

    private static OSType detectOperatingSystem() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            return OSType.WINDOWS;
        }
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return OSType.LINUX;
        }
        if (osName.contains("mac")) {
            return OSType.MACOS;
        }
        return OSType.UNKNOWN;
    }
}
