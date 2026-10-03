package com.xiaowu.game.starveil.platform.windows;

import com.xiaowu.game.starveil.platform.api.SystemManagerInterface;
import javafx.stage.Stage;

/**
 * Windows 系统管理器：直接委托给 {@link WinOpsManager}。
 *
 * <p>这是核心内置的唯一平台实现。其它平台请让插件注册
 * {@link com.xiaowu.game.starveil.platform.api.PlatformInjectPoints#SYSTEM_MANAGER}
 * 注入点来替换 {@link com.xiaowu.game.starveil.platform.api.SystemManagerFactory#getInstance()}。
 */
public class WindowsSystemManager implements SystemManagerInterface {

    @Override
    public void showInfo(String title, String message) {
        WinOpsManager.showInfo(title, message);
    }

    @Override
    public void showWarning(String title, String message) {
        WinOpsManager.showWarning(title, message);
    }

    @Override
    public void showError(String title, String message) {
        WinOpsManager.showError(title, message);
    }

    @Override
    public boolean showConfirm(String title, String message) {
        return WinOpsManager.showConfirm(title, message);
    }

    @Override
    public int showCustom(String title, String message, int buttons, int icon) {
        return WinOpsManager.showCustom(title, message, buttons, icon);
    }

    @Override
    public void shakeWindow(Stage stage, int soundType) {
        WinOpsManager.shakeWindow(stage, soundType);
    }

    @Override
    public void shakeWindow(Stage stage) {
        WinOpsManager.shakeWindow(stage);
    }

    @Override
    public void testShake(Stage stage) {
        WinOpsManager.testShake(stage);
    }
}
