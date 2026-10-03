package com.xiaowu.game.starveil.infrastructure.persistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 存档变量管理器
 *
 * <p>管理游戏过程中的存档变量（与特定存档绑定的变量）：这些变量随当前存档保存在
 * {@code save.dat} 中，仅在当前游玩的实例中有效，不写进 {@code data.dat}（全局配置）。
 *
 * <p><b>键必须先注册</b> —— 与 {@link DataManager} 同一套规则、同一个注册表。
 * 注册写明命名空间与键名（以及默认值/类型），读取时按注册的类型校验。
 *
 * <pre>
 *   // 声明
 *   DataKey&lt;Integer&gt; KILLS = DataManager.defineInt("chapter3", "kills", 0,
 *           DataKeyFlag.PER_SAVE);
 *
 *   // 读写
 *   KILLS.set(10);
 *   int n = KILLS.getInt();
 * </pre>
 *
 * <p>旧接口（裸字符串键）同样要求先注册，未注册的键读写都会被拒绝并记录日志。
 * {@code getString(String)} 这类方法仅用于读存档里已有的原始值（例如存档迁移、
 * 调试显示），业务代码应当使用 {@link DataKey} 句柄。
 *
 * <p>使用示例：
 * <pre>
 * // 新游戏时清空变量
 * SaveDataManager.getInstance().clear();
 * </pre>
 */
public class SaveDataManager {
    private static SaveDataManager instance;

    // 存储所有存档变量
    //
    // 用 ConcurrentHashMap 而不是 HashMap：剧情线程（StoryScript 的
    // set / setFlag / setCounter）与 FX 线程（设置、调试窗口）都会写它。
    // 普通 HashMap 在并发写入下可能丢数据，极端情况下迭代会陷入死循环。
    private final Map<String, String> saveVariables =
            new java.util.concurrent.ConcurrentHashMap<>();

    private SaveDataManager() {
    }

    /**
     * 获取单例实例。
     *
     * <p>注意：仅仅取到实例不代表「正在玩一个存档」—— 判断有没有活跃存档请用
     * {@link #hasActiveInstance()}，否则 {@link DataManager} 会把「框架启动时读一个
     * 存档作用域的键」误判成「玩家正在游戏里」。
     */
    public static SaveDataManager getInstance() {
        if (instance == null) {
            instance = new SaveDataManager();
        }
        return instance;
    }

    /**
     * 当前是否存在活跃存档（已经加载过一个存档）。
     *
     * <p>由 {@code GameInstance} 在加载/新建存档时置为 true、退出时置回 false。
     */
    public static boolean hasActiveInstance() {
        return instance != null && instance.active;
    }

    /** 标记存档已加载 / 已卸载。 */
    public static void setActive(boolean active) {
        getInstance().active = active;
        Logger("DEBUG", "存档会话状态: " + (active ? "已加载" : "未加载"));
    }

    /** 是否有活跃存档。 */
    private boolean active = false;
    /**
     * 清空所有存档变量（新游戏时调用）
     */
    public void clear() {
        saveVariables.clear();
        Logger("DEBUG", "所有存档变量已清空");
    }

    /**
     * 检查是否存在指定变量
     */
    public boolean contains(String key) {
        return key != null && saveVariables.containsKey(key.trim());
    }

    /**
     * 删除指定变量
     */
    public void remove(String key) {
        if (key != null) {
            saveVariables.remove(key.trim());
        }
    }

    /**
     * 获取所有变量
     */
    public Map<String, String> getAllVariables() {
        return new HashMap<>(saveVariables);
    }

    // ==================== 原始读写（键必须已注册） ====================

    /**
     * 注册并读取一个字符串变量。
     *
     * <p>键未注册时返回 {@code null} 并告警 —— 这与
     * {@link DataManager} 的规则一致。
     */
    public String getString(String key, String defaultValue) {
        DataKey<?> declared = requireRegistered(key, "读取");
        if (declared == null) {
            return defaultValue;
        }
        String raw = saveVariables.get(declared.qualified());
        return raw == null ? defaultValue : String.valueOf(declared.parse(raw));
    }

    public String getString(String key) {
        return getString(key, null);
    }

    /**
     * 写入一个字符串变量。键必须已注册，且值必须符合注册类型。
     */
    public void setString(String key, String value) {
        DataKey<?> declared = requireRegistered(key, "写入");
        if (declared == null) {
            return;
        }
        writeRaw(declared, value);
    }

    /**
     * 写入任意已注册键，类型由注册决定。
     *
     * <p>入参可以是<b>类型正确的对象</b>（{@code DataKey.set(7)}），也可以是
     * <b>该值的字符串形式</b>（{@code setString("…:i", "7")}）—— 存储层本来就是字符串，
     * 两种写法最终都归一化到注册类型。类型不符则拒绝并告警。
     *
     * <p>供 {@link DataKey} 与 {@link DataManager} 使用。
     */
    static boolean writeRaw(DataKey<?> declared, Object value) {
        String k = declared.qualified();
        if (DataKeyRegistry.isReadOnly(k)) {
            Logger("WARNING", "键 '" + k
                    + "' 是只读键，写入被拒绝（值锁定在默认值 " + declared.defaultValue() + "）");
            return false;
        }
        if (value == null) {
            getInstance().saveVariables.remove(k);
            return true;
        }

        String text;
        if (value instanceof String s) {
            // 字符串入参：按注册类型校验可解析性，再归一化存储
            if (!declared.canParse(s)) {
                Logger("WARNING", "键 '" + k + "' 的值 '" + s + "' 无法解析为 "
                        + declared.type() + "，写入被拒绝");
                return false;
            }
            text = s;
        } else if (declared.type().matches(value)) {
            text = String.valueOf(value);
        } else {
            Logger("WARNING", "键 '" + k + "' 注册类型为 " + declared.type()
                    + "，但收到的值 '" + value + "' (" + value.getClass().getSimpleName()
                    + ") 不属于该类型，写入被拒绝");
            return false;
        }
        getInstance().saveVariables.put(k, text);
        return true;
    }

    /** 键未注册时告警并返回 null。 */
    private static DataKey<?> requireRegistered(String key, String action) {
        if (key == null) {
            return null;
        }
        DataKey<?> declared = DataKeyRegistry.lookup(key.trim());
        if (declared == null) {
            Logger("WARNING", "尝试" + action + "未注册的存档变量 '" + key
                    + "' —— 已忽略。请先用 DataManager.define* 或 DataKey.of 声明它");
        }
        return declared;
    }

    // ==================== 布尔类型 ====================

    public boolean getBoolean(String key) {
        return parseBoolean(key, getString(key, null), false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return parseBoolean(key, getString(key, null), defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        setString(key, String.valueOf(value));
    }

    // ==================== 整数类型 ====================

    public int getInt(String key) {
        return getInt(key, 0);
    }

    public int getInt(String key, int defaultValue) {
        String raw = getRaw(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析整数存档变量: " + key + " = " + raw + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    public void setInt(String key, int value) {
        setString(key, String.valueOf(value));
    }

    public void incrementInt(String key) {
        incrementInt(key, 1);
    }

    /** 增加整数变量（指定增量） */
    public void incrementInt(String key, int delta) {
        setInt(key, getInt(key, 0) + delta);
    }

    // ==================== 长整数类型 ====================

    public long getLong(String key) {
        return getLong(key, 0L);
    }

    public long getLong(String key, long defaultValue) {
        String raw = getRaw(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析长整数存档变量: " + key + " = " + raw + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    public void setLong(String key, long value) {
        setString(key, String.valueOf(value));
    }

    public void incrementLong(String key) {
        incrementLong(key, 1L);
    }

    public void incrementLong(String key, long delta) {
        setLong(key, getLong(key, 0L) + delta);
    }

    // ==================== 浮点数类型 ====================

    public double getDouble(String key) {
        return getDouble(key, 0.0);
    }

    public double getDouble(String key, double defaultValue) {
        String raw = getRaw(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数存档变量: " + key + " = " + raw + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    public void setDouble(String key, double value) {
        setString(key, String.valueOf(value));
    }

    public float getFloat(String key) {
        return getFloat(key, 0.0f);
    }

    public float getFloat(String key, float defaultValue) {
        String raw = getRaw(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数存档变量: " + key + " = " + raw + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    public void setFloat(String key, float value) {
        setString(key, String.valueOf(value));
    }

    // ==================== 命名空间键 ====================

    /**
     * 带命名空间的存档变量键。
     *
     * <p>之所以做成值对象而不是两个 {@code String} 参数：本类已有
     * {@code getString(String key, String defaultValue)}，再叠加
     * {@code getString(String namespace, String name)} 会因为参数类型完全相同
     * 而<b>无法重载</b>。用 {@code SaveKey} 承载「命名空间 + 名字」两个字段，
     * 既满足「分开传入」的要求，又不会有重载歧义。
     *
     * <p>限定规则（{@link #qualified()}）：
     * <ul>
     *   <li>已经是完整键名（含 {@code :}）的直接使用；</li>
     *   <li>其余键自动补上命名空间前缀，例如 {@code namespace="chapter3"} +
     *       {@code name="123"} → {@code chapter3:123}。</li>
     * </ul>
     */
    public record SaveKey(String namespace, String name) {
        public SaveKey {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("存档变量名不能为空");
            }
            name = name.trim();
            namespace = (namespace == null || namespace.trim().isEmpty()) ? null : namespace.trim();
        }

        public static SaveKey of(String namespace, String name) {
            return new SaveKey(namespace, name);
        }

        /** 实际写入存档的键名。 */
        public String qualified() {
            if (name.indexOf(DataKeyRegistry.SEPARATOR) >= 0) {
                return name;
            }
            if (namespace == null) {
                throw new IllegalArgumentException(
                        "非完整键名必须提供命名空间（例如 chapter3 + 123），收到: " + name);
            }
            return namespace + DataKeyRegistry.SEPARATOR + name;
        }
    }

    /** 已登记的键。注册是「模式」，不随新游戏清空。 */
    private final Set<String> registeredKeys = new HashSet<>();

    /**
     * 提前登记一个存档变量键。
     *
     * <p>登记会转交给 {@link DataKeyRegistry} —— 只有注册表里的键才能读写，
     * 因此这里登记过的键必须同时给出类型与默认值（见
     * {@code DataManager.define*(namespace, name, defaultValue, PER_SAVE)}）。
     */
    public void registerKey(SaveKey key) {
        DataKey<?> declared = DataKeyRegistry.lookup(key.qualified());
        if (declared == null) {
            Logger("WARNING", "存档变量 '" + key.qualified()
                    + "' 未在键注册表中声明 —— 请用 DataManager.define*(..., PER_SAVE) 注册，"
                    + "仅调用 registerKey 不再足够");
            return;
        }
        if (!registeredKeys.add(declared.qualified())) {
            Logger("WARNING", "存档变量重复注册: " + declared.qualified());
            return;
        }
        Logger("DEBUG", "已登记存档变量: " + declared.qualified());
    }

    public void registerKey(String namespace, String name) {
        registerKey(new SaveKey(namespace, name));
    }

    public boolean isKeyRegistered(SaveKey key) {
        return DataKeyRegistry.isRegistered(key.qualified());
    }

    /** 读取存档里的原始字符串（未注册时告警并返回 null）。 */
    private String getRaw(String key) {
        DataKey<?> declared = requireRegistered(key, "读取");
        if (declared == null) {
            return null;
        }
        return saveVariables.get(declared.qualified());
    }

    /** 清空键登记（仅测试与重载模式定义时使用）。 */
    public void clearRegisteredKeys() {
        registeredKeys.clear();
    }

    public Set<String> registeredKeyNames() {
        return new HashSet<>(registeredKeys);
    }

    // ==================== 导出/导入（用于 SaveManager） ====================

    /**
     * 导出所有变量（保存到存档时调用）
     *
     * @return 包含所有变量的 Map
     */
    public Map<String, String> exportAll() {
        return new HashMap<>(saveVariables);
    }

    /**
     * 导入变量（加载存档时调用）
     *
     * <p>就地替换内容而不是重新赋值字段：读档发生在 FX 线程，而剧情线程可能
     * 正在读存档变量。重新赋值会让两边看到不同的 map，换掉的那份里刚写进去的
     * 值就悄悄丢了。
     *
     * @param variables 要导入的变量 Map
     */
    public void importAll(Map<String, String> variables) {
        if (variables != null) {
            saveVariables.clear();
            saveVariables.putAll(variables);
            Logger("DEBUG", "已导入 " + saveVariables.size() + " 个存档变量");
        }
    }

    // ==================== 调试信息 ====================

    /**
     * 获取变量数量
     */
    public int getVariableCount() {
        return saveVariables.size();
    }

    /**
     * 打印所有变量（用于调试）
     */
    public void printAllVariables() {
        Logger("DEBUG", "=== 存档变量列表 (" + saveVariables.size() + " 个) ===");
        for (Map.Entry<String, String> entry : saveVariables.entrySet()) {
            Logger("DEBUG", "  " + entry.getKey() + " = " + entry.getValue());
        }
        Logger("DEBUG", "=== 存档变量列表结束 ===");
    }

    // ==================== 内部解析 ====================

    private static boolean parseBoolean(String key, String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        String t = value.trim();
        if ("true".equalsIgnoreCase(t)) {
            return true;
        }
        if ("false".equalsIgnoreCase(t)) {
            return false;
        }
        Logger("WARNING", "键 '" + key + "' 的值不是合法布尔值: '" + value + "'，使用默认值: " + fallback);
        return fallback;
    }

    /** 仅测试使用。 */
    void resetForTest() {
        saveVariables.clear();
        registeredKeys.clear();
        active = false;
    }
}
