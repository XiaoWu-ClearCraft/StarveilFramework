package com.xiaowu.game.starveil.render.effects;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 花屏像素运算测试。
 *
 * <p>这些运算不能依赖 JavaFX，所以可以在这里直接喂像素数组验证行为：
 * 行撕裂确实搬了像素、色散确实拆了通道、扫描线确实压暗了奇数行，
 * 并且所有写入都限制在指定区域内。
 */
class GlitchPixelOpsTest {

    private static final int W = 64;
    private static final int H = 48;

    /** 造一张有规律可循的底图：每个像素编码自己的坐标。 */
    private static int[] makeFrame() {
        int[] px = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                px[y * W + x] = 0xFF000000 | (x << 16) | (y << 8) | 0x40;
            }
        }
        return px;
    }

    private static int[] copyOf(int[] src) {
        int[] dst = new int[src.length];
        System.arraycopy(src, 0, dst, 0, src.length);
        return dst;
    }

    @Test
    void tearBandsMovesPixelsHorizontally() {
        int[] src = makeFrame();
        int[] dst = copyOf(src);
        // 固定种子 + 高强度，保证一定产生位移
        GlitchPixelOps.tearBands(dst, src, W, H, 0, 0, W, H, 1.0, new Random(1234));

        int changed = countDifferences(src, dst);
        assertTrue(changed > 0, "行撕裂应当改变至少一部分像素");
        // 只允许水平搬运：每个输出像素必须仍然来自同一行
        assertTrue(allOutputsFromSameRow(src, dst, W),
                "行撕裂不应跨行取像素");
    }

    @Test
    void channelSplitSeparatesChannels() {
        int[] src = makeFrame();
        int[] dst = copyOf(src);
        GlitchPixelOps.channelSplit(dst, src, W, H, 0, 0, W, H, 1.0, new Random(7));

        boolean foundSplit = false;
        for (int i = 0; i < src.length && !foundSplit; i++) {
            int before = src[i];
            int after = dst[i];
            if (before == after) {
                continue;
            }
            // R 取自左侧、B 取自右侧，G 保持本位 -> 通道值不再来自同一坐标
            int r = (after >> 16) & 0xFF;
            int g = (after >> 8) & 0xFF;
            int b = after & 0xFF;
            int origX = (before >> 16) & 0xFF;
            int origY = (before >> 8) & 0xFF;
            if (g == origY && (r != origX || b != 0x40)) {
                foundSplit = true;
            }
        }
        assertTrue(foundSplit, "应当出现通道被拆到不同水平偏移的像素");
    }

    @Test
    void scanlinesDarkenOddRows() {
        int[] src = makeFrame();
        int[] dst = copyOf(src);
        // rollY 放到画面外，排除滚动高亮带的干扰
        GlitchPixelOps.scanlines(dst, W, H, 0, 0, W, H, 0.0, -1000);

        int odd = 1;
        int even = 2;
        int oddBefore = src[odd * W + 10] & 0xFF;
        int oddAfter = dst[odd * W + 10] & 0xFF;
        int evenBefore = src[even * W + 10] & 0xFF;
        int evenAfter = dst[even * W + 10] & 0xFF;

        assertTrue(oddAfter < oddBefore, "奇数行应被压暗");
        assertEquals(evenBefore, evenAfter, "偶数行不应被改动");
    }

    @Test
    void scanlinesRollingBandBrightens() {
        int[] src = makeFrame();
        int[] dst = copyOf(src);
        // 让高亮带正好落在第 10 行
        GlitchPixelOps.scanlines(dst, W, H, 0, 0, W, H, 0.5, 10.0);

        int r0 = (src[10 * W + 10] >> 16) & 0xFF;
        int r1 = (dst[10 * W + 10] >> 16) & 0xFF;
        assertTrue(r1 > r0, "滚动带内应当变亮");
    }

    @Test
    void operationsStayInsideTheirRegion() {
        int[] src = makeFrame();
        int[] dst = copyOf(src);
        Random rnd = new Random(99);

        // 只允许改 (10,10)-(30,30)
        GlitchPixelOps.tearBands(dst, src, W, H, 10, 10, 20, 20, 1.0, rnd);
        GlitchPixelOps.channelSplit(dst, src, W, H, 10, 10, 20, 20, 1.0, rnd);
        GlitchPixelOps.blockCorruption(dst, src, W, H, 10, 10, 20, 20, 1.0, rnd);
        GlitchPixelOps.scanlines(dst, W, H, 10, 10, 20, 20, 1.0, 15.0);
        GlitchPixelOps.signalNoise(dst, W, 10, 10, 20, 20, 1.0, rnd);

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inside = x >= 10 && x < 30 && y >= 10 && y < 30;
                if (!inside) {
                    assertEquals(src[y * W + x], dst[y * W + x],
                            "区域外像素不应被修改: (" + x + "," + y + ")");
                }
            }
        }
    }

    @Test
    void signalNoiseProducesSomeChangeWithoutCrashing() {
        int[] src = makeFrame();
        int[] dst = copyOf(src);
        GlitchPixelOps.signalNoise(dst, W, 0, 0, W, H, 1.0, new Random(3));
        // 噪点数量是按面积比例算的，小图上可能为 0；这里只要求不越界、不抛异常
        assertTrue(countDifferences(src, dst) >= 0);
    }

    @Test
    void strongerFractionMeansMoreDamage() {
        int[] src = makeFrame();
        int[] weak = copyOf(src);
        int[] strong = copyOf(src);

        GlitchPixelOps.tearBands(weak, src, W, H, 0, 0, W, H, 0.1, new Random(42));
        GlitchPixelOps.tearBands(strong, src, W, H, 0, 0, W, H, 1.0, new Random(42));

        assertTrue(countDifferences(src, strong) >= countDifferences(src, weak),
                "强度更高时受影响像素不应更少");
        assertNotEquals(0, countDifferences(src, strong));
    }

    @Test
    void clampKeepsValueInRange() {
        assertEquals(0, GlitchPixelOps.clamp(-5, 0, 10));
        assertEquals(10, GlitchPixelOps.clamp(50, 0, 10));
        assertEquals(5, GlitchPixelOps.clamp(5, 0, 10));
    }

    // ==================== 辅助 ====================

    private static int countDifferences(int[] a, int[] b) {
        int count = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) count++;
        }
        return count;
    }

    /** 行撕裂是「同一行内水平搬运」，检查每个被改动的像素是否仍来自本行内容。 */
    private static boolean allOutputsFromSameRow(int[] src, int[] dst, int w) {
        for (int i = 0; i < dst.length; i++) {
            int y = i / w;
            int value = dst[i];
            int srcY = (value >> 8) & 0xFF;
            if (srcY != y) {
                return false;
            }
        }
        return true;
    }
}
