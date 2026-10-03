package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Magic;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Stamina;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.item.Inventory;
import com.xiaowu.game.starveil.game.state.GameInstance;
import javafx.scene.image.ImageView;

/**
 * 玩家 API — 从 ECS 世界读取/写入玩家实体的组件。
 *
 * <p>魔力相关转由 {@link StarveilMagic} 统一处理：法阵系统关闭时，
 * 此处所有魔力读取一律返回 0。
 */
public class StarveilPlayer {

    private static final StarveilPlayer INSTANCE = new StarveilPlayer();

    private StarveilPlayer() {
    }

    public static StarveilPlayer getInstance() {
        return INSTANCE;
    }

    /** 玩家实体是否存在（已进入游戏）。 */
    public boolean exists() {
        return GameInstance.getPlayerEntityId() >= 0;
    }

    /** 当前生命值 */
    public double getHealth() {
        Health h = GameInstance.getPlayerHealthComp();
        return h != null ? h.current : 0;
    }

    /** 最大生命值 */
    public double getMaxHealth() {
        Health h = GameInstance.getPlayerHealthComp();
        return h != null ? h.max : 0;
    }

    /** 生命值百分比 0.0~1.0 */
    public double getHealthPercentage() {
        Health h = GameInstance.getPlayerHealthComp();
        return h != null ? h.percentage() : 0;
    }

    /** 治疗 */
    public void heal(double amount) {
        Health h = GameInstance.getPlayerHealthComp();
        if (h != null) {
            h.current = Math.min(h.max, h.current + amount);
            com.xiaowu.game.starveil.ui.core.GameUI.getInstance().setHealth(h.percentage());
        }
    }

    /** 造成伤害，返回是否死亡 */
    public boolean damage(double amount) {
        Health h = GameInstance.getPlayerHealthComp();
        if (h == null) return false;
        boolean died = h.takeDamage(amount);
        com.xiaowu.game.starveil.ui.core.GameUI.getInstance().triggerHitFlash();
        com.xiaowu.game.starveil.ui.core.GameUI.getInstance().setHealth(h.percentage());
        return died;
    }

    // ==================== 体力 ====================

    /** 当前体力值 */
    public double getStamina() {
        Stamina s = GameInstance.getPlayerStaminaComp();
        return s != null ? s.current : 0;
    }

    /** 最大体力值 */
    public double getMaxStamina() {
        Stamina s = GameInstance.getPlayerStaminaComp();
        return s != null ? s.max : 0;
    }

    /** 恢复体力 */
    public void restoreStamina() {
        Stamina s = GameInstance.getPlayerStaminaComp();
        if (s != null) s.reset();
    }

    // ==================== 魔力（受法阵开关影响） ====================

    /** 当前魔力值（法阵关闭时返回 0） */
    public double getMagic() {
        return StarveilMagic.getInstance().getCurrentMagic();
    }

    /** 最大魔力值（法阵关闭时返回 0） */
    public double getMaxMagic() {
        return StarveilMagic.getInstance().getMaxMagic();
    }

    /** 魔力百分比（法阵关闭时返回 0） */
    public double getMagicPercentage() {
        return StarveilMagic.getInstance().getMagicPercentage();
    }

    // ==================== 位置 ====================

    public double getX() {
        Transform t = GameInstance.getPlayerTransformComp();
        return t != null ? t.x : 0;
    }

    public double getY() {
        Transform t = GameInstance.getPlayerTransformComp();
        return t != null ? t.y : 0;
    }

    public void setPosition(double x, double y) {
        Transform t = GameInstance.getPlayerTransformComp();
        if (t != null) {
            t.x = x;
            t.y = y;
        }
    }

    public double getCenterX() {
        Transform t = GameInstance.getPlayerTransformComp();
        return t != null ? t.centerX() : 0;
    }

    public double getCenterY() {
        Transform t = GameInstance.getPlayerTransformComp();
        return t != null ? t.centerY() : 0;
    }

    /** 朝向 */
    public Facing getFacing() {
        Sprite spr = GameInstance.getPlayerSpriteComp();
        return spr != null ? spr.facing : Facing.DOWN;
    }

    /** 玩家视图节点 */
    public ImageView getView() {
        Sprite spr = GameInstance.getPlayerSpriteComp();
        return spr != null ? spr.view : null;
    }

    // ==================== 背包 ====================

    /**
     * 获取玩家背包（手上物品 + 背包槽位）。
     */
    public Inventory getInventory() {
        GameInstance gi = GameInstance.getCurrentInstance();
        return gi != null ? gi.getInventory() : null;
    }

    /**
     * 使用一个物品（执行 onUse 事件）。
     */
    public boolean useItem(String itemId) {
        GameInstance gi = GameInstance.getCurrentInstance();
        return gi != null && gi.useItem(itemId);
    }
}
