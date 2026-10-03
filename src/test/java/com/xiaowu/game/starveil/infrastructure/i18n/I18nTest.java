package com.xiaowu.game.starveil.infrastructure.i18n;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** i18n 纯逻辑测试（展平、占位符、语言代码规范化）。 */
class I18nTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    // ==================== 展平 ====================

    @Test
    void flattensNestedObjects() {
        Map<String, String> m = I18n.flatten(json("""
                {
                  "menu": {
                    "start": "开始游戏",
                    "settings": { "volume": "音量" }
                  }
                }
                """));
        assertEquals("开始游戏", m.get("menu.start"));
        assertEquals("音量", m.get("menu.settings.volume"));
        assertEquals(2, m.size());
    }

    @Test
    void keepsAlreadyFlatKeys() {
        Map<String, String> m = I18n.flatten(json("""
                { "menu.start": "开始游戏", "menu.quit": "退出" }
                """));
        assertEquals("开始游戏", m.get("menu.start"));
        assertEquals("退出", m.get("menu.quit"));
    }

    @Test
    void mixesFlatAndNested() {
        Map<String, String> m = I18n.flatten(json("""
                {
                  "a": "1",
                  "b": { "c": "2" }
                }
                """));
        assertEquals("1", m.get("a"));
        assertEquals("2", m.get("b.c"));
    }

    @Test
    void emptyObjectYieldsEmptyMap() {
        assertTrue(I18n.flatten(json("{}")).isEmpty());
        assertTrue(I18n.flatten(null).isEmpty());
    }

    @Test
    void nonStringLeavesStillResolve() {
        Map<String, String> m = I18n.flatten(json("""
                { "count": 3, "flag": true }
                """));
        assertEquals("3", m.get("count"));
        assertEquals("true", m.get("flag"));
    }

    // ==================== 占位符 ====================

    @Test
    void replacesPositionalPlaceholders() {
        assertEquals("你好，小明！", I18n.format("你好，{0}！", "小明"));
        assertEquals("3 个苹果给了 小明",
                I18n.format("{0} 个苹果给了 {1}", 3, "小明"));
    }

    @Test
    void keepsPlaceholderWhenArgumentIsMissing() {
        // 漏传参数时保留占位符，界面上能一眼看出问题，而不是变成空洞
        assertEquals("你好，{1}！", I18n.format("你好，{1}！", "小明"));
    }

    @Test
    void formatWithoutArgsReturnsTemplate() {
        assertEquals("原样", I18n.format("原样"));
        assertEquals("原样", I18n.format("原样", new Object[0]));
        assertNull(I18n.format(null, "x"));
    }

    @Test
    void repeatedPlaceholderIsReplacedEverywhere() {
        assertEquals("A-A-A", I18n.format("{0}-{0}-{0}", "A"));
    }

    // ==================== 语言代码 ====================

    @Test
    void normalizesLanguageCode() {
        assertEquals("zh_cn", I18n.normalizeCode("zh-CN"));
        assertEquals("zh_cn", I18n.normalizeCode("  ZH_cn  "));
        assertEquals("us", I18n.normalizeCode("US"));
        assertNull(I18n.normalizeCode(null));
        assertNull(I18n.normalizeCode("   "));
    }

    @Test
    void resourcePathFollowsAssetsLayout() {
        assertEquals("starveil:lang/zh_cn.json", I18n.resourcePath("zh-CN"));
        assertEquals("starveil:lang/us.json", I18n.resourcePath("us"));
    }

    // ==================== 真实加载（会暴露资源解析器的类型支持问题） ====================

    /**
     * 回归：{@code starveil:lang/...} 必须被资源解析器认识。
     *
     * <p>早先 {@code StarveilResourceResolver} 只支持 textures / fonts / sounds / data
     * 四类，{@code lang} 直接解析失败，语言文件<b>永远读不到</b> —— i18n 静默失效。
     * 而当时这里只测了 flatten / format 这些纯函数，完全没碰真正的加载路径，
     * 所以一直没被发现。
     */
    @Test
    void languageResourceIsResolvable() {
        String path = I18n.resourcePath(I18n.DEFAULT_LANGUAGE);
        assertNotNull(com.xiaowu.game.starveil.infrastructure.ResourceResolver.getResource(path),
                "资源解析器不认识 " + path + " —— 这正是 i18n 静默失效的原因");
    }

    @Test
    void loadsARealLanguageFile() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        assertTrue(i18n.load(I18n.DEFAULT_LANGUAGE),
                "语言文件必须能真正读到，而不是「加载失败但没人发现」");
        assertTrue(i18n.size() > 0, "加载成功却一条文案都没有，说明解析出来的内容是空的");
    }

    @Test
    void loadedTranslationsAreActuallyLookedUp() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();
        i18n.load(I18n.DEFAULT_LANGUAGE);

        assertEquals("Starveil", i18n.get("framework.name"), "zh_cn.json 里确实有这个键");
        assertTrue(i18n.has("framework.plugin.loading"));
    }

    @Test
    void loadingANonexistentLanguageFailsCleanly() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        assertFalse(i18n.load("no_such_language"),
                "不存在的语言应当返回 false，而不是抛异常");
    }

    // ==================== 缺键行为 ====================

    @Test
    void missingKeyReturnsKeyItself() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();
        assertEquals("some.missing.key", i18n.get("some.missing.key"),
                "找不到必须返回键名本身：返回空串会让界面变成空白按钮，看不出是漏翻");
        assertFalse(i18n.has("some.missing.key"));
    }

    @Test
    void getOrDefaultFallsBackForMissingKey() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();
        assertEquals("兜底", i18n.getOrDefault("nope", "兜底"));
    }

    @Test
    void emptyKeyReturnsEmptyString() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();
        assertEquals("", i18n.get(""));
        assertEquals("", i18n.get(null));
    }

    // ==================== 注入 / 替换 ====================

    /**
     * 框架自带 {@code lang/zh_cn.json}，内容可以通过注入<b>覆盖</b>其中的文案，
     * 而不必复制整份语言文件。
     */
    @Test
    void injectedTextOverridesFileContent() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        i18n.load(I18n.DEFAULT_LANGUAGE);
        assertEquals("Starveil", i18n.get("framework.name"));

        I18n.inject(I18n.DEFAULT_LANGUAGE, "framework.name", "被内容改过的名字");
        i18n.load(I18n.DEFAULT_LANGUAGE);

        assertEquals("被内容改过的名字", i18n.get("framework.name"),
                "注入的文案必须覆盖文件里的同名键");
    }

    @Test
    void injectionCanAddBrandNewKeys() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        I18n.injectAll(I18n.DEFAULT_LANGUAGE, java.util.Map.of(
                "game.title", "雾隐星阑",
                "game.subtitle", "第二章"));

        assertTrue(i18n.load(I18n.DEFAULT_LANGUAGE),
                "注入与文件内容应当合并");
        assertEquals("雾隐星阑", i18n.get("game.title"));
        assertEquals("第二章", i18n.get("game.subtitle"));
    }

    @Test
    void injectionAloneIsEnoughWithoutALanguageFile() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        I18n.inject("xx_yy", "only.key", "仅注入");

        assertTrue(i18n.load("xx_yy"),
                "内容可以只靠注入提供一门语言，不需要额外放语言文件");
        assertEquals("仅注入", i18n.get("only.key"));
    }

    @Test
    void languageResourceCanBeReplaced() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        I18n.registerLanguageResource(I18n.DEFAULT_LANGUAGE, "starveil:lang/zh_cn.json");
        assertTrue(i18n.load(I18n.DEFAULT_LANGUAGE));
        assertTrue(i18n.size() > 0);
    }

    @Test
    void injectedSnapshotIsACopy() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        I18n.inject("zh_cn", "k", "v");
        assertEquals("v", I18n.injected("zh_cn").get("k"));

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> I18n.injected("zh_cn").put("a", "b"));
    }

    @Test
    void invalidInjectionArgumentsAreIgnored() {
        I18n i18n = I18n.getInstance();
        i18n.resetForTest();

        I18n.inject(null, "k", "v");
        I18n.inject("zh_cn", null, "v");
        I18n.inject("zh_cn", "k", null);
        I18n.injectAll("zh_cn", null);

        assertTrue(I18n.injected("zh_cn").isEmpty());
    }
}
