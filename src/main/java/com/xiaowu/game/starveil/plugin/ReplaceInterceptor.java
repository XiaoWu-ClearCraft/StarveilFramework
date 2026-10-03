package com.xiaowu.game.starveil.plugin;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 注入点委托器：完全替换目标方法，改为调用插件注册的处理器。
 *
 * <p>这里刻意 <b>不使用</b> {@code @Origin Method} 与 {@code @SuperCall}：
 * <ul>
 *   <li>{@code @Origin Method} 会让 ByteBuddy 在被插桩的类里新增静态缓存字段；</li>
 *   <li>{@code @SuperCall} 需要把原方法体抽成一个新方法。</li>
 * </ul>
 * 两者都属于「改变类结构」，而平台默认实现在插件加载时早已完成类加载，
 * 只能通过 retransform 改写方法体 —— JVM 明确禁止在 retransform 中增删字段/方法。
 * 因此委托目标只保留 {@code @PointId}（常量）、{@code @This}、{@code @AllArguments}，
 * 生成的字节码只替换方法体，retransform 可以正常应用。
 *
 * <p>代价：注入是「单向」的，不会回退到原实现。处理器返回 {@code null} 且方法有
 * 返回值时，返回该类型的零值；处理器抛异常时同样返回零值（并记录 ERROR 日志）。
 */
public final class ReplaceInterceptor {

    private ReplaceInterceptor() {}

    @RuntimeType
    public static Object intercept(@PointId String pointId,
                                   @This(optional = true) Object self,
                                   @AllArguments Object[] args) {
        Method method = InjectPointMethods.get(pointId);
        InvocationHandler handler = InjectionRegistry.getInstance().findReplaceHandler(pointId);

        if (handler == null) {
            Logger("ERROR", "[ReplaceInterceptor] 注入点 " + pointId + " 没有可用的处理器，返回默认值");
            return defaultValue(method);
        }

        try {
            Object result = handler.invoke(self, method, args == null ? new Object[0] : args);
            if (result == null && method != null && method.getReturnType() != void.class) {
                return defaultValue(method);
            }
            return result;
        } catch (Throwable t) {
            Logger("ERROR", "[ReplaceInterceptor] 注入点 " + pointId + " 执行异常，返回默认值: " + t);
            return defaultValue(method);
        }
    }

    /**
     * 基本类型返回值不能是 null，这里给出零值。
     */
    private static Object defaultValue(Method method) {
        if (method == null || !method.getReturnType().isPrimitive()) {
            return null;
        }
        Class<?> type = method.getReturnType();
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0d;
        if (type == char.class) return '\0';
        return null;
    }
}
