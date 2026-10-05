package com.xiaowu.game.starveil.infrastructure.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 联网状态：后台定期探测「现在能不能连上互联网」。
 *
 * <h2>怎么判断「联网」</h2>
 * 能不能连上互联网，本地是<b>问不出来</b>的：网卡有地址只说明连着路由器，
 * {@code InetAddress.isReachable} 又经常被防火墙静默丢掉（它不一定用 ICMP）。
 * 所以这里用最朴素也最可靠的办法：<b>真的去 TCP 连一个公网地址</b>。
 *
 * <p>默认端点选的是 DNS 服务的 443 端口（{@code 223.5.5.5} / {@code 1.1.1.1} /
 * {@code 8.8.8.8}）—— 它们是纯 IP，不需要先解析域名（域名解析本身就依赖网络，
 * 断网时那一步会先失败，判断不出到底是谁的问题），而且任何一个连上就算在线，
 * 所以在国内 / 国外都能用。内容可以用
 * {@link com.xiaowu.game.starveil.infrastructure.ContentConfig#setNetworkEndpoints(String...)}
 * 换成自己的端点。
 *
 * <h2>线程</h2>
 * 探测在后台单线程上跑，结果放在 volatile 字段里；<b>任何查询都不会阻塞调用方</b>
 * （包括在 JavaFX 线程上调用）。状态变化时通知监听者。
 *
 * <p>第一次探测出结果之前，{@link #isOnline()} 返回 {@code true}：宁可先当「有网」，
 * 也不要在还没测出来的那几百毫秒里给玩家弹一个「你没网」——那才是真的吓人。
 */
public final class NetworkStatus {

    /** 联网状态。 */
    public enum State {
        /** 还没测出结果（刚启动）。此时按「在线」处理，见类说明。 */
        UNKNOWN,
        ONLINE,
        OFFLINE
    }

    /** 默认探测端点：公网 DNS 的 443 端口，纯 IP 不需要解析域名。 */
    private static final String[] DEFAULT_ENDPOINTS = {"223.5.5.5:443", "1.1.1.1:443", "8.8.8.8:443"};

    private static final long DEFAULT_INTERVAL_SECONDS = 10;
    private static final int DEFAULT_TIMEOUT_MS = 2500;

    private static volatile State state = State.UNKNOWN;
    private static volatile List<Endpoint> endpoints = parseEndpoints(DEFAULT_ENDPOINTS);
    private static volatile long intervalSeconds = DEFAULT_INTERVAL_SECONDS;
    private static volatile int timeoutMs = DEFAULT_TIMEOUT_MS;

    private static final List<Consumer<State>> listeners = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile ScheduledExecutorService executor;

    private NetworkStatus() {
    }

    // ==================== 生命周期 ====================

    /** 启动后台探测（幂等；重复调用不会起第二个线程）。 */
    public static void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "starveil-network");
            t.setDaemon(true);   // 守护线程：不该拦住游戏退出
            return t;
        });
        executor.scheduleWithFixedDelay(NetworkStatus::probeAndPublish, 0,
                Math.max(1, intervalSeconds), TimeUnit.SECONDS);
        Logger("DEBUG", "联网探测已启动: " + endpoints + "，间隔 " + intervalSeconds + "s");
    }

    /** 停止探测。 */
    public static void stop() {
        started.set(false);
        ScheduledExecutorService e = executor;
        executor = null;
        if (e != null) {
            e.shutdownNow();
        }
    }

    /** 立刻探测一次（异步，不阻塞）。 */
    public static void checkNow() {
        ScheduledExecutorService e = executor;
        if (e == null) {
            start();
            return;
        }
        e.execute(NetworkStatus::probeAndPublish);
    }

    // ==================== 查询 ====================

    public static State state() {
        return state;
    }

    /** 是否在线。<b>首次探测出结果前返回 true</b>（见类说明）。 */
    public static boolean isOnline() {
        State s = state;
        return s != State.OFFLINE;
    }

    /** 是否已经探测出过结果。 */
    public static boolean isKnown() {
        return state != State.UNKNOWN;
    }

    /** 状态变化回调（在探测线程上触发，UI 用法请自己 Platform.runLater）。 */
    public static void addListener(Consumer<State> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Consumer<State> listener) {
        listeners.remove(listener);
    }

    // ==================== 配置 ====================

    /**
     * 设置探测端点，格式 {@code "主机:端口"} 或 {@code "主机"}（默认 443）。
     *
     * <p>传空表示回到框架默认端点。
     */
    public static void setEndpoints(String... hostPorts) {
        if (hostPorts == null || hostPorts.length == 0) {
            endpoints = parseEndpoints(DEFAULT_ENDPOINTS);
        } else {
            List<Endpoint> parsed = parseEndpoints(hostPorts);
            endpoints = parsed.isEmpty() ? parseEndpoints(DEFAULT_ENDPOINTS) : parsed;
        }
        Logger("INFO", "联网探测端点: " + endpoints);
    }

    /** 设置探测间隔（秒）。 */
    public static void setIntervalSeconds(long seconds) {
        intervalSeconds = Math.max(1, seconds);
    }

    /** 设置单次连接超时（毫秒）。 */
    public static void setTimeoutMillis(int millis) {
        timeoutMs = Math.max(200, millis);
    }

    // ==================== 内部 ====================

    private static void probeAndPublish() {
        boolean ok = probeOnce();
        State next = ok ? State.ONLINE : State.OFFLINE;
        State previous = state;
        state = next;
        if (next == previous) {
            return;
        }
        Logger("INFO", "联网状态: " + next + (subscription(previous, next)));
        for (Consumer<State> listener : listeners) {
            try {
                listener.accept(next);
            } catch (Exception e) {
                Logger("WARNING", "联网状态监听者执行失败: " + e);
            }
        }
    }

    private static String subscription(State previous, State next) {
        if (previous == State.UNKNOWN) {
            return "";
        }
        return next == State.ONLINE ? "（已恢复）" : "（已断开）";
    }

    /** 探测一次：任意一个端点能连上就算在线。 */
    static boolean probeOnce() {
        for (Endpoint ep : endpoints) {
            if (probeEndpoint(ep)) {
                return true;
            }
        }
        return false;
    }

    private static boolean probeEndpoint(Endpoint ep) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ep.host(), ep.port()), timeoutMs);
            return true;
        } catch (IOException e) {
            // 连不上：正常情况（断网 / 该端点被墙），换下一个
            return false;
        }
    }

    private static List<Endpoint> parseEndpoints(String... raw) {
        List<Endpoint> list = new ArrayList<>();
        for (String s : raw) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }
            String text = s.trim();
            String host = text;
            int port = 443;
            int colon = text.lastIndexOf(':');
            if (colon > 0) {
                host = text.substring(0, colon);
                try {
                    port = Integer.parseInt(text.substring(colon + 1).trim());
                } catch (NumberFormatException e) {
                    Logger("WARNING", "联网探测端点端口不合法，已忽略: " + text);
                    continue;
                }
            }
            if (host.isEmpty() || port <= 0 || port > 65535) {
                Logger("WARNING", "联网探测端点不合法，已忽略: " + text);
                continue;
            }
            list.add(new Endpoint(host, port));
        }
        return list;
    }

    /** 一个探测端点。 */
    record Endpoint(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    /** 把主机名解析成地址（给「想先看看 DNS 通不通」的调用方用）。 */
    public static boolean canResolve(String host) {
        try {
            InetAddress.getByName(host);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
