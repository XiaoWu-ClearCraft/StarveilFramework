package com.xiaowu.game.starveil.infrastructure;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Starveil 标准资源路径解析器。
 *
 * <p>物理资源统一存放在 {@code assets/starveil/<type>/<sub>/<file>} 下。
 * <b>内容写相对路径就行</b>（{@code character/normal/relaxed.png}）：类型与后缀由框架推断，
 * 开发者不用记 textures / sounds 这类类型名，见 {@link #resolveRelative(String)}。
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
 * <p>三种写法，按优先级：
 * <ol>
 *   <li>显式命名空间 {@code starveil:<类型>/<路径>}（要精确控制或跨类型时用）；</li>
 *   <li>旧式 {@code /assets/...} 路径（向后兼容）；</li>
 *   <li>相对路径：在类型目录下挨个找（推荐，内容侧日常用这个）。</li>
 * </ol>
 *
 * <p>另有几个「类型已知」的入口（{@link #resolveTexture(String)} 等）：调用方知道这是贴图 /
 * 音频 / 字体时用它们，解析是确定的、不做跨类型搜索。
 *
 * <p>省略文件路径时使用类型默认文件名（{@code starveil:textures} → {@code textures.png} 等）；
 * 带子类目录查找失败时会自动忽略子类再尝试一次。
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

    // ==================== 命名空间（资源根） ====================

    /**
     * 命名空间 → 资源根目录。
     *
     * <p>{@code starveil:} 是<b>引擎与游戏内容</b>的命名空间，映射到 {@code /assets/starveil}；
     * 插件可以用自己的命名空间（{@code xxx:} → {@code /assets/xxx}），
     * 于是「谁带的资源」一目了然，也不会和游戏内容撞路径。
     *
     * <p>和「数据键」的命名空间规则一致：{@code starveil} 是<b>引擎保留</b>的，
     * 插件请注册自己的名字。
     */
    private static final Map<String, String> NAMESPACE_ROOTS = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        NAMESPACE_ROOTS.put(NAMESPACE, ASSETS_ROOT + "/" + NAMESPACE);
    }

    /** 命名空间名的合法写法（小写字母开头，可含数字、下划线、连字符）。 */
    private static final java.util.regex.Pattern NAMESPACE_NAME =
            java.util.regex.Pattern.compile("[a-z][a-z0-9_-]*");

    /**
     * 注册一个资源命名空间，根目录取默认的 {@code /assets/<名字>}。
     *
     * <pre>{@code
     * StarveilResourceResolver.registerNamespace("myplugin");
     * // 之后 "myplugin:textures/icon.png" → /assets/myplugin/textures/icon.png
     * }</pre>
     *
     * @return 是否注册成功（名字非法、或想占用 {@code starveil} 时返回 false）
     */
    public static boolean registerNamespace(String name) {
        return registerNamespace(name, null);
    }

    /**
     * 注册一个资源命名空间并指定根目录（比如插件把资源放在自己的子目录下）。
     *
     * @param name 命名空间名，见 {@link #NAMESPACE_NAME}；{@code starveil} 为引擎保留
     * @param root 根目录；传 null / 空表示默认的 {@code /assets/<名字>}
     */
    public static boolean registerNamespace(String name, String root) {
        if (name == null || !NAMESPACE_NAME.matcher(name).matches()) {
            LoggerManager.Logger("WARNING", "命名空间名不合法（要小写字母开头、只含字母数字下划线连字符）: " + name);
            return false;
        }
        if (NAMESPACE.equals(name)) {
            LoggerManager.Logger("WARNING", "命名空间 '" + NAMESPACE + "' 是引擎保留的，"
                    + "插件请注册自己的名字（例如插件 id）");
            return false;
        }
        String realRoot = (root == null || root.trim().isEmpty())
                ? ASSETS_ROOT + "/" + name
                : normalizeRoot(root.trim());
        NAMESPACE_ROOTS.put(name, realRoot);
        LoggerManager.Logger("INFO", "已注册资源命名空间: " + name + ": → " + realRoot);
        return true;
    }

    /** 注销命名空间（引擎保留的那个不给注销）。 */
    public static boolean unregisterNamespace(String name) {
        if (name == null || NAMESPACE.equals(name)) {
            return false;
        }
        return NAMESPACE_ROOTS.remove(name) != null;
    }

    /** 已注册的命名空间名（含引擎的 {@code starveil}）。 */
    public static java.util.Set<String> namespaces() {
        return java.util.Set.copyOf(NAMESPACE_ROOTS.keySet());
    }

    /** 命名空间对应的根目录；未注册返回 null。 */
    public static String namespaceRoot(String name) {
        return name == null ? null : NAMESPACE_ROOTS.get(name);
    }

    private static String normalizeRoot(String root) {
        String r = root.startsWith("/") ? root : "/" + root;
        return r.endsWith("/") ? r.substring(0, r.length() - 1) : r;
    }

    /**
     * 判断传入引用是否是命名空间写法（形如 {@code xxx:yyy/zzz}）。
     *
     * <p>只看形状不看是否已注册 —— 未注册的命名空间会在解析时报错并提示，
     * 免得悄悄退回「当普通路径处理」而看不出问题。{@code http://…} 这类带斜杠的
     * 前缀不算命名空间。
     */
    public static boolean isNamespaceReference(String path) {
        if (path == null) {
            return false;
        }
        int colon = path.indexOf(':');
        if (colon <= 0 || colon == path.length() - 1) {
            return false;
        }
        if (path.charAt(colon + 1) == '/') {
            return false;   // http:// 之类
        }
        return NAMESPACE_NAME.matcher(path.substring(0, colon)).matches();
    }

    /** 取出命名空间名；不是命名空间写法时返回 null。 */
    public static String namespaceOf(String path) {
        if (!isNamespaceReference(path)) {
            return null;
        }
        return path.substring(0, path.indexOf(':'));
    }

    /**
     * 将任意资源引用解析为 classpath 绝对路径（以 / 开头）。
     *
     * <p>命名空间引用会映射到该命名空间的根目录（{@code starveil:} → {@code /assets/starveil}，
     * 插件命名空间同理），旧式 {@code /assets/...} 路径原样归一化返回。无法解析时返回 null。
     */
    public static String resolve(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (!isNamespaceReference(path)) {
            return normalizeLegacy(path);
        }
        int colon = path.indexOf(':');
        String namespace = path.substring(0, colon);
        String root = NAMESPACE_ROOTS.get(namespace);
        if (root == null) {
            LoggerManager.Logger("WARNING", "资源命名空间未注册: " + namespace + "（" + path + "）"
                    + " —— 已注册的有 " + namespaces()
                    + "；插件应当在加载时 registerNamespace(\"自己的名字\")");
            return null;
        }
        String rest = path.substring(colon + 1);
        if (rest.isEmpty()) {
            return root;
        }
        // 第二段是类型（textures/sounds/…）时走类型规则；不是就当命名空间根下的普通路径
        int slash = rest.indexOf('/');
        String type = slash < 0 ? rest : rest.substring(0, slash);
        String filePath = slash < 0 ? "" : rest.substring(slash + 1);
        String realType = normalizeType(type);
        if (realType == null) {
            return resolveInsideRoot(root, namespace, rest, type);
        }
        if (filePath.isEmpty()) {
            String defaultFile = DEFAULT_FILES.get(realType);
            if (defaultFile == null) {
                return root + "/" + realType;
            }
            filePath = defaultFile;
        }
        String base = root + "/" + realType;
        String primary = base + "/" + filePath;
        if (existsResolved(primary)) {
            return primary;
        }
        // 带子类目录时查找失败，忽略子类（第一个目录段）再尝试一次
        int subSlash = filePath.indexOf('/');
        if (subSlash > 0) {
            String plainFile = filePath.substring(subSlash + 1);
            String fallback = base + "/" + plainFile;
            if (existsResolved(fallback)) {
                return fallback;
            }
        }
        return primary;
    }

    /**
     * 命名空间里第二段不是已知类型时的处理：当作根目录下的普通路径
     * （插件可以有自己的目录结构）；确实找不到时提醒正确写法。
     */
    private static String resolveInsideRoot(String root, String namespace, String rest, String type) {
        String literal = root + "/" + rest;
        if (existsResolved(literal)) {
            return literal;
        }
        warnUnknownType(namespace, rest, type, literal);
        return literal;
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

    // ==================== 类型已知时的解析 ====================

    /** 没写后缀时按类型试这些后缀（只在该文件确实存在时才采用）。 */
    private static final Map<String, List<String>> EXTENSION_CANDIDATES = new HashMap<>();
    static {
        EXTENSION_CANDIDATES.put(TYPE_TEXTURES, List.of(".png", ".jpg", ".jpeg", ".webp"));
        EXTENSION_CANDIDATES.put(TYPE_SOUNDS, List.of(".mp3", ".ogg", ".wav", ".m4a"));
        EXTENSION_CANDIDATES.put(TYPE_FONTS, List.of(".ttf", ".otf"));
        EXTENSION_CANDIDATES.put(TYPE_DATA, List.of(".json"));
        EXTENSION_CANDIDATES.put(TYPE_LANG, List.of(".json"));
    }

    /** 贴图：{@code character/normal/relaxed.png} → {@code /assets/starveil/textures/character/normal/relaxed.png}。 */
    public static String resolveTexture(String path) {
        return resolveAs(TYPE_TEXTURES, path);
    }

    /** 音频。 */
    public static String resolveSound(String path) {
        return resolveAs(TYPE_SOUNDS, path);
    }

    /** 字体。 */
    public static String resolveFont(String path) {
        return resolveAs(TYPE_FONTS, path);
    }

    /** 数据文件。 */
    public static String resolveData(String path) {
        return resolveAs(TYPE_DATA, path);
    }

    /**
     * <b>调用方知道类型时</b>用这个 —— 内容只需要写「类型目录下面的路径」，
     * 类型由框架按用途补上。
     *
     * <pre>{@code
     * // 立绘：assets/starveil/textures/character/normal/relaxed.png
     * ChatManager.setStandee("character/normal/relaxed.png");   // 类型 textures 自动补
     * }</pre>
     *
     * <p>规则：
     * <ol>
     *   <li>已经是显式引用（{@code starveil:...}、以 {@code /} 开头、或以 {@code assets/} 开头）
     *       时原样解析 —— 显式写法永远优先，需要跨类型或旧式路径时仍然可用；</li>
     *   <li>否则按 {@code /assets/starveil/<type>/<path>} 解释；</li>
     *   <li>没写后缀时按类型试常见后缀（贴图 .png/.jpg/…、音频 .mp3/…、字体 .ttf/…、
     *       数据 .json），<b>找到才用</b>，所以写不写后缀都行。</li>
     * </ol>
     *
     * @param type 资源类型，见 {@link #TYPE_TEXTURES} 等
     * @param path 类型目录下的相对路径；也可以是显式引用
     * @return classpath 绝对路径；解析不出来时返回 null
     */
    public static String resolveAs(String type, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String realType = normalizeType(type);
        if (realType == null) {
            // 类型不认识：当普通引用处理，走原来的规则（并在需要时报错提示）
            return resolve(path);
        }
        if (isExplicitReference(path)) {
            return resolve(path);
        }
        String candidate = resolve(NAMESPACE + ":" + realType + "/" + path);
        if (existsResolved(candidate)) {
            return candidate;
        }
        if (!hasFileExtension(path)) {
            for (String ext : EXTENSION_CANDIDATES.getOrDefault(realType, List.of())) {
                String withExt = resolve(NAMESPACE + ":" + realType + "/" + path + ext);
                if (existsResolved(withExt)) {
                    LoggerManager.Logger("DEBUG", "资源路径没写后缀，按类型补上了: "
                            + path + " → " + withExt);
                    return withExt;
                }
            }
        }
        // 找不到也把按规则算出来的路径返回：报错信息里能看到它，便于对照
        return candidate;
    }

    /** 是否已经是显式引用（命名空间 / 绝对 / 旧式 assets 路径）。 */
    public static boolean isExplicitReference(String path) {
        return isNamespaceReference(path)
                || path.startsWith("/")
                || path.startsWith("assets/");
    }

    // ==================== 类型未知时的推断（相对路径直接用） ====================

    /** 相对路径的搜索顺序：内容写下相对路径时按这个顺序找类型目录。 */
    private static final List<String> TYPE_SEARCH_ORDER =
            List.of(TYPE_TEXTURES, TYPE_SOUNDS, TYPE_FONTS, TYPE_DATA, TYPE_LANG);

    /** 相对路径 → classpath 路径；找不到时缓存空串，避免每次都翻一遍。 */
    private static final Map<String, String> RELATIVE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 类型未知时的解析：把「相对路径」在 {@code assets/starveil/<类型>/} 下挨个找一遍。
     *
     * <p>内容不该为了引用一张图去记「贴图叫 textures、音频叫 sounds」——
     * 直接写 {@code character/normal/relaxed.png} 就行，类型由框架推断
     * （按 {@link #TYPE_TEXTURES} → sounds → fonts → data → lang 的顺序，
     * 命中即止）。没写文件后缀时也会按类型试常见后缀。
     *
     * <p>显式引用（{@code starveil:...} / {@code /assets/...}）不走这里：那是明确指定，
     * 需要跨类型或精确控制时用。找不到时返回 {@code null}（调用方退回原路径处理）。
     */
    public static String resolveRelative(String path) {
        if (path == null || path.isEmpty() || isExplicitReference(path)) {
            return null;
        }
        String cached = RELATIVE_CACHE.get(path);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String found = searchTypeDirectories(path);
        RELATIVE_CACHE.put(path, found == null ? "" : found);
        return found;
    }

    private static String searchTypeDirectories(String path) {
        boolean hasExtension = hasFileExtension(path);
        for (String type : TYPE_SEARCH_ORDER) {
            String base = ASSETS_ROOT + "/" + NAMESPACE + "/" + type + "/" + path;
            if (existsResolved(base)) {
                LoggerManager.Logger("DEBUG", "资源相对路径已推断类型: "
                        + path + " → " + base);
                return base;
            }
            if (hasExtension) {
                continue;
            }
            for (String ext : EXTENSION_CANDIDATES.getOrDefault(type, List.of())) {
                if (existsResolved(base + ext)) {
                    LoggerManager.Logger("DEBUG", "资源相对路径已推断类型与后缀: "
                            + path + " → " + base + ext);
                    return base + ext;
                }
            }
        }
        return null;
    }

    /** 最后一段里有没有后缀（只看文件名，目录名里的点不算）。 */
    private static boolean hasFileExtension(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash < 0 ? path : path.substring(slash + 1);
        return file.lastIndexOf('.') > 0;
    }

    private static boolean existsResolved(String classpathPath) {
        return classpathPath != null && ResourceResolver.getResource(classpathPath) != null;
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
     * 命名空间里第二段不是资源类型时的提示。
     *
     * <p>这是最常见的写法错误：把 {@code assets/starveil/} 下的<b>子目录</b>当成了类型，
     * 例如想引用 {@code assets/starveil/textures/character/normal/relaxed.png} 却写成
     * {@code starveil:character/normal/relaxed.png}（类型段写成了子目录）。
     */
    private static void warnUnknownType(String namespace, String rest, String type, String tried) {
        String key = namespace + ":" + rest;
        if (WARNED.size() > 64 || !WARNED.add(key)) {
            return;
        }
        LoggerManager.Logger("WARNING", "资源路径的第二段「" + type + "」不是资源类型（"
                + namespace + ":" + rest + "）。类型是 "
                + TYPE_TEXTURES + " / " + TYPE_SOUNDS + " / " + TYPE_FONTS + " / "
                + TYPE_DATA + " / " + TYPE_LANG + "；"
                + "如果这是贴图，正确写法是 " + namespace + ":" + TYPE_TEXTURES + "/" + rest
                + "，或者干脆写相对路径 " + rest + "（类型由框架推断）。"
                + "（自定义目录结构也允许，但得真的存在：" + tried + " 没找到）");
    }
}
