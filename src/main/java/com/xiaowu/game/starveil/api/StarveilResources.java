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
 * <p><b>类型由方法给，内容只写类型目录下面的路径</b>：
 *
 * <pre>{@code
 * Starveil.resources().loadImage("character/normal/relaxed.png");   // 贴图
 * Starveil.resources().loadFont("xiaolai-sc-regular.ttf", 20);      // 字体
 * }</pre>
 *
 * <p>没写文件后缀也行：会按类型试常见后缀（贴图 .png/.jpg/…、字体 .ttf/…），找到才用。
 * 需要完全掌控时仍然可以写显式引用：{@code starveil:<类型>/<路径>}（旧式
 * {@code /assets/...} 路径同样兼容），显式写法永远优先。
 */
public class StarveilResources {

    private static final StarveilResources INSTANCE = new StarveilResources();

    private StarveilResources() {
    }

    public static StarveilResources getInstance() {
        return INSTANCE;
    }

    /**
     * 把资源引用解析为 classpath 绝对路径（如 {@code /assets/starveil/...}）。
     */
    public String resolve(String path) {
        return StarveilResourceResolver.resolve(path);
    }

    /** 按类型解析：相对路径按 {@code assets/starveil/<type>/} 解释。 */
    public String resolveAs(String type, String path) {
        return StarveilResourceResolver.resolveAs(type, path);
    }

    /** 贴图路径解析（{@code character/normal/relaxed.png}）。 */
    public String resolveTexture(String path) {
        return StarveilResourceResolver.resolveTexture(path);
    }

    /** 音频路径解析（{@code music/dream.mp3}）。 */
    public String resolveSound(String path) {
        return StarveilResourceResolver.resolveSound(path);
    }

    /** 字体路径解析（{@code xiaolai-sc-regular.ttf}）。 */
    public String resolveFont(String path) {
        return StarveilResourceResolver.resolveFont(path);
    }

    /** 数据文件路径解析（{@code worlds/gravity-test.json}）。 */
    public String resolveData(String path) {
        return StarveilResourceResolver.resolveData(path);
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
     * 加载纹理贴图（路径按贴图解释）；失败返回 null。
     */
    public javafx.scene.image.Image loadImage(String path) {
        try (InputStream is = StarveilResourceResolver.openStream(
                StarveilResourceResolver.resolveTexture(path))) {
            if (is != null) {
                return new javafx.scene.image.Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 加载字体（大小以像素指定，路径按字体解释）；失败返回 null。
     */
    public javafx.scene.text.Font loadFont(String path, double size) {
        try (InputStream is = StarveilResourceResolver.openStream(
                StarveilResourceResolver.resolveFont(path))) {
            if (is != null) {
                return javafx.scene.text.Font.loadFont(is, size);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 加载动画表（路径按贴图解释）。贴图 + 同名 meta（JSON）声明动画帧；
     * 未配置动画时整张图作为单帧。
     */
    public AnimationSheet loadAnimationSheet(String path) {
        return AnimationSheet.load(StarveilResourceResolver.resolveTexture(path));
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