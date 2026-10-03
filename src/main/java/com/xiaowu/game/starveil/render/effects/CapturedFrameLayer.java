package com.xiaowu.game.starveil.render.effects;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.SnapshotParameters;

/**
 * 捕获帧层 —— 遮罩层里那张「下方画面的快照」。
 *
 * <p>它只改变遮罩层自己显示的内容，<b>不动任何系统设置</b>：
 * 把遮罩层下面的画面抓成一张图贴在最底下，然后按目标帧率更新这张图。
 * 两次更新之间画面是静止的，于是观众看到的刷新率就被压到了目标帧率。
 *
 * <p>画面来源有两种，优先用前者：
 * <ol>
 *   <li>{@link OverlayFrameCapture}（Windows 上是 GDI 抓整个屏幕，
 *       因此任务栏和其它窗口也一并被遮住，和真实「遮罩」一致）；</li>
 *   <li>退回 JavaFX 节点快照（只抓游戏场景根节点）。</li>
 * </ol>
 *
 * <p>同一张图也被花屏效果复用：花屏直接在这张图上做撕裂/错位/色散。
 */
final class CapturedFrameLayer {

    private final ImageView view = new ImageView();

    /** 外部提供的画面来源；为 null 时走节点快照。 */
    private OverlayFrameCapture captureStrategy;

    /** 最近一次捕获到的原始画面。 */
    private WritableImage base;
    /** 捕获画面的 ARGB 像素缓冲（花屏直接改它）。 */
    private int[] pixels;
    /** 花屏输出缓冲，尺寸与 base 相同。 */
    private WritableImage output;

    private int width;
    private int height;

    /** 节点快照用的被捕获节点（游戏场景根节点）。 */
    private Node source;
    /** 目标帧率，<= 0 表示关闭降帧。可能从非 FX 线程读取。 */
    private volatile double throttleFps;
    private volatile long lastCaptureNanos;
    private volatile boolean attached;

    CapturedFrameLayer() {
        view.setMouseTransparent(true);
        view.setPreserveRatio(false);
        view.setSmooth(false);
    }

    // ==================== 画面来源 ====================

    void setCaptureStrategy(OverlayFrameCapture strategy) {
        this.captureStrategy = strategy;
    }

    OverlayFrameCapture getCaptureStrategy() {
        return captureStrategy;
    }

    /** 释放外部画面来源占用的资源。 */
    void releaseCaptureStrategy() {
        OverlayFrameCapture strategy = this.captureStrategy;
        this.captureStrategy = null;
        if (strategy != null) {
            strategy.release();
        }
    }

    // ==================== 挂载 ====================

    void setSource(Node source) {
        this.source = source;
    }

    Node getSource() {
        return source;
    }

    /**
     * 设置这张捕获图在遮罩层里的位置与大小。
     *
     * <p>遮罩层是整屏的，游戏窗口不一定整屏，所以不能简单拉伸铺满——
     * 否则窗口化运行时画面会被拉到整个屏幕上。
     */
    void setViewport(double x, double y, double width, double height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        view.setLayoutX(x);
        view.setLayoutY(y);
        view.setFitWidth(width);
        view.setFitHeight(height);
    }

    /** 把捕获层挂到遮罩层最底下（索引 0，永远在其它特效之下）。 */
    void reassert(Pane layer) {
        if (layer == null) {
            return;
        }
        if (layer.getChildren().contains(view)) {
            // 蓝屏会 clear 整个层，这里保证它仍然在最底下
            if (layer.getChildren().indexOf(view) != 0) {
                layer.getChildren().remove(view);
                layer.getChildren().add(0, view);
            }
            attached = true;
            return;
        }
        layer.getChildren().add(0, view);
        attached = true;
    }

    void detach(Pane layer) {
        if (layer != null) {
            layer.getChildren().remove(view);
        }
        attached = false;
    }

    boolean isAttached() {
        return attached;
    }

    boolean isShowing() {
        return attached && view.getImage() != null;
    }

    // ==================== 降帧 ====================

    void setThrottleFps(double fps) {
        this.throttleFps = fps > 0 ? fps : 0;
        if (this.throttleFps <= 0) {
            lastCaptureNanos = 0;
        }
    }

    double getThrottleFps() {
        return throttleFps;
    }

    boolean isThrottling() {
        return throttleFps > 0;
    }

    /**
     * 每个渲染脉冲调用一次；到点才重新捕获并刷新遮罩层内容。
     *
     * <p>捕获本身丢到 {@code Platform.runLater} 里执行：在 animation pulse 中间
     * 同步渲染整棵场景树容易和布局/渲染阶段打架，放到任务队列里更稳。
     */
    void tick() {
        if (throttleFps <= 0) {
            return;
        }
        if (source == null && captureStrategy == null) {
            return;
        }
        long now = System.nanoTime();
        long interval = (long) (1_000_000_000L / throttleFps);
        if (lastCaptureNanos != 0 && now - lastCaptureNanos < interval) {
            return;
        }
        lastCaptureNanos = now;
        Platform.runLater(() -> {
            if (throttleFps <= 0) {
                return;
            }
            if (capture()) {
                publish(base);
            }
        });
    }

    /**
     * 立刻捕获一帧。优先用外部画面来源（整屏），失败时退回节点快照。
     *
     * @return 是否捕获成功
     */
    boolean capture() {
        OverlayFrameCapture strategy = captureStrategy;
        if (strategy != null) {
            int w = strategy.width();
            int h = strategy.height();
            if (w > 0 && h > 0) {
                ensureBufferSize(w, h);
                if (strategy.captureInto(pixels)) {
                    ensureBaseImage(w, h);
                    pushPixelsToBase();
                    return true;
                }
                LoggerManager.Logger("DEBUG",
                        "[CapturedFrameLayer] 外部画面来源抓取失败，退回节点快照");
            }
        }
        return captureFromNode();
    }

    /** 抓游戏场景根节点（外部画面来源不可用时的兜底）。 */
    private boolean captureFromNode() {
        Node node = source;
        if (node == null) {
            return false;
        }
        try {
            Bounds bounds = node.getBoundsInParent();
            int w = (int) Math.ceil(bounds.getWidth());
            int h = (int) Math.ceil(bounds.getHeight());
            if (w <= 0 || h <= 0 || w > 16384 || h > 16384) {
                return false;
            }

            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage shot = node.snapshot(params, null);
            if (shot == null || shot.getWidth() <= 0 || shot.getHeight() <= 0) {
                return false;
            }

            int bw = (int) shot.getWidth();
            int bh = (int) shot.getHeight();
            ensureBufferSize(bw, bh);
            shot.getPixelReader().getPixels(0, 0, bw, bh,
                    PixelFormat.getIntArgbInstance(), pixels, 0, bw);
            // 快照本身就是一张可用的图，直接拿来当底图，省一次拷贝
            base = shot;
            return true;
        } catch (Throwable t) {
            LoggerManager.Logger("WARNING", "[CapturedFrameLayer] 节点快照失败: " + t);
            return false;
        }
    }

    /** 按需调整像素缓冲与花屏输出缓冲的尺寸。 */
    private void ensureBufferSize(int w, int h) {
        if (w != width || h != height || pixels == null) {
            width = w;
            height = h;
            pixels = new int[w * h];
            output = null;
        }
    }

    private void ensureBaseImage(int w, int h) {
        if (base == null || (int) base.getWidth() != w || (int) base.getHeight() != h) {
            base = new WritableImage(w, h);
        }
    }

    private void pushPixelsToBase() {
        base.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), pixels, 0, width);
    }

    /** 把一张图显示到遮罩层上。 */
    void publish(Image image) {
        view.setImage(image);
    }

    /** 清掉捕获内容，让遮罩层重新透出下层实时画面。 */
    void clear() {
        view.setImage(null);
        lastCaptureNanos = 0;
    }

    // ==================== 像素访问（花屏用） ====================

    WritableImage getBase() {
        return base;
    }

    int[] getPixels() {
        return pixels;
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    /** 与 base 同尺寸的输出缓冲，按需创建。 */
    WritableImage getOutputBuffer() {
        if (output == null || (int) output.getWidth() != width || (int) output.getHeight() != height) {
            if (width <= 0 || height <= 0) {
                return null;
            }
            output = new WritableImage(width, height);
        }
        return output;
    }
}
