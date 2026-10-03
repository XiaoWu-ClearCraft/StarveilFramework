package com.xiaowu.game.starveil.plugin;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.platform.common.SystemDetector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注入管道测试。
 *
 * <p>验证平台适配最关键的几件事：
 * <ol>
 *   <li>{@link InjectPointScanner} 能发现游戏自带的 {@code @InjectPoint} 方法；</li>
 *   <li>注入可以改写 <b>已经加载</b> 的类（retransform）——
 *       核心的 Windows 平台实现在插件加载前就已经被调用过，这是插件仍能接管的前提；</li>
 *   <li>实例方法能拿到 {@code this}，静态方法能正常注入；</li>
 *   <li>基本类型 / void 返回值的处理不会炸。</li>
 * </ol>
 */
class InjectionPipelineTest {

    @BeforeAll
    static void enableDebugLogging() {
        LoggerManager.initializeLogger();
        LoggerManager.DEBUG = true;
    }

    // ==================== 测试目标 ====================

    static class StaticTarget {
        static String value() {
            return "original";
        }
    }

    static class InstanceTarget {
        String who() {
            return "original";
        }
    }

    static class PrimitiveTarget {
        static int number() {
            return 42;
        }
    }

    static class VoidTarget {
        static final AtomicInteger CALLS = new AtomicInteger();
        static final AtomicInteger INJECTED = new AtomicInteger();

        static void run() {
            CALLS.incrementAndGet();
        }
    }

    // ==================== 测试 ====================

    @Test
    void replaceRewritesAlreadyLoadedStaticMethod() {
        // 先调用一次，强制类加载器加载该类（模拟核心在插件加载前就已经用过平台实现）
        assertEquals("original", StaticTarget.value());

        assertTrue(BytecodeInjector.initialize(), "ByteBuddy Agent 应初始化成功");

        BytecodeInjector.registerInjectPointMapping(
                "test.static", StaticTarget.class, "value");
        InjectionRegistry.getInstance().register(
                "test.static", InjectionTiming.REPLACE,
                (proxy, method, args) -> "injected", "test-plugin");

        BytecodeInjector.applyInjectionPoints(getClass().getClassLoader());

        assertEquals("injected", StaticTarget.value(),
                "REPLACE 注入点应改写已经加载的类");
    }

    @Test
    void replacePassesInstanceToHandler() {
        InstanceTarget target = new InstanceTarget();
        assertEquals("original", target.who());

        assertTrue(BytecodeInjector.initialize());

        BytecodeInjector.registerInjectPointMapping(
                "test.instance", InstanceTarget.class, "who");
        InjectionRegistry.getInstance().register(
                "test.instance", InjectionTiming.REPLACE,
                (proxy, method, args) -> proxy == target ? "instance-injected" : "wrong-proxy",
                "test-plugin");

        BytecodeInjector.applyInjectionPoints(getClass().getClassLoader());

        assertEquals("instance-injected", target.who(),
                "实例方法注入应把 this 传给处理器");
    }

    @Test
    void handlerReturningNullYieldsZeroForPrimitiveReturn() {
        assertEquals(42, PrimitiveTarget.number());

        assertTrue(BytecodeInjector.initialize());

        BytecodeInjector.registerInjectPointMapping(
                "test.primitive", PrimitiveTarget.class, "number");
        InjectionRegistry.getInstance().register(
                "test.primitive", InjectionTiming.REPLACE,
                (proxy, method, args) -> null, "test-plugin");

        BytecodeInjector.applyInjectionPoints(getClass().getClassLoader());

        assertEquals(0, PrimitiveTarget.number(),
                "处理器返回 null 时基本类型返回值应为零值而不是抛 NPE");
    }

    @Test
    void replaceWorksForVoidMethods() {
        VoidTarget.CALLS.set(0);
        VoidTarget.run();
        assertEquals(1, VoidTarget.CALLS.get(), "注入前应执行原实现");

        assertTrue(BytecodeInjector.initialize());

        BytecodeInjector.registerInjectPointMapping(
                "test.void", VoidTarget.class, "run");
        InjectionRegistry.getInstance().register(
                "test.void", InjectionTiming.REPLACE,
                (proxy, method, args) -> {
                    VoidTarget.INJECTED.incrementAndGet();
                    return null;
                }, "test-plugin");

        BytecodeInjector.applyInjectionPoints(getClass().getClassLoader());

        VoidTarget.run();
        assertEquals(1, VoidTarget.CALLS.get(), "void 方法被替换后不应再执行原实现");
        assertEquals(1, VoidTarget.INJECTED.get(), "void 方法应执行插件处理器");
    }

    @Test
    void scannerFindsPlatformInjectPoints() {
        assertTrue(BytecodeInjector.initialize());

        BytecodeInjector.applyInjectionPoints(getClass().getClassLoader());

        // 平台适配依赖这些注入点存在；缺少任何一个都说明注解或扫描链路断了
        assertTrue(InjectPointMethods.getAll().keySet().contains("platform.os.supported"),
                "应扫描到 platform.os.supported");
        assertTrue(InjectPointMethods.getAll().keySet().contains("platform.system.manager"),
                "应扫描到 platform.system.manager");
        assertTrue(InjectPointMethods.getAll().keySet().contains("platform.hardware.all_info"),
                "应扫描到 platform.hardware.all_info");
        assertTrue(InjectPointMethods.getAll().keySet().contains("platform.window.overlay_setup"),
                "应扫描到 platform.window.overlay_setup");
        assertFalse(InjectPointMethods.getAll().keySet().isEmpty());
    }

    /**
     * 端到端验证真实的平台注入点：插件必须能把「当前平台是否受支持」改掉。
     * 这正是平台适配插件让核心在 Linux / macOS 上启动的关键一步。
     */
    @Test
    void realPlatformInjectPointCanBeOverridden() {
        assertTrue(BytecodeInjector.initialize());

        // 第一次应用只是让扫描器把 platform.* 注入点登记进映射表（此处还没有回调）
        BytecodeInjector.applyInjectionPoints(getClass().getClassLoader());

        boolean nativeSupport = SystemDetector.isWindows();
        assertEquals(nativeSupport, SystemDetector.isPlatformSupported(),
                "未注册回调时，平台声明应保持核心默认行为");

        InjectionRegistry registry = InjectionRegistry.getInstance();
        registry.register("platform.os.supported", InjectionTiming.REPLACE,
                (proxy, method, args) -> !nativeSupport, "test-plugin");

        BytecodeInjector.applyInjectionPoints(getClass().getClassLoader());
        assertEquals(!nativeSupport, SystemDetector.isPlatformSupported(),
                "插件应能替换 platform.os.supported 的返回值");

        // 后注册的处理器覆盖先注册的：平台适配插件之间可以互相覆盖
        registry.register("platform.os.supported", InjectionTiming.REPLACE,
                (proxy, method, args) -> nativeSupport, "test-plugin-2");
        assertEquals(nativeSupport, SystemDetector.isPlatformSupported(),
                "后注册的 REPLACE 处理器应覆盖先注册的");
    }
}
