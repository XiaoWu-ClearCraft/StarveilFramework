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
     * 上一帧「跳跃键（W）是否按着」。只用于识别<b>按下的那一瞬间</b>：
     * 没有它就只能用 {@code isKeyPressed}，按住不放会连跳。
     *
     * <p>公开是因为置位的是输入侧（{@code PlayerControlSystem}）、消费的是
     * 重力侧（{@code GravitySystem}），两边不是同一个类。
     */
    public boolean jumpKeyHeldLastFrame = false;

    /** 上一帧「向下键（S）是否按着」，同样用于识别按下的那一瞬间。 */
    public boolean downKeyHeldLastFrame = false;

    // ==================== 手感：土狼时间 / 跳跃缓冲 ====================

    /**
     * <b>土狼时间</b>：离开地面之后还能起跳的宽容时间（秒）。
     *
     * <p>名字来自「走到悬崖边 coyote 还能在空中多跑两步」的卡通桥段。
     * 它解决的是操作问题：玩家在台子边缘按住方向键按跳跃时，
     * 这一帧很可能已经走出边缘（{@code grounded == false}），
     * 严格判定就会「明明按了却没跳」—— 玩家会认为游戏在吞键。
     * 给 0.1 秒的宽限，这期间仍然算「刚离开地面」。
     */
    public double coyoteTime = 0.10;

    /**
     * <b>跳跃缓冲</b>：落地之前按下的跳跃键最多记住多久（秒）。
     *
     * <p>和土狼时间是一对：土狼时间宽容「晚按」，缓冲宽容「早按」。
     * 玩家在下落快落地时提前按跳，落地那一帧就立刻起跳，
     * 不必卡在「刚落地那一瞬间」去按。0.12 秒约等于 7 帧。
     */
    public double jumpBuffer = 0.12;

    /** 距离「上一次还踩在地面上」过了多久（秒）。初始为无穷大 = 从没落地过。 */
    public double timeSinceGrounded = Double.POSITIVE_INFINITY;

    /** 距离「上一次按下跳跃键」过了多久（秒）。初始为无穷大 = 还没按过。 */
    public double timeSinceJumpPress = Double.POSITIVE_INFINITY;

    // ==================== 单向平台：向下穿越 ====================

    /** 按下「下」穿下单向平台后，忽略单向平台的剩余时间（秒）。 */
    public double dropThroughTimer = 0;

    /** 一次穿越要忽略单向平台多久 —— 约 0.2 秒足够让实体从台面里穿出去。 */
    public double dropThroughTime = 0.20;

    public Gravity() {
    }

    public Gravity(double acceleration, double maxFallSpeed) {
        this.acceleration = acceleration;
        this.maxFallSpeed = maxFallSpeed;
    }

    /** 请求起跳（越过输入缓冲，直接起跳；仍然受土狼时间约束）。 */
    public void requestJump() {
        jumpQueued = true;
    }

    /** 现在能不能起跳：踩在地上，或者刚离开地面还在土狼时间内。 */
    public boolean canJump() {
        return grounded || timeSinceGrounded <= coyoteTime;
    }

    /** 是否处于「向下穿单向平台」的窗口内。 */
    public boolean isDroppingThrough() {
        return dropThroughTimer > 0;
    }

    /**
     * 起跳成功后的收尾：把缓冲与土狼窗口一起作废。
     *
     * <p>必须成对清掉，否则起跳后的头几帧 {@code timeSinceGrounded} 还很小、
     * 缓冲里又还有一次按下的记录，同一帧的判定会再放行一次 —— 那就是二段跳。
     */
    public void consumeJumpWindow() {
        timeSinceJumpPress = Double.POSITIVE_INFINITY;
        timeSinceGrounded = Double.POSITIVE_INFINITY;
    }

    /** 记下一次跳跃键按下（由输入侧调用）。 */
    public void pressJumpKey() {
        timeSinceJumpPress = 0;
    }

    /** 开始向下穿越单向平台。 */
    public void startDropThrough() {
        dropThroughTimer = dropThroughTime;
    }

    /**
     * 每帧推进计时器 —— 必须在判定跳跃<b>之前</b>调用。
     */
    public void tickTimers(double deltaTime) {
        if (grounded) {
            timeSinceGrounded = 0;
        } else {
            // 与 Double.POSITIVE_INFINITY 相加仍是无穷大，不会溢出成 NaN
            timeSinceGrounded += deltaTime;
        }
        timeSinceJumpPress += deltaTime;
        if (dropThroughTimer > 0) {
            dropThroughTimer = Math.max(0, dropThroughTimer - deltaTime);
        }
    }

    /** 重新开始下落（例如被击飞后）。 */
    public void resetFall() {
        speed = 0;
        grounded = false;
        jumpQueued = false;
        timeSinceGrounded = Double.POSITIVE_INFINITY;
        dropThroughTimer = 0;
    }
}
