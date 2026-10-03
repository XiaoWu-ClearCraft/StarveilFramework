package com.xiaowu.game.starveil.startup.handlers;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

import com.xiaowu.game.starveil.startup.BaseStartupArgumentHandler;
import com.xiaowu.game.starveil.startup.ExecutionMode;
import com.xiaowu.game.starveil.config.LauncherConfig;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;

import javax.swing.*;

/**
 * -no-admin 参数处理器
 * 不以管理员模式运行游戏
 */
public class NoAdminHandler extends BaseStartupArgumentHandler {

    public NoAdminHandler() {
        super("no-admin", ExecutionMode.PARALLEL, false,
              "不以管理员模式运行游戏");
    }

    @Override
    public boolean handle(String value, String[] allArgs) {
        try {
            // 设置不需要管理员权限
            LauncherConfig.getInstance().setNeedAdmin(false);

            return true;
        } catch (Exception e) {
            Logger("ERROR", "NoAdminHandler 处理失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}