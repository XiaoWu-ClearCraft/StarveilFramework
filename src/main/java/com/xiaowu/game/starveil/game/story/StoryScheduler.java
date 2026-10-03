package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 剧情调度器 —— 全游戏 <b>唯一</b> 一条剧情线程。
 *
 * <p>旧写法里每段剧情各自 {@code new Thread(...)}：一段对话一个线程、
 * 每个「等教程完成」的回调再开一个线程，而且它们还要和 FX 线程互相等待。
 * 现在所有章节脚本都排进这一条线程顺序执行：
 *
 * <ul>
 *   <li>脚本可以直接写阻塞代码（{@code StoryScript} 的每个交互方法都会等玩家做完）；</li>
 *   <li>线程数是常数 1，和章节数量无关；</li>
 *   <li>脚本之间天然互斥，不会出现两段剧情同时抢对话框；</li>
 *   <li>读档 / 退出时 {@link #cancelAll()} 一次就能清干净。</li>
 * </ul>
 *
 * <p>线程是 daemon，且只做调度，不做渲染——所有 UI 操作都会由
 * {@code ChatManager} / {@code RenderEngine} 投递到 FX 线程执行。
 */
public final class StoryScheduler {

    private static final StoryScheduler INSTANCE = new StoryScheduler();

    public static StoryScheduler getInstance() {
        return INSTANCE;
    }

    /** 章节脚本体。 */
    @FunctionalInterface
    public interface StoryBody {
        void write(StoryScript script) throws Exception;
    }

    private final ExecutorService executor;
    private final ThreadLocal<Boolean> storyThread = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final AtomicLong generation = new AtomicLong();

    private volatile Thread runningThread;
    private volatile String runningLabel;
    private volatile boolean uiReady;

    private StoryScheduler() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "starveil-story");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 当前线程是否就是剧情线程。 */
    public static boolean isStoryThread() {
        return Boolean.TRUE.equals(INSTANCE.storyThread.get());
    }

    /** 是否有脚本正在执行。 */
    public boolean isRunning() {
        return runningThread != null;
    }

    /** 正在执行的脚本名（无则返回 null）。 */
    public String getRunningLabel() {
        return runningLabel;
    }

    /**
     * 提交一个章节脚本，排队执行。
     *
     * @param label 用于日志与调试的脚本名，例如 {@code "chapter1/author_chapter1"}
     * @return 脚本结束（正常/失败/取消）时完成的 future
     */
    public CompletableFuture<Void> submit(String label, StoryBody body) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        long myGeneration = generation.get();

        Logger("INFO", "[StoryScheduler] 排队剧情: " + label);
        executor.execute(() -> {
            if (myGeneration != generation.get()) {
                Logger("INFO", "[StoryScheduler] 丢弃已过期的剧情: " + label);
                done.cancel(false);
                return;
            }

            storyThread.set(Boolean.TRUE);
            runningThread = Thread.currentThread();
            runningLabel = label;

            ChatManager chat = ChatManager.getInstance();
            StoryScript script = null;
            try {
                // 确保 ChatManager（含 JavaFX 节点）在 FX 线程上完成初始化
                ensureUiReady();
                chat.blockGameLayer();

                script = new StoryScript(label);
                chat.setActiveStory(script);

                body.write(script);
                done.complete(null);
            } catch (StoryCancelledException e) {
                Logger("INFO", "[StoryScheduler] 剧情已取消: " + label);
                done.cancel(false);
            } catch (Throwable t) {
                Logger("ERROR", "[StoryScheduler] 剧情执行失败: " + label + " - " + t);
                t.printStackTrace();
                done.completeExceptionally(t);
            } finally {
                // 注意：不清空 activeStory —— 保留最后一个脚本便于在 DebugWindow 里复盘
                chat.unblockGameLayer();
                runningThread = null;
                runningLabel = null;
                storyThread.set(Boolean.FALSE);
                Logger("INFO", "[StoryScheduler] 剧情结束: " + label);
            }
        });
        return done;
    }

    /**
     * 取消当前脚本并丢弃所有排队脚本。
     *
     * <p>正在等待玩家的脚本会被中断，阻塞点抛 {@link StoryCancelledException} 后退出。
     */
    public void cancelAll() {
        generation.incrementAndGet();
        Thread thread = runningThread;
        if (thread != null) {
            Logger("INFO", "[StoryScheduler] 取消正在执行的剧情: " + runningLabel);
            thread.interrupt();
        }
    }

    /** 进程退出时关闭调度线程。 */
    public void shutdown() {
        cancelAll();
        executor.shutdownNow();
    }

    /**
     * 在 FX 线程上同步初始化对话系统。
     *
     * <p>{@code ChatManager} 的构造会创建 JavaFX 节点，必须在 FX 线程上完成；
     * 之后从剧情线程直接取单例即可。
     */
    private void ensureUiReady() {
        if (uiReady) {
            ChatManager.getInstance();
            return;
        }
        if (Platform.isFxApplicationThread()) {
            ChatManager.getInstance();
            uiReady = true;
            return;
        }
        CompletableFuture<Void> ready = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                ChatManager.getInstance();
                ready.complete(null);
            } catch (Throwable t) {
                ready.completeExceptionally(t);
            }
        });
        try {
            ready.get();
            uiReady = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoryCancelledException("等待 UI 初始化被中断");
        } catch (Exception e) {
            throw new StoryScriptException("对话系统初始化失败", e);
        }
    }
}
