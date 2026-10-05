package com.xiaowu.game.starveil.infrastructure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 内容配置 —— 由游戏内容在 {@code com.xiaowu.game.starveil.content.init.init}
 * 里设定，用来替换框架自带的默认外观资源。
 *
 * <p><b>为什么需要这一层</b>：框架代码里到处写着
 * {@code "starveil:fonts/xiaolai-sc-regular.ttf"} 这种路径（20 多处）。
 * 那些路径属于「框架自带的默认值」，不该成为内容的硬性约束 ——
 * 换一套字体、换一张主菜单背景，不应该去改框架源码。
 *
 * <p><b>实现方式</b>：这里的 setter 除了记录新值，还会往
 * {@link #redirect} 表里登记一条「默认路径 → 新路径」的映射。
 * {@link ResourceResolver} 是所有资源读取的唯一入口，它会在解析前查这张表。
 * 于是框架里那几十处硬编码路径<b>一行都不用改</b>，内容侧换资源就会全局生效。
 *
 * <p>用法（在 {@code content.init.init()} 里）：
 * <pre>
 *   ContentConfig.setBodyFont("starveil:fonts/my-body.ttf");
 *   ContentConfig.setMenuBackground("starveil:textures/backgrounds/my-menu.png");
 *   ContentConfig.setPrimaryColor("#7FD4FF");
 * </pre>
 *
 * <p>不设置时一切保持框架默认，因此「不装内容」也能跑起来（虽然会被
 * {@code GameContentInit.requireContent()} 拦下，见那里的说明）。
 */
public final class ContentConfig {

    // ==================== 框架默认值 ====================

    public static final String DEFAULT_BODY_FONT = "starveil:fonts/xiaolai-sc-regular.ttf";
    public static final String DEFAULT_TITLE_FONT = "starveil:fonts/zhengjing.ttf";
    public static final String DEFAULT_DECOR_FONT = "starveil:fonts/handwriting.ttf";
    public static final String DEFAULT_APP_ICON = "starveil:textures/icons/app-icon.png";
    public static final String DEFAULT_MENU_BACKGROUND = "starveil:textures/backgrounds/main-menu.png";
    public static final String DEFAULT_MENU_MUSIC = "starveil:sounds/music/dream.mp3";

    /**
     * 游戏名默认值。
     *
     * <p>框架是通用的：在内容声明自己叫什么之前，它只知道自己在跑一个「游戏」。
     * 内容应当在自己的 {@code init} 里 {@code ContentConfig.setGameName("……")}。
     */
    public static final String DEFAULT_GAME_NAME = "My Game";

    // 主题色默认值：直接引用 GameConstants，保持单一事实源。
    // GameConstants 只依赖 java.io/nio，因此 infrastructure → config 不构成包循环。
    public static final String DEFAULT_PRIMARY_COLOR =
            com.xiaowu.game.starveil.config.GameConstants.PINK_COLOR_HEX;
    public static final String DEFAULT_SECONDARY_COLOR =
            com.xiaowu.game.starveil.config.GameConstants.DEEP_PINK_COLOR_HEX;
    public static final String DEFAULT_TERTIARY_COLOR =
            com.xiaowu.game.starveil.config.GameConstants.LIGHT_PINK_COLOR_HEX;

    private ContentConfig() {
    }

    // ==================== 当前值 ====================

    /*
     * 框架【不提供】任何默认外观资源。
     *
     * 字体、菜单背景、菜单音乐、窗口图标全部为 null，含义是「内容没有指定」——
     * 此时框架应当什么都不做：字体回退系统字体、背景纯黑、不播音乐、用系统默认图标。
     * 如果这里给个框架自带的默认路径，框架就会去加载一个「本来就不该由框架提供」的文件，
     * 表现就是无内容启动时满屏「找不到资源」的报错。
     */
    private static volatile String bodyFont = null;
    private static volatile String titleFont = null;
    private static volatile String decorFont = null;
    private static volatile String appIcon = null;
    private static volatile String menuBackground = null;
    private static volatile String menuMusic = null;

    private static volatile String primaryColor = DEFAULT_PRIMARY_COLOR;
    private static volatile String secondaryColor = DEFAULT_SECONDARY_COLOR;
    private static volatile String tertiaryColor = DEFAULT_TERTIARY_COLOR;

    /**
     * 游戏名。
     *
     * <p>框架<b>不</b>知道自己被用来做哪个游戏，所以默认是通用的 {@value #DEFAULT_GAME_NAME}；
     * 内容在 {@code init} 里用 {@link #setGameName(String)} 覆盖它。
     * 它出现在：窗口标题、{@code {TITLE}} / {@code {window.title}} 占位符、
     * 以及 {@code s.dialog(...)} 的说话人（旁白）位置。
     */
    private static volatile String gameName = null;

    /**
     * 窗口标题（整串）。
     *
     * <p>为 {@code null}（默认）时由框架按界面拼：主菜单是「游戏名 - 主菜单」，
     * 游戏内就是游戏名。内容想完全自定义标题栏就显式设它。
     */
    private static volatile String windowTitle = null;

    /** 默认路径 → 内容指定的路径。 */
    private static final Map<String, String> REDIRECTS = new ConcurrentHashMap<>();

    // ==================== 游戏名 / 窗口标题 ====================

    /** 设置游戏名（默认 {@value #DEFAULT_GAME_NAME}）。 */
    public static void setGameName(String name) {
        if (name == null || name.trim().isEmpty()) {
            Logger("WARNING", "游戏名为空，保持默认: " + DEFAULT_GAME_NAME);
            gameName = null;
            return;
        }
        gameName = name.trim();
        Logger("INFO", "游戏名已设为: " + gameName);
    }

    /** 游戏名（内容没设过就是默认值）。 */
    public static String gameName() {
        return gameName != null ? gameName : DEFAULT_GAME_NAME;
    }

    /**
     * 设置窗口标题（整串）。不设则按界面自动拼，见 {@link #windowTitle()}。
     */
    public static void setWindowTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            windowTitle = null;
            Logger("INFO", "窗口标题未指定，将按「游戏名 - 界面」自动拼接");
            return;
        }
        windowTitle = title.trim();
        Logger("INFO", "窗口标题已设为: " + windowTitle);
    }

    /**
     * 窗口标题基数：内容设过就用它，否则用游戏名。
     *
     * <p>「基数」的意思是框架还会往后接界面名（如「 - 主菜单」）。
     * 想连界面名一起自定义，请用 {@link #setWindowTitle(String)} 之外的
     * {@code GameConstants.windowTitleFor(subtitle)}（内容一般用不到）。
     */
    public static String windowTitle() {
        return windowTitle != null ? windowTitle : gameName();
    }

    /** 内容是否显式指定了窗口标题。 */
    public static boolean hasWindowTitle() {
        return windowTitle != null;
    }

    // ==================== 联网相关 ====================

    /** 断网时是否强制弹窗提示（默认关闭）。 */
    private static volatile boolean offlinePromptEnabled = false;

    /** 跳过断网提示的特殊键（KeyCode 名，默认 F8）。 */
    private static volatile String offlineBypassKey = "F8";

    /**
     * 开启「断网时强制提示」。
     *
     * <p>默认<b>关闭</b>：框架不替内容决定「没网能不能玩」。开启后，一旦探测到断网就会
     * 弹一个盖住整个画面的提示，联网后自动消失；不提供「知道了」按钮（见
     * {@code OfflinePrompt} 的说明）。留了一个特殊键作为逃生口 ——
     * 网络判断会有假阴性，不能把玩家永久关在门外。
     */
    public static void setOfflinePromptEnabled(boolean enabled) {
        offlinePromptEnabled = enabled;
        Logger("INFO", "断网提示: " + (enabled ? "开启" : "关闭"));
    }

    public static boolean offlinePromptEnabled() {
        return offlinePromptEnabled;
    }

    /**
     * 设置跳过断网提示的特殊键（{@code KeyCode} 的名字，如 {@code "F8"}）。
     * 按一次即本次会话不再提示；恢复联网后再断开仍会提示。
     */
    public static void setOfflineBypassKey(String keyCodeName) {
        if (keyCodeName == null || keyCodeName.trim().isEmpty()) {
            offlineBypassKey = "F8";
            return;
        }
        offlineBypassKey = keyCodeName.trim().toUpperCase();
        Logger("INFO", "断网提示的跳过键: " + offlineBypassKey);
    }

    public static String offlineBypassKey() {
        return offlineBypassKey;
    }

    /**
     * 设置「是否联网」的探测端点，格式 {@code "主机:端口"}。
     *
     * <p>不设置则用框架默认（公网 DNS 的 443 端口，见 {@code NetworkStatus}）。
     * 判断规则是「任意一个能连上就算在线」，所以多写几个更稳。
     */
    public static void setNetworkEndpoints(String... hostPorts) {
        com.xiaowu.game.starveil.infrastructure.net.NetworkStatus.setEndpoints(hostPorts);
    }

    /** 设置「可信时间」的取时地址（HTTP Date 头）。不设置则用框架默认。 */
    public static void setTrustedTimeUrls(String... urls) {
        com.xiaowu.game.starveil.infrastructure.net.TrustedTime.setUrls(urls);
    }

    /**
     * 设置「IP 属地」查询接口。
     *
     * <p>不设置则用框架默认的几个（国内可达的优先）。响应按 UTF-8 解析；
     * 需要别的编码请直接改 {@code IpLocation} 的默认表。
     */
    public static void setIpLocationUrls(String... urls) {
        com.xiaowu.game.starveil.infrastructure.net.IpLocation.setUrls(urls);
    }

    // ==================== 字体 ====================

    /** 正文字体（界面里绝大多数文字）。 */
    public static void setBodyFont(String path) {
        bodyFont = assign(path, DEFAULT_BODY_FONT, "正文字体");
    }

    /** 标题字体。 */
    public static void setTitleFont(String path) {
        titleFont = assign(path, DEFAULT_TITLE_FONT, "标题字体");
    }

    /** 装饰 / 手写字体。 */
    public static void setDecorFont(String path) {
        decorFont = assign(path, DEFAULT_DECOR_FONT, "装饰字体");
    }

    public static String bodyFont() {
        return bodyFont;
    }

    public static String titleFont() {
        return titleFont;
    }

    public static String decorFont() {
        return decorFont;
    }

    // ==================== 菜单 / 图标 ====================

    public static void setMenuBackground(String path) {
        menuBackground = assign(path, DEFAULT_MENU_BACKGROUND, "主菜单背景");
    }

    public static void setMenuMusic(String path) {
        menuMusic = assign(path, DEFAULT_MENU_MUSIC, "主菜单音乐");
    }

    /**
     * 设置窗口图标。
     *
     * <p>框架<b>不</b>自带默认图标：未设置时窗口用系统默认图标（Windows 默认），
     * 而不是去找一个框架里并不存在的文件。
     */
    public static void setAppIcon(String path) {
        appIcon = assign(path, DEFAULT_APP_ICON, "应用图标");
    }

    public static String menuBackground() {
        return menuBackground;
    }

    public static String menuMusic() {
        return menuMusic;
    }

    /** 窗口图标路径；内容未提供时为 null —— 调用方应改用系统默认图标。 */
    public static String appIcon() {
        return appIcon;
    }

    public static boolean hasAppIcon() {
        return appIcon != null && !appIcon.isEmpty();
    }

    // ==================== 主题色 ====================

    public static void setPrimaryColor(String hex) {
        primaryColor = validColor(hex, DEFAULT_PRIMARY_COLOR, "主色");
    }

    public static void setSecondaryColor(String hex) {
        secondaryColor = validColor(hex, DEFAULT_SECONDARY_COLOR, "副色");
    }

    public static void setTertiaryColor(String hex) {
        tertiaryColor = validColor(hex, DEFAULT_TERTIARY_COLOR, "三级色");
    }

    public static String primaryColor() {
        return primaryColor;
    }

    public static String secondaryColor() {
        return secondaryColor;
    }

    public static String tertiaryColor() {
        return tertiaryColor;
    }

    // ==================== 通用资源重定向 ====================

    /**
     * 把框架引用的某个资源重定向到另一个路径。
     *
     * <p>给「上面那些具名 setter 没覆盖到的资源」留的通用出口，
     * 例如某个 UI 里写死的贴图。{@code from} 必须写框架里原本引用的那个路径。
     */
    public static void redirect(String from, String to) {
        if (from == null || from.trim().isEmpty() || to == null || to.trim().isEmpty()) {
            Logger("WARNING", "资源重定向参数无效: " + from + " → " + to);
            return;
        }
        REDIRECTS.put(from.trim(), to.trim());
        Logger("INFO", "资源已重定向: " + from.trim() + " → " + to.trim());
    }

    /** 查询重定向目标；没有登记则原样返回。ResourceResolver 调用此方法。 */
    public static String redirectOf(String path) {
        if (path == null || REDIRECTS.isEmpty()) {
            return path;
        }
        String target = REDIRECTS.get(path);
        return target != null ? target : path;
    }

    public static Map<String, String> redirects() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(REDIRECTS));
    }

    // ==================== 语言列表 ====================

    /**
     * 内容提供的可选语言（语言代码 → 显示名）。
     *
     * <p>框架自带的语言文件只有 {@code lang/zh_cn.json} 一份，它不负责决定
     * 「这款游戏支持哪些语言」—— 那是内容的事。内容不提供时这张表为空，
     * 设置界面就<b>不显示</b>语言切换，而不是显示一个只有一项的下拉框。
     */
    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    /**
     * 登记一个可选语言。
     *
     * @param code        语言代码，如 {@code zh_cn}、{@code en_us}
     * @param displayName 设置界面里显示的名字，如「简体中文」
     */
    public static void addLanguage(String code, String displayName) {
        if (code == null || code.trim().isEmpty()) {
            Logger("WARNING", "语言代码为空，已忽略");
            return;
        }
        String c = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        LANGUAGES.put(c, displayName == null || displayName.trim().isEmpty() ? c : displayName.trim());
        Logger("INFO", "已登记可选语言: " + c + "（" + LANGUAGES.get(c) + "）");
    }

    /** 可选语言（只读，保持登记顺序）。 */
    public static Map<String, String> availableLanguages() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(LANGUAGES));
    }

    /** 内容是否提供了语言列表 —— 为空时设置界面不显示语言切换。 */
    public static boolean hasLanguages() {
        return !LANGUAGES.isEmpty();
    }

    // ==================== 数据文件密钥 ====================

    /**
     * 指定数据文件的加密密钥与 IV。
     *
     * <p>不指定时用框架默认值（见 {@code FileCrypto.DEFAULT_KEY}）。
     * 指定自己的密钥后，本游戏的数据文件与其它基于本框架的游戏互不可读 ——
     * 否则任何人拿到框架源码就能解开所有游戏的存档。
     *
     * <p><b>两者都必须是 16 字节</b>（AES-128，UTF-8 编码后计）。长度不对会记录
     * ERROR 并保持默认值，不会中断启动。
     *
     * <pre>
     *   // 在 content.init.init() 里
     *   ContentConfig.setDataCryptoKey("MyGame_SecretKey", null);   // 只换密钥，IV 用默认
     *   ContentConfig.setDataCryptoKey("MyGame_SecretKey", "MyGame_InitVec16");
     * </pre>
     *
     * <p><b>换密钥会作废已有存档</b>：旧文件解不开，需要删掉重来。
     *
     * @param key 16 字节密钥；{@code null} 或空串表示不修改
     * @param iv  16 字节 IV；{@code null} 或空串表示不修改
     * @return 是否应用成功
     */
    public static boolean setDataCryptoKey(String key, String iv) {
        return com.xiaowu.game.starveil.infrastructure.persistence.FileCrypto
                .configure(key, iv);
    }

    // ==================== 内部 ====================

    /** 记录新值，并登记「默认路径 → 新路径」的重定向。空路径 = 清空（视为未指定）。 */
    private static String assign(String path, String defaultValue, String what) {
        if (path == null || path.trim().isEmpty()) {
            Logger("WARNING", what + "路径为空，视为未指定（框架不会去加载任何默认文件）");
            return null;
        }
        String v = path.trim();
        REDIRECTS.put(defaultValue, v);
        Logger("INFO", what + "已设为: " + v);
        return v;
    }

    private static String validColor(String hex, String fallback, String what) {
        if (hex == null || !hex.trim().matches("#[0-9a-fA-F]{6,8}")) {
            Logger("WARNING", what + "不是合法的 #RRGGBB 颜色，保持默认: " + fallback);
            return fallback;
        }
        return hex.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 仅测试使用：恢复到框架默认（即「什么都没有」）。
     *
     * <p>公开是为了让其它包的测试也能重置这一层静态状态 ——
     * 语言相关逻辑（{@code infrastructure.i18n}）需要它来保证用例之间互不影响。
     */
    public static void resetForTest() {
        bodyFont = null;
        titleFont = null;
        decorFont = null;
        appIcon = null;
        menuBackground = null;
        menuMusic = null;
        gameName = null;
        windowTitle = null;
        offlinePromptEnabled = false;
        offlineBypassKey = "F8";
        primaryColor = DEFAULT_PRIMARY_COLOR;
        secondaryColor = DEFAULT_SECONDARY_COLOR;
        tertiaryColor = DEFAULT_TERTIARY_COLOR;
        REDIRECTS.clear();
        LANGUAGES.clear();
    }
}
