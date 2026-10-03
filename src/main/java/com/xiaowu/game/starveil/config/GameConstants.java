package com.xiaowu.game.starveil.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 游戏常量类 - 集中管理所有硬编码的常量值
 */
public class  GameConstants {
    
    // 文件路径常量
    public static final String LOCK_FILE_PATH;
    public static final String DATA_DIR;

    static {
        String home = System.getProperty("user.home");
        String starveil = home + File.separator + ".starveil";
        DATA_DIR = starveil + File.separator + "data";
        LOCK_FILE_PATH = starveil + File.separator + "game.lock";
    }

    /**
     * 隐藏用户目录下的 {@code .starveil} 数据目录。
     *
     * <p>Windows 使用 {@code dos:hidden} 文件属性；其它平台的适配插件可以
     * REPLACE {@link com.xiaowu.game.starveil.platform.api.PlatformInjectPoints#HIDE_DATA_DIR}
     * 注入点换成自己的实现（例如改名为 {@code .} 前缀或修改权限）。
     */
    @com.xiaowu.game.starveil.plugin.InjectPoint(
            com.xiaowu.game.starveil.platform.api.PlatformInjectPoints.HIDE_DATA_DIR)
    public static void setStarveilDirHidden() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            try {
                Path p = Paths.get(System.getProperty("user.home"), ".starveil");
                if (Files.exists(p)) {
                    Files.setAttribute(p, "dos:hidden", true);
                }
            } catch (Exception ignored) {
                // 「隐藏目录」只是整洁性优化，失败不影响游戏能否运行，
                // 没必要为它弹窗或刷日志（某些文件系统也不支持 dos:hidden）
            }
        }
    }
    public static final String LOGS_DIR = "logs";
    public static final String PLUGINS_DIR = "plugins";
    public static final String PLUGINS_DISABLED_DIR = "plugins/disabled";
    public static final String CONFIG_FILE_NAME = "game.dat";
    public static final String BACKGROUND_MUSIC_DIR = "starveil:sounds/music/";
    public static final String ICON_PATH = "starveil:textures/icons/app-icon.png";
    public static final String BACKGROUND_IMG_PATH = "starveil:textures/backgrounds/main-menu.png";
    
    // ==================== 数据键名 ====================
    //
    // 这些常量是键名的「单一事实源」：注册表按它们声明键
    // （见 FrameworkDataKeys），调用点需要裸字符串键名时也用它们，
    // 因此名字对不上就等于键没注册。
    //
    // 新代码应当直接使用 FrameworkDataKeys 里的 DataKey 句柄，
    // 只在「必须传字符串」的地方才引用这些常量。

    // 游戏设置键名常量
    public static final String SETTING_ASPECT_RATIO = "starveil:setting.aspect_ratio";
    public static final String SETTING_FULLSCREEN = "starveil:setting.fullscreen";
    public static final String SETTING_FULLSCREEN_MODE = "starveil:setting.fullscreen_mode";
    public static final String SETTING_BGM_VOLUME = "starveil:setting.bgm_volume";
    public static final String SETTING_SFX_VOLUME = "starveil:setting.sfx_volume";
    public static final String SETTING_BGM_INTERVAL = "starveil:setting.bgm_interval";
    public static final String SETTING_TEXT_SPEED = "starveil:setting.text_speed";
    public static final String SETTING_LANGUAGE = "starveil:setting.language";

    // 游戏状态键名常量
    public static final String ILLEGALLY_SHUTDOWN_KEY = "starveil:illegally_shutdown";

    /**
     * 框架版本号 —— 与游戏内容版本<b>相互独立</b>。
     *
     * <p>插件可以在 plugin.yml 里用 {@code framework-version} 声明自己适配的框架版本
     * （见 {@code PluginCompatibility}）。只比主版本号：要求逐位相同会让每次发版
     * 都作废所有插件，完全不管又会让跨大版本的插件带着失效的注入点静默跑起来。
     *
     * <p>改动插件 API、注入点语义或启动流程时需要递增此版本。
     */
    public static final String FRAMEWORK_VERSION = "1.0.0";

    /**
     * 「禁止退出」标记。
     *
     * <p>内置键一律带 {@code starveil:} 前缀。这个键是<b>存档作用域</b>：
     * 全局可以有基准值，某段剧情也能只在当前存档里禁掉退出。
     * 键定义见 {@code FrameworkDataKeys.CANT_EXIT}，规则见 {@code docs/data-keys.md}。
     */
    public static final String CANT_EXIT_KEY = "starveil:cant_exit";
    /**
     * 惯性开关。默认<b>开启</b> —— 移动带加减速，疾跑停下时播放急停。
     * 显式设为 false 则移动恢复为瞬时启停，且不再触发急停。
     */
    public static final String INERTIA_KEY = "starveil:inertia";

    /**
     * 聊天记录是否跨章节保留。
     *
     * <p>默认<b>不保留</b>（章节切换即清空）。设为 true 后记录会一直留在内存里，
     * 直到手动清理或章节代码自行调用 {@code ChatHistory.clear()}。
     */
    public static final String CHAT_HISTORY_KEEP_CHAPTERS_KEY = "starveil:chat_history_keep_chapters";
    public static final String TUTORIAL_COMPLETED_KEY = "starveil:tutorial_completed";
    /**
     * {@code starveil:tutorial_completed} 是否需要<b>跨存档</b>保存。默认<b>否</b>。
     *
     * <p>默认情况下教程进度记在**当前存档**里 —— 开新档重新走教程。
     * 设为 true 时它会被当作普通特殊键走 DataManager，全局只记一次，
     * 之后所有存档都不再重复教程。
     */
    public static final String TUTORIAL_PERSIST_KEY = "starveil:tutorial_persist";
    public static final String PLUGIN_CONSENT_KEY = "starveil:plugin_consent_given";
    // 法阵（魔力）系统开关，默认关闭。关闭时魔力值一律返回0、GameUI不显示魔力条、Q键法阵无效
    public static final String MAGIC_CIRCLE_ENABLED_KEY = "starveil:magic_circle_enabled";
    /**
     * 屏幕特效遮罩层开关，默认<b>开启</b>（随游戏启动自动显示）。
     * 显式设为 false 时不创建全屏遮罩层，故障花屏 / 伪蓝屏 / 降帧等特效随之不可用。
     */
    public static final String SETTING_OVERLAY_ENABLED = "starveil:overlay_enabled";

    
    // 颜色常量
    public static final String PINK_COLOR_HEX = "#FF69B4";
    public static final String DEEP_PINK_COLOR_HEX = "#FF1493";
    public static final String LIGHT_PINK_COLOR_HEX = "#FFC0CB";
    
    // 游戏逻辑常量
    public static final double DEFAULT_BGM_VOLUME = 0.8;
    public static final double DEFAULT_SFX_VOLUME = 0.8;
    public static final double DEFAULT_MAX_HEALTH = 100.0;
    public static final double DEFAULT_MOVE_SPEED = 6.0;
    public static final double DEFAULT_ACCELERATION_FACTOR = 2.0;
    
    // 界面常量
    public static final double DEFAULT_WINDOW_WIDTH = 1200.0;
    public static final double DEFAULT_WINDOW_HEIGHT = 675.0;
    public static final double MIN_WINDOW_WIDTH = 1200.0;
    public static final double MIN_WINDOW_HEIGHT = 675.0;
    public static final double DEFAULT_BUTTON_WIDTH = 200.0;
    public static final double DEFAULT_BUTTON_HEIGHT = 50.0;
    public static final double DEFAULT_PANEL_WIDTH = 300.0;
    public static final double DEFAULT_PANEL_HEIGHT = 50.0;
    public static final int DEFAULT_CORNER_RADIUS = 10;
    
    // 游戏循环常量
    public static final long FPS_UPDATE_INTERVAL_NS = 500_000_000L; // 0.5秒
    public static final double MIN_DELTA_TIME = 0.001;
    public static final double MAX_DELTA_TIME = 0.1;
    public static final long PAUSE_DEBOUNCE_TIME_MS = 300L; // 暂停切换防抖时间（毫秒）
    
    // 音频常量
    public static final long DEFAULT_BGM_INTERVAL_MS = 30000L; // 默认BGM间隔时间：30秒
    public static final double MIN_VOLUME = 0.0;
    public static final double MAX_VOLUME = 1.0;
    
    // 游戏名称和标题
    public static final String GAME_TITLE = "雾隐星阑";
    public static final String GAME_TITLE_IN_PARENS = GAME_TITLE + " - ";
    public static final String MAIN_MENU_TITLE = GAME_TITLE_IN_PARENS + "主菜单";
    public static final String GAME_TEST_TITLE = GAME_TITLE_IN_PARENS + "开放探索系统测试";
    
    // 错误和消息对话框常量
    public static final String ERROR_ADMIN_PRIVILEGE = "提升权限错误";
    public static final String ERROR_ADMIN_PRIVILEGE_MSG = "尝试提升到管理员权限时产生错误 请联系管理员并附带日志以解决此问题\n如果不想以管理员模式运行 可以尝试携带参数 \"-no-admin\" 来运行 (这可能导致BUG和功能缺失)";
    public static final String WARN_NON_ADMIN_MODE = "注意!";
    public static final String WARN_NON_ADMIN_MSG = "您运行在非管理员模式下，这种模式可能出现错误或Bug，此模式下发生的错误或Bug请不要向管理员寻求帮助";
    public static final String ERROR_LOCK_FILE = "启动失败";
    public static final String ERROR_LOCK_FILE_MSG = "无法创建游戏锁文件，请检查文件权限。";
    public static final String ERROR_GAME_RUNNING = "游戏已在运行";
    public static final String ERROR_GAME_RUNNING_MSG = "游戏已经在运行中，无法启动第二个实例。";
    public static final String ERROR_UNCAUGHT_EXCEPTION = "发生错误";
    public static final String ERROR_UNCAUGHT_EXCEPTION_MSG = "应用程序产生了未被捕获的错误，请查看日志文件并交给开发者解决问题";
    
    // 窗口抖动效果参数
    public static final int SHAKE_DISTANCE = 15;
    public static final int SHAKE_COUNT = 6;
    public static final int SHAKE_DELAY_MS = 30;
    
    // 游戏逻辑常量
    public static final double ASPECT_RATIO_16_9 = 16.0 / 9.0;
    public static final double ASPECT_RATIO_4_3 = 4.0 / 3.0;
    public static final double ASPECT_RATIO_16_10 = 16.0 / 10.0;
    public static final double ASPECT_RATIO_21_9 = 21.0 / 9.0;
}