package com.xiaowu.game.starveil.startup;

/**
 * 启动参数处理器基础抽象类
 * 提供一些通用的默认实现和工具方法
 */
public abstract class BaseStartupArgumentHandler implements StartupArgumentHandler {
    protected String argumentName;
    protected ExecutionMode executionMode;
    protected boolean requiresValue;
    protected String description;

    /**
     * 构造函数
     *
     * @param argumentName 参数名称（不带-号）
     * @param executionMode 执行模式
     * @param requiresValue 是否需要参数值
     * @param description 描述信息
     */
    public BaseStartupArgumentHandler(String argumentName, ExecutionMode executionMode,
                                      boolean requiresValue, String description) {
        this.argumentName = argumentName;
        this.executionMode = executionMode;
        this.requiresValue = requiresValue;
        this.description = description;
    }

    @Override
    public String getArgumentName() {
        return argumentName;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public boolean requiresValue() {
        return requiresValue;
    }

    @Override
    public String getDescription() {
        return description;
    }

    /**
     * 从参数数组中提取指定参数的值
     *
     * @param args 参数数组
     * @param argumentName 参数名称（带-号，如 "-config"）
     * @return 参数值，如果不存在则返回 null
     */
    protected String extractArgumentValue(String[] args, String argumentName) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(argumentName) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    /**
     * 检查参数数组中是否包含指定参数
     *
     * @param args 参数数组
     * @param argumentName 参数名称（带-号，如 "-debug"）
     * @return true 如果包含，false 如果不包含
     */
    protected boolean hasArgument(String[] args, String argumentName) {
        for (String arg : args) {
            if (arg.equals(argumentName)) {
                return true;
            }
        }
        return false;
    }
}