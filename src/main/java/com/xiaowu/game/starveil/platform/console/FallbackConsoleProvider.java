package com.xiaowu.game.starveil.platform.console;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.platform.common.SystemDetector;
import com.xiaowu.game.starveil.platform.windows.WindowsDarkModeUtil;
import com.xiaowu.game.starveil.debug.DebugWindowTheme;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 与平台无关的模拟终端控制台（非 Windows 平台的回退实现）
 * 使用 Swing JFrame + 黑灰色文本区域模拟原生终端
 * 使用全局 KeyEventDispatcher 确保所有按键都能被捕获
 *
 * 行为规则:
 * - keepAlive() 等待回车 → 调用 exit() → 关闭窗口
 * - 手动关闭窗口 → 中断所有 console 创建的阻塞线程
 * - 如果没有其它 console 线程阻塞，程序自然退出
 */
public class FallbackConsoleProvider implements ConsoleProvider {

    // 终端配色
    private static final Color BG_COLOR = new Color(0x1E, 0x1E, 0x1E);
    private static final Color FG_COLOR = new Color(0xCC, 0xCC, 0xCC);
    private static final Color SELECTION_COLOR = new Color(0x3A, 0x3A, 0x3A);
    private static final Color HIGHLIGHT_BG = new Color(0xCC, 0xCC, 0xCC);
    private static final Color HIGHLIGHT_FG = new Color(0x1E, 0x1E, 0x1E);

    private JFrame frame;
    private JTextPane textPane;
    private StyledDocument document;
    private final BlockingQueue<Integer> keyQueue = new LinkedBlockingQueue<>();
    private volatile boolean closed = false;

    // 跟踪所有由 console 创建的阻塞线程 (如 ProgressBar 动画线程等)
    private final Set<Thread> managedThreads = Collections.newSetFromMap(new ConcurrentHashMap<>());
    // 当前等待按键的线程
    private volatile Thread waitingForKeyThread = null;

    private Color currentFgColor = FG_COLOR;
    private Color currentBgColor = BG_COLOR;

    // 全局按键分发器
    private final KeyEventDispatcher keyEventDispatcher = new KeyEventDispatcher() {
        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (e.getID() == KeyEvent.KEY_PRESSED && frame != null) {
                int code = mapKeyCode(e);
                if (code != -1) {
                    keyQueue.offer(code);
                    return true;
                }
            }
            return false;
        }
    };

    private int mapKeyCode(KeyEvent e) {
        return switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT -> 0x25;
            case KeyEvent.VK_RIGHT -> 0x27;
            case KeyEvent.VK_UP -> 0x26;
            case KeyEvent.VK_DOWN -> 0x28;
            case KeyEvent.VK_ENTER -> 0x0D;
            case KeyEvent.VK_ESCAPE -> 0x1B;
            case KeyEvent.VK_SPACE -> 0x20;
            case KeyEvent.VK_A -> 'a';
            case KeyEvent.VK_D -> 'd';
            default -> {
                char c = e.getKeyChar();
                yield (c != KeyEvent.CHAR_UNDEFINED) ? (int) c : -1;
            }
        };
    }

    public FallbackConsoleProvider() {
        // 初始化主题（检测系统主题 + 注册动态监听）
        DebugWindowTheme.init();

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
        SwingUtilities.invokeLater(this::createWindow);
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        LoggerManager.Logger("INFO", "使用模拟终端控制台 (Swing)");
    }

    private void createWindow() {
        frame = new JFrame("雾隐星阑 - 终端");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(720, 450);
        frame.setLocationRelativeTo(null);
        frame.setFocusable(true);
        frame.setFocusableWindowState(true);

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFocusable(false);
        textPane.setBackground(BG_COLOR);
        textPane.setForeground(FG_COLOR);
        textPane.setCaretColor(FG_COLOR);
        textPane.setSelectionColor(SELECTION_COLOR);
        textPane.setSelectedTextColor(FG_COLOR);
        textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textPane.setMargin(new Insets(6, 8, 6, 8));
        document = textPane.getStyledDocument();

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(BG_COLOR);
        scrollPane.getViewport().setBackground(BG_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setFocusable(false);

        frame.getContentPane().setBackground(BG_COLOR);
        frame.add(scrollPane);
        frame.setVisible(true);

        // 根据当前主题设置标题栏颜色（仅Windows支持）
        if (SystemDetector.isWindows()) {
            WindowsDarkModeUtil.enableForSwingAuto(frame);
        }

        SwingUtilities.invokeLater(() -> {
            frame.toFront();
            frame.requestFocusInWindow();
        });
    }

    /**
     * 统一关闭流程: 关闭窗口 + 中断所有管理的线程
     */
    private void shutdown() {
        if (closed) return;
        closed = true;
        LoggerManager.Logger("INFO", "[Console] 开始关闭控制台");

        // 中断等待按键的线程
        if (waitingForKeyThread != null) {
            waitingForKeyThread.interrupt();
        }

        // 中断所有管理的线程 (ProgressBar 动画等)
        for (Thread t : managedThreads) {
            if (t.isAlive()) {
                t.interrupt();
            }
        }
        managedThreads.clear();

        // 关闭窗口
        if (frame != null) {
            SwingUtilities.invokeLater(() -> {
                frame.dispose();
                LoggerManager.Logger("INFO", "[Console] 窗口已关闭");
            });
        }
    }

    // --- 文本输出 ---

    private void appendText(String text, Color fg, Color bg) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, fg);
                if (bg != null && !bg.equals(BG_COLOR)) {
                    StyleConstants.setBackground(attrs, bg);
                }
                StyleConstants.setFontFamily(attrs, Font.MONOSPACED);
                StyleConstants.setFontSize(attrs, 13);
                document.insertString(document.getLength(), text, attrs);
                textPane.setCaretPosition(document.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    @Override public void print(String text) { if (!closed) appendText(text, currentFgColor, currentBgColor); }
    @Override public void println(String text) { print(text + "\n"); }

    @Override
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            try { document.remove(0, document.getLength()); } catch (BadLocationException ignored) {}
        });
    }

    @Override
    public void setTitle(String title) {
        if (frame != null) {
            SwingUtilities.invokeLater(() -> frame.setTitle(title));
        }
    }

    @Override
    public void clearLine() {
        SwingUtilities.invokeLater(() -> {
            try {
                String content = document.getText(0, document.getLength());
                int lastNewline = content.lastIndexOf('\n');
                if (lastNewline >= 0) {
                    document.remove(lastNewline + 1, document.getLength() - lastNewline - 1);
                } else {
                    document.remove(0, document.getLength());
                }
            } catch (BadLocationException ignored) {}
        });
    }

    // --- 颜色 ---

    @Override public void setColor(ConsoleColor color) { currentFgColor = toAwtColor(color); }
    @Override public void setBackgroundColor(ConsoleColor color) { currentBgColor = toAwtColor(color); }
    @Override public void resetColor() { currentFgColor = FG_COLOR; currentBgColor = BG_COLOR; }
    @Override public void printColor(String text, ConsoleColor color) { appendText(text, toAwtColor(color), currentBgColor); }
    @Override public void printBackground(String text, ConsoleColor color) { appendText(text, currentFgColor, toAwtColor(color)); }

    private Color toAwtColor(ConsoleColor c) {
        int r = c.red ? 200 : 0;
        int g = c.green ? 200 : 0;
        int b = c.blue ? 200 : 0;
        return new Color(r, g, b);
    }

    // --- 选项选择 ---

    @Override
    public String selectOption(List<String> options, String title) {
        if (closed) return null;
        if (title != null && !title.isEmpty()) println(title);

        int selectedIndex = 0;
        while (!closed) {
            clearLine();
            appendText("[ ", FG_COLOR, BG_COLOR);
            for (int i = 0; i < options.size(); i++) {
                if (i == selectedIndex) {
                    appendText("[" + options.get(i) + "]", HIGHLIGHT_FG, HIGHLIGHT_BG);
                } else {
                    appendText(options.get(i), FG_COLOR, BG_COLOR);
                }
                if (i < options.size() - 1) appendText(" | ", FG_COLOR, BG_COLOR);
            }
            appendText(" ]", FG_COLOR, BG_COLOR);

            int key = waitForKeypress();
            if (closed) return null;
            if (key == 0x0D) { println(""); return options.get(selectedIndex); }
            if (key == 0x1B) { println(""); return null; }
            if (key == 0x25 || key == 'a' || key == 'A') selectedIndex = Math.max(0, selectedIndex - 1);
            if (key == 0x27 || key == 'd' || key == 'D') selectedIndex = Math.min(options.size() - 1, selectedIndex + 1);
        }
        return null;
    }

    // --- 按键输入 ---

    @Override
    public int readKey() {
        Integer key = keyQueue.poll();
        return key != null ? key : -1;
    }

    @Override
    public int waitForKeypress() {
        if (closed) return -1;

        waitingForKeyThread = Thread.currentThread();
        try {
            while (!closed) {
                try {
                    Integer key = keyQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (key != null) return key;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
            }
            return -1;
        } finally {
            waitingForKeyThread = null;
        }
    }

    // --- 进度条 ---

    @Override
    public ProgressBar createProgressBar() {
        return new ProgressBar(this);
    }

    /**
     * 注册一个由 console 管理的线程 (如 ProgressBar 动画)
     */
    public void registerManagedThread(Thread thread) {
        managedThreads.add(thread);
    }

    /**
     * 注销一个管理的线程
     */
    public void unregisterManagedThread(Thread thread) {
        managedThreads.remove(thread);
    }

    // --- 生命周期 ---

    /**
     * 关闭控制台: 关闭窗口 + 中断所有阻塞的 console 线程
     * 调用此方法后，keepAlive/selectOption/waitForKeypress 等会立刻返回
     */
    @Override
    public void exit() {
        shutdown();
    }

    @Override
    public boolean isClosed() { return closed; }

    @Override
    public void reopen() {
        if (closed) {
            closed = false;
            if (frame == null || !frame.isVisible()) {
                SwingUtilities.invokeLater(this::createWindow);
            }
            clear();
        }
    }

    @Override
    public void hideWindow() {
        if (frame != null) SwingUtilities.invokeLater(() -> frame.setVisible(false));
    }

    @Override
    public void showWindow() {
        if (frame != null) {
            SwingUtilities.invokeLater(() -> {
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocusInWindow();
            });
        }
    }

    @Override
    public void closeWindow() {
        shutdown();
    }

    @Override
    public void closeWindowSafe() {
        hideWindow();
        closed = true;
    }

    @Override
    public void recreateWindow() {
        if (frame == null || !frame.isDisplayable()) {
            SwingUtilities.invokeLater(this::createWindow);
            closed = false;
        }
    }

    @Override
    public void keepAlive() {
        println("\n按回车键退出...");
        while (!closed) {
            int key = waitForKeypress();
            if (key == 0x0D) {
                // 回车键 → 调用 exit() 关闭控制台
                exit();
                break;
            }
        }
    }
}
