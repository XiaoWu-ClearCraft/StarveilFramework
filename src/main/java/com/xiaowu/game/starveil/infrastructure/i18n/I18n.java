package com.xiaowu.game.starveil.infrastructure.i18n;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 国际化文案表。
 *
 * <p>配置文件放在 {@code assets/starveil/lang/<语言代码>.json}，
 * 通过资源命名空间引用即 {@code starveil:lang/<语言代码>.json}。
 *
 * <p>文件内容支持两种写法，可混用：
 * <pre>
 * {
 *   "menu.start": "开始游戏",
 *   "menu": {
 *     "quit": "退出",
 *     "settings": { "volume": "音量" }
 *   }
 * }
 * </pre>
 * 嵌套对象会被展平成点号键（{@code menu.quit}、{@code menu.settings.volume}）。
 *
 * <p><b>找不到键时返回键名本身</b>，而不是空串 —— 界面上直接显示
 * {@code menu.start} 能一眼看出漏翻，返回空串则只会看到一个空白按钮。
 */
public final class I18n {

    /** 语言文件路径模板。 */
    public static final String RESOURCE_TEMPLATE = "starveil:lang/%s.json";

    /** 默认（兜底）语言。 */
    public static final String DEFAULT_LANGUAGE = "zh_cn";

    private static I18n instance;

    private final Map<String, String> translations = new LinkedHashMap<>();
    private final Map<String, String> fallback = new LinkedHashMap<>();
    private String currentLanguage = DEFAULT_LANGUAGE;
    private boolean fallbackLoaded = false;

    private I18n() {
    }

    public static synchronized I18n getInstance() {
        if (instance == null) {
            instance = new I18n();
        }
        return instance;
    }

    // ==================== 加载 ====================

    /**
     * 加载并切换到指定语言。
     *
     * @return 是否成功；失败时保留原有文案表不动，避免界面突然全变键名
     */
    public synchronized boolean load(String languageCode) {
        String code = normalizeCode(languageCode);
        if (code == null) {
            Logger("WARNING", "语言代码为空，保持当前语言: " + currentLanguage);
            return false;
        }

        Map<String, String> loaded = readLanguageFile(code);
        if (loaded == null) {
            Logger("WARNING", "语言文件不存在或无法解析: " + resourcePath(code)
                    + "，保持当前语言: " + currentLanguage);
            return false;
        }

        translations.clear();
        translations.putAll(loaded);
        currentLanguage = code;
        Logger("INFO", "已加载语言 " + code + "，共 " + translations.size() + " 条文案");
        return true;
    }

    // ==================== 注入 / 替换 ====================

    /**
     * 内容 / 插件为某个语言代码指定的替代资源路径。
     *
     * <p>框架自带 {@code lang/<code>.json}，这里是「允许被替换」的入口：
     * 内容想让 {@code zh_cn} 用自己的那份文案，指过来即可。
     */
    private static final Map<String, String> LANGUAGE_RESOURCES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 运行时注入的文案，按语言代码分组。优先级<b>高于</b>文件内容。
     *
     * <p>适合「少量覆盖」的场景（改几个措辞、修一个翻译错误），
     * 不必为此复制整份语言文件。
     */
    private static final Map<String, Map<String, String>> INJECTED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 为某个语言代码指定替代资源路径。 */
    public static void registerLanguageResource(String languageCode, String resourcePath) {
        String code = normalizeCode(languageCode);
        if (code == null || resourcePath == null || resourcePath.trim().isEmpty()) {
            Logger("WARNING", "语言资源登记参数无效: " + languageCode + " → " + resourcePath);
            return;
        }
        LANGUAGE_RESOURCES.put(code, resourcePath.trim());
        Logger("INFO", "语言 " + code + " 的资源已指向: " + resourcePath.trim());
    }

    /** 注入或覆盖单条文案（重新 load 该语言后生效）。 */
    public static void inject(String languageCode, String key, String value) {
        String code = normalizeCode(languageCode);
        if (code == null || key == null || key.isEmpty() || value == null) {
            return;
        }
        INJECTED.computeIfAbsent(code, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(key, value);
    }

    /** 批量注入。 */
    public static void injectAll(String languageCode, Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        entries.forEach((k, v) -> inject(languageCode, k, v));
    }

    /** 已注入的文案（只读快照）。 */
    public static Map<String, String> injected(String languageCode) {
        String code = normalizeCode(languageCode);
        Map<String, String> m = code == null ? null : INJECTED.get(code);
        return m == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(m));
    }

    /** 读取并展平一个语言文件，并叠加注入的文案。 */
    static Map<String, String> readLanguageFile(String code) {
        Map<String, String> result = new LinkedHashMap<>();

        // 内容可以把这个语言指向自己的文件；没指定就用框架自带的那份
        String path = LANGUAGE_RESOURCES.getOrDefault(code, resourcePath(code));
        try (InputStream is = ResourceResolver.getResourceAsStream(path)) {
            if (is != null) {
                JsonElement root = JsonParser.parseReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8));
                if (root.isJsonObject()) {
                    result.putAll(flatten(root.getAsJsonObject()));
                } else {
                    Logger("ERROR", "语言文件根节点必须是对象: " + path);
                }
            } else {
                Logger("WARNING", "语言文件不存在: " + path);
            }
        } catch (Exception e) {
            Logger("ERROR", "读取语言文件失败 " + path + ": " + e.getMessage());
        }

        // 注入的文案覆盖文件内容
        Map<String, String> injected = INJECTED.get(code);
        if (injected != null) {
            result.putAll(injected);
        }

        // 文件和注入都没有 → 视为该语言不可用
        return result.isEmpty() ? null : result;
    }

    public static String resourcePath(String languageCode) {
        return String.format(RESOURCE_TEMPLATE, normalizeCode(languageCode));
    }

    // ==================== 查询 ====================

    /**
     * 取文案。
     *
     * <p>查找顺序：当前语言 → 默认语言 → 键名本身。
     */
    public synchronized String get(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        String value = translations.get(key);
        if (value != null) {
            return value;
        }
        ensureFallbackLoaded();
        value = fallback.get(key);
        if (value != null) {
            return value;
        }
        return key;
    }

    /**
     * 取文案并做占位符替换，占位符形如 {@code {0}}、{@code {1}}。
     *
     * <pre>
     *   "greeting": "你好，{0}！"
     *   get("greeting", "小明")  →  "你好，小明！"
     * </pre>
     */
    public String get(String key, Object... args) {
        return format(get(key), args);
    }

    /** 取文案，找不到时用给定默认值。 */
    public synchronized String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return value.equals(key) ? defaultValue : value;
    }

    public synchronized boolean has(String key) {
        if (translations.containsKey(key)) {
            return true;
        }
        ensureFallbackLoaded();
        return fallback.containsKey(key);
    }

    public synchronized String language() {
        return currentLanguage;
    }

    public synchronized Set<String> keys() {
        return Collections.unmodifiableSet(translations.keySet());
    }

    /** 当前语言文件里的条目数（测试与调试用）。 */
    public synchronized int size() {
        return translations.size();
    }

    /** 清空（仅测试用）。 */
    synchronized void resetForTest() {
        translations.clear();
        fallback.clear();
        fallbackLoaded = false;
        currentLanguage = DEFAULT_LANGUAGE;
        INJECTED.clear();
        LANGUAGE_RESOURCES.clear();
    }

    private void ensureFallbackLoaded() {
        if (fallbackLoaded) {
            return;
        }
        fallbackLoaded = true;
        if (DEFAULT_LANGUAGE.equals(currentLanguage)) {
            return;
        }
        Map<String, String> loaded = readLanguageFile(DEFAULT_LANGUAGE);
        if (loaded != null) {
            fallback.putAll(loaded);
            Logger("INFO", "已加载兜底语言 " + DEFAULT_LANGUAGE + "，共 " + loaded.size() + " 条");
        }
    }

    // ==================== 纯函数（可单测） ====================

    /**
     * 把嵌套 JSON 展平成点号键。
     *
     * <p>非字符串的叶子值会被跳过（语言文件里出现数字/数组通常是写错了），
     * 而不是转成字符串混进文案表。
     */
    public static Map<String, String> flatten(JsonObject root) {
        Map<String, String> out = new LinkedHashMap<>();
        flattenInto(root, "", out);
        return out;
    }

    private static void flattenInto(JsonObject object, String prefix, Map<String, String> out) {
        if (object == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value.isJsonObject()) {
                flattenInto(value.getAsJsonObject(), key, out);
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                out.put(key, value.getAsString());
            } else if (value.isJsonPrimitive()) {
                out.put(key, value.getAsString());
            }
        }
    }

    /**
     * 占位符替换。{@code {0}}、{@code {1}} … 按序号取参数。
     *
     * <p>参数不足以替换的占位符保持原样 —— 让漏传参数在界面上看得见，
     * 而不是变成一个空洞。
     */
    public static String format(String template, Object... args) {
        if (template == null || args == null || args.length == 0) {
            return template;
        }
        String result = template;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(args[i]));
            }
        }
        return result;
    }

    /** 语言代码规范化：去空白 + 小写 + 连字符转下划线。 */
    public static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String c = code.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return c.isEmpty() ? null : c;
    }
}
