package com.xiaowu.game.starveil.infrastructure.net;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.xiaowu.game.starveil.platform.common.SystemDetector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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
 * 联网状态：Windows 上监听网络接口变化（JNA），变化时立刻探测；另配一个慢速兜底轮询。
 *
 * <h2>为什么不只用轮询</h2>
 * 轮询的问题是「要么不够快、要么太费」：间隔短了白耗电，间隔长了拔网线好一会儿才反应过来。
 * Windows 提供了 {@code NotifyIpInterfaceChange}（iphlpapi）—— 注册一次回调，
 * 接口 up/down 时系统主动通知，于是<b>插拔网线、开关 WiFi 会立刻被察觉</b>。
 *
 * <h2>为什么还留一个轮询</h2>
 * 因为「接口事件」和「能不能上互联网」不是一回事：最典型的是路由器活着但 WAN 断了
 * （网线还插着、WiFi 还连着），这时接口没有任何变化、事件不会触发 ——
 * 只看事件的话，「断网提示」要等到玩家真的发请求失败才会出现。
 * 所以：事件负责「秒级反应」，轮询负责兜住这种静默断网。
 *
 * <p>非 Windows 平台（或 iphlpapi 调用失败）自动退回纯 TCP 轮询，并在日志里说明。
 *
 * <h2>怎么判断「联网」</h2>
 * 优先问 Windows 自己：{@link WindowsInternetState}（网络列表管理器 NLM）
 * 给出的就是任务栏「有网 / 无 Internet」图标的那个结论，能区分
 * <b>「有网络但没互联网」</b>（连着路由器/热点，但出不去）这种情况。
 * 它是本地 COM 调用，不发网络包，所以可以问得很勤（自动模式 5 秒一次）。
 *
 * <p>NLM 不可用时（非 Windows、COM 创建失败）才退回
 * <b>真的 TCP 连一个公网地址</b>：网卡有地址只说明连着路由器，
 * {@code InetAddress.isReachable} 又常被防火墙静默丢掉，只能真连。
 * 默认端点是公网 DNS 的 443（{@code 223.5.5.5} / {@code 1.1.1.1} / {@code 8.8.8.8}）：
 * 纯 IP 不需要先解析域名（断网时那一步会先失败，反而看不出是谁的问题），
 * 任意一个连上就算在线，国内国外都能用。这条路的间隔默认拉长到
 * {@value #DEFAULT_TCP_INTERVAL_SECONDS} 秒 —— 它是要发网络包的，不该问太勤。
 *
 * <h2>线程</h2>
 * 探测在后台守护线程上跑，结果放 volatile 字段；<b>任何查询都不阻塞调用方</b>。
 * 首次探测出结果之前 {@link #isOnline()} 返回 {@code true} —— 宁可先当有网，
 * 也不要在还没测出来的那几百毫秒里给玩家弹一个「你没网」。
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

    /** 自动模式的兜底轮询间隔：问系统（NLM）时问得勤，自己发 TCP 探测时问得省。 */
    public static final long AUTO_INTERVAL = -1;
    private static final long SYSTEM_INTERVAL_SECONDS = 5;
    private static final long DEFAULT_TCP_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_TIMEOUT_MS = 2500;

    private static volatile State state = State.UNKNOWN;
    private static volatile List<Endpoint> endpoints = parseEndpoints(DEFAULT_ENDPOINTS);
    private static volatile long intervalSeconds = AUTO_INTERVAL;
    private static volatile int timeoutMs = DEFAULT_TIMEOUT_MS;

    private static final List<Consumer<State>> listeners = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile ScheduledExecutorService executor;

    /** 接口变化通知的句柄：必须保持强引用（回调还在系统那边挂着），退出时要注销。 */
    private static volatile InterfaceChangeWatcher interfaceWatcher;

    private NetworkStatus() {
    }

    // ==================== 生命周期 ====================

    /** 启动探测（幂等；重复调用不会起第二个线程，也不会重复注册回调）。 */
    public static void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "starveil-network");
            t.setDaemon(true);   // 守护线程：不该拦住游戏退出
            return t;
        });
        // 先测一次，别让玩家等第一个轮询周期；顺便在这一次里完成 NLM 初始化
        executor.execute(() -> {
            probeAndPublish();
            schedulePollingIfWanted();
        });
        startInterfaceWatcher();
    }

    /**
     * 排兜底轮询。放在第一次探测之后，是因为间隔取决于「问系统还是自己探测」——
     * 这要等 {@link WindowsInternetState} 试过 COM 才定得下来。
     */
    private static void schedulePollingIfWanted() {
        long interval = effectiveIntervalSeconds();
        ScheduledExecutorService e = executor;
        if (e == null) {
            return;
        }
        if (interval <= 0) {
            Logger("INFO", "联网状态不轮询，只靠接口事件与手动 checkNow()");
            return;
        }
        e.scheduleWithFixedDelay(NetworkStatus::probeAndPublish, interval, interval, TimeUnit.SECONDS);
        Logger("INFO", "联网判定来源: " + sourceName() + "，兜底轮询 " + interval + "s");
    }

    /** 实际用的轮询间隔：显式设置优先，自动模式下按判定来源选。 */
    private static long effectiveIntervalSeconds() {
        long configured = intervalSeconds;
        if (configured != AUTO_INTERVAL) {
            return configured;
        }
        return WindowsInternetState.isAvailable() ? SYSTEM_INTERVAL_SECONDS : DEFAULT_TCP_INTERVAL_SECONDS;
    }

    /** 当前判定来源的可读名字。 */
    public static String sourceName() {
        return WindowsInternetState.isAvailable() ? "Windows 系统联网状态（NLM，本地调用）"
                : "TCP 探测（" + endpoints + "）";
    }

    /** 停止探测（注销系统回调）。一般不需要调。 */
    public static void stop() {
        started.set(false);
        ScheduledExecutorService e = executor;
        executor = null;
        if (e != null) {
            // COM 对象绑定在创建它的线程上，所以在探测线程上释放了再收线程
            e.execute(WindowsInternetState::release);
            e.shutdown();
            try {
                e.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            e.shutdownNow();
        }
        InterfaceChangeWatcher watcher = interfaceWatcher;
        interfaceWatcher = null;
        if (watcher != null) {
            watcher.close();
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
        return state != State.OFFLINE;
    }

    /** 是否已经探测出过结果。 */
    public static boolean isKnown() {
        return state != State.UNKNOWN;
    }

    /**
     * 状态变化回调。
     *
     * <p>在探测线程上触发：要碰 UI 请自己 {@code Platform.runLater}。
     */
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
     * 传空表示回到框架默认端点。
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

    /**
     * 设置兜底轮询间隔（秒）。
     *
     * <ul>
     *   <li>{@link #AUTO_INTERVAL}（默认）：自动 —— 问系统（NLM）时 5 秒一次，
     *       自己发 TCP 探测时 30 秒一次；</li>
     *   <li>{@code 0}：<b>关掉轮询</b>，只靠接口事件 + 手动 {@link #checkNow()}。</li>
     * </ul>
     *
     * <p>关掉之前请先想清楚：路由器活着但 WAN 断了这种「静默断网」不一定有事件，
     * 状态要等到下一次 {@link #checkNow()}（或玩家真发请求失败）才会更新。
     */
    public static void setIntervalSeconds(long seconds) {
        intervalSeconds = seconds < 0 ? AUTO_INTERVAL : seconds;
    }

    /** 配置的轮询间隔：{@link #AUTO_INTERVAL} 表示自动（见 {@link #setIntervalSeconds}）。 */
    public static long intervalSeconds() {
        return intervalSeconds;
    }

    /** 设置单次连接超时（毫秒）。 */
    public static void setTimeoutMillis(int millis) {
        timeoutMs = Math.max(200, millis);
    }

    /** 是否正在用系统接口事件（Windows 且注册成功）。 */
    public static boolean isEventDriven() {
        return interfaceWatcher != null && interfaceWatcher.registered;
    }

    // ==================== 接口事件（Windows / iphlpapi） ====================

    /**
     * 注册「网络接口变化」回调。
     *
     * <p>只做触发：回调里不判断联网（接口 up 不等于能上互联网），
     * 而是丢一个探测任务给后台线程 —— 这样回调能尽快返回，不占着系统的通知线程。
     */
    private static void startInterfaceWatcher() {
        if (!SystemDetector.isWindows()) {
            Logger("DEBUG", "非 Windows 平台：联网状态改为纯轮询");
            return;
        }
        try {
            InterfaceChangeWatcher watcher = new InterfaceChangeWatcher();
            if (watcher.register()) {
                interfaceWatcher = watcher;
                Logger("INFO", "已监听 Windows 网络接口变化（拔插网线/开关 WiFi 会立刻重测）");
            } else {
                watcher.close();
                Logger("WARNING", "注册网络接口变化回调失败，退回纯轮询");
            }
        } catch (Throwable t) {
            Logger("WARNING", "网络接口事件不可用（" + t + "），退回纯轮询");
        }
    }

    /** iphlpapi 的 {@code NotifyIpInterfaceChange} 封装。 */
    private static final class InterfaceChangeWatcher {

        /** AF_UNSPEC：IPv4 / IPv6 都听。 */
        private static final short AF_UNSPEC = 0;

        /** MIB_NOTIFICATION_TYPE 的值：接口发生变化。 */
        @SuppressWarnings("unused")
        private static final int MIB_NOTIFICATION_TYPE_CHANGE = 1;

        private final PointerByReference handle = new PointerByReference();
        /** 回调对象必须保持强引用：只被 native 侧引用的话会被 GC 掉，然后就是随机崩溃。 */
        private final IpInterfaceChangeCallback callback = this::onInterfaceChanged;
        private volatile boolean registered;

        boolean register() {
            int result = Iphlpapi.INSTANCE.NotifyIpInterfaceChange(
                    AF_UNSPEC, callback, null, (byte) 0, handle);
            registered = result == 0 && handle.getValue() != null;
            if (!registered) {
                Logger("DEBUG", "NotifyIpInterfaceChange 返回 " + result);
            }
            return registered;
        }

        private void onInterfaceChanged(Pointer callerContext, Pointer row, int notificationType) {
            Logger("DEBUG", "网络接口发生变化（type=" + notificationType + "），立刻重测联网状态");
            ScheduledExecutorService e = executor;
            if (e != null) {
                e.execute(NetworkStatus::probeAndPublish);
            }
        }

        void close() {
            if (handle.getValue() != null) {
                try {
                    Iphlpapi.INSTANCE.CancelMibChangeNotify2(handle.getValue());
                } catch (Throwable ignored) {
                    // 注销失败无所谓：进程退出时系统会清理
                }
                handle.setValue(null);
            }
            registered = false;
        }
    }

    /** iphlpapi.dll 里我们用到的那几个函数。 */
    private interface Iphlpapi extends StdCallLibrary {
        Iphlpapi INSTANCE = Native.load("iphlpapi", Iphlpapi.class, W32APIOptions.DEFAULT_OPTIONS);

        /**
         * DWORD NotifyIpInterfaceChange(ADDRESS_FAMILY, PIPINTERFACE_CHANGE_CALLBACK,
         * PVOID CallerContext, BOOLEAN InitialNotification, PHANDLE NotificationHandle);
         */
        int NotifyIpInterfaceChange(short family, IpInterfaceChangeCallback callback,
                                    Pointer callerContext, byte initialNotification,
                                    PointerByReference notificationHandle);

        /** DWORD CancelMibChangeNotify2(HANDLE NotificationHandle); */
        int CancelMibChangeNotify2(Pointer notificationHandle);
    }

    /** {@code PIPINTERFACE_CHANGE_CALLBACK}：callerContext / MIB_IPINTERFACE_ROW / 通知类型。 */
    private interface IpInterfaceChangeCallback extends StdCallLibrary.StdCallCallback {
        void invoke(Pointer callerContext, Pointer row, int notificationType);
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
        Logger("INFO", "联网状态: " + next + transitionHint(previous, next));
        for (Consumer<State> listener : listeners) {
            try {
                listener.accept(next);
            } catch (Exception e) {
                Logger("WARNING", "联网状态监听者执行失败: " + e);
            }
        }
    }

    private static String transitionHint(State previous, State next) {
        if (previous == State.UNKNOWN) {
            return "";
        }
        return next == State.ONLINE ? "（已恢复）" : "（已断开）";
    }

    /** 探测一次：先信系统结论，系统答不上来才自己 TCP 连。 */
    static boolean probeOnce() {
        Boolean system = WindowsInternetState.isConnectedToInternet();
        if (system == null) {
            system = WindowsInternetState.hasInternetFlag();
        }
        if (system != null) {
            return system;
        }
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

    /** 只判 DNS：给「想先看看域名解析通不通」的调用方用。 */
    public static boolean canResolve(String host) {
        try {
            java.net.InetAddress.getByName(host);
            return true;
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }
}
