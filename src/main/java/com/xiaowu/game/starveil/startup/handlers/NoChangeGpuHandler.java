package com.xiaowu.game.starveil.startup.handlers;

import com.xiaowu.game.starveil.startup.BaseStartupArgumentHandler;
import com.xiaowu.game.starveil.startup.ExecutionMode;
import com.xiaowu.game.starveil.config.LauncherConfig;

/**
 * -no-change-gpu 参数处理器
 * 不以独立显卡模式运行（保持当前GPU）
 */
public class NoChangeGpuHandler extends BaseStartupArgumentHandler {

    public NoChangeGpuHandler() {
        super("no-change-gpu", ExecutionMode.PARALLEL, false,
              "不以独立显卡模式运行（保持当前GPU）");
    }

    @Override
    public boolean handle(String value, String[] allArgs) {
        LauncherConfig.getInstance().setNoChangeGpu(true);
        return true;
    }
}
