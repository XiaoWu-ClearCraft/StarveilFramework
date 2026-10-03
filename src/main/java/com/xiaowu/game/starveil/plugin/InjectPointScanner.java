package com.xiaowu.game.starveil.plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 注入点扫描器。
 *
 * <p>在启动时扫描游戏自身的类，收集所有标注了 {@link InjectPoint} 的方法，
 * 建立「注入点 ID → 方法」映射，使插件可以通过注入点 ID 覆盖这些方法。
 *
 * <p>只扫描 {@link #PACKAGE_ROOT} 前缀下的类，且使用 {@code initialize=false}
 * 加载，避免触发静态初始化块产生副作用。
 */
public final class InjectPointScanner {

    /** 只扫描游戏自身的包，避免遍历整个 classpath。 */
    private static final String PACKAGE_ROOT = "com/xiaowu/game/starveil/";
    private static final String PACKAGE_ROOT_PREFIX = "com.xiaowu.game.starveil.";

    private InjectPointScanner() {}

    /**
     * 扫描并返回所有注入点映射。扫描失败时返回已收集到的部分结果，不抛异常。
     */
    public static Map<String, Method> scan(ClassLoader classLoader) {
        Map<String, Method> result = new java.util.LinkedHashMap<>();
        List<String> classNames = listGameClassNames();
        Logger("INFO", "[InjectPointScanner] 开始扫描注入点，候选类 " + classNames.size() + " 个");

        for (String className : classNames) {
            Class<?> type;
            try {
                type = Class.forName(className, false, classLoader);
            } catch (Throwable ignored) {
                // 加载失败的类直接跳过（可能依赖缺失的可选库）
                continue;
            }
            collect(type, result);
        }

        Logger("INFO", "[InjectPointScanner] 扫描完成，发现 " + result.size() + " 个注入点");
        return result;
    }

    private static void collect(Class<?> type, Map<String, Method> result) {
        if (type.isAnnotation() || type.isInterface() || type.isEnum() || type.isSynthetic()) {
            return;
        }
        for (Method method : type.getDeclaredMethods()) {
            InjectPoint annotation = method.getAnnotation(InjectPoint.class);
            if (annotation == null) {
                continue;
            }
            String pointId = annotation.value();
            if (pointId == null || pointId.isBlank()) {
                Logger("WARNING", "[InjectPointScanner] 忽略空注入点 ID: " + method);
                continue;
            }
            Method previous = result.putIfAbsent(pointId, method);
            if (previous != null) {
                Logger("WARNING", "[InjectPointScanner] 注入点 ID 重复: " + pointId
                        + " -> " + previous + " / " + method + "，保留前者");
            }
        }
    }

    /**
     * 枚举游戏自身代码位置（开发模式的 classes 目录或发布模式的 JAR）下的所有类名。
     */
    private static List<String> listGameClassNames() {
        List<String> names = new ArrayList<>();
        URL location = resolveCodeSource();
        if (location == null) {
            Logger("WARNING", "[InjectPointScanner] 无法定位游戏代码位置，跳过自动扫描");
            return names;
        }

        try {
            if ("file".equals(location.getProtocol())) {
                Path root = new File(location.toURI()).toPath();
                if (Files.isDirectory(root)) {
                    collectFromDirectory(root, names);
                } else {
                    collectFromJar(root, names);
                }
            } else if ("jar".equals(location.getProtocol())) {
                String spec = location.getPath();
                int bang = spec.indexOf("!/");
                String jarPath = bang >= 0 ? spec.substring(0, bang) : spec;
                collectFromJar(new File(new URI(jarPath)).toPath(), names);
            } else {
                Logger("WARNING", "[InjectPointScanner] 不支持的代码位置协议: " + location.getProtocol());
            }
        } catch (Exception e) {
            Logger("WARNING", "[InjectPointScanner] 枚举类失败: " + e.getMessage());
        }
        return names;
    }

    private static URL resolveCodeSource() {
        try {
            CodeSource source = InjectPointScanner.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                return source.getLocation();
            }
        } catch (Throwable ignored) {
        }
        return InjectPointScanner.class.getResource("/" + PACKAGE_ROOT);
    }

    private static void collectFromDirectory(Path root, List<String> names) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".class"))
                  .forEach(p -> addClassName(root.relativize(p).toString(), names));
        } catch (IOException e) {
            Logger("WARNING", "[InjectPointScanner] 遍历目录失败: " + e.getMessage());
        }
    }

    private static void collectFromJar(Path jarPath, List<String> names) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                addClassName(entries.nextElement().getName(), names);
            }
        } catch (IOException e) {
            Logger("WARNING", "[InjectPointScanner] 读取 JAR 失败: " + e.getMessage());
        }
    }

    private static void addClassName(String resourcePath, List<String> names) {
        if (!resourcePath.endsWith(".class")) {
            return;
        }
        String normalized = resourcePath.replace(File.separatorChar, '/').replace('\\', '/');
        if (!normalized.startsWith(PACKAGE_ROOT) || normalized.contains("module-info")) {
            return;
        }
        String className = normalized.substring(0, normalized.length() - ".class".length()).replace('/', '.');
        if (className.startsWith(PACKAGE_ROOT_PREFIX)) {
            names.add(className);
        }
    }
}
