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
        assertNull(StarveilResourceResolver.resolve("starveil:character/normal/relaxed"),
                "「character」不是资源类型，解析应当失败（并在日志里说明原因）");
        assertNull(StarveilResourceResolver.resolve("starveil:character/normal/relaxed.png"));
        assertFalse(StarveilResourceResolver.exists("starveil:character/normal/relaxed.png"));
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
}
