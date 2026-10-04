package com.xiaowu.game.starveil.game.story;

/**
 * 章节脚本。
 *
 * <p>一个章节 = 一个类，实现 {@link #write(StoryScript)} 用普通 Java 控制流写剧情；
 * 随后用 {@link StoryScripts#bind} 把某个世界事件（地图 JSON 里 {@code event[].id}）
 * 接到这段脚本上。
 *
 * <pre>{@code
 * public class Chapter1 implements StoryChapter {
 *     @Override public String id() { return "chapter1"; }
 *
 *     @Override public void write(StoryScript s) {
 *         s.say("???", "你好呀~");
 *         ...
 *     }
 * }
 *
 * // 注册（通常在构造函数里，或 GameInstance 初始化时）
 * StoryScripts.bind(this, "author_chapter1");
 * }</pre>
 *
 * <p>{@link #write} 运行在 {@link StoryScheduler} 的剧情线程上，可以放心使用阻塞式写法。
 */
public interface StoryChapter {

    /** 章节唯一标识，用于日志、调试与存档变量前缀。 */
    String id();

    /**
     * 章节运行模式。
     *
     * <p>默认 {@link ChapterMode#NORMAL}：加载完整世界，玩家能在场景里走动。
     * 纯对话章节返回 {@link ChapterMode#VISUAL_NOVEL} —— 启动时<b>不会</b>加载世界，
     * 屏幕为纯黑，背景由章节自己用 {@code s.image(path)} 铺。
     *
     * <p>这个值在<b>加载世界之前</b>就会被读取，所以实现里不要做任何有副作用的初始化。
     */
    default ChapterMode mode() {
        return ChapterMode.NORMAL;
    }

    /**
     * 本章要用的世界（地图 JSON 路径）；{@code null} 表示「用内容声明的起始世界」。
     *
     * <p>为什么需要它：声明 {@link ChapterMode#NORMAL} 的章节需要一个世界，
     * 而那个世界必须在<b>章节脚本开始跑之前</b>就加载好（否则玩家会先看到一帧
     * 空白或加载页面，然后才「啪」地出现地图）。章节不可能自己先
     * {@code enterWorld} 再声明需要世界 —— {@code mode()} 与 {@code world()}
     * 都是在世界加载之前读的，所以这里只能<b>声明</b>，不能做动作。
     *
     * <pre>{@code
     * @Override public ChapterMode mode() { return ChapterMode.NORMAL; }
     * @Override public String world() { return "starveil:data/worlds/my-map.json"; }
     * }</pre>
     *
     * <p>返回 {@code null} 时退回内容在
     * {@code ContentConfig.setStartWorld(...)} 里给的那张；两者都没有时框架
     * <b>不猜</b>（猜错等于把玩家丢进一张不相干的图），而是记 ERROR
     * 并保持加载页面，等章节脚本自己调 {@code s.enterWorld(...)}。
     *
     * <p>和 {@link #mode()} 一样，这个值在加载世界前读取，实现里不要有副作用。
     */
    default String world() {
        return null;
    }

    /** 本章结束后进入下一章（等价于 {@code s.nextChapter()}，放在这里便于纯声明式章节）。 */
    default boolean hasNextChapter() {
        return false;
    }

    /** 章节剧情内容。 */
    void write(StoryScript script) throws Exception;
}
