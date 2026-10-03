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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class DataManager {
    private static final String DATA_FILE_NAME = GameConstants.CONFIG_FILE_NAME;
    private static final Gson gson = new GsonBuilder().create();

    /**
     * 特殊键集合由 {@link SpecialKeys} 统一管理（内置键带 {@code starveil:} 前缀，
     * 第三方注册必须带自己的命名空间前缀）。这里不再单独维护一份，避免两处漂移。
     */

    private static Map<String, String> dataMap;
    private static File dataFile;

    public static void initialize() {
        try {
            ensureDataDirectory();
            dataFile = new File(GameConstants.DATA_DIR, DATA_FILE_NAME);
            if (!dataFile.exists()) {
                dataMap = new HashMap<>();
                saveData();
                Logger("DEBUG", "创建新的配置文件: " + dataFile.getAbsolutePath());
            } else {
                loadData();
            }
        } catch (Exception e) {
            dataMap = new HashMap<>();
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

    /**
     * 获取指定键的原始值。
     * 对于特殊键，若当前正在运行的游戏的SaveDataManager中的值与其不同，
     * 则优先返回SaveDataManager中的值；否则返回全局配置中的值。
     */
    private static String getRawValue(String key) {
        // 历史遗留的无前缀键（如 "CantExit"）先迁移到带前缀的当前键
        String k = SpecialKeys.migrate(key);
        // 临时键优先级最高：它是本次运行的权威状态，且刻意不落盘。
        // 即便配置文件没加载成功（dataMap == null）也必须能读到。
        if (k != null && TEMPORARY.containsKey(k)) {
            return TEMPORARY.get(k);
        }
        // 内存覆盖（调试窗口设的）次之：调试时手动设的值应当立刻可见
        if (k != null && MEMORY_OVERRIDES.containsKey(k)) {
            return MEMORY_OVERRIDES.get(k);
        }
        // 尚未 initialize() 时（例如单元测试、极早期调用）按「没有配置」处理，
        // 而不是抛 NPE —— 剧情文本格式化等工具会在这里取玩家名。
        if (dataMap == null) {
            return null;
        }
        if (SpecialKeys.isSpecial(k)) {
            SaveDataManager saveDataManager = SaveDataManager.getInstance();
            String configValue = dataMap.get(k);
            String saveValue = saveDataManager.getString(k);
            if (saveValue != null && !saveValue.equals(configValue)) {
                return saveValue;
            }
        }
        return dataMap.get(k);
    }

    /**
     * 惯性是否启用。默认<b>开启</b>。
     *
     * <p>开启后玩家移动带加减速（松开方向键会滑行一小段），
     * 疾跑减速到静止时播放急停动画；关闭则恢复瞬时启停。
     */
    public static boolean isInertiaEnabled() {
        return getBoolean(GameConstants.INERTIA_KEY, true);
    }

    /**
     * 是否禁止退出。
     *
     * <p>由剧情通过特殊键 {@code starveil:cant_exit} 控制，用来<b>强化代入感</b>：
     * 一段不容打断的独白、一次无法回头的抉择，退出按钮的存在本身就会削弱张力。
     * 设为 true 时主菜单与 ESC 暂停菜单都会移除退出入口；设回 false 或移除该键即恢复。
     *
     * <p>默认<b>不禁止</b>。
     */
    public static boolean isExitBlocked() {
        return getBoolean(GameConstants.CANT_EXIT_KEY, false);
    }

    // ==================== 教程进度 ====================

    /**
     * 教程是否已完成。
     *
     * <p>默认记在<b>当前存档</b>里（每个存档各自记录，开新档重新走教程）。
     * 把特殊键 {@code starveil:tutorial_persist} 设为 true 则改为跨存档：
     * 它会被当作普通特殊键走 DataManager，全局只记一次。
     */
    public static boolean isTutorialCompleted() {
        if (getBoolean(GameConstants.TUTORIAL_PERSIST_KEY, false)) {
            return getBoolean(GameConstants.TUTORIAL_COMPLETED_KEY, false);
        }
        return SaveDataManager.getInstance()
                .getBoolean(GameConstants.TUTORIAL_COMPLETED_KEY, false);
    }

    public static void setTutorialCompleted(boolean completed) {
        if (getBoolean(GameConstants.TUTORIAL_PERSIST_KEY, false)) {
            setBoolean(GameConstants.TUTORIAL_COMPLETED_KEY, completed);
        } else {
            SaveDataManager.getInstance()
                    .setBoolean(GameConstants.TUTORIAL_COMPLETED_KEY, completed);
        }
    }

    // ==================== 键名前缀强制 ====================

    /**
     * 所有 DataManager 键都必须带命名空间前缀（内置 {@code starveil:}，
     * 第三方用自己的命名空间）。
     *
     * <p>裸名无法判断归属，也无法与第三方键区分 —— 强制前缀是让「键的归属」
     * 一眼可辨的唯一办法。不带前缀的键会被直接拒绝。
     */
    private static boolean hasPrefix(String key) {
        if (key == null) {
            return false;
        }
        int i = key.indexOf(SpecialKeys.SEPARATOR);
        return i > 0 && i < key.length() - 1;
    }

    private static boolean rejectUnprefixed(String key, String action) {
        if (hasPrefix(key)) {
            return false;
        }
        Logger("ERROR", "数据键缺少命名空间前缀，已" + action + ": '" + key
                + "'（内置键用 starveil: 前缀，第三方用自己的命名空间）");
        return true;
    }

    public static String getString(String key) {
        return getRawValue(key);
    }

    public static String getString(String key, String defaultValue) {
        String value = getRawValue(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 向后兼容的get方法（等同于getString）
     */
    public static String get(String key) {
        return getString(key);
    }

    /**
     * 向后兼容的get方法（等同于getString）
     */
    public static String get(String key, String defaultValue) {
        return getString(key, defaultValue);
    }

    /**
     * 获取布尔值配置
     */
    public static boolean getBoolean(String key) {
        String value = getRawValue(key);
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取布尔值配置（带默认值）
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = getRawValue(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 获取整数配置
     */
    public static int getInt(String key) {
        String value = getRawValue(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析整数配置: " + key + " = " + value);
            return 0;
        }
    }

    /**
     * 获取整数配置（带默认值）
     */
    public static int getInt(String key, int defaultValue) {
        String value = getRawValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析整数配置: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取长整数配置
     */
    public static long getLong(String key) {
        String value = getRawValue(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析长整数配置: " + key + " = " + value);
            return 0L;
        }
    }

    /**
     * 获取长整数配置（带默认值）
     */
    public static long getLong(String key, long defaultValue) {
        String value = getRawValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析长整数配置: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取浮点数配置
     */
    public static double getDouble(String key) {
        String value = getRawValue(key);
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数配置: " + key + " = " + value);
            return 0.0;
        }
    }

    /**
     * 获取浮点数配置（带默认值）
     */
    public static double getDouble(String key, double defaultValue) {
        String value = getRawValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数配置: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    /**
     * 获取浮点数配置（带默认值）
     */
    public static float getFloat(String key) {
        String value = getRawValue(key);
        if (value == null) {
            return 0.0f;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数配置: " + key + " = " + value);
            return 0.0f;
        }
    }

    /**
     * 获取浮点数配置（带默认值）
     */
    public static float getFloat(String key, float defaultValue) {
        String value = getRawValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Logger("WARNING", "无法解析浮点数配置: " + key + " = " + value + "，使用默认值: " + defaultValue);
            return defaultValue;
        }
    }

    public static void set(String key, String value) {
        putAndSave(key, value);
    }

    /**
     * 设置布尔值配置
     */
    public static void setBoolean(String key, boolean value) {
        putAndSave(key, String.valueOf(value));
    }

    /**
     * 设置整数配置
     */
    public static void setInt(String key, int value) {
        putAndSave(key, String.valueOf(value));
    }

    /**
     * 设置长整数配置
     */
    public static void setLong(String key, long value) {
        putAndSave(key, String.valueOf(value));
    }

    /**
     * 设置浮点数配置
     */
    public static void setDouble(String key, double value) {
        putAndSave(key, String.valueOf(value));
    }

    /**
     * 设置浮点数配置
     */
    public static void setFloat(String key, float value) {
        putAndSave(key, String.valueOf(value));
    }

    public static void remove(String key) {
        MEMORY_OVERRIDES.remove(key);
        dataMap.remove(key);
        saveData();
    }

    // ==================== 内存覆盖（调试用） ====================

    /**
     * 只改内存、<b>不写入文件</b>的覆盖层。
     *
     * <p>用途：调参时临时试一个值，不该把玩家或开发者的配置文件改脏。
     * 进程结束即失效。
     *
     * <p><b>覆盖会被代码写入顶掉</b>：只要代码显式调用 {@code set*} 写同一个键，
     * 覆盖立即失效、以新写入的值为准。这是刻意的 ——
     * 「代码设定的值」永远比「调试时手动改的值」权威，
     * 否则一个忘了清掉的调试覆盖会悄悄压住游戏逻辑真正想写的值，
     * 排查起来毫无头绪。
     */
    private static final Map<String, String> MEMORY_OVERRIDES = new HashMap<>();

    /** 所有写入的统一入口：先清掉同键的内存覆盖，再落盘。 */
    private static void putAndSave(String key, String value) {
        MEMORY_OVERRIDES.remove(key);
        dataMap.put(key, value);
        saveData();
    }

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

    // ==================== 临时键（仅本次运行有效） ====================

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

    /** 设置一个临时键（不落盘）。 */
    public static void setTemporary(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        String k = SpecialKeys.migrate(key.trim());
        if (value == null) {
            TEMPORARY.remove(k);
            return;
        }
        TEMPORARY.put(k, value);
        Logger("DEBUG", "已设置临时键（不写入文件）: " + k + " = " + value);
    }

    public static void setTemporaryBoolean(String key, boolean value) {
        setTemporary(key, String.valueOf(value));
    }

    public static void setTemporaryInt(String key, int value) {
        setTemporary(key, String.valueOf(value));
    }

    public static boolean isTemporary(String key) {
        return key != null && TEMPORARY.containsKey(SpecialKeys.migrate(key.trim()));
    }

    public static void clearTemporary(String key) {
        if (key != null) {
            TEMPORARY.remove(SpecialKeys.migrate(key.trim()));
        }
    }

    /** 当前所有临时键（只读快照）。 */
    public static Map<String, String> getTemporaryKeys() {
        return new HashMap<>(TEMPORARY);
    }


    public static boolean contains(String key) {
        return dataMap.containsKey(key);
    }

    public static Map<String, String> getAll() {
        return new HashMap<>(dataMap);
    }

    public static void clear() {
        dataMap.clear();
        saveData();
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
                dataMap = new HashMap<>();
                Logger("INFO", "配置文件不存在，使用默认配置");
            }
        } catch (Exception e) {
            dataMap = new HashMap<>();
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
}