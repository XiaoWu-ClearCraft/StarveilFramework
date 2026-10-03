package com.xiaowu.game.starveil.render;

import com.xiaowu.game.starveil.infrastructure.AssetMeta;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;
import com.xiaowu.game.starveil.infrastructure.StarveilResourceResolver;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 纹理动画表 — 从一张包含多帧的贴图中按 meta 配置切分出动画帧。
 *
 * <p>帧按行序排列：自左上角 (0,0) 开始，先从左到右，再从上到下
 * （即整体获取顺序为从上到下）。若 meta 未指定 frames，则依据
 * 贴图尺寸与每帧尺寸自动计算。
 *
 * <p>meta 配置示例（{@code xxx.png.meta}，JSON）：
 * <pre>
 * {
 *   "animation": { "frameWidth": 32, "frameHeight": 32, "frames": 8, "frameRate": 12 }
 * }
 * </pre>
 */
public class AnimationSheet {

    private final Image sheet;
    private final AssetMeta meta;
    private final int frameWidth;
    private final int frameHeight;
    private final int frames;
    private final int frameRate;

    /**
     * 从命名空间资源路径加载动画表。meta 缺失或未声明 animation 时退化为单帧（整张图）。
     */
    public static AnimationSheet load(String resourcePath) {
        Image image = loadImage(resourcePath);
        if (image == null) {
            return null;
        }
        return new AnimationSheet(image, resourcePath);
    }

    public AnimationSheet(Image sheet, String resourcePath) {
        this.sheet = sheet;
        this.meta = AssetMeta.loadOrEmpty(resourcePath);
        AssetMeta.AnimationMeta anim = meta.getAnimation();
        int fw = anim != null ? anim.frameWidth : 0;
        int fh = anim != null ? anim.frameHeight : 0;
        this.frameWidth = fw > 0 ? fw : (int) sheet.getWidth();
        this.frameHeight = fh > 0 ? fh : (int) sheet.getHeight();
        int cols = frameWidth > 0 ? Math.max(1, (int) sheet.getWidth() / frameWidth) : 1;
        int rows = frameHeight > 0 ? Math.max(1, (int) sheet.getHeight() / frameHeight) : 1;
        int maxFrames = cols * rows;
        int declared = anim != null ? anim.frames : 0;
        this.frames = declared > 0 ? Math.min(declared, maxFrames) : maxFrames;
        this.frameRate = anim != null ? anim.frameRate : 0;
    }

    private static Image loadImage(String resourcePath) {
        try (InputStream is = StarveilResourceResolver.openStream(resourcePath)) {
            if (is != null) {
                return new Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
            }
        } catch (Exception ignored) {
        }
        // 兜底：直接走旧资源解析
        try (InputStream is = ResourceResolver.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new Image(new java.io.ByteArrayInputStream(is.readAllBytes()));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Image getSheet() {
        return sheet;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    public int getFrameCount() {
        return frames;
    }

    public int getFrameRate() {
        return frameRate;
    }

    /**
     * 获取第 index 帧对应的视口（相对整张贴图的裁剪区域）。
     */
    public Rectangle2D getViewport(int index) {
        int i = clampIndex(index);
        int cols = Math.max(1, (int) sheet.getWidth() / frameWidth);
        int col = i % cols;
        int row = i / cols;
        return new Rectangle2D(col * frameWidth, row * frameHeight, frameWidth, frameHeight);
    }

    /**
     * 提取第 index 帧为独立 Image。
     */
    public Image getFrame(int index) {
        int i = clampIndex(index);
        Rectangle2D vp = getViewport(i);
        PixelReader reader = sheet.getPixelReader();
        if (reader == null) {
            return sheet;
        }
        return new WritableImage(reader, (int) vp.getMinX(), (int) vp.getMinY(),
                (int) vp.getWidth(), (int) vp.getHeight());
    }

    /**
     * 提取全部帧。
     */
    public List<Image> getAllFrames() {
        List<Image> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            list.add(getFrame(i));
        }
        return list;
    }

    private int clampIndex(int index) {
        if (index < 0) {
            return 0;
        }
        return Math.min(index, frames - 1);
    }
}
