package com.xiaowu.game.starveil.platform.console;

import java.util.List;

/**
 * 跨平台控制台提供者接口
 * 定义控制台UI的统一API，由各平台实现
 */
public interface ConsoleProvider {

    /**
     * 输出文本到控制台
     */
    void print(String text);

    /**
     * 输出文本到控制台并换行
     */
    void println(String text);

    /**
     * 清空控制台屏幕
     */
    void clear();

    /**
     * 设置控制台窗口标题
     */
    default void setTitle(String title) {
        // 默认空实现，由具体平台实现
    }

    /**
     * 清除当前行
     */
    void clearLine();

    /**
     * 设置文本颜色
     */
    void setColor(ConsoleColor color);

    /**
     * 设置背景色
     */
    void setBackgroundColor(ConsoleColor color);

    /**
     * 重置颜色为默认
     */
    void resetColor();

    /**
     * 显示带颜色的文本
     */
    void printColor(String text, ConsoleColor color);

    /**
     * 显示带背景色的文本
     */
    void printBackground(String text, ConsoleColor color);

    /**
     * 显示选项列表，让用户选择
     * @param options 选项列表
     * @param title 标题（可为null）
     * @return 选中的选项字符串，取消返回null
     */
    String selectOption(List<String> options, String title);

    /**
     * 显示选项列表（无标题）
     */
    default String selectOption(List<String> options) {
        return selectOption(options, null);
    }

    /**
     * 读取按键
     * @return 按键码，失败返回-1
     */
    int readKey();

    /**
     * 等待按键按下
     * @return 按下的键码
     */
    int waitForKeypress();

    /**
     * 创建一个进度条
     */
    ProgressBar createProgressBar();

    /**
     * 关闭控制台（逻辑关闭）
     */
    void exit();

    /**
     * 检查控制台是否已关闭
     */
    boolean isClosed();

    /**
     * 重新打开控制台
     */
    void reopen();

    /**
     * 隐藏控制台窗口
     */
    void hideWindow();

    /**
     * 显示控制台窗口
     */
    void showWindow();

    /**
     * 真正关闭控制台窗口
     */
    void closeWindow();

    /**
     * 安全关闭控制台窗口（仅隐藏）
     */
    void closeWindowSafe();

    /**
     * 重新创建控制台窗口
     */
    void recreateWindow();

    /**
     * 保持程序运行（等待用户按键退出）
     */
    void keepAlive();
}
