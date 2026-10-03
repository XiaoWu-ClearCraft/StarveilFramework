package com.xiaowu.game.starveil.infrastructure.persistence;

import com.xiaowu.game.starveil.config.GameConstants;

import static com.xiaowu.game.starveil.infrastructure.persistence.DataKeyFlag.PER_SAVE;
import static com.xiaowu.game.starveil.infrastructure.persistence.DataKeyFlag.READ_ONLY;
import static com.xiaowu.game.starveil.infrastructure.persistence.DataKeyFlag.TEMPORARY;

/**
 * 框架内置数据键的定义表。
 *
 * <p><b>这就是内容项目该照抄的样板</b>：内容侧在
 * {@code com.xiaowu.game.starveil.content.init.init} 里用同样的写法声明自己的键 ——
 * 写明命名空间、键名、默认值和类型，然后项目里到处用这个句柄读写即可。
 *
 * <pre>
 *   // 内容侧（StarveilContent）的写法
 *   public static final DataKey&lt;String&gt; HERO_NAME =
 *           DataManager.define("mydemo", "hero_name", "霁雾");
 *
 *   public static final DataKey&lt;Integer&gt; AFFECTION =
 *           DataManager.defineInt("mydemo", "affection", 0, PER_SAVE);   // 随存档
 * </pre>
 *
 * <p>引擎命名空间 {@code starveil} 由框架占用，内容/插件请用自己的前缀。
 *
 * <p>本类的静态初始化会一次性把内置键全部声明出来。之所以做成「显式声明 + 静态
 * 初始化」而不是散在各处按需注册：键的默认值属于<b>设计决策</b>，集中一处才能被
 * 审查、被文档引用、被内容整体重定义。
 */
public final class FrameworkDataKeys {

    // ==================== 框架元信息 ====================

    /**
     * 框架版本号 —— 只读键。
     *
     * <p>内容可能需要根据框架版本调整自己的行为（例如某个 API 从某个版本起才有），
     * 但内容<b>不该改</b>它。所以这里声明成只读：写入会被拒绝并告警，
     * 读取永远拿到 {@link GameConstants#FRAMEWORK_VERSION}。
     */
    public static final DataKey<String> FRAMEWORK_VERSION =
            DataManager.defineStr("starveil", "framework_version",
                    GameConstants.FRAMEWORK_VERSION, READ_ONLY);

    // ==================== 玩家 / 剧情 ====================

    /** 玩家名字（全局，跨存档）。剧情通过它做文本替换。 */
    public static final DataKey<String> PLAYER_NAME =
            DataManager.defineStr("starveil", "player_name", "");

    // ==================== 画面设置（全局） ====================

    public static final DataKey<String> ASPECT_RATIO =
            DataManager.defineStr("starveil", "setting.aspect_ratio", "16:9");

    public static final DataKey<Boolean> FULLSCREEN =
            DataManager.defineBool("starveil", "setting.fullscreen", false);

    public static final DataKey<String> FULLSCREEN_MODE =
            DataManager.defineStr("starveil", "setting.fullscreen_mode", "无边框窗口");

    // ==================== 音频设置（全局） ====================

    public static final DataKey<Double> BGM_VOLUME =
            DataManager.defineDouble("starveil", "setting.bgm_volume",
                    GameConstants.DEFAULT_BGM_VOLUME);

    public static final DataKey<Double> SFX_VOLUME =
            DataManager.defineDouble("starveil", "setting.sfx_volume",
                    GameConstants.DEFAULT_SFX_VOLUME);

    /** 背景音乐两曲之间的间隔（毫秒）。 */
    public static final DataKey<Long> BGM_INTERVAL =
            DataManager.defineLong("starveil", "setting.bgm_interval", 0L);

    // ==================== 文本设置（全局） ====================

    /** 打字机速度：每字间隔毫秒数，越大越慢。 */
    public static final DataKey<Integer> TEXT_SPEED =
            DataManager.defineInt("starveil", "setting.text_speed", 50);

    // ==================== 按键绑定（全局） ====================

    /** 按键绑定键名 → 该键绑定的 KeyCode。 */
    public static final DataKey<Integer> KEY_BIND_MOVE_UP =
            DataManager.defineInt("starveil", "key_bind.move_up", 0);
    public static final DataKey<Integer> KEY_BIND_MOVE_DOWN =
            DataManager.defineInt("starveil", "key_bind.move_down", 0);
    public static final DataKey<Integer> KEY_BIND_MOVE_LEFT =
            DataManager.defineInt("starveil", "key_bind.move_left", 0);
    public static final DataKey<Integer> KEY_BIND_MOVE_RIGHT =
            DataManager.defineInt("starveil", "key_bind.move_right", 0);
    public static final DataKey<Integer> KEY_BIND_ACCELERATE =
            DataManager.defineInt("starveil", "key_bind.accelerate", 0);
    public static final DataKey<Integer> KEY_BIND_INTERACT =
            DataManager.defineInt("starveil", "key_bind.interact", 0);
    public static final DataKey<Integer> KEY_BIND_MAGIC_ATTACK =
            DataManager.defineInt("starveil", "key_bind.magic_attack", 0);
    public static final DataKey<Integer> KEY_BIND_BACKPACK =
            DataManager.defineInt("starveil", "key_bind.backpack", 0);

    // ==================== 游戏内开关（存档作用域） ====================

    /**
     * 禁止退出。默认<b>不禁止</b>。
     *
     * <p>存档作用域：全局可以设一个基准值，而某段剧情可以只在当前存档里禁掉退出 ——
     * 退出按钮的存在本身就会削弱「不容打断的独白」的张力。
     * 设为 true 时主菜单与 ESC 暂停菜单都会移除退出入口；设回 false 或删除该键即恢复。
     */
    public static final DataKey<Boolean> CANT_EXIT =
            DataManager.defineBool("starveil", "cant_exit", false, PER_SAVE);

    /**
     * 惯性开关。默认<b>开启</b> —— 移动带加减速，疾跑停下时播放急停。
     * 显式设为 false 则恢复瞬时启停。
     */
    public static final DataKey<Boolean> INERTIA =
            DataManager.defineBool("starveil", "inertia", true, PER_SAVE);

    /** 聊天记录是否跨章节保留，默认<b>不保留</b>（章节切换即清空）。 */
    public static final DataKey<Boolean> CHAT_HISTORY_KEEP_CHAPTERS =
            DataManager.defineBool("starveil", "chat_history_keep_chapters", false, PER_SAVE);

    /**
     * 法阵（魔力）系统开关，默认<b>关闭</b>。
     * 关闭时魔力值一律返回 0、GameUI 不显示魔力条、Q 键法阵无效。
     */
    public static final DataKey<Boolean> MAGIC_CIRCLE_ENABLED =
            DataManager.defineBool("starveil", "magic_circle_enabled", false, PER_SAVE);

    /**
     * 屏幕特效遮罩层开关，默认<b>开启</b>。
     * 设为 false 时不创建全屏遮罩层，故障花屏 / 伪蓝屏 / 降帧等特效随之不可用。
     */
    public static final DataKey<Boolean> OVERLAY_ENABLED =
            DataManager.defineBool("starveil", "overlay_enabled", true, PER_SAVE);

    /**
     * 教程是否已完成，作用域由 {@link #TUTORIAL_PERSIST} 决定。
     *
     * <p>默认记在<b>当前存档</b>里：每个存档各自记录，开新档重新走教程。
     * 注意这个键的<b>实际作用域在启动时按 {@code TUTORIAL_PERSIST} 的当前值重算</b>
     * （见 {@code TutorialState.applyPersistMode()}），因此这里先声明为存档作用域 ——
     * 那是默认行为。
     */
    public static final DataKey<Boolean> TUTORIAL_COMPLETED =
            DataManager.defineBool("starveil", "tutorial_completed", false, PER_SAVE);

    /**
     * {@link #TUTORIAL_COMPLETED} 是否跨存档保存。默认<b>否</b>。
     *
     * <p>设为 true 时教程进度当作全局键处理，全局只记一次，之后所有存档都不再重复教程。
     */
    public static final DataKey<Boolean> TUTORIAL_PERSIST =
            DataManager.defineBool("starveil", "tutorial_persist", false);

    // ==================== 启动流程状态 ====================

    /**
     * 插件授权是否已给出，默认<b>否</b>。
     */
    public static final DataKey<Boolean> PLUGIN_CONSENT_GIVEN =
            DataManager.defineBool("starveil", "plugin_consent_given", false);

    /**
     * 上次是否异常关闭 —— <b>临时键</b>，不落盘，进程结束即消失。
     *
     * <p>以前它是要写进配置再在下次启动时清理的。改成临时键后根本不需要清理逻辑：
     * 生命周期由进程本身保证，被强杀也不会留下残留标记。
     */
    public static final DataKey<Boolean> ILLEGALLY_SHUTDOWN =
            DataManager.defineBool("starveil", "illegally_shutdown", false, TEMPORARY);

    /** 兼容性提示是否已被忽略（只问一次），默认<b>否</b>。 */
    public static final DataKey<Boolean> COMPATIBILITY_WARNING_SHOWN =
            DataManager.defineBool("starveil", "compatibility_warning_shown", false);

    /** 显卡检查是否已被忽略，默认<b>否</b>。 */
    public static final DataKey<Boolean> GPU_CHECK_IGNORED =
            DataManager.defineBool("starveil", "gpu_check_ignored", false);

    // ==================== 内部 ====================

    /**
     * 把本类声明的键标记为「框架自带」，并留一份原始定义快照。
     *
     * <p>声明成字段而不是静态块：字段初始化器按声明顺序执行，因此
     * {@link DataKeyRegistry#markBuiltInDuring} 一定在所有键声明完之后才跑，
     * 不需要额外保证顺序。
     *
     * <p>快照用于测试：某个用例重定义了内置键的默认值后，
     * {@code DataKeyRegistry.resetForTest()} 靠它把原定义放回去。
     */
    @SuppressWarnings("unused")
    private static final boolean MARKED_AS_BUILT_IN = DataKeyRegistry.markBuiltInDuring();

    /** 内置键的原始定义快照（键名 → 定义）。 */
    private static final java.util.Map<String, DataKey<?>> BUILT_IN_SNAPSHOT =
            java.util.Map.copyOf(DataKeyRegistry.snapshotRegistered());

    /** 内置键的原始定义。 */
    static java.util.Map<String, DataKey<?>> snapshotBuiltIns() {
        return BUILT_IN_SNAPSHOT;
    }

    private FrameworkDataKeys() {
    }
}
