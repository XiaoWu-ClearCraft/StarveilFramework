package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.infrastructure.AssetMeta;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;
import com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver;
import com.xiaowu.game.starveil.render.AnimationSheet;

import java.io.InputStream;
import java.net.URL;

/**
 * 标准资源加载 API。
 *
 * <p>统一使用命名空间写法访问资源：{@code starveil:<类型>/<路径>}，例如
 * {@code starveil:textures/icons/app-icon.png}、{@code starveil:fonts/xiaolai-sc-regular.ttf}、
 * {@code starveil:sounds/music/dream.mp3}、{@code starveil:data/config/quests.json}。
 *
 * <p>类型与物理目录对应：贴图 textures、音频 sounds、字体 fonts、数据 data。
 * 省略文件路径时使用类型默认文件（textures.png / fonts.ttf / sounds.mp3）；
 * 带子类目录查找失败时会自动忽略子类再尝试一次。
 */
public class StarveilResources {

    private static final StarveilResources INSTANCE = new StarveilResources();

    private StarveilResources() {
    }

    public static StarveilResources getInstance() {
        return INSTANCE;
    }

    /**
     * 将命名空间引用解析为 classpath 绝对路径（如 /assets/starveil/...）。
     */
    public String resolve(String path) {
        return StarveilResourceResolver.resolve(path);
    }

    /**
     * 判断资源是否存在。
     */
    public boolean exists(String path) {
        return StarveilResourceResolver.exists(path);
    }

    /**
     * 打开资源输入流（找不到返回 null）。
     */
    public InputStream openStream(String path) {
        return StarveilResourceResolver.openStream(path);
    }

    /**
     * 获取资源 URL（找不到返回 null）。
     */
    public URL getURL(String path) {
        return StarveilResourceResolver.getResource(path);
    }

    /**
     * 获取类型默认文件名（如 textures → textures.png；未知类型返回 null）。
     */
    public String getDefaultFile(String type) {
        return StarveilResourceResolver.getDefaultFile(type);
    }

    /**
     * 加载纹理贴图；失败返回 null。
     */
    public javafx.scene.image.Image loadImage(String path) {
        try (InputStream is = openStream(path)) {
            if (is != null) {
                return new javafx.scene.image.Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 加载字体（大小以像素指定）；失败返回 null。
     */
    public javafx.scene.text.Font loadFont(String path, double size) {
        try (InputStream is = openStream(path)) {
            if (is != null) {
                return javafx.scene.text.Font.loadFont(is, size);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 加载动画表。贴图 + 同名 meta（JSON）声明动画帧；
     * 未配置动画时整张图作为单帧。
     */
    public AnimationSheet loadAnimationSheet(String path) {
        return AnimationSheet.load(path);
    }

    /**
     * 加载资源同名 meta 数据（JSON），缺失时返回 null。
     */
    public AssetMeta loadMeta(String path) {
        return AssetMeta.load(path);
    }

    /**
     * 兼容旧式路径：直接用 {@link ResourceResolver} 打开。
     */
    public InputStream openLegacy(String path) {
        return ResourceResolver.getResourceAsStream(path);
    }
}