package com.xiaowu.game.starveil.game.story;

/**
 * 剧情被取消（读档 / 退出 / forceClose 打断阻塞等待）时抛出。
 *
 * <p>由剧情线程在阻塞点上抛出，{@link StoryScheduler} 捕获后静默结束该脚本，
 * 不会当作错误记录。
 */
public class StoryCancelledException extends RuntimeException {

    public StoryCancelledException() {
        super("剧情已取消");
    }

    public StoryCancelledException(String message) {
        super(message);
    }
}
