package com.xiaowu.game.starveil.game.ecs.factory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaowu.game.starveil.game.ecs.AnimState;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.AutoHeal;
import com.xiaowu.game.starveil.game.ecs.comp.Attributes;
import com.xiaowu.game.starveil.game.ecs.comp.Charge;
import com.xiaowu.game.starveil.game.ecs.comp.Gravity;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Magic;
import com.xiaowu.game.starveil.game.ecs.comp.Npc;
import com.xiaowu.game.starveil.game.ecs.comp.PlayerController;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Stamina;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import javafx.scene.layout.Pane;

/**
 * 实体工厂：把 JSON / 代码配置装配成 ECS 实体组件。
 */
public final class EntityFactory {
    private EntityFactory() {
    }

    // ==================== 玩家 ====================

    /**
     * 按世界的玩法模式给实体挂上重力。
     *
     * <p><b>为什么要在这里挂</b>：重力原本只挂给玩家（见
     * {@code GameInstance.applyGravityComponent}），于是重力图里的 NPC
     * 一个个浮在半空不动 —— 玩家一眼就能看出不对。规则改成
     * 「重力模式下，生成的实体会下落」，玩家、NPC、掉落物都走这一条。
     *
     * <p>想让某个实体飘着（漂浮物、挂在半空的装饰），生成之后把它的
     * {@code Gravity.enabled} 置 false 即可 —— 组件在、但不受重力。
     */
    public static void applyGravityForMode(World world, int entity) {
        if (world == null || entity < 0) {
            return;
        }
        if (world.gameplayMode().hasGravity() && world.get(entity, Gravity.class) == null) {
            world.add(entity, new Gravity());
        }
    }

    /**
     * 创建玩家实体（初始角色 default），视图加入世界 Pane。
     */
    public static int createPlayer(World world, Pane pane, double spawnX, double spawnY) {
        SkinSheets.SkinConfig cfg = SkinSheets.forPlayer("default");

        int e = world.newEntity();
        PlayerController ctl = new PlayerController();
        Transform t = new Transform(spawnX, spawnY, 100, 100);
        Health h = new Health(100, 100);
        Sprite spr = new Sprite();
        spr.playerLike = true;
        applySkin(cfg, spr);
        spr.view.setFitWidth(t.width);
        pane.getChildren().add(spr.view);

        world.add(e, ctl);
        world.add(e, t);
        world.add(e, h);
        world.add(e, new Stamina());
        world.add(e, new Magic());
        world.add(e, new Charge());
        world.add(e, new AutoHeal());
        world.add(e, spr);

        Attributes attrs = new Attributes()
                .set(Attributes.ENTITY_TYPE, Attributes.ENTITY_TYPE_PLAYER)
                .set(Attributes.ENTITY_ID, "player")
                .set(Attributes.DISPLAY_NAME, "玩家")
                .set(Attributes.PLAYER_CHARACTER, ctl.selectedCharacter);
        world.add(e, attrs);
        applyGravityForMode(world, e);
        return e;
    }

    /**
     * 切换玩家角色皮肤。
     */
    public static void applyPlayerCharacter(World world, int playerEntity, String characterId) {
        if (characterId == null || characterId.equals(world.get(playerEntity, PlayerController.class).selectedCharacter)) {
            return;
        }
        PlayerController ctl = world.get(playerEntity, PlayerController.class);
        ctl.selectedCharacter = characterId;
        Sprite spr = world.get(playerEntity, Sprite.class);
        applySkin(SkinSheets.forPlayer(characterId), spr);
        LoggerManager.Logger("INFO", "玩家角色已切换为: " + characterId);
    }

    private static void applySkin(SkinSheets.SkinConfig cfg, Sprite spr) {
        spr.textureMap.clear();
        spr.textureMap.putAll(cfg.stateTextures);
        // 新动画键体系（支持变体与任意效果），查找时优先于上面的旧结构
        spr.keyedSheets.clear();
        spr.keyedSheets.putAll(cfg.keyedSheets);
        spr.enableFacing = cfg.enableFacing;
        spr.textureFacing = cfg.textureFacing;
        spr.initialFacing = cfg.initialFacing;
        spr.facing = cfg.initialFacing;
        spr.displayFacing = cfg.initialFacing;
        spr.lastHorizontalFacing = cfg.initialFacing.isHorizontal() ? cfg.initialFacing : Facing.LEFT;
        spr.currentState = AnimState.IDLE;
    }

    // ==================== NPC ====================

    /**
     * 从 map JSON 的 npc def 装配 NPC 实体（移植自 WorldMap.createNPC）。
     */
    public static int createNpc(World world, Pane pane, WorldMap owner, String id, JsonObject def) {
        SkinSheets.SkinConfig cfg = SkinSheets.forNpc(def);

        double w = def.has("width") ? def.get("width").getAsDouble() : 100;
        double h = def.has("height") ? def.get("height").getAsDouble() : 100;

        double nx = 0, ny = 0;
        try {
            if (def.has("spawn")) {
                JsonElement sp = def.get("spawn");
                if (sp.isJsonArray() && !sp.getAsJsonArray().isEmpty()) {
                    int idx = (int) (Math.random() * sp.getAsJsonArray().size());
                    JsonObject choose = sp.getAsJsonArray().get(idx).getAsJsonObject();
                    nx = choose.has("x") ? choose.get("x").getAsDouble() : nx;
                    ny = choose.has("y") ? choose.get("y").getAsDouble() : ny;
                } else if (sp.isJsonObject()) {
                    JsonObject so = sp.getAsJsonObject();
                    nx = so.has("x") ? so.get("x").getAsDouble() : nx;
                    ny = so.has("y") ? so.get("y").getAsDouble() : ny;
                }
            } else if (def.has("location") && def.get("location").isJsonObject()) {
                JsonObject loc = def.getAsJsonObject("location");
                nx = loc.has("x") ? loc.get("x").getAsDouble() : nx;
                ny = loc.has("y") ? loc.get("y").getAsDouble() : ny;
            }
        } catch (Exception ignored) {
        }

        double[] safe = owner.findSafePosition(nx, ny, w, h);
        if (safe[0] != nx || safe[1] != ny) {
            nx = safe[0];
            ny = safe[1];
            LoggerManager.Logger("INFO", "NPC " + id + " spawn inside airWall, teleported to safe position: " + nx + "," + ny);
        }

        int e = world.newEntity();
        Transform t = new Transform(nx, ny, w, h);
        Sprite spr = new Sprite();
        applySkin(cfg, spr);

        Npc npc = new Npc(id);
        if (def.has("algorithm")) {
            String alg = def.get("algorithm").getAsString();
            try {
                npc.algorithm = Npc.Algorithm.valueOf(alg.trim().toUpperCase());
            } catch (Exception ignored) {
            }
        }
        if (def.has("vision")) {
            try {
                npc.visionRadius = def.get("vision").getAsDouble();
                npc.originalVisionRadius = npc.visionRadius;
                npc.currentVisionRadius = npc.visionRadius;
            } catch (Exception ignored) {
            }
        }
        if (def.has("attack")) {
            try {
                npc.attackRadius = def.get("attack").getAsDouble();
            } catch (Exception ignored) {
            }
        }
        if (def.has("damage")) {
            try {
                npc.attackDamage = def.get("damage").getAsDouble();
            } catch (Exception ignored) {
            }
        }
        if (def.has("speed")) {
            try {
                npc.speed = def.get("speed").getAsDouble();
            } catch (Exception ignored) {
            }
        }
        if (def.has("stopRadius")) {
            try {
                npc.stopRadius = def.get("stopRadius").getAsDouble();
            } catch (Exception ignored) {
            }
        }
        if (def.has("patrol") && def.get("patrol").getAsBoolean()) {
            npc.patrolEnabled = true;
        }

        Health health = new Health(50, 50);
        if (def.has("health")) {
            try {
                double hp = def.get("health").getAsDouble();
                if (hp > 0) {
                    health.max = hp;
                    health.current = hp;
                } else {
                    health.max = -1;
                    health.immortal = true;
                }
            } catch (Exception ignored) {
            }
        }

        world.add(e, npc);
        world.add(e, t);
        world.add(e, health);
        world.add(e, spr);

        Attributes attrs = new Attributes()
                .set(Attributes.ENTITY_TYPE, Attributes.ENTITY_TYPE_NPC)
                .set(Attributes.ENTITY_ID, id)
                .set(Attributes.DISPLAY_NAME, id)
                .set(Attributes.NPC_ALGORITHM, npc.algorithm.name());
        world.add(e, attrs);
        pane.getChildren().add(spr.view);
        applyGravityForMode(world, e);

        // guide steps
        if (def.has("move") && def.get("move").isJsonArray()) {
            JsonArray moveArr = def.getAsJsonArray("move");
            for (JsonElement se : moveArr) {
                try {
                    JsonObject so = se.getAsJsonObject();
                    Npc.GuideStep gs = new Npc.GuideStep();
                    if (so.has("x")) gs.x = so.get("x").getAsDouble();
                    if (so.has("y")) gs.y = so.get("y").getAsDouble();
                    if (so.has("speed")) gs.speed = so.get("speed").getAsDouble();
                    if (so.has("wait")) gs.waitForPlayer = so.get("wait").getAsBoolean();
                    if (so.has("event") && so.get("event").isJsonObject()) {
                        JsonObject eventObj = so.getAsJsonObject("event");
                        gs.event = WorldMap.MapEvent.fromJson(eventObj);
                        gs.event.setNpcOwner(owner, e);
                        if (so.has("eventTriggerDistance")) {
                            gs.eventTriggerDistance = so.get("eventTriggerDistance").getAsDouble();
                        }
                    }
                    npc.guideSteps.add(gs);
                } catch (Exception ignored) {
                }
            }
        }

        return e;
    }

    // ==================== 通用查询 ====================

    /** 玩家实体 id（若无则 -1）。 */
    public static int findPlayer(World world) {
        int[] ps = world.view(PlayerController.class);
        return ps.length > 0 ? ps[0] : -1;
    }

    /** 按 NPC id 查实体（无则 -1）。 */
    public static int findNpc(World world, String npcId) {
        for (int e : world.view(Npc.class)) {
            Npc npc = world.get(e, Npc.class);
            if (npc != null && npc.id.equals(npcId)) {
                return e;
            }
        }
        return -1;
    }
}
