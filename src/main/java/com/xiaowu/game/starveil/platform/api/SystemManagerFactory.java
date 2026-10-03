package com.xiaowu.game.starveil.platform.api;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.platform.common.SwingSystemManager;
import com.xiaowu.game.starveil.platform.common.SystemDetector;
import com.xiaowu.game.starveil.platform.windows.WindowsSystemManager;
import com.xiaowu.game.starveil.plugin.InjectPoint;

/**
 * 系统管理器工厂。
 *
 * <p>本版本核心只提供 Windows 实现；当前平台不是 Windows 时，
 * 在平台适配插件接管之前使用 {@link SwingSystemManager} 作为中性回退，
 * 以便把「平台不受支持」的提示显示给用户，而不是直接崩溃。
 *
 * <p>{@link #getInstance()} 是平台注入点
 * {@link PlatformInjectPoints#SYSTEM_MANAGER}：适配插件用 REPLACE 返回自己的
 * {@link SystemManagerInterface} 实现即可接管全部对话框与窗口抖动。
 */
public class SystemManagerFactory {

    private static volatile SystemManagerInterface instance;

    private SystemManagerFactory() {}

    @InjectPoint(PlatformInjectPoints.SYSTEM_MANAGER)
    public static SystemManagerInterface getInstance() {
        SystemManagerInterface local = instance;
        if (local == null) {
            synchronized (SystemManagerFactory.class) {
                local = instance;
                if (local == null) {
                    local = createDefault();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * 直接替换全局系统管理器（插件也可通过 {@code SystemManagerFactory.setInstance} 完成，
     * 但推荐走注入点，以便和插件卸载流程保持一致）。
     */
    public static void setInstance(SystemManagerInterface manager) {
        if (manager != null) {
            instance = manager;
            LoggerManager.Logger("INFO", "系统管理器已被替换: " + manager.getClass().getName());
        }
    }

    private static SystemManagerInterface createDefault() {
        if (SystemDetector.isWindows()) {
            return new WindowsSystemManager();
        }
        LoggerManager.Logger("WARNING", "当前平台（" + SystemDetector.getPlatformDisplayName()
                + "）没有内置系统管理器，使用 Swing 回退实现；请安装对应的平台适配插件");
        return new SwingSystemManager();
    }
}
