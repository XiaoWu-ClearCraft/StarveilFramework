package com.xiaowu.game.starveil.platform.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * Windows 桌面捕获（GDI BitBlt）。
 *
 * <p>遮罩层是整屏覆盖的，所以「遮罩层下面的画面」是<b>整个桌面合成结果</b>，
 * 而不只是游戏窗口。这里用最经典也最稳的 GDI 路径拿到它：
 * {@code GetDC(NULL) → CreateCompatibleBitmap → BitBlt → GetDIBits}。
 *
 * <p>两个关键点：
 * <ul>
 *   <li>{@link #excludeFromCapture} —— 必须把遮罩层窗口标记成
 *       {@code WDA_EXCLUDEFROMCAPTURE}，否则 BitBlt 会把遮罩层自己也拍进去，
 *       形成「捕获到自己刚贴上去的那张图」的回授；</li>
 *   <li>{@code CAPTUREBLT} —— 不带这个标志会漏掉分层窗口（很多现代 UI 都是分层窗口）。</li>
 * </ul>
 *
 * <p>说明：抓屏与 {@code SetWindowDisplayAffinity} 都<b>不需要管理员权限</b>；
 * 管理员权限影响的是别的东西。这里没有任何提权要求。
 *
 * <p>本类不是线程安全的，请在同一个线程（JavaFX 线程）上使用。
 */
public final class Win32ScreenCapture {

    /** BitBlt 光栅操作码：直接拷贝。 */
    private static final int SRCCOPY = 0x00CC0020;
    /** 连同分层窗口一起拷贝。 */
    private static final int CAPTUREBLT = 0x40000000;
    /** GetDIBits 的 usage：使用 bmiColors 中的颜色表（32 位时无意义）。 */
    private static final int DIB_RGB_COLORS = 0;
    /** StretchBlt 缩放模式：半色调，缩小时更平滑。 */
    private static final int HALFTONE = 4;

    /**
     * JNA 自带的 {@code GDI32} 没有声明 StretchBlt / SetStretchBltMode，
     * 这里补上——降采样能显著减少抓屏开销。
     */
    private interface Gdi32Extra extends com.sun.jna.win32.StdCallLibrary {
        Gdi32Extra INSTANCE = com.sun.jna.Native.load("gdi32", Gdi32Extra.class);

        boolean StretchBlt(WinDef.HDC hdcDest, int xDest, int yDest, int wDest, int hDest,
                           WinDef.HDC hdcSrc, int xSrc, int ySrc, int wSrc, int hSrc, int rop);

        int SetStretchBltMode(WinDef.HDC hdc, int mode);
    }

    /** DrawIconEx 的绘制方式：按图标原有的透明通道正常绘制。 */
    private static final int DI_NORMAL = 0x0003;

    /**
     * JNA 的 {@code User32} 没有声明这几个，补上。
     *
     * <p>光标是 DWM 单独合成在**所有窗口之上**的，抓屏拿不到它，遮罩层也盖不住它。
     * 所以「让光标跟着降帧」只能靠：把光标画进捕获帧 + 把真实光标藏起来。
     */
    private interface User32Extra2 extends com.sun.jna.win32.StdCallLibrary {
        User32Extra2 INSTANCE = com.sun.jna.Native.load("user32", User32Extra2.class);

        WinDef.HICON GetCursor();

        boolean DrawIconEx(WinDef.HDC hdc, int xLeft, int yTop, WinDef.HICON hIcon,
                           int cxWidth, int cyWidth, int istepIfAniCur,
                           WinDef.HBRUSH hbrFlickerFreeDraw, int diFlags);

        /** 返回新的显示计数；计数 < 0 时光标隐藏。 */
        int ShowCursor(boolean bShow);
    }

    /** SM_XVIRTUALSCREEN */
    private static final int SM_XVIRTUALSCREEN = 76;
    /** SM_YVIRTUALSCREEN */
    private static final int SM_YVIRTUALSCREEN = 77;
    /** SM_CXVIRTUALSCREEN */
    private static final int SM_CXVIRTUALSCREEN = 78;
    /** SM_CYVIRTUALSCREEN */
    private static final int SM_CYVIRTUALSCREEN = 79;
    /** SM_CXSCREEN（主显示器宽度，物理像素） */
    private static final int SM_CXSCREEN = 0;

    /** 从捕获中排除窗口（Win10 2004+）：窗口位置会显示它后面的内容。 */
    public static final int WDA_EXCLUDEFROMCAPTURE = 0x00000011;
    /** 旧系统的降级选项：窗口在捕获里显示为黑块。 */
    public static final int WDA_MONITOR = 0x00000001;
    /** 取消限制。 */
    public static final int WDA_NONE = 0x00000000;

    private WinDef.HDC screenDc;
    private WinDef.HDC memoryDc;
    private WinDef.HBITMAP bitmap;
    private WinNT.HANDLE previousBitmap;
    /**
     * DIB section 的像素内存首地址。
     *
     * <p>用 {@code CreateDIBSection} 而不是 {@code CreateCompatibleBitmap}：
     * 后者是设备相关位图，取像素还得再来一次 {@code GetDIBits} 转换成 DIB
     * （整整一遍 4M 像素的拷贝）。DIB section 的像素本来就躺在可访问的内存里，
     * BitBlt 画完直接读走即可。
     */
    private Pointer dibBits;

    /** 是否把光标画进捕获帧（默认开）。 */
    private boolean drawCursor = true;

    public void setDrawCursor(boolean drawCursor) {
        this.drawCursor = drawCursor;
    }

    private int width;
    private int height;
    private int sourceX;
    private int sourceY;
    private int sourceWidth;
    private int sourceHeight;

    /** 最近一次抓屏耗时（毫秒），用于确认目标帧率是否撑得住。 */
    private volatile double lastCaptureMillis = -1;

    public double getLastCaptureMillis() {
        return lastCaptureMillis;
    }

    // ==================== 光标 ====================

    /**
     * 隐藏/恢复系统光标。
     *
     * <p>显示计数是「每线程」的，可能有别的地方（JavaFX/glass）动过它，
     * 所以用循环把计数推到目标区间，最多 32 次防止死循环。
     * 进程退出时该计数会随之消失，不会把用户的系统光标永久弄没。
     */
    public static void setCursorVisible(boolean visible) {
        try {
            for (int i = 0; i < 32; i++) {
                int count = User32Extra2.INSTANCE.ShowCursor(visible);
                if (visible ? count >= 0 : count < 0) {
                    Logger("DEBUG", "[Win32ScreenCapture] 光标可见=" + visible + ", count=" + count);
                    return;
                }
            }
            Logger("WARNING", "[Win32ScreenCapture] 无法切换光标可见性（显示计数异常）");
        } catch (Throwable t) {
            Logger("DEBUG", "[Win32ScreenCapture] 切换光标可见性失败: " + t);
        }
    }

    /**
     * 把当前光标画进捕获帧。
     *
     * <p>画的是「抓屏这一刻」的位置与形状。因为只在抓屏时画，光标也就跟着
     * 降到同一个帧率——这正是让整块屏幕（含光标）看起来一起卡住的关键。
     */
    private void drawCursorInto(WinDef.HDC dc) {
        WinGDI.ICONINFO info = null;
        try {
            WinDef.HICON cursor = User32Extra2.INSTANCE.GetCursor();
            if (cursor == null || cursor.getPointer() == null) {
                return;
            }
            info = new WinGDI.ICONINFO();
            if (!User32.INSTANCE.GetIconInfo(cursor, info)) {
                return;
            }
            WinDef.POINT pt = new WinDef.POINT();
            if (!User32.INSTANCE.GetCursorPos(pt)) {
                return;
            }

            // 屏幕坐标 -> 捕获区域坐标，再减去热点
            double x = pt.x - sourceX - info.xHotspot;
            double y = pt.y - sourceY - info.yHotspot;
            if (width != sourceWidth || height != sourceHeight) {
                x = x * width / sourceWidth;
                y = y * height / sourceHeight;
            }
            User32Extra2.INSTANCE.DrawIconEx(dc, (int) Math.round(x), (int) Math.round(y),
                    cursor, 0, 0, 0, null, DI_NORMAL);
        } catch (Throwable t) {
            Logger("DEBUG", "[Win32ScreenCapture] 绘制光标失败: " + t);
        } finally {
            // GetIconInfo 会新建两张位图，必须自己删掉，否则每次抓屏都漏
            if (info != null) {
                try {
                    if (info.hbmMask != null) {
                        GDI32.INSTANCE.DeleteObject(info.hbmMask);
                    }
                    if (info.hbmColor != null) {
                        GDI32.INSTANCE.DeleteObject(info.hbmColor);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** GDI 抓屏是否可用（类能加载起来就说明可用）。 */
    public static boolean isAvailable() {
        try {
            return GDI32.INSTANCE != null && User32.INSTANCE != null;
        } catch (Throwable t) {
            Logger("WARNING", "[Win32ScreenCapture] 初始化 GDI 失败: " + t);
            return false;
        }
    }

    /**
     * 虚拟桌面（所有显示器合起来）的物理像素边界。
     *
     * @return {@code [x, y, width, height]}
     */
    public static int[] virtualScreenBounds() {
        try {
            return new int[]{
                    User32.INSTANCE.GetSystemMetrics(SM_XVIRTUALSCREEN),
                    User32.INSTANCE.GetSystemMetrics(SM_YVIRTUALSCREEN),
                    User32.INSTANCE.GetSystemMetrics(SM_CXVIRTUALSCREEN),
                    User32.INSTANCE.GetSystemMetrics(SM_CYVIRTUALSCREEN)
            };
        } catch (Throwable t) {
            return null;
        }
    }

    /** 主显示器宽度（物理像素），用于把 JavaFX 逻辑坐标换算成物理像素。 */
    public static int primaryScreenWidth() {
        try {
            return User32.INSTANCE.GetSystemMetrics(SM_CXSCREEN);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 把某个窗口从屏幕捕获中排除掉。
     *
     * <p>必须做，否则遮罩层会捕获到自己贴在屏幕上的那一帧。
     *
     * @return 是否成功；Win10 2004 之前不支持 {@code WDA_EXCLUDEFROMCAPTURE}，会返回 false
     */
    public static boolean excludeFromCapture(WinDef.HWND hwnd) {
        return setDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE);
    }

    /** 取消捕获排除。 */
    public static boolean includeInCapture(WinDef.HWND hwnd) {
        return setDisplayAffinity(hwnd, WDA_NONE);
    }

    private static boolean setDisplayAffinity(WinDef.HWND hwnd, int affinity) {
        if (hwnd == null) {
            return false;
        }
        try {
            boolean ok = User32ExtraBridge.setWindowDisplayAffinity(hwnd, affinity);
            if (!ok) {
                Logger("DEBUG", "[Win32ScreenCapture] SetWindowDisplayAffinity("
                        + affinity + ") 失败（旧系统不支持时属正常）");
            }
            return ok;
        } catch (Throwable t) {
            Logger("DEBUG", "[Win32ScreenCapture] SetWindowDisplayAffinity 异常: " + t);
            return false;
        }
    }

    /**
     * 准备一块 {@code width × height} 的离屏位图，源区域为
     * {@code (sourceX, sourceY, sourceWidth, sourceHeight)}（虚拟桌面物理像素坐标）。
     *
     * <p>源尺寸与输出尺寸不同时走 {@code StretchBlt} 降采样。抓全屏 32 位像素很贵
     * （2560x1600 大约 20-45ms），降采样后才能在 30fps 的目标下跑得动；画面会被
     * 遮罩层放大回去，对「卡住的降质画面」来说反而更自然。
     *
     * <p>尺寸或源区域变化时会自动重建。
     */
    public boolean prepare(int width, int height,
                           int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return false;
        }
        if (this.width == width && this.height == height
                && this.sourceX == sourceX && this.sourceY == sourceY
                && this.sourceWidth == sourceWidth && this.sourceHeight == sourceHeight
                && memoryDc != null && bitmap != null && dibBits != null) {
            return true;
        }
        release();

        try {
            this.width = width;
            this.height = height;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;

            screenDc = User32.INSTANCE.GetDC(null);
            if (screenDc == null) {
                release();
                return false;
            }
            memoryDc = GDI32.INSTANCE.CreateCompatibleDC(screenDc);
            if (memoryDc == null) {
                release();
                return false;
            }

            WinGDI.BITMAPINFO info = new WinGDI.BITMAPINFO();
            info.bmiHeader.biSize = info.bmiHeader.size();
            info.bmiHeader.biWidth = width;
            // 负高度 = 自上而下的 DIB，正好和屏幕坐标一致
            info.bmiHeader.biHeight = -height;
            info.bmiHeader.biPlanes = 1;
            info.bmiHeader.biBitCount = 32;
            info.bmiHeader.biCompression = WinGDI.BI_RGB;
            info.bmiHeader.biSizeImage = width * height * 4;
            info.write();

            com.sun.jna.ptr.PointerByReference bitsRef = new com.sun.jna.ptr.PointerByReference();
            bitmap = GDI32.INSTANCE.CreateDIBSection(screenDc, info, DIB_RGB_COLORS,
                    bitsRef, null, 0);
            if (bitmap == null) {
                release();
                return false;
            }
            dibBits = bitsRef.getValue();
            if (dibBits == null) {
                release();
                return false;
            }
            previousBitmap = GDI32.INSTANCE.SelectObject(memoryDc, bitmap);
            if (previousBitmap == null) {
                release();
                return false;
            }
            if (width != sourceWidth || height != sourceHeight) {
                Gdi32Extra.INSTANCE.SetStretchBltMode(memoryDc, HALFTONE);
            }
            return true;
        } catch (Throwable t) {
            Logger("WARNING", "[Win32ScreenCapture] 准备 DIB section 失败: " + t);
            release();
            return false;
        }
    }

    /**
     * 抓取一帧。
     *
     * @param argbOut 输出缓冲，长度必须 &ge; {@code width * height}；
     *                写入的是 {@code 0xAARRGGBB}（alpha 一律补成 255，桌面本身不透明）
     * @return 是否成功
     */
    public boolean captureInto(int[] argbOut) {
        if (memoryDc == null || bitmap == null || dibBits == null) {
            return false;
        }
        if (argbOut == null || argbOut.length < width * height) {
            return false;
        }
        long startedAt = System.nanoTime();
        try {
            boolean ok;
            if (width != sourceWidth || height != sourceHeight) {
                ok = Gdi32Extra.INSTANCE.StretchBlt(memoryDc, 0, 0, width, height,
                        screenDc, sourceX, sourceY, sourceWidth, sourceHeight,
                        SRCCOPY | CAPTUREBLT);
            } else {
                ok = GDI32.INSTANCE.BitBlt(memoryDc, 0, 0, width, height,
                        screenDc, sourceX, sourceY, SRCCOPY | CAPTUREBLT);
            }
            if (!ok) {
                return false;
            }
            // 光标不在屏幕 DC 里（DWM 单独合成），手工画进去
            if (drawCursor) {
                drawCursorInto(memoryDc);
            }
            // BitBlt 已经把像素写进 DIB section 的内存，直接读走，无需再转换
            dibBits.read(0, argbOut, 0, width * height);
            // GDI 抓屏的 alpha 通常是 0，直接贴到 JavaFX 上会整张透明，这里统一补满
            int pixels = width * height;
            for (int i = 0; i < pixels; i++) {
                argbOut[i] |= 0xFF000000;
            }
            return true;
        } catch (Throwable t) {
            Logger("WARNING", "[Win32ScreenCapture] 抓屏失败: " + t);
            return false;
        } finally {
            lastCaptureMillis = (System.nanoTime() - startedAt) / 1_000_000.0;
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** 释放所有 GDI 资源；可重复调用。 */
    public void release() {
        try {
            if (memoryDc != null && previousBitmap != null) {
                GDI32.INSTANCE.SelectObject(memoryDc, previousBitmap);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (bitmap != null) {
                GDI32.INSTANCE.DeleteObject(bitmap);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (memoryDc != null) {
                GDI32.INSTANCE.DeleteDC(memoryDc);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (screenDc != null) {
                User32.INSTANCE.ReleaseDC(null, screenDc);
            }
        } catch (Throwable ignored) {
        }
        screenDc = null;
        memoryDc = null;
        bitmap = null;
        previousBitmap = null;
        dibBits = null;
        width = 0;
        height = 0;
    }

    /**
     * 用 {@code User32Extra} 里的声明调用 {@code SetWindowDisplayAffinity}
     * （JNA 自带的 {@code User32} 没有这个方法）。
     */
    private static final class User32ExtraBridge {
        private User32ExtraBridge() {}

        static boolean setWindowDisplayAffinity(WinDef.HWND hwnd, int affinity) {
            return WinOpsManager.User32Extra.INSTANCE
                    .SetWindowDisplayAffinity(hwnd, affinity);
        }
    }
}
