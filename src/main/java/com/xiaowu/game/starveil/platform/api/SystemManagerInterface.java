package com.xiaowu.game.starveil.platform.api;

import javafx.stage.Stage;

/**
 * 系统管理器接口 - 统一管理不同操作系统的系统操作
 */
public interface SystemManagerInterface {
    /**
     * 显示信息对话框
     */
    void showInfo(String title, String message);

    /**
     * 显示警告对话框
     */
    void showWarning(String title, String message);

    /**
     * 显示错误对话框
     */
    void showError(String title, String message);

    /**
     * 显示确认对话框
     */
    boolean showConfirm(String title, String message);

    /**
     * 显示自定义对话框
     */
    int showCustom(String title, String message, int buttons, int icon);

    /**
     * 窗口抖动效果
     */
    void shakeWindow(Stage stage, int soundType);

    /**
     * 窗口抖动效果（默认警告音）
     */
    void shakeWindow(Stage stage);

    /**
     * 测试窗口抖动效果
     */
    void testShake(Stage stage);
}