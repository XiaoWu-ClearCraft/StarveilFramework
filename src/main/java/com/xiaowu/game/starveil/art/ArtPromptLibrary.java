package com.xiaowu.game.starveil.art;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 美术提示词库：加载 {@code art/} 目录下的 JSON 数据。
 *
 * <p>数据与代码完全分离 —— 增删角色、场景、表情、压缩包参数都只改 JSON，
 * 不需要碰这个类。
 *
 * <p>目录结构：
 * <pre>
 * art/
 *   presets.json            质量词 / 负面词 / 模型参数 / 后处理
 *   scenes.json             场景清单
 *   targets.json            五类资产目标的出图配方
 *   characters/*.json       每个角色一份
 * </pre>
 */
public final class ArtPromptLibrary {

    private final Path artDir;
    private final JsonObject presets;
    private final Map<String, JsonObject> characters;
    private final Map<String, JsonObject> scenes;
    private final JsonObject targets;

    private ArtPromptLibrary(Path artDir,
                             JsonObject presets,
                             Map<String, JsonObject> characters,
                             Map<String, JsonObject> scenes,
                             JsonObject targets) {
        this.artDir = artDir;
        this.presets = presets;
        this.characters = characters;
        this.scenes = scenes;
        this.targets = targets;
    }

    // ==================== 加载 ====================

    public static ArtPromptLibrary load(Path artDir) throws IOException {
        if (!Files.isDirectory(artDir)) {
            throw new IOException("美术数据目录不存在: " + artDir.toAbsolutePath());
        }

        JsonObject presets = readObject(artDir.resolve("presets.json"));
        JsonObject targets = readObject(artDir.resolve("targets.json"));

        JsonObject scenesRoot = readObject(artDir.resolve("scenes.json"));
        Map<String, JsonObject> scenes = new LinkedHashMap<>();
        JsonObject sceneMap = scenesRoot.has("scenes") ? scenesRoot.getAsJsonObject("scenes") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : sceneMap.entrySet()) {
            scenes.put(e.getKey(), e.getValue().getAsJsonObject());
        }

        Map<String, JsonObject> characters = new LinkedHashMap<>();
        Path charDir = artDir.resolve("characters");
        if (Files.isDirectory(charDir)) {
            List<Path> files = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(charDir, "*.json")) {
                ds.forEach(files::add);
            }
            // 排序保证输出顺序稳定（否则每次构建的 manifest 顺序会变，diff 很吵）
            files.sort(Comparator.comparing(p -> p.getFileName().toString()));
            for (Path f : files) {
                JsonObject c = readObject(f);
                characters.put(requireString(c, "id"), c);
            }
        }

        return new ArtPromptLibrary(artDir, presets, characters, scenes, targets);
    }

    // ==================== 访问器 ====================

    public Path artDir() {
        return artDir;
    }

    public Set<String> characterIds() {
        return characters.keySet();
    }

    public JsonObject character(String id) {
        JsonObject c = characters.get(id);
        if (c == null) {
            throw new IllegalArgumentException("未定义的角色: " + id
                    + "（已定义: " + characters.keySet() + "）");
        }
        return c;
    }

    public Set<String> sceneIds() {
        return scenes.keySet();
    }

    public JsonObject scene(String id) {
        JsonObject s = scenes.get(id);
        if (s == null) {
            throw new IllegalArgumentException("未定义的场景: " + id
                    + "（已定义: " + scenes.keySet() + "）");
        }
        return s;
    }

    public Set<String> targetIds() {
        return targetMap().keySet();
    }

    public JsonObject target(String id) {
        JsonObject t = targetMap().getAsJsonObject(id);
        if (t == null) {
            throw new IllegalArgumentException("未定义的目标: " + id
                    + "（已定义: " + targetMap().keySet() + "）");
        }
        return t;
    }

    private JsonObject targetMap() {
        return targets.getAsJsonObject("targets");
    }

    // ==================== presets 查询 ====================

    public JsonObject model(String id) {
        JsonObject models = presets.getAsJsonObject("models");
        JsonObject m = models == null ? null : models.getAsJsonObject(id);
        if (m == null) {
            throw new IllegalArgumentException("未定义的模型: " + id);
        }
        return m;
    }

    public List<String> positivePreset(String id) {
        return presetTags("positive", id);
    }

    public List<String> negativePreset(String id) {
        return presetTags("negative", id);
    }

    public List<String> postProcessSteps(String id) {
        if (id == null || id.isEmpty()) {
            return List.of();
        }
        return presetTags("postProcess", id);
    }

    private List<String> presetTags(String section, String id) {
        JsonObject sec = presets.getAsJsonObject(section);
        JsonElement el = sec == null ? null : sec.get(id);
        if (el == null) {
            throw new IllegalArgumentException("presets.json 的 " + section + " 中没有: " + id);
        }
        if (el.isJsonArray()) {
            return stringList(sec, id);
        }
        // 允许写成对象形式 {"positive": [...], "negative": [...]}
        if (el.isJsonObject() && el.getAsJsonObject().has("tags")) {
            return stringList(el.getAsJsonObject(), "tags");
        }
        throw new IllegalArgumentException("presets.json 的 " + section + "." + id + " 格式无法识别");
    }

    // ==================== JSON 取值工具 ====================

    public static JsonObject readObject(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(r);
            if (el == null || !el.isJsonObject()) {
                throw new IOException("期望一个 JSON 对象: " + path);
            }
            return el.getAsJsonObject();
        } catch (com.google.gson.JsonParseException e) {
            throw new IOException("JSON 解析失败: " + path + " —— " + e.getMessage(), e);
        }
    }

    public static String requireString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            throw new IllegalStateException("缺少必填字段 \"" + key + "\"");
        }
        return o.get(key).getAsString();
    }

    /** 读取字符串数组；字段不存在或类型不符时返回空列表而不是抛异常。 */
    public static List<String> stringList(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o == null || !o.has(key)) {
            return out;
        }
        JsonElement el = o.get(key);
        if (!el.isJsonArray()) {
            return out;
        }
        for (JsonElement e : el.getAsJsonArray()) {
            if (!e.isJsonNull()) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    /** 读取 [width, height]；缺失或格式不符时返回 null。 */
    public static int[] intPair(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonArray()) {
            return null;
        }
        var arr = o.getAsJsonArray(key);
        if (arr.size() != 2) {
            return null;
        }
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()};
    }

    /** 读取一个字符串字段；缺失或为 null 时返回 fallback。 */
    public static String optString(JsonObject o, String key, String fallback) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) {
            return fallback;
        }
        return o.get(key).getAsString();
    }
}
