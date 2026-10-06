package com.xiaowu.game.starveil.render;

import com.xiaowu.game.starveil.infrastructure.ResourceResolver;
import com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * 贴图节点工厂 — 按资源路径创建贴图显示节点。
 *
 * <p>若目标贴图是动画贴图（meta 声明了 animation 且帧数/帧率有效），
 * 返回播放动画的 {@link AnimationSheetView}；否则返回普通 {@link ImageView}。
 * 调用方无需关心具体类型。
 */
public final class TextureNodeFactory {

    private TextureNodeFactory() {
    }

    /**
     * 加载贴图节点。动画贴图自动播放，静态贴图返回普通 ImageView。
     *
     * <p>这是<b>贴图</b>工厂：类型由这里补上，内容写命名空间或相对路径都行
     * （{@code starveil:character/relaxed.png}、{@code character/relaxed.png}、
     * {@code starveil:textures/character/relaxed.png} 三种写法等价）。
     *
     * @param path       资源路径
     * @param fitWidth   期望宽度（&lt;=0 表示按原尺寸）
     * @param fitHeight  期望高度（&lt;=0 表示按原尺寸）
     * @return 贴图节点；加载失败返回 null
     */
    public static Node load(String path, double fitWidth, double fitHeight) {
        if (path == null) {
            return null;
        }
        String resolved = StarveilResourceResolver.resolveTexture(path);
        AnimationSheet sheet = AnimationSheet.load(resolved);
        if (sheet != null && sheet.getFrameCount() > 1 && sheet.getFrameRate() > 0) {
            AnimationSheetView view = new AnimationSheetView();
            view.playSheet(sheet);
            applyFit(view, fitWidth, fitHeight);
            return view;
        }
        Image img = sheet != null ? sheet.getSheet() : loadImage(path);
        if (img == null) {
            return null;
        }
        ImageView view = new ImageView(img);
        applyFit(view, fitWidth, fitHeight);
        return view;
    }

    /**
     * 加载动画表视图（静态图同样退化为单帧视图）。用于需要按状态切换动画的实体。
     *
     * <p>路径按<b>贴图</b>解释，见 {@link #load(String, double, double)}。
     */
    public static AnimationSheetView loadSheetView(String path, double fitWidth, double fitHeight) {
        if (path == null) {
            return null;
        }
        AnimationSheet sheet = AnimationSheet.load(StarveilResourceResolver.resolveTexture(path));
        if (sheet == null) {
            return null;
        }
        AnimationSheetView view = new AnimationSheetView();
        view.playSheet(sheet);
        applyFit(view, fitWidth, fitHeight);
        return view;
    }

    /**
     * 加载贴图为 Image（非动画）。路径按<b>贴图</b>解释。
     */
    public static Image loadImage(String path) {
        if (path == null) {
            return null;
        }
        try (java.io.InputStream is = StarveilResourceResolver.openStream(
                StarveilResourceResolver.resolveTexture(path))) {
            if (is != null) {
                Image img = new Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
                return img.isError() ? null : img;
            }
        } catch (Exception ignored) {
        }
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream(path)) {
            if (is != null) {
                Image img = new Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
                return img.isError() ? null : img;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void applyFit(ImageView view, double width, double height) {
        if (width > 0) {
            view.setFitWidth(width);
        }
        if (height > 0) {
            view.setFitHeight(height);
        }
        view.setPreserveRatio(true);
    }
}