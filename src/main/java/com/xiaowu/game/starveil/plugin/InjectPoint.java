package com.xiaowu.game.starveil.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注入点注解
 * 游戏开发者在方法上标记此注解，允许插件通过注入点 API 修改该方法行为
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InjectPoint {
    /**
     * 注入点唯一标识
     */
    String value();
}
