package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.game.MagicSystem;
import com.xiaowu.game.starveil.game.ecs.comp.Magic;
import com.xiaowu.game.starveil.game.state.GameInstance;

/**
 * 法阵（魔力）系统 API。
 *
 * <p>法阵默认关闭。关闭时：
 * <ul>
 *   <li>魔力和法阵均不显示，Q 键无效</li>
 *   <li>所有魔力读取返回 0，消耗/恢复/增加均无效</li>
 * </ul>
 */
public class StarveilMagic {

    private static final StarveilMagic INSTANCE = new StarveilMagic();

    private StarveilMagic() {
    }

    public static StarveilMagic getInstance() {
        return INSTANCE;
    }

    /**
     * 法阵系统是否启用。
     */
    public boolean isEnabled() {
        return MagicSystem.isEnabled();
    }

    /**
     * 设置法阵系统开关。
     */
    public void setEnabled(boolean enabled) {
        MagicSystem.setEnabled(enabled);
    }

    /**
     * 魔力组件（法阵关闭时所有读取仍返回 0）。
     */
    public Magic getMagicComponent() {
        return GameInstance.getPlayerMagicComp();
    }

    /**
     * 当前魔力值（法阵关闭时返回 0）。
     */
    public double getCurrentMagic() {
        if (!MagicSystem.isEnabled()) return 0;
        Magic m = GameInstance.getPlayerMagicComp();
        return m != null ? m.current : 0;
    }

    /**
     * 最大魔力值（法阵关闭时返回 0）。
     */
    public double getMaxMagic() {
        if (!MagicSystem.isEnabled()) return 0;
        Magic m = GameInstance.getPlayerMagicComp();
        return m != null ? m.max : 0;
    }

    /**
     * 魔力百分比 0.0~1.0（法阵关闭时返回 0）。
     */
    public double getMagicPercentage() {
        if (!MagicSystem.isEnabled()) return 0;
        Magic m = GameInstance.getPlayerMagicComp();
        return m != null ? m.percentage() : 0;
    }

    /**
     * 尝试消耗魔力；法阵关闭或魔力不足时返回 false。
     */
    public boolean consume(double amount) {
        if (!MagicSystem.isEnabled()) return false;
        Magic m = GameInstance.getPlayerMagicComp();
        if (m == null || !m.consume(amount)) {
            return false;
        }
        com.xiaowu.game.starveil.ui.core.GameUI.getInstance()
                .setMagic(m.max > 0 ? m.current / m.max : 0, m.isDepleted);
        return true;
    }
}
