package com.xiaowu.game.starveil.launcher;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.config.LauncherConfig;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.platform.api.PlatformInjectPoints;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;
import com.xiaowu.game.starveil.platform.common.HardwareManager;
import com.xiaowu.game.starveil.platform.common.PlatformSupport;
import com.xiaowu.game.starveil.platform.windows.WindowsPermissionHandler;
import com.xiaowu.game.starveil.plugin.InjectPoint;
import com.xiaowu.game.starveil.startup.GameContentInit;
import com.xiaowu.game.starveil.startup.StartupHandlerRegistry;
import com.xiaowu.game.starveil.startup.handlers.CmdTest;
import com.xiaowu.game.starveil.startup.handlers.DebugHandler;
import com.xiaowu.game.starveil.startup.handlers.NoAdminHandler;
import com.xiaowu.game.starveil.startup.handlers.TestCompatWarningHandler;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 应用程序入口点。
 *
 * <p>启动顺序（本版本核心只支持 Windows，其它平台依赖插件注入适配）：
 * <ol>
 *   <li>基础日志 / 启动参数；</li>
 *   <li>{@code DataManager} 初始化；</li>
 *   <li><b>插件加载</b>——平台适配插件在这里注册注入点；</li>
 *   <li>平台支持校验（插件可以把 {@code platform.os.supported} 注入点替换为 true）；</li>
 *   <li>其余平台相关步骤（提权、锁文件、硬件日志、独显优化）——此时注入点均已生效；</li>
 *   <li>反射加载 {@code Launcher}，使插件对 Launcher 的字节码转换同样生效。</li>
 * </ol>
 */
public class AppEntry {
    private static FileLock lock = null;
    private static FileChannel lockChannel = null;
    private static Boolean IllegallyShutdown = false;

    private static final String LOCK_FILE_PATH = GameConstants.LOCK_FILE_PATH;

    public static void main(String[] args) {
        try {
            // 确保 LoggerManager 最先初始化
            LoggerManager.initializeLogger();
            Logger("INFO", "=== 程序启动 (AppEntry) ===");
            Logger("INFO", "操作系统: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            Logger("INFO", "Java 版本: " + System.getProperty("java.version"));
            Logger("INFO", "Starveil 框架版本: " + GameConstants.FRAMEWORK_VERSION
                    + "（框架版本与游戏内容版本相互独立）");

            // 初始化启动参数处理器
            initializeStartupHandlers();

            // 处理启动参数（优先级最高）
            StartupHandlerRegistry registry = StartupHandlerRegistry.getInstance();
            boolean shouldOverride = registry.processArguments(args);

            if (shouldOverride) {
                return;
            }

            boolean debug = LauncherConfig.getInstance().isDebugMode();
            boolean needAdmin = LauncherConfig.getInstance().isNeedAdmin();

            // 框架本身不是一款可运行的游戏。没有 content 包时章节、地图、物品、
            // 成就全是空的 —— 与其让玩家对着一个玩不了的空壳猜测，
            // 不如在这里明确失败并说明「这是框架，缺少内容」。
            GameContentInit.requireContent();

            // ==================== 内容初始化（非框架行为） ====================
            // 启动顺序在这里刻意分成两段，因为插件需要在目标代码【首次加载之前】
            // 装好字节码转换器：
            //   1) 先加载 INIT 时机的插件 —— 它们注入 content.init.init；
            //   2) 再执行 content.init.init；
            //   3) 之后才加载 LAUNCHER 时机的插件 —— 此时 init 写入 DataManager 的
            //      配置项已经可读，插件也能在 Launcher 首次加载前完成注入。
            //
            // 先探测类是否存在（initialize=false，不触发类初始化），
            // 这样插件加载流程能据此提前判断 INIT 插件是否无从生效。
            boolean hasContentInit = GameContentInit.exists();
            Logger("INFO", "内容初始化入口 " + GameContentInit.CLASS_NAME
                    + (hasContentInit ? " 存在" : " 不存在"));

            com.xiaowu.game.starveil.plugin.PluginLoader.initializeAndLoadPlugins(
                    com.xiaowu.game.starveil.plugin.PluginLoadTiming.INIT);

            if (hasContentInit) {
                GameContentInit.run();
            }

            // ⚠ 必须在内容初始化【之后】才 initialize：
            //   内容会通过 ContentConfig.setDataCryptoKey() 指定数据文件密钥，
            //   而 initialize() 在文件不存在时会立刻写出一个 game.dat。
            //   顺序颠倒的话，这个新文件是用【框架默认密钥】加密的，
            //   之后就再也读不回来（表现为每次启动都报解密失败），
            //   而玩家什么都没做错。
            com.xiaowu.game.starveil.infrastructure.persistence.DataManager.initialize();

            // 可信时间：先把上次联网同步得到的校正量读回来。这样即使这次还没联网、
            // 甚至整局都不联网，游戏拿到的也不是玩家可以随手改的本机时钟。
            com.xiaowu.game.starveil.infrastructure.net.TrustedTime.loadFromConfig();

            // 界面语言：同样必须在内容初始化之后 —— 内容是通过
            // ContentConfig.addLanguage() 才登记可选语言的，早于那一步就
            // 无从校验存下来的偏好是否仍然可用。
            com.xiaowu.game.starveil.infrastructure.i18n.LanguageSettings.applyStored();

            // 加载插件（此时 Launcher 类还未被加载，插件可以注册字节码转换与平台注入点）
            boolean hasPlugins = com.xiaowu.game.starveil.plugin.PluginLoader.initializeAndLoadPlugins();
            if (hasPlugins) {
                Logger("INFO", "插件加载完成，准备启动 Launcher");
            }

            // 框架自身就绪：配置、内容、插件都已到位，界面还没开始建。
            // 广播出去，谁想在这时准备点什么（读资源、注册自己的监听）就自己接。
            com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents
                    .frameworkReady(GameConstants.FRAMEWORK_VERSION);

            // 插件加载之后才能判断平台是否受支持：平台适配插件可能刚刚替换了注入点。
            // 此时还没有创建锁文件，直接退出不会留下残留状态。
            if (!PlatformSupport.isSupported()) {
                PlatformSupport.showUnsupportedMessage();
                LoggerManager.close();
                System.exit(1);
                return;
            }

            checkCommandLineArgs();
            detectIllegalShutdownBeforeLock();

            if (needAdmin) {
                boolean isAdmin = requestAdminIfNeeded(IllegallyShutdown);
                if (!isAdmin) {
                    SystemManagerFactory.getInstance().showError(
                        GameConstants.ERROR_ADMIN_PRIVILEGE,
                        GameConstants.ERROR_ADMIN_PRIVILEGE_MSG);
                    System.exit(1);
                } else {
                    Logger("INFO", "游戏正以管理员权限运行（非 SYSTEM，主题监听和输入法正常）");
                }
            } else {
                if (!debug) {
                    SystemManagerFactory.getInstance().showInfo(
                        GameConstants.WARN_NON_ADMIN_MODE,
                        GameConstants.WARN_NON_ADMIN_MSG);
                }
                Logger("Warning", "当前处于非管理员模式下（部分系统操作可能受限）");
            }

            if (!createLockFile()) {
                return;
            }

            StartLogger();
            LoggerManager.DEBUG = debug;

            setupExceptionHandling();

            // GPU 优化检查：检测独立显卡并提示切换（平台适配插件可替换该注入点）
            checkAndOptimizeGPU();

            // 标记核心 API 就绪（插件 onLoad 之前仅能访问数据/资源 API）
            com.xiaowu.game.starveil.api.Starveil.markReady();

            // 通过反射调用 Launcher.launchGame，这样 Launcher 类现在才被加载
            // 插件的字节码转换在 Launcher 首次加载时就会生效
            Class<?> launcherClass = Class.forName("com.xiaowu.game.starveil.launcher.Launcher");
            java.lang.reflect.Method launchGameMethod = launcherClass.getMethod("launchGame", String[].class, boolean.class);
            launchGameMethod.invoke(null, args, debug);

        } catch (Exception e) {
            try {
                SystemManagerFactory.getInstance().showError(
                    "警告",
                    "您好,程序运行时发生了严重错误，错误信息如下:\n" + e.getMessage() +
                            "\n请将此信息反馈给开发者以便解决此问题");
                System.exit(1);
            } catch (Exception e1) {
                Logger("ERROR", "桌面环境异常，连错误对话框都无法显示。"
                        + "\n应用错误: " + e.getMessage()
                        + "\n桌面环境错误: " + e1.getMessage());
                System.exit(1);
            }
        }
    }

    private static void initializeStartupHandlers() {
        StartupHandlerRegistry registry = StartupHandlerRegistry.getInstance();
        registry.registerHandler(new DebugHandler());
        registry.registerHandler(new TestCompatWarningHandler());
        registry.registerHandler(new NoAdminHandler());
        registry.registerHandler(new CmdTest());
        registry.registerHandler(new com.xiaowu.game.starveil.startup.handlers.NoChangeGpuHandler());
        registry.registerHandler(new com.xiaowu.game.starveil.startup.handlers.OverlayTestHandler());
        registry.registerHandler(new com.xiaowu.game.starveil.startup.handlers.NoContentHandler());
    }

    private static void checkCommandLineArgs() {
        if (PlatformSupport.isWindows() && WindowsPermissionHandler.isAdminRestart()) {
            Logger("DEBUG", "检测到管理员权限重启");
            if (WindowsPermissionHandler.hasIllegalShutdownFlag()) {
                IllegallyShutdown = true;
                Logger("DEBUG", "检测到异常关闭标记（通过命令行参数）");
            }
        }
    }

    private static void detectIllegalShutdownBeforeLock() {
        try {
            if (PlatformSupport.isWindows() && WindowsPermissionHandler.isAdminRestart()) {
                Logger("DEBUG", "管理员重启，使用命令行参数状态: " + IllegallyShutdown);
                return;
            }

            Path dataDir = Paths.get(GameConstants.DATA_DIR);
            if (!Files.exists(dataDir)) {
                IllegallyShutdown = false;
                return;
            }

            File lockFile = new File(GameConstants.LOCK_FILE_PATH);
            if (lockFile.exists()) {
                IllegallyShutdown = true;
                Logger("DEBUG", "检测到锁文件存在，判断为异常关闭");
            } else {
                IllegallyShutdown = false;
            }
        } catch (Exception e) {
            Logger("WARNING", "检测异常关闭状态时出错: " + e.getMessage());
        }
    }

    private static boolean createLockFile() {
        try {
            Path dataDir = Paths.get(GameConstants.DATA_DIR);
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            GameConstants.setStarveilDirHidden();

            File lockFile = new File(GameConstants.LOCK_FILE_PATH);
            if (lockFile.exists()) {
                if (lockFile.delete()) {
                    Logger("DEBUG", "清理旧锁文件成功");
                } else {
                    Logger("WARNING", "无法删除旧锁文件，可能被其他进程占用");
                    File oldLockFile = new File(GameConstants.LOCK_FILE_PATH + ".old");
                    if (lockFile.renameTo(oldLockFile)) {
                        Logger("DEBUG", "旧锁文件已重命名");
                    }
                }
            }

            FileOutputStream fos = new FileOutputStream(GameConstants.LOCK_FILE_PATH);
            lockChannel = fos.getChannel();
            lock = lockChannel.tryLock();

            if (lock == null) {
                SystemManagerFactory.getInstance().showError(
                    GameConstants.ERROR_LOCK_FILE,
                    GameConstants.ERROR_LOCK_FILE_MSG);
                lockChannel.close();
                fos.close();
                return false;
            }

            Logger("DEBUG", "锁文件创建成功，游戏实例已锁定");
            return true;

        } catch (IOException e) {
            Logger("ERROR", "创建锁文件时发生错误: " + e.getMessage());
            SystemManagerFactory.getInstance().showError(
                GameConstants.ERROR_LOCK_FILE,
                GameConstants.ERROR_LOCK_FILE_MSG);
            return false;
        }
    }

    private static void setupExceptionHandling() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Logger("Error", "全局异常捕获 - 线程: " + thread.getName());
            Logger("ERROR", "应用程序产生了未被捕获的错误，请查看日志文件并交给开发者解决问题");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            Logger("ERROR", sw.toString());
            SystemManagerFactory.getInstance().showError(
                GameConstants.ERROR_UNCAUGHT_EXCEPTION,
                GameConstants.ERROR_UNCAUGHT_EXCEPTION_MSG + "\n" + throwable.getMessage());
            cleanupLock();
            System.exit(1);
        });
    }

    public static void StartLogger() {
        LoggerManager.initializeLogger();
        String hardwareInfo = HardwareManager.getAllHardwareInfo();
        Logger("INFO","--------ClearCraft Game--------");
        Logger("INFO", hardwareInfo);
        Logger("INFO","游戏启动中...");
        Logger("INFO", "异常关闭状态: " + (IllegallyShutdown ? "是" : "否"));
    }

    /**
     * 请求管理员权限（Windows）。
     *
     * <p>注入点 {@link PlatformInjectPoints#REQUEST_ADMIN}：平台适配插件可以用 REPLACE
     * 换成自己的提权 / 重启流程（例如 pkexec）。注意本方法总是发生在插件加载之后，
     * 因此替换是可生效的。
     */
    @InjectPoint(PlatformInjectPoints.REQUEST_ADMIN)
    private static boolean requestAdminIfNeeded(boolean illegallyShutdown) {
        if (!PlatformSupport.isWindows()) {
            Logger("INFO", "当前平台不是 Windows，跳过 Windows 提权流程（由平台适配插件负责）");
            return true;
        }
        return WindowsPermissionHandler.requestAdminIfNeeded(illegallyShutdown);
    }

    /**
     * GPU 优化检查：自动切换到独立显卡（不提示）
     * -no-change-gpu 跳过检查；-no-admin 切换时不提权；-no-admin + -debug 完全跳过
     *
     * <p>注入点 {@link PlatformInjectPoints#GPU_OPTIMIZE}：非 Windows 平台
     * （prime-run / switcheroo-control 等）由平台适配插件用 REPLACE 接管。
     */
    @InjectPoint(PlatformInjectPoints.GPU_OPTIMIZE)
    private static void checkAndOptimizeGPU() {
        try {
            LauncherConfig config = LauncherConfig.getInstance();

            if (config.isNoChangeGpu()) {
                Logger("INFO", "-no-change-gpu: 跳过 GPU 优化检查");
                return;
            }

            if (!config.isNeedAdmin() && config.isDebugMode()) {
                Logger("INFO", "-no-admin -debug: 跳过 GPU 优化检查");
                return;
            }

            if (!PlatformSupport.isWindows()) {
                Logger("INFO", "当前平台不是 Windows，跳过内置 GPU 优化检查（由平台适配插件负责）");
                return;
            }

            checkAndOptimizeGPUWindows();
        } catch (Exception e) {
            Logger("WARNING", "GPU 优化检查失败: " + e.getMessage());
        }
    }

    private static void checkAndOptimizeGPUWindows() {
        if (!HardwareManager.hasDedicatedGPU()) {
            Logger("INFO", "未检测到独立显卡，跳过 GPU 优化检查");
            return;
        }

        if (com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys.GPU_CHECK_IGNORED.get()) {
            return;
        }

        String javaPath = System.getProperty("java.home") + "\\bin\\javaw.exe";
        String[] options = {"我知道了", "不再提示"};
        int choice = javax.swing.JOptionPane.showOptionDialog(
            null,
            "检测到您的电脑拥有独立显卡，\n" +
            "但当前游戏可能运行在集成显卡上。\n\n" +
            "建议将 Java 设置为高性能 GPU：\n" +
            "1. 打开「设置 → 系统 → 屏幕 → 显示卡」\n" +
            "2. 添加 \"" + javaPath + "\"\n" +
            "3. 选择「高性能」\n" +
            "4. 重启游戏\n\n" +
            "这将显著提升游戏性能。",
            "GPU 优化提示",
            javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.INFORMATION_MESSAGE,
            null,
            options,
            options[0]
        );
        if (choice == javax.swing.JOptionPane.NO_OPTION) {
            com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys.GPU_CHECK_IGNORED.set(true);
        }
    }

    public static void cleanupLock() {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
                Logger("DEBUG", "锁文件已释放");
            }
            if (lockChannel != null && lockChannel.isOpen()) {
                lockChannel.close();
                Logger("DEBUG", "锁文件通道已关闭");
            }

            File lockFile = new File(LOCK_FILE_PATH);
            if (lockFile.exists()) {
                if (lockFile.delete()) {
                    Logger("DEBUG", "游戏锁文件已删除");
                } else {
                    Logger("WARNING", "无法删除锁文件，可能已被其他进程占用");
                }
            }
            Logger("INFO", "游戏结束 - 锁文件清理完成");
            LoggerManager.close();
        } catch (IOException e) {
            Logger("ERROR", "清理锁文件时发生错误: " + e.getMessage());
        }
    }
}
