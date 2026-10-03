package com.xiaowu.game.starveil.startup;

/**
 * 启动参数处理器接口
 * 用于注册和处理游戏启动时的命令行参数
 */
public interface StartupArgumentHandler {

    /**
     * 获取此处理器要捕获的参数名称（不带-号）
     * 例如：对于参数 "-debug"，返回 "debug"
     *
     * @return 参数名称
     */
    String getArgumentName();

    /**
     * 获取此处理器的执行模式
     *
     * @return 执行模式（PARALLEL 或 OVERRIDE）
     */
    ExecutionMode getExecutionMode();

    /**
     * 判断此参数是否需要参数值
     * 例如："-cmd chapter1" 需要参数值，返回 true
     * "-debug" 不需要参数值，返回 false
     *
     * @return true 如果需要参数值，false 如果不需要
     */
    boolean requiresValue();

    /**
     * 处理参数
     *
     * @param value 参数值（如果不需要参数值，则为 null）
     * @param allArgs 所有启动参数数组
     * @return true 如果处理成功，false 如果处理失败
     */
    boolean handle(String value, String[] allArgs);

    /**
     * 获取处理器的描述信息（用于调试和文档）
     *
     * @return 描述信息
     */
    String getDescription();
}