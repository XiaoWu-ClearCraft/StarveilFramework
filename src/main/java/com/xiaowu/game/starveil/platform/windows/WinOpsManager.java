package com.xiaowu.game.starveil.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.reflect.Method;
import java.util.List;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class WinOpsManager {

    public interface User32Extra extends StdCallLibrary {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class);

        int MB_OK = 0x00000000;
        int MB_OKCANCEL = 0x00000001;
        int MB_YESNO = 0x00000004;
        int MB_ICONINFORMATION = 0x00000040;
        int MB_ICONWARNING = 0x00000030;
        int MB_ICONERROR = 0x00000010;
        int MB_ICONQUESTION = 0x00000020;

        /**
         * 应用模态：弹窗关闭前，其 owner 窗口收不到输入。
         *
         * <p>注意值就是 0 —— 真正决定模态的是 {@code MessageBoxW} 的 <b>owner 参数</b>：
         * 传 null 时弹窗没有属主，就成了普通顶层窗口，玩家可以直接点回主窗口。
         */
        int MB_APPLMODAL = 0x00000000;

        int IDOK = 1;
        int IDCANCEL = 2;
        int IDYES = 6;
        int IDNO = 7;

        int MessageBoxW(WinDef.HWND hWnd, WString text, WString caption, int type);

        /**
         * 启用/禁用窗口输入。
         *
         * <p>JNA 自带的 {@code User32} 没有声明这个方法，所以放在这里。
         * 用于在弹窗期间兜底锁住主窗口（跨线程调用 MessageBox 时系统的模态禁用不一定生效）。
         */
        boolean EnableWindow(WinDef.HWND hWnd, boolean bEnable);

        boolean GetWindowRect(WinDef.HWND hWnd, WinDef.RECT rect);
        boolean SetWindowPos(WinDef.HWND hWnd, WinDef.HWND hWndInsertAfter,
                             int X, int Y, int cx, int cy, int uFlags);

        int GetWindowLongW(WinDef.HWND hWnd, int nIndex);
        int SetWindowLongW(WinDef.HWND hWnd, int nIndex, int dwNewLong);

        boolean MessageBeep(int uType);

        WinDef.HWND FindWindowW(String lpClassName, String lpWindowName);
        int GetWindowTextW(WinDef.HWND hWnd, char[] lpString, int nMaxCount);

        /**
         * 设置窗口的「显示亲和性」。
         *
         * <p>用 {@code WDA_EXCLUDEFROMCAPTURE (0x11)} 可以让窗口对抓屏 API 不可见
         * （窗口位置显示它后面的内容），这是遮罩层能安全抓取下层桌面的前提。
         * JNA 自带的 {@code User32} 没有声明这个方法，所以放在这里。
         */
        boolean SetWindowDisplayAffinity(WinDef.HWND hWnd, int dwAffinity);
    }

    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_SHOWWINDOW = 0x0040;

    private static final WinDef.HWND HWND_TOPMOST = new WinDef.HWND(com.sun.jna.Pointer.createConstant(-1));
    private static final WinDef.HWND HWND_NOTOPMOST = new WinDef.HWND(com.sun.jna.Pointer.createConstant(-2));

    private static final int MB_ICONASTERISK = 0x00000040;
    private static final int MB_ICONEXCLAMATION = 0x00000030;
    private static final int MB_ICONHAND = 0x00000010;
    private static final int MB_OK = 0x00000000;

    // 全局弹窗阻断标志 — 从游戏返回主菜单时设为 true，阻止新弹窗
    private static volatile boolean dialogBlocked = false;

    public static void setDialogBlocked(boolean blocked) {
        dialogBlocked = blocked;
    }

    public static boolean isDialogBlocked() {
        return dialogBlocked;
    }

    /**
     * 统一的原生弹窗入口 —— 把 MessageBox 挂到游戏主窗口上当【子窗口】。
     *
     * <p>原来所有调用都传 {@code null} 作为 owner，弹窗就成了<b>无属主</b>的顶层窗口：
     * 玩家可以直接点回游戏窗口，弹窗被压到后面，看起来像"游戏卡住了"。
     *
     * <p>传了 owner HWND 之后 Windows 会把它当作 {@code MB_APPLMODAL} 子窗，
     * 主窗口在弹窗关闭前收不到输入，也就无法抢焦点。
     *
     * <p>另外在弹窗前后<b>显式</b> {@code EnableWindow} 一次：跨线程调用 MessageBox 时
     * 系统的模态禁用有时不生效，显式禁用能兜住这种情况。拿不到句柄时退化回原行为。
     */
    private static int showOwnedMessageBox(String title, String message, int type) {
        WinDef.HWND owner = gameWindowHandle();
        if (owner == null) {
            // 拿不到主窗口句柄 —— 只能退化成无属主弹窗（至少不会崩）
            return User32Extra.INSTANCE.MessageBoxW(null,
                    new WString(message), new WString(title), type | User32Extra.MB_APPLMODAL);
        }

        User32Extra.INSTANCE.EnableWindow(owner, false);
        try {
            return User32Extra.INSTANCE.MessageBoxW(owner,
                    new WString(message), new WString(title), type | User32Extra.MB_APPLMODAL);
        } finally {
            User32Extra.INSTANCE.EnableWindow(owner, true);
            // 弹窗关掉后主动把焦点交还游戏窗口，避免焦点留在别处
            User32.INSTANCE.SetForegroundWindow(owner);
        }
    }

    /** 取游戏主窗口句柄；拿不到返回 null（此时弹窗退化为无属主）。 */
    private static WinDef.HWND gameWindowHandle() {
        try {
            Stage stage = com.xiaowu.game.starveil.game.state.GameManager
                    .getInstance().getPrimaryStage();
            if (stage == null || !stage.isShowing()) {
                return null;
            }
            return getStageHandle(stage);
        } catch (Exception e) {
            return null;
        }
    }

    public static void showInfo(String title, String message) {
        if (dialogBlocked) {
            Logger("WARNING", "[弹窗被拦截] " + title + ": " + message);
            return;
        }
        showOwnedMessageBox(title, message,
                User32Extra.MB_OK | User32Extra.MB_ICONINFORMATION);
    }

    public static void showWarning(String title, String message) {
        if (dialogBlocked) {
            Logger("WARNING", "[弹窗被拦截] " + title + ": " + message);
            return;
        }
        showOwnedMessageBox(title, message,
                User32Extra.MB_OK | User32Extra.MB_ICONWARNING);
    }

    public static void showError(String title, String message) {
        if (dialogBlocked) {
            Logger("ERROR", "[弹窗被拦截] " + title + ": " + message);
            return;
        }
        showOwnedMessageBox(title, message,
                User32Extra.MB_OK | User32Extra.MB_ICONERROR);
    }

    public static boolean showConfirm(String title, String message) {
        if (dialogBlocked) {
            Logger("WARNING", "[弹窗被拦截] " + title + ": " + message + " → 按取消处理");
            return false;
        }
        int result = showOwnedMessageBox(title, message,
                User32Extra.MB_YESNO | User32Extra.MB_ICONQUESTION);
        return result == User32Extra.IDYES;
    }

    public static int showCustom(String title, String message, int buttons, int icon) {
        if (dialogBlocked) {
            Logger("WARNING", "[弹窗被拦截] " + title + ": " + message);
            return 1;
        }
        return showOwnedMessageBox(title, message, buttons | icon);
    }

    public static void shakeWindow(Stage stage, int soundType) {
        try {
            playSystemSound(soundType);
            performWindowShake(stage);
        } catch (Exception e) {
            Logger("WARNING", "窗口抖动失败: " + e.getMessage());
        }
    }

    public static void shakeWindow(Stage stage) {
        shakeWindow(stage, 2);
    }

    private static void playSystemSound(int soundType) {
        try {
            int beepType = switch (soundType) {
                case 1 -> MB_ICONASTERISK;
                case 2 -> MB_ICONEXCLAMATION;
                case 3 -> MB_ICONHAND;
                default -> MB_OK;
            };
            User32Extra.INSTANCE.MessageBeep(beepType);
        } catch (Exception e) {
            Logger("WARNING", "播放提示音失败: " + e.getMessage());
        }
    }

    private static void performWindowShake(Stage stage) {
        try {
            WinDef.HWND hwnd = getWindowHandle(stage);
            if (hwnd == null) {
                Logger("DEBUG", "取不到窗口句柄，尝试备用方法");
                hwnd = getWindowHandleByTitle(stage);
                if (hwnd == null) {
                    Logger("WARNING", "备用方法也取不到窗口句柄，无法执行窗口抖动");
                    return;
                }
            }

            WinDef.RECT rect = new WinDef.RECT();
            if (!User32Extra.INSTANCE.GetWindowRect(hwnd, rect)) {
                Logger("WARNING", "无法获取窗口位置，抖动中止");
                return;
            }

            int shakeDistance = GameConstants.SHAKE_DISTANCE;
            int shakeCount = GameConstants.SHAKE_COUNT;
            int delay = GameConstants.SHAKE_DELAY_MS;

            int originalX = rect.left;
            int originalY = rect.top;

            for (int i = 0; i < shakeCount; i++) {
                int xOffset, yOffset;
                yOffset = switch (i % 4) {
                    case 1 -> {
                        xOffset = -shakeDistance;
                        yield -shakeDistance;
                    }
                    case 2 -> {
                        xOffset = -shakeDistance;
                        yield shakeDistance;
                    }
                    case 3 -> {
                        xOffset = shakeDistance;
                        yield -shakeDistance;
                    }
                    default -> {
                        xOffset = shakeDistance;
                        yield shakeDistance;
                    }
                };

                User32Extra.INSTANCE.SetWindowPos(hwnd, null,
                        originalX + xOffset,
                        originalY + yOffset,
                        0, 0,
                        SWP_NOSIZE | SWP_NOZORDER);

                sleep(delay);
            }

            User32Extra.INSTANCE.SetWindowPos(hwnd, null,
                    originalX, originalY,
                    0, 0,
                    SWP_NOSIZE | SWP_NOZORDER);

        } catch (Exception e) {
            Logger("WARNING", "窗口抖动执行失败: " + e.getMessage());
        }
    }

    /**
     * 取 JavaFX 窗口对应的 Win32 窗口句柄。
     *
     * <p>公开出来是给需要 HWND 的功能用（例如把遮罩层从屏幕捕获中排除）。
     * 拿不到时返回 null。
     */
    public static WinDef.HWND getStageHandle(Stage stage) {
        if (stage == null) {
            return null;
        }
        return getWindowHandle(stage);
    }

    private static WinDef.HWND getWindowHandle(Stage stage) {
        if (stage == null) {
            return null;
        }

        // 1) 按标题找：最稳，且不需要任何额外 JVM 参数
        WinDef.HWND byTitle = findByTitle(stage.getTitle());
        if (byTitle != null) {
            return byTitle;
        }

        // 2) 退化：本进程里面积最大的可见顶层窗口。
        //    遮罩层是无标题的全屏窗口，游戏主窗口有标题（走第 1 步），
        //    所以这里挑出的通常正是遮罩层。
        return findLargestOwnWindow();
    }

    /**
     * 按窗口标题查找。先 {@code FindWindow}，再用 {@code EnumWindows} 精确比对，
     * 避免标题里有前后空格 / 编码差异时找不到。
     */
    private static WinDef.HWND findByTitle(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        try {
            WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, title);
            if (hwnd != null) {
                return hwnd;
            }
        } catch (Throwable ignored) {
        }

        try {
            final String expected = title.trim();
            final WinDef.HWND[] found = new WinDef.HWND[1];
            User32.INSTANCE.EnumWindows((hwnd, data) -> {
                char[] text = new char[512];
                User32.INSTANCE.GetWindowText(hwnd, text, text.length);
                if (new String(text).trim().equals(expected)) {
                    found[0] = hwnd;
                    return false;
                }
                return true;
            }, null);
            return found[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 在本进程的可见顶层窗口里挑面积最大的一个。
     *
     * <p>JavaFX 25 的 {@code com.sun.glass.ui.Window} 已经<b>没有</b> {@code getStage()}，
     * 旧代码靠反射从 glass 窗口反查 Stage 的做法在新版本上必然失败
     * （所有 Win32 调用都会因为拿不到句柄而静默失效）。这里改用纯 Win32 枚举。
     */
    private static WinDef.HWND findLargestOwnWindow() {
        final int ourPid = (int) ProcessHandle.current().pid();
        final WinDef.HWND[] best = {null};
        final long[] bestArea = {0L};
        try {
            User32.INSTANCE.EnumWindows((hwnd, data) -> {
                try {
                    if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
                        return true;
                    }
                    com.sun.jna.ptr.IntByReference pid = new com.sun.jna.ptr.IntByReference();
                    User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
                    if (pid.getValue() != ourPid) {
                        return true;
                    }
                    WinDef.RECT rect = new WinDef.RECT();
                    if (!User32Extra.INSTANCE.GetWindowRect(hwnd, rect)) {
                        return true;
                    }
                    long area = (long) Math.max(0, rect.right - rect.left)
                            * Math.max(0, rect.bottom - rect.top);
                    if (area > bestArea[0]) {
                        bestArea[0] = area;
                        best[0] = hwnd;
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }, null);
        } catch (Throwable ignored) {
        }
        return best[0];
    }

    private static WinDef.HWND getWindowHandleByTitle(Stage stage) {
        return findByTitle(stage == null ? null : stage.getTitle());
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 使用 JNA SetWindowPos 将窗口设置为始终最上层
     */
    public static void setAlwaysOnTop(Stage stage, boolean topmost) {
        try {
            WinDef.HWND hwnd = getWindowHandle(stage);
            if (hwnd == null) return;
            User32Extra.INSTANCE.SetWindowPos(hwnd,
                topmost ? HWND_TOPMOST : HWND_NOTOPMOST,
                0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW);
        } catch (Exception e) {
            Logger("WARNING", "设置窗口置顶失败: " + e.getMessage());
        }
    }

    // 扩展窗口样式：工具窗口（不显示在任务栏）
    private static final int GWL_EXSTYLE = -20;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    /** 有该样式时 Windows 会强制在任务栏上放一个按钮，必须一起清掉。 */
    private static final int WS_EX_APPWINDOW = 0x00040000;
    private static final int SWP_FRAMECHANGED = 0x0020;

    /**
     * 设置窗口是否显示在任务栏。
     *
     * <p>要点（只设置 {@code WS_EX_TOOLWINDOW} 往往不生效）：
     * <ol>
     *   <li>加 {@code WS_EX_TOOLWINDOW} 的同时必须清掉 {@code WS_EX_APPWINDOW}，
     *       否则任务栏按钮依旧存在；</li>
     *   <li>改完扩展样式必须补一次 {@code SetWindowPos(SWP_FRAMECHANGED)}，
     *       非客户区才会被重新计算，任务栏按钮随之消失。</li>
     * </ol>
     *
     * @param stage 目标窗口
     * @param skip  true=从任务栏隐藏, false=显示在任务栏
     */
    public static void setSkipTaskbar(Stage stage, boolean skip) {
        try {
            WinDef.HWND hwnd = getWindowHandle(stage);
            if (hwnd == null) {
                // 窗口刚创建时可能还查不到，调用方会隔一段时间重试，这里不刷 ERROR
                LoggerManager.Logger("DEBUG", "设置任务栏显示: 暂时拿不到窗口句柄");
                return;
            }
            int exStyle = User32Extra.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE);
            int newStyle = skip
                    ? (exStyle | WS_EX_TOOLWINDOW) & ~WS_EX_APPWINDOW
                    : (exStyle & ~WS_EX_TOOLWINDOW) | WS_EX_APPWINDOW;
            if (newStyle == exStyle) {
                forceFrameChange(hwnd);
                return;
            }
            User32Extra.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, newStyle);
            forceFrameChange(hwnd);
            LoggerManager.Logger("DEBUG", "跳过任务栏: " + skip
                    + ", exStyle=0x" + Integer.toHexString(newStyle));
        } catch (Exception e) {
            Logger("WARNING", "设置任务栏显示失败: " + e.getMessage());
        }
    }

    /** 让 Windows 重新计算非客户区，扩展样式变更才会真正生效。 */
    private static void forceFrameChange(WinDef.HWND hwnd) {
        User32Extra.INSTANCE.SetWindowPos(hwnd, null, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED | SWP_SHOWWINDOW);
    }

    /** WS_EX_TRANSPARENT：鼠标命中测试直接穿透到下层窗口。 */
    private static final int WS_EX_TRANSPARENT = 0x00000020;

    /**
     * 设置窗口是否对鼠标完全穿透。
     *
     * <p>透明遮罩层平时靠「分层窗口的 alpha 命中测试」让点击漏下去；
     * 一旦遮罩层画出不透明内容（降帧保持的整屏截图、花屏），
     * alpha 全满，点击就会被遮罩层吃掉。这时需要显式加 {@code WS_EX_TRANSPARENT}
     * 把鼠标事件全部让给下层游戏窗口。
     *
     * <p>只影响鼠标，不影响键盘焦点。
     */
    public static void setClickThrough(Stage stage, boolean clickThrough) {
        try {
            WinDef.HWND hwnd = getWindowHandle(stage);
            if (hwnd == null) {
                return;
            }
            int exStyle = User32Extra.INSTANCE.GetWindowLongW(hwnd, GWL_EXSTYLE);
            int newStyle = clickThrough
                    ? exStyle | WS_EX_TRANSPARENT
                    : exStyle & ~WS_EX_TRANSPARENT;
            if (newStyle == exStyle) {
                return;
            }
            User32Extra.INSTANCE.SetWindowLongW(hwnd, GWL_EXSTYLE, newStyle);
            forceFrameChange(hwnd);
            LoggerManager.Logger("DEBUG", "覆盖层鼠标穿透: " + clickThrough);
        } catch (Exception e) {
            LoggerManager.Logger("DEBUG", "设置鼠标穿透失败: " + e.getMessage());
        }
    }

    /**
     * 使用 JNA SetForegroundWindow 将窗口带到前台并抢占焦点
     */
    public static void setForegroundWindow(Stage stage) {
        try {
            WinDef.HWND hwnd = getWindowHandle(stage);
            if (hwnd == null) return;
            User32.INSTANCE.SetForegroundWindow(hwnd);
        } catch (Exception e) {
            Logger("WARNING", "设置前台窗口失败: " + e.getMessage());
        }
    }

    public static void testShake(Stage stage) {
        shakeWindow(stage, 2);
    }
}