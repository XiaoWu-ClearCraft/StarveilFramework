package com.xiaowu.game.starveil.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容配置测试。
 *
 * <p>核心机制是<b>重定向</b>：setter 除了记录新值，还会登记
 * 「框架默认路径 → 内容指定路径」。{@link ResourceResolver} 读取资源时会查这张表，
 * 于是框架里几十处硬编码路径不需要改动，内容换资源就全局生效。
 */
class ContentConfigTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContentConfig.resetForTest();
    }

    // ==================== 默认值 ====================

    /**
     * 框架<b>不提供</b>任何默认外观资源。
     *
     * <p>字体 / 菜单背景 / 菜单音乐 / 窗口图标全部为 null，含义是「内容没有指定」。
     * 如果这里给了框架自带的默认路径，框架就会去加载一个本来不该由它提供的文件，
     * 无内容启动时就会满屏「找不到资源」。
     */
    @Test
    void frameworkProvidesNoDefaultAssets() {
        assertNull(ContentConfig.bodyFont(), "字体由内容提供");
        assertNull(ContentConfig.titleFont());
        assertNull(ContentConfig.decorFont());
        assertNull(ContentConfig.menuBackground(), "菜单背景由内容提供，缺省纯黑");
        assertNull(ContentConfig.menuMusic(), "菜单音乐由内容提供，缺省静音");
        assertNull(ContentConfig.appIcon(), "窗口图标由内容提供，缺省系统图标");
    }

    @Test
    void defaultRedirectsAreEmpty() {
        assertTrue(ContentConfig.redirects().isEmpty());
        assertEquals("starveil:whatever.png", ContentConfig.redirectOf("starveil:whatever.png"),
                "没有登记重定向时必须原样返回，否则框架默认资源会全部失效");
    }

    // ==================== 字体 ====================

    @Test
    void settingBodyFontRegistersARedirect() {
        ContentConfig.setBodyFont("starveil:fonts/my-body.ttf");

        assertEquals("starveil:fonts/my-body.ttf", ContentConfig.bodyFont());
        assertEquals("starveil:fonts/my-body.ttf",
                ContentConfig.redirectOf(ContentConfig.DEFAULT_BODY_FONT),
                "框架里写死的默认字体路径必须被重定向到新字体");
    }

    @Test
    void settingTitleAndDecorFonts() {
        ContentConfig.setTitleFont("starveil:fonts/my-title.ttf");
        ContentConfig.setDecorFont("starveil:fonts/my-decor.ttf");

        assertEquals("starveil:fonts/my-title.ttf",
                ContentConfig.redirectOf(ContentConfig.DEFAULT_TITLE_FONT));
        assertEquals("starveil:fonts/my-decor.ttf",
                ContentConfig.redirectOf(ContentConfig.DEFAULT_DECOR_FONT));
    }

    @Test
    void blankFontPathKeepsItUnset() {
        ContentConfig.setBodyFont(null);
        ContentConfig.setBodyFont("   ");

        assertNull(ContentConfig.bodyFont(), "空路径应当保持「内容未指定」");
        assertTrue(ContentConfig.redirects().isEmpty(),
                "空路径不应登记重定向，否则会把资源指到空串上");
    }

    // ==================== 菜单 / 图标 / 音乐 ====================

    @Test
    void settingMenuAssets() {
        ContentConfig.setMenuBackground("starveil:textures/backgrounds/my-menu.png");
        ContentConfig.setMenuMusic("starveil:sounds/music/my-theme.mp3");
        ContentConfig.setAppIcon("starveil:textures/icons/my-icon.png");

        assertEquals("starveil:textures/backgrounds/my-menu.png",
                ContentConfig.redirectOf(ContentConfig.DEFAULT_MENU_BACKGROUND));
        assertEquals("starveil:sounds/music/my-theme.mp3",
                ContentConfig.redirectOf(ContentConfig.DEFAULT_MENU_MUSIC));
        assertEquals("starveil:textures/icons/my-icon.png",
                ContentConfig.redirectOf(ContentConfig.DEFAULT_APP_ICON));
    }

    // ==================== 主题色 ====================

    @Test
    void settingThemeColors() {
        ContentConfig.setPrimaryColor("#7FD4FF");
        ContentConfig.setSecondaryColor("#3FA9F5");
        ContentConfig.setTertiaryColor("#BEE9FF");

        assertEquals("#7FD4FF", ContentConfig.primaryColor());
        assertEquals("#3FA9F5", ContentConfig.secondaryColor());
        assertEquals("#BEE9FF", ContentConfig.tertiaryColor());
    }

    @Test
    void colorIsNormalizedToUpperHex() {
        ContentConfig.setPrimaryColor("#7fd4ff");
        assertEquals("#7FD4FF", ContentConfig.primaryColor());
    }

    @Test
    void invalidColorKeepsTheDefault() {
        ContentConfig.setPrimaryColor("red");
        assertEquals(ContentConfig.DEFAULT_PRIMARY_COLOR, ContentConfig.primaryColor(),
                "非 #RRGGBB 应当被拒绝并保留默认，而不是让界面用上非法颜色");

        ContentConfig.setPrimaryColor("#FFF");
        assertEquals(ContentConfig.DEFAULT_PRIMARY_COLOR, ContentConfig.primaryColor());

        ContentConfig.setPrimaryColor(null);
        assertEquals(ContentConfig.DEFAULT_PRIMARY_COLOR, ContentConfig.primaryColor());
    }

    // ==================== 通用重定向 ====================

    @Test
    void genericRedirectWorks() {
        ContentConfig.redirect("starveil:textures/tiles/wood-floor.png",
                "starveil:textures/tiles/marble.png");

        assertEquals("starveil:textures/tiles/marble.png",
                ContentConfig.redirectOf("starveil:textures/tiles/wood-floor.png"));
        assertEquals("starveil:textures/other.png",
                ContentConfig.redirectOf("starveil:textures/other.png"),
                "没登记过的路径不能受影响");
    }

    @Test
    void invalidRedirectArgumentsAreIgnored() {
        ContentConfig.redirect(null, "x");
        ContentConfig.redirect("x", null);
        ContentConfig.redirect("  ", "x");
        ContentConfig.redirect("x", "   ");

        assertTrue(ContentConfig.redirects().isEmpty());
    }

    @Test
    void redirectsSnapshotIsReadOnly() {
        ContentConfig.setBodyFont("starveil:fonts/a.ttf");
        assertFalse(ContentConfig.redirects().isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> ContentConfig.redirects().put("a", "b"));
    }

    // ==================== 与资源解析的联动（机制验证） ====================

    /**
     * 这是整套「零调用点改动」能否成立的关键：
     * 框架里那几十处硬编码路径不会变，变的是 {@link ResourceResolver} 解析出来的实际资源。
     */
    @Test
    void resourceResolverHonoursRedirects() {
        // 框架现在只剩 lang/zh_cn.json 一个资源，拿它当重定向目标。
        // 这样测的正是真实场景：框架里写着一个「内容才提供的」路径。
        assertNotNull(ResourceResolver.getResource("starveil:lang/zh_cn.json"),
                "lang 由框架自带，必须存在");

        String frameworkDefault = ContentConfig.DEFAULT_APP_ICON;
        org.junit.jupiter.api.Assertions.assertNull(
                ResourceResolver.getResource(frameworkDefault),
                "框架不应再自带贴图 —— 它们已经移交给内容项目");

        ContentConfig.redirect(frameworkDefault, "starveil:lang/zh_cn.json");

        java.net.URL after = ResourceResolver.getResource(frameworkDefault);
        assertNotNull(after, "重定向后，框架里写的原路径必须能解析到目标资源");
        assertTrue(after.toString().endsWith("zh_cn.json"));
    }

    @Test
    void settingFontRedirectsFrameworkFontLookups() {
        // 框架自带的字体路径现在解析不到东西（字体归内容项目提供）
        org.junit.jupiter.api.Assertions.assertNull(
                ResourceResolver.getResource(ContentConfig.DEFAULT_BODY_FONT),
                "框架不应再自带字体");

        ContentConfig.setBodyFont("starveil:lang/zh_cn.json");

        java.net.URL after = ResourceResolver.getResource(ContentConfig.DEFAULT_BODY_FONT);
        assertNotNull(after,
                "内容声明字体后，框架里写死的默认字体路径必须解析到内容提供的资源");
        assertTrue(after.toString().endsWith("zh_cn.json"));
    }

    @Test
    void unrelatedResourcesAreUnaffectedByRedirects() {
        ContentConfig.setBodyFont("starveil:lang/zh_cn.json");
        java.net.URL untouched = ResourceResolver.getResource("starveil:lang/zh_cn.json");
        org.junit.jupiter.api.Assertions.assertNotNull(untouched);
        assertTrue(untouched.toString().endsWith("zh_cn.json"));
    }

    @Test
    void colorDefaultsMatchGameConstants() {
        assertEquals(com.xiaowu.game.starveil.config.GameConstants.PINK_COLOR_HEX,
                ContentConfig.DEFAULT_PRIMARY_COLOR);
        assertEquals(com.xiaowu.game.starveil.config.GameConstants.DEEP_PINK_COLOR_HEX,
                ContentConfig.DEFAULT_SECONDARY_COLOR);
        assertEquals(com.xiaowu.game.starveil.config.GameConstants.LIGHT_PINK_COLOR_HEX,
                ContentConfig.DEFAULT_TERTIARY_COLOR);
    }
}
