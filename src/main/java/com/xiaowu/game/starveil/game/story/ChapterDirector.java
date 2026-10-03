package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.game.state.GameInstance;

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
            // 聊天记录默认随章节切换清空；特殊键 starveil:chat_history_keep_chapters
            // 设为 true 可跨章节保留。章节代码也可以随时自行 clear()。
            ChatHistory.getInstance().onChapterChanged();

            return StoryScripts.run(instance).whenComplete((r, ex) -> {
                running = false;
                if (ex != null) {
                    Logger("ERROR", "[ChapterDirector] 章节 " + chapter + " 异常结束: " + ex);
                }
                // 章节结束后再进入排定的下一章 —— 保证同一时刻只有一章在跑
                int next = pendingChapter;
                if (next >= 1) {
                    pendingChapter = -1;
                    runChapter(next);
                }
            });
        });
    }

    /**
     * 让运行模式与章节要求一致。
     *
     * <p>NORMAL → VISUAL_NOVEL 走的是和切换地图相同的过场，只是不加载新世界。
     * 反方向（VISUAL_NOVEL → NORMAL）需要重新建世界，目前还不支持，会明确报错。
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
        // 反向：引擎不猜该加载哪张图 —— 由章节自己在 write() 里调 s.enterWorld(...)
        Logger("INFO", "[ChapterDirector] 当前是仅视觉小说模式，第 " + currentChapter
                + " 章声明要求世界。请在章节里用 s.enterWorld(\"地图路径\") 进入世界；"
                + "引擎不会自动加载（因为无法知道该去哪张图）。");
        return CompletableFuture.completedFuture(null);
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
