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
 * 写明命名空间、键名、默认值和<b>类型</b>，然后项目里到处用这个句柄读写即可。
 *
 * <pre>
 *   // 内容侧（StarveilContent）的写法：类型写在默认值后面
 *   public static final DataKey&lt;String&gt; HERO_NAME =
 *           DataManager.defineStr("mydemo", "hero_name", "霁雾");
 *
 *   public static final DataKey&lt;Integer&gt; AFFECTION =
 *           DataManager.defineInt("mydemo", "affection", 0, PER_SAVE);   // 随存档
 *
 *   // 也可以直接写类型，基本类型 / 包装类型都行；默认值 null 表示「没有默认值」
 *   public static final DataKey&lt;Boolean&gt; CANT_EXIT =
 *           DataManager.define("mydemo", "cant_exit", false, boolean.class, PER_SAVE);
 * </pre>
 *
 * <p>默认值与声明类型不一致会在<b>注册那一刻</b>直接报错，不必等到读取时才
 * 发现值解析不了。
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
            DataManager.defineStr("starveil", "setting.fullscreen_mode", "borderless");

    /** 全屏方式：无边框窗口（默认）。 */
    public static final String FULLSCREEN_MODE_BORDERLESS = "borderless";

    /** 全屏方式：传统独占全屏。 */
    public static final String FULLSCREEN_MODE_EXCLUSIVE = "exclusive";

    /**
     * 把全屏方式归一化到 {@link #FULLSCREEN_MODE_BORDERLESS} /
     * {@link #FULLSCREEN_MODE_EXCLUSIVE}。
     *
     * <p>这个值以前直接存界面上的中文串（{@code 无边框窗口} / {@code 全屏}）。
     * 一旦界面文字要能跟着语言换，就不能再拿显示文字当标识 —— 否则
     * 「切到英文之后全屏方式失效」这种 bug 会非常难查。老配置里的中文串
     * 在这里顺手认一下，省得玩家升级后设置被悄悄重置。
     */
    public static String normalizeFullscreenMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULLSCREEN_MODE_BORDERLESS;
        }
        String v = raw.trim();
        if (FULLSCREEN_MODE_EXCLUSIVE.equalsIgnoreCase(v) || "全屏".equals(v)) {
            return FULLSCREEN_MODE_EXCLUSIVE;
        }
        if (FULLSCREEN_MODE_BORDERLESS.equalsIgnoreCase(v) || "无边框窗口".equals(v)) {
            return FULLSCREEN_MODE_BORDERLESS;
        }
        // 不认识的值按默认处理，而不是原样留着让后续比较全部落空
        return FULLSCREEN_MODE_BORDERLESS;
    }

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

    /**
     * 界面语言代码（如 {@code zh_cn}），默认 {@code zh_cn}。
     *
     * <p>可选语言由内容通过 {@code ContentConfig.addLanguage()} 提供；
     * 内容没提供任何语言时，设置界面不显示语言切换，这个键也就一直是默认值。
     * 详见 {@code docs/data-keys.md}。
     */
    public static final DataKey<String> LANGUAGE =
            DataManager.defineStr("starveil", "setting.language",
                    com.xiaowu.game.starveil.infrastructure.i18n.I18n.DEFAULT_LANGUAGE);

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

    // ==================== 可信时间（全局） ====================

    /**
     * 时间基准：上次联网同步取回的可信时间戳（毫秒）。
     *
     * <p>和 {@link #TRUSTED_TIME_BASE_WALL} 配对存下来，下次启动就能接着往下算：
     * 可信时间 = 本基准 + （本机时钟走过的这段时间）。所以一次运行内玩家改系统时间无效，
     * 跨重启改时间才会影响它（直到重新联网同步）。
     */
    public static final DataKey<Long> TRUSTED_TIME_BASE =
            DataManager.defineLong("starveil", "trusted_time_base", 0L);

    /** 记下上面那个基准时的<b>本机时钟</b>毫秒值；用来说明这份基准有多旧、并补上跨重启的时间差。 */
    public static final DataKey<Long> TRUSTED_TIME_BASE_WALL =
            DataManager.defineLong("starveil", "trusted_time_base_wall", 0L);

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

    // ==================== 章节过场 ====================

    /**
     * 章节之间是否做「落幕 → 卸下世界 → 亮幕」的过场。默认<b>开启</b>。
     *
     * <p>正常流程下这个过场是必要的：它把上一章的场景与下一章的场景在视觉上切开，
     * 也给卸下/加载世界留出时间。但有些内容希望章节之间无缝衔接
     * （例如一章拆成两半来写），那时把它设为 false 即可。
     *
     * <p>注意它只控制<b>过场与卸下世界</b>：下一章要不要世界仍然由该章的
     * {@code mode()} 决定 —— 需要世界时该加载还是会加载，不会因为关掉过场
     * 就把世界也一起省掉。
     */
    public static final DataKey<Boolean> CHAPTER_FADE =
            DataManager.defineBool("starveil", "chapter_fade", true);

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
