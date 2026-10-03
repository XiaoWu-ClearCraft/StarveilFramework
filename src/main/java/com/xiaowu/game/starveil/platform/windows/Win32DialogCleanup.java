package com.xiaowu.game.starveil.platform.windows;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;

public class Win32DialogCleanup {

    private static final int WM_CLOSE = 0x0010;

    private interface User32Extra extends StdCallLibrary {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class);

        boolean EnumWindows(EnumWindowsProc lpEnumFunc, Pointer lParam);
        int GetClassNameW(WinDef.HWND hWnd, char[] lpClassName, int nMaxCount);
        int GetWindowThreadProcessId(WinDef.HWND hWnd, int[] lpdwProcessId);
        boolean PostMessageW(WinDef.HWND hWnd, int Msg, WinDef.WPARAM wParam, WinDef.LPARAM lParam);

        interface EnumWindowsProc extends StdCallLibrary.StdCallCallback {
            boolean callback(WinDef.HWND hWnd, Pointer lParam);
        }
    }

    private interface Kernel32Extra extends StdCallLibrary {
        Kernel32Extra INSTANCE = Native.load("kernel32", Kernel32Extra.class);
        int GetCurrentProcessId();
    }

    public static void closeAllDialogs(final String threadNamePrefix) {
        new Thread(() -> {
            WinOpsManager.setDialogBlocked(true);

            closeMessageBoxesInProcess();

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }

            for (Thread t : Thread.getAllStackTraces().keySet()) {
                if (t.getName().startsWith(threadNamePrefix)) {
                    t.interrupt();
                    try {
                        t.join(500);
                    } catch (InterruptedException ignored) {
                    }
                    if (t.isAlive()) {
                        t.interrupt();
                    }
                }
            }

            WinOpsManager.setDialogBlocked(false);
        }, "DialogCleanup-Thread").start();
    }

    private static void closeMessageBoxesInProcess() {
        int pid = Kernel32Extra.INSTANCE.GetCurrentProcessId();
        User32Extra.INSTANCE.EnumWindows(new User32Extra.EnumWindowsProc() {
            @Override
            public boolean callback(WinDef.HWND hwnd, Pointer data) {
                char[] className = new char[256];
                User32Extra.INSTANCE.GetClassNameW(hwnd, className, 256);
                String cls = new String(className).trim();
                if ("#32770".equals(cls)) {
                    int[] procId = new int[1];
                    User32Extra.INSTANCE.GetWindowThreadProcessId(hwnd, procId);
                    if (procId[0] == pid) {
                        User32Extra.INSTANCE.PostMessageW(hwnd, WM_CLOSE,
                                new WinDef.WPARAM(0), new WinDef.LPARAM(0));
                    }
                }
                return true;
            }
        }, null);
    }
}
