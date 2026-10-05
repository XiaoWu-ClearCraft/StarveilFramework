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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * IP 属地（市级）：<b>启动时查一次</b>，之后整个运行期都用这一次的结果。
 *
 * <h2>语义</h2>
 * <ul>
 *   <li>进程启动时（{@code AppEntry}）后台查一次，结果只放内存 ——
 *       <b>不写盘</b>：IP 会变（换网络、换城市），把上一次的属地存下来接着用等于骗自己；</li>
 *   <li>查不到（没网、接口不通、返回里没有市）就保持 {@code null}，<b>不猜</b>。
 *       市一级信息只存在于 IP 库里，纯离线拿不到；按系统时区猜出的「上海」对南昌玩家是错的，
 *       错的值比没有更糟；</li>
 *   <li>整个运行期只查这一次：{@link #city()} 命中缓存就直接返回，不会每次都发请求。</li>
 * </ul>
 *
 * <h2>字段与格式</h2>
 * 依次请求若干公共接口，认其中常见的字段名，哪个先成功用哪个。只给整串地址
 * （如「江西省南昌市」）的接口会走 {@link #cityFromAddress(String)} 把市名切出来。
 * 返回的城市名统一<b>去掉「市」字</b>（{@code 南昌市 → 南昌}），方便直接拼进文案。
 */
public final class IpLocation {

    /**
     * 一次查询的结果。
     *
     * @param city    城市名（已去掉「市」后缀）；可能为 null
     * @param region  省 / 州；可能为 null
     * @param country 国家 / 地区；可能为 null
     */
    public record Result(String city, String region, String country) {

        /** 是否有「市」这一级的信息。 */
        public boolean hasCity() {
            return city != null && !city.isEmpty();
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

    private static final int TIMEOUT_MS = 4000;

    private static volatile List<Endpoint> endpoints = parseDefaults();

    /** 本次运行查到的结果；null = 还没查到 / 查不到。 */
    private static volatile Result cached = null;
    private static volatile boolean fetching = false;
    private static volatile ExecutorService executor;

    private IpLocation() {
    }

    // ==================== 查询 ====================

    /**
     * 本次运行查到的结果；没查到返回 {@code null}。
     *
     * <p>不会触发联网：想拿结果请先在启动时调 {@link #prefetchAsync()}。
     */
    public static Result current() {
        return cached;
    }

    /** 城市名；没查到返回 {@code null}（<b>不猜</b>）。 */
    public static String city() {
        Result r = cached;
        return r != null ? r.city() : null;
    }

    /** 省份；没查到返回 {@code null}。 */
    public static String region() {
        Result r = cached;
        return r != null ? r.region() : null;
    }

    /** 是否已经有结果。 */
    public static boolean isAvailable() {
        return cached != null && cached.hasCity();
    }

    /**
     * 启动时调用：后台查一次并缓存（幂等）。
     *
     * <p>查不到就保持 null，<b>不重试</b> —— 属地是个锦上添花的东西，
     * 不值得为它反复发请求。
     */
    public static void prefetchAsync() {
        if (cached != null || fetching) {
            return;
        }
        fetching = true;
        CompletableFuture.runAsync(() -> {
            try {
                resolveOnce();
            } finally {
                fetching = false;
            }
        }, executor());
    }

    /** 显式重查一次（例如玩家手动刷新）。 */
    public static CompletableFuture<Result> refreshAsync() {
        return CompletableFuture.supplyAsync(IpLocation::resolveOnce, executor());
    }

    /** 同步查询一次（阻塞，别在 JavaFX 线程上调）。查不到返回 null，缓存不变。 */
    public static Result resolveOnce() {
        for (Endpoint ep : endpoints) {
            try {
                String body = httpGet(ep.url(), ep.charset());
                if (body == null) {
                    continue;
                }
                Result result = parse(body);
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
        Logger("INFO", "IP 属地未取得结果（离线或接口不可用），将返回 null");
        return null;
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

    /** 清掉缓存（测试用，或玩家换网络后想重查）。 */
    static void clearCacheForTest() {
        cached = null;
    }

    // ==================== 解析（纯逻辑，便于测试） ====================

    /**
     * 从接口返回的 JSON 里取城市 / 省 / 国家。
     *
     * <p>不同接口字段名不一样，这里按候选字段逐个找；只给整串地址的接口
     * （例如 {@code addr = "江西省南昌市"}）会走 {@link #cityFromAddress(String)} 切出市名。
     */
    static Result parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new Result(null, null, null);
        }
        JsonObject obj;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return new Result(null, null, null);
            }
            obj = root.getAsJsonObject();
        } catch (Exception e) {
            Logger("DEBUG", "IP 属地返回的不是合法 JSON");
            return new Result(null, null, null);
        }

        String city = null;
        for (String field : CITY_FIELDS) {
            String value = string(obj, field);
            if (value == null || value.isEmpty()) {
                continue;
            }
            // addr / pro 是整串地址（「江西省南昌市」），要切；其余本身就是市名
            city = field.equals("addr") || field.equals("pro")
                    ? cityFromAddress(value)
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
        return new Result(city, region, country);
    }

    /**
     * 城市名归一化：去掉后缀「市」、去掉空白。
     *
     * <p>{@code "南昌市" → "南昌"}、{@code " 南 昌 市 " → "南昌"}。
     * 只去「市」：{@code "湘西土家族苗族自治州"} 去掉「州」就废了。
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

    /**
     * 省名归一化：去掉「省」「市」（直辖市在 region 字段里会是「北京市」），
     * 自治区 / 特别行政区原样保留 —— 去掉「区」就成了另一个词。
     */
    public static String normalizeRegion(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.length() > 1 && (text.endsWith("省") || text.endsWith("市"))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**
     * 从一整串属地地址里切出市名。
     *
     * <p>「江西省南昌市」「江西省南昌市 电信」「江西省 南昌市」都要能切出「南昌」：
     * 取最后一个「省」之后、第一个「市」之前的那一段。直辖市「北京市朝阳区」同理。
     * 真的没有「市」字就返回 {@code null} —— 宁可不给，也不瞎猜。
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
}
