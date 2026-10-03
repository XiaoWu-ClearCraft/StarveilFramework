package com.xiaowu.game.starveil.plugin;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注入点方法映射表。
 *
 * <p>维护「注入点 ID → 目标方法」以及反向的「目标方法 → 注入点 ID」映射。
 * 反向映射用于运行时委托器：字节码拦截器拿到的是 {@link Method} 对象，
 * 需要据此反查插件注册在该注入点上的回调。
 *
 * <p>映射来源有两处：
 * <ul>
 *   <li>启动时扫描游戏类中标注了 {@link InjectPoint} 的方法（自动注册）；</li>
 *   <li>{@link BytecodeInjector#registerInjectPointMapping} 的手动注册（插件可调用）。</li>
 * </ul>
 */
public final class InjectPointMethods {

    private static final Map<String, Method> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, String> ID_BY_METHOD = new ConcurrentHashMap<>();

    private InjectPointMethods() {}

    /**
     * 注册一个注入点映射。
     *
     * @param pointId 注入点 ID
     * @param method  目标方法
     * @return true 表示注册成功；若该 ID 已被其它方法占用则返回 false
     */
    public static boolean register(String pointId, Method method) {
        if (pointId == null || pointId.isEmpty() || method == null) {
            return false;
        }
        Method existing = BY_ID.putIfAbsent(pointId, method);
        if (existing != null && !existing.equals(method)) {
            return false;
        }
        ID_BY_METHOD.put(keyOf(method), pointId);
        return true;
    }

    /**
     * 覆盖式注册（后注册者生效），用于插件显式声明的注入点。
     */
    public static void override(String pointId, Method method) {
        if (pointId == null || pointId.isEmpty() || method == null) {
            return;
        }
        Method previous = BY_ID.put(pointId, method);
        if (previous != null) {
            ID_BY_METHOD.remove(keyOf(previous), pointId);
        }
        ID_BY_METHOD.put(keyOf(method), pointId);
    }

    public static boolean contains(String pointId) {
        return BY_ID.containsKey(pointId);
    }

    public static Method get(String pointId) {
        return BY_ID.get(pointId);
    }

    /**
     * 由目标方法反查注入点 ID，未注册时返回 null。
     */
    public static String pointIdOf(Method method) {
        return method == null ? null : ID_BY_METHOD.get(keyOf(method));
    }

    public static Map<String, Method> getAll() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(BY_ID));
    }

    public static void clear() {
        BY_ID.clear();
        ID_BY_METHOD.clear();
    }

    /**
     * 目标方法的唯一键：声明类名#方法名(参数类型1,参数类型2)。
     * 字节码重定义后方法签名不变，因此该键在 retransform 前后保持稳定。
     */
    public static String keyOf(Method method) {
        StringBuilder sb = new StringBuilder(96);
        sb.append(method.getDeclaringClass().getName())
          .append('#')
          .append(method.getName())
          .append('(');
        for (Class<?> param : method.getParameterTypes()) {
            sb.append(param.getName()).append(',');
        }
        return sb.append(')').toString();
    }
}
