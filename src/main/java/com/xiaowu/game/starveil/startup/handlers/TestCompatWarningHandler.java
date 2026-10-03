package com.xiaowu.game.starveil.startup.handlers;

import com.xiaowu.game.starveil.startup.BaseStartupArgumentHandler;
import com.xiaowu.game.starveil.startup.ExecutionMode;
import com.xiaowu.game.starveil.config.LauncherConfig;

/**
 * -test-compat-warning 参数处理器
 * 强制显示兼容性警告对话框，用于测试
 */
public class TestCompatWarningHandler extends BaseStartupArgumentHandler {

    public TestCompatWarningHandler() {
        super("test-compat-warning", ExecutionMode.PARALLEL, false,
              "强制显示兼容性警告对话框（测试用）");
    }

    @Override
    public boolean handle(String value, String[] allArgs) {
        LauncherConfig.getInstance().setTestCompatWarning(true);
        return true;
    }
}
