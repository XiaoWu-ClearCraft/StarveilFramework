package com.xiaowu.game.starveil.infrastructure.persistence;

import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xiaowu.game.starveil.config.GameConstants;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 全局数据管理器 —— 读写 {@code data.dat}（跨存档配置）。
 *
 * <h2>键必须先注册</h2>
 *
 * <p>任何键在使用前都要先声明：命名空间 + 键名 + 默认值 + <b>类型</b>，例如
 * {@code defineInt("mymod", "affection", 0)} 声明了键 {@code mymod:affection}，
 * 一个 {@code int}、默认值 0。
 *
 * <pre>
 *   // 声明（通常在 content.init.init() 里，或作为 static final 字段）
 *   DataKey&lt;Integer&gt; AFFECTION = DataManager.defineInt("mymod", "affection", 0);
 *
 *   // 想直接写类型 / 用基本类型 / 不要默认值：
 *   DataManager.define("mymod", "cant_exit", false, boolean.class, DataKeyFlag.PER_SAVE);
 *   DataManager.define("mymod", "last_map", null, String.class);   // 无默认值 → 读到 null
 *
 *   // 读写 —— 不需要再传默认值，也不需要选类型方法
 *   AFFECTION.set(3);
 *   int v = AFFECTION.get();
 * </pre>
 *
 * <p><b>默认值必须与声明类型一致</b>（{@code null} 除外，那表示「没有默认值」）：
 * 不一致会在注册那一刻直接报错，不必等到读取时才发现值解析不了。
 *
 * <p><b>为什么强制注册</b>：
 * <ul>
 *   <li>默认值只有一份。以前 {@code getInt(key, 50)} 这种写法让同一个键在不同
 *       文件里可能带着不同的默认值，读出来的结果取决于先执行哪一处；</li>
 *   <li>拼错的键会当场报错，而不是静默地读到一个默认值；</li>
 *   <li>「这个键属于谁」一眼可辨 —— 键名自带命名空间前缀。</li>
 * </ul>
 *
 * <p>未注册的键：读操作返回 {@code null} 并记录 WARNING，写操作被拒绝并记录 ERROR。
 *
 * <h2>读取优先级</h2>
 *
 * <p>临时键 → 内存覆盖（调试用） → 当前存档（仅存档作用域的键） → {@code data.dat}。
 *
 * <h2>旧接口</h2>
 *
 * <p>{@code getBoolean(String, boolean)} 这类「裸字符串键 + 自带默认值」的方法仍然
 * 保留（大量调用点尚未迁移），但它们<b>不构成例外</b>：键照样必须注册，
 * 传进来的默认值只在键未注册时作为回退使用。
 */
public class DataManager {
    private static final String DATA_FILE_NAME = GameConstants.CONFIG_FILE_NAME;
    private static final Gson gson = new GsonBuilder().create();

    /**
     * 全局配置的内存镜像。
     *
     * <p>用 {@link java.util.concurrent.ConcurrentHashMap} 而不是 {@code HashMap}：
     * <b>它会被两个线程写</b> —— 剧情线程通过 {@code StoryScript.setData} 写，
     * FX 线程通过设置界面与调试窗口写。普通 {@code HashMap} 在并发写入下
     * 可能丢数据，极端情况下会陷入死循环把 CPU 打满。
     */
    private static Map<String, String> dataMap;
    private static File dataFile;

    public static void initialize() {
        try {
            ensureDataDirectory();
            dataFile = new File(GameConstants.DATA_DIR, DATA_FILE_NAME);
            if (!dataFile.exists()) {
                dataMap = new java.util.concurrent.ConcurrentHashMap<>();
                saveData();
                Logger("DEBUG", "创建新的配置文件: " + dataFile.getAbsolutePath());
            } else {
                loadData();
            }
        } catch (Exception e) {
            dataMap = new java.util.concurrent.ConcurrentHashMap<>();
            Logger("ERROR", "初始化配置管理器失败: " + e.getMessage());
        }
    }


    private static void ensureDataDirectory() {
        try {
            File dataDir = new File(GameConstants.DATA_DIR);
            if (!dataDir.exists()) {
                boolean created = dataDir.mkdirs();
                if (!created) {
                    Logger("ERROR", "创建data目录失败: " + dataDir.getAbsolutePath());
                }
            }
            GameConstants.setStarveilDirHidden();
        } catch (Exception e) {
            Logger("ERROR", "确保data目录存在时出错: " + e.getMessage());
        }
    }

    // ==================== 键注册 ====================
    //
    // 类型由调用方显式写明，默认值只提供「没设过值时读到什么」：
    //
    //   DataManager.defineBool("mymod", "cant_exit", false, Boolean.class, DataKeyFlag.PER_SAVE);
    //
    // 两者不一致（例如把 "true" 当布尔键的默认值）会在注册那一刻直接报错，
    // 不必等到读取时才发现值解析不了。默认值传 null 表示「没有默认值」。

    /** 声明一个字符串键。 */
    public static DataKey<String> defineStr(String namespace, String name, String defaultValue,
                                            DataKeyFlag... flags) {
        return DataKey.of(namespace, name, defaultValue, String.class, flags);
    }

    /** 声明一个布尔键。 */
    public static DataKey<Boolean> defineBool(String namespace, String name, Boolean defaultValue,
                                              DataKeyFlag... flags) {
        return DataKey.of(namespace, name, defaultValue, Boolean.class, flags);
    }

    /** 声明一个整数键。 */
    public static DataKey<Integer> defineInt(String namespace, String name, Integer defaultValue,
                                             DataKeyFlag... flags) {
        return DataKey.of(namespace, name, defaultValue, Integer.class, flags);
    }

    /** 声明一个长整数键。 */
    public static DataKey<Long> defineLong(String namespace, String name, Long defaultValue,
                                           DataKeyFlag... flags) {
        return DataKey.of(namespace, name, defaultValue, Long.class, flags);
    }

    /** 声明一个双精度浮点键。 */
    public static DataKey<Double> defineDouble(String namespace, String name, Double defaultValue,
                                               DataKeyFlag... flags) {
        return DataKey.of(namespace, name, defaultValue, Double.class, flags);
    }

    /** 声明一个单精度浮点键。 */
    public static DataKey<Float> defineFloat(String namespace, String name, Float defaultValue,
                                             DataKeyFlag... flags) {
        return DataKey.of(namespace, name, defaultValue, Float.class, flags);
    }

    /**
     * 声明一个键，类型显式指定（想写基本类型 {@code int.class} 时用这个）。
     *
     * @param defaultValue 默认值，可为 {@code null}
     * @param valueType    值类型，基本类型或包装类型都可以
     */
    public static <T> DataKey<T> define(String namespace, String name, T defaultValue,
                                        Class<?> valueType, DataKeyFlag... flags) {
        return DataKey.of(namespace, name, defaultValue, valueType, flags);
    }

    /** 该键是否已注册。 */
    public static boolean isKeyRegistered(String qualified) {
        return DataKeyRegistry.isRegistered(qualified);
    }

    /** 已注册的全部键（只读）。 */
    public static java.util.Set<String> registeredKeys() {
        return DataKeyRegistry.registeredKeys();
    }

    // ==================== 读取 ====================

    /**
     * 读取优先级解析。
     *
     * @return 原始值；{@code null} 表示「没有设置」/「键未注册」
     */
    private static String lookup(String qualified) {
        if (qualified == null) {
            return null;
        }
        String k = qualified.trim();

        // 1. 内存覆盖（调试窗口设的）优先于「键是否注册」：
        //    调试窗口要能覆盖任意键，包括还没声明的，否则它就没法用来排查
        //    「这个键到底有没有被读到」这类问题。
        if (MEMORY_OVERRIDES.containsKey(k)) {
            return MEMORY_OVERRIDES.get(k);
        }
        // 2. 临时键同理：它是本次运行的权威状态，刻意不落盘，
        //    即便配置文件没加载成功、键还没注册也必须能读到。
        String temporary = TEMPORARY.get(k);
        if (temporary != null) {
            return temporary;
        }

        DataKey<?> key = DataKeyRegistry.lookup(k);
        if (key == null) {
            Logger("WARNING", "读取了未注册的数据键 '" + k
                    + "' —— 已按「没有这个键」处理并返回默认值。"
                    + "请先用 DataManager.define* 或 DataKey.of 声明它");
            return null;
        }

        // 3. 教程进度键的归属是动态的（见 TutorialState）：跨存档模式下它就是普通全局键
        boolean perSave = key.scope() == DataKeyScope.PER_SAVE;
        if (perSave && key == FrameworkDataKeys.TUTORIAL_COMPLETED) {
            perSave = !TutorialState.isPersistMode();
        }
        // 4. 存档作用域的键：当前存档里有值且与全局不同时优先
        if (perSave) {
            String saveValue = SaveDataManager.getInstance().getString(k);
            String configValue = dataMap == null ? null : dataMap.get(k);
            if (saveValue != null && !saveValue.equals(configValue)) {
                return saveValue;
            }
            return configValue;
        }
        // 5. 尚未 initialize() 时（单测、极早期调用）按「没有配置」处理而不是抛 NPE
        if (dataMap == null) {
            return null;
        }
        return dataMap.get(k);
    }

    /** 供 {@link DataKey} 读取原始值。 */
    static Object readRaw(DataKey<?> key) {
        return lookup(key.qualified());
    }

    /**
     * 按原始字符串读一个已注册键，并解析为布尔。
     *
     * <p>与 {@code getBoolean(key)} 的区别：这里不做「未注册键告警」这类判断，
     * 只服务于框架内部状态机（见 {@code TutorialState}），它需要在键的注册定义
     * 之外独立读一个开关。
     */
    static Boolean rawBoolean(String qualified) {
        String raw = lookup(qualified);
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if ("true".equalsIgnoreCase(t)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(t)) {
            return Boolean.FALSE;
        }
        return null;
    }

    // ==================== 写入 ====================

    /**
     * 写入的统一入口：校验注册 → 校验只读 → 校验类型 → 分流（临时 / 存档 / 全局）。
     *
     * @return 是否真的写入
     */
    static boolean writeRaw(DataKey<?> key, Object value) {
        String k = key.qualified();

        if (DataKeyRegistry.isReadOnly(k)) {
            Logger("WARNING", "键 '" + k + "' 是只读键，写入被拒绝（值锁定在默认值 "
                    + key.defaultValue() + "），收到: " + value);
            return false;
        }

        // 类型校验：允许 int → long/double 这类数值拓宽，不允许把 "abc" 写进 int 键
        Coercion coercion = coerce(key.type(), value);
        if (!coercion.ok()) {
            Logger("WARNING", "键 '" + k + "' 注册类型为 " + key.type()
                    + "，但收到的值 " + describe(value) + " 不属于该类型，写入被拒绝");
            return false;
        }

        // 临时键：只改内存，永不落盘
        if (DataKeyRegistry.isTemporary(k)) {
            TEMPORARY.put(k, coercion.text());
            Logger("DEBUG", "已设置临时键（不写入文件）: " + k + " = " + coercion.text());
            return true;
        }

        // 存档作用域的键写进当前存档；没有存档时回落到全局
        if (isSaveScoped(key) && SaveDataManager.hasActiveInstance()) {
            SaveDataManager.getInstance().setString(k, coercion.text());
            return true;
        }

        if (dataMap == null) {
            // 尚未 initialize()（单测、极早期调用）时也要能写入内存 ——
            // 否则「值写不进去」会伪装成「键没注册」，排查方向完全错。
            // 此时 saveData() 会因为没有文件对象而失败并记录日志，写内存不受影响。
            dataMap = new java.util.concurrent.ConcurrentHashMap<>();
        }
        MEMORY_OVERRIDES.remove(k);
        dataMap.put(k, coercion.text());
        requestSave();
        return true;
    }

    // ==================== 批量写入 ====================

    /**
     * 合并多次写入，最后只落盘一次。
     *
     * <p><b>为什么需要它</b>：每次写入都会走完整的「Gson 序列化 → AES 加密 →
     * 阻塞写文件」。滑杆拖动一次会触发几十次 value 变化，绑一遍按键要写八个键 ——
     * 逐个落盘就是几十次磁盘写入，而且全在 FX 线程上。
     *
     * <pre>
     *   DataManager.batch(() -> {
     *       FrameworkDataKeys.BGM_VOLUME.set(v);
     *       FrameworkDataKeys.SFX_VOLUME.set(v);
     *   });   // 到这里才真正写文件，只写一次
     * </pre>
     *
     * <p>嵌套调用是安全的：只有最外层退出时才落盘。
     * 中途抛异常也会落盘 —— 已经写进内存的值不该因为后面的代码出错而丢掉。
     */
    public static void batch(Runnable writes) {
        if (writes == null) {
            return;
        }
        if (batchDepth > 0) {
            // 已经在批量里了，直接执行，由最外层负责落盘
            writes.run();
            return;
        }
        batchDepth = 1;
        try {
            writes.run();
        } finally {
            batchDepth = 0;
            if (batchDirty) {
                batchDirty = false;
                saveData();
            }
        }
    }

    /** 批量嵌套层数；0 表示不在批量中。 */
    private static int batchDepth = 0;

    /** 批量期间是否攒下了未落盘的改动。 */
    private static boolean batchDirty = false;

    /**
     * 请求落盘：在批量中就攒着，否则立刻写。
     *
     * <p>所有涉及 {@code dataMap} 的写入都必须经由它，别直接调 {@code saveData()}。
     */
    private static void requestSave() {
        if (batchDepth > 0) {
            batchDirty = true;
            return;
        }
        saveData();
    }

    /** 删除键在存储里的值（回到默认值）。 */
    static void removeQualified(DataKey<?> key) {
        String k = key.qualified();
        if (DataKeyRegistry.isReadOnly(k)) {
            Logger("WARNING", "键 '" + k + "' 是只读键，无法删除");
            return;
        }
        if (DataKeyRegistry.isTemporary(k)) {
            TEMPORARY.remove(k);
            return;
        }
        if (isSaveScoped(key) && SaveDataManager.hasActiveInstance()) {
            SaveDataManager.getInstance().remove(k);
            return;
        }
        MEMORY_OVERRIDES.remove(k);
        if (dataMap != null) {
            dataMap.remove(k);
            requestSave();
        }
    }

    /**
     * 该键当前是否写进「当前存档」。
     *
     * <p>一般是注册时的 {@link DataKeyScope}，只有教程进度键例外 ——
     * 它的归属由 {@code starveil:tutorial_persist} 在启动时决定。
     */
    private static boolean isSaveScoped(DataKey<?> key) {
        if (key.scope() != DataKeyScope.PER_SAVE) {
            return false;
        }
        if (key == FrameworkDataKeys.TUTORIAL_COMPLETED) {
            return !TutorialState.isPersistMode();
        }
        return true;
    }

    /**
     * 该键是否显式设过值。
     *
     * <p>不看 {@code lookup()}：那个方法会把「未注册」也返回 null，
     * 而这里问的是「设过没有」，只读键（永远停在默认值）也不算设过。
     */
    static boolean isSet(DataKey<?> key) {
        if (key.readOnly()) {
            return false;
        }
        String k = key.qualified();
        if (DataKeyRegistry.isTemporary(k)) {
            return TEMPORARY.containsKey(k);
        }
        if (MEMORY_OVERRIDES.containsKey(k)) {
            return true;
        }
        if (isSaveScoped(key) && SaveDataManager.getInstance().contains(k)) {
            return true;
        }
        return dataMap != null && dataMap.containsKey(k);
    }

    // ==================== 未注册键的兜底（旧接口用） ====================

    /**
     * 旧接口（裸字符串键 + 自带默认值）的读取。
     *
     * <p>键仍然必须注册 —— 未注册时记录 WARNING，并返回调用方给的 {@code fallback}
     * 以便老调用点不至于直接崩掉。这条路径只是过渡，新代码不该再用。
     *
     * <p>例外：调试覆盖与临时值即便键未注册也要能读出来。它们本来就是
     * 「让调试者直接压一个值」的逃生口，若还要先注册一遍就失去意义了。
     */
    private static String legacyRead(String key, String fallback) {
        String raw = lookup(key);
        if (raw != null) {
            return raw;
        }
        if (DataKeyRegistry.lookup(key) == null) {
            Logger("WARNING", "使用了未注册的数据键 '" + key + "'，返回调用方给的默认值 "
                    + fallback + "；请改用注册后的 DataKey 句柄");
        }
        return fallback;
    }

    /**
     * 旧接口的写入（裸字符串键）。
     *
     * <p>键必须注册，值必须符合注册类型。未注册直接拒绝。
     */
    private static void legacyWrite(String key, Object value) {
        DataKey<?> declared = DataKeyRegistry.lookup(key);
        if (declared == null) {
            Logger("ERROR", "尝试写入未注册的数据键 '" + key + "'，已拒绝。"
                    + "请先用 DataManager.define* 或 DataKey.of 声明它");
            return;
        }
        writeRaw(declared, value);
    }

    private static void legacyRemove(String key) {
        DataKey<?> declared = DataKeyRegistry.lookup(key);
        if (declared == null) {
            Logger("ERROR", "尝试删除未注册的数据键 '" + key + "'，已拒绝");
            return;
        }
        removeQualified(declared);
    }

    // ==================== 业务语义 ====================

    /**
     * 惯性是否启用。默认<b>开启</b>。
     *
     * <p>开启后玩家移动带加减速（松开方向键会滑行一小段），
     * 疾跑减速到静止时播放急停动画；关闭则恢复瞬时启停。
     */
    public static boolean isInertiaEnabled() {
        return FrameworkDataKeys.INERTIA.get();
    }

    /**
     * 是否禁止退出。
     *
     * <p>由剧情通过 {@link FrameworkDataKeys#CANT_EXIT} 控制，用来<b>强化代入感</b>：
     * 一段不容打断的独白、一次无法回头的抉择，退出按钮的存在本身就会削弱张力。
     * 设为 true 时主菜单与 ESC 暂停菜单都会移除退出入口；设回 false 或移除该键即恢复。
     *
     * <p>默认<b>不禁止</b>。
     */
    public static boolean isExitBlocked() {
        return FrameworkDataKeys.CANT_EXIT.get();
    }

    /** 教程是否已完成。作用域随 {@link FrameworkDataKeys#TUTORIAL_PERSIST} 切换。 */
    public static boolean isTutorialCompleted() {
        return TutorialState.isCompleted();
    }

    /** 记录教程完成状态。作用域随 {@link FrameworkDataKeys#TUTORIAL_PERSIST} 切换。 */
    public static void setTutorialCompleted(boolean completed) {
        TutorialState.setCompleted(completed);
    }

    // ==================== 旧接口：读取 ====================

    public static String getString(String key) {
        return legacyRead(key, null);
    }

    public static String getString(String key, String defaultValue) {
        return legacyRead(key, defaultValue);
    }

    /** 向后兼容的get方法（等同于getString） */
    public static String get(String key) {
        return getString(key);
    }

    /** 向后兼容的get方法（等同于getString） */
    public static String get(String key, String defaultValue) {
        return getString(key, defaultValue);
    }

    public static boolean getBoolean(String key) {
        return parseBoolean(key, getString(key, null), false);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return parseBoolean(key, getString(key, null), defaultValue);
    }

    public static int getInt(String key) {
        return parseInt(key, getString(key, null), 0);
    }

    public static int getInt(String key, int defaultValue) {
        return parseInt(key, getString(key, null), defaultValue);
    }

    public static long getLong(String key) {
        return parseLong(key, getString(key, null), 0L);
    }

    public static long getLong(String key, long defaultValue) {
        return parseLong(key, getString(key, null), defaultValue);
    }

    public static double getDouble(String key) {
        return parseDouble(key, getString(key, null), 0.0);
    }

    public static double getDouble(String key, double defaultValue) {
        return parseDouble(key, getString(key, null), defaultValue);
    }

    public static float getFloat(String key) {
        return parseFloat(key, getString(key, null), 0.0f);
    }

    public static float getFloat(String key, float defaultValue) {
        return parseFloat(key, getString(key, null), defaultValue);
    }

    // ==================== 旧接口：写入 ====================

    /**
     * 写入字符串值。
     *
     * <p>只对<b>字符串类型</b>的键有效。写进 int/boolean 键会被拒绝 ——
     * 值类型由注册决定，调用点不该靠「先转成字符串」绕过校验。
     */
    public static void set(String key, String value) {
        legacyWrite(key, value);
    }

    public static void setBoolean(String key, boolean value) {
        legacyWrite(key, value);
    }

    public static void setInt(String key, int value) {
        legacyWrite(key, value);
    }

    public static void setLong(String key, long value) {
        legacyWrite(key, value);
    }

    public static void setDouble(String key, double value) {
        legacyWrite(key, value);
    }

    public static void setFloat(String key, float value) {
        legacyWrite(key, value);
    }

    public static void remove(String key) {
        if (key == null) {
            return;
        }
        MEMORY_OVERRIDES.remove(key.trim());
        TEMPORARY.remove(key.trim());
        legacyRemove(key);
    }

    // ==================== 内存覆盖（调试用） ====================

    /**
     * 只改内存、<b>不写入文件</b>的覆盖层。
     *
     * <p>用途：调参时临时试一个值，不该把玩家或开发者的配置文件改脏。
     * 进程结束即失效。
     *
     * <p><b>唯一允许使用未注册键的入口</b> —— 调试窗口需要能覆盖任意键，
     * 包括还没注册的。但它同样会被 {@code set*} 顶掉。
     *
     * <p><b>覆盖会被代码写入顶掉</b>：只要代码显式写同一个键，覆盖立即失效、
     * 以新写入的值为准。这是刻意的 ——「代码设定的值」永远比「调试时手动改的值」
     * 权威，否则一个忘了清掉的调试覆盖会悄悄压住游戏逻辑真正想写的值。
     */
    private static final Map<String, String> MEMORY_OVERRIDES = new HashMap<>();

    /** 设置内存覆盖（不落盘）。 */
    public static void setMemoryOverride(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        MEMORY_OVERRIDES.put(key.trim(), value);
        Logger("INFO", "已设置内存覆盖（未写入文件）: " + key.trim() + " = " + value);
    }

    public static void clearMemoryOverride(String key) {
        if (key != null && MEMORY_OVERRIDES.remove(key.trim()) != null) {
            Logger("INFO", "已清除内存覆盖: " + key.trim());
        }
    }

    public static void clearAllMemoryOverrides() {
        if (!MEMORY_OVERRIDES.isEmpty()) {
            Logger("INFO", "已清除全部内存覆盖（" + MEMORY_OVERRIDES.size() + " 项）");
            MEMORY_OVERRIDES.clear();
        }
    }

    public static boolean hasMemoryOverride(String key) {
        return key != null && MEMORY_OVERRIDES.containsKey(key.trim());
    }

    /** 当前所有内存覆盖（只读快照）。 */
    public static Map<String, String> getMemoryOverrides() {
        return new HashMap<>(MEMORY_OVERRIDES);
    }

    // ==================== 临时键 ====================

    /**
     * 临时键：<b>不写入文件</b>，只在当前进程内有效，进程结束即消失。
     *
     * <p>存在的意义是替掉「写进配置、用完再主动清理」那套做法。后者有两个毛病：
     * <ul>
     *   <li>崩溃或被强杀时清理不掉，标记就永久残留在配置里
     *       （例如「异常关闭」「禁止退出」这类标记）；</li>
     *   <li>清理逻辑散落在启动流程各处，容易漏。</li>
     * </ul>
     * 改成进程内状态后<b>根本不需要清理</b> —— 生命周期由进程本身保证。
     *
     * <p>优先级高于内存覆盖（调试覆盖）与文件内容。
     */
    private static final Map<String, String> TEMPORARY =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 设置一个临时键（不落盘）。
     *
     * <p>键必须已注册；注册成 {@link DataKeyFlag#TEMPORARY} 的键用普通
     * {@code set()} 写也走这条路。
     */
    public static void setTemporary(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        DataKey<?> declared = DataKeyRegistry.lookup(key.trim());
        if (declared == null) {
            Logger("ERROR", "尝试写入未注册的临时键 '" + key.trim() + "'，已拒绝");
            return;
        }
        if (value == null) {
            TEMPORARY.remove(declared.qualified());
            return;
        }
        TEMPORARY.put(declared.qualified(), value);
        Logger("DEBUG", "已设置临时键（不写入文件）: " + declared.qualified() + " = " + value);
    }

    public static void setTemporaryBoolean(String key, boolean value) {
        setTemporary(key, String.valueOf(value));
    }

    public static void setTemporaryInt(String key, int value) {
        setTemporary(key, String.valueOf(value));
    }

    public static boolean isTemporary(String key) {
        return key != null && TEMPORARY.containsKey(key.trim());
    }
    public static void clearTemporary(String key) {
        if (key != null) {
            TEMPORARY.remove(key.trim());
        }
    }

    /** 当前所有临时键（只读快照）。 */
    public static Map<String, String> getTemporaryKeys() {
        return new HashMap<>(TEMPORARY);
    }


    public static boolean contains(String key) {
        return key != null && lookup(key.trim()) != null;
    }

    public static Map<String, String> getAll() {
        return dataMap == null ? new HashMap<>() : new HashMap<>(dataMap);
    }

    public static void clear() {
        if (dataMap != null) {
            dataMap.clear();
            requestSave();
        }
    }

    public static String getDataFilePath() {
        return dataFile.getAbsolutePath();
    }


    public static String getDataDirectoryPath() {
        return new File(GameConstants.DATA_DIR).getAbsolutePath();
    }

    public static void reload() {
        loadData();
        // 重载也是一次内容变化，调试窗口需要据此刷新
        fireChanged();
    }

    private static void loadData() {
        try {
            if (dataFile.exists()) {
                String json = FileCrypto.loadAndDecrypt(dataFile.getAbsolutePath());
                Type type = new TypeToken<HashMap<String, String>>(){}.getType();
                Map<String, String> loadedData = gson.fromJson(json, type);
                dataMap = Objects.requireNonNullElseGet(loadedData, HashMap::new);
            } else {
                dataMap = new java.util.concurrent.ConcurrentHashMap<>();
                Logger("INFO", "配置文件不存在，使用默认配置");
            }
        } catch (Exception e) {
            dataMap = new java.util.concurrent.ConcurrentHashMap<>();
            Logger("ERROR", "加载配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 历史键迁移已移除。
     *
     * <p>框架仍在测试阶段，<b>不做历史数据迁移</b>：老配置直接删掉即可。
     * 保留这段说明是为了避免以后有人「补回」一个其实不需要的迁移逻辑。
     */

    private static void saveData() {
        try {
            ensureDataDirectory();

            String json = gson.toJson(dataMap);
            FileCrypto.encryptAndSave(dataFile.getAbsolutePath(), json);
        } catch (Exception e) {
            Logger("ERROR", "保存配置失败: " + e.getMessage());
        }
        // 所有写入都汇聚到这里，因此这一个点是可靠的变化通知源。
        // 调试窗口据此按需重读并解密 game.dat，而不是每 500ms 无脑读一次。
        fireChanged();
    }

    // ==================== 变更通知 ====================

    private static final List<Runnable> changeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.add(listener);
        }
    }

    public static void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private static void fireChanged() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
                // 单个监听者出错不应影响其它监听者
            }
        }
    }


    public static void exportDataToPlainText() {
        try {
            String json = gson.toJson(dataMap);
            Path exportPath = Paths.get(GameConstants.DATA_DIR, "config_export.json");
            Files.write(exportPath, json.getBytes());
            Logger("INFO", "配置已导出为明文: " + exportPath.toAbsolutePath());
        } catch (Exception e) {
            Logger("ERROR", "导出配置失败: " + e.getMessage());
        }
    }


    public static void importDataFromPlainText() {
        try {
            Path importPath = Paths.get(GameConstants.DATA_DIR, "config_export.json");
            if (Files.exists(importPath)) {
                String json = new String(Files.readAllBytes(importPath));
                Type type = new TypeToken<HashMap<String, String>>(){}.getType();
                Map<String, String> importedData = gson.fromJson(json, type);
                if (importedData != null) {
                    dataMap = importedData;
                    saveData();
                    Logger("INFO", "配置从明文文件导入成功");
                }
            }
        } catch (Exception e) {
            Logger("ERROR", "导入配置失败: " + e.getMessage());
        }
    }


    public static void backupConfig() {
        try {
            if (dataFile.exists()) {
                Path backupPath = Paths.get(GameConstants.DATA_DIR, "game_backup_" + System.currentTimeMillis() + ".dat");
                Files.copy(dataFile.toPath(), backupPath);
                Logger("INFO", "配置文件已备份: " + backupPath.getFileName());
            }
        } catch (Exception e) {
            Logger("ERROR", "备份配置失败: " + e.getMessage());
        }
    }


    public static boolean restoreConfigFromBackup(String backupFileName) {
        try {
            Path backupPath = Paths.get(GameConstants.DATA_DIR, backupFileName);
            if (Files.exists(backupPath)) {
                Files.copy(backupPath, dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                loadData();
                Logger("INFO", "配置文件从备份恢复成功: " + backupFileName);
                return true;
            }
        } catch (Exception e) {
            Logger("ERROR", "恢复配置失败: " + e.getMessage());
        }
        return false;
    }


    public static File[] getBackupFiles() {
        File dataDir = new File(GameConstants.DATA_DIR);
        if (dataDir.exists()) {
            return dataDir.listFiles((dir, name) -> name.startsWith("game_backup_") && name.endsWith(".dat"));
        }
        return new File[0];
    }

    /**
     * 从resources加载JSON文件内容
     */
    public static String loadJson(String resourcePath) {
        try (InputStream is = ResourceResolver.getResourceAsStream(resourcePath)) {
            if (is == null) {
                Logger("ERROR", "Resource not found: " + resourcePath);
                return null;
            }
            return new String(is.readAllBytes());
        } catch (Exception e) {
            Logger("ERROR", "Failed to load JSON resource: " + resourcePath + ", " + e.getMessage());
            return null;
        }
    }

    /**
     * 将JSON字符串解析为Map
     */
    public static Map<String, Object> parseJsonToMap(String json) {
        try {
            Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
            return gson.fromJson(json, type);
        } catch (Exception e) {
            Logger("ERROR", "Failed to parse JSON to Map: " + e.getMessage());
            return null;
        }
    }

    /**
     * 从JSON字符串中获取指定键的字符串值
     */
    public static String getStringFromJson(String json, String key, String defaultValue) {
        try {
            Map<String, Object> map = parseJsonToMap(json);
            if (map != null && map.containsKey(key)) {
                Object value = map.get(key);
                return value != null ? value.toString() : defaultValue;
            }
        } catch (Exception e) {
            Logger("ERROR", "Failed to get string from JSON: " + e.getMessage());
        }
        return defaultValue;
    }

    public static double getDoubleFromJson(String json, String key, double defaultValue) {
        try {
            Map<String, Object> map = parseJsonToMap(json);
            if (map != null && map.containsKey(key)) {
                Object value = map.get(key);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                } else if (value instanceof String) {
                    try {
                        return Double.parseDouble((String) value);
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                }
            }
        } catch (Exception e) {
            Logger("ERROR", "Failed to get double from JSON: " + e.getMessage());
        }
        return defaultValue;
    }

    // ==================== 值类型转换 ====================

    /** 写入时的类型校验结果。 */
    private record Coercion(boolean ok, String text) {
    }

    /**
     * 校验并归一化一个待写入的值。
     *
     * <p>数值类型之间允许<b>拓宽</b>（int 写进 long/double 键），因为那是无损的；
     * 反过来不允许，因为有精度损失或直接是错的。
     */
    private static Coercion coerce(DataType type, Object value) {
        if (value == null) {
            return new Coercion(true, null);
        }
        switch (type) {
            case STRING:
                return value instanceof String s
                        ? new Coercion(true, s)
                        : new Coercion(false, null);
            case BOOLEAN:
                return value instanceof Boolean b
                        ? new Coercion(true, String.valueOf(b))
                        : new Coercion(false, null);
            case INT:
                return value instanceof Integer i
                        ? new Coercion(true, String.valueOf(i))
                        : new Coercion(false, null);
            case LONG:
                if (value instanceof Long l) return new Coercion(true, String.valueOf(l));
                if (value instanceof Integer i) return new Coercion(true, String.valueOf(i.longValue()));
                return new Coercion(false, null);
            case DOUBLE:
                if (value instanceof Double d) return new Coercion(true, String.valueOf(d));
                if (value instanceof Number n) return new Coercion(true, String.valueOf(n.doubleValue()));
                return new Coercion(false, null);
            case FLOAT:
                if (value instanceof Float f) return new Coercion(true, String.valueOf(f));
                if (value instanceof Number n) return new Coercion(true, String.valueOf(n.floatValue()));
                return new Coercion(false, null);
            default:
                return new Coercion(false, null);
        }
    }

    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        return "'" + value + "' (" + value.getClass().getSimpleName() + ")";
    }

    private static boolean parseBoolean(String key, String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        Logger("WARNING", "键 '" + key + "' 的值不是合法布尔值: '" + value + "'，使用默认值: " + fallback);
        return fallback;
    }

    private static int parseInt(String key, String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析整数配置: " + key + " = " + value + "，使用默认值: " + fallback);
            return fallback;
        }
    }

    private static long parseLong(String key, String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析长整数配置: " + key + " = " + value + "，使用默认值: " + fallback);
            return fallback;
        }
    }

    private static double parseDouble(String key, String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数配置: " + key + " = " + value + "，使用默认值: " + fallback);
            return fallback;
        }
    }

    private static float parseFloat(String key, String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数配置: " + key + " = " + value + "，使用默认值: " + fallback);
            return fallback;
        }
    }

    /**
     * 仅测试使用：清空内存状态与键注册表。
     *
     * <p>公开是为了让其它包的测试也能重置这一层静态状态。
     */
    public static void resetForTest() {
        MEMORY_OVERRIDES.clear();
        TEMPORARY.clear();
        dataMap = null;
        dataFile = null;
        changeListeners.clear();
        DataKeyRegistry.resetForTest();
    }
}
