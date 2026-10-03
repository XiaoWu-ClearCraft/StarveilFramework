package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.game.ecs.comp.Stamina;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.game.event.EventCallbackManager;
import com.xiaowu.game.starveil.game.quest.QuestManager;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.core.GameUI;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import com.xiaowu.game.starveil.ui.overlay.NotificationManager;
import com.xiaowu.game.starveil.ui.overlay.TutorialOverlay;
import com.xiaowu.game.starveil.config.GameConstants;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 教程管理器 —— 负责新手教程的流程编排。
 *
 * <h3>流程</h3>
 * <pre>
 *   欢迎  →  UI 介绍（逐个高亮生命值/体力值/…）  →  移动说明
 *         →  移动练习（四方向各按住 N 秒）       →  疾跑说明
 *         →  疾跑练习（按住 N 秒）              →  完成提示
 * </pre>
 *
 * <h3>界面分工</h3>
 * <ul>
 *   <li><b>说明文字</b>用 {@link TutorialOverlay}：压暗画面 + 中央白色文字，
 *       不套弹窗外框。要介绍某个 HUD 元素时把它从遮罩里「挖」出来高亮。</li>
 *   <li><b>练习进度</b>用 {@link NotificationManager} 的<b>常驻通知</b>：
 *       右上角、不自动消失、带一根进度条。移动和疾跑各用一根。</li>
 * </ul>
 *
 * <h3>其它要点</h3>
 * <ul>
 *   <li>要介绍哪些 UI 由 GameUI 决定，不写死在这里 ——
 *       见 {@link GameUI#availableTutorialTargets()}，新增 HUD 元素只需在
 *       {@link GameUI.TutorialTarget} 里加一项。</li>
 *   <li>移动进度是<b>合计</b>的：一格最多贡献 1/4，所以必须四个方向都练满才到 100%，
 *       猛按一个键是刷不满的（见 {@link HoldProgressTracker#totalProgress()}）。</li>
 *   <li>教程期间会冻结剧情推进并接管画面，见 {@link #initialize}。</li>
 * </ul>
 */
public class TutorialManager {

    /** 每个方向需要按住并实际移动的秒数。可用 -Dstarveil.tutorial.moveHoldSeconds 覆盖。 */
    private static final double MOVE_HOLD_SECONDS =
            readSeconds("starveil.tutorial.moveHoldSeconds", 1.5);

    /** 疾跑需要持续的秒数。 */
    private static final double SPRINT_HOLD_SECONDS =
            readSeconds("starveil.tutorial.sprintHoldSeconds", 1.5);

    /** 判定「这一帧确实移动了」的最小位移（像素），用来排除浮点噪声。 */
    private static final double MOVE_EPSILON = 1e-3;

    // 方向槽位下标
    private static final int SLOT_UP = 0;
    private static final int SLOT_DOWN = 1;
    private static final int SLOT_LEFT = 2;
    private static final int SLOT_RIGHT = 3;

    private enum Step {
        IDLE,
        WELCOME,
        UI_INTRO,
        MOVE_INTRO,
        MOVE_PRACTICE,
        SPRINT_INTRO,
        SPRINT_PRACTICE,
        /** 完成提示已经弹出，等玩家按继续 */
        DONE_INTRO,
        FINISHED
    }

    private static TutorialManager instance;

    private InputHandler inputHandler;
    private Step step = Step.IDLE;
    private boolean pendingTutorialStart = false;

    /** 当前「说明文字」的完成信号；玩家按继续后完成。 */
    private CompletableFuture<Void> infoFuture;

    private List<GameUI.TutorialTarget> introQueue = new ArrayList<>();
    private int introIndex = 0;

    private HoldProgressTracker moveTracker;
    private HoldProgressTracker sprintTracker;

    /** 练习用的常驻通知（一根合计进度条）。 */
    private NotificationManager.PersistentNotification moveNotification;
    private NotificationManager.PersistentNotification sprintNotification;
    private int lastMoveDoneCount = -1;

    private double lastPlayerX;
    private double lastPlayerY;
    private boolean hasLastPosition = false;

    private TutorialManager() {
    }

    public static TutorialManager getInstance() {
        if (instance == null) {
            instance = new TutorialManager();
        }
        return instance;
    }

    private static double readSeconds(String property, double fallback) {
        try {
            double v = Double.parseDouble(System.getProperty(property, String.valueOf(fallback)));
            return v > 0 ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ==================== 启动 ====================

    public void startTutorial() {
        startTutorial(GameInstance.getInputHandlerStatic());
    }

    public void startTutorial(InputHandler handler) {
        if (isGamePaused()) {
            pendingTutorialStart = true;
            LoggerManager.Logger("INFO", "游戏暂停中，教程将在恢复后启动");
            return;
        }
        QuestManager.getInstance().acceptQuest("quest_001");
        initialize(handler);
        LoggerManager.Logger("INFO", "新手教程已启动");
    }

    public void initialize(InputHandler handler) {
        this.inputHandler = handler;
        this.introQueue = new ArrayList<>();
        this.introIndex = 0;
        this.infoFuture = null;
        this.moveTracker = null;
        this.sprintTracker = null;
        this.moveNotification = null;
        this.sprintNotification = null;
        this.lastMoveDoneCount = -1;
        this.hasLastPosition = false;

        // 聚光灯层挂在逻辑画布的模态宿主上 —— 与 HUD 同坐标系，且在对话框之上
        TutorialOverlay.getInstance().attach();

        // 教程期间冻结剧情推进：否则玩家点着点着就把对话翻过去了，注意力完全不在教程上。
        // ⚠️ 前提是剧情脚本此时已经走到 awaitTutorial()（不再等待点击），
        //    否则「剧情等点击、玩家点不动」会死锁。顺序见 Chapter1.meetJiwu。
        ChatManager.getInstance().setStoryAdvanceLocked(true);
        // 视觉小说模式会把 GameUI 整体隐藏，不接管画面就没有东西可以高亮
        ChatManager.getInstance().handOverToHudForTutorial();

        showInfo("欢迎来到雾隐星阑",
                "你刚进入游戏，先来熟悉一下界面和操作吧",
                null, Step.WELCOME);
    }

    // ==================== 每帧更新 ====================

    /**
     * 更新教程状态（每帧调用）。
     *
     * @param deltaTime 本帧时长（秒），用于累计「按住」进度
     */
    public void update(double deltaTime) {
        if (!isGameRunning()) {
            return;
        }
        if (pendingTutorialStart) {
            startPendingTutorial();
            return;
        }

        switch (step) {
            case WELCOME -> {
                if (infoDone()) {
                    beginUiIntro();
                }
            }
            case UI_INTRO -> {
                if (infoDone() && !showNextUiIntro()) {
                    showMoveIntro();
                }
            }
            case MOVE_INTRO -> {
                if (infoDone()) {
                    beginMovePractice();
                }
            }
            case MOVE_PRACTICE -> updateMovePractice(deltaTime);
            case SPRINT_INTRO -> {
                if (infoDone()) {
                    beginSprintPractice();
                }
            }
            case SPRINT_PRACTICE -> updateSprintPractice(deltaTime);
            case DONE_INTRO -> {
                if (infoDone()) {
                    finishTutorial();
                }
            }
            default -> {
                // IDLE / FINISHED
            }
        }
    }

    /** 当前说明是否已被玩家「继续」掉；消费掉信号后返回 true。 */
    private boolean infoDone() {
        if (infoFuture == null || !infoFuture.isDone()) {
            return false;
        }
        infoFuture = null;
        return true;
    }

    private boolean isGameRunning() {
        GameInstance instance = GameInstance.getCurrentInstance();
        if (instance == null) {
            return false;
        }
        return !instance.isPaused() && !instance.isDead() && !instance.isSettingOpen();
    }

    private boolean isGamePaused() {
        GameInstance instance = GameInstance.getCurrentInstance();
        return instance != null && instance.isPaused();
    }

    // ==================== 说明文字 ====================

    /** 显示一条说明（压暗 + 中央白字），并把状态推进到 next。 */
    private void showInfo(String title, String body, Node highlight, Step next) {
        infoFuture = TutorialOverlay.getInstance().showInfo(title, body, highlight);
        step = next;
    }

    // ==================== UI 介绍 ====================

    private void beginUiIntro() {
        // 要介绍哪些元素完全由 GameUI 决定 —— 隐藏的（如法阵关闭时的魔力条）不会进来
        introQueue = GameUI.getInstance().availableTutorialTargets();
        introIndex = 0;
        step = Step.UI_INTRO;
        LoggerManager.Logger("INFO", "教程：开始介绍 " + introQueue.size() + " 个界面元素");

        if (!showNextUiIntro()) {
            showMoveIntro();
        }
    }

    /** 显示下一个 UI 介绍；没有更多则返回 false。 */
    private boolean showNextUiIntro() {
        if (introQueue == null || introIndex >= introQueue.size()) {
            return false;
        }
        GameUI.TutorialTarget target = introQueue.get(introIndex++);
        showInfo(target.title(), target.body(),
                GameUI.getInstance().getTutorialNode(target), Step.UI_INTRO);
        return true;
    }

    // ==================== 移动说明与练习 ====================

    private void showMoveIntro() {
        showInfo("基本移动",
                String.format("使用 %s / %s / %s / %s 来移动\n每个方向按住 %.1f 秒就算掌握",
                        keyName("MOVE_UP"), keyName("MOVE_LEFT"),
                        keyName("MOVE_DOWN"), keyName("MOVE_RIGHT"),
                        MOVE_HOLD_SECONDS),
                null, Step.MOVE_INTRO);
    }

    private void beginMovePractice() {
        moveTracker = new HoldProgressTracker(4, MOVE_HOLD_SECONDS);
        hasLastPosition = false;
        lastMoveDoneCount = -1;
        step = Step.MOVE_PRACTICE;

        moveNotification = NotificationManager.getInstance().showPersistent(
                "移动练习",
                String.format("按住 %s / %s / %s / %s 移动",
                        keyName("MOVE_UP"), keyName("MOVE_DOWN"),
                        keyName("MOVE_LEFT"), keyName("MOVE_RIGHT")),
                true);
        moveNotification.setProgress(0);
        LoggerManager.Logger("INFO", "教程：进入移动练习");
    }

    private void updateMovePractice(double deltaTime) {
        if (moveTracker == null || inputHandler == null) {
            return;
        }
        Transform pt = GameInstance.getPlayerTransformComp();
        if (pt == null) {
            return;
        }
        if (!hasLastPosition) {
            lastPlayerX = pt.x;
            lastPlayerY = pt.y;
            hasLastPosition = true;
            return;
        }
        double dx = pt.x - lastPlayerX;
        double dy = pt.y - lastPlayerY;
        lastPlayerX = pt.x;
        lastPlayerY = pt.y;

        // 「按住」还不够，必须这一帧真的朝那个方向位移了 ——
        // 否则顶着墙按键也能过，玩家其实没学会移动。
        moveTracker.tick(SLOT_UP,
                inputHandler.isKeyPressed("MOVE_UP") && dy < -MOVE_EPSILON, deltaTime);
        moveTracker.tick(SLOT_DOWN,
                inputHandler.isKeyPressed("MOVE_DOWN") && dy > MOVE_EPSILON, deltaTime);
        moveTracker.tick(SLOT_LEFT,
                inputHandler.isKeyPressed("MOVE_LEFT") && dx < -MOVE_EPSILON, deltaTime);
        moveTracker.tick(SLOT_RIGHT,
                inputHandler.isKeyPressed("MOVE_RIGHT") && dx > MOVE_EPSILON, deltaTime);

        // 只有一根合计进度条：必须四个方向都练满才会到 100%
        if (moveNotification != null) {
            moveNotification.setProgress(moveTracker.totalProgress());
            int done = moveTracker.doneCount();
            if (done != lastMoveDoneCount) {
                lastMoveDoneCount = done;
                moveNotification.setMessage(done >= 4
                        ? "四个方向都练完了"
                        : String.format("已完成 %d / 4 个方向", done));
            }
        }

        if (moveTracker.allDone()) {
            closeMoveNotification();
            LoggerManager.Logger("INFO", "教程：移动练习通过");
            showInfo("疾跑",
                    String.format("很棒，现在来试试疾跑\n按住 %s 的同时移动，持续 %.1f 秒\n（疾跑会消耗体力）",
                            keyName("ACCELERATE"), SPRINT_HOLD_SECONDS),
                    null, Step.SPRINT_INTRO);
        }
    }

    private void closeMoveNotification() {
        if (moveNotification != null) {
            moveNotification.close();
            moveNotification = null;
        }
    }

    // ==================== 疾跑说明与练习 ====================

    private void beginSprintPractice() {
        sprintTracker = new HoldProgressTracker(1, SPRINT_HOLD_SECONDS);
        step = Step.SPRINT_PRACTICE;

        sprintNotification = NotificationManager.getInstance().showPersistent(
                "疾跑练习",
                String.format("按住 %s 并移动 %.1f 秒", keyName("ACCELERATE"), SPRINT_HOLD_SECONDS),
                true);
        sprintNotification.setProgress(0);
        LoggerManager.Logger("INFO", "教程：进入疾跑练习");
    }

    private void updateSprintPractice(double deltaTime) {
        if (sprintTracker == null || inputHandler == null) {
            return;
        }
        // 直接读 ECS 的权威状态：体力耗尽时 isSprinting 自动为 false，
        // 不需要在这里重新推导「想疾跑」和「能疾跑」的区别。
        Stamina st = GameInstance.getPlayerStaminaComp();
        boolean sprinting = st != null && st.isSprinting;

        sprintTracker.tick(0, sprinting, deltaTime);
        if (sprintNotification != null) {
            sprintNotification.setProgress(sprintTracker.totalProgress());
        }

        if (sprintTracker.allDone()) {
            if (sprintNotification != null) {
                sprintNotification.close();
                sprintNotification = null;
            }
            LoggerManager.Logger("INFO", "教程：疾跑练习通过");
            showInfo("教程完成",
                    "恭喜！你已经掌握了基本的界面与操作\n现在可以自由探索这个世界了",
                    null, Step.DONE_INTRO);
        }
    }

    // ==================== 完成 ====================

    private void finishTutorial() {
        QuestManager questManager = QuestManager.getInstance();
        if (questManager.hasActiveQuest()) {
            questManager.setQuestProgress(100);
        }
        // 走 DataManager 的路由方法：进度记在存档还是全局，由 starveil:tutorial_persist 决定
        DataManager.setTutorialCompleted(true);
        // 广播教程完成 + 解锁剧情脚本的 awaitTutorial() 等待
        com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents.tutorialCompleted();
        EventCallbackManager.getInstance().triggerTutorialCompleted();
        releaseStoryLayer();
        step = Step.FINISHED;
        LoggerManager.Logger("INFO", "新手教程已完成");
    }

    /**
     * 结束教程对剧情层的占用：解锁推进、把画面还给剧情层。幂等，可重复调用。
     */
    private void releaseStoryLayer() {
        ChatManager.getInstance().setStoryAdvanceLocked(false);
        ChatManager.getInstance().takeBackFromHudAfterTutorial();
    }

    // ==================== 状态查询 / 重置 ====================

    /** 重置教程状态。 */
    public void reset() {
        // 收起可能还挂着的遮罩和通知，否则重置后会残留
        TutorialOverlay.getInstance().forceHide();
        closeMoveNotification();
        if (sprintNotification != null) {
            sprintNotification.close();
            sprintNotification = null;
        }
        // 教程被打断（例如剧情取消）时必须把剧情层的锁放开，否则对话永远推不动
        releaseStoryLayer();

        step = Step.IDLE;
        pendingTutorialStart = false;
        infoFuture = null;
        introQueue = new ArrayList<>();
        introIndex = 0;
        moveTracker = null;
        sprintTracker = null;
        hasLastPosition = false;
        lastMoveDoneCount = -1;
    }

    public boolean isTutorialComplete() {
        return step == Step.FINISHED;
    }

    public boolean isTutorialInProgress() {
        return step != Step.IDLE && step != Step.FINISHED;
    }

    /** 当前是否正在介绍界面元素（供外部判断是否要屏蔽其它 UI）。 */
    public boolean isIntroducingUi() {
        return step == Step.UI_INTRO;
    }

    private void startPendingTutorial() {
        if (!pendingTutorialStart) {
            return;
        }
        pendingTutorialStart = false;
        QuestManager.getInstance().acceptQuest("quest_001");
        initialize(GameInstance.getInputHandlerStatic());
        LoggerManager.Logger("INFO", "待启动的教程已启动");
    }

    // ==================== 工具 ====================

    /** 取按键的可读名称（支持玩家改键）。 */
    private String keyName(String function) {
        if (inputHandler == null) {
            return "?";
        }
        int idx = inputHandler.getKeyForFunction(function);
        if (idx < 0) {
            return "?";
        }
        try {
            return KeyCode.values()[idx].getName();
        } catch (Exception e) {
            return "?";
        }
    }
}
