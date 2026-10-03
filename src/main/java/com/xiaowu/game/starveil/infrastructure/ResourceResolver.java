package com.xiaowu.game.starveil.infrastructure;

import java.io.InputStream;

/**
 * 资源解析器。
 *
 * <p>同时支持两类引用：
 * <ul>
 *   <li>旧式 classpath 路径：{@code /assets/...}</li>
 *   <li>Starveil 命名空间写法：{@code starveil:textures/...}（见 {@link StarveilResourceResolver}）</li>
 * </ul>
 *
 * <p>本版本只支持从 classpath / 打包 JAR 读取资源（Windows 单平台分发方式）。
 */
public class ResourceResolver {

    private ResourceResolver() {}

    public static java.net.URL getResource(String path) {
        String resolved = resolve(path);
        if (resolved == null) {
            return null;
        }
        return ResourceResolver.class.getResource(resolved);
    }

    public static InputStream getResourceAsStream(String path) {
        String resolved = resolve(path);
        if (resolved == null) {
            return null;
        }
        return ResourceResolver.class.getResourceAsStream(resolved);
    }

    private static String resolve(String path) {
        if (path == null) {
            return null;
        }
        // 内容侧可以重定向框架引用的资源（字体、菜单背景、图标、音乐…）。
        // 这里是所有资源读取的唯一入口，因此在这拦一次就够了 ——
        // 框架里那几十处硬编码路径一行都不用改，换资源就能全局生效。
        String effective = ContentConfig.redirectOf(path);
        return StarveilResourceResolver.isNamespaceReference(effective)
                ? StarveilResourceResolver.resolve(effective)
                : normalize(effective);
    }

    private static String normalize(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
