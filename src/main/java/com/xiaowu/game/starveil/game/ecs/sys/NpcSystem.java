package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.ecs.EcsSystem;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Npc;
import com.xiaowu.game.starveil.game.ecs.comp.PlayerController;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.core.GameUI;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC 行为系统 —— 把原先 NPC 的状态机（NONE/FOLLOW/HOSTILE/GUIDE）、寻路、
 * 重叠分离与对玩家的攻击整体移植到 ECS 系统，按组件读写状态。
 */
public final class NpcSystem implements EcsSystem {

    @Override
    public void update(World world, double deltaTime) {
        WorldMap map = world.getResource(WorldMap.class);
        if (map == null || map.isPaused()) {
            return;
        }
        int[] ps = world.view(PlayerController.class, Transform.class, Health.class, Sprite.class);
        if (ps.length == 0) {
            return;
        }
        int playerEntity = ps[0];
        Transform pt = world.get(playerEntity, Transform.class);
        Health ph = world.get(playerEntity, Health.class);
        Sprite pSpr = world.get(playerEntity, Sprite.class);

        for (int e : world.view(Npc.class, Transform.class, Health.class, Sprite.class)) {
            Health h = world.get(e, Health.class);
            if (h == null || h.dead) {
                continue;
            }
            updateNpc(world, map, e, world.get(e, Npc.class), world.get(e, Transform.class),
                    world.get(e, Sprite.class), playerEntity, pt, ph, pSpr, deltaTime);
        }
    }

    // ==================== 逐 NPC 更新 ====================

    private static void updateNpc(World world, WorldMap map, int selfEntity, Npc npc, Transform t, Sprite spr,
                                  int playerEntity, Transform pt, Health ph, Sprite pSpr, double deltaTime) {
        double startX = t.x;
        double startY = t.y;

        // 重叠分离
        if (isOverlappedWithNpcs(world, selfEntity, t)) {
            resolveOverlaps(world, map, selfEntity, npc, t);
        }

        double px = pt.x;
        double py = pt.y;
        double dx = px - t.x;
        double dy = py - t.y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        double timeScale = deltaTime * 60.0;
        double actualSpeed = npc.speed * timeScale;

        switch (npc.algorithm) {
            case NONE:
                if (npc.patrolEnabled) {
                    if (npc.patrolCooldown <= 0 && (Math.hypot(npc.patrolTargetX - t.x, npc.patrolTargetY - t.y) < 4)) {
                        chooseNewPatrolTarget(npc, t);
                    }
                    if (npc.patrolCooldown > 0) {
                        npc.patrolCooldown--;
                    }
                    moveTowards(npc, t, spr, npc.patrolTargetX, npc.patrolTargetY, world, map, selfEntity, actualSpeed);
                }
                break;

            case FOLLOW: {
                if (dist > npc.stopRadius) {
                    long now = System.currentTimeMillis();
                    boolean directBlocked = false;
                    double sx = t.x + t.width / 2.0;
                    double sy = t.y + t.height / 2.0;
                    double tx = pt.x + pt.width / 2.0;
                    double ty = pt.y + pt.height / 2.0;
                    double ddx = tx - sx;
                    double ddy = ty - sy;
                    double dlen = Math.sqrt(ddx * ddx + ddy * ddy);
                    int steps = Math.max(2, (int) (dlen / 8));
                    for (int i = 1; i <= steps; i++) {
                        double f = (double) i / steps;
                        double ix = sx + ddx * f - t.width / 2.0;
                        double iy = sy + ddy * f - t.height / 2.0;
                        if (map.isBlockedByAirWall(ix, iy, t.width, t.height)) {
                            directBlocked = true;
                            break;
                        }
                    }

                    if (!directBlocked) {
                        npc.hasTarget = true;
                        npc.targetX = pt.x;
                        npc.targetY = pt.y;
                        moveTowards(npc, t, spr, pt.x, pt.y, world, map, selfEntity, actualSpeed);
                    } else {
                        double[] pass = map.findNearestPassage(t.x, t.y, pt.x, pt.y, t.width, t.height);
                        if (pass != null) {
                            if (now < npc.targetLockUntil && npc.hasTarget) {
                                moveTowards(npc, t, spr, npc.targetX, npc.targetY, world, map, selfEntity, actualSpeed);
                                break;
                            }
                            npc.hasTarget = true;
                            npc.targetX = pass[0];
                            npc.targetY = pass[1];
                            npc.targetLockUntil = now + Npc.TARGET_LOCK_MS;
                            moveTowards(npc, t, spr, npc.targetX, npc.targetY, world, map, selfEntity, actualSpeed);
                            if (Math.hypot(npc.targetX - t.x, npc.targetY - t.y) < 6) {
                                npc.hasTarget = false;
                            }
                        } else {
                            npc.hasTarget = false;
                        }
                    }
                }
                break;
            }

            case HOSTILE: {
                boolean los = true;
                double sx = t.x + t.width / 2.0;
                double sy = t.y + t.height / 2.0;
                double tx = pt.x + pt.width / 2.0;
                double ty = pt.y + pt.height / 2.0;
                double ddx = tx - sx;
                double ddy = ty - sy;
                double dlen = Math.sqrt(ddx * ddx + ddy * ddy);
                int steps = Math.max(2, (int) (dlen / 8));
                for (int i = 1; i <= steps; i++) {
                    double f = (double) i / steps;
                    double ix = sx + ddx * f - t.width / 2.0;
                    double iy = sy + ddy * f - t.height / 2.0;
                    if (map.isBlockedByAirWall(ix, iy, t.width, t.height)) {
                        los = false;
                        break;
                    }
                }

                long now = System.currentTimeMillis();
                if (los && dist <= npc.currentVisionRadius) {
                    npc.playerSeen = true;
                    npc.lastSeenTime = now;
                    npc.currentVisionRadius = npc.originalVisionRadius * 1.3;

                    if (dist <= npc.attackRadius) {
                        attack(npc, t, pt, ph);
                    } else {
                        moveTowards(npc, t, spr, pt.x, pt.y, world, map, selfEntity, actualSpeed);
                    }
                } else {
                    if (npc.playerSeen && (now - npc.lastSeenTime) <= npc.visionPersistMs) {
                        if (dist <= npc.attackRadius) {
                            attack(npc, t, pt, ph);
                        } else {
                            moveTowards(npc, t, spr, pt.x, pt.y, world, map, selfEntity, actualSpeed);
                        }
                    } else {
                        npc.playerSeen = false;
                        npc.currentVisionRadius = npc.originalVisionRadius;
                        if (!los) {
                            double[] pass = map.findNearestPassage(t.x, t.y, pt.x, pt.y, t.width, t.height);
                            if (pass != null) {
                                moveTowards(npc, t, spr, pass[0], pass[1], world, map, selfEntity, actualSpeed);
                                break;
                            } else {
                                patrol(npc, t, spr, world, map, selfEntity, actualSpeed);
                            }
                        } else {
                            patrol(npc, t, spr, world, map, selfEntity, actualSpeed);
                        }
                    }
                }
                break;
            }

            case GUIDE: {
                if (!npc.guideSteps.isEmpty()) {
                    if (npc.guideIndex >= npc.guideSteps.size()) {
                        break;
                    }
                    Npc.GuideStep step = npc.guideSteps.get(npc.guideIndex);
                    double gtx = step.x;
                    double gty = step.y;

                    if (step.event != null && !step.eventTriggered) {
                        boolean shouldTrigger = false;
                        if (step.event.on != null) {
                            boolean conditionMet = step.event.checkTriggerCondition(
                                    pt.x, pt.y, pSpr != null ? pSpr.facing : Facing.LEFT,
                                    map.isInteractPressed());
                            if (step.event.on.equals("enter") || step.event.on.equals("left")
                                    || step.event.on.equals("right") || step.event.on.equals("up")
                                    || step.event.on.equals("down")) {
                                if (dist < step.eventTriggerDistance && conditionMet) {
                                    shouldTrigger = true;
                                }
                            } else if (step.event.on.equals("interact")) {
                                if (dist < step.eventTriggerDistance && conditionMet && map.isInteractPressed()) {
                                    shouldTrigger = true;
                                }
                            }
                        } else {
                            if (dist < step.eventTriggerDistance) {
                                shouldTrigger = true;
                            }
                        }
                        if (shouldTrigger) {
                            step.eventTriggered = true;
                            List<WorldMap.MapEventListener> ls = map.getListenersForEvent(step.event.id);
                            if (ls != null) {
                                for (WorldMap.MapEventListener listener : ls) {
                                    listener.onEvent(step.event, step.event.argsAsMap(pt.x, pt.y));
                                }
                            }
                            LoggerManager.Logger("INFO", "NPC " + npc.id + " 触发事件: " + step.event.id);
                        }
                    }

                    if (step.event != null && !step.eventCompleted) {
                        break;
                    }

                    if (step.speed == -1) {
                        double far = Math.max(npc.visionRadius * 2, 200);
                        if (dist > far) {
                            t.x = gtx;
                            t.y = gty;
                            npc.guideIndex++;
                        }
                    } else {
                        if (step.waitForPlayer && dist > Math.max(npc.visionRadius, 200)) {
                            break;
                        }
                        double guideSpeed = step.speed <= 0 ? step.speed : step.speed * timeScale;
                        moveTowards(npc, t, spr, gtx, gty, world, map, selfEntity, guideSpeed);
                        if (Math.hypot(gtx - t.x, gty - t.y) < 4) {
                            npc.guideIndex++;
                        }
                    }
                }
                break;
            }
            default:
                break;
        }

        spr.setMoving(t.x != startX || t.y != startY);
    }

    // ==================== 行为工具 ====================

    private static void patrol(Npc npc, Transform t, Sprite spr, World world, WorldMap map, int selfEntity, double actualSpeed) {
        if (npc.patrolEnabled) {
            if (npc.patrolCooldown <= 0 && (Math.hypot(npc.patrolTargetX - t.x, npc.patrolTargetY - t.y) < 4)) {
                chooseNewPatrolTarget(npc, t);
            }
            if (npc.patrolCooldown > 0) {
                npc.patrolCooldown--;
            }
            moveTowards(npc, t, spr, npc.patrolTargetX, npc.patrolTargetY, world, map, selfEntity, actualSpeed);
        }
    }

    private static void chooseNewPatrolTarget(Npc npc, Transform t) {
        npc.patrolTargetX = t.x + (Math.random() * 400 - 200);
        npc.patrolTargetY = t.y + (Math.random() * 400 - 200);
        npc.patrolCooldown = 60 + (int) (Math.random() * 120);
    }

    private static void attack(Npc npc, Transform t, Transform pt, Health ph) {
        long now = System.currentTimeMillis();
        if (now - npc.lastAttackTime < npc.attackCooldownMs) {
            return;
        }
        double dx = pt.x - t.x;
        double dy = pt.y - t.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= npc.attackRadius) {
            boolean playerDied = ph.takeDamage(npc.attackDamage);
            npc.lastAttackTime = now;
            GameUI.getInstance().triggerHitFlash();
            GameUI.getInstance().setHealth(ph.percentage());
            LoggerManager.Logger("DEBUG", "NPC " + npc.id + " 对玩家造成 " + npc.attackDamage + " 点伤害");
            if (playerDied) {
                LoggerManager.Logger("INFO", "NPC " + npc.id + " 击败了玩家");
            }
        }
    }

    /** 检查是否与其他 NPC 重叠。 */
    private static boolean isOverlappedWithNpcs(World world, int selfEntity, Transform t) {
        for (int e : world.view(Npc.class, Transform.class)) {
            if (e == selfEntity) {
                continue;
            }
            Transform o = world.get(e, Transform.class);
            if (o != null && overlaps(t, o)) {
                return true;
            }
        }
        return false;
    }

    /** 分离重叠的 NPC（互相推开）。 */
    private static void resolveOverlaps(World world, WorldMap map, int selfEntity, Npc npc, Transform t) {
        List<Transform> overlapping = new ArrayList<>();
        for (int e : world.view(Npc.class, Transform.class)) {
            if (e == selfEntity) {
                continue;
            }
            Transform o = world.get(e, Transform.class);
            if (o != null && overlaps(t, o)) {
                overlapping.add(o);
            }
        }

        if (overlapping.isEmpty()) {
            npc.isOverlapped = false;
            return;
        }
        npc.isOverlapped = true;

        double pushX = 0;
        double pushY = 0;
        for (Transform o : overlapping) {
            double ddx = t.centerX() - o.centerX();
            double ddy = t.centerY() - o.centerY();
            double distance = Math.max(0.001, Math.sqrt(ddx * ddx + ddy * ddy));
            pushX += (ddx / distance) * 2.0;
            pushY += (ddy / distance) * 2.0;
        }

        double newX = t.x + pushX;
        double newY = t.y + pushY;
        double[] clampedPos = map.clampEntityPosition(newX, newY, t.width, t.height);
        newX = clampedPos[0];
        newY = clampedPos[1];
        if (!map.isBlockedByAirWall(newX, newY, t.width, t.height)) {
            t.x = newX;
            t.y = newY;
        }
    }

    private static boolean overlaps(Transform a, Transform b) {
        return a.x < b.x + b.width && a.x + a.width > b.x
                && a.y < b.y + b.height && a.y + a.height > b.y;
    }

    /**
     * 寻路移动 —— 移植自 NPC.moveTowards，含轴分离、阻挡记忆与角度采样避障。
     * spr 可空（巡逻等仅移动场景）。
     */
    private static void moveTowards(Npc npc, Transform t, Sprite spr,
                                    double tx, double ty, World world, WorldMap map,
                                    int selfEntity, double segSpeed) {
        double useSpeed = segSpeed <= 0 ? npc.speed : segSpeed;
        double dx = tx - t.x;
        double dy = ty - t.y;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 0.0001) {
            return;
        }

        double step = Math.min(useSpeed, dist);
        double nx = t.x + (dx / dist) * step;
        double ny = t.y + (dy / dist) * step;

        double tryX = nx;
        double tryY = ny;

        boolean directBlocked = map.isBlockedByAirWall(tryX, t.y, t.width, t.height)
                || map.isBlockedByAirWall(tryX, tryY, t.width, t.height);
        boolean currentlyOverlapping = isOverlappedWithNpcs(world, selfEntity, t);
        if (directBlocked || currentlyOverlapping) {
            long now = System.currentTimeMillis();
            if (!Double.isNaN(npc.lastBlockedX) && !Double.isNaN(npc.lastBlockedY)
                    && (now - npc.lastBlockedTime) < Npc.BLOCKED_MEMORY_MS
                    && Math.abs(tryX - npc.lastBlockedX) < 10 && Math.abs(tryY - npc.lastBlockedY) < 10) {
                double baseAng = Math.atan2(dy, dx);
                double ang = baseAng + Math.toRadians(45);
                double cx = t.x + Math.cos(ang) * step;
                double cy = t.y + Math.sin(ang) * step;
                double[] cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                cx = cl[0];
                cy = cl[1];
                if ((cx != t.x || cy != t.y)
                        && !map.isBlockedByAirWall(cx, cy, t.width, t.height)
                        && !npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                    tryX = cx;
                    tryY = cy;
                } else {
                    ang = baseAng - Math.toRadians(45);
                    cx = t.x + Math.cos(ang) * step;
                    cy = t.y + Math.sin(ang) * step;
                    cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                    cx = cl[0];
                    cy = cl[1];
                    if ((cx != t.x || cy != t.y)
                            && !map.isBlockedByAirWall(cx, cy, t.width, t.height)
                            && !npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                        tryX = cx;
                        tryY = cy;
                    } else {
                        double sstep = step * 0.5;
                        ang = baseAng + Math.toRadians(30);
                        cx = t.x + Math.cos(ang) * sstep;
                        cy = t.y + Math.sin(ang) * sstep;
                        cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                        cx = cl[0];
                        cy = cl[1];
                        if ((cx != t.x || cy != t.y)
                                && !map.isBlockedByAirWall(cx, cy, t.width, t.height)
                                && !npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                            tryX = cx;
                            tryY = cy;
                        } else {
                            tryX = t.x;
                            tryY = t.y;
                        }
                    }
                }
                npc.lastBlockedX = tryX;
                npc.lastBlockedY = tryY;
                npc.lastBlockedTime = now;
            } else {
                double baseAng = Math.atan2(dy, dx);
                double[] angOffsetsDeg = new double[]{0, 15, -15, 30, -30, 45, -45, 60, -60, 90, -90, 120, -120, 150, -150, 180};
                double bestScore = Double.MAX_VALUE;
                double bestX = t.x;
                double bestY = t.y;
                double continuityPenalty = 0.6;
                for (double deg : angOffsetsDeg) {
                    double ang = baseAng + Math.toRadians(deg);
                    double cx = t.x + Math.cos(ang) * step;
                    double cy = t.y + Math.sin(ang) * step;
                    double[] cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                    cx = cl[0];
                    cy = cl[1];
                    if (cx == t.x && cy == t.y) {
                        continue;
                    }
                    if (map.isBlockedByAirWall(cx, cy, t.width, t.height)) {
                        continue;
                    }
                    if (npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                        continue;
                    }
                    double distToTarget = Math.hypot(tx - cx, ty - cy);
                    double angPenalty = 0.0;
                    double lmLen = Math.hypot(npc.lastMoveX, npc.lastMoveY);
                    if (lmLen > 0.001) {
                        double lmAng = Math.atan2(npc.lastMoveY, npc.lastMoveX);
                        double diff = Math.abs(Math.atan2(Math.sin(ang - lmAng), Math.cos(ang - lmAng)));
                        angPenalty = diff * lmLen * continuityPenalty;
                    }
                    double score = distToTarget + angPenalty;
                    if (score < bestScore) {
                        bestScore = score;
                        bestX = cx;
                        bestY = cy;
                    }
                }

                if (bestX != t.x || bestY != t.y) {
                    tryX = bestX;
                    tryY = bestY;
                } else {
                    double cx = t.x + Math.cos(baseAng + Math.PI / 2) * step;
                    double cy = t.y + Math.sin(baseAng + Math.PI / 2) * step;
                    double[] cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                    cx = cl[0];
                    cy = cl[1];
                    if (!map.isBlockedByAirWall(cx, cy, t.width, t.height)
                            && (cx != t.x || cy != t.y)
                            && !npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                        tryX = cx;
                        tryY = cy;
                    } else {
                        cx = t.x + Math.cos(baseAng - Math.PI / 2) * step;
                        cy = t.y + Math.sin(baseAng - Math.PI / 2) * step;
                        cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                        cx = cl[0];
                        cy = cl[1];
                        if (!map.isBlockedByAirWall(cx, cy, t.width, t.height)
                                && (cx != t.x || cy != t.y)
                                && !npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                            tryX = cx;
                            tryY = cy;
                        } else {
                            tryX = t.x;
                            tryY = t.y;
                        }
                    }
                }
                npc.lastBlockedX = tryX;
                npc.lastBlockedY = tryY;
                npc.lastBlockedTime = now;
            }
        } else {
            double[] cl = map.clampEntityPosition(tryX, tryY, t.width, t.height);
            tryX = cl[0];
            tryY = cl[1];
            npc.lastBlockedX = Double.NaN;
            npc.lastBlockedY = Double.NaN;
        }

        if (tryX == t.x && tryY == t.y) {
            double[] scales = new double[]{0.6, 0.4, 0.2, 0.1};
            boolean moved = false;
            double baseAng = Math.atan2(dy, dx);
            double[] nudgeDeg = new double[]{0, 15, -15, 30, -30, 45, -45, 90, -90};
            for (double sc : scales) {
                double sstep = step * sc;
                for (double deg : nudgeDeg) {
                    double ang = baseAng + Math.toRadians(deg);
                    double cx = t.x + Math.cos(ang) * sstep;
                    double cy = t.y + Math.sin(ang) * sstep;
                    double[] cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                    cx = cl[0];
                    cy = cl[1];
                    if ((cx != t.x || cy != t.y)
                            && !map.isBlockedByAirWall(cx, cy, t.width, t.height)
                            && !npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                        tryX = cx;
                        tryY = cy;
                        moved = true;
                        break;
                    }
                }
                if (moved) {
                    break;
                }
            }
            if (!moved) {
                int searchCount = 0;
                double[] wideNudgeDeg = new double[]{0, 22.5, -22.5, 45, -45, 67.5, -67.5, 90, -90, 112.5, -112.5, 135, -135, 157.5, -157.5, 180};
                for (double deg : wideNudgeDeg) {
                    if (searchCount >= 8) {
                        break;
                    }
                    searchCount++;
                    double ang = baseAng + Math.toRadians(deg);
                    double cx = t.x + Math.cos(ang) * step;
                    double cy = t.y + Math.sin(ang) * step;
                    double[] cl = map.clampEntityPosition(cx, cy, t.width, t.height);
                    cx = cl[0];
                    cy = cl[1];
                    if ((cx != t.x || cy != t.y)
                            && !map.isBlockedByAirWall(cx, cy, t.width, t.height)
                            && !npc.isOverlappedWithOthers(cx, cy, t.width, t.height, world, selfEntity)) {
                        tryX = cx;
                        tryY = cy;
                        break;
                    }
                }
            }
        }

        npc.lastMoveX = tryX - t.x;
        npc.lastMoveY = tryY - t.y;

        if (tryX > t.x) {
            spr.facing = Facing.RIGHT;
            spr.lastHorizontalFacing = Facing.RIGHT;
        } else if (tryX < t.x) {
            spr.facing = Facing.LEFT;
            spr.lastHorizontalFacing = Facing.LEFT;
        }
        spr.displayFacing = spr.facing;

        t.x = tryX;
        t.y = tryY;
    }
}
