package com.xiaowu.game.starveil.startup;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 游戏内容侧初始化钩子（框架侧）。
 *
 * <p>约定类 {@code com.xiaowu.game.starveil.content.init.init}：如果存在，
 * 它就是「游戏启动前的非框架行为初始化动作」的入口。框架只负责在正确时机调用它，
 * 不关心里面做什么 —— 这正是它与框架代码的分界。
 *
 * <p>入口方法优先取静态无参 {@code init()}，其次取静态 {@code main(String[])}。
 * 两者都没有则视为约定未被满足，报错而不是静默跳过。
 *
 * <p><b>本类属于框架，因此放在 {@code startup} 而不是 {@code content}</b>：
 * {@code content} 是游戏内容的地盘（章节、物品、成就定义），框架代码混进去会形成
 * 「框架 → 内容」的编译期反向依赖。这里对内容侧的唯一联系是那个<b>字符串常量</b>
 * {@link #CLASS_NAME}，全靠反射探测 —— 没有编译期依赖，方向才是对的。
 *
 * <p>这个类只做「探测 / 调用」，不持有状态，也不依赖 JavaFX，
 * 因此可以在 AppEntry 的最早期安全使用。
 */
public final class GameContentInit {

    /** 约定的初始化类全限定名（内容侧契约，不要改）。 */
    public static final String CLASS_NAME = "com.xiaowu.game.starveil.content.init.init";

    /** 入口方法名（按优先级）。 */
    private static final String ENTRY_METHOD = "init";

    /** 内容包目录（用于判断有没有装内容）。 */
    private static final String CONTENT_PACKAGE_PATH = "com/xiaowu/game/starveil/content";

    private GameContentInit() {
    }

    /**
     * 内容包是否存在。
     *
     * <p>先看包目录有没有东西（目录或 jar 条目都能命中），
     * 再退回探测约定入口类 —— 有些打包方式不写目录项。
     */
    public static boolean contentPresent() {
        ClassLoader cl = GameContentInit.class.getClassLoader();
        for (String candidate : new String[]{CONTENT_PACKAGE_PATH, CONTENT_PACKAGE_PATH + "/"}) {
            try {
                if (cl.getResources(candidate).hasMoreElements()) {
                    return true;
                }
            } catch (Exception ignored) {
                // 换下一个候选
            }
        }
        // 兜底：包目录项缺失时，看约定入口类在不在
        return exists();
    }

    /**
     * 要求存在游戏内容，否则<b>致命退出</b>。
     *
     * <p>框架本身不是一款可运行的游戏：没有 {@code content} 包时，
     * 章节、地图、物品、成就全是空的，界面能画出来但什么都玩不了。
     * 与其让玩家对着一个空壳猜测，不如明确告诉他「这是框架，缺少内容」。
     */
    public static void requireContent() {
        if (contentPresent()) {
            return;
        }
        // -no-content：开发/测试用的强制运行开关。
        // 没有它就没法验证「缺资源时各处降级是否正常」—— 会被下面直接拦死。
        if (com.xiaowu.game.starveil.config.LauncherConfig.getInstance().isNoContent()) {
            Logger("WARNING", """
                    ============================================================
                    -no-content：强制在【没有游戏内容】的情况下运行。
                    这是开发/测试模式，用于检查缺资源时各处降级是否正常：
                      主菜单背景 → 纯黑；窗口图标 → 系统默认；
                      字体 → 系统字体；音频 → 静默。
                    章节、地图、物品、成就都会是空的 —— 这不是可玩的游戏。
                    ============================================================""");
            return;
        }
        String msg = """
                当前运行的是 Starveil 游戏框架。
                我们没有在 com.xiaowu.game.starveil.content 找到任何内容
                
                如果您是开发者，这意味这你的游戏内容没有被正确打包
                如果您是玩家，请找到打包成功的完整游戏
                
                如果游戏使用运行时注入，您应该可以在根目录找到launch.exe
                """;
        Logger("ERROR", msg);
        try {
            com.xiaowu.game.starveil.platform.api.SystemManagerFactory.getInstance()
                    .showError("缺少游戏内容", msg);
        } catch (Exception e) {
            Logger("ERROR", "无法显示错误对话框: " + e.getMessage());
        }
        System.exit(1);
    }

    /**
     * 类是否存在。
     *
     * <p>用 {@code initialize=false} 探测，<b>不会</b>触发类初始化，
     * 因此可以在插件加载之前安全地判断。
     */
    public static boolean exists() {
        try {
            Class.forName(CLASS_NAME, false, GameContentInit.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * 是否有可调用的入口方法。
     *
     * @return 找到的入口方法；没有则 null
     */
    public static Method findEntryPoint() {
        Class<?> type;
        try {
            type = Class.forName(CLASS_NAME, false, GameContentInit.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }

        try {
            Method m = type.getDeclaredMethod(ENTRY_METHOD);
            if (Modifier.isStatic(m.getModifiers())) {
                m.setAccessible(true);
                return m;
            }
            Logger("ERROR", CLASS_NAME + "." + ENTRY_METHOD + " 必须是静态方法");
        } catch (NoSuchMethodException ignored) {
            // 退回 main(String[])
        }

        try {
            Method m = type.getDeclaredMethod("main", String[].class);
            if (Modifier.isStatic(m.getModifiers())) {
                m.setAccessible(true);
                return m;
            }
        } catch (NoSuchMethodException ignored) {
            // 两个都没有
        }
        return null;
    }

    /**
     * 执行初始化。
     *
     * @return 是否成功（类不存在 / 没有入口 / 抛异常都算失败）
     */
    public static boolean run() {
        if (!exists()) {
            Logger("INFO", "未发现 " + CLASS_NAME + "，跳过内容初始化");
            return false;
        }
        Method entry = findEntryPoint();
        if (entry == null) {
            Logger("ERROR", CLASS_NAME + " 存在但没有静态 init() 或 main(String[]) 入口");
            return false;
        }

        try {
            Logger("INFO", "执行内容初始化: " + CLASS_NAME + "." + entry.getName());
            if (entry.getParameterCount() == 1) {
                entry.invoke(null, (Object) new String[0]);
            } else {
                entry.invoke(null);
            }
            Logger("INFO", "内容初始化完成");
            return true;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            Logger("ERROR", "内容初始化执行失败: " + cause);
            return false;
        }
    }
}
