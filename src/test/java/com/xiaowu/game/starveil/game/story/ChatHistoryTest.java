package com.xiaowu.game.starveil.game.story;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 聊天记录测试。
 *
 * <p>重点：容量上限（丢最旧而不是丢最新）、三种清理来源互不干扰。
 */
class ChatHistoryTest {

    private ChatHistory history;

    @BeforeEach
    @AfterEach
    void reset() {
        history = ChatHistory.getInstance();
        history.resetForTest();
    }

    // ==================== 记录 ====================

    @Test
    void recordsEntriesInOrder() {
        history.add("甲", "第一句");
        history.add("乙", "第二句");

        List<ChatHistory.Entry> all = history.snapshot();
        assertEquals(2, all.size());
        assertEquals("甲", all.get(0).speaker());
        assertEquals("第一句", all.get(0).text());
        assertEquals("乙", all.get(1).speaker());
        assertEquals("第二句", all.get(1).text());
    }

    @Test
    void ignoresNullAndEmptyText() {
        assertFalse(history.add("甲", null));
        assertFalse(history.add("甲", ""));
        assertTrue(history.isEmpty());
        assertNull(history.last());
    }

    @Test
    void nullSpeakerShowsAsNarration() {
        assertTrue(history.add(null, "旁白内容"));
        assertNull(history.last().speaker());
        assertTrue(history.last().display().contains("旁白"),
                "没有说话人时应显示为旁白，而不是空白");
    }

    @Test
    void idsIncrementAndAreStable() {
        history.add("甲", "a");
        history.add("甲", "b");
        List<ChatHistory.Entry> all = history.snapshot();
        assertEquals(1, all.get(0).id());
        assertEquals(2, all.get(1).id());
    }

    @Test
    void lastReturnsMostRecent() {
        history.add("甲", "旧");
        history.add("甲", "新");
        assertEquals("新", history.last().text());
    }

    // ==================== 容量上限 ====================

    @Test
    void dropsOldestWhenExceedingCapacity() {
        for (int i = 0; i < ChatHistory.MAX_ENTRIES + 50; i++) {
            history.add("甲", "第 " + i + " 条");
        }
        assertEquals(ChatHistory.MAX_ENTRIES, history.size(),
                "超出上限必须裁剪，否则记录会无限吃掉堆内存");

        List<ChatHistory.Entry> all = history.snapshot();
        assertEquals("第 50 条", all.get(0).text(), "应当丢最旧的");
        assertEquals("第 " + (ChatHistory.MAX_ENTRIES + 49) + " 条",
                all.get(all.size() - 1).text(), "最新的必须还在");
    }

    @Test
    void exactlyAtCapacityKeepsEverything() {
        for (int i = 0; i < ChatHistory.MAX_ENTRIES; i++) {
            history.add("甲", "x" + i);
        }
        assertEquals(ChatHistory.MAX_ENTRIES, history.size());
    }

    // ==================== 清理 ====================

    @Test
    void manualClearEmptiesEverything() {
        history.add("甲", "a");
        history.add("甲", "b");
        history.clear();

        assertTrue(history.isEmpty());
        assertEquals(0, history.size());
        assertNull(history.last());
    }

    @Test
    void chapterChangeClearsByDefault() {
        history.add("甲", "a");
        history.onChapterChanged(false);
        assertTrue(history.isEmpty(), "默认应当随章节切换清空");
    }

    @Test
    void chapterChangeKeepsWhenConfigured() {
        history.add("甲", "a");
        history.onChapterChanged(true);
        assertEquals(1, history.size(),
                "特殊键声明保留时不能清掉（starveil:chat_history_keep_chapters）");
    }

    @Test
    void keepFlagDoesNotAccumulateBeyondCapacity() {
        for (int i = 0; i < ChatHistory.MAX_ENTRIES + 10; i++) {
            history.add("甲", "x" + i);
            history.onChapterChanged(true);
        }
        assertEquals(ChatHistory.MAX_ENTRIES, history.size(),
                "「跨章节保留」也不能突破上限");
    }

    @Test
    void clearAfterAddingStartsFreshIds() {
        history.add("甲", "a");
        history.clear();
        history.add("甲", "b");
        // id 单调递增即可，不要求清空后重排
        assertNotNull(history.last());
        assertEquals("b", history.last().text());
    }

    // ==================== 展示 ====================

    @Test
    void displayIncludesIdSpeakerAndText() {
        history.add("霁雾", "你好");
        String line = history.last().display();
        assertTrue(line.contains("0001"), "应带序号: " + line);
        assertTrue(line.contains("霁雾"));
        assertTrue(line.contains("你好"));
    }

    @Test
    void linesMatchesSnapshotCount() {
        history.add("甲", "a");
        history.add("乙", "b");
        assertEquals(history.size(), history.lines().size());
    }

    @Test
    void readKeepAcrossChaptersDefaultsToFalse() {
        // DataManager 未初始化时应按「不保留」处理：
        // 宁可多清一次，也不要因为读不到配置而让记录无限堆积
        assertFalse(ChatHistory.readKeepAcrossChapters());
    }
}
