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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 可信时间：从网络取一个时间戳，之后用<b>单调时钟</b>往前推，玩家改系统时间也不受影响。
 *
 * <h2>为什么不是「本机时钟 + 一个偏差值」</h2>
 * 偏差值的写法在玩家拨动系统时间的那一刻就崩了：本机时钟一跳，偏差值立刻失真。
 * 这里换一种记法 —— 同步时同时记下：
 * <ul>
 *   <li><b>基准可信时间</b>：联网要回来的那个时间戳；</li>
 *   <li><b>基准单调时间</b>：{@code System.nanoTime()}（<b>不受系统时间调整影响</b>）。</li>
 * </ul>
 * 之后 {@code 现在 = 基准可信时间 + (当前单调时间 − 基准单调时间)} ——
 * 程序运行期间无论玩家怎么改系统时钟，可信时间都照常往前走。
 * （剧情里需要玩家自己去改系统时间时，游戏看到的时间仍然是正确的。）
 *
 * <h2>跨重启怎么办</h2>
 * {@code nanoTime} 只在一次进程内有效，重启后没法接着算，所以把
 * <b>基准可信时间 + 同步那一刻的本机时钟</b>两个数一起写进全局配置。
 * 下次启动用「本机时钟走了多久」补上这段时间差 —— 这一步只能相信本机时钟，
 * 除非每次启动都联网同步。所以：<b>一次运行内改系统时间无效；跨重启改则有效</b>，
 * 直到重新联网同步为止。
 *
 * <h2>怎么取</h2>
 * 读 HTTP 响应的 {@code Date} 头（RFC 7231 规定由服务器生成）。
 * 相比 NTP：走 443/80 几乎不会被防火墙拦，实现几十行、没有额外依赖；
 * 代价是精度到秒 —— 冷却、每日重置这类用途够用。
 */
public final class TrustedTime {

    /** 默认取时地址：都会用标准 Date 头回应的站点。 */
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

    /** 同步得到的可信时间戳（毫秒）。 */
    private static volatile long baseTrustedMillis = 0;
    /** 记下那个时间戳时的单调时钟读数（纳秒），用它往前推。 */
    private static volatile long baseNanoTime = 0;
    private static volatile boolean synced = false;
    private static volatile String source = null;
    private static volatile long lastSyncWallMillis = 0;
    private static volatile ExecutorService executor;

    private TrustedTime() {
    }

    // ==================== 查询 ====================

    /**
     * 可信时间。
     *
     * <p>从没同步过（也没存过）时退回本机时钟 —— 游戏不该因为取不到时间就跑不动，
     * 但要判断时间可不可信请用 {@link #isSynced()}。
     */
    public static Instant now() {
        if (!synced) {
            return Instant.now();
        }
        long elapsedMillis = (System.nanoTime() - baseNanoTime) / 1_000_000L;
        return Instant.ofEpochMilli(baseTrustedMillis + elapsedMillis);
    }

    /** 可信时间的本地时间形式。 */
    public static LocalDateTime nowLocal() {
        return LocalDateTime.ofInstant(now(), ZoneId.systemDefault());
    }

    /** 可信时间的带时区形式。 */
    public static ZonedDateTime nowZoned() {
        return ZonedDateTime.ofInstant(now(), ZoneId.systemDefault());
    }

    /** 本机时钟（不校正），用来对比 / 调试。 */
    public static Instant systemNow() {
        return Instant.now();
    }

    /** 当前偏差：可信时间 − 本机时间（毫秒）。派生值，仅供参考。 */
    public static long offsetMillis() {
        return now().toEpochMilli() - System.currentTimeMillis();
    }

    /** 是否已经有可信时间（含从配置里恢复）。 */
    public static boolean isSynced() {
        return synced;
    }

    /** 基准可信时间戳的毫秒值；0 表示没有。 */
    public static long baseTrustedMillis() {
        return baseTrustedMillis;
    }

    /** 上次同步（或恢复）时的本机时钟毫秒值；0 表示从未。 */
    public static long lastSyncWallMillis() {
        return lastSyncWallMillis;
    }

    /** 上次同步用的地址；null 表示当前基准来自配置而不是本次联网。 */
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
     * 启动时调用：恢复上次存下来的基准时间。
     *
     * <p>用「这次启动时的本机时钟 − 上次同步时的本机时钟」补上中间这段时间，
     * 于是从启动那一刻起又回到单调推进（本次运行内再改系统时间也不影响）。
     */
    public static void loadFromConfig() {
        Long savedBase = FrameworkDataKeys.TRUSTED_TIME_BASE.get();
        Long savedWall = FrameworkDataKeys.TRUSTED_TIME_BASE_WALL.get();
        if (savedBase == null || savedBase <= 0 || savedWall == null || savedWall <= 0) {
            return;
        }
        long wallNow = System.currentTimeMillis();
        long elapsed = wallNow - savedWall;
        if (elapsed < 0) {
            // 本机时钟被往回拨了：这段时间差不可信，宁可从「现在」重新起算
            Logger("WARNING", "本机时钟早于上次同步时间（被改过？），时间基准重新起算");
            elapsed = 0;
        }
        baseTrustedMillis = savedBase + elapsed;
        baseNanoTime = System.nanoTime();
        lastSyncWallMillis = savedWall;
        synced = true;
        Logger("INFO", "已恢复时间基准: " + Instant.ofEpochMilli(baseTrustedMillis)
                + "（上次同步于本机时钟 " + Instant.ofEpochMilli(savedWall) + "）");
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
            // 关键：可信时间与单调时钟成对记录，之后不再依赖系统时钟
            baseTrustedMillis = networkMillis;
            baseNanoTime = System.nanoTime();
            long wall = System.currentTimeMillis();
            lastSyncWallMillis = wall;
            synced = true;
            source = url;
            FrameworkDataKeys.TRUSTED_TIME_BASE.set(baseTrustedMillis);
            FrameworkDataKeys.TRUSTED_TIME_BASE_WALL.set(wall);
            Logger("INFO", "时间已同步: " + Instant.ofEpochMilli(networkMillis)
                    + "（本机偏差 " + (networkMillis - wall) + "ms，来源 " + url + "）");
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
                try (BufferedReader ignored = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    ignored.readLine();   // 只为了 Date 头，正文丢掉
                }
            }
            return parseHttpDate(conn.getHeaderField("Date"));
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
     * <p>三种历史格式都要认：RFC 1123（现在用的）、RFC 850、asctime ——
     * 规范要求接收方都能解析，虽然现实中几乎只会遇到第一种。
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

    /** 仅测试使用：把静态状态清回「从没同步过」。 */
    static void resetForTest() {
        baseTrustedMillis = 0;
        baseNanoTime = 0;
        synced = false;
        source = null;
        lastSyncWallMillis = 0;
    }
}
