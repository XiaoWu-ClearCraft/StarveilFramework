package com.xiaowu.game.starveil.render.effects;

import java.util.Random;

/**
 * 花屏像素运算（纯计算，不依赖 JavaFX）。
 *
 * <p>所有方法都在 ARGB {@code int[]} 上原地操作，便于单独测试与复用。
 * 坐标语义：
 * <ul>
 *   <li>{@code w/h} —— 整张画面的宽高（用于把像素坐标换算成数组下标）</li>
 *   <li>{@code rx/ry/rw/rh} —— 本次生效的矩形区域（画面像素坐标）</li>
 *   <li>{@code src} —— 原始画面（只读，避免多次修改互相叠加）</li>
 *   <li>{@code dst} —— 输出缓冲（可写）</li>
 * </ul>
 *
 * <p>这些运算组合起来就是真实数字信号故障的观感：
 * 行撕裂 → 块错位 / 宏块损坏 → RGB 色散 → 扫描线滚动 → 颗粒噪点。
 */
final class GlitchPixelOps {

    private GlitchPixelOps() {}

    /**
     * 行撕裂：整条水平带整体横向错位，两端按区域边界补齐。
     */
    static void tearBands(int[] dst, int[] src, int w, int h,
                          int rx, int ry, int rw, int rh, double f, Random rnd) {
        int bands = 2 + (int) (f * 16);
        int maxBandHeight = Math.max(1, (int) (rh * (0.02 + 0.10 * f)));
        int maxShift = Math.max(4, (int) (rw * (0.02 + 0.18 * f)));

        for (int i = 0; i < bands; i++) {
            int bandH = 1 + rnd.nextInt(maxBandHeight);
            int bandY = ry + rnd.nextInt(Math.max(1, rh - bandH));
            int shift = rnd.nextInt(maxShift * 2 + 1) - maxShift;
            int yEnd = Math.min(bandY + bandH, ry + rh);
            int xEnd = rx + rw;

            for (int y = bandY; y < yEnd; y++) {
                int rowStart = y * w;
                for (int x = rx; x < xEnd; x++) {
                    int sx = clamp(x + shift, rx, xEnd - 1);
                    dst[rowStart + x] = src[rowStart + sx];
                }
            }
        }
    }

    /**
     * RGB 色散：带子里三个通道分别取自不同水平偏移，产生彩色拖影。
     */
    static void channelSplit(int[] dst, int[] src, int w, int h,
                             int rx, int ry, int rw, int rh, double f, Random rnd) {
        int bands = 1 + (int) (f * 6);
        int maxHeight = Math.max(1, (int) (rh * 0.06));
        int maxOffset = Math.max(2, (int) (8 + f * 26));

        for (int i = 0; i < bands; i++) {
            int bandH = 2 + rnd.nextInt(maxHeight);
            int bandY = ry + rnd.nextInt(Math.max(1, rh - bandH));
            int off = 2 + rnd.nextInt(maxOffset);
            int yEnd = Math.min(bandY + bandH, ry + rh);
            int xEnd = rx + rw;

            for (int y = bandY; y < yEnd; y++) {
                int rowStart = y * w;
                for (int x = rx; x < xEnd; x++) {
                    int center = src[rowStart + x];
                    int leftPx = src[rowStart + clamp(x - off, rx, xEnd - 1)];
                    int rightPx = src[rowStart + clamp(x + off, rx, xEnd - 1)];
                    int r = (leftPx >> 16) & 0xFF;
                    int g = (center >> 8) & 0xFF;
                    int b = rightPx & 0xFF;
                    dst[rowStart + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            }
        }
    }

    /**
     * 区块花块：模拟压缩宏块损坏——整块被别处内容顶掉，或直接变成纯噪声。
     */
    static void blockCorruption(int[] dst, int[] src, int w, int h,
                                int rx, int ry, int rw, int rh, double f, Random rnd) {
        int blocks = 2 + (int) (f * 42);
        for (int i = 0; i < blocks; i++) {
            int bw = 8 << rnd.nextInt(3);
            int bh = 8 << rnd.nextInt(3);
            int bx = rx + rnd.nextInt(Math.max(1, Math.max(1, rw - bw)));
            int by = ry + rnd.nextInt(Math.max(1, Math.max(1, rh - bh)));

            boolean noiseBlock = rnd.nextDouble() < 0.35 * f;
            int dx = rnd.nextInt(41) - 20;
            int dy = rnd.nextInt(21) - 10;
            int yEnd = Math.min(by + bh, ry + rh);
            int xEnd = Math.min(bx + bw, rx + rw);

            for (int y = by; y < yEnd; y++) {
                int rowStart = y * w;
                for (int x = bx; x < xEnd; x++) {
                    if (noiseBlock) {
                        int v = rnd.nextInt(256);
                        int r = rnd.nextBoolean() ? v : rnd.nextInt(256);
                        dst[rowStart + x] = (0x80 + rnd.nextInt(0x60)) << 24
                                | (r << 16) | (v << 8) | rnd.nextInt(256);
                    } else {
                        int sx = clamp(x + dx, rx, rx + rw - 1);
                        int sy = clamp(y + dy, ry, ry + rh - 1);
                        dst[rowStart + x] = src[sy * w + sx];
                    }
                }
            }
        }
    }

    /**
     * 扫描线 + 一条滚动高亮带（CRT / 行不同步的观感）。
     *
     * @param rollY 高亮带中心所在的像素行；由调用方按时间推进，测试里可固定
     */
    static void scanlines(int[] dst, int w, int h,
                          int rx, int ry, int rw, int rh, double f, double rollY) {
        double rollHeight = Math.max(24, rh * 0.08);
        int yEnd = Math.min(ry + rh, h);
        int xEnd = Math.min(rx + rw, w);

        for (int y = ry; y < yEnd; y++) {
            int rowStart = y * w;
            boolean odd = (y & 1) == 1;
            double distance = Math.abs(y - rollY);
            double rollBoost = distance < rollHeight
                    ? (1.0 - distance / rollHeight) * (0.16 * (0.4 + f))
                    : 0;

            for (int x = rx; x < xEnd; x++) {
                int argb = dst[rowStart + x];
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                if (odd) {
                    r = (int) (r * 0.82);
                    g = (int) (g * 0.82);
                    b = (int) (b * 0.82);
                }
                if (rollBoost > 0) {
                    r = clamp((int) (r + 255 * rollBoost), 0, 255);
                    g = clamp((int) (g + 255 * rollBoost), 0, 255);
                    b = clamp((int) (b + 255 * rollBoost), 0, 255);
                }
                dst[rowStart + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }

    /**
     * 细粒度信号噪点：稀疏地撒一些亮暗点。
     */
    static void signalNoise(int[] dst, int w,
                            int rx, int ry, int rw, int rh, double f, Random rnd) {
        int count = (int) (Math.max(1, rw) * Math.max(1, rh) * 0.0006 * (0.3 + f));
        for (int i = 0; i < count; i++) {
            int x = rx + rnd.nextInt(Math.max(1, rw));
            int y = ry + rnd.nextInt(Math.max(1, rh));
            int index = y * w + x;
            if (index < 0 || index >= dst.length) {
                continue;
            }
            int v = rnd.nextInt(256);
            int a = 0x40 + rnd.nextInt(0x80);
            dst[index] = (a << 24) | (v << 16) | (v << 8) | v;
        }
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
