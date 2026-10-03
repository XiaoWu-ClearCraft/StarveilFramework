package com.xiaowu.game.starveil.game.story;

/**
 * 章节运行模式 —— 决定启动/切换章节时要不要加载完整世界。
 *
 * <p>由章节自己声明（{@link StoryChapter#mode()}），而不是靠外部配置，
 * 这样「这一章需不需要玩家走动」这件事只写在一个地方。
 */
public enum ChapterMode {

    /**
     * 普通模式：加载完整世界、创建玩家实体、注册 ECS 系统管线。
     * 剧情通过地图事件触发，玩家可以在场景里自由走动（现有行为）。
     */
    NORMAL,

    /**
     * 仅视觉小说：<b>不加载世界</b>、不创建玩家、不注册 ECS 系统。
     *
     * <p>画面是纯黑的（视口底色），背景由章节自己用
     * {@code s.image(path)} 铺，结束后用 {@code s.hideImage()} 收起。
     * 适合纯对话章节、序章、过场 —— 不需要为一屏对话加载整张地图。
     */
    VISUAL_NOVEL
}
