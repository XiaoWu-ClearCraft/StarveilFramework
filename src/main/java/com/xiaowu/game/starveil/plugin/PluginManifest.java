package com.xiaowu.game.starveil.plugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.nio.file.Path;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 插件清单模型
 * 从 JAR 根目录的 plugin.yml 文件中解析插件信息
 */
public class PluginManifest {

    private static final String MANIFEST_FILE = "plugin.yml";

    public String id;
    public String name;
    /** 插件自身版本。 */
    public String version;
    /**
     * 适配的框架版本，例如 {@code 1.0.0}。
     *
     * <p>缺省 / 空 / {@code *} 表示不挑版本；只比主版本号，
     * 详见 {@link PluginCompatibility}。
     */
    public String frameworkVersion;
    /** 加载时机，缺省 {@link PluginLoadTiming#LAUNCHER}。 */
    public PluginLoadTiming loadTiming = PluginLoadTiming.LAUNCHER;
    public String main;
    public String icon;

    /**
     * 从 JAR 文件加载 plugin.yml
     */
    public static PluginManifest loadFromJar(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry entry = jarFile.getJarEntry(MANIFEST_FILE);
            if (entry == null) {
                return null;
            }
            try (InputStream is = jarFile.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return parse(reader);
            }
        } catch (IOException e) {
            Logger("WARNING", "无法读取插件清单: " + jarPath.getFileName() + " - " + e.getMessage());
            return null;
        }
    }

    /** 解析清单文本（包内可见，便于单测直接喂文本）。 */
    static PluginManifest parse(BufferedReader reader) throws IOException {
        PluginManifest manifest = new PluginManifest();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) {
                continue;
            }
            String key = line.substring(0, colonIdx).trim().toLowerCase();
            String value = line.substring(colonIdx + 1).trim();
            // 去掉可能的引号
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            switch (key) {
                case "id" -> manifest.id = value;
                case "name" -> manifest.name = value;
                case "version" -> manifest.version = value;
                case "framework-version", "framework_version", "framework",
                     "api-version", "api_version" -> manifest.frameworkVersion = value;
                case "load", "load-at", "load_at", "timing" -> {
                    if (PluginLoadTiming.isValidName(value)) {
                        manifest.loadTiming = PluginLoadTiming.fromName(value);
                    } else {
                        Logger("WARNING", "插件 " + manifest.id + " 的加载时机无法识别: '"
                                + value + "'，回退到 " + PluginLoadTiming.LAUNCHER);
                    }
                }
                case "main" -> manifest.main = value;
                case "icon" -> manifest.icon = value;
                default -> { }
            }
        }
        return manifest;
    }

    public boolean isValid() {
        return id != null && !id.isEmpty()
            && main != null && !main.isEmpty();
    }
}
