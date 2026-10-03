package com.xiaowu.game.starveil.game.story;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 章节总导演测试。
 *
 * <p>重点是<b>命名规范的强制校验</b>：章节类必须严格是
 * {@code com.xiaowu.game.starveil.content.chapter{N}.Chapter{N}}。
 * 旧实现靠字符串猜类名、猜不到就静默跳过 —— 结果是过章节死活不触发却毫无线索。
 * 这里把「不合规就报错」钉死。
 */
class ChapterDirectorTest {

    // ==================== 命名规范 ====================

    @Test
    void acceptsTheCanonicalNaming() {
        assertTrue(ChapterDirector.isValidChapterClassName(
                "com.xiaowu.game.starveil.content.chapter1.Chapter1"));
        assertTrue(ChapterDirector.isValidChapterClassName(
                "com.xiaowu.game.starveil.content.chapter12.Chapter12"));
        assertTrue(ChapterDirector.isValidChapterClassName(
                "com.xiaowu.game.starveil.content.chapterPrologue.ChapterPrologue"));
    }

    @Test
    void rejectsNamesOutsideTheRequiredShape() {
        // 包不对
        assertFalse(ChapterDirector.isValidChapterClassName(
                "com.xiaowu.game.starveil.content.Chapter1"));
        assertFalse(ChapterDirector.isValidChapterClassName(
                "other.package.chapter1.Chapter1"));
        // 类名前缀不对
        assertFalse(ChapterDirector.isValidChapterClassName(
                "com.xiaowu.game.starveil.content.chapter1.Script1"));
        // 包名段与类名不匹配结构（少一层）
        assertFalse(ChapterDirector.isValidChapterClassName(
                "com.xiaowu.game.starveil.content.chapter1"));
        // 空/ null
        assertFalse(ChapterDirector.isValidChapterClassName(null));
        assertFalse(ChapterDirector.isValidChapterClassName(""));
        // 试图用内部类 / 多一层点号绕过
        assertFalse(ChapterDirector.isValidChapterClassName(
                "com.xiaowu.game.starveil.content.chapter1.Chapter1$Inner"));
    }

    @Test
    void classNameForFollowsTheRule() {
        assertEquals("com.xiaowu.game.starveil.content.chapter1.Chapter1",
                ChapterDirector.classNameFor(1));
        assertEquals("com.xiaowu.game.starveil.content.chapter7.Chapter7",
                ChapterDirector.classNameFor(7));
        // 生成出来的名字本身必须通过校验
        for (int n = 1; n <= 20; n++) {
            assertTrue(ChapterDirector.isValidChapterClassName(ChapterDirector.classNameFor(n)),
                    "第 " + n + " 章生成的名字不合规: " + ChapterDirector.classNameFor(n));
        }
    }

    // ==================== 解析 ====================

    /*
     * 注意：这里只测「解析规则」本身。
     *
     * 「第 1 章确实是那个类」「第 2 章声明 VISUAL_NOVEL」这类断言依赖真实章节，
     * 而章节属于游戏内容（已拆到 ../StarveilContent），因此那些测试也搬了过去
     * —— 否则框架单独构建时会因为「找不到 content.chapter1.Chapter1」而假红。
     */

    @Test
    void resolveRejectsMissingChapterWithClearError() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ChapterDirector.resolve(99));
        // 报错信息里必须带上期望的类名，否则排查时毫无线索
        assertTrue(ex.getMessage().contains("chapter99.Chapter99"),
                "错误信息应包含期望的类名，实际: " + ex.getMessage());
    }

    @Test
    void resolveRejectsIllegalChapterNumber() {
        assertThrows(IllegalArgumentException.class, () -> ChapterDirector.resolve(0));
        assertThrows(IllegalArgumentException.class, () -> ChapterDirector.resolve(-3));
    }

    // ==================== 模式与推进 ====================

    @Test
    void unknownChapterFallsBackToNormalInsteadOfBlowingUp() {
        // 章节没写好也不该连游戏都进不去
        assertEquals(ChapterMode.NORMAL, ChapterDirector.getInstance().resolveMode(99));
    }

    @Test
    void pendingChapterTracksRequests() {
        ChapterDirector d = ChapterDirector.getInstance();
        d.reset();
        assertEquals(ChapterDirector.FIRST_CHAPTER, d.currentChapter());

        d.requestChapter(3);
        // requestChapter 只是「排定」，不会立刻改变当前章节
        assertEquals(ChapterDirector.FIRST_CHAPTER, d.currentChapter());

        d.requestNextChapter();
        assertEquals(ChapterDirector.FIRST_CHAPTER, d.currentChapter());

        d.requestChapter(0);   // 非法值应被忽略而不是排定
        d.reset();
    }

    @Test
    void firstChapterIsOne() {
        assertEquals(1, ChapterDirector.FIRST_CHAPTER);
    }
}
