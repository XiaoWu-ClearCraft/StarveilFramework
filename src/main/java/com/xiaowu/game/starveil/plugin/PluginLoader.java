package com.xiaowu.game.starveil.plugin;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;
import com.xiaowu.game.starveil.startup.GameContentInit;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 插件加载器
 * 负责发现、加载和卸载插件
 *
 * 插件发现规则: JAR 文件根目录包含 plugin.yml 声明文件即视为有效插件
 * plugin.yml 必须包含: id, main（主类全限定名）
 */
public class PluginLoader {

    private static final List<LoadedPlugin> loadedPlugins = new ArrayList<>();
    private static final List<URLClassLoader> pluginClassLoaders = new ArrayList<>();

    static class LoadedPlugin {
        final PluginManifest manifest;
        final StarveilPlugin instance;
        final URLClassLoader classLoader;

        LoadedPlugin(PluginManifest manifest, StarveilPlugin instance, URLClassLoader classLoader) {
            this.manifest = manifest;
            this.instance = instance;
            this.classLoader = classLoader;
        }
    }

    /**
     * 初始化并加载所有插件（LAUNCHER 时机，向后兼容入口）。
     *
     * <p>应在 DataManager.initialize() 与内容初始化之后调用。
     *
     * @return true 如果加载了插件，false 如果以纯净模式运行
     */
    public static boolean initializeAndLoadPlugins() {
        return initializeAndLoadPlugins(PluginLoadTiming.LAUNCHER);
    }

    /**
     * 按加载时机初始化并加载插件。
     *
     * <p>分阶段是必需的：INIT 时机要在 {@code content.init.init} 首次加载之前
     * 把字节码转换器装好，否则注入不会生效；LAUNCHER 时机则要在 {@code Launcher}
     * 首次加载之前装好。两个阶段各调一次 {@link BytecodeInjector#applyInjectionPoints}，
     * 内部会跳过已安装过的类。
     *
     * @param timing 只加载声明了该时机的插件
     * @return true 如果本阶段加载了插件
     */
    public static boolean initializeAndLoadPlugins(PluginLoadTiming timing) {
        Path pluginsDir = Paths.get(GameConstants.PLUGINS_DIR);

        try {
            if (!Files.exists(pluginsDir)) {
                Files.createDirectories(pluginsDir);
                Logger("INFO", "插件目录已创建: " + pluginsDir.toAbsolutePath());
            }
        } catch (IOException e) {
            Logger("ERROR", "创建插件目录失败: " + e.getMessage());
            return false;
        }

        List<PluginManifest> manifests = discoverPluginManifests(pluginsDir);
        if (manifests.isEmpty()) {
            Logger("INFO", "未发现有效插件，以纯净模式启动");
            return false;
        }
        Logger("INFO", "发现 " + manifests.size() + " 个有效插件");

        List<PluginManifest> phase = manifests.stream()
                .filter(m -> m.loadTiming == timing)
                .collect(Collectors.toList());
        if (phase.isEmpty()) {
            Logger("INFO", "没有声明 " + timing + " 加载时机的插件");
            return false;
        }

        // INIT 插件的唯一目的就是注入 content.init.init。
        // 该入口不存在时插件无从生效，必须立刻报致命错误并点名，而不是静默跳过 ——
        // 否则用户会以为插件在起作用，实际什么都没发生。
        if (timing == PluginLoadTiming.INIT && !GameContentInit.exists()) {
            fatalMissingInitClass(phase);
        }

        if (!ensurePluginConsent(pluginsDir)) {
            return false;
        }

        if (!injectorReady) {
            if (!BytecodeInjector.initialize()) {
                Logger("WARNING", "ByteBuddy 初始化失败，插件将无法使用字节码注入功能");
            }
            injectorReady = true;
        }

        for (PluginManifest manifest : phase) {
            if (!checkFrameworkVersion(manifest)) {
                continue;
            }
            loadPlugin(manifest, pluginsDir);
        }

        // 风险提示只针对「确实注入了 init」的插件，而不是「声明了 INIT 时机」的插件：
        // 声明了时机但注入的是别的东西，并不比普通插件更危险。
        if (timing == PluginLoadTiming.INIT) {
            confirmInitInjection();
        }

        if (BytecodeInjector.isInitialized()) {
            BytecodeInjector.applyInjectionPoints(PluginLoader.class.getClassLoader());
        }

        Logger("INFO", timing + " 时机插件加载完成，本次加载 " + phase.size()
                + " 个，累计 " + loadedPlugins.size() + " 个");
        return !loadedPlugins.isEmpty();
    }

    /** 是否已经确认过插件加载同意（两个阶段共用一次确认）。 */
    private static boolean consentChecked = false;
    /** 用户是否明确拒绝过。拒绝后所有阶段都必须继续拒绝。 */
    private static boolean consentDenied = false;
    private static boolean injectorReady = false;

    private static boolean ensurePluginConsent(Path pluginsDir) {
        // 拒绝是终态：如果只看 consentChecked，第二阶段会跳过确认直接加载，
        // 目前只是靠「JAR 已被移到禁用目录」侥幸不出问题。
        if (consentDenied) {
            Logger("INFO", "用户此前已拒绝加载插件，本阶段同样跳过");
            return false;
        }
        if (consentChecked) {
            return true;
        }
        consentChecked = true;

        if (com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys
                .PLUGIN_CONSENT_GIVEN.get()) {
            Logger("INFO", "插件加载同意标记已存在，直接加载插件");
            return true;
        }
        if (showPluginConsentDialog()) {
            com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys
                    .PLUGIN_CONSENT_GIVEN.set(true);
            Logger("INFO", "用户同意加载插件，已保存同意标记");
            return true;
        }

        Logger("INFO", "用户拒绝加载插件，将插件移至禁用目录");
        consentDenied = true;
        disableAllJars(pluginsDir);
        return false;
    }

    /**
     * INIT 插件声明了却找不到 {@code content.init.init}：致命错误，点名到插件。
     */
    private static void fatalMissingInitClass(List<PluginManifest> initPlugins) {
        String names = initPlugins.stream()
                .map(m -> "  · " + m.name + " (ID: " + m.id + ")")
                .collect(Collectors.joining("\n"));
        String msg = "以下插件声明在 INIT 时机加载（用于注入 " + GameContentInit.CLASS_NAME
                + "），但该初始化类不存在：\n\n" + names
                + "\n\n这些插件是为特定的游戏内容编写的，当前游戏没有提供这个初始化入口，"
                + "插件无法生效。请移除这些插件，或使用提供了该入口的游戏内容。";
        Logger("ERROR", msg);
        try {
            SystemManagerFactory.getInstance().showError("插件加载失败", msg);
        } catch (Exception e) {
            Logger("ERROR", "无法显示错误对话框: " + e.getMessage());
        }
        System.exit(1);
    }

    /**
     * 是否真的有注入点指向 init 类。
     *
     * <p>这是「注入了 init」的精确判据 —— 看的是注入点最终绑定到了哪个类的哪个方法，
     * 而不是插件在 plugin.yml 里声明了什么。
     */
    public static boolean injectsInitClass() {
        for (java.lang.reflect.Method m : InjectPointMethods.getAll().values()) {
            if (m.getDeclaringClass().getName().equals(GameContentInit.CLASS_NAME)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 确实注入了 init 时才提示风险。
     *
     * <p>init 由非框架的游戏开发者定义，不同游戏 / 不同版本可能完全不同，
     * 注入错误会让游戏直接崩溃且难以排查。
     */
    private static void confirmInitInjection() {
        if (!injectsInitClass()) {
            Logger("INFO", "本阶段没有任何注入点指向 " + GameContentInit.CLASS_NAME
                    + "，无需额外的崩溃风险提示");
            return;
        }
        Logger("WARNING", "检测到有插件注入 " + GameContentInit.CLASS_NAME + "，请求用户确认");
        boolean accepted;
        try {
            accepted = SystemManagerFactory.getInstance().showConfirm(
                    "高风险插件注入",
                    "检测到插件正在注入游戏初始化入口：\n" + GameContentInit.CLASS_NAME
                            + "\n\n该入口由非框架的游戏开发者定义，不同游戏、不同版本可能完全不同。"
                            + "注入错误可能导致游戏直接崩溃，或产生难以排查的问题。\n\n"
                            + "即便如此也要继续吗？");
        } catch (Exception e) {
            Logger("WARNING", "无法显示风险提示对话框: " + e.getMessage());
            accepted = false;
        }
        if (!accepted) {
            Logger("ERROR", "用户拒绝 init 注入，终止启动");
            try {
                SystemManagerFactory.getInstance().showError("已终止",
                        "您拒绝了初始化入口注入，插件无法继续加载，游戏将退出。");
            } catch (Exception ignored) {
            }
            System.exit(1);
        }
    }

    /**
     * 框架版本兼容性检查。
     *
     * @return 是否可加载
     */
    private static boolean checkFrameworkVersion(PluginManifest manifest) {
        if (PluginCompatibility.isCompatible(
                manifest.frameworkVersion, GameConstants.FRAMEWORK_VERSION)) {
            return true;
        }
        Logger("ERROR", "跳过插件：" + PluginCompatibility.describeMismatch(
                manifest.name, manifest.frameworkVersion, GameConstants.FRAMEWORK_VERSION));
        return false;
    }

    /**
     * 扫描插件目录，解析每个 JAR 的 plugin.yml
     */
    private static List<PluginManifest> discoverPluginManifests(Path pluginsDir) {
        List<PluginManifest> result = new ArrayList<>();
        try {
            List<Path> jars = Files.list(pluginsDir)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toList());

            for (Path jarPath : jars) {
                PluginManifest manifest = PluginManifest.loadFromJar(jarPath);
                if (manifest != null && manifest.isValid()) {
                    // 检查 ID 是否重复
                    boolean duplicate = result.stream().anyMatch(m -> m.id.equals(manifest.id));
                    if (duplicate) {
                        Logger("WARNING", "跳过重复插件 ID: " + manifest.id + " (" + jarPath.getFileName() + ")");
                        continue;
                    }
                    result.add(manifest);
                    Logger("INFO", "发现插件: " + manifest.name + " v" + manifest.version
                            + " (ID: " + manifest.id + ", 时机: " + manifest.loadTiming
                            + (manifest.frameworkVersion != null
                                    ? ", 框架: " + manifest.frameworkVersion : "")
                            + ")");
                } else {
                    Logger("INFO", "跳过非插件 JAR: " + jarPath.getFileName());
                }
            }
        } catch (IOException e) {
            Logger("ERROR", "扫描插件目录失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 显示插件加载同意对话框
     */
    private static boolean showPluginConsentDialog() {
        try {
            return SystemManagerFactory.getInstance().showConfirm(
                "插件加载警告",
                "非官方插件可能导致系统损坏，即便如此也要加载吗？\n" +
                "插件操作不受游戏开发者管制，游戏开发者不承担任何责任。");
        } catch (Exception e) {
            Logger("WARNING", "无法显示插件加载对话框: " + e.getMessage());
            return false;
        }
    }

    /**
     * 将插件目录中的所有 JAR 移动到禁用目录
     */
    private static void disableAllJars(Path pluginsDir) {
        Path disabledDir = Paths.get(GameConstants.PLUGINS_DISABLED_DIR);
        try {
            if (!Files.exists(disabledDir)) {
                Files.createDirectories(disabledDir);
            }
            List<Path> jars = Files.list(pluginsDir)
                    .filter(p -> p.toString().endsWith(".jar"))
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());

            for (Path jar : jars) {
                Path target = disabledDir.resolve(jar.getFileName());
                Files.move(jar, target, StandardCopyOption.REPLACE_EXISTING);
                Logger("INFO", "插件已禁用: " + jar.getFileName() + " -> " + target);
            }
        } catch (IOException e) {
            Logger("ERROR", "禁用插件失败: " + e.getMessage());
        }
    }

    /**
     * 加载单个插件
     */
    private static void loadPlugin(PluginManifest manifest, Path pluginsDir) {
        Logger("INFO", "正在加载插件: " + manifest.name + " v" + manifest.version);

        try {
            // 定位 JAR 文件
            Path jarPath = pluginsDir.resolve(manifest.id + ".jar");
            if (!Files.exists(jarPath)) {
                // 尝试找到匹配的 JAR
                List<Path> candidates = Files.list(pluginsDir)
                        .filter(p -> p.toString().endsWith(".jar"))
                        .collect(Collectors.toList());
                if (candidates.size() == 1) {
                    jarPath = candidates.get(0);
                } else {
                    Logger("ERROR", "找不到插件 JAR: " + manifest.id);
                    return;
                }
            }

            // 将插件 JAR 添加到系统 ClassLoader 搜索路径
            // 这样插件的类和游戏类共享同一个 ClassLoader，消除 ClassLoader 隔离
            addJarToClassLoader(jarPath);

            // 支持同级 -lib 目录存放依赖 JAR
            String baseName = manifest.id;
            Path libDir = jarPath.resolveSibling(baseName + "-lib");
            if (Files.isDirectory(libDir)) {
                try {
                    Files.list(libDir)
                            .filter(p -> p.toString().endsWith(".jar"))
                            .forEach(p -> {
                                try {
                                    addJarToClassLoader(p);
                                    Logger("INFO", "添加插件依赖: " + p.getFileName());
                                } catch (Exception e) {
                                    Logger("WARNING", "无法添加依赖 URL: " + p);
                                }
                            });
                } catch (IOException e) {
                    Logger("WARNING", "扫描插件依赖目录失败: " + libDir);
                }
            }

            // 直接从系统 ClassLoader 加载插件主类
            ClassLoader appClassLoader = PluginLoader.class.getClassLoader();
            Class<?> mainClass = appClassLoader.loadClass(manifest.main);
            if (!StarveilPlugin.class.isAssignableFrom(mainClass)) {
                Logger("ERROR", "插件主类 " + manifest.main + " 未实现 StarveilPlugin 接口");
                return;
            }

            StarveilPlugin plugin = (StarveilPlugin) mainClass.getDeclaredConstructor().newInstance();
            PluginContext context = new DefaultPluginContext(manifest, plugin, pluginsDir);

            // 插件自带资源直接用插件 id 当命名空间：assets/<插件id>/… 写成 <插件id>:…
            // （和「数据键」的命名空间一个思路：谁的资源谁负责，不会和游戏内容撞路径）
            com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver
                    .registerNamespace(manifest.id);

            plugin.onLoad(context);
            loadedPlugins.add(new LoadedPlugin(manifest, plugin, null));
            Logger("INFO", "插件已加载: " + manifest.name + " v" + manifest.version + " (ID: " + manifest.id + ")");

        } catch (Exception e) {
            Logger("ERROR", "加载插件失败 [" + manifest.id + "]: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将 JAR 添加到应用 ClassLoader 的类路径中
     * 使用 Instrumentation.appendToSystemClassLoaderSearch 绕过 Java 9+ 模块限制
     */
    private static void addJarToClassLoader(Path jarPath) throws Exception {
        if (com.xiaowu.game.starveil.plugin.BytecodeInjector.isInitialized()) {
            // 有 Instrumentation 时，使用官方 API 添加到系统类加载搜索路径
            java.io.File jarFile = jarPath.toFile();
            java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile);
            com.xiaowu.game.starveil.plugin.BytecodeInjector.getInstrumentation()
                .appendToSystemClassLoaderSearch(jar);
            Logger("INFO", "已添加 JAR 到 ClassLoader (Instrumentation): " + jarPath.getFileName());
        } else {
            // 没有 Instrumentation 时，回退到 URLClassLoader 方案
            // 这种情况不应该发生，因为 ByteBuddy 应该先初始化
            Logger("WARNING", "BytecodeInjector 未初始化，无法添加 JAR: " + jarPath.getFileName());
            throw new IllegalStateException("BytecodeInjector 未初始化");
        }
    }

    /**
     * 卸载所有插件（在游戏关闭时调用）
     */
    public static void unloadAllPlugins() {
        if (loadedPlugins.isEmpty()) {
            return;
        }

        Logger("INFO", "正在卸载 " + loadedPlugins.size() + " 个插件...");
        for (LoadedPlugin lp : loadedPlugins) {
            try {
                lp.instance.onUnload();
                InjectionRegistry.getInstance().unregisterPlugin(lp.manifest.id);
                Logger("INFO", "插件已卸载: " + lp.manifest.name);
            } catch (Exception e) {
                Logger("ERROR", "插件 " + lp.manifest.name + " 的 onUnload 失败: " + e.getMessage());
            }
        }

        // 注意：不再关闭 URLClassLoader，因为使用的是共享系统 ClassLoader
        // 插件 JAR 已添加到系统 ClassLoader 的 URLClassPath，无法移除

        InjectionRegistry.getInstance().clear();
        loadedPlugins.clear();
        pluginClassLoaders.clear();
        Logger("INFO", "所有插件已卸载");
    }

    /**
     * 获取已加载的插件列表
     */
    public static List<PluginManifest> getLoadedPluginManifests() {
        List<PluginManifest> result = new ArrayList<>();
        for (LoadedPlugin lp : loadedPlugins) {
            result.add(lp.manifest);
        }
        return Collections.unmodifiableList(result);
    }
}
