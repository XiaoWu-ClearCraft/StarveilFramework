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

        // 章节切换的「落幕」在上一章结束时已经做掉了（见 transitionToChapter），
        // 这里只负责把运行模式调整到位。
        //
        // 注意 ensureMode 在「章节要世界、当前没有世界」时【不做等待】：
        // 它只把加载页面准备好（黑幕 + 加载指示器）就把控制权交给本章脚本 ——
        // 真正的加载由章节自己调用 s.enterWorld(...) 发起。
        // 这样「谁决定去哪张图」始终只有一个答案：章节。
        return ensureMode(mode).thenCompose(ignored -> {
            currentChapter = chapter;
            pendingChapter = -1;
            running = true;
            Logger("INFO", "[ChapterDirector] 开始章节 " + chapter + "（模式 " + mode + "）");
            // 广播章节切换：谁关心谁处理（例如聊天记录据此决定是否清空），
            // 章节调度器不需要认识它们。没有监听者时这次广播就是空操作。
            com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents
                    .chapterChanged(chapter, mode.name());

            // 控制权在这一刻交给本章：脚本随后开始跑。
            // 本章应当先调用 s.enterWorld(...) —— 它会等「挂载世界 + 亮幕动画」
            // 全部结束才返回，所以紧接着的对话不会出现在黑屏上。
            return StoryScripts.run(instance);
        }).whenComplete((r, ex) -> {
            running = false;
            if (ex != null) {
                Logger("ERROR", "[ChapterDirector] 章节 " + chapter + " 异常结束: " + ex);
            }
            int next = pendingChapter;
            if (next >= 1) {
                pendingChapter = -1;
                // 章节之间的过场：落幕 → 交给下一章（由它自己加载世界）
                transitionToChapter(next).thenRun(() -> runChapter(next));
            }
        });
    }

    /**
     * 章节之间的过场：落幕，然后把控制权交给下一章。
     *
     * <p><b>不在这里卸下世界</b> —— 卸不卸由下一章的 {@code mode()} 决定
     * （要世界就接着用，只要视觉小说就由 {@link #ensureMode} 卸掉）。
     * 这样「换图」的责任仍然只在章节自己身上，框架不会替它做决定。
     *
     * <p>两种情况跳过，不白白黑一次屏：
     * <ul>
     *   <li>下一章声明 {@link ChapterMode#NORMAL} 且当前已有世界 ——
     *       两边都是「有世界」，接着用就行，黑一次只是闪烁。</li>
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
            // 下一章接着用当前世界，不需要黑屏；要换图由它自己 enterWorld
            return CompletableFuture.completedFuture(null);
        }

        if (!gi.isWorldLoaded()) {
            // 本来就没有世界（上一章是纯视觉小说）：已经在一片黑幕上，
            // 直接交接即可，不必再落幕一次
            return CompletableFuture.completedFuture(null);
        }

        Logger("INFO", "[ChapterDirector] 章节过场：落幕，交给第 " + next + " 章");
        return gi.curtainDownForChapter();
    }

    /**
     * 把运行环境调整到章节声明的模式。
     *
     * <p><b>框架不猜世界。</b>「去哪张图」永远只有一个答案来源：章节自己调用
     * {@code s.enterWorld(...)}。所以「章节要世界、当前却没有世界」时这里只做一件事
     * —— 把加载页面准备好（黑幕 + 加载指示器），然后就把控制权交给章节脚本，
     * 真正的加载由脚本发起。
     *
     * <p>章节切换的时间线因此是：
     * <pre>
     *   上一章结束
     *     → 落幕（等动画结束）
     *     → 本章脚本开始跑（画面是黑幕 + 加载指示器）
     *     → 本章调 s.enterWorld(...)：挂载世界 → 亮幕（等动画结束）
     *     → 之后的对话与演出
     * </pre>
     * 最后一步「亮幕动画结束才返回」，所以不会出现「黑屏上先弹出对话框」。
     *
     * <p><b>章节只要视觉小说</b>时卸下世界，让脚本从干净的黑幕开始
     * （章节通常紧接着 {@code s.image(...)} 铺背景）。
     */
    private CompletableFuture<Void> ensureMode(ChapterMode mode) {
        GameInstance gi = GameInstance.getCurrentInstance();
        if (gi == null) {
            return CompletableFuture.completedFuture(null);
        }
        boolean wantWorld = mode == ChapterMode.NORMAL;

        if (!wantWorld) {
            if (gi.isWorldLoaded()) {
                Logger("INFO", "[ChapterDirector] 章节要求仅视觉小说模式，卸下当前世界");
                return gi.enterVisualNovelMode();
            }
            // 本来就没有世界：什么都不用做，脚本直接在黑幕上开始
            return CompletableFuture.completedFuture(null);
        }

        if (gi.isWorldLoaded()) {
            // 已经有世界：本章接着用（要换图就自己 enterWorld）
            return CompletableFuture.completedFuture(null);
        }

        Logger("INFO", "[ChapterDirector] 第 " + currentChapter
                + " 章声明需要世界。加载页面就绪后即交给本章，"
                + "由它自己调 s.enterWorld(\"地图路径\") —— 框架不猜该去哪张图。");
        return gi.holdLoadingPage();
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
