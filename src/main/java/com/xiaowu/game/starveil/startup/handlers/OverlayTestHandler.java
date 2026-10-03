package com.xiaowu.game.starveil.startup.handlers;

import com.xiaowu.game.starveil.config.LauncherConfig;
import com.xiaowu.game.starveil.startup.BaseStartupArgumentHandler;
import com.xiaowu.game.starveil.startup.ExecutionMode;

/**
 * -overlay-test 参数处理器
 *
 * <p>遮罩层自检：进入游戏后自动跑一遍「降帧保持 → 花屏 → 恢复」，
 * 并把每一个环节的实际状态写进日志，用来确认整屏抓取是否真的生效。
 * 需要在游戏内观察，因此这里只置标志位，由
 * {@code ScreenEffectsManager} 在遮罩层显示后执行。
 */
public class OverlayTestHandler extends BaseStartupArgumentHandler {

    public OverlayTestHandler() {
        super("overlay-test", ExecutionMode.PARALLEL, false,
                "遮罩层自检：自动跑一次降帧保持 + 花屏（测试用）");
    }

    @Override
    public boolean handle(String value, String[] allArgs) {
        LauncherConfig.getInstance().setOverlayTest(true);
        return true;
    }
}
