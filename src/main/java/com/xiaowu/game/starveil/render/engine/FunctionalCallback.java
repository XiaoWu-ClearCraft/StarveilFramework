package com.xiaowu.game.starveil.render.engine;

/**
 * 函数式回调接口
 * @param <T> 参数类型
 */
@FunctionalInterface
public interface FunctionalCallback<T> {
    void run(T event);
}
