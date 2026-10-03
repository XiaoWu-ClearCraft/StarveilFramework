package com.xiaowu.game.starveil.startup.handlers;

import com.xiaowu.game.starveil.startup.BaseStartupArgumentHandler;
import com.xiaowu.game.starveil.startup.ExecutionMode;
import com.xiaowu.game.starveil.config.LauncherConfig;

/**
 * -debug 参数处理器
 * 启用调试模式
 */
public class DebugHandler extends BaseStartupArgumentHandler {

    public DebugHandler() {
        super("debug", ExecutionMode.PARALLEL, false,
              "启用调试模式，开启详细日志和调试窗口");
    }

    @Override
    public boolean handle(String value, String[] allArgs) {
        try {
            // 设置调试模式配置
            LauncherConfig.getInstance().setDebugMode(true);

            // 注意：DebugWindow的启动由Launcher在JavaFX初始化后处理
            // 不能在这里启动，因为此时JavaFX toolkit还未初始化

            return true;
        } catch (Exception e) {
            System.err.println("DebugHandler 处理失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}