package com.xiaowu.game.starveil.infrastructure.persistence;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 教程进度状态。
 *
 * <p>教程进度存在哪里取决于 {@code starveil:tutorial_persist}：
 * <ul>
 *   <li><b>默认（false）</b>：每个存档各自记录，开新档重新走教程 —— 所以它先看当前
 *       存档里有没有值。</li>
 *   <li><b>true</b>：全局只记一次，之后所有存档都不再重复教程 —— 直接读写全局配置。</li>
 * </ul>
 *
 * <p>两种模式共用同一个键名，因此在启动时把 {@link #persistMode} 定下来，
 * 之后所有读写都按它分流。这样「玩家中途改了 persist 开关」不会让同一个键
 * 一半在存档里一半在全局里。
 */
public final class TutorialState {
    /**
     * 是否跨存档。启动时从 {@code starveil:tutorial_persist} 读取一次后固定。
     *
     * <p>尚未读取时为 {@code null} —— 此时按默认（不跨存档）处理。
     *
     * <p>注意它<b>不</b>通过 {@code FrameworkDataKeys.TUTORIAL_PERSIST.get()} 读取：
     * 键的默认值为 null 时会被识别为「不该出现的配置」，那样日志会很难看。
     * 这里直接走 {@code DataManager} 的通用读取。
     */
    private static volatile Boolean persistMode;

    private TutorialState() {
    }

    /** 读取一次配置，固定本次运行的模式。应在存档系统就绪后调用。 */
    public static void applyPersistMode() {
        boolean persist = Boolean.TRUE.equals(
                DataManager.rawBoolean(FrameworkDataKeys.TUTORIAL_PERSIST.qualified()));
        if (persistMode == null || persistMode != persist) {
            Logger("INFO", "教程进度保存范围: " + (persist ? "跨存档（全局）" : "随存档"));
        }
        persistMode = persist;
    }

    private static boolean persist() {
        Boolean mode = persistMode;
        if (mode == null) {
            // 还没调用过 applyPersistMode：按默认（随存档）处理，
            // 而不是去读配置 —— 读它会再次进入本类
            return false;
        }
        return mode;
    }

    /** 教程键当前是否应当跨存档（供 {@code DataManager} 决定读取来源）。 */
    static boolean isPersistMode() {
        return persist();
    }

    static boolean isCompleted() {
        if (persist()) {
            return FrameworkDataKeys.TUTORIAL_COMPLETED.get();
        }
        return readFromSave();
    }

    static void setCompleted(boolean completed) {
        if (persist()) {
            FrameworkDataKeys.TUTORIAL_COMPLETED.set(completed);
        } else {
            writeToSave(completed);
        }
    }

    private static boolean readFromSave() {
        SaveDataManager save = SaveDataManager.getInstance();
        String raw = save.getString(FrameworkDataKeys.TUTORIAL_COMPLETED.qualified());
        return "true".equalsIgnoreCase(raw);
    }

    private static void writeToSave(boolean completed) {
        SaveDataManager.getInstance()
                .setString(FrameworkDataKeys.TUTORIAL_COMPLETED.qualified(),
                        String.valueOf(completed));
    }

    /** 仅测试使用。 */
    static void resetForTest() {
        persistMode = null;
    }
}
