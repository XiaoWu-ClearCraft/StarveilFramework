package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;

/**
 * 剧情文本占位符替换。
 *
 * <p>章节作者可以直接写占位符，不用到处手动拼字符串：
 * <ul>
 *   <li>{@code {NAME}} —— 玩家当前名字</li>
 *   <li>{@code {TITLE}} / {@code {window.title}} —— 游戏标题</li>
 * </ul>
 *
 * <p>与 {@code ChatManager} 的富文本标记（{@code <more>}、{@code <green>} 等）互不冲突，
 * 那些标记由渲染层处理。
 */
public final class StoryText {

    private StoryText() {}

    public static String format(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        if (result.indexOf('{') < 0) {
            return result;
        }
        String playerName = com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys
                .PLAYER_NAME.get();
        result = result.replace("{NAME}", playerName == null ? "" : playerName);
        result = result.replace("{TITLE}", GameConstants.GAME_TITLE);
        result = result.replace("{window.title}", GameConstants.GAME_TITLE);
        return result;
    }
}
