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

    public Gravity() {
    }

    public Gravity(double acceleration, double maxFallSpeed) {
        this.acceleration = acceleration;
        this.maxFallSpeed = maxFallSpeed;
    }

    /** 重新开始下落（例如被击飞后）。 */
    public void resetFall() {
        speed = 0;
        grounded = false;
    }
}
