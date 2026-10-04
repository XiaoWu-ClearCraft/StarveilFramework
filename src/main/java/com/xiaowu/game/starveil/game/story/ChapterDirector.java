package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 章节总导演 —— 决定「现在跑哪一章」，并负责章节之间的推进。
 *
 * <h3>为什么需要它</h3>
 * 旧实现是<b>由世界 JSON 的 {@code chapter} 字段</b>驱动章节加载
 * （{@code WorldMap.createChapterInstance}），而且那套逻辑靠字符串猜类名，
 * 猜不到就静默跳过。结果是「章节」和「进了哪张地图」被绑死，
 * 想单纯放一段对话也必须先有一张地图。
 *
 * <p>现在章节与世界解耦：<b>从第 1 章开始，由章节代码自己决定什么时候进下一章</b>。
 *
 * <h3>命名规范（强制）</h3>
 * 章节类必须严格匹配：
 * <pre>com.xiaowu.game.starveil.content.chapter{N}.Chapter{N}</pre>
 * 例如 {@code com.xiaowu.game.starveil.content.chapter1.Chapter1}。
 * 不符合规范的类会被<b>直接拒绝</b>并抛出异常，不再静默跳过 ——
 * 静默跳过导致过章节死活不触发却毫无线索，排查成本极高。
 */
public final class ChapterDirector {

    /** 游戏默认从第几章开始（内容未指定时的兜底）。 */
    public static final int FIRST_CHAPTER = 1;

    /**
     * 起始章节号。由内容在 {@code content.init.init()} 里通过
     * {@link #setStartChapter(int)} 指定；不指定则从 {@link #FIRST_CHAPTER} 开始。
     *
     * <p>让内容决定的原因：一款游戏从第几章开始是<b>内容的事</b>
     * （序章、教程关、或直接从中间某章切入），框架写死第 1 章会逼着内容迁就框架。
     */
    private static volatile int startChapter = FIRST_CHAPTER;

    /** 内容指定起始章节。 */
    public static void setStartChapter(int chapter) {
        if (chapter < 0) {
            com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger(
                    "WARNING", "[ChapterDirector] 起始章节号非法，忽略: " + chapter);
            return;
        }
        startChapter = chapter;
        com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger(
                "INFO", "[ChapterDirector] 起始章节已设为: " + chapter);
    }

    /** 当前配置的起始章节号。 */
    public static int startChapter() {
        return startChapter;
    }

    /** 允许的包前缀。 */
    private static final String PACKAGE_PREFIX = "com.xiaowu.game.starveil.content.chapter";
    /** 允许的类名前缀。 */
    private static final String CLASS_PREFIX = "Chapter";

    /**
     * 章节类的强制命名规范：
     * {@code ...content.chapter<名字>.<Chapter><名字>}。
     */
    private static final Pattern REQUIRED_NAME =
            Pattern.compile("^com\\.xiaowu\\.game\\.starveil\\.content\\.chapter\\w*\\.Chapter\\w*$");

    private static final ChapterDirector INSTANCE = new ChapterDirector();

    private int currentChapter = FIRST_CHAPTER;
    /** 当前章节结束后要进入的章节；-1 表示不再继续。 */
    private int pendingChapter = -1;
    private boolean running = false;

    private ChapterDirector() {
    }

    public static ChapterDirector getInstance() {
        return INSTANCE;
    }

    // ==================== 命名规范 ====================

    /** 第 N 章要求的完整类名。 */
    public static String classNameFor(int chapter) {
        return PACKAGE_PREFIX + chapter + "." + CLASS_PREFIX + chapter;
    }

    /** 该类名是否符合章节命名规范。 */
    public static boolean isValidChapterClassName(String fqcn) {
        return fqcn != null && REQUIRED_NAME.matcher(fqcn).matches();
    }

    /**
     * 解析第 N 章的类，并强制校验命名规范。
     *
     * @throws IllegalArgumentException 类名不合规 / 类不存在 / 未实现 {@link StoryChapter}
     */
    @SuppressWarnings("unchecked")
    public static Class<? extends StoryChapter> resolve(int chapter) {
        if (chapter < 1) {
            throw new IllegalArgumentException("章节号必须 >= 1，实际 " + chapter);
        }
        String fqcn = classNameFor(chapter);
        if (!isValidChapterClassName(fqcn)) {
            throw new IllegalArgumentException(
                    "章节类名不符合规范（必须形如 " + PACKAGE_PREFIX + "N." + CLASS_PREFIX + "N）: " + fqcn);
        }

        Class<?> clazz;
        try {
            clazz = Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("找不到章节类: " + fqcn, e);
        }

        // 再查一次实际类名 —— 即使有人用别名/内部类绕进来也拦住
        if (!isValidChapterClassName(clazz.getName())) {
            throw new IllegalArgumentException("章节类名不符合规范: " + clazz.getName());
        }
        if (!StoryChapter.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException("章节类必须实现 StoryChapter: " + fqcn);
        }
        return (Class<? extends StoryChapter>) clazz;
    }

    // ==================== 状态 ====================

    /** 当前章节号。 */
    public int currentChapter() {
        return currentChapter;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 解析「起始章节」的运行模式。
     *
     * <p>在加载世界<b>之前</b>调用，用来决定要不要建世界。
     * 解析失败时退化成 {@link ChapterMode#NORMAL}，避免因为章节没写好连游戏都进不去。
     */
    public ChapterMode resolveStartMode() {
        return resolveMode(startChapter());
    }

    /** 解析指定章节的运行模式；失败时退化为 NORMAL。 */
    public ChapterMode resolveMode(int chapter) {
        try {
            StoryChapter probe = instantiate(resolve(chapter));
            ChapterMode mode = probe.mode();
            return mode != null ? mode : ChapterMode.NORMAL;
        } catch (Exception e) {
            Logger("WARN", "[ChapterDirector] 无法解析章节 " + chapter + " 的模式，按 NORMAL 处理: "
                    + e.getMessage());
            return ChapterMode.NORMAL;
        }
    }

    // ==================== 推进 ====================

    /** 从第 {@link #FIRST_CHAPTER} 章开始。 */
    public CompletableFuture<Void> start() {
        return gotoChapter(startChapter());
    }

    /**
     * 请求「本章结束后进入下一章」。由章节代码在 {@code write()} 里调用。
     */
    public void requestNextChapter() {
        requestChapter(currentChapter + 1);
    }

    /** 请求「本章结束后跳到指定章节」。 */
    public void requestChapter(int chapter) {
        if (chapter < 1) {
            Logger("WARN", "[ChapterDirector] 非法章节号，忽略: " + chapter);
            return;
        }
        pendingChapter = chapter;
        Logger("INFO", "[ChapterDirector] 已排定下一章: " + chapter);
    }

    /** 立刻切换到指定章节（会打断当前章节之后排队的内容）。 */
    public CompletableFuture<Void> gotoChapter(int chapter) {
        pendingChapter = -1;
        return runChapter(chapter);
    }

    private CompletableFuture<Void> runChapter(int chapter) {
        Class<? extends StoryChapter> type = resolve(chapter);
        StoryChapter instance = instantiate(type);
        ChapterMode mode = instance.mode();

        // 章节声明的模式必须与「世界是否已加载」一致。
        // 不一致时先走过场切换（例如 NORMAL → VISUAL_NOVEL 会把世界卸下），
        // 再开始跑章节 —— 这样章节作者不需要关心当前是哪种模式。
        return ensureMode(mode).thenCompose(ignored -> {
            currentChapter = chapter;
            pendingChapter = -1;
            running = true;
            Logger("INFO", "[ChapterDirector] 开始章节 " + chapter + "（模式 " + mode + "）");
            // 广播章节切换：谁关心谁处理（例如聊天记录据此决定是否清空），
            // 章节调度器不需要认识它们。没有监听者时这次广播就是空操作。
            com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents
                    .chapterChanged(chapter, mode.name());

            return StoryScripts.run(instance).whenComplete((r, ex) -> {
                running = false;
                if (ex != null) {
                    Logger("ERROR", "[ChapterDirector] 章节 " + chapter + " 异常结束: " + ex);
                }
                // 章节结束后再进入排定的下一章 —— 保证同一时刻只有一章在跑
                int next = pendingChapter;
                if (next >= 1) {
                    pendingChapter = -1;
                    // 章节之间的过场：落幕 → 卸下世界 → 交给下一章
                    //（由下一章自己决定要世界还是只要视觉小说）。
                    // 全程可等待，所以下一章的脚本一定是在画面切换完之后才开跑。
                    transitionToChapter(next).thenRun(() -> runChapter(next));
                }
            });
        });
    }

    /**
     * 章节之间的过场。
     *
     * <p>规则：<b>落幕之后先卸下世界，控制权交给下一章</b>，
     * 由下一章决定「要世界」还是「只要视觉小说」（{@link #ensureMode} 会在
     * 下一章的 {@code runChapter} 里做这件事）。
     *
     * <p>两种情况直接跳过，不白白黑一次屏：
     * <ul>
     *   <li>下一章声明 {@link ChapterMode#NORMAL} 且当前已有世界 ——
     *       两边都是「有世界」，卸了再装只是闪烁；换图由章节自己
     *       {@code s.enterWorld(...)}。</li>
     *   <li>特殊键 {@code starveil:chapter_fade} 被显式设为 false。</li>
     * </ul>
     */
    private CompletableFuture<Void> transitionToChapter(int next) {
        GameInstance gi = GameInstance.getCurrentInstance();
        if (gi == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!FrameworkDataKeys.CHAPTER_FADE.get()) {
            Logger("INFO", "[ChapterDirector] 特殊键要求不做章节过场，直接进入第 " + next + " 章");
            return CompletableFuture.completedFuture(null);
        }
        boolean nextWantsWorld = resolveMode(next) == ChapterMode.NORMAL;
        if (nextWantsWorld && gi.isWorldLoaded()) {
            return CompletableFuture.completedFuture(null);
        }

        Logger("INFO", "[ChapterDirector] 章节过场：落幕并卸下世界，交给第 " + next + " 章");
        return gi.curtainDownAndUnloadWorld();
    }

    /**
     * 让运行模式与章节要求一致，并保证需要世界的章节在脚本开跑前就有世界。
     *
     * <p>规则：
     * <ol>
     *   <li><b>章节声明要世界、当前却没有</b>：落幕 → 显示加载页面 → 加载世界
     *       （图由 {@link StoryChapter#world()} 指定，没指定则用内容在
     *       {@code ContentConfig.setStartWorld(...)} 里给的那张）→ 亮幕。
     *       整段过程都在这条 future 里，所以<b>亮幕完成之前章节脚本不会开始跑</b>。</li>
     *   <li><b>章节声明要世界、当前已有世界</b>：什么都不做（同图不重复加载，
     *       换图由章节自己 {@code s.enterWorld(...)}）。</li>
     *   <li><b>章节声明只要视觉小说</b>：落幕 → 卸下世界 → 亮幕（幕布背后是黑的，
     *       由视觉小说层接管）。卸下之后 HUD 不会自己冒出来。</li>
     * </ol>
     */
    private CompletableFuture<Void> ensureMode(ChapterMode mode) {
        GameInstance gi = GameInstance.getCurrentInstance();
        if (gi == null) {
            return CompletableFuture.completedFuture(null);
        }
        boolean wantWorld = mode == ChapterMode.NORMAL;
        if (wantWorld == gi.isWorldLoaded()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!wantWorld) {
            Logger("INFO", "[ChapterDirector] 章节要求仅视觉小说模式，卸下当前世界");
            return gi.enterVisualNovelMode();
        }
        return loadWorldForCurrentChapter(gi);
    }

    /**
     * 为当前章节加载世界：落幕 → 加载 → 亮幕，全程可等待。
     *
     * <p>找不到该用哪张图时<b>不猜</b>：记 ERROR 并保持加载页面，
     * 让章节脚本自己决定（{@code s.enterWorld(...)}）。猜错的代价是把玩家
     * 丢进一张跟他要玩的剧情无关的地图，比停在加载页面糟得多。
     */
    private CompletableFuture<Void> loadWorldForCurrentChapter(GameInstance gi) {
        String declared = null;
        try {
            declared = instantiate(resolve(currentChapter)).world();
        } catch (Exception e) {
            Logger("ERROR", "[ChapterDirector] 读取章节 " + currentChapter
                    + " 声明的世界失败: " + e.getMessage());
        }
        String map = declared != null && !declared.isBlank()
                ? declared
                : com.xiaowu.game.starveil.infrastructure.ContentConfig.startWorld();

        if (map == null || map.isBlank()) {
            Logger("ERROR", "[ChapterDirector] 第 " + currentChapter
                    + " 章声明需要世界，但没有可用的地图："
                    + "请在章节里覆写 world()，或由内容调用"
                    + " ContentConfig.setStartWorld(...) 指定起始世界。"
                    + "在此之前界面会停在加载页面，等章节自己调 s.enterWorld(...)。");
            // 停在加载页面（幕布落下 + 加载指示器），而不是亮幕给玩家一张空图
            return gi.holdLoadingPage();
        }

        Logger("INFO", "[ChapterDirector] 为第 " + currentChapter + " 章加载世界: " + map);
        return gi.enterNormalMode(map, null, null);
    }

    private StoryChapter instantiate(Class<? extends StoryChapter> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "无法实例化章节类（需要无参构造函数）: " + type.getName(), e);
        }
    }

    /** 新游戏 / 回主菜单时重置。 */
    public void reset() {
        currentChapter = startChapter();
        pendingChapter = -1;
        running = false;
    }
}
