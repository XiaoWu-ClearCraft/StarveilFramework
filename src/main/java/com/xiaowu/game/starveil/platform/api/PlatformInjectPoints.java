package com.xiaowu.game.starveil.platform.api;

/**
 * 平台注入点 ID 常量表。
 *
 * <p>本版本的游戏核心 <b>只支持 Windows</b>：所有平台相关能力都有一份 Windows 默认实现，
 * 并在此处登记为注入点。插件可以在 {@code onLoad} 中注册这些注入点，
 * 用 {@link com.xiaowu.game.starveil.plugin.InjectionTiming#REPLACE} 把某个能力
 * 换成其它平台的实现，从而让游戏在 Linux / macOS 等平台上运行。
 *
 * <p>典型用法（插件侧）：
 * <pre>{@code
 * context.registerInjection(PlatformInjectPoints.PLATFORM_SUPPORTED, InjectionTiming.REPLACE,
 *     (proxy, method, args) -> true);          // 声明「本插件已适配当前平台」
 *
 * context.registerInjection(PlatformInjectPoints.SYSTEM_MANAGER, InjectionTiming.REPLACE,
 *     (proxy, method, args) -> myLinuxSystemManager);  // 换成 Linux 的对话框实现
 * }</pre>
 *
 * <p>全部注入点在 {@code plugin-examples/linux-adapter} 中都有可运行的示例实现。
 *
 * <p><b>注意</b>：这些注入点必须在插件 {@code onLoad} 期间注册——核心会在所有插件
 * 加载完成后统一应用。此时 Windows 默认实现早已完成类加载，注入只能走 JVM 的
 * retransform 通道改写方法体，因此只支持
 * {@link com.xiaowu.game.starveil.plugin.InjectionTiming#REPLACE}。
 */
public final class PlatformInjectPoints {

    private PlatformInjectPoints() {}

    // ==================== 平台声明 ====================

    /**
     * {@code SystemDetector#isPlatformSupported()}。
     * 返回 {@code boolean}：当前平台是否已被适配（Windows 默认返回 true）。
     * 未适配且未在启动检查前被覆盖时，游戏会提示并退出。
     */
    public static final String PLATFORM_SUPPORTED = "platform.os.supported";

    // ==================== 系统对话框 / 窗口抖动 ====================

    /**
     * {@code SystemManagerFactory#getInstance()}。
     * 返回一个 {@link SystemManagerInterface}，用于消息框、确认框与窗口抖动。
     */
    public static final String SYSTEM_MANAGER = "platform.system.manager";

    // ==================== 硬件信息 ====================

    /** {@code HardwareManager#getSystemInfo()} → {@code String} */
    public static final String HARDWARE_SYSTEM_INFO = "platform.hardware.system_info";
    /** {@code HardwareManager#getMotherboardInfo()} → {@code String} */
    public static final String HARDWARE_MOTHERBOARD_INFO = "platform.hardware.motherboard_info";
    /** {@code HardwareManager#getMemoryInfo()} → {@code String} */
    public static final String HARDWARE_MEMORY_INFO = "platform.hardware.memory_info";
    /** {@code HardwareManager#getCPUInfo()} → {@code String} */
    public static final String HARDWARE_CPU_INFO = "platform.hardware.cpu_info";
    /** {@code HardwareManager#getGPUInfo()} → {@code String} */
    public static final String HARDWARE_GPU_INFO = "platform.hardware.gpu_info";
    /** {@code HardwareManager#getSoundCardInfo()} → {@code String} */
    public static final String HARDWARE_SOUND_CARD_INFO = "platform.hardware.sound_card_info";
    /** {@code HardwareManager#getNetworkInfo()} → {@code String} */
    public static final String HARDWARE_NETWORK_INFO = "platform.hardware.network_info";
    /** {@code HardwareManager#getAllHardwareInfo()} → {@code String} */
    public static final String HARDWARE_ALL_INFO = "platform.hardware.all_info";
    /** {@code HardwareManager#hasDedicatedGPU()} → {@code boolean} */
    public static final String HARDWARE_HAS_DEDICATED_GPU = "platform.hardware.has_dedicated_gpu";

    // ==================== 其它平台能力 ====================

    /**
     * {@code DialogCleanup#closeAllDialogs(String)}。
     * 关闭由系统对话框工具拉起的原生窗口，参数为线程名前缀。
     */
    public static final String DIALOG_CLEANUP = "platform.dialog.cleanup";

    /**
     * {@code ConsoleProviderFactory#getInstance()}。
     * 返回一个 {@link com.xiaowu.game.starveil.platform.console.ConsoleProvider}。
     */
    public static final String CONSOLE_PROVIDER = "platform.console.provider";

    /**
     * {@code GameConstants#setStarveilDirHidden()}。
     * 隐藏用户目录下的 {@code .starveil} 数据目录（Windows 使用 dos:hidden 属性）。
     */
    public static final String HIDE_DATA_DIR = "platform.fs.hide_data_dir";

    /**
     * {@code AppEntry#checkAndOptimizeGPU()}。
     * 独显优化：Windows 上提示用户在系统设置里指定高性能 GPU；
     * 其它平台可以在插件里换成 prime-run / switcheroo 等方案。
     */
    public static final String GPU_OPTIMIZE = "platform.gpu.optimize";

    /**
     * {@code AppEntry#requestAdminIfNeeded(boolean)}。
     * 请求管理员 / root 权限，返回 {@code boolean}：是否已获得权限（或不需要权限）。
     */
    public static final String REQUEST_ADMIN = "platform.permission.request_admin";

    /**
     * {@code ScreenEffectsManager#applyPlatformWindowSetup()}。
     * 全屏特效覆盖层的平台化窗口属性（置顶、跳过任务栏、输入穿透等）。
     */
    public static final String OVERLAY_WINDOW_SETUP = "platform.window.overlay_setup";

    /**
     * {@code BlueScreenEffect#activateWindowForBlueScreen(javafx.stage.Stage)}。
     * 进入伪蓝屏时取消鼠标穿透并抢占焦点。
     */
    public static final String BLUE_SCREEN_ACTIVATE = "platform.window.blue_screen_activate";

    /**
     * {@code BlueScreenEffect#restoreWindowAfterBlueScreen(javafx.stage.Stage)}。
     * 退出伪蓝屏时恢复鼠标穿透与置顶。
     */
    public static final String BLUE_SCREEN_RESTORE = "platform.window.blue_screen_restore";

    /**
     * {@code DebugWindowTheme#isSystemDark()}。
     * 返回 {@code boolean}：系统当前是否处于暗色主题。
     */
    public static final String SYSTEM_DARK_MODE = "platform.theme.system_dark";
}
