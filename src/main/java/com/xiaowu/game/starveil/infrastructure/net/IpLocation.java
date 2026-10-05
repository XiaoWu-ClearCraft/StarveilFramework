package com.xiaowu.game.starveil.infrastructure.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * IP 属地（市级）。
 *
 * <h2>离线拿不到「市」</h2>
 * 先说清楚一件事：<b>纯离线拿不到准确的市级位置</b>。市一级的信息只存在于
 * IP 归属地库里，而那份库要么联网查、要么随游戏带一份几十 MB 的数据 ——
 * 框架按约定不携带任何数据文件，所以离线只能给出一个<b>粗略猜测</b>：
 * 系统时区（{@code Asia/Shanghai} → 上海）。在国内大家都用
 * {@code Asia/Shanghai}，所以这个猜测对很多人是错的（例如在南昌会猜成上海）。
 *
 * <p>因此本类的返回值带 {@link Source}：{@link Source#IP} 才是真按 IP 查出来的，
 * {@link Source#TIMEZONE} 只是兜底猜测。用它的内容要自己决定能不能接受猜测值
 * （例如「按城市发限定奖励」就不该用猜测值）。
 *
 * <h2>联网怎么查</h2>
 * 依次请求若干公共接口，认其中常见的字段名；任一个成功就用它的结果。
 * 默认接口列表在国内可用，内容可以用
 * {@link com.xiaowu.game.starveil.infrastructure.ContentConfig#setIpLocationUrls(String...)}
 * 换成自己的。
 *
 * <p>返回的城市名会去掉「市」字（{@code 南昌市 → 南昌}），方便直接拼进文案。
 */
public final class IpLocation {

    /** 结果来源。 */
    public enum Source {
        /** 按 IP 联网查出来的（可信）。 */
        IP,
        /** 本地时区猜的（仅供参考，见类说明）。 */
        TIMEZONE,
        /** 还没有任何结果。 */
        UNKNOWN
    }

    /**
     * 一次查询的结果。
     *
     * @param city    城市名（已去掉「市」后缀）；可能为 null
     * @param region  省 / 州；可能为 null
     * @param country 国家 / 地区；可能为 null
     * @param source  来源
     */
    public record Result(String city, String region, String country, Source source) {

        /** 是否有「市」这一级的信息。 */
        public boolean hasCity() {
            return city != null && !city.isEmpty();
        }

        /** 是否按 IP 联网查出来的（而不是时区猜测）。 */
        public boolean isExact() {
            return source == Source.IP;
        }
    }

    /** 默认接口：优先国内可达的；pconline 返回 GBK，需要按 GBK 解码。 */
    private static final String[][] DEFAULT_URLS = {
            {"https://whois.pconline.com.cn/ipJson.jsp?json=true", "GBK"},
            {"https://ipapi.co/json/", "UTF-8"},
            {"https://ipinfo.io/json", "UTF-8"},
            {"http://ip-api.com/json/?lang=zh-CN", "UTF-8"},
    };

    /** JSON 里可能装城市名的字段，按可信度排序。 */
    private static final String[] CITY_FIELDS = {"city", "cityName", "regionName", "addr", "pro"};
    private static final String[] REGION_FIELDS = {"region", "pro", "province", "regionName"};
    private static final String[] COUNTRY_FIELDS = {"country_name", "country", "countryCode"};

    /** 常见时区 → 城市名（离线兜底用；只列常见的）。 */
    private static final Map<String, String> ZONE_CITIES = Map.ofEntries(
            Map.entry("Asia/Shanghai", "上海"),
            Map.entry("Asia/Chongqing", "重庆"),
            Map.entry("Asia/Harbin", "哈尔滨"),
            Map.entry("Asia/Urumqi", "乌鲁木齐"),
            Map.entry("Asia/Kashgar", "喀什"),
            Map.entry("Asia/Taipei", "台北"),
            Map.entry("Asia/Hong_Kong", "香港"),
            Map.entry("Asia/Macau", "澳门"),
            Map.entry("Asia/Tokyo", "东京"),
            Map.entry("Asia/Seoul", "首尔"),
            Map.entry("Asia/Singapore", "新加坡"),
            Map.entry("Asia/Bangkok", "曼谷"),
            Map.entry("Europe/London", "伦敦"),
            Map.entry("Europe/Paris", "巴黎"),
            Map.entry("Europe/Berlin", "柏林"),
            Map.entry("Europe/Moscow", "莫斯科"),
            Map.entry("America/New_York", "纽约"),
            Map.entry("America/Chicago", "芝加哥"),
            Map.entry("America/Denver", "丹佛"),
            Map.entry("America/Los_Angeles", "洛杉矶"),
            Map.entry("Australia/Sydney", "悉尼")
    );

    private static final int TIMEOUT_MS = 4000;

    private static volatile List<Endpoint> endpoints = parseDefaults();
    private static volatile Result cached = new Result(null, null, null, Source.UNKNOWN);
    private static volatile ExecutorService executor;

    private IpLocation() {
    }

    // ==================== 查询 ====================

    /** 最近一次的结果（可能是时区猜测，也可能是 UNKNOWN）。 */
    public static Result current() {
        return cached;
    }

    /** 最近一次结果里的城市名；没有就返回离线猜测。 */
    public static String city() {
        Result r = cached;
        if (r.hasCity()) {
            return r.city();
        }
        return offlineGuess().city();
    }

    /** 离线猜测：按系统时区给一个城市（见类说明，仅供参考）。 */
    public static Result offlineGuess() {
        String zoneId = java.util.TimeZone.getDefault().getID();
        String zonePart = zoneId.contains("/") ? zoneId.substring(zoneId.lastIndexOf('/') + 1) : zoneId;
        String city = ZONE_CITIES.get(zoneId);
        if (city == null) {
            // 没有对照表就退回时区里的城市段（会是英文，例如 Shanghai）
            city = zonePart.replace('_', ' ');
        }
        return new Result(normalizeCity(city), zoneCountry(zoneId), null, Source.TIMEZONE);
    }

    private static String zoneCountry(String zoneId) {
        int slash = zoneId.indexOf('/');
        return slash > 0 ? zoneId.substring(0, slash) : null;
    }

    /** 异步联网查询一次；返回结果（失败时给出时区猜测）。 */
    public static CompletableFuture<Result> resolveAsync() {
        return CompletableFuture.supplyAsync(IpLocation::resolveOnce, executor());
    }

    /** 同步查询一次（阻塞，别在 JavaFX 线程上调）。 */
    public static Result resolveOnce() {
        for (Endpoint ep : endpoints) {
            try {
                String body = httpGet(ep.url(), ep.charset());
                if (body == null) {
                    continue;
                }
                Result result = parse(body, Source.IP);
                if (result.hasCity()) {
                    cached = result;
                    Logger("INFO", "IP 属地: " + result.city()
                            + (result.region() != null ? "（" + result.region() + "）" : "")
                            + "，来源 " + ep.url());
                    return result;
                }
            } catch (Exception e) {
                Logger("DEBUG", "IP 属地查询失败（" + ep.url() + "）: " + e);
            }
        }
        Result guess = offlineGuess();
        cached = guess;
        Logger("WARNING", "IP 属地联网查询失败，先用时区猜测: " + guess.city());
        return guess;
    }

    public static void setUrls(String... newUrls) {
        if (newUrls == null || newUrls.length == 0) {
            endpoints = parseDefaults();
        } else {
            List<Endpoint> list = new ArrayList<>();
            for (String u : newUrls) {
                if (u != null && !u.trim().isEmpty()) {
                    list.add(new Endpoint(u.trim(), StandardCharsets.UTF_8));
                }
            }
            endpoints = list.isEmpty() ? parseDefaults() : list;
        }
        Logger("INFO", "IP 属地接口: " + endpoints.size() + " 个");
    }

    // ==================== 解析（纯逻辑，便于测试） ====================

    /**
     * 从接口返回的 JSON 里取城市 / 省 / 国家。
     *
     * <p>不同接口字段名不一样，这里按候选字段逐个找；没有 {@code city} 的接口
     * （例如只给一整串「江西省南昌市」的 {@code addr}）会走
     * {@link #cityFromAddress(String)} 把市名切出来。
     */
    static Result parse(String json, Source source) {
        if (json == null || json.trim().isEmpty()) {
            return new Result(null, null, null, Source.UNKNOWN);
        }
        JsonObject obj;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return new Result(null, null, null, Source.UNKNOWN);
            }
            obj = root.getAsJsonObject();
        } catch (Exception e) {
            Logger("DEBUG", "IP 属地返回的不是合法 JSON");
            return new Result(null, null, null, Source.UNKNOWN);
        }

        String city = null;
        for (String field : CITY_FIELDS) {
            String value = string(obj, field);
            if (value == null || value.isEmpty()) {
                continue;
            }
            city = field.equals("addr") || field.equals("pro")
                    ? cityFromAddress(value)     // 整串地址：切出市名
                    : normalizeCity(value);
            if (city != null && !city.isEmpty()) {
                break;
            }
        }

        String region = null;
        for (String field : REGION_FIELDS) {
            String value = string(obj, field);
            if (value != null && !value.isEmpty()) {
                region = normalizeRegion(value);
                break;
            }
        }

        String country = null;
        for (String field : COUNTRY_FIELDS) {
            String value = string(obj, field);
            if (value != null && !value.isEmpty()) {
                country = value.trim();
                break;
            }
        }
        return new Result(city, region, country, source);
    }

    /**
     * 城市名归一化：去掉后缀「市」、去掉空白。
     *
     * <p>{@code "南昌市" → "南昌"}、{@code "南昌市 " → "南昌"}。
     * 只去「市」：{@code "湘西土家族苗族自治州"} 这种自治州名去掉「州」就废了。
     */
    public static String normalizeCity(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim().replace(" ", "").replace("\u3000", "");
        if (text.isEmpty()) {
            return null;
        }
        if (text.endsWith("市") && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /** 省名归一化：去掉「省」（「江西省」→「江西」），自治区 / 特别行政区原样保留。 */
    public static String normalizeRegion(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        // 「省」和「市」都去掉（直辖市在 region 字段里会是「北京市」）；
        // 自治区 / 特别行政区保留原样 —— 去掉「区」就成了另一个词。
        if (text.length() > 1 && (text.endsWith("省") || text.endsWith("市"))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**
     * 从一整串属地地址里切出市名。
     *
     * <p>形如「江西省南昌市」「江西省南昌市 电信」「江西省 南昌市」都要能切出「南昌」：
     * 取最后一个「省」之后、第一个「市」之前的那一段。没有「市」字时（直辖市写成
     * 「北京市朝阳区」也一样有「市」；真的没有就返回 null，宁可不给也不瞎猜）。
     */
    public static String cityFromAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return null;
        }
        String text = address.trim().replace(" ", "").replace("\u3000", "");
        int cityEnd = text.indexOf('市');
        if (cityEnd < 0) {
            return null;
        }
        String before = text.substring(0, cityEnd);
        int provinceEnd = before.lastIndexOf('省');
        String city = provinceEnd >= 0 ? before.substring(provinceEnd + 1) : before;
        // 直辖市的写法是「北京市」，此时 before 只有「北京」，也能得到「北京」
        return normalizeCity(city);
    }

    private static String string(JsonObject obj, String field) {
        if (obj == null || !obj.has(field)) {
            return null;
        }
        JsonElement e = obj.get(field);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) {
            return null;
        }
        try {
            return e.getAsString();
        } catch (Exception ex) {
            return null;
        }
    }

    // ==================== 内部 ====================

    private record Endpoint(String url, Charset charset) {
    }

    private static List<Endpoint> parseDefaults() {
        List<Endpoint> list = new ArrayList<>();
        for (String[] pair : DEFAULT_URLS) {
            Charset cs;
            try {
                cs = Charset.forName(pair[1]);
            } catch (Exception e) {
                cs = StandardCharsets.UTF_8;
            }
            list.add(new Endpoint(pair[0], cs));
        }
        return list;
    }

    private static String httpGet(String url, Charset charset) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "StarveilFramework/1.0");
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                int total = 0;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                    if (total > 64 * 1024) {
                        break;   // 归属地接口不该返回大响应，防意外
                    }
                }
                return out.toString(charset.name());
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "starveil-ip-location");
                t.setDaemon(true);
                return t;
            });
        }
        return executor;
    }

    /** 便于调试：把时区对照表导出来看看。 */
    static Map<String, String> zoneCityTable() {
        return new HashMap<>(ZONE_CITIES);
    }
}
