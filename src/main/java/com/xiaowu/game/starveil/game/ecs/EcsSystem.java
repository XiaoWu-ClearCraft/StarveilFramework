package com.xiaowu.game.starveil.game.ecs;

/**
 * 系统 —— 每帧被 {@link World#update(double)} 按注册顺序调用。
 * 系统不得持有可被跨世界复用的实体状态；实体数据一律放组件。
 */
public interface EcsSystem {
    default void addedTo(World world) {
    }

    default void removedFrom(World world) {
    }

    /**
     * 剧情暂停期间该系统是否仍然运行。
     *
     * <p>默认 {@code false} —— 剧情期间整个世界冻结。只有<b>纯表现层</b>的系统
     * （如把组件同步到视图的 {@link com.xiaowu.game.starveil.game.ecs.sys.SpriteSyncSystem}）
     * 才应该返回 true，否则被单独豁免的 NPC 系统动了、画面却不会跟着更新。
     *
     * <p>需要让某个正常系统（如 NPC AI）在剧情期间继续跑的，用
     * {@link World#exemptFromStoryPause(Class)} 按章节声明。
     */
    default boolean runsDuringStoryPause() {
        return false;
    }

    void update(World world, double deltaTime);
}
