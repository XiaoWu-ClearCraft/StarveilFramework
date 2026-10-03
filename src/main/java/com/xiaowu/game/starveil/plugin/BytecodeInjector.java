package com.xiaowu.game.starveil.plugin;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 字节码注入器
 * 封装 ByteBuddy 提供字节码注入能力
 *
 * <p>核心能力：把标注了 {@link InjectPoint} 的方法映射到插件注册的回调上。
 * 由于平台的默认实现（Windows）往往在插件加载之前就已经被类加载器加载，
 * 这里使用 {@link AgentBuilder.Default#disableClassFormatChanges()} 打开
 * retransform 通道，使 <b>已经加载</b> 的类同样可以被改写。
 */
public class BytecodeInjector {

    private static Instrumentation instrumentation;
    private static ByteBuddy byteBuddy;
    private static boolean initialized = false;

    /**
     * 已经安装过转换器的类。
     *
     * <p>插件现在是<b>分阶段</b>加载的（INIT 时机要在 init 类加载前装好转换器，
     * LAUNCHER 时机再装 Launcher 的），因此 {@link #applyInjectionPoints} 会被调用多次。
     * 已经装过的类必须跳过：重复安装同一转换器会让字节码被处理两遍，
     * 表现为注入逻辑执行两次甚至转换失败。
     */
    private static final Set<String> installedClasses = new java.util.HashSet<>();

    /** 已安装转换器的类数量（测试与日志用）。 */
    public static synchronized int installedClassCount() {
        return installedClasses.size();
    }

    private BytecodeInjector() {}

    /**
     * 初始化 ByteBuddy Agent（必须在插件加载前调用）
     */
    public static synchronized boolean initialize() {
        if (initialized) {
            return true;
        }
        try {
            instrumentation = ByteBuddyAgent.install();
            // 检查是否支持 retransform
            if (!instrumentation.isRetransformClassesSupported()) {
                Logger("WARNING", "[BytecodeInjector] JVM 不支持 retransform，"
                        + "已加载类的注入点（如平台适配）将无法生效");
            } else {
                Logger("INFO", "[BytecodeInjector] JVM 支持 retransform，已加载类可被注入");
            }
            byteBuddy = new ByteBuddy();
            initialized = true;
            Logger("INFO", "[BytecodeInjector] ByteBuddy Agent 已初始化");
            return true;
        } catch (Exception | LinkageError e) {
            Logger("ERROR", "[BytecodeInjector] ByteBuddy Agent 初始化失败: " + e);
            return false;
        }
    }

    public static ByteBuddy getByteBuddy() {
        if (!initialized) {
            throw new IllegalStateException("BytecodeInjector 未初始化");
        }
        return byteBuddy;
    }

    public static Instrumentation getInstrumentation() {
        if (!initialized) {
            throw new IllegalStateException("BytecodeInjector 未初始化");
        }
        return instrumentation;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 应用注入点转换。
     *
     * <p>流程：
     * <ol>
     *   <li>扫描游戏类中标注了 {@link InjectPoint} 的方法，补齐注入点映射；</li>
     *   <li>筛出插件真正注册了回调的注入点；</li>
     *   <li>按声明类分组，逐个类安装转换器（retransform 已加载的类）。</li>
     * </ol>
     */
    public static void applyInjectionPoints(ClassLoader gameClassLoader) {
        if (!initialized) {
            Logger("WARNING", "[BytecodeInjector] 未初始化，跳过注入点应用");
            return;
        }

        registerDiscoveredInjectPoints(gameClassLoader);

        InjectionRegistry registry = InjectionRegistry.getInstance();
        if (registry.getRegisteredPoints().isEmpty()) {
            Logger("INFO", "[BytecodeInjector] 无注入点回调，跳过");
            return;
        }

        Map<String, List<Target>> byClass = groupTargets(registry);
        if (byClass.isEmpty()) {
            Logger("WARNING", "[BytecodeInjector] 插件注册的注入点均未匹配到目标方法，未做任何注入");
            return;
        }

        int applied = byClass.values().stream().mapToInt(List::size).sum();
        Logger("INFO", "[BytecodeInjector] 开始在 " + byClass.size() + " 个类上应用 " + applied + " 个注入点...");

        int success = 0;
        for (Map.Entry<String, List<Target>> entry : byClass.entrySet()) {
            String className = entry.getKey();
            if (installedClasses.contains(className)) {
                // 已经装过。若此时又多出了目标，说明有插件在后期阶段往同一个类上加注入，
                // 这类「迟到的注入」不会生效 —— 明确报出来，而不是静默忽略。
                Logger("WARNING", "[BytecodeInjector] 类 " + className
                        + " 已安装过转换器，本次新增的 " + entry.getValue().size()
                        + " 个注入点将被跳过（请在更早的加载时机注册）");
                continue;
            }
            if (installForClass(className, entry.getValue())) {
                success += entry.getValue().size();
                installedClasses.add(className);
            }
        }
        Logger("INFO", "[BytecodeInjector] 注入完成: " + success + "/" + applied + " 个注入点已生效");
    }

    /**
     * 扫描并注册游戏自带的注入点。已手动注册（插件声明）的映射优先级更高，不会被覆盖。
     */
    private static void registerDiscoveredInjectPoints(ClassLoader gameClassLoader) {
        Map<String, Method> discovered =
                InjectPointScanner.scan(gameClassLoader == null
                        ? BytecodeInjector.class.getClassLoader()
                        : gameClassLoader);
        for (Map.Entry<String, Method> entry : discovered.entrySet()) {
            if (!InjectPointMethods.contains(entry.getKey())) {
                InjectPointMethods.register(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 把「有回调的注入点」按声明类分组。
     */
    private static Map<String, List<Target>> groupTargets(InjectionRegistry registry) {
        Map<String, List<Target>> byClass = new LinkedHashMap<>();
        for (Map.Entry<String, Method> entry : InjectPointMethods.getAll().entrySet()) {
            String pointId = entry.getKey();
            if (!registry.hasCallbacks(pointId)) {
                continue;
            }
            Target target = new Target(pointId, entry.getValue());
            byClass.computeIfAbsent(target.method.getDeclaringClass().getName(), k -> new ArrayList<>())
                   .add(target);
        }

        for (String pointId : registry.getRegisteredPoints()) {
            if (!InjectPointMethods.contains(pointId)) {
                Logger("WARNING", "[BytecodeInjector] 注入点 '" + pointId
                        + "' 未找到对应方法（ID 拼写错误，或该注入点未被 @InjectPoint 标注）");
            }
        }
        return byClass;
    }

    /**
     * 为单个类安装转换器，并对已经加载的类显式触发 retransform。
     *
     * <p>平台默认实现（Windows 实现）在插件加载之前就已经被核心调用过，类早已加载完成，
     * 因此不能只依赖「等类加载时再转换」。这里对已加载的类使用
     * {@link AgentBuilder.RedefinitionListenable.WithImplicitDiscoveryStrategy#redefineOnly}
     * 明确要求 retransform；对尚未加载的类，注册的转换器会在其加载时生效。
     */
    private static boolean installForClass(String className, List<Target> targets) {
        try {
            Class<?> loaded = findLoadedClass(className);
            java.util.concurrent.atomic.AtomicBoolean transformed = new java.util.concurrent.atomic.AtomicBoolean();

            AgentBuilder.RedefinitionListenable.WithImplicitDiscoveryStrategy builder =
                    new AgentBuilder.Default()
                            .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                            .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);

            AgentBuilder agent = (loaded != null && instrumentation.isModifiableClass(loaded))
                    ? builder.redefineOnly(loaded)
                    : builder;

            agent.with(new AgentBuilder.Listener.Adapter() {
                        @Override
                        public void onTransformation(
                                net.bytebuddy.description.type.TypeDescription typeDescription,
                                ClassLoader classLoader, net.bytebuddy.utility.JavaModule module,
                                boolean loaded, net.bytebuddy.dynamic.DynamicType dynamicType) {
                            transformed.set(true);
                            Logger("DEBUG", "[BytecodeInjector] 已转换类: " + typeDescription.getName()
                                    + " (loaded=" + loaded + ")");
                        }

                        @Override
                        public void onError(String typeName, ClassLoader classLoader,
                                            net.bytebuddy.utility.JavaModule module,
                                            boolean loaded, Throwable throwable) {
                            Logger("ERROR", "[BytecodeInjector] 转换类 " + typeName + " 失败 (loaded="
                                    + loaded + "): " + throwable);
                        }
                    })
                    .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                    .type(ElementMatchers.named(className))
                    .transform((dynBuilder, typeDescription, classLoader, module, protectionDomain) ->
                            applyTargets(dynBuilder, targets))
                    .installOn(instrumentation);

            for (Target target : targets) {
                String where = target.method.getDeclaringClass().getSimpleName()
                        + "." + target.method.getName();
                if (loaded == null) {
                    Logger("INFO", "[BytecodeInjector] 注入点已注册（类尚未加载，加载时生效）: "
                            + target.pointId + " -> " + where);
                } else if (transformed.get()) {
                    Logger("INFO", "[BytecodeInjector] 注入点已生效（已 retransform）: "
                            + target.pointId + " -> " + where);
                } else {
                    Logger("ERROR", "[BytecodeInjector] 注入点未能生效: " + target.pointId
                            + " -> " + where + "（已加载的类没有被转换）");
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            Logger("ERROR", "[BytecodeInjector] 在类 " + className + " 上应用注入点失败: " + t);
            return false;
        }
    }

    /** 在已加载的类中按名字查找目标类；未加载时返回 null。 */
    private static Class<?> findLoadedClass(String className) {
        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
            if (loaded.getName().equals(className)) {
                return loaded;
            }
        }
        return null;
    }

    private static DynamicType.Builder<?> applyTargets(DynamicType.Builder<?> builder, List<Target> targets) {
        DynamicType.Builder<?> result = builder;
        for (Target target : targets) {
            result = result
                    .method(ElementMatchers.named(target.method.getName())
                            .and(ElementMatchers.takesArguments(target.method.getParameterCount())))
                    .intercept(target.implementation());
        }
        return result;
    }

    /**
     * 手动注册注入点映射（游戏侧 / 插件侧调用）。
     *
     * <p>用于给没有标注 {@link InjectPoint} 的方法（例如插件自带类）绑定注入点 ID。
     * 必须在 {@link #applyInjectionPoints} 之前、即插件的 {@code onLoad} 中调用。
     *
     * @param pointId     注入点 ID
     * @param targetClass 目标类
     * @param methodName  目标方法名
     * @param paramTypes  方法参数类型
     */
    public static void registerInjectPointMapping(String pointId, Class<?> targetClass,
                                                  String methodName, Class<?>... paramTypes) {
        try {
            Method method = targetClass.getDeclaredMethod(methodName, paramTypes);
            InjectPointMethods.override(pointId, method);
            Logger("INFO", "[BytecodeInjector] 注册注入点映射: " + pointId + " -> "
                    + targetClass.getSimpleName() + "." + methodName);
        } catch (NoSuchMethodException e) {
            Logger("ERROR", "[BytecodeInjector] 注册注入点映射失败: " + pointId + " - 方法不存在");
        } catch (RuntimeException e) {
            Logger("ERROR", "[BytecodeInjector] 注册注入点映射失败: " + pointId + " - " + e.getMessage());
        }
    }

    /** 一次注入的目标：注入点 ID + 目标方法。 */
    private static final class Target {
        private final String pointId;
        private final Method method;

        Target(String pointId, Method method) {
            this.pointId = pointId;
            this.method = method;
        }

        /**
         * 把注入点 ID 作为常量绑定到委托方法的 {@link PointId} 参数上。
         */
        Implementation implementation() {
            return MethodDelegation.withDefaultConfiguration()
                    .withBinders(new PointIdBinder(pointId))
                    .to(ReplaceInterceptor.class);
        }

        String timing() {
            return "REPLACE";
        }
    }
}
