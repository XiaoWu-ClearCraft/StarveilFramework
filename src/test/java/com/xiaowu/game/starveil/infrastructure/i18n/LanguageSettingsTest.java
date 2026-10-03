package com.xiaowu.game.starveil.infrastructure.i18n;

import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 界面语言读写与「可选语言由内容提供」的规则测试。
 *
 * <p>这里刻意<b>不</b>依赖磁盘上的语言文件：语言代码全部借用框架自带的
 * {@code zh_cn}，或者干脆只登记不加载。这样测试只关心规则本身
 * （可选列表、偏好回落、切换是否成功），不关心文案内容。
 */
class LanguageSettingsTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContentConfig.resetForTest();
        DataManager.resetForTest();
        I18n.getInstance().resetForTest();
    }

    // ==================== 内容不提供语言 ====================

    @Test
    void noLanguagesMeansNotSwitchable() {
        assertFalse(LanguageSettings.isSwitchable(),
                "内容一个语言都没登记时不该显示语言切换");
        assertTrue(LanguageSettings.options().isEmpty());
    }

    @Test
    void withoutContentLanguagesCurrentFallsBackToDefault() {
        assertEquals(I18n.DEFAULT_LANGUAGE, LanguageSettings.current());
    }

    @Test
    void switchToUnknownLanguageIsRejected() {
        assertFalse(LanguageSettings.apply("ja_jp"), "内容没提供的语言不该切过去");
        assertEquals(I18n.DEFAULT_LANGUAGE, LanguageSettings.current(),
                "被拒绝后偏好不变");
    }

    // ==================== 内容提供语言 ====================

    @Test
    void languagesRegisteredByContentBecomeOptions() {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        ContentConfig.addLanguage("en_us", "English");

        assertTrue(LanguageSettings.isSwitchable());
        Map<String, String> options = LanguageSettings.options();
        assertEquals(2, options.size());
        assertEquals("简体中文", options.get("zh_cn"));
        assertEquals("English", options.get("en_us"));
    }

    @Test
    void languageCodeIsNormalized() {
        ContentConfig.addLanguage("EN-US", "English");
        assertTrue(LanguageSettings.isAvailable("en_us"), "连字符与大写会被规范化");
        assertTrue(LanguageSettings.isAvailable("en-us"));
        assertTrue(LanguageSettings.isAvailable("EN_US"));
        assertEquals("English", LanguageSettings.displayName("en-us"));
    }

    @Test
    void unknownLanguageFallsBackToFirstRegistered() {
        ContentConfig.addLanguage("en_us", "English");
        ContentConfig.addLanguage("zh_cn", "简体中文");

        // 没存过偏好 → 用内容登记的第一个
        assertEquals("en_us", LanguageSettings.current(),
                "内容登记顺序决定默认语言");
    }

    @Test
    void storedPreferenceWinsOverFirstRegistered() {
        ContentConfig.addLanguage("en_us", "English");
        ContentConfig.addLanguage("zh_cn", "简体中文");
        FrameworkDataKeys.LANGUAGE.set("zh_cn");

        assertEquals("zh_cn", LanguageSettings.current(),
                "玩家选过的语言优先于登记顺序");
    }

    @Test
    void unavailableStoredPreferenceFallsBackAndDoesNotCrash() {
        ContentConfig.addLanguage("en_us", "English");
        // 存的语言内容已经不提供了（例如那份语言文件被删掉）
        FrameworkDataKeys.LANGUAGE.set("ja_jp");

        assertEquals("en_us", LanguageSettings.current());
    }

    // ==================== 切换 ====================

    @Test
    void applyLoadsLanguageAndStoresPreference() {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        FrameworkDataKeys.LANGUAGE.set("en_us");

        assertTrue(LanguageSettings.apply("zh_cn"));
        assertEquals("zh_cn", FrameworkDataKeys.LANGUAGE.get(), "偏好被记下来");
        assertEquals("zh_cn", I18n.getInstance().language(), "语言表也真的加载了");
    }

    @Test
    void applyRejectsLanguageContentDoesNotOffer() {
        ContentConfig.addLanguage("zh_cn", "简体中文");

        assertFalse(LanguageSettings.apply("en_us"));
        assertFalse(FrameworkDataKeys.LANGUAGE.isSet(),
                "被拒绝时不该留下一个打不开的偏好");
    }

    @Test
    void applyRejectsBlankCode() {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        assertFalse(LanguageSettings.apply(null));
        assertFalse(LanguageSettings.apply("   "));
    }

    @Test
    void applyStoredUsesTheStoredPreference() {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        FrameworkDataKeys.LANGUAGE.set("zh_cn");

        assertEquals("zh_cn", LanguageSettings.applyStored());
        assertEquals("zh_cn", I18n.getInstance().language());
    }

    // ==================== 显示名 ====================

    @Test
    void displayNameAndCodeCanBeMappedBothWays() {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        ContentConfig.addLanguage("en_us", "English");

        assertEquals("English", LanguageSettings.displayName("en_us"));
        assertEquals("en_us", LanguageSettings.codeOfDisplayName("English"));
        assertEquals("zh_cn", LanguageSettings.codeOfDisplayName("简体中文"));
    }

    @Test
    void displayNameOfUnregisteredCodeIsTheCodeItself() {
        assertEquals("ja_jp", LanguageSettings.displayName("ja_jp"),
                "没登记时返回代码，便于在日志里看出是哪个语言");
    }

    @Test
    void sameIgnoresCaseAndHyphens() {
        assertTrue(LanguageSettings.same("EN-US", "en_us"));
        assertTrue(LanguageSettings.same("zh_cn", "zh-cn"));
        assertFalse(LanguageSettings.same("zh_cn", "en_us"));
        assertFalse(LanguageSettings.same(null, "zh_cn"));
    }
}
