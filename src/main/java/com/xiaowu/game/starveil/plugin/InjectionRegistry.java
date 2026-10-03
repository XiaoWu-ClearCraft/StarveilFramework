package com.xiaowu.game.starveil.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 注入点回调
 */
class InjectionCallback {
    final InjectionTiming timing;
    final InvocationHandler handler;
    final String pluginId;

    InjectionCallback(InjectionTiming timing, InvocationHandler handler, String pluginId) {
        this.timing = timing;
        this.handler = handler;
        this.pluginId = pluginId;
    }
}

/**
 * 注入点注册表
 * 管理插件注册的注入点回调
 */
public class InjectionRegistry {

    private static final InjectionRegistry INSTANCE = new InjectionRegistry();
    private final Map<String, List<InjectionCallback>> callbacks = new ConcurrentHashMap<>();

    public static InjectionRegistry getInstance() {
        return INSTANCE;
    }

    private InjectionRegistry() {}

    /**
     * 注册注入点回调
     */
    public void register(String pointId, InjectionTiming timing, InvocationHandler handler, String pluginId) {
        if (pointId == null || pointId.isEmpty()) {
            throw new IllegalArgumentException("注入点 ID 不能为空");
        }
        if (handler == null) {
            throw new IllegalArgumentException("处理器不能为空");
        }
        callbacks.computeIfAbsent(pointId, k -> Collections.synchronizedList(new ArrayList<>()))
                 .add(new InjectionCallback(timing, handler, pluginId));
        Logger("INFO", "[InjectionRegistry] 插件 " + pluginId + " 注册注入点: " + pointId + " (" + timing + ")");
    }

    /**
     * 获取某注入点的所有回调
     */
    public List<InjectionCallback> getCallbacks(String pointId) {
        return callbacks.getOrDefault(pointId, Collections.emptyList());
    }

    /**
     * 判断某注入点是否注册了任何回调。
     */
    public boolean hasCallbacks(String pointId) {
        List<InjectionCallback> all = callbacks.get(pointId);
        return all != null && !all.isEmpty();
    }

    /**
     * 取该注入点上最后注册的 REPLACE 处理器；没有则返回 null。
     * 后注册者覆盖先注册者，便于插件之间互相覆盖平台实现。
     */
    InvocationHandler findReplaceHandler(String pointId) {
        List<InjectionCallback> all = callbacks.get(pointId);
        if (all == null || all.isEmpty()) {
            return null;
        }
        InvocationHandler found = null;
        synchronized (all) {
            for (InjectionCallback cb : all) {
                if (cb.timing == InjectionTiming.REPLACE) {
                    found = cb.handler;
                }
            }
        }
        return found;
    }

    /**
     * 获取所有已注册的注入点 ID
     */
    public Set<String> getRegisteredPoints() {
        return callbacks.keySet();
    }

    /**
     * 获取所有已注册注入点的方法映射快照（注入点 ID → 目标方法）。
     */
    public Map<String, Method> getRegisteredMethods() {
        return InjectPointMethods.getAll();
    }

    /**
     * 清除某插件注册的所有回调。
     *
     * <p>注意：字节码注入是单向的，卸载插件不会恢复原实现——被替换的方法会失去
     * 处理器，只能返回默认值。因此卸载只应在进程退出阶段进行。
     */
    public void unregisterPlugin(String pluginId) {
        for (Map.Entry<String, List<InjectionCallback>> entry : callbacks.entrySet()) {
            entry.getValue().removeIf(cb -> cb.pluginId.equals(pluginId));
        }
        // 清理空列表
        callbacks.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * 清除所有回调
     */
    public void clear() {
        callbacks.clear();
    }
}
