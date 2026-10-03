package com.xiaowu.game.starveil.platform.console;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;

import java.util.List;

/**
 * Windows 控制台提供者
 * 通过 JNA 调用 Windows 原生 API 实现丰富的命令行交互
 */
public class WindowsConsoleProvider implements ConsoleProvider {

    // Windows API 定义
    private static final int STD_INPUT_HANDLE = -10;
    private static final int STD_OUTPUT_HANDLE = -11;

    // 虚拟终端模式标志
    private static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

    // 控制台模式标志
    private static final int ENABLE_QUICK_EDIT_MODE = 0x0040;
    private static final int ENABLE_EXTENDED_FLAGS = 0x0080;

    // 事件类型常量
    private static final short KEY_EVENT = 0x0001;

    // 虚拟键码常量
    private static final short VK_LEFT = 0x25;
    private static final short VK_RIGHT = 0x27;
    private static final short VK_RETURN = 0x0D;
    private static final short VK_ESCAPE = 0x1B;
    private static final short VK_A = 0x41;
    private static final short VK_D = 0x44;

    // 扩展的Kernel32接口
    private interface Kernel32Ext extends Kernel32 {
        Kernel32Ext INSTANCE = Native.load("kernel32", Kernel32Ext.class);
        boolean SetConsoleTextAttribute(WinNT.HANDLE hConsoleOutput, short wAttributes);
        boolean ReadConsoleInputA(WinNT.HANDLE hConsoleInput, com.sun.jna.platform.win32.Wincon.INPUT_RECORD[] lpBuffer, int nLength, IntByReference lpNumberOfEventsRead);
        boolean SetConsoleCursorPosition(WinNT.HANDLE hConsoleOutput, com.sun.jna.platform.win32.Wincon.COORD dwCursorPosition);
        boolean AllocConsole();
        boolean FreeConsole();
        com.sun.jna.platform.win32.WinDef.HWND GetConsoleWindow();
        boolean SetConsoleCtrlHandler(com.sun.jna.Callback handler, boolean add);
        boolean SetConsoleWindowInfo(WinNT.HANDLE hConsoleOutput, boolean bAbsolute, com.sun.jna.platform.win32.Wincon.SMALL_RECT lpConsoleWindow);
        boolean SetConsoleMode(WinNT.HANDLE hConsoleHandle, int dwMode);
        boolean GetConsoleMode(WinNT.HANDLE hConsoleHandle, com.sun.jna.platform.win32.WinDef.DWORDByReference lpMode);
        boolean SetConsoleTitleW(com.sun.jna.WString lpConsoleTitle);
    }

    // 控制台控制事件类型
    private static final int CTRL_CLOSE_EVENT = 2;

    // User32接口（用于窗口操作）
    private interface User32Ext extends com.sun.jna.platform.win32.User32 {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);
        boolean ShowWindow(com.sun.jna.platform.win32.WinDef.HWND hWnd, int nCmdShow);
        boolean SetForegroundWindow(com.sun.jna.platform.win32.WinDef.HWND hWnd);
    }

    private static final int SW_HIDE = 0;
    private static final int SW_SHOW = 5;

    private final WinNT.HANDLE hConsoleInput;
    private final WinNT.HANDLE hConsoleOutput;
    private boolean consoleCreated;
    private boolean closed;
    private static boolean consoleAllocated = false;
    private com.sun.jna.platform.win32.WinDef.HWND consoleWindowHandle = null;
    private static com.sun.jna.Callback consoleCtrlHandler = null;
    private static boolean ignoreCloseEvent = false;

    public WindowsConsoleProvider() {
        this.consoleCreated = false;
        this.closed = false;

        setupConsoleCtrlHandler();

        if (!consoleAllocated) {
            this.hConsoleInput = createConsole();
            consoleAllocated = true;
        } else {
            this.hConsoleInput = Kernel32.INSTANCE.GetStdHandle(STD_INPUT_HANDLE);
        }

        this.hConsoleOutput = Kernel32.INSTANCE.GetStdHandle(STD_OUTPUT_HANDLE);
    }

    private static void setupConsoleCtrlHandler() {
        if (consoleCtrlHandler == null) {
            consoleCtrlHandler = new com.sun.jna.Callback() {
                @SuppressWarnings("unused")
                public boolean callback(int ctrlType) {
                    if (ctrlType == CTRL_CLOSE_EVENT && ignoreCloseEvent) {
                        return true;
                    }
                    return false;
                }
            };
            Kernel32Ext.INSTANCE.SetConsoleCtrlHandler(consoleCtrlHandler, true);
        }
    }

    private WinNT.HANDLE createConsole() {
        if (!Kernel32Ext.INSTANCE.AllocConsole()) {
            consoleAllocated = true;
            consoleWindowHandle = Kernel32Ext.INSTANCE.GetConsoleWindow();
            disableQuickEditMode();
            return Kernel32.INSTANCE.GetStdHandle(STD_INPUT_HANDLE);
        }
        consoleAllocated = true;
        consoleCreated = true;
        consoleWindowHandle = Kernel32Ext.INSTANCE.GetConsoleWindow();
        redirectOutput();
        disableQuickEditMode();
        return Kernel32.INSTANCE.GetStdHandle(STD_INPUT_HANDLE);
    }

    private void disableQuickEditMode() {
        try {
            WinNT.HANDLE hIn = Kernel32.INSTANCE.GetStdHandle(STD_INPUT_HANDLE);
            com.sun.jna.platform.win32.WinDef.DWORDByReference modeRef = new com.sun.jna.platform.win32.WinDef.DWORDByReference();
            if (Kernel32Ext.INSTANCE.GetConsoleMode(hIn, modeRef)) {
                int mode = modeRef.getValue().intValue();
                // 禁用快速编辑模式
                mode = mode & ~ENABLE_QUICK_EDIT_MODE;
                // 确保扩展标志启用
                mode = mode | ENABLE_EXTENDED_FLAGS;
                Kernel32Ext.INSTANCE.SetConsoleMode(hIn, mode);
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    private void redirectOutput() {
        try {
            System.setIn(new java.io.FileInputStream("CONIN$"));
        } catch (Exception e) {
            // 忽略错误
        }
    }

    private void writeToConsole(String text) {
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream("CONOUT$", true);
            fos.write(text.getBytes("GBK"));
            fos.flush();
            fos.close();
        } catch (Exception e) {
            System.out.print(text);
        }
    }

    private void ensureConsoleOpen() {
        if (closed) {
            closed = false;
            clear();
        }
    }

    @Override
    public void print(String text) {
        ensureConsoleOpen();
        writeToConsole(text);
    }

    @Override
    public void println(String text) {
        ensureConsoleOpen();
        writeToConsole(text + "\n");
    }

    @Override
    public void clear() {
        ensureConsoleOpen();
        try {
            com.sun.jna.platform.win32.WinDef.DWORDByReference mode = new com.sun.jna.platform.win32.WinDef.DWORDByReference();
            if (Kernel32Ext.INSTANCE.GetConsoleMode(hConsoleOutput, mode)) {
                int currentMode = mode.getValue().intValue();
                int newMode = currentMode | ENABLE_VIRTUAL_TERMINAL_PROCESSING;
                Kernel32Ext.INSTANCE.SetConsoleMode(hConsoleOutput, newMode);
            }
            writeToConsole("\033[2J\033[H\033[3J");

            com.sun.jna.platform.win32.Wincon.CONSOLE_SCREEN_BUFFER_INFO csbi = new com.sun.jna.platform.win32.Wincon.CONSOLE_SCREEN_BUFFER_INFO();
            Kernel32.INSTANCE.GetConsoleScreenBufferInfo(hConsoleOutput, csbi);

            com.sun.jna.platform.win32.Wincon.SMALL_RECT window = new com.sun.jna.platform.win32.Wincon.SMALL_RECT();
            window.Top = 0;
            window.Left = 0;
            window.Bottom = (short)(csbi.srWindow.Bottom - csbi.srWindow.Top);
            window.Right = (short)(csbi.srWindow.Right - csbi.srWindow.Left);
            Kernel32Ext.INSTANCE.SetConsoleWindowInfo(hConsoleOutput, true, window);
        } catch (Exception e) {
            for (int i = 0; i < 200; i++) {
                println("");
            }
        }
    }

    @Override
    public void setTitle(String title) {
        Kernel32Ext.INSTANCE.SetConsoleTitleW(new com.sun.jna.WString(title));
    }

    @Override
    public void clearLine() {
        ensureConsoleOpen();
        print("\r" + " ".repeat(80) + "\r");
    }

    @Override
    public void setColor(ConsoleColor color) {
        ensureConsoleOpen();
        WinNT.HANDLE hOut = Kernel32.INSTANCE.GetStdHandle(STD_OUTPUT_HANDLE);
        Kernel32Ext.INSTANCE.SetConsoleTextAttribute(hOut, color.toWindowsForeground());
    }

    @Override
    public void setBackgroundColor(ConsoleColor color) {
        ensureConsoleOpen();
        WinNT.HANDLE hOut = Kernel32.INSTANCE.GetStdHandle(STD_OUTPUT_HANDLE);
        Kernel32Ext.INSTANCE.SetConsoleTextAttribute(hOut, color.toWindowsBackground());
    }

    @Override
    public void resetColor() {
        ensureConsoleOpen();
        WinNT.HANDLE hOut = Kernel32.INSTANCE.GetStdHandle(STD_OUTPUT_HANDLE);
        short defaultAttr = (short)(0x0004 | 0x0002 | 0x0001); // FOREGROUND_RED | GREEN | BLUE
        Kernel32Ext.INSTANCE.SetConsoleTextAttribute(hOut, defaultAttr);
    }

    @Override
    public void printColor(String text, ConsoleColor color) {
        setColor(color);
        print(text);
        resetColor();
    }

    @Override
    public void printBackground(String text, ConsoleColor color) {
        setBackgroundColor(color);
        print(text);
        resetColor();
    }

    @Override
    public String selectOption(List<String> options, String title) {
        ensureConsoleOpen();

        if (title != null && !title.isEmpty()) {
            println(title);
        }

        WinNT.HANDLE hIn = Kernel32.INSTANCE.GetStdHandle(STD_INPUT_HANDLE);
        WinNT.HANDLE hOut = Kernel32.INSTANCE.GetStdHandle(STD_OUTPUT_HANDLE);

        com.sun.jna.platform.win32.Wincon.CONSOLE_SCREEN_BUFFER_INFO csbi = new com.sun.jna.platform.win32.Wincon.CONSOLE_SCREEN_BUFFER_INFO();
        Kernel32.INSTANCE.GetConsoleScreenBufferInfo(hOut, csbi);
        com.sun.jna.platform.win32.Wincon.COORD cursorPos = csbi.dwCursorPosition;

        short defaultAttr = (short)(0x0004 | 0x0002 | 0x0001);
        short highlightAttr = (short)(0x0040 | 0x0020 | 0x0010);

        int selectedIndex = 0;

        try {
            while (true) {
                displayOptions(options, selectedIndex, cursorPos, defaultAttr, highlightAttr, hOut);

                com.sun.jna.platform.win32.Wincon.INPUT_RECORD[] events = new com.sun.jna.platform.win32.Wincon.INPUT_RECORD[1];
                IntByReference eventsRead = new IntByReference();

                if (!Kernel32Ext.INSTANCE.ReadConsoleInputA(hIn, events, 1, eventsRead)) {
                    println("\n读取输入失败");
                    return null;
                }

                com.sun.jna.platform.win32.Wincon.INPUT_RECORD record = events[0];

                if (record.EventType == KEY_EVENT && record.Event.KeyEvent.bKeyDown) {
                    short vkCode = record.Event.KeyEvent.wVirtualKeyCode;

                    if (vkCode == VK_RETURN) {
                        clearLine();
                        return options.get(selectedIndex);
                    } else if (vkCode == VK_ESCAPE) {
                        clearLine();
                        return null;
                    } else if (vkCode == VK_LEFT || vkCode == VK_A || vkCode == 0x61) {
                        selectedIndex = Math.max(0, selectedIndex - 1);
                    } else if (vkCode == VK_RIGHT || vkCode == VK_D || vkCode == 0x64) {
                        selectedIndex = Math.min(options.size() - 1, selectedIndex + 1);
                    }
                }
            }
        } catch (Exception e) {
            println("\n错误: " + e.getMessage());
            return null;
        }
    }

    private void displayOptions(List<String> options, int selectedIndex,
                                com.sun.jna.platform.win32.Wincon.COORD cursorPos,
                                short defaultAttr, short highlightAttr,
                                WinNT.HANDLE hOut) {
        Kernel32Ext.INSTANCE.SetConsoleCursorPosition(hOut, cursorPos);
        print("\r" + " ".repeat(80));
        print("\r");
        Kernel32Ext.INSTANCE.SetConsoleCursorPosition(hOut, cursorPos);

        print("[ ");
        for (int i = 0; i < options.size(); i++) {
            if (i == selectedIndex) {
                Kernel32Ext.INSTANCE.SetConsoleTextAttribute(hOut, highlightAttr);
                print("[" + options.get(i) + "]");
                Kernel32Ext.INSTANCE.SetConsoleTextAttribute(hOut, defaultAttr);
            } else {
                print(options.get(i));
            }
            if (i < options.size() - 1) {
                print(" | ");
            }
        }
        print(" ]");
    }

    @Override
    public int readKey() {
        ensureConsoleOpen();
        WinNT.HANDLE hIn = Kernel32.INSTANCE.GetStdHandle(STD_INPUT_HANDLE);

        com.sun.jna.platform.win32.Wincon.INPUT_RECORD[] events = new com.sun.jna.platform.win32.Wincon.INPUT_RECORD[1];
        IntByReference eventsRead = new IntByReference();

        if (!Kernel32Ext.INSTANCE.ReadConsoleInputA(hIn, events, 1, eventsRead)) {
            return -1;
        }

        com.sun.jna.platform.win32.Wincon.INPUT_RECORD record = events[0];
        if (record.EventType == KEY_EVENT && record.Event.KeyEvent.bKeyDown) {
            return record.Event.KeyEvent.wVirtualKeyCode;
        }
        return -1;
    }

    @Override
    public int waitForKeypress() {
        while (true) {
            int key = readKey();
            if (key != -1) {
                return key;
            }
        }
    }

    @Override
    public ProgressBar createProgressBar() {
        return new ProgressBar(this);
    }

    @Override
    public void exit() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void reopen() {
        if (closed) {
            closed = false;
            clear();
        }
    }

    @Override
    public void hideWindow() {
        if (consoleWindowHandle != null) {
            User32Ext.INSTANCE.ShowWindow(consoleWindowHandle, SW_HIDE);
        }
    }

    @Override
    public void showWindow() {
        if (consoleWindowHandle != null) {
            User32Ext.INSTANCE.ShowWindow(consoleWindowHandle, SW_SHOW);
            User32Ext.INSTANCE.SetForegroundWindow(consoleWindowHandle);
        }
    }

    @Override
    public void closeWindow() {
        if (consoleAllocated) {
            hideWindow();
            Kernel32Ext.INSTANCE.FreeConsole();
            consoleAllocated = false;
            consoleCreated = false;
            closed = true;
            consoleWindowHandle = null;
        }
    }

    @Override
    public void closeWindowSafe() {
        closeWindow();
    }

    @Override
    public void recreateWindow() {
        if (!consoleAllocated) {
            createConsole();
            closed = false;
        }
    }

    @Override
    public void keepAlive() {
        println("\n按回车键退出...");
        try {
            int key;
            do {
                key = readKey();
            } while (key != 0x0D);
        } catch (Exception e) {
            // 忽略异常
        }
    }
}
