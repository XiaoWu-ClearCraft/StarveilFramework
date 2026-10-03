package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 聊天记录（内存环形缓冲）。
 *
 * <p>只保留最近 {@value #MAX_ENTRIES} 条 —— 调试/回顾用途不值得无限增长，
 * 而一条对话记录里可能带着整段剧情文本，放任增长会实打实地吃掉堆内存。
 *
 * <p>清理有三个来源：
 * <ol>
 *   <li><b>手动</b> —— 调试窗口上的「清空」按钮；</li>
 *   <li><b>章节切换自动清理</b> —— 默认清理；把特殊键
 *       {@code starveil:chat_history_keep_chapters} 设为 true 可保留；</li>
 *   <li><b>章节代码自行清理</b> —— 任意时刻调用 {@link #clear()}。</li>
 * </ol>
 *
 * <p>本类不依赖 JavaFX / Swing，可以被 UI 与剧情线程同时访问。
 */
public final class ChatHistory {

    /** 内存中最多保留的条数。 */
    public static final int MAX_ENTRIES = 1000;

    /** 一条聊天记录。 */
    public record Entry(int id, long timestamp, String speaker, String text) {

        /** 用于调试窗口展示的一行文本。 */
        public String display() {
            String who = (speaker == null || speaker.isBlank()) ? "旁白" : speaker;
            return "[" + String.format("%04d", id) + "] " + who + "：" + text;
        }
    }

    private static ChatHistory instance;

    private final Deque<Entry> entries = new ArrayDeque<>();
    private int nextId = 1;

    private ChatHistory() {
    }

    public static synchronized ChatHistory getInstance() {
        if (instance == null) {
            instance = new ChatHistory();
        }
        return instance;
    }

    // ==================== 写入 ====================

    /**
     * 追加一条记录。超过上限时丢弃最旧的一条。
     *
     * @return 是否被记录下来（null / 空文本会被忽略）
     */
    public synchronized boolean add(String speaker, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        entries.addLast(new Entry(nextId++, System.currentTimeMillis(), speaker, text));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }
        return true;
    }

    // ==================== 读取 ====================

    /** 按时间顺序（旧 → 新）快照当前记录。 */
    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    /** 只用文本行，便于直接塞进文本框。 */
    public synchronized List<String> lines() {
        List<String> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            out.add(e.display());
        }
        return out;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized boolean isEmpty() {
        return entries.isEmpty();
    }

    /** 最近一条记录；没有则 null。 */
    public synchronized Entry last() {
        return entries.peekLast();
    }

    // ==================== 清理 ====================

    /**
     * 清空记录。任意时刻都可以调用 —— 章节代码想清就清。
     */
    public synchronized void clear() {
        entries.clear();
    }

    /**
     * 章节切换时的处理：默认清空，除非特殊键声明要跨章节保留。
     */
    public synchronized void onChapterChanged() {
        onChapterChanged(readKeepAcrossChapters());
    }

    /** 带显式开关的版本（便于测试，也便于章节代码自行决定）。 */
    public synchronized void onChapterChanged(boolean keepAcrossChapters) {
        if (!keepAcrossChapters) {
            entries.clear();
        }
    }

    /**
     * 是否跨章节保留。
     *
     * <p>读了配置就为 true；DataManager 尚未初始化时按 false（清理）处理 ——
     * 宁可多清一次，也不要因为读不到配置而让记录无限堆积。
     */
    public static boolean readKeepAcrossChapters() {
        return com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys
                .CHAT_HISTORY_KEEP_CHAPTERS.get();
    }

    /** 仅测试使用：重置单例内部状态。 */
    synchronized void resetForTest() {
        entries.clear();
        nextId = 1;
    }
}
