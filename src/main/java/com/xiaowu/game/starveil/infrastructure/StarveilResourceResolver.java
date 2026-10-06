package com.xiaowu.game.starveil.infrastructure;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Starveil 标准资源路径解析器。
 *
 * <p>物理资源统一存放在 {@code assets/starveil/<type>/<sub>/<file>} 下，
 * 调用方使用命名空间写法：{@code starveil:<type>/<path>}。
 *
 * <p>类型与目录映射（子类目录）：
 * <ul>
 *   <li>贴图 textures（旧 images 自动映射）</li>
 *   <li>音频 sounds（旧 audio 自动映射）</li>
 *   <li>字体 fonts（旧 font 自动映射）</li>
 *   <li>数据 data</li>
 *   <li>国际化文案 lang</li>
 * </ul>
 *
 * <p>解析规则：
 * <ul>
 *   <li>省略文件路径时使用类型默认文件名，如 {@code starveil:textures} → {@code textures.png}，
 *       {@code starveil:fonts} → {@code fonts.ttf}，{@code starveil:sounds} → {@code sounds.mp3}</li>
 *   <li>路径中的子类目录（第一个目录段）可直接省略；若带子类查找失败，会自动忽略子类再查一次</li>
 *   <li>旧式 {@code /assets/...} 路径保持向后兼容</li>
 * </ul>
 *
 * <p>同名 meta 文件（{@code 文件路径.meta}，内容为 JSON）可为资源声明属性，
 * 例如动画帧信息，见 {@link com.xiaowu.game.starveil.infrastructure.AssetMeta}。
 */
public class StarveilResourceResolver {

    public static final String NAMESPACE = "starveil";
    public static final String ASSETS_ROOT = "/assets";

    public static final String TYPE_TEXTURES = "textures";
    public static final String TYPE_FONTS = "fonts";
    public static final String TYPE_SOUNDS = "sounds";
    public static final String TYPE_DATA = "data";
    /** 国际化文案（{@code assets/starveil/lang/<语言代码>.json}）。 */
    public static final String TYPE_LANG = "lang";

    private static final Gson gson = new Gson();

    /** 类型默认文件名（省略文件路径时使用） */
    private static final Map<String, String> DEFAULT_FILES = new HashMap<>();
    static {
        DEFAULT_FILES.put(TYPE_TEXTURES, "textures.png");
        DEFAULT_FILES.put(TYPE_FONTS, "fonts.ttf");
        DEFAULT_FILES.put(TYPE_SOUNDS, "sounds.mp3");
    }

    /** 旧类型名到新子类目录的映射 */
    private static final Map<String, String> TYPE_ALIASES = new HashMap<>();
    static {
        TYPE_ALIASES.put("images", TYPE_TEXTURES);
        TYPE_ALIASES.put("image", TYPE_TEXTURES);
        TYPE_ALIASES.put("texture", TYPE_TEXTURES);
        TYPE_ALIASES.put("audio", TYPE_SOUNDS);
        TYPE_ALIASES.put("sound", TYPE_SOUNDS);
        TYPE_ALIASES.put("music", TYPE_SOUNDS);
        TYPE_ALIASES.put("font", TYPE_FONTS);
    }

    private StarveilResourceResolver() {
    }

    /**
     * 判断传入引用是否是 Starveil 命名空间写法（形如 starveil:xxx/yyy）。
     */
    public static boolean isNamespaceReference(String path) {
        return path != null && path.startsWith(NAMESPACE + ":");
    }

    /**
     * 将任意资源引用解析为 classpath 绝对路径（以 / 开头）。
     *
     * <p>命名空间引用会映射到 {@code /assets/starveil/...}，旧式 {@code /assets/...}
     * 路径原样归一化返回。无法解析时返回 null。
     */
    public static String resolve(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (!isNamespaceReference(path)) {
            return normalizeLegacy(path);
        }
        String rest = path.substring(NAMESPACE.length() + 1);
        if (rest.isEmpty()) {
            return ASSETS_ROOT + "/" + NAMESPACE;
        }

        int slash = rest.indexOf('/');
        String type = slash < 0 ? rest : rest.substring(0, slash);
        String filePath = slash < 0 ? "" : rest.substring(slash + 1);

        String realType = normalizeType(type);
        if (realType == null) {
            warnUnknownType(path, type);
            return null;
        }

        if (filePath.isEmpty()) {
            String defaultFile = DEFAULT_FILES.get(realType);
            if (defaultFile == null) {
                return null;
            }
            filePath = defaultFile;
        }

        String base = ASSETS_ROOT + "/" + NAMESPACE + "/" + realType;
        String primary = base + "/" + filePath;
        if (exists(primary)) {
            return primary;
        }
        // 带子类目录时查找失败，忽略子类（第一个目录段）再尝试一次
        int subSlash = filePath.indexOf('/');
        if (subSlash > 0) {
            String plainFile = filePath.substring(subSlash + 1);
            String fallback = base + "/" + plainFile;
            if (exists(fallback)) {
                return fallback;
            }
        }
        return primary;
    }

    /**
     * 获取资源输入流。找不到时返回 null。
     */
    public static InputStream openStream(String path) {
        String resolved = resolve(path);
        return resolved == null ? null : ResourceResolver.getResourceAsStream(resolved);
    }

    /**
     * 获取资源 URL。找不到时返回 null。
     */
    public static URL getResource(String path) {
        String resolved = resolve(path);
        return resolved == null ? null : ResourceResolver.getResource(resolved);
    }

    /**
     * 判断资源是否存在（支持命名空间引用与旧式路径）。
     */
    public static boolean exists(String path) {
        String resolved = isNamespaceReference(path) ? resolve(path) : normalizeLegacy(path);
        if (resolved == null) {
            return false;
        }
        return ResourceResolver.getResource(resolved) != null;
    }

    /**
     * 获取某个类型的默认文件名（如 textures → textures.png）。
     */
    public static String getDefaultFile(String type) {
        return DEFAULT_FILES.get(normalizeType(type));
    }

    /**
     * 归一化类型名：新类型名原样返回，旧类型名映射到新子类目录。
     */
    public static String normalizeType(String type) {
        if (type == null) {
            return null;
        }
        String t = type.toLowerCase();
        if (DEFAULT_FILES.containsKey(t)
                || TYPE_TEXTURES.equals(t) || TYPE_FONTS.equals(t)
                || TYPE_SOUNDS.equals(t) || TYPE_DATA.equals(t)
                || TYPE_LANG.equals(t)) {
            return t;
        }
        return TYPE_ALIASES.get(t);
    }

    /**
     * 加载资源同名 meta 文件（JSON），不存在时返回 null。
     *
     * <p>meta 文件路径规则：资源路径 + ".meta"（如 {@code starveil:textures/foo.png.meta}）。
     * 也兼容 {@code .meta.json} 与 {@code .json} 后缀。
     */
    public static JsonObject loadMeta(String resourcePath) {
        String resolved = resolve(resourcePath);
        if (resolved == null) {
            return null;
        }
        String[] candidates = {
                resolved + ".meta",
                resolved + ".meta.json",
                resolved + ".json"
        };
        for (String candidate : candidates) {
            try (InputStream is = ResourceResolver.getResourceAsStream(candidate)) {
                if (is != null) {
                    JsonObject obj = JsonParser.parseString(new String(is.readAllBytes())).getAsJsonObject();
                    if (obj != null) {
                        return obj;
                    }
                }
            } catch (Exception e) {
                LoggerManager.Logger("WARNING", "解析 meta 文件失败: " + candidate + " - " + e.getMessage());
            }
        }
        return null;
    }

    private static String normalizeLegacy(String path) {
        if (path.startsWith(ASSETS_ROOT) || path.startsWith("/" + NAMESPACE)) {
            return path;
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /** 已经提醒过的错误引用（同一个写错的路径只吵一次，别刷屏）。 */
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 命名空间引用的第一段不是资源类型时的提示。
     *
     * <p>这是最常见的写法错误：把 {@code assets/starveil/} 下的<b>子目录</b>当成了类型，
     * 例如想引用 {@code assets/starveil/textures/character/normal/relaxed.png} 却写成
     * {@code starveil:character/normal/relaxed.png}（少了 {@code textures}）。
     * 解析会直接失败，报错只显示「加载失败」，很容易找不着北 —— 所以这里把原因说清楚。
     */
    private static void warnUnknownType(String path, String type) {
        if (WARNED.size() > 64 || !WARNED.add(path)) {
            return;
        }
        String hint = path.startsWith(NAMESPACE + ":" + type + "/")
                ? NAMESPACE + ":" + TYPE_TEXTURES + "/" + path.substring((NAMESPACE + ":" + type + "/").length())
                : NAMESPACE + ":" + TYPE_TEXTURES + "/…";
        LoggerManager.Logger("WARNING", "资源路径写错了: " + path
                + " —— 「" + type + "」不是资源类型，第一段必须是 "
                + TYPE_TEXTURES + " / " + TYPE_SOUNDS + " / " + TYPE_FONTS + " / "
                + TYPE_DATA + " / " + TYPE_LANG + "，并且要带文件后缀。"
                + "如果这是贴图，多半想写的是 " + hint);
    }
}
