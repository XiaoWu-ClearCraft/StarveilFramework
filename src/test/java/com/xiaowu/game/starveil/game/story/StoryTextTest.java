package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.config.GameConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 剧情文本占位符测试。
 *
 * <p>章节作者现在可以直接写 {@code "{window.title}"} 或 {@code "{NAME}"}，
 * 不用再手动拼字符串（旧章节里 {@code "{window.title}"} 是原样显示出来的）。
 */
class StoryTextTest {

    @Test
    void nullAndPlainTextPassThrough() {
        assertNull(StoryText.format(null));
        assertEquals("", StoryText.format(""));
        assertEquals("你好呀~", StoryText.format("你好呀~"));
    }

    @Test
    void replacesWindowTitlePlaceholders() {
        assertEquals(GameConstants.gameTitle(), StoryText.format("{window.title}"));
        assertEquals(GameConstants.gameTitle(), StoryText.format("{TITLE}"));
        assertEquals("标题: " + GameConstants.gameTitle(), StoryText.format("标题: {window.title}"));
    }

    @Test
    void replacesNamePlaceholder() {
        // 未初始化 DataManager 时按空字符串处理，不抛异常
        assertEquals("你好，", StoryText.format("你好，{NAME}"));
    }

    @Test
    void leavesRichTextMarkupAlone() {
        // <more> / <green> 由渲染层处理，占位符替换不能碰它们
        assertEquals("<green>中文，日文</green>", StoryText.format("<green>中文，日文</green>"));
        assertEquals("第一段<more>第二段", StoryText.format("第一段<more>第二段"));
    }
}
