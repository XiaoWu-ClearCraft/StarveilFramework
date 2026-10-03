package com.xiaowu.game.starveil.render.effects;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 画面故障（花屏）效果。
 *
 * <p>两种渲染方式：
 * <ul>
 *   <li><b>像素级（推荐）</b>：直接在捕获到的下层画面上做行撕裂、块错位、
 *       RGB 色散、扫描线滚动、区块花块——这是真实数字信号故障的样子。
 *       具体算法在 {@link GlitchPixelOps} 里，本类只负责缓冲与贴图；</li>
 *   <li><b>矢量级（兜底）</b>：没有可用捕获画面时，退化成在遮罩层上堆随机矩形。</li>
 * </ul>
 *
 * <p>像素级操作在 {@link CapturedFrameLayer} 提供的 ARGB 缓冲上进行，
 * 缓冲按尺寸复用，不会每帧新建大数组。
 */
public class GlitchEffect {

    /** 同一时刻最多生成多少次像素级花屏，避免高强度连续调用把 FX 线程压死。 */
    private static final long MIN_PIXEL_GLITCH_INTERVAL_MS = 33;

    private final List<Node> glitchNodes = new ArrayList<>();

    private final Random random = new Random();
    private long lastPixelGlitchMillis;
    /** 复用的输出缓冲，避免每帧分配 2M 长度的 int[]。 */
    private int[] scratch;

    // ==================== 像素级花屏 ====================

    /**
     * 基于捕获画面渲染花屏。必须在 JavaFX 线程调用。
     *
     * @param layer    遮罩层（用于取尺寸；兜底渲染也用它）
     * @param fraction 强度 0.0-1.0
     * @param frame    捕获帧层；内部没有画面时返回 false，交由矢量兜底
     * @param x        区域左上角 X（遮罩层坐标）
     * @param y        区域左上角 Y
     * @param w        区域宽度
     * @param h        区域高度
     * @return 是否成功走像素级渲染
     */
    public boolean renderPixelGlitch(Pane layer, double fraction, CapturedFrameLayer frame,
                                     double x, double y, double w, double h) {
        if (frame == null || frame.getBase() == null || frame.getPixels() == null) {
            return false;
        }
        final double f = Math.max(0, Math.min(1, fraction));
        if (f <= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now - lastPixelGlitchMillis < MIN_PIXEL_GLITCH_INTERVAL_MS) {
            // 距离上次生成太近：保持当前这一帧花屏不动（看起来就是画面卡住的那一下）
            return true;
        }
        lastPixelGlitchMillis = now;

        int iw = frame.getWidth();
        int ih = frame.getHeight();
        if (iw <= 0 || ih <= 0) {
            return false;
        }

        // 遮罩层坐标 -> 画面像素坐标
        double layerW = layer.getWidth() > 0 ? layer.getWidth() : w;
        double layerH = layer.getHeight() > 0 ? layer.getHeight() : h;
        double scaleX = iw / Math.max(1.0, layerW);
        double scaleY = ih / Math.max(1.0, layerH);
        int rx = GlitchPixelOps.clamp((int) (x * scaleX), 0, iw - 1);
        int ry = GlitchPixelOps.clamp((int) (y * scaleY), 0, ih - 1);
        int rw = GlitchPixelOps.clamp((int) (w * scaleX), 1, iw - rx);
        int rh = GlitchPixelOps.clamp((int) (h * scaleY), 1, ih - ry);

        int[] src = frame.getPixels();
        if (scratch == null || scratch.length != src.length) {
            scratch = new int[src.length];
        }
        int[] dst = scratch;
        System.arraycopy(src, 0, dst, 0, src.length);

        GlitchPixelOps.tearBands(dst, src, iw, ih, rx, ry, rw, rh, f, random);
        GlitchPixelOps.channelSplit(dst, src, iw, ih, rx, ry, rw, rh, f, random);
        GlitchPixelOps.blockCorruption(dst, src, iw, ih, rx, ry, rw, rh, f, random);
        // 滚动带随时间缓慢下移，制造「信号不同步」的持续感
        double rollY = ry + (System.currentTimeMillis() / 12.0 % Math.max(1, rh));
        GlitchPixelOps.scanlines(dst, iw, ih, rx, ry, rw, rh, f, rollY);
        GlitchPixelOps.signalNoise(dst, iw, rx, ry, rw, rh, f, random);

        WritableImage out = frame.getOutputBuffer();
        if (out == null) {
            return false;
        }
        out.getPixelWriter().setPixels(0, 0, iw, ih,
                PixelFormat.getIntArgbInstance(), dst, 0, iw);
        frame.publish(out);
        return true;
    }

    // ==================== 矢量兜底 ====================

    /**
     * 在覆盖层上渲染故障效果（没有捕获画面时的兜底实现）。
     *
     * @param layer    目标覆盖层
     * @param fraction 强度 0.0-1.0
     * @param x        区域左上角 X
     * @param y        区域左上角 Y
     * @param w        区域宽度
     * @param h        区域高度
     */
    public void render(Pane layer, double fraction, double x, double y, double w, double h) {
        if (layer == null) return;
        final double f = Math.max(0, Math.min(1, fraction));
        Platform.runLater(() -> {
            layer.getChildren().removeAll(glitchNodes);
            glitchNodes.clear();

            Random rand = new Random();
            int density = (int) (280 * f);

            for (int i = 0; i < density; i++) {
                double type = rand.nextDouble();

                if (type < 0.35) {
                    Rectangle bar = new Rectangle(
                            x, y + rand.nextDouble() * h, w, 2 + rand.nextDouble() * 12
                    );
                    bar.setFill(Color.rgb(
                            rand.nextInt(256), rand.nextInt(256), rand.nextInt(256),
                            0.15 + rand.nextDouble() * 0.3
                    ));
                    glitchNodes.add(bar);

                } else if (type < 0.55) {
                    double bw = 2 + rand.nextDouble() * 10;
                    double bh = 2 + rand.nextDouble() * 10;
                    Rectangle block = new Rectangle(
                            x + rand.nextDouble() * (w - bw),
                            y + rand.nextDouble() * (h - bh), bw, bh
                    );
                    block.setFill(Color.rgb(
                            rand.nextInt(256), rand.nextInt(256), rand.nextInt(256),
                            0.4 + rand.nextDouble() * 0.5
                    ));
                    glitchNodes.add(block);

                } else if (type < 0.70) {
                    double bw = 6 + rand.nextDouble() * 20;
                    double bh = 2 + rand.nextDouble() * 6;
                    Rectangle rgb = new Rectangle(
                            x + rand.nextDouble() * (w - bw),
                            y + rand.nextDouble() * (h - bh), bw, bh
                    );
                    int r = rand.nextBoolean() ? rand.nextInt(100) + 155 : rand.nextInt(40);
                    int g = rand.nextBoolean() ? rand.nextInt(100) + 155 : rand.nextInt(40);
                    int b = rand.nextBoolean() ? rand.nextInt(100) + 155 : rand.nextInt(40);
                    rgb.setFill(Color.rgb(r, g, b, 0.25 + rand.nextDouble() * 0.35));
                    glitchNodes.add(rgb);

                } else if (type < 0.85) {
                    Rectangle scanline = new Rectangle(
                            x, y + rand.nextDouble() * h, w, 1
                    );
                    scanline.setFill(Color.rgb(0, 0, 0, 0.08 + rand.nextDouble() * 0.12));
                    glitchNodes.add(scanline);

                } else {
                    double bw = 40 + rand.nextDouble() * (w * 0.5);
                    double bh = 1 + rand.nextDouble() * 4;
                    Rectangle tear = new Rectangle(
                            x + rand.nextDouble() * (w - bw),
                            y + rand.nextDouble() * (h - bh), bw, bh
                    );
                    tear.setFill(Color.rgb(
                            200 + rand.nextInt(56), 200 + rand.nextInt(56), 255,
                            0.3 + rand.nextDouble() * 0.4
                    ));
                    glitchNodes.add(tear);
                }
            }

            int vertCount = (int) (8 * f);
            for (int i = 0; i < vertCount; i++) {
                double vx = x + rand.nextDouble() * w;
                Rectangle vertBar = new Rectangle(vx, y, 1 + rand.nextDouble() * 3, h);
                vertBar.setFill(Color.rgb(
                        rand.nextInt(100) + 100, rand.nextInt(50), rand.nextInt(50),
                        0.05 + rand.nextDouble() * 0.15
                ));
                glitchNodes.add(vertBar);
            }

            layer.getChildren().addAll(glitchNodes);
        });
    }

    /**
     * 在覆盖层上渲染全屏故障效果
     *
     * @param layer    目标覆盖层
     * @param fraction 强度 0.0-1.0
     * @param width    区域宽度
     * @param height   区域高度
     */
    public void render(Pane layer, double fraction, double width, double height) {
        render(layer, fraction, 0, 0, width, height);
    }

    /**
     * 清除覆盖层上的故障效果
     */
    public void clear(Pane layer) {
        if (layer == null) return;
        Platform.runLater(() -> {
            layer.getChildren().removeAll(glitchNodes);
            glitchNodes.clear();
        });
    }
}
