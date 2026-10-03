package com.xiaowu.game.starveil.platform.windows;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.xiaowu.game.starveil.platform.common.SystemDetector;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WindowsDarkModeUtil {

    private WindowsDarkModeUtil() {}

    // DWM窗口属性常量
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY = 19;

    // 延迟加载DWM API
    private static class DwmApiHolder {
        static final DwmApi INSTANCE;
        static {
            DwmApi instance = null;
            try {
                instance = Native.load("dwmapi", DwmApi.class);
            } catch (UnsatisfiedLinkError e) {
            }
            INSTANCE = instance;
        }
    }

    public interface DwmApi extends Library {
        int DwmSetWindowAttribute(WinDef.HWND hwnd, int dwAttribute,
                                   IntByReference pvAttribute, int cbAttribute);
    }

    // 延迟加载User32扩展
    private static class User32ExtHolder {
        static final User32Ext INSTANCE;
        static {
            User32Ext instance = null;
            try {
                instance = Native.load("user32", User32Ext.class,
                    com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS);
            } catch (UnsatisfiedLinkError e) {
            }
            INSTANCE = instance;
        }
    }

    public interface User32Ext extends User32 {
        WinDef.LRESULT CallWindowProc(Pointer lpPrevWndFunc, WinDef.HWND hWnd,
                                       int Msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam);
        INT_PTR SetWindowLongPtr(WinDef.HWND hWnd, int nIndex, Callback dwNewLong);
        INT_PTR SetWindowLong(WinDef.HWND hWnd, int nIndex, Callback dwNewLong);
    }

    // 消息常量
    private static final int WM_SETTINGCHANGE = 0x001A;
    private static final int WM_QUIT = 0x0012;
    private static final int GWL_WNDPROC = -4;

    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int WS_POPUP = 0x80000000;

    @FunctionalInterface
    public interface ThemeChangeListener {
        void onThemeChanged(boolean isDark);
    }

    private static final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();
    private static ThemeMessageWindow themeWindow;

    private static final List<javafx.beans.value.ObservableValue<javafx.stage.Stage>> stageWatchers =
        new CopyOnWriteArrayList<>();
    private static volatile boolean lastThemeState = true;

    private static boolean canUseNative() {
        return SystemDetector.isWindows() && DwmApiHolder.INSTANCE != null;
    }

    public static boolean isSupported() {
        if (!SystemDetector.isWindows()) return false;
        String version = System.getProperty("os.version");
        try {
            return version.startsWith("10") || version.startsWith("11");
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSystemDarkMode() {
        if (!isSupported()) return true;
        return readRegistryDarkMode();
    }

    private static boolean readRegistryDarkMode() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme"
            );
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
            return output.contains("0x0");
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean setTitleBarMode(WinDef.HWND hwnd, boolean dark) {
        if (hwnd == null || !isSupported() || !canUseNative()) return false;
        try {
            IntByReference value = new IntByReference(dark ? 1 : 0);
            int result = DwmApiHolder.INSTANCE.DwmSetWindowAttribute(
                hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, value, 4);
            if (result != 0) {
                DwmApiHolder.INSTANCE.DwmSetWindowAttribute(
                    hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, value, 4);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static WinDef.HWND findWindowByTitle(String title) {
        if (title == null) return null;
        final WinDef.HWND[] result = {null};
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            char[] buffer = new char[512];
            User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            if (title.equals(Native.toString(buffer))) {
                result[0] = hwnd;
                return false;
            }
            return true;
        }, null);
        return result[0];
    }

    public static boolean enableForSwing(java.awt.Frame frame, boolean dark) {
        if (frame == null || !isSupported() || !canUseNative()) return false;
        WinDef.HWND hwnd = findWindowByTitle(frame.getTitle());
        return hwnd != null && setTitleBarMode(hwnd, dark);
    }

    public static boolean enableForSwingAuto(java.awt.Frame frame) {
        return enableForSwing(frame, isSystemDarkMode());
    }

    public static boolean enableForJavaFX(javafx.stage.Stage stage, boolean dark) {
        if (stage == null || !isSupported() || !canUseNative()) return false;
        String title = stage.getTitle();
        if (title == null || title.isEmpty()) return false;
        WinDef.HWND hwnd = findWindowByTitle(title);
        return hwnd != null && setTitleBarMode(hwnd, dark);
    }

    public static boolean enableForJavaFXAuto(javafx.stage.Stage stage) {
        boolean result = enableForJavaFX(stage, isSystemDarkMode());
        registerStageForThemeUpdates(stage);
        return result;
    }

    private static void registerStageForThemeUpdates(javafx.stage.Stage stage) {
        if (stage == null) return;
        stage.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
        });
    }

    public static synchronized void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
        ensureMessageWindowStarted();
    }

    public static synchronized void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    private static synchronized void ensureMessageWindowStarted() {
        if (themeWindow != null) return;
        if (!isSupported() || !canUseNative()) return;

        themeWindow = new ThemeMessageWindow();
        themeWindow.start();
    }

    static void notifyListeners(boolean isDark) {
        lastThemeState = isDark;

        updateAllJavaFXStages(isDark);

        for (ThemeChangeListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onThemeChanged(isDark);
            } catch (Exception e) {
            }
        }
    }

    private static void updateAllJavaFXStages(boolean dark) {
        javafx.application.Platform.runLater(() -> {
            try {
                for (javafx.stage.Stage stage : javafx.stage.Window.getWindows().stream()
                        .filter(w -> w instanceof javafx.stage.Stage)
                        .map(w -> (javafx.stage.Stage) w)
                        .toList()) {
                    if (stage.isShowing()) {
                        enableForJavaFX(stage, dark);
                    }
                }
            } catch (Exception e) {
            }
        });
    }

    private static class ThemeMessageWindow {
        private Thread messageThread;
        private WinDef.HWND hwnd;
        private boolean running = true;

        void start() {
            messageThread = new Thread(this::runMessageLoop, "DarkModeMessageThread");
            messageThread.setDaemon(true);
            messageThread.start();
        }

        private void runMessageLoop() {
            try {
                String className = "DarkModeThemeMsgWindow";
                WinUser.WNDCLASSEX wndClass = new WinUser.WNDCLASSEX();
                wndClass.cbSize = wndClass.size();
                wndClass.lpfnWndProc = new WindowProc();
                wndClass.hInstance = Kernel32.INSTANCE.GetModuleHandle("");
                wndClass.lpszClassName = className;

                User32.INSTANCE.RegisterClassEx(wndClass);

                hwnd = User32.INSTANCE.CreateWindowEx(
                    WS_EX_TOOLWINDOW,
                    className,
                    "DarkModeThemeMonitor",
                    WS_POPUP,
                    -10000, -10000, 1, 1,
                    null,
                    null,
                    wndClass.hInstance,
                    null
                );

                if (hwnd == null || hwnd.getPointer() == null) {
                    int error = Native.getLastError();
                    fallbackToPolling();
                    return;
                }

                WinUser.MSG msg = new WinUser.MSG();
                while (running && User32.INSTANCE.GetMessage(msg, hwnd, 0, 0) > 0) {
                    User32.INSTANCE.TranslateMessage(msg);
                    User32.INSTANCE.DispatchMessage(msg);
                }

            } catch (Exception e) {
                fallbackToPolling();
            }
        }

        void stop() {
            running = false;
            if (hwnd != null) {
                User32.INSTANCE.PostMessage(hwnd, WM_QUIT, new WinDef.WPARAM(0), new WinDef.LPARAM(0));
            }
            if (messageThread != null) {
                messageThread.interrupt();
            }
        }

        private class WindowProc implements com.sun.jna.platform.win32.WinUser.WindowProc {
            @Override
            public WinDef.LRESULT callback(WinDef.HWND hWnd, int uMsg,
                                           WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
                if (uMsg == WM_SETTINGCHANGE) {
                    try {
                        long ptrValue = lParam.longValue();
                        if (ptrValue != 0) {
                            Pointer strPtr = new Pointer(ptrValue);
                            char[] buffer = new char[256];
                            int len = 0;
                            for (int i = 0; i < buffer.length && len < 256; i++) {
                                char c = (char) strPtr.getShort(i * 2);
                                if (c == 0) break;
                                buffer[len++] = c;
                            }
                            String section = new String(buffer, 0, len);

                            if ("ImmersiveColorSet".equals(section)) {
                                boolean isDark = readRegistryDarkMode();
                                SwingUtilities.invokeLater(() -> notifyListeners(isDark));
                            }
                        }
                    } catch (Exception e) {
                    }
                }

                return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
        }

        private void fallbackToPolling() {
            Thread pollThread = new Thread(() -> {
                boolean lastState = readRegistryDarkMode();
                while (true) {
                    try {
                        Thread.sleep(5000);
                        boolean currentState = readRegistryDarkMode();
                        if (currentState != lastState) {
                            lastState = currentState;
                            notifyListeners(currentState);
                        }
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });
            pollThread.setDaemon(true);
            pollThread.setName("DarkModeFallbackPoller");
            pollThread.start();
        }
    }
}
