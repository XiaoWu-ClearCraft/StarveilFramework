package com.xiaowu.game.starveil.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 资源路径解析规则 —— 最容易写错的地方（把子目录当类型、忘了后缀）。
 *
 * <p>解析只做「字符串 → classpath 路径」的换算，不读文件，所以这里不依赖内容项目。
 */
class StarveilResourceResolverTest {

    @Test
    void texturesLiveUnderAssetsStarveil() {
        assertEquals("/assets/starveil/textures/character/normal/relaxed.png",
                StarveilResourceResolver.resolve("starveil:textures/character/normal/relaxed.png"));
    }

    @Test
    void everyTypeMapsToItsOwnDirectory() {
        assertEquals("/assets/starveil/sounds/music/dream.mp3",
                StarveilResourceResolver.resolve("starveil:sounds/music/dream.mp3"));
        assertEquals("/assets/starveil/fonts/xiaolai-sc-regular.ttf",
                StarveilResourceResolver.resolve("starveil:fonts/xiaolai-sc-regular.ttf"));
        assertEquals("/assets/starveil/data/worlds/gravity-test.json",
                StarveilResourceResolver.resolve("starveil:data/worlds/gravity-test.json"));
        assertEquals("/assets/starveil/lang/zh_cn.json",
                StarveilResourceResolver.resolve("starveil:lang/zh_cn.json"));
    }

    @Test
    void aSubdirectoryIsNotAType() {
        // 最常见的写法错误：想引用 assets/starveil/textures/character/… 却写成 starveil:character/…
        // 现在不会硬失败，而是按相对路径兜一次（框架测试里没有真实资源，所以这里兜不到）
        assertNull(StarveilResourceResolver.resolve("starveil:character/normal/relaxed.png"),
                "「character」不是资源类型，兜底也找不到时就返回 null（日志里会说明正确写法）");
    }

    @Test
    void legacyTypeNamesStillWork() {
        assertEquals("/assets/starveil/textures/icons/app-icon.png",
                StarveilResourceResolver.resolve("starveil:images/icons/app-icon.png"));
        assertEquals("/assets/starveil/sounds/music/dream.mp3",
                StarveilResourceResolver.resolve("starveil:audio/music/dream.mp3"));
        assertEquals("/assets/starveil/fonts/a.ttf",
                StarveilResourceResolver.resolve("starveil:font/a.ttf"));
    }

    @Test
    void typeOnlyReferencesFallBackToTheDefaultFile() {
        assertEquals("/assets/starveil/textures/textures.png",
                StarveilResourceResolver.resolve("starveil:textures"));
        assertEquals("/assets/starveil/sounds/sounds.mp3",
                StarveilResourceResolver.resolve("starveil:sounds"));
        // data / lang 没有默认文件名
        assertNull(StarveilResourceResolver.resolve("starveil:data"));
    }

    @Test
    void legacyClasspathPathsAreKept() {
        assertEquals("/assets/starveil/textures/a.png",
                StarveilResourceResolver.resolve("assets/starveil/textures/a.png"));
        assertEquals("/assets/starveil/textures/a.png",
                StarveilResourceResolver.resolve("/assets/starveil/textures/a.png"));
        assertTrue(StarveilResourceResolver.isNamespaceReference("starveil:textures/a.png"));
        assertFalse(StarveilResourceResolver.isNamespaceReference("/assets/starveil/textures/a.png"));
    }

    @Test
    void emptyReferencesResolveToNothing() {
        assertNull(StarveilResourceResolver.resolve(null));
        assertNull(StarveilResourceResolver.resolve(""));
    }

    // ==================== 类型由调用方给（内容只写相对路径） ====================

    @Test
    void relativePathsAreResolvedUnderTheGivenType() {
        assertEquals("/assets/starveil/textures/character/normal/relaxed.png",
                StarveilResourceResolver.resolveTexture("character/normal/relaxed.png"));
        assertEquals("/assets/starveil/sounds/music/dream.mp3",
                StarveilResourceResolver.resolveSound("music/dream.mp3"));
        assertEquals("/assets/starveil/fonts/xiaolai-sc-regular.ttf",
                StarveilResourceResolver.resolveFont("xiaolai-sc-regular.ttf"));
        assertEquals("/assets/starveil/data/worlds/gravity-test.json",
                StarveilResourceResolver.resolveData("worlds/gravity-test.json"));
    }

    @Test
    void explicitReferencesAlwaysWinOverTheType() {
        // 命名空间 / 绝对 / 旧式 assets 路径都原样解析
        assertEquals("/assets/starveil/textures/a.png",
                StarveilResourceResolver.resolveTexture("starveil:textures/a.png"));
        assertEquals("/assets/starveil/textures/a.png",
                StarveilResourceResolver.resolveTexture("/assets/starveil/textures/a.png"));
        assertEquals("/assets/starveil/textures/a.png",
                StarveilResourceResolver.resolveTexture("assets/starveil/textures/a.png"));
        // 真要跨类型时，显式写就行（贴图入口也能拿到音频路径）
        assertEquals("/assets/starveil/sounds/x.mp3",
                StarveilResourceResolver.resolveTexture("starveil:sounds/x.mp3"));
    }

    @Test
    void aMissingExtensionStillYieldsTheRuleBasedPath() {
        // 框架测试里没有真实资源文件，所以这里钉的是「算出来的路径长什么样」：
        // 补后缀只在该文件确实存在时才采用（见 resolveAs 的说明），
        // 找不到时返回没后缀的那条，报错信息里能对照。
        assertEquals("/assets/starveil/textures/character/normal/relaxed",
                StarveilResourceResolver.resolveTexture("character/normal/relaxed"));
        assertFalse(StarveilResourceResolver.exists("character/normal/relaxed"));
    }

    @Test
    void typeAwareResolutionOfMissingFilesIsStillStable() {
        // 相对路径不会因为「类型已知」就退化成老的 /xxx 解析
        assertEquals("/assets/starveil/textures/nope/missing.png",
                StarveilResourceResolver.resolveTexture("nope/missing.png"));
    }

    // ==================== 类型未知时的推断 ====================

    @Test
    void relativePathsAreSearchedAcrossTypeDirectories() {
        // 框架测试里没有真实资源，所以这里钉的是边界：显式引用一律不走推断。
        // 「真的找到文件」那条路由内容项目自己的资源覆盖（查找顺序见 resolveRelative 的说明）。
        assertNull(StarveilResourceResolver.resolveRelative("starveil:textures/a.png"),
                "显式命名空间不走推断");
        assertNull(StarveilResourceResolver.resolveRelative("/assets/starveil/textures/a.png"),
                "绝对路径不走推断");
        assertNull(StarveilResourceResolver.resolveRelative("assets/starveil/textures/a.png"),
                "旧式 assets 路径不走推断");
        assertNull(StarveilResourceResolver.resolveRelative(null));
        assertNull(StarveilResourceResolver.resolveRelative(""));
    }

    @Test
    void aRelativePathThatExistsNowhereYieldsNothing() {
        // 找不到就返回 null，ResourceResolver 会退回原来的归一化处理 ——
        // 所以新增推断不会改变「路径不存在」时的老行为
        assertNull(StarveilResourceResolver.resolveRelative("there/is/no/such/file.png"));
        assertFalse(StarveilResourceResolver.exists("there/is/no/such/file.png"));
    }
}
