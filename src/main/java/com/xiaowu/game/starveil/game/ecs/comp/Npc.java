package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 组件 —— 承载 AI 配置与寻路/巡逻/引导过程所需的全部可变状态。
 * 规则逻辑位于 NPC 系统中。
 */
public final class Npc implements Component {
    public enum Algorithm { NONE, FOLLOW, HOSTILE, GUIDE }

    /** 引导步骤。 */
    public static class GuideStep {
        public double x, y;
        public double speed = 2.0; // 每帧像素；-1 = 传送
        public boolean waitForPlayer = false;
        public com.xiaowu.game.starveil.game.world.WorldMap.MapEvent event;
        public double eventTriggerDistance = 100;
        public boolean eventTriggered = false;
        public boolean eventCompleted = false;
    }

    public final String id;

    public Algorithm algorithm = Algorithm.NONE;
    public double speed = 5.0;

    // 视野/攻击
    public double visionRadius = 200;
    public double originalVisionRadius = 200;
    public double currentVisionRadius = 200;
    public double attackRadius = 40;
    public boolean playerSeen = false;
    public long lastSeenTime = 0;
    public long visionPersistMs = 3000;

    // 跟随寻路目标（避免绕障碍振荡）
    public boolean hasTarget = false;
    public double targetX = 0;
    public double targetY = 0;
    public long targetLockUntil = 0;
    public static final long TARGET_LOCK_MS = 300;

    // 最近移动向量（连续性偏好）
    public double lastMoveX = 0;
    public double lastMoveY = 0;

    public double stopRadius = 300;

    // 巡逻
    public boolean patrolEnabled = false;
    public double patrolTargetX = 0;
    public double patrolTargetY = 0;
    public int patrolCooldown = 0;

    // 引导
    public final List<GuideStep> guideSteps = new ArrayList<>();
    public int guideIndex = 0;

    public boolean isOverlapped = false;

    // 阻挡记忆，避免短时间内重复尝试同一条路径
    public double lastBlockedX = Double.NaN;
    public double lastBlockedY = Double.NaN;
    public long lastBlockedTime = 0;
    public static final long BLOCKED_MEMORY_MS = 500;

    // 攻击
    public double attackDamage = 10.0;
    public long lastAttackTime = 0;
    public long attackCooldownMs = 1000;

    public Npc(String id) {
        this.id = id;
    }

    public boolean isOverlappedWithOthers(double x, double y, double width, double height,
                                          com.xiaowu.game.starveil.game.ecs.World world,
                                          int selfEntity) {
        for (int e : world.view(Transform.class)) {
            if (e == selfEntity) {
                continue;
            }
            if (!world.has(e, Npc.class) && !world.has(e, PlayerController.class)) {
                continue;
            }
            Transform t = world.get(e, Transform.class);
            if (t != null && overlaps(x, y, width, height, t.x, t.y, t.width, t.height)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlaps(double ax, double ay, double aw, double ah,
                                    double bx, double by, double bw, double bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
}
