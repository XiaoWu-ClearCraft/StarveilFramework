package com.xiaowu.game.starveil.platform.console;

import com.xiaowu.game.starveil.platform.api.PlatformInjectPoints;
import com.xiaowu.game.starveil.platform.common.SystemDetector;
import com.xiaowu.game.starveil.plugin.InjectPoint;

/**
 * 控制台提供者工厂。
 *
 * <p>本版本内置 Windows 原生控制台实现；其它平台在适配插件接管之前
 * 使用 {@link FallbackConsoleProvider}（Swing 模拟终端）作为回退。
 *
 * <p>{@link #getInstance()} 是注入点 {@link PlatformInjectPoints#CONSOLE_PROVIDER}，
 * 适配插件可以 REPLACE 成自己的终端实现。
 */
public class ConsoleProviderFactory {

    private static volatile ConsoleProvider instance;

    private ConsoleProviderFactory() {}

    @InjectPoint(PlatformInjectPoints.CONSOLE_PROVIDER)
    public static ConsoleProvider getInstance() {
        ConsoleProvider local = instance;
        if (local == null) {
            synchronized (ConsoleProviderFactory.class) {
                local = instance;
                if (local == null) {
                    local = SystemDetector.isWindows() ? new WindowsConsoleProvider() : new FallbackConsoleProvider();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * 直接替换控制台实现（主要供平台适配插件使用）。
     */
    public static void setInstance(ConsoleProvider provider) {
        instance = provider;
    }
}
