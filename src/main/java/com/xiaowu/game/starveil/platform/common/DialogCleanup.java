package com.xiaowu.game.starveil.platform.common;

import com.xiaowu.game.starveil.platform.api.PlatformInjectPoints;
import com.xiaowu.game.starveil.platform.windows.Win32DialogCleanup;
import com.xiaowu.game.starveil.plugin.InjectPoint;

/**
 * 原生对话框清理门面。
 *
 * <p>本版本只内置 Windows（Win32 窗口枚举）实现。其它平台的适配插件可以
 * REPLACE {@link PlatformInjectPoints#DIALOG_CLEANUP} 注入点，换成基于
 * X11 / zenity / kdialog 的清理逻辑。
 */
public class DialogCleanup {

    private DialogCleanup() {}

    /**
     * 关闭由系统对话框工具拉起的、线程名以 {@code threadNamePrefix} 开头的原生窗口。
     */
    @InjectPoint(PlatformInjectPoints.DIALOG_CLEANUP)
    public static void closeAllDialogs(String threadNamePrefix) {
        if (SystemDetector.isWindows()) {
            Win32DialogCleanup.closeAllDialogs(threadNamePrefix);
        }
    }
}
