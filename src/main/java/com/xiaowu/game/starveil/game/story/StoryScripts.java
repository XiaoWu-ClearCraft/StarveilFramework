package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.world.WorldMap;

import java.util.concurrent.CompletableFuture;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 章节脚本注册工具。
 *
 * <p>把「地图事件触发 → 跑剧情 → 事件完成」这三步收敛成一次调用，
 * 代替旧写法里手写的 {@code worldMap.addEventListener(id, (event, args) -> new Thread(...))}
 * 加末尾的 {@code event.completeCurrentEvent()}。
 */
public final class StoryScripts {

    private StoryScripts() {}

    /**
     * 把章节脚本绑定到当前世界地图的某个事件上。
     *
     * <p>事件触发时脚本进入 {@link StoryScheduler} 队列；脚本结束时自动调用
     * {@code event.completeCurrentEvent()}，因此章节作者不需要再管事件生命周期。
     *
     * <p>重复调用是安全的：同一个 {@code chapterId#eventId} 只会保留最后一次注册的监听器，
     * 地图反复加载不会叠加出多份剧情。
     *
     * @param chapter 章节
     * @param eventId 地图 JSON 里的事件 id
     */
    public static void bind(StoryChapter chapter, String eventId) {
        WorldMap worldMap = GameInstance.getWorldMapInstance();
        if (worldMap == null) {
            Logger("WARN", "[StoryScripts] WorldMap 未初始化，无法绑定章节事件: " + eventId);
            return;
        }
        bind(chapter, worldMap, eventId);
    }

    public static void bind(StoryChapter chapter, WorldMap worldMap, String eventId) {
        String ownerKey = chapter.id() + "#" + eventId;
        worldMap.bindEvent(eventId, ownerKey, (event, args) -> run(chapter, event));
        Logger("INFO", "[StoryScripts] 章节 " + chapter.id() + " 已绑定事件: " + eventId);
    }

    /**
     * 直接运行一次章节脚本（不依赖地图事件）。
     */
    public static CompletableFuture<Void> run(StoryChapter chapter) {
        return StoryScheduler.getInstance().submit(chapter.id(), chapter::write);
    }

    /**
     * 重置剧情运行时：新开游戏 / 读档 / 回主菜单时调用。
     *
     * <p>取消正在播放与排队中的脚本、关掉对话界面、清空剧情信号，
     * 保证上一局残留的剧情不会串到新一局。
     */
    public static void resetRuntime() {
        StoryScheduler.getInstance().cancelAll();
        com.xiaowu.game.starveil.ui.dialog.ChatManager.getInstance().forceCloseAll();
        StorySignals.resetAll();
    }

    /**
     * 在世界事件里运行章节脚本。
     *
     * <p>事件在脚本 <b>入队时</b> 就被标记为完成，与旧写法（起线程后立刻
     * {@code completeCurrentEvent()}）保持一致：剧情中途存档/读档时，
     * 「已触发」状态已经被记录下来，不会整章重播。
     */
    public static CompletableFuture<Void> run(StoryChapter chapter, WorldMap.MapEvent event) {
        if (event != null) {
            event.completeCurrentEvent();
        }
        return StoryScheduler.getInstance().submit(chapter.id(), chapter::write);
    }
}
