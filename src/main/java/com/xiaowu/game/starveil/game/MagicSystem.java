package com.xiaowu.game.starveil.game;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.ui.core.GameUI;

/**
 * 法阵（魔力）系统总开关。
 *
 * <p>由 DataManager 特殊键 {@value GameConstants#MAGIC_CIRCLE_ENABLED_KEY} 控制，
 * 默认 false（关闭）。关闭时：
 * <ul>
 *   <li>法阵（Q 键）不可用</li>
 *   <li>GameUI 不显示魔力条</li>
 *   <li>任何读取魔力值的代码返回 0，消耗/增加/恢复均无效</li>
 * </ul>
 */
public final class MagicSystem {

    private MagicSystem() {
    }

    /**
     * 法阵（魔力）系统是否启用。
     */
    public static boolean isEnabled() {
        return DataManager.getBoolean(GameConstants.MAGIC_CIRCLE_ENABLED_KEY, false);
    }

    /**
     * 设置法阵（魔力）系统开关，并即时刷新 UI。
     */
    public static void setEnabled(boolean enabled) {
        DataManager.setBoolean(GameConstants.MAGIC_CIRCLE_ENABLED_KEY, enabled);
        GameUI.getInstance().refreshMagicVisibility();
    }
}
