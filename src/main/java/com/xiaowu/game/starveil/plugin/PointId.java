package com.xiaowu.game.starveil.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 把注入点 ID 作为常量绑定到 {@code MethodDelegation} 的委托方法参数上。
 *
 * <p>为什么不直接用 {@code @Origin Method}：那会让 ByteBuddy 在被插桩的类里
 * 新增静态缓存字段，而 retransform / redefine 不允许改变类结构，
 * 结果是整次转换被 JVM 丢弃（且不会有任何报错）。
 * 注入点 ID 是编译期常量，直接绑定最稳妥。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PointId {
}
