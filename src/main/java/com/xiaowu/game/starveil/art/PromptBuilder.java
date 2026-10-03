package com.xiaowu.game.starveil.art;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 把 {@link ArtPromptLibrary} 里的数据按 target 的 recipe 合成为可直接使用的提示词。
 *
 * <p>合成规则见 {@code art/targets.json} 的 {@code _recipeSyntax}：
 * 正向词按 recipe 顺序拼接，反向词由 target 的 {@code negative} 预设合并后去重。
 */
public final class PromptBuilder {

    /**
     * 一次出图任务。
     *
     * @param targetId     目标类型（standee / scene-bg / pixel-map / ...）
     * @param characterId  角色 id；场景类任务为 {@code null}
     * @param outfitId     服装 id；场景类任务为 {@code null}
     * @param expressionId 表情 id；场景类或模板任务可为 {@code null}
     * @param sceneId      场景 id；角色类任务为 {@code null}
     * @param subject      模板任务的主体名（用于输出命名）；可为 {@code null}
     */
    public record Job(String targetId,
                      String characterId,
                      String outfitId,
                      String expressionId,
                      String sceneId,
                      String subject) {

        public static Job standee(String characterId, String outfitId, String expressionId) {
            return new Job("standee", characterId, outfitId, expressionId, null, null);
        }

        public static Job expressionIcon(String characterId, String outfitId, String expressionId) {
            return new Job("expression-icon", characterId, outfitId, expressionId, null, null);
        }

        public static Job scene(String sceneId, String targetId) {
            return new Job(targetId, null, null, null, sceneId, null);
        }

        /**
         * 模板任务：没有具体实例，用角色的默认服装 + 默认表情生成一份可复制的样板。
         * subject 取角色 id，用于输出文件命名。
         */
        public static Job template(String targetId, String characterId, String outfitId, String expressionId) {
            return new Job(targetId, characterId, outfitId, expressionId, null, characterId);
        }
    }

    /** 构建结果：正向词、反向词、参数、后处理、注意事项。 */
    public record BuiltPrompt(String jobId,
                              String targetId,
                              String title,
                              String modelId,
                              String modelLabel,
                              String modelFile,
                              List<String> loras,
                              Map<String, String> params,
                              int width,
                              int height,
                              String positive,
                              String negative,
                              List<String> postProcess,
                              List<String> warnings,
                              String outputDir,
                              String outputFile) {

        /** 相对于项目根目录的落位路径，用 / 分隔。 */
        public String outputPath() {
            return outputDir + "/" + outputFile;
        }
    }

    private final ArtPromptLibrary lib;

    public PromptBuilder(ArtPromptLibrary lib) {
        this.lib = lib;
    }

    // ==================== 主流程 ====================

    public BuiltPrompt build(Job job) {
        JsonObject target = lib.target(job.targetId());

        JsonObject character = job.characterId() == null ? null : lib.character(job.characterId());
        JsonObject outfit = character == null ? null : subObject(character, "outfits", job.outfitId(),
                "角色 " + job.characterId() + " 的服装");
        JsonObject expression = character == null || job.expressionId() == null ? null
                : subObject(character, "expressions", job.expressionId(),
                "角色 " + job.characterId() + " 的表情");
        JsonObject scene = job.sceneId() == null ? null : lib.scene(job.sceneId());

        // ---------- 正向词 ----------
        List<String> parts = new ArrayList<>();
        for (String token : ArtPromptLibrary.stringList(target, "recipe")) {
            parts.addAll(resolveToken(token, job, target, character, outfit, expression, scene));
        }
        String positive = String.join(", ", dedup(parts));

        // ---------- 反向词 ----------
        List<String> negParts = new ArrayList<>();
        for (String presetId : ArtPromptLibrary.stringList(target, "negative")) {
            negParts.addAll(lib.negativePreset(presetId));
        }
        String negative = String.join(", ", dedup(negParts));

        // ---------- 模型与参数 ----------
        String modelId = ArtPromptLibrary.requireString(target, "model");
        JsonObject model = lib.model(modelId);
        Map<String, String> params = new LinkedHashMap<>();
        JsonObject paramsObj = model.getAsJsonObject("params");
        if (paramsObj != null) {
            for (Map.Entry<String, JsonElement> e : paramsObj.entrySet()) {
                params.put(e.getKey(), e.getValue().getAsString());
            }
        }
        List<String> loras = new ArrayList<>();
        if (model.has("loras") && model.get("loras").isJsonArray()) {
            for (JsonElement e : model.getAsJsonArray("loras")) {
                JsonObject lo = e.getAsJsonObject();
                String name = ArtPromptLibrary.requireString(lo, "name");
                String weight = lo.has("weight") ? lo.get("weight").getAsString() : "1.0";
                loras.add(name + " @ " + weight);
            }
        }

        // ---------- 分辨率 ----------
        int[] res = ArtPromptLibrary.intPair(target, "resolution");
        int width = res == null ? 0 : res[0];
        int height = res == null ? 0 : res[1];

        // ---------- 输出路径 ----------
        JsonObject output = target.getAsJsonObject("output");
        String outputDir = ArtPromptLibrary.requireString(output, "dir");
        String pattern = ArtPromptLibrary.requireString(output, "pattern");
        String outputFile = pattern
                .replace("{outfit}", nz(job.outfitId()))
                .replace("{expression}", nz(job.expressionId()))
                .replace("{scene}", nz(job.sceneId()))
                .replace("{subject}", nz(job.subject()));

        return new BuiltPrompt(
                jobId(job),
                job.targetId(),
                ArtPromptLibrary.optString(target, "label", job.targetId()),
                modelId,
                ArtPromptLibrary.optString(model, "label", modelId),
                ArtPromptLibrary.optString(model, "file", ""),
                List.copyOf(loras),
                Map.copyOf(params),
                width,
                height,
                positive,
                negative,
                List.copyOf(lib.postProcessSteps(ArtPromptLibrary.optString(target, "postProcess", ""))),
                collectWarnings(job, target, model, character, outfit, expression, scene),
                outputDir,
                outputFile);
    }

    // ==================== recipe token 解析 ====================

    private List<String> resolveToken(String token,
                                      Job job,
                                      JsonObject target,
                                      JsonObject character,
                                      JsonObject outfit,
                                      JsonObject expression,
                                      JsonObject scene) {
        if (token.equals("identity")) {
            return require(character, target, job, "identity", ArtPromptLibrary.stringList(character, "identity"));
        }
        if (token.equals("outfit")) {
            return require(outfit, target, job, "outfit", ArtPromptLibrary.stringList(outfit, "tags"));
        }
        if (token.equals("expression")) {
            return require(expression, target, job, "expression", ArtPromptLibrary.stringList(expression, "tags"));
        }
        if (token.equals("scene")) {
            return require(scene, target, job, "scene", ArtPromptLibrary.stringList(scene, "tags"));
        }
        if (token.startsWith("shot:")) {
            // shots.<id> 直接就是 tag 数组，不是 {"tags": [...]} 对象
            String shotId = token.substring(5);
            JsonObject shots = character == null ? null : character.getAsJsonObject("shots");
            if (shots == null || !shots.has(shotId)) {
                throw new IllegalStateException(
                        "target \"" + job.targetId() + "\" 的 recipe 需要 " + token
                                + "，但角色 " + job.characterId() + " 没有定义该构图"
                                + "（已定义: " + (shots == null ? "[]" : shots.keySet()) + "）");
            }
            return ArtPromptLibrary.stringList(shots, shotId);
        }
        if (token.startsWith("positive:")) {
            return lib.positivePreset(token.substring(9));
        }
        if (token.startsWith("literal:")) {
            return splitTags(token.substring(8));
        }
        throw new IllegalArgumentException(
                "target \"" + job.targetId() + "\" 的 recipe 里有无法识别的 token: \"" + token
                        + "\"（支持 identity / outfit / expression / scene / shot:<id> / positive:<id> / literal:<text>）");
    }

    private static List<String> require(JsonObject source, JsonObject target, Job job,
                                        String what, List<String> tags) {
        if (source == null) {
            throw new IllegalStateException(
                    "target \"" + job.targetId() + "\" 的 recipe 需要 " + what
                            + "，但这次任务没有提供（job=" + job + "）");
        }
        return tags;
    }

    // ==================== 注意事项收集 ====================

    private List<String> collectWarnings(Job job,
                                         JsonObject target,
                                         JsonObject model,
                                         JsonObject character,
                                         JsonObject outfit,
                                         JsonObject expression,
                                         JsonObject scene) {
        List<String> out = new ArrayList<>();

        addIfPresent(out, target, "resolutionNote");
        addIfPresent(out, target, "criticalWarning");
        addIfPresent(out, target, "capabilityWarning");
        addIfPresent(out, target, "aspectNote");
        addIfPresent(out, model, "licenseNote");
        addIfPresent(out, model, "note");

        // 已存在的表情图标 —— 覆盖前必须备份
        if ("expression-icon".equals(job.targetId())
                && expression != null && expression.has("gameFile") && !expression.get("gameFile").isJsonNull()) {
            out.add("⚠️ 目标文件 " + expression.get("gameFile").getAsString()
                    + " 已存在（现有的是占位图，像素风、疑似借用素材）。覆盖前请备份。");
        }

        if (scene != null) {
            addIfPresent(out, scene, "note");
        }
        if (outfit != null) {
            for (String todo : ArtPromptLibrary.stringList(outfit, "todos")) {
                out.add("TODO 设定待补: " + todo);
            }
        }

        // 只有带表情的任务才需要这条 —— 场景/地图没有差分，挂上去只是噪音
        if (job.expressionId() != null) {
            out.add("固定 seed 以保证表情差分之间的一致性（发型/服装/姿态/光照不能变）。");
        }
        return out;
    }

    private static void addIfPresent(List<String> out, JsonObject o, String key) {
        String v = ArtPromptLibrary.optString(o, key, null);
        if (v != null && !v.isBlank()) {
            out.add(v);
        }
    }

    // ==================== 工具 ====================

    /** 按 recipe 顺序去重（忽略大小写），保留首次出现的写法。 */
    static List<String> dedup(List<String> in) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (String s : in) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            seen.putIfAbsent(t.toLowerCase(Locale.ROOT), t);
        }
        return new ArrayList<>(seen.values());
    }

    static List<String> splitTags(String text) {
        List<String> out = new ArrayList<>();
        for (String piece : text.split(",")) {
            String t = piece.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static JsonObject subObject(JsonObject parent, String section, String id, String what) {
        if (id == null) {
            return null;
        }
        JsonObject sec = parent.getAsJsonObject(section);
        JsonObject o = sec == null ? null : sec.getAsJsonObject(id);
        if (o == null) {
            throw new IllegalArgumentException(what + " 中没有: " + id
                    + "（已定义: " + (sec == null ? "[]" : sec.keySet()) + "）");
        }
        return o;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String jobId(Job job) {
        StringBuilder sb = new StringBuilder(job.targetId());
        for (String p : new String[]{job.characterId(), job.outfitId(), job.expressionId(),
                job.sceneId(), job.subject()}) {
            if (p != null && !p.isEmpty()) {
                sb.append('/').append(p);
            }
        }
        return sb.toString();
    }
}
