package com.xiaowu.game.starveil.launcher;

import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.debug.DebugWindow;
import com.xiaowu.game.starveil.config.LauncherConfig;
import com.xiaowu.game.starveil.platform.common.HardwareManager;
import com.xiaowu.game.starveil.platform.common.PlatformSupport;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.core.Menu;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;
import javafx.application.Application;

import javax.swing.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class Launcher {
    private static FileLock lock = null;
    private static FileChannel lockChannel = null;
    private static Boolean IllegallyShutdown = false;
    
    private static final String LOCK_FILE_PATH = GameConstants.LOCK_FILE_PATH;

    public static void main(String[] args) {
        // 兼容直接启动 Launcher 的情况（无插件模式）
        Logger("INFO", "=== Launcher.main 直接调用 ===");
        launchGame(args, false);
    }

    /**
     * 游戏启动逻辑（插件可注入此方法）
     */
    public static void launchGame(String[] args, boolean debug) {
        // 平台支持校验（直接以 Launcher 启动、绕过 AppEntry 时的兜底检查）
        if (!PlatformSupport.isSupported()) {
            PlatformSupport.showUnsupportedMessage();
            cleanupLock();
            System.exit(1);
            return;
        }

        // 在 DataManager 初始化后检查系统兼容性
        checkSystemCompatibility();

        // 应用保存的渲染设置
        applySavedSettings();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            com.xiaowu.game.starveil.plugin.PluginLoader.unloadAllPlugins();
            cleanupLock();
        }));
        cleanupTempConfigs();

        if (IllegallyShutdown) {
            DataManager.set(GameConstants.ILLEGALLY_SHUTDOWN_KEY, "true");
            Logger("DEBUG", "检测到上次异常关闭，已设置标记");
        }

        // 在JavaFX启动后显示DebugWindow（如果启用了调试模式）
        if (debug) {
            SwingUtilities.invokeLater(() -> {
                try {
                    DebugWindow.getInstance();
                } catch (Exception e) {
                    Logger("ERROR", "启动调试窗口失败: " + e.getMessage());
                }
            });
        }

        // 如果不是调试模式且不需要管理员权限，显示警告信息
        if (!debug && !LauncherConfig.getInstance().isNeedAdmin()) {
            SwingUtilities.invokeLater(() -> {
                SystemManagerFactory.getInstance().showInfo(
                    GameConstants.WARN_NON_ADMIN_MODE,
                    GameConstants.WARN_NON_ADMIN_MSG
                );
            });
        }
        try {
            Application.launch(Menu.class, args);
        } catch (Exception e) {
            Logger("ERROR", "launch语句出错");
            System.err.println("Menu.start方法发生异常:");
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * 检查系统兼容性并在需要时显示警告
     * 针对 Windows 10 以下版本显示兼容性提示
     * 注意：此方法必须在 DataManager.initialize() 之后调用
     */
    private static void checkSystemCompatibility() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osLower = osName.toLowerCase();
        boolean needWarning = false;
        
        // 测试模式：强制显示警告
        if (LauncherConfig.getInstance().isTestCompatWarning()) {
            needWarning = true;
            Logger("INFO", "测试模式：强制显示兼容性警告");
        } else {
            // 检查是否已忽略过
            if (DataManager.getBoolean("starveil:compatibility_warning_shown", false)) {
                return;
            }
            
            // Windows 10 以下需要提示
            if (osLower.contains("win")) {
                boolean isWindows10OrLater = osVersion.startsWith("10") || osVersion.startsWith("11");
                if (!isWindows10OrLater) {
                    needWarning = true;
                }
            }
        }
        
        if (!needWarning) {
            return;
        }
        
        // 显示兼容性警告
        String osFullName = osName + " " + osVersion;
        final int[] choice = {-1};
        try {
            SwingUtilities.invokeAndWait(() -> {
                choice[0] = JOptionPane.showOptionDialog(
                    null,
                    "您正在使用的操作系统 " + osFullName + " 可能不受支持，\n" +
                    "可能无法达成完整游戏体验。\n\n" +
                    "建议切换到 Windows 10 及以上版本。\n" +
                    "其它平台请安装对应的平台适配插件后运行。",
                    "兼容性提示",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new Object[]{"忽略", "退出"},
                    "忽略"
                );
            });
        } catch (Exception e) {
            Logger("WARNING", "无法显示兼容性对话框: " + e.getMessage());
            choice[0] = JOptionPane.YES_OPTION; // 默认忽略
        }
        
        if (choice[0] != JOptionPane.YES_OPTION) {
            Logger("INFO", "用户选择退出游戏（兼容性原因）");
            System.exit(0);
        } else {
            DataManager.setBoolean("starveil:compatibility_warning_shown", true);
            Logger("INFO", "用户选择忽略兼容性提示，已记录");
        }
    }

    private static void cleanupTempConfigs() {
        if (DataManager.contains(GameConstants.CANT_EXIT_KEY)) {
            DataManager.remove(GameConstants.CANT_EXIT_KEY);
            Logger("DEBUG", "清理临时退出阻止标记");
        }

        if (!IllegallyShutdown && DataManager.contains(GameConstants.ILLEGALLY_SHUTDOWN_KEY)) {
            DataManager.remove(GameConstants.ILLEGALLY_SHUTDOWN_KEY);
            Logger("DEBUG", "清理异常关闭标记（本次正常启动）");
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

    public static void StartLogger() {
        LoggerManager.initializeLogger();
        String hardwareInfo = HardwareManager.getAllHardwareInfo();
        Logger("INFO","--------ClearCraft Game--------");
        Logger("INFO", hardwareInfo);
        Logger("INFO","游戏启动中...");
        Logger("INFO", "异常关闭状态: " + (IllegallyShutdown ? "是" : "否"));
    }

    /**
     * 应用保存的设置
     */
    private static void applySavedSettings() {
        // 初始化AudioManager以应用音量设置
        AudioManager.getInstance();

        // 加载音频设置
        String bgmVolume = DataManager.get(GameConstants.SETTING_BGM_VOLUME, String.valueOf(GameConstants.DEFAULT_BGM_VOLUME));
        if (bgmVolume != null) {
            AudioManager.setBackgroundMusicVolumeGlobal(Double.parseDouble(bgmVolume));
        }

        String sfxVolume = DataManager.get(GameConstants.SETTING_SFX_VOLUME, String.valueOf(GameConstants.DEFAULT_SFX_VOLUME));
        if (sfxVolume != null) {
            AudioManager.setSoundEffectsVolumeGlobal(Double.parseDouble(sfxVolume));
        }

        Logger("INFO", "游戏设置已应用");
    }
}