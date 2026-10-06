package com.xiaowu.game.starveil.infrastructure;

import java.io.InputStream;

/**
 * 资源解析器。
 *
 * <p>支持三类引用：
 * <ul>
 *   <li>命名空间写法：{@code starveil:<类型>/<路径>}（见 {@link StarveilResourceResolver}）</li>
 *   <li>旧式 classpath 路径：{@code /assets/...}</li>
 *   <li><b>相对路径</b>：{@code character/normal/relaxed.png} —— 类型由框架推断
 *       （在 {@code assets/starveil/<类型>/} 下挨个找），内容不必记 textures / sounds 这些类型名</li>
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
        if (StarveilResourceResolver.isNamespaceReference(effective)) {
            return StarveilResourceResolver.resolve(effective);
        }
        if (StarveilResourceResolver.isExplicitReference(effective)) {
            return normalize(effective);
        }
        // 相对路径：类型由框架推断（贴图/音频/字体/数据/文案挨个找），找不到再按老规则处理
        String inferred = StarveilResourceResolver.resolveRelative(effective);
        return inferred != null ? inferred : normalize(effective);
    }

    private static String normalize(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
