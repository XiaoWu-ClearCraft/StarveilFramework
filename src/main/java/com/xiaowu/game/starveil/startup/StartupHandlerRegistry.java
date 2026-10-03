package com.xiaowu.game.starveil.startup;

import java.util.*;
import java.util.concurrent.*;

/**
 * 启动参数处理器注册管理器
 * 负责注册、解析和执行启动参数处理器
 */
public class StartupHandlerRegistry {
    private static StartupHandlerRegistry instance;
    private final Map<String, StartupArgumentHandler> handlers;
    private final ExecutorService executorService;

    private StartupHandlerRegistry() {
        this.handlers = new ConcurrentHashMap<>();
        // 使用缓存线程池来执行PARALLEL模式的处理器
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * 获取单例实例
     *
     * @return StartupHandlerRegistry 实例
     */
    public static synchronized StartupHandlerRegistry getInstance() {
        if (instance == null) {
            instance = new StartupHandlerRegistry();
        }
        return instance;
    }

    /**
     * 注册一个参数处理器
     *
     * @param handler 参数处理器
     * @throws IllegalArgumentException 如果已存在相同名称的处理器
     */
    public void registerHandler(StartupArgumentHandler handler) {
        if (handlers.containsKey(handler.getArgumentName())) {
            throw new IllegalArgumentException(
                "参数处理器 '" + handler.getArgumentName() + "' 已存在"
            );
        }
        handlers.put(handler.getArgumentName(), handler);
    }

    /**
     * 注销一个参数处理器
     *
     * @param argumentName 参数名称
     * @return 被移除的处理器，如果不存在则返回 null
     */
    public StartupArgumentHandler unregisterHandler(String argumentName) {
        return handlers.remove(argumentName);
    }

    /**
     * 处理启动参数
     *
     * @param args 启动参数数组
     * @return true 如果需要阻止Launcher主代码执行（OVERRIDE模式），false 如果继续执行
     */
    public boolean processArguments(String[] args) {
        List<StartupArgumentHandler> overrideHandlers = new ArrayList<>();
        List<StartupArgumentHandler> parallelHandlers = new ArrayList<>();
        List<StartupArgumentHandler> blockingHandlers = new ArrayList<>();

        // 解析参数并分类处理器
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            // 检查是否是参数格式（以 - 开头）
            if (!arg.startsWith("-") || arg.length() <= 1) {
                continue;
            }

            String argumentName = arg.substring(1); // 去掉 - 号
            StartupArgumentHandler handler = handlers.get(argumentName);

            if (handler == null) {
                continue;
            }

            // 获取参数值（如果需要）
            String value = null;
            if (handler.requiresValue()) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    value = args[i + 1];
                    i++; // 跳过参数值
                } else {
                    System.err.println("错误: 参数 -" + argumentName + " 需要一个值");
                    continue;
                }
            }

            // 根据执行模式分类
            if (handler.getExecutionMode() == ExecutionMode.OVERRIDE) {
                overrideHandlers.add(handler);
            } else if (handler.getExecutionMode() == ExecutionMode.BLOCKING) {
                blockingHandlers.add(handler);
            } else {
                parallelHandlers.add(handler);
            }
        }

        // 执行 BLOCKING 模式的处理器（同步执行，在游戏主代码之前执行）
        for (StartupArgumentHandler handler : blockingHandlers) {
            final String argumentName = handler.getArgumentName();
            final String value = extractValue(handler, args);

            try {
                System.out.println("执行阻塞处理器: -" + argumentName);
                boolean success = handler.handle(value, args);
                if (!success) {
                    System.err.println("参数处理器 -" + argumentName + " 执行失败");
                } else {
                    System.out.println("阻塞处理器 -" + argumentName + " 执行完成");
                }
            } catch (Exception e) {
                System.err.println("参数处理器 -" + argumentName + " 执行失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 执行 PARALLEL 模式的处理器（同步执行，以便处理器之间可以共享状态）
        for (StartupArgumentHandler handler : parallelHandlers) {
            final String argumentName = handler.getArgumentName();
            final String value = extractValue(handler, args);

            try {
                handler.handle(value, args);
            } catch (Exception e) {
                System.err.println("参数处理器 -" + argumentName + " 执行失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 如果有 OVERRIDE 模式的处理器被触发，则阻止 Launcher 主代码执行
        if (!overrideHandlers.isEmpty()) {
            // 执行 OVERRIDE 模式的处理器（同步执行）
            for (StartupArgumentHandler handler : overrideHandlers) {
                final String argumentName = handler.getArgumentName();
                final String value = extractValue(handler, args);

                try {
                    boolean success = handler.handle(value, args);
                    if (!success) {
                        System.err.println("参数处理器 -" + argumentName + " 执行失败");
                    }
                } catch (Exception e) {
                    System.err.println("参数处理器 -" + argumentName + " 执行失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // 关闭执行器
            shutdown();
            return true;
        }

        return false;
    }

    /**
     * 从参数数组中提取指定处理器的参数值
     *
     * @param handler 参数处理器
     * @param args 参数数组
     * @return 参数值，如果不需要值或不存在则返回 null
     */
    private String extractValue(StartupArgumentHandler handler, String[] args) {
        if (!handler.requiresValue()) {
            return null;
        }

        String argName = "-" + handler.getArgumentName();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(argName) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }

    /**
     * 获取所有已注册的参数处理器
     *
     * @return 参数处理器列表
     */
    public Collection<StartupArgumentHandler> getRegisteredHandlers() {
        return new ArrayList<>(handlers.values());
    }

    /**
     * 获取参数处理器
     *
     * @param argumentName 参数名称
     * @return 参数处理器，如果不存在则返回 null
     */
    public StartupArgumentHandler getHandler(String argumentName) {
        return handlers.get(argumentName);
    }

    /**
     * 检查是否已注册指定参数的处理器
     *
     * @param argumentName 参数名称
     * @return true 如果已注册，false 如果未注册
     */
    public boolean hasHandler(String argumentName) {
        return handlers.containsKey(argumentName);
    }

    /**
     * 关闭执行器
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}