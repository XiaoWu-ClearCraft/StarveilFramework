package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.infrastructure.net.NetworkStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 网络状况 API —— 现在有没有网、什么时候变的。
 *
 * <h2>判定的是什么</h2>
 * 「能上互联网」，不是「网卡亮着」。Windows 上优先采信系统自己的结论
 * （网络列表管理器，就是任务栏「无 Internet」图标的数据源），
 * 所以<b>「连着路由器但出不去」也能测出来</b>；系统答不上来时框架才自己发 TCP 探测。
 * 细节与代价见 {@code docs/network.md}。
 *
 * <h2>首次结果之前先当「有网」</h2>
 * 刚启动还没测出结果时 {@link #isOnline()} 返回 {@code true}、{@link #state()} 是
 * {@link State#UNKNOWN}：宁可在没测出来的一瞬间当有网，也不要给玩家弹一个假的「你没网」。
 * 想区分「确定在线」和「还没测」，用 {@link #isKnown()}。
 *
 * <pre>{@code
 * if (!Starveil.network().isOnline()) { ... }
 * Starveil.network().addListener(state -> {
 *     // 在探测线程上回调：碰 UI 请自己 Platform.runLater
 * });
 * }</pre>
 */
public class StarveilNetwork {

    /** 联网状态（与框架内部状态一一对应，避免内容侧依赖内部类型）。 */
    public enum State {
        /** 还没测出结果；此时按「在线」处理。 */
        UNKNOWN,
        ONLINE,
        OFFLINE
    }

    private static final StarveilNetwork INSTANCE = new StarveilNetwork();

    /** 内容侧监听器 → 内部包装器，用于能取消注册。 */
    private final Map<Consumer<State>, Consumer<NetworkStatus.State>> wrappers = new ConcurrentHashMap<>();

    private StarveilNetwork() {
    }

    public static StarveilNetwork getInstance() {
        return INSTANCE;
    }

    /** 是否在线。首次探测出结果前返回 {@code true}（见类说明）。 */
    public boolean isOnline() {
        return NetworkStatus.isOnline();
    }

    /** 当前状态（含「还没测」）。 */
    public State state() {
        return convert(NetworkStatus.state());
    }

    /** 是否已经测出过结果（{@code false} = 还是 {@link State#UNKNOWN}）。 */
    public boolean isKnown() {
        return NetworkStatus.isKnown();
    }

    /**
     * 立刻重测一次（异步，不阻塞）。
     *
     * <p>框架平时会自动测：网络接口变化时立刻重测，另有兜底轮询。
     */
    public void checkNow() {
        NetworkStatus.checkNow();
    }

    /** 是否正在用系统接口事件（Windows 且注册成功）：拔插网线这类变化会立刻被察觉。 */
    public boolean isEventDriven() {
        return NetworkStatus.isEventDriven();
    }

    /**
     * 当前判定来源的可读描述（问系统还是自己探测）—— 写进日志排查假阴性时有用。
     */
    public String source() {
        return NetworkStatus.sourceName();
    }

    /** 域名能不能解析。只测 DNS，不代表能连上互联网。 */
    public boolean canResolve(String host) {
        return NetworkStatus.canResolve(host);
    }

    /**
     * 状态变化回调（在线 ⇄ 离线）。
     *
     * <p><b>在探测线程上触发</b>：要碰 UI 请自己 {@code Platform.runLater}。
     * 同一个监听器重复注册只算一次；用 {@link #removeListener} 取消。
     */
    public void addListener(Consumer<State> listener) {
        if (listener == null) {
            return;
        }
        Consumer<NetworkStatus.State> wrapper = state -> listener.accept(convert(state));
        if (wrappers.putIfAbsent(listener, wrapper) == null) {
            NetworkStatus.addListener(wrapper);
        }
    }

    /** 取消 {@link #addListener} 注册的监听器（传同一个实例）。 */
    public void removeListener(Consumer<State> listener) {
        if (listener == null) {
            return;
        }
        Consumer<NetworkStatus.State> wrapper = wrappers.remove(listener);
        if (wrapper != null) {
            NetworkStatus.removeListener(wrapper);
        }
    }

    private static State convert(NetworkStatus.State state) {
        return switch (state) {
            case ONLINE -> State.ONLINE;
            case OFFLINE -> State.OFFLINE;
            case UNKNOWN -> State.UNKNOWN;
        };
    }
}
