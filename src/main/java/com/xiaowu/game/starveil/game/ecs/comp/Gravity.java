package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

/**
 * 重力组件 —— 让实体沿<b>重力方向</b>自动下落。
 *
 * <p>重力方向是全局的（由地图或代码设定），存在 ECS 世界上；
 * 本组件只保存「沿该方向的速度」。这样方向变了不需要重建组件。
 *
 * <p>角度约定：{@code 0° = 向下（+y）}，顺时针为正 ——
 * 方向向量为 {@code (sin θ, cos θ)}，因此 90° = 向右、180° = 向上、270° = 向左。
 */
public final class Gravity implements Component {
    /** 关掉即可让该实体不受重力影响（例如漂浮物）。 */
    public boolean enabled = true;

    /** 沿重力方向的当前速度（像素/秒）。 */
    public double speed = 0;

    /** 是否已经落地（本帧被挡住）。 */
    public boolean grounded = false;

    /** 加速度（像素/秒²）。 */
    public double acceleration = 1800;

    /** 速度上限（像素/秒），避免一帧位移直接跳过整片墙体。 */
    public double maxFallSpeed = 1200;

    // ==================== 跳跃 ====================

    /**
     * 起跳初速度（像素/秒），方向与重力相反。
     *
     * <p>默认值配合默认加速度算出的跳跃高度约
     * {@code v² / (2a) = 700² / 3600 ≈ 136px} —— 略高于一个玩家身位的高度，
     * 够上平台但不至于一跳飞天。想改手感就改它。
     */
    public double jumpSpeed = 700;

    /**
     * 下一次更新时是否施加起跳冲量。
     *
     * <p>由输入系统置位、由重力系统消费并清零 —— 这样「按一下跳一次」
     * 与「按住不放」区分得开，而且两个系统之间不需要共享额外状态。
     */
    public boolean jumpQueued = false;

    /**
     * 上一帧「跳跃键是否按着」。只用于识别<b>按下的那一瞬间</b>：
     * 没有它就只能用 {@code isKeyPressed}，按住不放会连跳。
     *
     * <p>公开是因为置位的是输入侧（{@code PlayerControlSystem}）、消费的是
     * 重力侧（{@code GravitySystem}），两边不是同一个类。
     */
    public boolean jumpKeyHeldLastFrame = false;

    public Gravity() {
    }

    public Gravity(double acceleration, double maxFallSpeed) {
        this.acceleration = acceleration;
        this.maxFallSpeed = maxFallSpeed;
    }

    /** 请求起跳（只有落地时才会真的生效，见 GravitySystem）。 */
    public void requestJump() {
        jumpQueued = true;
    }

    /** 重新开始下落（例如被击飞后）。 */
    public void resetFall() {
        speed = 0;
        grounded = false;
        jumpQueued = false;
    }
}
