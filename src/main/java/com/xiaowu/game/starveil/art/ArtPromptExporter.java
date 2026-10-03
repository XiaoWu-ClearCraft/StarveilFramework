package com.xiaowu.game.starveil.art;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 把提示词库展开成一批可直接使用的文件。
 *
 * <p>用法：
 * <pre>
 * gradlew buildArtPrompts
 * # 或直接跑
 * java -cp ... com.xiaowu.game.starveil.art.ArtPromptExporter [artDir] [outDir]
 * </pre>
 *
 * <p>产物：
 * <pre>
 * build/art-prompts/
 *   README.md          索引入口
 *   manifest.json      全部任务的机器可读清单（用于复现，不要删）
 *   &lt;target&gt;/*.txt    每个任务一份，直接复制粘贴进 ComfyUI
 * </pre>
 */
public final class ArtPromptExporter {

    private static final String SPLIT = "# ---------------------------------------------------------------------------";

    public static void main(String[] args) throws IOException {
        Path artDir = Path.of(args.length > 0 ? args[0] : "art").toAbsolutePath().normalize();
        Path outDir = Path.of(args.length > 1 ? args[1] : "build/art-prompts").toAbsolutePath().normalize();

        ArtPromptLibrary lib = ArtPromptLibrary.load(artDir);
        PromptBuilder builder = new PromptBuilder(lib);

        List<PromptBuilder.Job> jobs = enumerateJobs(lib);
        if (jobs.isEmpty()) {
            System.out.println("[art] 没有生成任何任务 —— 检查 targets.json 的 appliesTo 和 scenes.json。");
            return;
        }

        List<PromptBuilder.BuiltPrompt> built = new ArrayList<>();
        for (PromptBuilder.Job job : jobs) {
            built.add(builder.build(job));
        }
        built.sort(Comparator.comparing(PromptBuilder.BuiltPrompt::jobId));

        if (Files.exists(outDir)) {
            deleteRecursively(outDir);
        }
        Files.createDirectories(outDir);

        for (PromptBuilder.BuiltPrompt p : built) {
            Path dir = outDir.resolve(p.targetId());
            Files.createDirectories(dir);
            String txtName = p.outputFile().replaceAll("\\.(png|jpg|jpeg|webp)$", "") + ".txt";
            Files.writeString(dir.resolve(txtName), renderPromptFile(p), StandardCharsets.UTF_8);
        }

        writeManifest(outDir, built);
        writeIndex(outDir, artDir, built);

        // ---------- 控制台摘要 ----------
        System.out.println("[art] 美术提示词已生成");
        System.out.println("[art]   数据目录: " + artDir);
        System.out.println("[art]   输出目录: " + outDir);
        System.out.println("[art]   共 " + built.size() + " 个任务:");
        Map<String, Integer> byTarget = new java.util.LinkedHashMap<>();
        for (PromptBuilder.BuiltPrompt p : built) {
            byTarget.merge(p.targetId(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : byTarget.entrySet()) {
            System.out.println("[art]     - " + e.getKey() + ": " + e.getValue());
        }
        System.out.println("[art]   入口: " + outDir.resolve("README.md"));
    }

    // ==================== 任务枚举 ====================

    static List<PromptBuilder.Job> enumerateJobs(ArtPromptLibrary lib) {
        List<PromptBuilder.Job> jobs = new ArrayList<>();

        // ① 角色 × 服装 × 表情（立绘 / 表情图标）
        List<String> charTargets = new ArrayList<>();
        List<String> templateTargets = new ArrayList<>();
        for (String targetId : lib.targetIds()) {
            String appliesTo = ArtPromptLibrary.optString(lib.target(targetId), "appliesTo", "");
            switch (appliesTo) {
                case "character-expressions" -> charTargets.add(targetId);
                case "template" -> templateTargets.add(targetId);
                default -> { /* scene 类由 scenes.json 驱动 */ }
            }
        }

        for (String characterId : lib.characterIds()) {
            JsonObject character = lib.character(characterId);
            JsonObject outfits = character.getAsJsonObject("outfits");
            JsonObject expressions = character.getAsJsonObject("expressions");
            if (outfits == null || expressions == null) {
                continue;
            }

            for (String targetId : charTargets) {
                for (String outfitId : outfits.keySet()) {
                    for (String expressionId : expressions.keySet()) {
                        jobs.add(new PromptBuilder.Job(targetId, characterId, outfitId, expressionId, null, null));
                    }
                }
            }

            // 模板任务：用角色的默认服装 + neutral 表情生成一份样板
            if (!outfits.isEmpty() && !expressions.isEmpty()) {
                String defaultOutfit = ArtPromptLibrary.optString(character, "defaultOutfit", "");
                if (defaultOutfit.isEmpty() || !outfits.has(defaultOutfit)) {
                    defaultOutfit = outfits.keySet().iterator().next();
                }
                String defaultExpression = expressions.has("neutral")
                        ? "neutral" : expressions.keySet().iterator().next();
                for (String targetId : templateTargets) {
                    jobs.add(PromptBuilder.Job.template(targetId, characterId, defaultOutfit, defaultExpression));
                }
            }
        }

        // ② 场景（每个场景自己声明用哪个 target）
        for (String sceneId : lib.sceneIds()) {
            JsonObject scene = lib.scene(sceneId);
            String targetId = ArtPromptLibrary.requireString(scene, "target");
            // 提前校验，避免拼错 target 名到导出时才炸
            lib.target(targetId);
            jobs.add(PromptBuilder.Job.scene(sceneId, targetId));
        }

        return jobs;
    }

    // ==================== 单文件渲染 ====================

    static String renderPromptFile(PromptBuilder.BuiltPrompt p) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ===========================================================================\n");
        sb.append("# ").append(p.jobId()).append('\n');
        sb.append("# 目标   ：").append(p.title()).append('\n');
        sb.append("# ===========================================================================\n");
        sb.append("# 模型   ：").append(p.modelLabel()).append('\n');
        if (!p.modelFile().isEmpty()) {
            sb.append("# 权重   ：").append(p.modelFile()).append('\n');
        }
        if (!p.loras().isEmpty()) {
            sb.append("# LoRA   ：").append(String.join(" | ", p.loras())).append('\n');
        }
        sb.append("# 分辨率 ：").append(p.width()).append(" x ").append(p.height()).append('\n');
        if (!p.params().isEmpty()) {
            List<String> kv = new ArrayList<>();
            p.params().forEach((k, v) -> kv.add(k + "=" + v));
            sb.append("# 参数   ：").append(String.join(", ", kv)).append('\n');
        }
        sb.append("# 落位   ：").append(p.outputPath()).append('\n');
        sb.append('\n');

        sb.append("# ---------- POSITIVE（复制这段） ----------\n");
        sb.append(p.positive()).append('\n');
        sb.append('\n');

        sb.append("# ---------- NEGATIVE（复制这段） ----------\n");
        sb.append(p.negative()).append('\n');

        if (!p.postProcess().isEmpty()) {
            sb.append('\n').append("# ---------- 后处理 ----------\n");
            for (String step : p.postProcess()) {
                sb.append("# ").append(step).append('\n');
            }
        }

        if (!p.warnings().isEmpty()) {
            sb.append('\n').append("# ---------- 注意 ----------\n");
            for (String w : p.warnings()) {
                sb.append("# - ").append(w).append('\n');
            }
        }

        return sb.toString();
    }

    // ==================== manifest.json ====================

    private static void writeManifest(Path outDir, List<PromptBuilder.BuiltPrompt> built) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("_comment",
                "由 gradlew buildArtPrompts 生成。请勿手工编辑 —— 改 art/*.json 后重新生成。"
                        + "本文件用于复现出图（记录 prompt / 参数 / 落位），不要删除。");
        root.addProperty("jobCount", built.size());

        JsonArray arr = new JsonArray();
        for (PromptBuilder.BuiltPrompt p : built) {
            JsonObject o = new JsonObject();
            o.addProperty("jobId", p.jobId());
            o.addProperty("target", p.targetId());
            o.addProperty("title", p.title());
            o.addProperty("model", p.modelId());
            o.addProperty("modelFile", p.modelFile());
            if (!p.loras().isEmpty()) {
                JsonArray loras = new JsonArray();
                p.loras().forEach(loras::add);
                o.add("loras", loras);
            }
            o.addProperty("width", p.width());
            o.addProperty("height", p.height());
            JsonObject params = new JsonObject();
            p.params().forEach(params::addProperty);
            o.add("params", params);
            o.addProperty("positive", p.positive());
            o.addProperty("negative", p.negative());
            JsonArray post = new JsonArray();
            p.postProcess().forEach(post::add);
            o.add("postProcess", post);
            JsonArray warns = new JsonArray();
            p.warnings().forEach(warns::add);
            o.add("warnings", warns);
            o.addProperty("outputPath", p.outputPath());
            arr.add(o);
        }
        root.add("jobs", arr);

        try (Writer w = Files.newBufferedWriter(outDir.resolve("manifest.json"), StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root, w);
        }
    }

    // ==================== README 索引 ====================

    private static void writeIndex(Path outDir, Path artDir, List<PromptBuilder.BuiltPrompt> built)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# 美术提示词索引\n\n");
        sb.append("> 本目录**由 `gradlew buildArtPrompts` 生成**，不要手工编辑。\n");
        sb.append("> 改 `").append(artDir.getFileName()).append("/` 下的 JSON 后重新生成。\n\n");
        sb.append("共 **").append(built.size()).append("** 个出图任务。\n\n");

        sb.append("## 使用方式\n\n");
        sb.append("1. 打开对应 `*.txt`，复制 `POSITIVE` 和 `NEGATIVE` 两段\n");
        sb.append("2. 按文件头注明的**模型 / 分辨率 / 参数**在 ComfyUI 里设置\n");
        sb.append("3. 出图后按「后处理」小节处理，按「落位」路径存放\n");
        sb.append("4. 参考 `manifest.json` 记录 seed 以便复现\n\n");

        sb.append("## 重要约定\n\n");
        sb.append("- **表情差分必须固定 seed** —— 否则切换表情时角色会「跳」\n");
        sb.append("- **立绘必须抠图 + 裁到 alpha 包围盒** —— 详见 `docs/art/character-bible.md` §3.3\n");
        sb.append("- **像素地图定稿后禁止缩放** —— 碰撞坐标绑定绝对像素，详见 `docs/art/pixel-pipeline.md` §1\n");
        sb.append("- **不要把 `.safetensors` 打进游戏包** —— 详见 `docs/art/licensing.md` §3.1\n\n");

        sb.append("## 任务清单\n\n");
        String currentTarget = null;
        for (PromptBuilder.BuiltPrompt p : built) {
            if (!p.targetId().equals(currentTarget)) {
                currentTarget = p.targetId();
                sb.append("\n### ").append(currentTarget).append(" — ").append(p.title()).append("\n\n");
                sb.append("| 任务 | 分辨率 | 落位 |\n");
                sb.append("|---|---|---|\n");
            }
            String txtName = p.outputFile().replaceAll("\\.(png|jpg|jpeg|webp)$", "") + ".txt";
            sb.append("| `").append(p.jobId()).append("` | ")
                    .append(p.width()).append("×").append(p.height()).append(" | ")
                    .append("[`").append(txtName).append("`](").append(p.targetId()).append('/').append(txtName)
                    .append(") → `").append(p.outputPath()).append("` |\n");
        }

        sb.append("\n").append(SPLIT).append("\n\n");
        sb.append("相关文档：\n\n");
        sb.append("- `docs/art/character-bible.md` —— 角色设定与立绘规格\n");
        sb.append("- `docs/art/pixel-pipeline.md` —— 像素管线与分辨率锁定\n");
        sb.append("- `docs/art/licensing.md` —— 许可与著作权风险\n");

        Files.writeString(outDir.resolve("README.md"), sb.toString(), StandardCharsets.UTF_8);
    }

    // ==================== 工具 ====================

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

    private ArtPromptExporter() {
    }
}
