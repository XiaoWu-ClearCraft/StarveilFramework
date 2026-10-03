package com.xiaowu.game.starveil.infrastructure.i18n;

import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 界面语言的读写入口。
 *
 * <p>把「语言偏好存在哪、怎么生效」收在一处，避免启动流程与设置界面各自实现一遍。
 *
 * <p><b>可选语言由内容提供</b>（{@link ContentConfig#addLanguage}）。框架不规定
 * 一款游戏支持哪些语言：内容不提供时 {@link ContentConfig#hasLanguages()} 为 false，
 * 设置界面就不显示语言切换，这个类也只会把界面固定在 {@link I18n#DEFAULT_LANGUAGE}。
 *
 * <p>语言代码一律规范化（小写、连字符转下划线），所以 {@code ZH-CN} 与 {@code zh_cn}
 * 是同一个语言。
 */
public final class LanguageSettings {

    private LanguageSettings() {
    }

    // ==================== 读取 ====================

    /**
     * 当前应当使用的语言代码。
     *
     * <p>优先用存下来的偏好；偏好为空、或内容已经不提供该语言时，
     * 回落到内容提供的第一个语言，再回落到 {@link I18n#DEFAULT_LANGUAGE}。
     */
    public static String current() {
        String stored = normalizeStored();
        if (stored != null && isAvailable(stored)) {
            return stored;
        }
        if (stored != null) {
            // 存的语言已经不可用了（内容删了那份语言文件）—— 明确说一声而不是静默换语言
            Logger("WARNING", "已保存的语言 '" + stored + "' 不在内容提供的语言列表里，改用默认语言");
        }
        String first = firstAvailable();
        return first != null ? first : I18n.DEFAULT_LANGUAGE;
    }

    /** 内容是否提供了可选语言 —— 为 false 时设置界面不显示语言切换。 */
    public static boolean isSwitchable() {
        return ContentConfig.hasLanguages();
    }

    /**
     * 设置界面下拉框里要显示的语言。
     *
     * @return 语言代码 → 显示名，保持内容登记的顺序
     */
    public static Map<String, String> options() {
        return ContentConfig.availableLanguages();
    }

    /** 某个语言代码是否由内容提供。 */
    public static boolean isAvailable(String languageCode) {
        String code = I18n.normalizeCode(languageCode);
        return code != null && ContentConfig.availableLanguages().containsKey(code);
    }

    /** 某个语言代码在设置界面里的显示名；内容没登记时返回代码本身。 */
    public static String displayName(String languageCode) {
        String code = I18n.normalizeCode(languageCode);
        if (code == null) {
            return "";
        }
        String name = ContentConfig.availableLanguages().get(code);
        return name != null ? name : code;
    }

    /** 语言代码 → 显示名（{@link #options()} 的反向视图，便于按显示名找回代码）。 */
    public static Map<String, String> displayNameToCode() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : options().entrySet()) {
            // 显示名重复时保留先登记的，避免后登记的把它顶掉
            out.putIfAbsent(e.getValue(), e.getKey());
        }
        return out;
    }

    /** 按显示名找语言代码（设置界面下拉框里显示的是名字）。 */
    public static String codeOfDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        return displayNameToCode().get(displayName);
    }

    // ==================== 应用 ====================

    /**
     * 启动时应用已保存的语言偏好。
     *
     * <p>必须在内容初始化（即 {@code ContentConfig.addLanguage} 已经登记完）之后调用。
     *
     * @return 实际生效的语言代码
     */
    public static String applyStored() {
        String code = current();
        I18n.getInstance().load(code);
        if (isSwitchable()) {
            Logger("INFO", "界面语言: " + describe(code) + "，可选: "
                    + ContentConfig.availableLanguages().values());
        } else {
            Logger("INFO", "界面语言: " + describe(code)
                    + "（内容未提供语言列表，设置界面不显示语言切换）");
        }
        return code;
    }

    /**
     * 切换语言：校验 → 记入偏好 → 立即生效。
     *
     * @param languageCode 语言代码
     * @return 是否切换成功；失败时语言与偏好都保持不变
     */
    public static boolean apply(String languageCode) {
        String code = I18n.normalizeCode(languageCode);
        if (code == null) {
            Logger("WARNING", "语言代码为空，保持当前语言: " + I18n.getInstance().language());
            return false;
        }
        if (!isAvailable(code)) {
            Logger("WARNING", "内容未提供语言 '" + code + "'，可选: "
                    + options().keySet() + "，保持当前语言");
            return false;
        }
        // 先加载成功再记偏好：加载失败（语言文件缺失/损坏）时不该把界面锁在一个打不开的语言上
        if (!I18n.getInstance().load(code)) {
            Logger("WARNING", "语言 '" + code + "' 加载失败，偏好未改变");
            return false;
        }
        FrameworkDataKeys.LANGUAGE.set(code);
        Logger("INFO", "界面语言已切换为: " + code + "（" + displayName(code) + "）");
        return true;
    }

    // ==================== 内部 ====================

    /** 存下来的偏好；从未设置过时返回 null（而不是默认值，以便与「显式设成默认」区分）。 */
    private static String normalizeStored() {
        if (!FrameworkDataKeys.LANGUAGE.isSet()) {
            return null;
        }
        String raw = FrameworkDataKeys.LANGUAGE.get();
        return I18n.normalizeCode(raw);
    }

    private static String firstAvailable() {
        for (String code : options().keySet()) {
            return code;
        }
        return null;
    }

    /** 语言代码的显示名，找不到时退回代码。用于日志与设置界面。 */
    public static String describe(String languageCode) {
        String code = I18n.normalizeCode(languageCode);
        if (code == null) {
            return "";
        }
        String name = displayName(code);
        return name.equals(code) ? code : name + " (" + code + ")";
    }

    /** 规范化语言代码（转发，保持调用点不必同时 import I18n）。 */
    public static String normalize(String languageCode) {
        return I18n.normalizeCode(languageCode);
    }

    /** 大小写不敏感的语言代码比较。 */
    public static boolean same(String a, String b) {
        String x = I18n.normalizeCode(a);
        String y = I18n.normalizeCode(b);
        return x != null && x.equals(y);
    }
}
