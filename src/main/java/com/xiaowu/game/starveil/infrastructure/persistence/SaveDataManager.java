package com.xiaowu.game.starveil.infrastructure.persistence;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 存档变量管理器
 * 
 * 功能特点：
 * - 管理游戏过程中的存档变量（与特定存档绑定的变量）
 * - 这些变量会随当前存档保存在 save.dat 中
 * - 仅在当前游玩的实例中有效
 * - 存档和读取时保持有效
 * - 不存储在 data.dat（全局配置）中
 * 
 * 使用示例：
 * <pre>
 * // 设置变量
 * SaveDataManager.getInstance().setString("chapter", "1");
 * SaveDataManager.getInstance().setInt("killed_enemies", 10);
 * SaveDataManager.getInstance().setBoolean("has_key", true);
 * 
 * // 获取变量
 * String chapter = SaveDataManager.getInstance().getString("chapter");
 * int killed = SaveDataManager.getInstance().getInt("killed_enemies", 0);
 * boolean hasKey = SaveDataManager.getInstance().getBoolean("has_key", false);
 * 
 * // 新游戏时清空变量
 * SaveDataManager.getInstance().clear();
 * </pre>
 */
public class SaveDataManager {
    private static SaveDataManager instance;
    
    // 存储所有存档变量
    private Map<String, String> saveVariables;
    
    private SaveDataManager() {
        saveVariables = new HashMap<>();
    }
    
    /**
     * 获取单例实例
     */
    public static SaveDataManager getInstance() {
        if (instance == null) {
            instance = new SaveDataManager();
        }
        return instance;
    }
    
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
        return saveVariables.containsKey(key);
    }
    
    /**
     * 删除指定变量
     */
    public void remove(String key) {
        saveVariables.remove(key);
    }
    
    /**
     * 获取所有变量
     */
    public Map<String, String> getAllVariables() {
        return new HashMap<>(saveVariables);
    }
    
    // ==================== 字符串类型 ====================
    
    /**
     * 获取字符串变量
     */
    public String getString(String key) {
        return saveVariables.get(key);
    }
    
    /**
     * 获取字符串变量（带默认值）
     */
    public String getString(String key, String defaultValue) {
        return saveVariables.getOrDefault(key, defaultValue);
    }
    
    /**
     * 设置字符串变量
     */
    public void setString(String key, String value) {
        if (value == null) {
            saveVariables.remove(key);
        } else {
            saveVariables.put(key, value);
        }
    }
    
    // ==================== 布尔类型 ====================
    
    /**
     * 获取布尔变量
     */
    public boolean getBoolean(String key) {
        String value = saveVariables.get(key);
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * 获取布尔变量（带默认值）
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = saveVariables.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * 设置布尔变量
     */
    public void setBoolean(String key, boolean value) {
        saveVariables.put(key, String.valueOf(value));
    }
    
    // ==================== 整数类型 ====================
    
    /**
     * 获取整数变量
     */
    public int getInt(String key) {
        String value = saveVariables.get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析整数存档变量: " + key + " = " + value);
            return 0;
        }
    }
    
    /**
     * 获取整数变量（带默认值）
     */
    public int getInt(String key, int defaultValue) {
        String value = saveVariables.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析整数存档变量: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 设置整数变量
     */
    public void setInt(String key, int value) {
        saveVariables.put(key, String.valueOf(value));
    }
    
    /**
     * 增加整数变量
     */
    public void incrementInt(String key) {
        int currentValue = getInt(key, 0);
        setInt(key, currentValue + 1);
    }
    
    /**
     * 增加整数变量（指定增量）
     */
    public void incrementInt(String key, int delta) {
        int currentValue = getInt(key, 0);
        setInt(key, currentValue + delta);
    }
    
    // ==================== 长整数类型 ====================
    
    /**
     * 获取长整数变量
     */
    public long getLong(String key) {
        String value = saveVariables.get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析长整数存档变量: " + key + " = " + value);
            return 0L;
        }
    }
    
    /**
     * 获取长整数变量（带默认值）
     */
    public long getLong(String key, long defaultValue) {
        String value = saveVariables.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析长整数存档变量: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 设置长整数变量
     */
    public void setLong(String key, long value) {
        saveVariables.put(key, String.valueOf(value));
    }
    
    /**
     * 增加长整数变量
     */
    public void incrementLong(String key) {
        long currentValue = getLong(key, 0);
        setLong(key, currentValue + 1);
    }
    
    /**
     * 增加长整数变量（指定增量）
     */
    public void incrementLong(String key, long delta) {
        long currentValue = getLong(key, 0);
        setLong(key, currentValue + delta);
    }
    
    // ==================== 浮点数类型 ====================
    
    /**
     * 获取双精度浮点数变量
     */
    public double getDouble(String key) {
        String value = saveVariables.get(key);
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数存档变量: " + key + " = " + value);
            return 0.0;
        }
    }
    
    /**
     * 获取双精度浮点数变量（带默认值）
     */
    public double getDouble(String key, double defaultValue) {
        String value = saveVariables.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数存档变量: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 设置双精度浮点数变量
     */
    public void setDouble(String key, double value) {
        saveVariables.put(key, String.valueOf(value));
    }
    
    /**
     * 获取单精度浮点数变量
     */
    public float getFloat(String key) {
        String value = saveVariables.get(key);
        if (value == null) {
            return 0.0f;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数存档变量: " + key + " = " + value);
            return 0.0f;
        }
    }
    
    /**
     * 获取单精度浮点数变量（带默认值）
     */
    public float getFloat(String key, float defaultValue) {
        String value = saveVariables.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数存档变量: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 设置单精度浮点数变量
     */
    public void setFloat(String key, float value) {
        saveVariables.put(key, String.valueOf(value));
    }
    
    // ==================== 命名空间键（推荐用法） ====================

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
     *   <li>特殊键（{@link SpecialKeys} 中已注册，如 {@code starveil:inertia}）
     *       直接使用自身，<b>不加</b>章节前缀；</li>
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
            if (SpecialKeys.isSpecial(name)) {
                return name;
            }
            if (name.indexOf(SpecialKeys.SEPARATOR) >= 0) {
                return name;
            }
            if (namespace == null) {
                throw new IllegalArgumentException(
                        "非特殊键必须提供命名空间（例如 chapter3 + 123），收到: " + name);
            }
            return namespace + SpecialKeys.SEPARATOR + name;
        }
    }

    /** 已登记的键。注册是「模式」，不随新游戏清空。 */
    private final Set<String> registeredKeys = new HashSet<>();

    /**
     * 提前登记一个存档变量键。使用前必须先登记。
     *
     * <p>强制登记是为了把「拼错键名」从「静默丢数据」变成「启动即报错」——
     * 存档变量一旦写错名字，玩家只会看到进度莫名消失。
     */
    public void registerKey(SaveKey key) {
        String q = key.qualified();
        if (!registeredKeys.add(q)) {
            Logger("WARNING", "存档变量重复注册: " + q);
            return;
        }
        Logger("DEBUG", "已注册存档变量: " + q);
    }

    public void registerKey(String namespace, String name) {
        registerKey(new SaveKey(namespace, name));
    }

    public boolean isKeyRegistered(SaveKey key) {
        String q = key.qualified();
        return SpecialKeys.isSpecial(q) || registeredKeys.contains(q);
    }

    /** 解析并校验：未提前注册直接抛异常。 */
    private String resolve(SaveKey key) {
        String q = key.qualified();
        if (SpecialKeys.isSpecial(q)) {
            return q;
        }
        if (!registeredKeys.contains(q)) {
            throw new IllegalArgumentException(
                    "存档变量未提前注册: " + q + "（请先调用 registerKey(\"" 
                            + key.namespace() + "\", \"" + key.name() + "\")）");
        }
        return q;
    }

    /** 清空键登记（仅测试与重载模式定义时使用）。 */
    public void clearRegisteredKeys() {
        registeredKeys.clear();
    }

    public Set<String> registeredKeyNames() {
        return new HashSet<>(registeredKeys);
    }

    // ---- 字符串 ----

    public String getScopedString(SaveKey key, String defaultValue) {
        return saveVariables.getOrDefault(resolve(key), defaultValue);
    }

    public void setScopedString(SaveKey key, String value) {
        String q = resolve(key);
        if (value == null) {
            saveVariables.remove(q);
        } else {
            saveVariables.put(q, value);
        }
    }

    // ---- 布尔 ----

    public boolean getScopedBoolean(SaveKey key, boolean defaultValue) {
        String value = saveVariables.get(resolve(key));
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public void setScopedBoolean(SaveKey key, boolean value) {
        saveVariables.put(resolve(key), String.valueOf(value));
    }

    // ---- 整数 ----

    public int getScopedInt(SaveKey key, int defaultValue) {
        String value = saveVariables.get(resolve(key));
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析整数存档变量: " + resolve(key) + " = " + value);
            return defaultValue;
        }
    }

    public void setScopedInt(SaveKey key, int value) {
        saveVariables.put(resolve(key), String.valueOf(value));
    }

    public void incrementScopedInt(SaveKey key, int delta) {
        setScopedInt(key, getScopedInt(key, 0) + delta);
    }

    // ---- 长整数 ----

    public long getScopedLong(SaveKey key, long defaultValue) {
        String value = saveVariables.get(resolve(key));
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析长整数存档变量: " + resolve(key) + " = " + value);
            return defaultValue;
        }
    }

    public void setScopedLong(SaveKey key, long value) {
        saveVariables.put(resolve(key), String.valueOf(value));
    }

    // ---- 浮点数 ----

    public double getScopedDouble(SaveKey key, double defaultValue) {
        String value = saveVariables.get(resolve(key));
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点存档变量: " + resolve(key) + " = " + value);
            return defaultValue;
        }
    }

    public void setScopedDouble(SaveKey key, double value) {
        saveVariables.put(resolve(key), String.valueOf(value));
    }

    public float getScopedFloat(SaveKey key, float defaultValue) {
        String value = saveVariables.get(resolve(key));
        if (value == null) return defaultValue;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点存档变量: " + resolve(key) + " = " + value);
            return defaultValue;
        }
    }

    public void setScopedFloat(SaveKey key, float value) {
        saveVariables.put(resolve(key), String.valueOf(value));
    }

    // ---- 存在性 / 删除 ----

    public boolean containsScoped(SaveKey key) {
        return saveVariables.containsKey(resolve(key));
    }

    public void removeScoped(SaveKey key) {
        saveVariables.remove(resolve(key));
    }

    // ==================== 导出/导入（用于 SaveManager） ====================
    
    /**
     * 导出所有变量（保存到存档时调用）
     * @return 包含所有变量的 Map
     */
    public Map<String, String> exportAll() {
        return new HashMap<>(saveVariables);
    }
    
    /**
     * 导入变量（加载存档时调用）
     * @param variables 要导入的变量 Map
     */
    public void importAll(Map<String, String> variables) {
        if (variables != null) {
            saveVariables = new HashMap<>(variables);
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
}