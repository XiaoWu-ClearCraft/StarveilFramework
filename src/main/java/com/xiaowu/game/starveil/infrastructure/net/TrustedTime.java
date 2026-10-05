package com.xiaowu.game.starveil.infrastructure.net;

import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 可信时间：本机时钟 + 从网络取回来的「校正量」。
 *
 * <h2>为什么要可信时间</h2>
 * 本机时钟是玩家说了算的：改一下系统时间就能跳过冷却、刷新每日奖励、伪造存档时间戳。
 * 需要「现在几点」这种东西真正可信时，得去问网络。
 *
 * <h2>怎么取</h2>
 * 用 <b>HTTP 响应的 {@code Date} 头</b>（RFC 7231 规定由服务器生成）。
 * 相比 NTP：走 443/80，几乎不会被防火墙拦；实现只有几十行、不需要额外依赖。
 * 代价是精度到秒（对游戏里的冷却、每日重置这类用途绰绰有余）。
 *
 * <p>取回来的是「网络时间 − 本机时间」的差（{@link #offsetMillis()}），
 * 之后 {@link #now()} 一直用这个差值校正本机时钟 —— 于是不需要频繁联网，
 * 断网期间也能继续给出校正后的时间。
 *
 * <p>差值会写进全局配置（{@code starveil:trusted_time_offset}），下次启动可以先用
 * 上次的校正量，直到重新同步成功。注意本机时钟如果被改动很大，这个旧差值也会跟着失准；
 * 所以 {@link #syncAsync()} 每次启动都值得调一次。
 */
public final class TrustedTime {

    /** 默认取时地址：都是会用标准 Date 头回应的站点。 */
    private static final String[] DEFAULT_URLS = {
            "https://www.baidu.com",
            "https://www.bing.com",
            "https://www.cloudflare.com"
    };

    private static final long SYNC_TIMEOUT_MS = 4000;

    private static final DateTimeFormatter[] HTTP_DATE_FORMATS = {
            DateTimeFormatter.RFC_1123_DATE_TIME,                     // Tue, 3 Jun 2008 11:05:30 GMT
            DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.ENGLISH),  // RFC 850
    };

    /** asctime 格式（{@code Tue Jun 3 11:05:30 2008}）没有时区，按 GMT 解释。 */
    private static final DateTimeFormatter ASCTIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);

    private static volatile List<String> urls = new ArrayList<>(List.of(DEFAULT_URLS));
    private static volatile long offsetMillis = 0;
    private static volatile boolean synced = false;
    private static volatile long lastSyncLocalMillis = 0;
    private static volatile String source = null;
    private static volatile ExecutorService executor;

    private TrustedTime() {
    }

    // ==================== 查询 ====================

    /** 可信时间（本机时钟 + 校正量）。 */
    public static Instant now() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + offsetMillis);
    }

    /** 可信时间的本地时间形式。 */
    public static LocalDateTime nowLocal() {
        return LocalDateTime.ofInstant(now(), ZoneId.systemDefault());
    }

    /** 可信时间的带时区形式（要显示「几点」时用这个，能带上时区）。 */
    public static ZonedDateTime nowZoned() {
        return ZonedDateTime.ofInstant(now(), ZoneId.systemDefault());
    }

    /** 本机时钟（不做校正），用来对比 / 调试。 */
    public static Instant systemNow() {
        return Instant.now();
    }

    /** 校正量：可信时间 − 本机时间（毫秒）。正数表示本机慢了。 */
    public static long offsetMillis() {
        return offsetMillis;
    }

    /** 是否已经成功同步过（含从配置里恢复）。 */
    public static boolean isSynced() {
        return synced;
    }

    /** 上次同步（或恢复）时的本机时间戳；0 表示从未。 */
    public static long lastSyncLocalMillis() {
        return lastSyncLocalMillis;
    }

    /** 上次同步用的地址；null 表示从未联网同步（当前值可能来自配置）。 */
    public static String source() {
        return source;
    }

    public static void setUrls(String... newUrls) {
        if (newUrls == null || newUrls.length == 0) {
            urls = new ArrayList<>(List.of(DEFAULT_URLS));
        } else {
            List<String> list = new ArrayList<>();
            for (String u : newUrls) {
                if (u != null && !u.trim().isEmpty()) {
                    list.add(u.trim());
                }
            }
            urls = list.isEmpty() ? new ArrayList<>(List.of(DEFAULT_URLS)) : list;
        }
        Logger("INFO", "取时地址: " + urls);
    }

    // ==================== 同步 ====================

    /**
     * 启动时调用：先把上次存下来的校正量读回来。
     *
     * <p>这样即使这一次还没联网，游戏拿到的也不是裸的本机时钟。
     */
    public static void loadFromConfig() {
        Long saved = FrameworkDataKeys.TRUSTED_TIME_OFFSET.get();
        if (saved != null && saved != 0) {
            offsetMillis = saved;
            synced = true;
            lastSyncLocalMillis = FrameworkDataKeys.TRUSTED_TIME_SYNCED_AT.get();
            Logger("INFO", "已恢复上次的时间校正量: " + saved + "ms（本机时间 "
                    + LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncLocalMillis),
                            ZoneId.systemDefault()) + " 时同步的）");
        }
    }

    /** 异步同步一次；返回是否成功。 */
    public static CompletableFuture<Boolean> syncAsync() {
        return CompletableFuture.supplyAsync(TrustedTime::syncOnce, executor());
    }

    /** 同步一次（阻塞，别在 JavaFX 线程上调；UI 请用 {@link #syncAsync()}）。 */
    public static boolean syncOnce() {
        for (String url : urls) {
            Long networkMillis = fetchNetworkTime(url);
            if (networkMillis == null) {
                continue;
            }
            long local = System.currentTimeMillis();
            long offset = networkMillis - local;
            offsetMillis = offset;
            synced = true;
            lastSyncLocalMillis = local;
            source = url;
            FrameworkDataKeys.TRUSTED_TIME_OFFSET.set(offset);
            FrameworkDataKeys.TRUSTED_TIME_SYNCED_AT.set(local);
            Logger("INFO", "时间已同步: 偏差 " + offset + "ms（来源 " + url + "）");
            return true;
        }
        Logger("WARNING", "取时失败：所有地址都没能给出 Date 头（网络不可用？）");
        return false;
    }

    /** 向一个地址要 HTTP 的 Date 头，换算成毫秒；失败返回 null。 */
    static Long fetchNetworkTime(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout((int) SYNC_TIMEOUT_MS);
            conn.setReadTimeout((int) SYNC_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "StarveilFramework/1.0");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) {
                // 有些站点不允许 HEAD：换 GET 再试一次
                conn.disconnect();
                conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout((int) SYNC_TIMEOUT_MS);
                conn.setReadTimeout((int) SYNC_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "StarveilFramework/1.0");
                conn.getResponseCode();
                // 只为了 Date 头，正文丢掉
                try (BufferedReader ignored = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    ignored.readLine();
                }
            }
            String date = conn.getHeaderField("Date");
            return parseHttpDate(date);
        } catch (Exception e) {
            Logger("DEBUG", "取时失败（" + url + "）: " + e);
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 解析 HTTP 的 {@code Date} 头。
     *
     * <p>三种历史格式都要认：RFC 1123（现在用的）、RFC 850、asctime —— 规范要求
     * 接收方都能解析，虽然现实中几乎只会遇到第一种。
     */
    static Long parseHttpDate(String header) {
        if (header == null || header.trim().isEmpty()) {
            return null;
        }
        String text = header.trim();
        for (DateTimeFormatter format : HTTP_DATE_FORMATS) {
            try {
                return ZonedDateTime.parse(text, format).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // 换下一种格式
            }
        }
        // asctime 不写时区：HTTP 里它一律表示 GMT
        try {
            return LocalDateTime.parse(text, ASCTIME_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // 真的不认识
        }
        Logger("DEBUG", "无法解析的 Date 头: " + header);
        return null;
    }

    private static synchronized ExecutorService executor() {
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "starveil-time-sync");
                t.setDaemon(true);
                return t;
            });
        }
        return executor;
    }
}
