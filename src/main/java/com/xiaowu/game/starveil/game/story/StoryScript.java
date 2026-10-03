package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.game.quest.QuestManager;
import com.xiaowu.game.starveil.game.world.MapManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.SaveDataManager;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;
import com.xiaowu.game.starveil.platform.api.SystemManagerInterface;
import com.xiaowu.game.starveil.render.effects.ScreenEffectsManager;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import com.xiaowu.game.starveil.ui.overlay.NotificationManager;
import com.xiaowu.game.starveil.ui.overlay.PopupManager;
import javafx.application.Platform;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 章节脚本编写门面 —— 剧情作者唯一需要接触的 API。
 *
 * <h2>为什么是「阻塞式」</h2>
 * 旧写法把剧情拆成一串 {@code DialogStep}，用 {@code insertChain} 返回步骤列表来分支、
 * 用 {@code setAnchor}/{@code addReturnStep} 来循环，最后每段剧情还得自己
 * {@code new Thread(...)}。作者要同时维护「步骤索引」「插入位置」「回调时机」三套心智模型。
 *
 * <p>本类把同一件事变成普通 Java 代码：脚本体运行在
 * {@link StoryScheduler} 的 <b>唯一一条剧情线程</b> 上，每个交互方法都会阻塞到玩家做完为止。
 * 于是 {@code while} / {@code if} / {@code try-finally} 直接就是剧情控制流：
 *
 * <pre>{@code
 * while (true) {
 *     String name = s.ask("你的名字是?");
 *     if (name.isEmpty()) { s.say("霁雾", "诶？你说什么了嘛？"); continue; }
 *     if (name.length() > 16) { s.say("霁雾", "好长.... 可以取个方便记忆的名字嘛？"); continue; }
 *     s.setPlayerName(name);
 *     break;
 * }
 * }</pre>
 *
 * <h2>线程规则</h2>
 * 所有交互方法只能在剧情线程调用（内部会校验并报错）。需要在 FX 线程做事时用
 * {@link #onFx(Runnable)}，需要跑任意逻辑用 {@link #run(Runnable)} / {@link #call(Supplier)}。
 */
public final class StoryScript {

    private static final int LOG_CAPACITY = 200;

    private final String label;
    private final Deque<String> log = new ArrayDeque<>();

    /** 当前是否持有操作权（true = 游戏被对话挡住，玩家不能自由行动）。 */
    private boolean holdingControl = true;

    private int stepIndex;

    StoryScript(String label) {
        this.label = label;
        log("脚本开始");
    }

    public String label() {
        return label;
    }

    // ==================== 对话 ====================

    /** 显示一句对话。 */
    public StoryScript say(String speaker, String text) {
        return say(speaker, text, null, null);
    }

    /** 显示一句带配音的对话。 */
    public StoryScript say(String speaker, String text, String voicePath) {
        return say(speaker, text, voicePath, null);
    }

    /**
     * 显示一句对话。
     *
     * @param voicePath        配音资源路径，可为 null
     * @param standeeImagePath 立绘路径；传 {@code ""} 表示收起立绘，null 表示不变
     */
    public StoryScript say(String speaker, String text, String voicePath, String standeeImagePath) {
        requireStoryThread();
        step("say", speaker, text);
        await(chat().showDialog(StoryText.format(speaker), StoryText.format(text),
                voicePath, standeeImagePath));
        return this;
    }

    /** 旁白（无说话人）。 */
    public StoryScript narrate(String text) {
        return say(null, text);
    }

    /**
     * 把多段文本按 {@code <more>} 标记合成一次点击推进的多段对话。
     */
    public StoryScript multi(String speaker, String... parts) {
        return say(speaker, String.join("<more>", parts));
    }

    /** 切换立绘（不显示对话）。传 null 或空串收起立绘。 */
    public StoryScript standee(String path) {
        requireStoryThread();
        step("standee", path);
        chat().setStandee(path);
        return this;
    }

    /** 收起立绘。 */
    public StoryScript hideStandee() {
        return standee("");
    }

    /** 显示剧情图。 */
    public StoryScript image(String path) {
        requireStoryThread();
        step("image", path);
        await(chat().showStoryImage(path));
        return this;
    }

    /** 隐藏剧情图。 */
    public StoryScript hideImage() {
        requireStoryThread();
        step("hideImage");
        await(chat().hideStoryImage());
        return this;
    }

    // ==================== 交互 ====================

    /** 让玩家输入文本，返回输入内容。 */
    public String ask(String prompt) {
        return ask(prompt, null);
    }

    /**
     * 让玩家输入文本。
     *
     * @param simulatedText 预填文本（玩家可以改），常用于「请输入你的名字」这类场景
     */
    public String ask(String prompt, String simulatedText) {
        requireStoryThread();
        String text = await(chat().showInputDialog(StoryText.format(prompt), simulatedText));
        step("ask", prompt, "->", text);
        return text == null ? "" : text;
    }

    /** 让玩家输入日期，返回输入内容。 */
    public String askDate(String prompt) {
        requireStoryThread();
        String text = await(chat().showDateInputDialog(StoryText.format(prompt)));
        step("askDate", prompt, "->", text);
        return text;
    }

    /** 让玩家在若干选项中选择，返回选项下标（取消为 -1）。 */
    public int choose(String prompt, String... options) {
        return choose(null, prompt, Arrays.asList(options));
    }

    /** 让玩家在若干选项中选择（带说话人）。 */
    public int choose(String speaker, String prompt, String... options) {
        return choose(speaker, prompt, Arrays.asList(options));
    }

    public int choose(String prompt, List<String> options) {
        return choose(null, prompt, options);
    }

    public int choose(String speaker, String prompt, List<String> options) {
        requireStoryThread();
        step("choose", prompt, options);
        Integer index = await(chat().showChoiceDialog(StoryText.format(speaker),
                StoryText.format(prompt), options));
        step("choose -> ", index);
        return index == null ? -1 : index;
    }

    /** 弹出地图选择器，返回被点击的按钮 id（取消为 -1）。 */
    public int map() {
        return map(null, null);
    }

    public int map(List<String> allowedMaps, List<Integer> allowedButtons) {
        requireStoryThread();
        step("map");
        Integer id = await(MapManager.getInstance().showMapSelector(allowedMaps, allowedButtons));
        return id == null ? -1 : id;
    }

    // ==================== 提示 ====================

    /** 右上角通知（默认 5 秒、无图标）。 */
    public StoryScript notify(String title, String message) {
        return notify(title, message, null, 5);
    }

    public StoryScript notify(String title, String message, String iconPath, double seconds) {
        step("notify", title, message);
        String t = StoryText.format(title);
        String m = StoryText.format(message);
        // 通知系统会创建 JavaFX 节点，必须在 FX 线程上完成首次初始化
        onFx(() -> NotificationManager.getInstance().showNotification(t, m, iconPath, seconds));
        return this;
    }

    /** 游戏内弹窗（不等待玩家关闭）。 */
    public StoryScript popup(String title, String message) {
        step("popup", title, message);
        String t = StoryText.format(title);
        String m = StoryText.format(message);
        onFx(() -> PopupManager.getInstance().showPopup(t, m));
        return this;
    }

    /** 系统级提示框（Windows 上是原生 MessageBox），等待玩家点击确定。 */
    public StoryScript dialog(String message) {
        return dialog(GameConstants.GAME_TITLE, message);
    }

    /**
     * 系统级提示框，等待玩家点击确定。
     *
     * <p>由剧情线程发起，因此不会卡住游戏渲染线程。
     */
    public StoryScript dialog(String title, String message) {
        requireStoryThread();
        step("dialog", title, message);
        SystemManagerInterface ops = SystemManagerFactory.getInstance();
        ops.showInfo(StoryText.format(title), StoryText.format(message));
        return this;
    }

    // ==================== 等待 ====================

    /** 等待若干毫秒（剧情线程休眠，不阻塞游戏）。 */
    public StoryScript delay(long millis) {
        requireStoryThread();
        step("delay", millis + "ms");
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoryCancelledException("延时被中断");
        }
        return this;
    }

    /** 等待一个剧情信号（见 {@link StorySignals}）。 */
    public StoryScript await(String signal) {
        requireStoryThread();
        step("await", signal);
        StorySignals.await(signal);
        return this;
    }

    /** 等待一个剧情信号，超时后继续。 */
    public StoryScript await(String signal, long timeoutMillis) {
        requireStoryThread();
        step("await", signal, timeoutMillis + "ms");
        StorySignals.await(signal, timeoutMillis);
        return this;
    }

    /** 每 50ms 轮询一次条件，直到成立。 */
    public StoryScript awaitUntil(BooleanSupplier condition) {
        requireStoryThread();
        step("awaitUntil");
        while (!condition.getAsBoolean()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new StoryCancelledException("条件等待被中断");
            }
        }
        return this;
    }

    /**
     * 等待新手教程完成。
     *
     * <p>期间会把操作权交还给玩家——教程本身要求玩家自己走动、疾跑，
     * 教程结束后自动收回操作权继续剧情。
     */
    public StoryScript awaitTutorial() {
        giveControl();
        try {
            return await(StorySignals.TUTORIAL_COMPLETED);
        } finally {
            takeControl();
        }
    }

    // ==================== 操作权 ====================

    /**
     * 把操作权交还玩家：恢复 HUD 与输入，但剧情仍在继续（脚本会在下一个阻塞点等待）。
     */
    public StoryScript giveControl() {
        if (holdingControl) {
            holdingControl = false;
            step("giveControl");
            chat().unblockGameLayer();
        }
        return this;
    }

    /** 收回操作权（通常是玩家自由活动结束后继续演出）。 */
    public StoryScript takeControl() {
        if (!holdingControl) {
            holdingControl = true;
            step("takeControl");
            chat().blockGameLayer();
        }
        return this;
    }

    boolean isHoldingControl() {
        return holdingControl;
    }

    /**
     * 让指定的 ECS 系统在<b>剧情暂停期间</b>继续运行。
     *
     * <p>剧情（视觉小说）期间整个世界默认冻结 —— 玩家、NPC、体力恢复等全部停下，
     * 也不会弹暂停界面。这个方法用来开一个口子，例如让 NPC 在对话时继续走动：
     *
     * <pre>{@code
     * s.keepRunningDuringStory(NpcSystem.class);
     * }</pre>
     *
     * <p>表现层的 {@link com.xiaowu.game.starveil.game.ecs.sys.SpriteSyncSystem}
     * 已经默认放行，不需要（也不应该）在这里重复声明。
     */
    @SafeVarargs
    public final StoryScript keepRunningDuringStory(
            Class<? extends com.xiaowu.game.starveil.game.ecs.EcsSystem>... systemTypes) {
        step("keepRunningDuringStory");
        com.xiaowu.game.starveil.game.ecs.World world =
                com.xiaowu.game.starveil.game.state.GameInstance.getEcsWorld();
        if (world == null) {
            log("keepRunningDuringStory: ECS 世界不存在（视觉小说模式？），已忽略");
            return this;
        }
        for (Class<? extends com.xiaowu.game.starveil.game.ecs.EcsSystem> type : systemTypes) {
            world.exemptFromStoryPause(type);
        }
        return this;
    }

    /** 清除所有「剧情暂停豁免」。 */
    public StoryScript clearStoryPauseExemptions() {
        step("clearStoryPauseExemptions");
        com.xiaowu.game.starveil.game.ecs.World world =
                com.xiaowu.game.starveil.game.state.GameInstance.getEcsWorld();
        if (world != null) {
            world.clearStoryPauseExemptions();
        }
        return this;
    }

    // ==================== 章节推进 ====================

    /**
     * 本章结束后进入下一章。
     *
     * <p>不会立即切换 —— 当前 {@code write()} 跑完、本章的收尾动作（收立绘、
     * 恢复操作权等）都执行完之后，{@link ChapterDirector} 才会启动下一章。
     */
    public StoryScript nextChapter() {
        step("nextChapter");
        ChapterDirector.getInstance().requestNextChapter();
        return this;
    }

    /** 本章结束后跳到指定章节。 */
    public StoryScript gotoChapter(int chapter) {
        step("gotoChapter", chapter);
        ChapterDirector.getInstance().requestChapter(chapter);
        return this;
    }

    /**
     * 从「仅视觉小说」模式进入世界 —— 走与切换地图<b>相同的过场</b>，
     * 方法返回时世界已经挂好，章节可以接着往下演。
     *
     * <p>地图由章节自己决定，引擎不需要猜「该回到哪张图」，所以也不必做世界状态快照。
     *
     * <pre>{@code
     * // 本章声明为 VISUAL_NOVEL：进来时引擎已自动卸下世界
     * @Override public ChapterMode mode() { return ChapterMode.VISUAL_NOVEL; }
     *
     * @Override public void write(StoryScript s) {
     *     s.image("starveil:textures/backgrounds/void-fog.png");
     *     s.narrate("……");
     *     s.enterWorld("starveil:data/worlds/wu-home.json");   // 过场结束后已站在屋里
     *     s.say("霁雾", "我们到了。");
     * }
     * }</pre>
     */
    public StoryScript enterWorld(String mapFile) {
        return enterWorld(mapFile, null, null);
    }

    /**
     * 进入指定世界并指定出生点。
     *
     * @param spawnX 出生点 X；{@code null} 表示用地图自带的 spawn
     * @param spawnY 出生点 Y；{@code null} 表示用地图自带的 spawn
     */
    public StoryScript enterWorld(String mapFile, Double spawnX, Double spawnY) {
        requireStoryThread();
        step("enterWorld", mapFile);
        com.xiaowu.game.starveil.game.state.GameInstance gi =
                com.xiaowu.game.starveil.game.state.GameInstance.getCurrentInstance();
        if (gi == null) {
            throw new StoryScriptException("没有游戏实例，无法进入世界");
        }
        // 剧情线程上阻塞等待过场结束 —— 过场跑在 FX 线程上，两者不会互相等待
        gi.enterNormalMode(mapFile, spawnX, spawnY).join();
        return this;
    }

    // ==================== 状态存取 ====================

    /** 读取存档内的剧情变量。 */
    public String flag(String key) {
        return SaveDataManager.getInstance().getString(key, null);
    }

    public boolean bool(String key, boolean defaultValue) {
        return SaveDataManager.getInstance().getBoolean(key, defaultValue);
    }

    public int counter(String key) {
        return SaveDataManager.getInstance().getInt(key, 0);
    }

    /** 写入存档变量（随存档保存）。 */
    public StoryScript set(String key, String value) {
        step("set", key, "=", value);
        SaveDataManager.getInstance().setString(key, value);
        return this;
    }

    public StoryScript setFlag(String key, boolean value) {
        step("setFlag", key, "=", value);
        SaveDataManager.getInstance().setBoolean(key, value);
        return this;
    }

    public StoryScript setCounter(String key, int value) {
        step("setCounter", key, "=", value);
        SaveDataManager.getInstance().setInt(key, value);
        return this;
    }

    public StoryScript addCounter(String key, int delta) {
        step("addCounter", key, "+", delta);
        SaveDataManager.getInstance().incrementInt(key, delta);
        return this;
    }

    /** 读取全局配置项（跨存档）。 */
    public String data(String key) {
        return DataManager.getString(key, null);
    }

    public StoryScript setData(String key, String value) {
        step("setData", key, "=", value);
        DataManager.set(key, value);
        return this;
    }

    /** 玩家当前名字。 */
    public String playerName() {
        return DataManager.getString("starveil:player_name", "");
    }

    /** 设置玩家名字（同时写入全局配置）。 */
    public StoryScript setPlayerName(String name) {
        step("setPlayerName", name);
        DataManager.set("starveil:player_name", name);
        return this;
    }

    // ==================== 屏幕特效（只改遮罩层内容） ====================

    /**
     * 降低遮罩层的可见帧率：把下层画面捕获到遮罩层并按目标帧率刷新。
     *
     * <p>只改变遮罩层显示的内容，不动任何系统设置；<b>只允许降低帧数</b>，
     * 请求值不低于游戏实测帧率时会输出 WARNING 并忽略。
     *
     * @param spec {@code "50%"}（原帧率的百分之多少）或 {@code "30"} / {@code "10"}（具体帧率）
     */
    public StoryScript frameThrottle(String spec) {
        step("frameThrottle", spec);
        onFx(() -> ScreenEffectsManager.getInstance().setFrameThrottle(spec));
        return this;
    }

    /** 关闭降帧保持，遮罩层重新透出实时画面。 */
    public StoryScript clearFrameThrottle() {
        step("clearFrameThrottle");
        onFx(() -> ScreenEffectsManager.getInstance().clearFrameThrottle());
        return this;
    }

    /**
     * 花屏（画面故障）。
     *
     * <p>效果会保持约 130ms，这段时间内重复调用会持续刷新，从而形成连续故障。
     *
     * @param fraction 强度 0.0-1.0
     */
    public StoryScript glitch(double fraction) {
        step("glitch", fraction);
        onFx(() -> ScreenEffectsManager.getInstance().glitch(fraction));
        return this;
    }

    /** 指定区域的花屏。坐标是遮罩层（=整屏）坐标。 */
    public StoryScript glitch(double fraction, double x, double y, double w, double h) {
        step("glitch", fraction, x, y, w, h);
        onFx(() -> ScreenEffectsManager.getInstance().glitch(fraction, x, y, w, h));
        return this;
    }

    /** 清除花屏，恢复实时画面。 */
    public StoryScript clearEffects() {
        step("clearEffects");
        onFx(() -> ScreenEffectsManager.getInstance().clearEffects());
        return this;
    }

    /** 伪蓝屏。 */
    public StoryScript blueScreen() {
        step("blueScreen");
        onFx(() -> ScreenEffectsManager.getInstance().showBlueScreen());
        return this;
    }

    /** 关闭伪蓝屏（默认 F12 也可以关）。 */
    public StoryScript dismissBlueScreen() {
        step("dismissBlueScreen");
        onFx(() -> ScreenEffectsManager.getInstance().dismissBlueScreen());
        return this;
    }

    // ==================== 任务 ====================

    public StoryScript quest(String questId) {
        step("quest", questId);
        onFx(() -> QuestManager.getInstance().acceptQuest(questId));
        return this;
    }

    public StoryScript questProgress(int value) {
        step("questProgress", value);
        onFx(() -> QuestManager.getInstance().setQuestProgress(value));
        return this;
    }

    public StoryScript questDone() {
        step("questDone");
        onFx(() -> QuestManager.getInstance().completeQuest());
        return this;
    }

    // ==================== 任意逻辑 ====================

    /** 在剧情线程上执行任意代码（可以直接用阻塞式调用）。 */
    public StoryScript run(Runnable action) {
        requireStoryThread();
        action.run();
        return this;
    }

    /** 在剧情线程上执行并取回结果。 */
    public <T> T call(Supplier<T> supplier) {
        requireStoryThread();
        return supplier.get();
    }

    /** 在 JavaFX 线程上同步执行（剧情线程等待其完成）。 */
    public StoryScript onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return this;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                action.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        await(future);
        return this;
    }

    /** 在 JavaFX 线程上同步取值。 */
    public <T> T onFx(Supplier<T> supplier) {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return await(future);
    }

    // ==================== 调试 ====================

    /** 记一条剧情日志（会出现在 DebugWindow 的剧情页）。 */
    public void log(String message) {
        if (log.size() >= LOG_CAPACITY) {
            log.pollFirst();
        }
        log.addLast(message);
    }

    public List<String> recentLog() {
        return new ArrayList<>(log);
    }

    // ==================== 内部 ====================

    private ChatManager chat() {
        return ChatManager.getInstance();
    }

    private void step(Object... parts) {
        stepIndex++;
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(String.format("%03d", stepIndex)).append("] ");
        for (Object part : parts) {
            if (part == null) continue;
            if (part instanceof List<?> list) {
                sb.append(list);
            } else {
                sb.append(part).append(' ');
            }
        }
        log(sb.toString().trim());
    }

    /** 阻塞等待一个 UI 操作完成。 */
    private <T> T await(CompletableFuture<T> future) {
        // 先检查取消标记：避免「取消瞬间刚好不在阻塞点」时脚本继续往下跑
        if (Thread.currentThread().isInterrupted()) {
            throw new StoryCancelledException("剧情已取消");
        }
        if (future == null) {
            return null;
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StoryCancelledException("剧情等待被中断");
        } catch (CancellationException e) {
            throw new StoryCancelledException("剧情等待被取消");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Logger("ERROR", "[StoryScript] " + label + " 步骤执行失败: " + cause);
            throw new StoryScriptException(label + " 步骤执行失败: " + cause, cause);
        }
    }

    private void requireStoryThread() {
        if (!StoryScheduler.isStoryThread()) {
            throw new IllegalStateException(
                    "StoryScript 的交互方法只能在剧情线程调用；当前线程: "
                            + Thread.currentThread().getName()
                            + "。若在 FX/事件回调里，请改用 StoryScripts.run(...) 投递脚本。");
        }
    }
}
