package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 玩家控制器组件 —— 标识玩家实体，并保存移动参数与外观 id。
 */
public final class PlayerController implements Component {
    public double moveSpeed = 6;
    public double accelerationFactor = 2.0;
    public String selectedCharacter = "default";

    // ==================== 惯性 ====================
    // 惯性开启时（DataManager 的 starveil:inertia，默认开），位置不再直接
    // 由输入决定，而是由这里的速度积分得到：按下时加速、松开时滑行减速。

    /** 当前水平速度（像素/秒）。 */
    public double velocityX = 0;
    /** 当前竖直速度（像素/秒）。 */
    public double velocityY = 0;
    /** 加速阶段的逼近速率（像素/秒²）。 */
    public double accelRate = 1600;
    /** 松开方向键后的减速速率（像素/秒²）。 */
    public double decelRate = 1100;

    /**
     * 急停触发瞬间的速度，用来决定「减速到多少算停稳」。
     * 用比例而非绝对值判断，避免依赖速度单位约定。
     */
    public double skidReferenceSpeed = 0;

    /** 当前速度大小。 */
    public double speed() {
        return Math.hypot(velocityX, velocityY);
    }

    /** 立刻停下（传送、切图、被打断时用）。 */
    public void stopImmediately() {
        velocityX = 0;
        velocityY = 0;
    }

    public PlayerController() {
    }
}
