package com.xiaowu.game.starveil.game.ecs.factory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaowu.game.starveil.game.ecs.AnimKey;
import com.xiaowu.game.starveil.game.ecs.AnimState;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;
import com.xiaowu.game.starveil.infrastructure.persistence.ConfigReferences;
import com.xiaowu.game.starveil.render.AnimationSheet;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 贴图/动画表构建工具。将 player.json / NPC def 解析为各状态各朝向的动画表。
 * 集中了原先分散在 Player 与 WorldMap 中的 JSON 解析逻辑。
 */
final class SkinSheets {
    static final String FALLBACK_ICON = "starveil:textures/icons/app-icon.png";

    private SkinSheets() {
    }

    /** 构建完成的可视化皮肤配置。 */
    static final class SkinConfig {
        boolean enableFacing = true;
        Facing textureFacing = Facing.LEFT;
        Facing initialFacing = Facing.LEFT;
        final Map<AnimState, Map<Facing, AnimationSheet>> stateTextures = new EnumMap<>(AnimState.class);
        /**
         * 新动画键体系的贴图表，支持变体与任意效果
         * （{@code LEFT}、{@code LEFT:WALK}、{@code LEFT:LENGTHWAYS}、
         * {@code LEFT:LENGTHWAYS:WALK}）。
         *
         * <p>与 {@link #stateTextures} 并存：新结构优先查找，旧结构作为回退，
         * 因此老配置不需要任何改动。
         */
        final Map<AnimKey, AnimationSheet> keyedSheets = new HashMap<>();
    }

    // ==================== 玩家 ====================

    /**
     * 加载指定角色（player.json），找不到则回退 default，再回退硬编码默认。
     */
    static SkinConfig forPlayer(String selectedCharacter) {
        SkinConfig cfg = new SkinConfig();
        try (InputStream is = ResourceResolver.getResourceAsStream("starveil:data/entities/player.json")) {
            if (is == null) {
                return defaultConfig(cfg);
            }
            JsonObject root = com.google.gson.JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
            JsonObject charConfig = null;
            if (root.has(selectedCharacter)) {
                charConfig = root.getAsJsonObject(selectedCharacter);
            }
            if (charConfig == null && root.has("default")) {
                charConfig = root.getAsJsonObject("default");
            }
            if (charConfig == null) {
                return defaultConfig(cfg);
            }

            cfg.enableFacing = !charConfig.has("enableFacing") || charConfig.get("enableFacing").getAsBoolean();
            cfg.textureFacing = parseFacing(charConfig, "texture_facing", Facing.LEFT);
            cfg.initialFacing = parseFacing(charConfig, "initial_facing", Facing.DOWN);

            boolean any = false;
            if (charConfig.has("texture")) {
                JsonElement texEl = charConfig.get("texture");
                if (texEl.isJsonPrimitive()) {
                    any = addSheetForAllStates(cfg.stateTextures, texEl.getAsString());
                } else if (texEl.isJsonObject()) {
                    JsonObject texObj = texEl.getAsJsonObject();
                    // 新动画键语法（可带变体与效果）
                    parseKeyedSheets(texObj, cfg.keyedSheets);
                    if (texObj.has("idle") || texObj.has("walk")) {
                        if (texObj.has("idle")) {
                            cfg.stateTextures.put(AnimState.IDLE, parseStateValueSheets(texObj, texObj.get("idle")));
                            any = true;
                        }
                        if (texObj.has("walk")) {
                            cfg.stateTextures.put(AnimState.WALK, parseStateValueSheets(texObj, texObj.get("walk")));
                            any = true;
                        }
                    } else if (!cfg.keyedSheets.isEmpty()) {
                        // 只写了动画键（例如只有 LEFT:LENGTHWAYS:WALK）时，
                        // 不能让空的方向表把整个配置判为「无效」而回退到默认图标。
                        any = true;
                    } else {
                        Map<Facing, AnimationSheet> map = parseFacingSheets(texObj);
                        cfg.stateTextures.put(AnimState.IDLE, map);
                        cfg.stateTextures.put(AnimState.WALK, copyFacingSheets(map));
                        any = !map.isEmpty();
                    }
                }
            }
            if (!any) {
                return defaultConfig(cfg);
            }
        } catch (Exception e) {
            return defaultConfig(cfg);
        }
        return cfg;
    }

    private static SkinConfig defaultConfig(SkinConfig cfg) {
        cfg.enableFacing = true;
        cfg.textureFacing = Facing.LEFT;
        cfg.initialFacing = Facing.DOWN;
        addSheetForAllStates(cfg.stateTextures, FALLBACK_ICON);
        return cfg;
    }

    // ==================== NPC ====================

    /**
     * 从 NPC def 解析 texture 字段（支持 "player:角色" 引用、字符串、状态/多方向对象）。
     * facing 配置同时从 def 覆盖。
     */
    static SkinConfig forNpc(JsonObject def) {
        SkinConfig cfg = new SkinConfig();
        cfg.enableFacing = def.has("enableFacing") && def.get("enableFacing").getAsBoolean();
        boolean hasFacingConfig = def.has("enableFacing") || def.has("texture_facing") || def.has("initial_facing");

        JsonElement texEl = def.get("texture");
        if (texEl != null) {
            if (texEl.isJsonPrimitive() && texEl.getAsJsonPrimitive().isString()) {
                String raw = texEl.getAsString();
                if (raw.startsWith("player:")) {
                    SkinConfig playerCfg = forPlayer(raw.substring("player:".length()));
                    copySheets(playerCfg.stateTextures, cfg.stateTextures);
                    cfg.keyedSheets.putAll(playerCfg.keyedSheets);
                    if (!hasFacingConfig) {
                        cfg.enableFacing = playerCfg.enableFacing;
                        cfg.textureFacing = playerCfg.textureFacing;
                        cfg.initialFacing = playerCfg.initialFacing;
                    }
                } else {
                    addSheetForAllStates(cfg.stateTextures, raw);
                }
            } else if (texEl.isJsonObject()) {
                JsonObject texObj = texEl.getAsJsonObject();
                parseKeyedSheets(texObj, cfg.keyedSheets);
                if (texObj.has("default")) {
                    addSheetForAllStates(cfg.stateTextures, ConfigReferences.resolve(texObj, "default"));
                } else if (texObj.has("idle") || texObj.has("walk")) {
                    if (texObj.has("idle")) {
                        cfg.stateTextures.put(AnimState.IDLE, parseStateValueSheets(texObj, texObj.get("idle")));
                    }
                    if (texObj.has("walk")) {
                        cfg.stateTextures.put(AnimState.WALK, parseStateValueSheets(texObj, texObj.get("walk")));
                    }
                } else {
                    Map<Facing, AnimationSheet> map = parseFacingSheets(texObj);
                    cfg.stateTextures.put(AnimState.IDLE, map);
                    cfg.stateTextures.put(AnimState.WALK, copyFacingSheets(map));
                }
            }
        }

        if (def.has("enableFacing")) {
            cfg.enableFacing = def.get("enableFacing").getAsBoolean();
        }
        cfg.textureFacing = parseFacing(def, "texture_facing", cfg.textureFacing);
        cfg.initialFacing = parseFacing(def, "initial_facing", cfg.initialFacing);
        return cfg;
    }

    // ==================== 通用解析工具 ====================

    private static Facing parseFacing(JsonObject obj, String key, Facing fallback) {
        if (obj.has(key)) {
            try {
                return Facing.valueOf(obj.get(key).getAsString().toUpperCase());
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    /**
     * 解析新动画键语法，结果写入 {@code out}。
     *
     * <p>支持的写法：
     * <pre>
     *   "LEFT"                 → 普通模型集的 LEFT 基础姿态
     *   "LEFT:WALK"            → 普通模型集的 LEFT 行走
     *   "LEFT:LENGTHWAYS"      → LENGTHWAYS 变体的 LEFT 基础姿态
     *   "LEFT:LENGTHWAYS:WALK" → LENGTHWAYS 变体的 LEFT 行走
     * </pre>
     *
     * <p>只处理「以方向开头且合法」的键；{@code idle} / {@code walk} /
     * {@code default} 这些旧写法由既有分支处理，两边互不干扰。
     */
    static void parseKeyedSheets(JsonObject texObj, Map<AnimKey, AnimationSheet> out) {
        if (texObj == null || out == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : texObj.entrySet()) {
            String keyText = entry.getKey();
            if (keyText == null || !AnimKey.isValid(keyText)) {
                continue;
            }
            // 值可能是 "@同级键" 的引用形式
            String path = ConfigReferences.resolve(texObj, keyText);
            if (path == null) {
                continue;
            }
            AnimationSheet sheet = loadSheet(path);
            if (sheet != null) {
                out.put(AnimKey.parse(keyText), sheet);
            }
        }
    }

    /** {LEFT/RIGHT/UP/DOWN} → 动画表映射（缺项留空，由精灵系统回退）。 */
    static Map<Facing, AnimationSheet> parseFacingSheets(JsonObject texObj) {
        Map<Facing, AnimationSheet> map = new HashMap<>();
        if (texObj == null) return map;
        for (Facing f : Facing.values()) {
            if (texObj.has(f.name())) {
                AnimationSheet sheet = loadSheet(ConfigReferences.resolve(texObj, f.name()));
                if (sheet != null) map.put(f, sheet);
            }
        }
        return map;
    }

    /** 状态值：字符串（所有朝向）或 {LEFT/RIGHT/UP/DOWN} 对象。 */
    static Map<Facing, AnimationSheet> parseStateValueSheets(JsonObject scope, JsonElement el) {
        Map<Facing, AnimationSheet> map = new HashMap<>();
        if (el == null) return map;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            AnimationSheet sheet = loadSheet(ConfigReferences.resolveValue(scope, el));
            if (sheet != null) {
                for (Facing f : Facing.values()) map.put(f, sheet);
            }
        } else if (el.isJsonObject()) {
            map.putAll(parseFacingSheets(el.getAsJsonObject()));
        }
        return map;
    }

    static Map<Facing, AnimationSheet> copyFacingSheets(Map<Facing, AnimationSheet> src) {
        Map<Facing, AnimationSheet> map = new HashMap<>();
        if (src != null) map.putAll(src);
        return map;
    }

    /** 单张贴图应用到所有状态与朝向。返回是否成功。 */
    static boolean addSheetForAllStates(Map<AnimState, Map<Facing, AnimationSheet>> out, String path) {
        AnimationSheet sheet = loadSheet(path);
        if (sheet == null) return false;
        Map<Facing, AnimationSheet> map = new HashMap<>();
        for (Facing f : Facing.values()) map.put(f, sheet);
        out.put(AnimState.IDLE, copyFacingSheets(map));
        out.put(AnimState.WALK, copyFacingSheets(map));
        return true;
    }

    private static void copySheets(Map<AnimState, Map<Facing, AnimationSheet>> src,
                                   Map<AnimState, Map<Facing, AnimationSheet>> dst) {
        if (src == null) return;
        for (Map.Entry<AnimState, Map<Facing, AnimationSheet>> e : src.entrySet()) {
            dst.put(e.getKey(), copyFacingSheets(e.getValue()));
        }
    }

    private static AnimationSheet loadSheet(String path) {
        if (path == null) {
            return null;
        }
        AnimationSheet sheet = AnimationSheet.load(path);
        if (sheet == null && !FALLBACK_ICON.equals(path)) {
            return AnimationSheet.load(FALLBACK_ICON);
        }
        return sheet;
    }
}
