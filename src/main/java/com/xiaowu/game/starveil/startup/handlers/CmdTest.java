package com.xiaowu.game.starveil.startup.handlers;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

import com.xiaowu.game.starveil.platform.console.ConsoleColor;
import com.xiaowu.game.starveil.platform.console.ConsoleProvider;
import com.xiaowu.game.starveil.platform.console.ConsoleProviderFactory;
import com.xiaowu.game.starveil.startup.BaseStartupArgumentHandler;
import com.xiaowu.game.starveil.startup.ExecutionMode;
import com.xiaowu.game.starveil.config.LauncherConfig;

import java.awt.*;
import java.io.Console;
import java.util.Arrays;
import java.util.List;

/**
 * -debug 参数处理器
 * 启用调试模式
 */
public class CmdTest extends BaseStartupArgumentHandler {

    public CmdTest() {
        super("cmd", ExecutionMode.OVERRIDE, false,
                "测试多平台控制台");
    }

    @Override
    public boolean handle(String value, String[] allArgs) {
        try {
            ConsoleProvider console = ConsoleProviderFactory.getInstance();
            console.setTitle(com.xiaowu.game.starveil.infrastructure.ContentConfig.gameName());
            console.println("测试成功");
            console.printBackground("背景 - 绿色  ", ConsoleColor.GREEN);
            console.println("");
            console.printColor("前景 - 红色", ConsoleColor.RED);
            console.println("");
            console.printBackground("背景 - 蓝色  ", ConsoleColor.BLUE);
            console.println("");
            List<String> option = Arrays.asList("选项1","选项2","选项3");
            String selects = console.selectOption(option);
            console.println("你选择了" + selects);
            console.println("长文本测试");

            for (int a = 0; a < 100;  a++) {
                Thread.sleep(100);
                console.println("占位符11111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111111");
            }

            console.keepAlive();
            // keepAlive 返回后继续执行（不退出程序）
            return true;
        } catch (Exception e) {
            Logger("ERROR", "CmdTest 处理失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}