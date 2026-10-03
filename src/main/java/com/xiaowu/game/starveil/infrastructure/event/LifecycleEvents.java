package com.xiaowu.game.starveil.infrastructure.event;

/**
 * 框架生命周期事件的定义与监听入口。
 *
 * <p>每个事件是一个不可变 {@code record}，字段就是这次广播携带的信息。
 * 监听方式：
 *
 * <pre>
 *   LifecycleEvents.onMenuShown(e -&gt;
 *           Logger("INFO", "主菜单显示了，标题是 " + e.title()));
 *
 *   LifecycleEvents.onChapterChanged(e -&gt;
 *           Logger("INFO", "进入第 " + e.chapter() + " 章"));
 * </pre>
 *
 * <p>所有 {@code on*} 方法都返回订阅句柄，可 {@code cancel()}；直接丢弃也没关系。
 * {@code once*} 版本只触发一次（例如「第一次进入游戏时做点什么」）。
 *
 * <p><b>没有监听者时广播就是空操作</b>，发布方不必写任何兜底分支。
 *
 * <p>可用事件一览见 {@code docs/lifecycle-events.md}。
 */
public final class LifecycleEvents {

    private LifecycleEvents() {
    }

    // ==================== 事件定义 ====================

    /**
     * 框架自身已经就绪：配置加载完、内容初始化跑完、插件也装好了。
     *
     * <p>此时还没有任何界面。想在这时做点准备（读资源、注册自己的监听）最合适。
     */
    public record FrameworkReady(String frameworkVersion) {
    }

    /**
     * 主菜单已经显示出来（启动提示之类的前置界面也都结束了）。
     *
     * @param title 窗口标题
     */
    public record MenuShown(String title) {
    }

    /**
     * 一局游戏开始（新游戏或读档都会发，用 {@link #fromSave()} 区分）。
     *
     * <p>此刻世界可能还没加载 —— 章节是 NORMAL 还是 VISUAL_NOVEL 由内容决定。
     *
     * @param startChapter 起始章节号
     * @param fromSave     是否来自读档
     */
    public record GameStarted(int startChapter, boolean fromSave) {
    }

    /**
     * 章节已确定，脚本<b>即将</b>开始运行。
     *
     * <p>注意时机：监听者先收到广播，章节脚本随后才开始跑。
     * 想「等这一章真正开始之后再做点什么」的话，自己在监听者里
     * {@code Platform.runLater(...)} 或者用别的信号。
     *
     * @param chapter 章节号
     * @param mode    该章节声明的运行模式（{@code "NORMAL"} / {@code "VISUAL_NOVEL"}）
     */
    public record ChapterChanged(int chapter, String mode) {
    }

    /**
     * 游戏已保存到某个槽位。
     *
     * @param slot 槽位号（0 基）
     */
    public record GameSaved(int slot) {
    }

    /**
     * 已从某个槽位读档完成。
     *
     * @param slot 槽位号（0 基）
     */
    public record GameLoaded(int slot) {
    }

    /** 设置界面打开。 */
    public record SettingsOpened() {
    }

    /**
     * 设置界面关闭。
     *
     * @param applied true = 点了应用/确定，false = 取消或直接关掉
     */
    public record SettingsClosed(boolean applied) {
    }

    /**
     * 界面语言已切换。
     *
     * @param languageCode 新语言代码，如 {@code en_us}
     */
    public record LanguageChanged(String languageCode) {
    }

    /**
     * 教程完成。
     *
     * <p>原先这类「单次事件」是用 {@code EventCallbackManager} 里一个
     * 执行完就自我移除的回调列表实现的；现在统一走事件总线。
     */
    public record TutorialCompleted() {
    }

    // ==================== 监听 ====================

    public static Lifecycle.Subscription<FrameworkReady> onFrameworkReady(
            java.util.function.Consumer<FrameworkReady> l) {
        return Lifecycle.on(FrameworkReady.class, l);
    }

    public static Lifecycle.Subscription<MenuShown> onMenuShown(
            java.util.function.Consumer<MenuShown> l) {
        return Lifecycle.on(MenuShown.class, l);
    }

    /** 只关心第一次显示主菜单。 */
    public static void onceMenuShown(java.util.function.Consumer<MenuShown> l) {
        Lifecycle.once(MenuShown.class, l);
    }

    public static Lifecycle.Subscription<GameStarted> onGameStarted(
            java.util.function.Consumer<GameStarted> l) {
        return Lifecycle.on(GameStarted.class, l);
    }

    public static Lifecycle.Subscription<ChapterChanged> onChapterChanged(
            java.util.function.Consumer<ChapterChanged> l) {
        return Lifecycle.on(ChapterChanged.class, l);
    }

    public static Lifecycle.Subscription<GameSaved> onGameSaved(
            java.util.function.Consumer<GameSaved> l) {
        return Lifecycle.on(GameSaved.class, l);
    }

    public static Lifecycle.Subscription<GameLoaded> onGameLoaded(
            java.util.function.Consumer<GameLoaded> l) {
        return Lifecycle.on(GameLoaded.class, l);
    }

    public static Lifecycle.Subscription<SettingsOpened> onSettingsOpened(
            java.util.function.Consumer<SettingsOpened> l) {
        return Lifecycle.on(SettingsOpened.class, l);
    }

    public static Lifecycle.Subscription<SettingsClosed> onSettingsClosed(
            java.util.function.Consumer<SettingsClosed> l) {
        return Lifecycle.on(SettingsClosed.class, l);
    }

    public static Lifecycle.Subscription<LanguageChanged> onLanguageChanged(
            java.util.function.Consumer<LanguageChanged> l) {
        return Lifecycle.on(LanguageChanged.class, l);
    }

    public static Lifecycle.Subscription<TutorialCompleted> onTutorialCompleted(
            java.util.function.Consumer<TutorialCompleted> l) {
        return Lifecycle.on(TutorialCompleted.class, l);
    }

    // ==================== 广播 ====================
    //
    // 这里只是把「构造事件 + 广播」写成一行，方便框架内部各发布点调用。
    // 内容与插件也可以直接用 Lifecycle.publish(new ...) 广播这些事件（或自定义事件），
    // 不必经过本类 —— 发布者和监听者一样，都只依赖事件类型本身。

    /** 广播「框架就绪」。 */
    public static void frameworkReady(String frameworkVersion) {
        Lifecycle.publish(new FrameworkReady(frameworkVersion));
    }

    /** 广播「主菜单已显示」。 */
    public static void menuShown(String title) {
        Lifecycle.publish(new MenuShown(title));
    }

    /** 广播「一局游戏开始」。 */
    public static void gameStarted(int startChapter, boolean fromSave) {
        Lifecycle.publish(new GameStarted(startChapter, fromSave));
    }

    /** 广播「章节已切换」。 */
    public static void chapterChanged(int chapter, String mode) {
        Lifecycle.publish(new ChapterChanged(chapter, mode));
    }

    /** 广播「存档已保存」。 */
    public static void gameSaved(int slot) {
        Lifecycle.publish(new GameSaved(slot));
    }

    /** 广播「读档完成」。 */
    public static void gameLoaded(int slot) {
        Lifecycle.publish(new GameLoaded(slot));
    }

    /** 广播「设置界面已打开」。 */
    public static void settingsOpened() {
        Lifecycle.publish(new SettingsOpened());
    }

    /** 广播「设置界面已关闭」。 */
    public static void settingsClosed(boolean applied) {
        Lifecycle.publish(new SettingsClosed(applied));
    }

    /** 广播「界面语言已切换」。 */
    public static void languageChanged(String languageCode) {
        Lifecycle.publish(new LanguageChanged(languageCode));
    }

    /** 广播「教程完成」。 */
    public static void tutorialCompleted() {
        Lifecycle.publish(new TutorialCompleted());
    }
}
