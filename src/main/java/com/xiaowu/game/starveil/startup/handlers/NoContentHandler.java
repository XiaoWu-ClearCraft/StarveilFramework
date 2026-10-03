package com.xiaowu.game.starveil.startup.handlers;

import com.xiaowu.game.starveil.config.LauncherConfig;
import com.xiaowu.game.starveil.startup.BaseStartupArgumentHandler;
import com.xiaowu.game.starveil.startup.ExecutionMode;

/**
 * {@code -no-content} 参数处理器。
 *
 * <p>强制在「没有游戏内容」的情况下运行，用于开发/测试。
 *
 * <p>正常情况下框架发现 {@code com.xiaowu.game.starveil.content} 不存在会致命退出 ——
 * 框架本身不是可玩的游戏。但那样就没法验证「缺资源时各处降级是否正常」了。
 * 加上这个参数可以绕过该检查，把框架单独跑起来，检查：
 * <ul>
 *   <li>主菜单背景是否退化为纯黑（而不是 NPE）；</li>
 *   <li>窗口图标是否落到系统默认（而不是 NPE）；</li>
 *   <li>字体缺失时是否回退系统字体；</li>
 *   <li>音频缺失时是否只是记日志而不中断。</li>
 * </ul>
 */
public class NoContentHandler extends BaseStartupArgumentHandler {

    public NoContentHandler() {
        super("no-content", ExecutionMode.PARALLEL, false,
              "强制在没有游戏内容的情况下运行（开发/测试用，检查缺资源时的降级行为）");
    }

    @Override
    public boolean handle(String value, String[] allArgs) {
        LauncherConfig.getInstance().setNoContent(true);
        return true;
    }
}
