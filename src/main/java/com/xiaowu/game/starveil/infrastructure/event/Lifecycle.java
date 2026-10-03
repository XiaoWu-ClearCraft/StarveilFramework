package com.xiaowu.game.starveil.infrastructure.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 生命周期事件总线 —— 框架在关键执行点「广播」，谁关心谁监听。
 *
 * <p><b>为什么需要它</b>：框架里原本是「到点了直接调某个类的方法」。
 * 每加一个关心这件事的东西，就得回去改那个调用点；插件想插一脚更是无从下手
 * （只能改框架源码，而这正是我们极力避免的）。改成广播之后，发布方不需要知道
 * 有谁在听，监听方也不需要知道是谁广播的 —— 两边都只依赖事件本身。
 *
 * <p><b>没有监听者时就是一次空操作。</b> 广播不返回任何东西、也不保证有人收到；
 * 发布方不需要为「没人听」写任何兜底分支。这正是它存在的意义：
 * 发布点可以到处加，而不会给框架增加耦合。
 *
 * <pre>
 *   // 监听（例如内容初始化、插件、或框架自己的某个模块）
 *   Lifecycle.Subscription sub = Lifecycle.onChapterChanged(e -&gt;
 *           Logger("INFO", "进入第 " + e.chapter() + " 章"));
 *
 *   // 不再关心时取消；返回的句柄被丢弃也完全没关系
 *   sub.cancel();
 * </pre>
 *
 * <h2>规则</h2>
 * <ul>
 *   <li><b>同步分发</b>：监听者在发布线程上被直接调用，顺序即注册顺序。
 *       需要切线程请自己在监听者里切。</li>
 *   <li><b>一个监听者出错不影响其它监听者</b>，也不影响广播方 ——
 *       出错只记 ERROR 日志，其余照常执行。</li>
 *   <li><b>发布方只管发</b>，不等待、不收集返回值；</li>
 *   <li>事件本身是<b>不可变对象</b>（见 {@link Lifecycle} 里那些 record），
 *       监听者不可能改到别人看到的内容。</li>
 * </ul>
 *
 * <h2>和另外两个事件机制的分工</h2>
 * <ul>
 *   <li>{@code game.ecs.EventBus} —— 单局游戏内、每局重建的 ECS 事件，
 *       用于实体之间的通信（例如「NPC 死亡」）；</li>
 *   <li>{@code game.event.EventCallbackManager} —— 剧情脚本要 await 的信号
 *       （{@code s.await("my_signal")}）；</li>
 *   <li><b>本类</b> —— 框架进程级的生命周期广播，跨局存在，
 *       用于「某件事发生了，谁关心谁处理」。</li>
 * </ul>
 */
public final class Lifecycle {

    /** 按事件类名分组的监听者。 */
    private static final Map<String, CopyOnWriteArrayList<Entry>> LISTENERS = new ConcurrentHashMap<>();

    /** 已经发出过、但一个监听者都没有的事件计数（调试用）。 */
    private static final Map<String, Integer> DISCARDED = new ConcurrentHashMap<>();

    private Lifecycle() {
    }

    // ==================== 监听 ====================

    /**
     * 监听某类事件。
     *
     * <p>同一个监听者重复注册会被重复调用（按注册次数），需要去重请自己保存句柄并取消。
     *
     * @param type     事件类型，见 {@link FrameworkReady} / {@link MenuShown} 等
     * @param listener 事件到达时被调用；不会收到 null
     * @return 订阅句柄，用于 {@code cancel()}；<b>直接丢弃也完全可以</b>
     */
    public static <T> Subscription<T> on(Class<T> type, Consumer<T> listener) {
        if (type == null || listener == null) {
            Logger("WARNING", "事件监听参数无效：type 或 listener 为 null，已忽略");
            return new Subscription<>(null, null);
        }
        Entry entry = new Entry(listener, false);
        LISTENERS.computeIfAbsent(keyOf(type), k -> new CopyOnWriteArrayList<>()).add(entry);
        Logger("DEBUG", "已注册事件监听: " + type.getSimpleName());
        return new Subscription<>(type, entry);
    }

    /** 只监听一次的事件监听，收到第一件后自动取消。 */
    public static <T> Subscription<T> once(Class<T> type, Consumer<T> listener) {
        if (type == null || listener == null) {
            Logger("WARNING", "事件监听参数无效：type 或 listener 为 null，已忽略");
            return new Subscription<>(null, null);
        }
        Entry entry = new Entry(listener, true);
        LISTENERS.computeIfAbsent(keyOf(type), k -> new CopyOnWriteArrayList<>()).add(entry);
        Logger("DEBUG", "已注册一次性事件监听: " + type.getSimpleName());
        return new Subscription<>(type, entry);
    }

    // ==================== 广播 ====================

    /**
     * 广播一个事件。没有监听者时什么都不做。
     *
     * <p>事件类型取自 {@code event.getClass()}，因此传子类实例时只会通知
     * 「监听该子类」的人 —— 需要连父类一起通知的话请显式多发一次。
     */
    public static void publish(Object event) {
        if (event == null) {
            return;
        }
        Class<?> type = event.getClass();
        String key = keyOf(type);

        CopyOnWriteArrayList<Entry> entries = LISTENERS.get(key);
        if (entries == null || entries.isEmpty()) {
            DISCARDED.merge(key, 1, Integer::sum);
            return;
        }

        for (Entry entry : entries) {
            if (entry.cancelled) {
                continue;
            }
            if (entry.once) {
                // 先摘掉再调用：万一监听者里又发布同类事件，不会重复触发它
                entries.remove(entry);
                entry.cancelled = true;
            }
            try {
                accept(entry.listener, event);
            } catch (Throwable t) {
                // 一个监听者出错不该影响其它监听者，也不该把广播方拖垮
                Logger("ERROR", "事件监听者执行失败 (" + type.getSimpleName() + "): " + t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void accept(Consumer<?> listener, Object event) {
        ((Consumer<Object>) listener).accept(event);
    }

    // ==================== 查询 / 清理 ====================

    /** 某类事件的监听者数量（测试与调试用）。 */
    public static int listenerCount(Class<?> type) {
        if (type == null) {
            return 0;
        }
        CopyOnWriteArrayList<Entry> entries = LISTENERS.get(keyOf(type));
        if (entries == null) {
            return 0;
        }
        int n = 0;
        for (Entry e : entries) {
            if (!e.cancelled) {
                n++;
            }
        }
        return n;
    }

    /** 已注册过监听者的全部事件类型名（调试用）。 */
    public static List<String> listenerTypes() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, CopyOnWriteArrayList<Entry>> e : LISTENERS.entrySet()) {
            for (Entry entry : e.getValue()) {
                if (!entry.cancelled) {
                    out.add(e.getKey());
                    break;
                }
            }
        }
        return out;
    }

    /** 清空所有监听者（测试用；正常游戏不该调用）。 */
    static void resetForTest() {
        LISTENERS.clear();
        DISCARDED.clear();
    }

    /**
     * 用类名而不是 {@code Class} 对象做键。
     *
     * <p>插件可能由独立的类加载器加载，同一个事件类在两个加载器里会得到两个不同的
     * {@code Class} 对象 —— 用 {@code Class} 当键会让「插件注册的监听」和
     * 「框架发布的广播」互相看不见。类名不受这个问题影响。
     */
    static String keyOf(Class<?> type) {
        return type.getName();
    }

    // ==================== 内部 ====================

    private static final class Entry {
        final Consumer<?> listener;
        final boolean once;
        volatile boolean cancelled;

        Entry(Consumer<?> listener, boolean once) {
            this.listener = listener;
            this.once = once;
        }
    }

    /** 订阅句柄。丢掉它并不会导致监听失效 —— 只有显式 {@code cancel()} 才会。 */
    public static final class Subscription<T> {
        private final Class<T> type;
        private final Entry entry;

        Subscription(Class<T> type, Entry entry) {
            this.type = type;
            this.entry = entry;
        }

        /** 取消监听。重复调用无副作用。 */
        public void cancel() {
            if (entry == null || entry.cancelled) {
                return;
            }
            entry.cancelled = true;
            CopyOnWriteArrayList<Entry> entries = LISTENERS.get(keyOf(type));
            if (entries != null) {
                entries.remove(entry);
            }
        }

        /** 是否仍在监听（一次性监听触发过之后即为 false）。 */
        public boolean isActive() {
            return entry != null && !entry.cancelled;
        }
    }
}
