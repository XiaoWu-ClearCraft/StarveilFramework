package com.xiaowu.game.starveil.render.effects;

/**
 * 遮罩层下方那一帧画面的来源。
 *
 * <p>遮罩层是整屏覆盖的，所以「它下面是什么」既可能是游戏窗口，也可能是整个桌面
 * （任务栏、后面的其它窗口……）。两种来源都实现这个接口：
 * <ul>
 *   <li>Windows 上用 GDI 抓整个屏幕（见 {@code Win32ScreenCapture}）；</li>
 *   <li>其它情况退回 JavaFX 节点快照（只抓游戏场景）。</li>
 * </ul>
 *
 * <p>实现必须保证 {@link #captureInto} 只在 JavaFX 线程被调用。
 */
interface OverlayFrameCapture {

    /** 输出像素宽度。 */
    int width();

    /** 输出像素高度。 */
    int height();

    /**
     * 抓取一帧。
     *
     * @param argb 输出缓冲，长度 &ge; {@code width() * height()}，格式 {@code 0xAARRGGBB}
     * @return 是否成功；失败时调用方会退回节点快照
     */
    boolean captureInto(int[] argb);

    /**
     * 最近一次抓取耗时（毫秒），未知时返回 -1。
     *
     * <p>用来判断当前目标帧率是否撑得住：耗时接近 {@code 1000/目标fps} 就说明
     * 该调小抓取分辨率了。
     */
    default double lastCaptureMillis() {
        return -1;
    }

    /** 释放底层资源（GDI 句柄等）。默认无操作。 */
    default void release() {
    }
}
