package com.xiaowu.game.starveil.plugin;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import net.bytebuddy.ByteBuddy;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationHandler;
import java.net.URL;
import java.nio.file.Path;

import javafx.scene.image.Image;

/**
 * PluginContext 默认实现
 * 插件信息优先从代码获取（getId/getName/getVersion），
 * 代码返回 null 时 fallback 到 plugin.yml。
 */
public class DefaultPluginContext implements PluginContext {

    private final PluginManifest manifest;
    private final StarveilPlugin plugin;
    private final Path pluginsDirectory;

    public DefaultPluginContext(PluginManifest manifest, StarveilPlugin plugin, Path pluginsDirectory) {
        this.manifest = manifest;
        this.plugin = plugin;
        this.pluginsDirectory = pluginsDirectory;
    }

    private String resolveId() {
        String codeId = plugin.getId();
        return codeId != null && !codeId.isEmpty() ? codeId : manifest.id;
    }

    private String resolveName() {
        String codeName = plugin.getName();
        return codeName != null && !codeName.isEmpty() ? codeName : manifest.name;
    }

    private String resolveVersion() {
        String codeVersion = plugin.getVersion();
        return codeVersion != null && !codeVersion.isEmpty() ? codeVersion : manifest.version;
    }

    private String resolveIcon() {
        String codeIcon = plugin.getIcon();
        return codeIcon != null && !codeIcon.isEmpty() ? codeIcon : manifest.icon;
    }

    @Override
    public void logInfo(String message) {
        Logger("INFO", "[" + resolveId() + "] " + message);
    }

    @Override
    public void logWarning(String message) {
        Logger("WARNING", "[" + resolveId() + "] " + message);
    }

    @Override
    public void logError(String message) {
        Logger("ERROR", "[" + resolveId() + "] " + message);
    }

    @Override
    public String getPluginId() {
        return resolveId();
    }

    @Override
    public String getPluginName() {
        return resolveName();
    }

    @Override
    public String getPluginVersion() {
        return resolveVersion();
    }

    @Override
    public Image getPluginIcon() {
        String iconPath = resolveIcon();
        if (iconPath == null || iconPath.isEmpty()) {
            return null;
        }
        try {
            // 网络 URL
            if (iconPath.startsWith("http://") || iconPath.startsWith("https://")) {
                return new Image(iconPath, 48, 48, true, true);
            }
            // JAR 内资源
            InputStream is = ResourceResolver.getResourceAsStream(iconPath);
            if (is != null) {
                try {
                    return new Image(is, 48, 48, true, true);
                } finally {
                    is.close();
                }
            }
            // 尝试作为 URL 资源加载
            URL url = ResourceResolver.getResource(iconPath);
            if (url != null) {
                return new Image(url.toString(), 48, 48, true, true);
            }
        } catch (Exception e) {
            Logger("WARNING", "[" + resolveId() + "] 无法加载图标: " + iconPath + " - " + e.getMessage());
        }
        return null;
    }

    @Override
    public Path getPluginsDirectory() {
        return pluginsDirectory;
    }

    @Override
    public ByteBuddy getByteBuddy() {
        return BytecodeInjector.getByteBuddy();
    }

    @Override
    public Instrumentation getInstrumentation() {
        return BytecodeInjector.getInstrumentation();
    }

    @Override
    public void registerInjection(String pointId, InjectionTiming timing, InvocationHandler handler) {
        InjectionRegistry.getInstance().register(pointId, timing, handler, resolveId());
    }

    private static void Logger(String level, String message) {
        com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger(level, message);
    }
}
