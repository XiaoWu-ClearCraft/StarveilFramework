package com.xiaowu.game.starveil.game.story;

/**
 * 剧情脚本执行失败（UI 操作抛异常等）时抛出。
 *
 * <p>与 {@link StoryCancelledException} 区分：取消是正常流程，本异常会记为 ERROR 日志。
 */
public class StoryScriptException extends RuntimeException {

    public StoryScriptException(String message) {
        super(message);
    }

    public StoryScriptException(String message, Throwable cause) {
        super(message, cause);
    }
}
