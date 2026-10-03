package com.xiaowu.game.starveil.plugin;

/**
 * 注入时机。
 *
 * <p>目前只支持 {@link #REPLACE}：完全替换目标方法的实现。
 * 这正是平台适配需要的语义——把核心的 Windows 实现整个换成其它平台的实现。
 *
 * <p>为什么没有 BEFORE / AFTER：平台的默认实现在插件加载时 <b>已经完成类加载</b>，
 * 只能通过 JVM 的 retransform 改写方法体，而 retransform 不允许增删字段或方法。
 * 「在原方法前后插入回调」需要把原方法体抽成一个新方法（ByteBuddy 的
 * {@code @SuperCall} / Advice 委托器都这么做），在 retransform 下无法实现。
 * 需要「包装」语义的插件可以直接 REPLACE，并在自己的实现里调用游戏公开 API。
 */
public enum InjectionTiming {
    /**
     * 完全替换原方法
     */
    REPLACE
}
