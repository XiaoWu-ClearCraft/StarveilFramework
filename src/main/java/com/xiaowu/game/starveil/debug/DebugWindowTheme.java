package com.xiaowu.game.starveil.debug;

import com.xiaowu.game.starveil.platform.common.SystemDetector;
import com.xiaowu.game.starveil.platform.windows.WindowsDarkModeUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DebugWindow 主题管理器。
 *
 * <p>支持亮色 / 暗色切换，可跟随系统主题并动态监听系统主题变化。
 *
 * <p>暗色覆盖分三层，缺一层就会出现「部分元素不暗色 / 暗色异常」：
 * <ol>
 *   <li><b>System color 键</b>（{@code control} / {@code info} / {@code text} …）——
 *       外观（LookAndFeel）自身靠这些键推导大量默认值，不设它们，
 *       滚动条、箭头、边框线一类「由 L&F 画出来」的元素还是亮的；</li>
 *   <li><b>各组件键</b>（{@code Table.*} / {@code List.*} / {@code Menu.*} …）——
 *       表格、列表、菜单、提示框这些没被显式设过的组件会各自用回默认亮色；</li>
 *   <li><b>代码里硬编码的颜色</b>（{@code Color.GRAY} 之类的边框）——
 *       {@code updateUI()} 完全救不了它们，只能在代码里改成用本类的取色方法，
 *       并在主题变化时重建。</li>
 * </ol>
 *
 * <p>第三层由 {@link #addThemeChangeListener} 支撑：会缓存颜色的边框
 * （{@code TitledBorder}、{@code LineBorder}）必须在切换时重建，
 * 因此本类在 {@link #applyTheme} 末尾通知监听者。
 */
public final class DebugWindowTheme {

    private DebugWindowTheme() {}

    // 暗色主题颜色
    private static final Color DARK_BG = new Color(0x1E, 0x1E, 0x1E);
    private static final Color DARK_BG_LIGHTER = new Color(0x2D, 0x2D, 0x2D);
    private static final Color DARK_FG = new Color(0xCC, 0xCC, 0xCC);
    private static final Color DARK_ACCENT = new Color(0x60, 0xA0, 0xE0);
    private static final Color DARK_BORDER = new Color(0x40, 0x40, 0x40);
    private static final Color DARK_SELECTION = new Color(0x40, 0x60, 0x90);

    // 亮色主题颜色
    private static final Color LIGHT_BG = new Color(0xF5, 0xF5, 0xF5);
    private static final Color LIGHT_BG_LIGHTER = Color.WHITE;
    private static final Color LIGHT_FG = new Color(0x33, 0x33, 0x33);
    private static final Color LIGHT_ACCENT = new Color(0x00, 0x60, 0xC0);
    private static final Color LIGHT_BORDER = new Color(0xC0, 0xC0, 0xC0);
    private static final Color LIGHT_SELECTION = new Color(0xB0, 0xD0, 0xF0);

    private static boolean isDark = true;
    private static boolean followSystem = true;
    private static boolean watcherRegistered = false;

    /** 主题变化监听者（用于重建会缓存颜色的边框）。 */
    private static final List<Runnable> themeListeners = new CopyOnWriteArrayList<>();

    // ==================== 取色（供代码使用，替代硬编码颜色） ====================

    public static Color backgroundColor() { return isDark ? DARK_BG : LIGHT_BG; }

    public static Color lighterBackgroundColor() { return isDark ? DARK_BG_LIGHTER : LIGHT_BG_LIGHTER; }

    public static Color foregroundColor() { return isDark ? DARK_FG : LIGHT_FG; }

    public static Color accentColor() { return isDark ? DARK_ACCENT : LIGHT_ACCENT; }

    public static Color borderColor() { return isDark ? DARK_BORDER : LIGHT_BORDER; }

    public static Color selectionColor() { return isDark ? DARK_SELECTION : LIGHT_SELECTION; }

    /**
     * 次要文字色（时间戳、计数一类）。
     *
     * <p>不做成半透明：Swing 的 {@code Color} 带 alpha 时很多 L&F 会画成黑块，
     * 直接给一个更暗/更亮的具体色更稳。
     */
    public static Color secondaryForegroundColor() {
        return isDark ? new Color(0x8A, 0x8A, 0x8A) : new Color(0x70, 0x70, 0x70);
    }

    // ==================== 边框工厂 ====================

    /** 跟随主题的细线边框。 */
    public static Border lineBorder() {
        return BorderFactory.createLineBorder(borderColor());
    }

    /** 跟随主题的带标题边框（{@code TitledBorder} 会缓存颜色，必须重建）。 */
    public static TitledBorder titledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(lineBorder(), title);
        border.setTitleColor(foregroundColor());
        return border;
    }

    // ==================== 主题变化通知 ====================

    public static void addThemeChangeListener(Runnable listener) {
        if (listener != null) {
            themeListeners.add(listener);
        }
    }

    public static void removeThemeChangeListener(Runnable listener) {
        themeListeners.remove(listener);
    }

    private static void fireThemeChanged() {
        for (Runnable listener : themeListeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
                // 单个监听者出错不应中断其它监听者
            }
        }
    }

    /**
     * 初始化：检测系统主题并注册监听器
     */
    public static synchronized void init() {
        if (watcherRegistered) return;
        watcherRegistered = true;

        // 检测系统主题
        applyFromSystem();

        // 注册系统主题变化监听
        if (SystemDetector.isWindows()) {
            WindowsDarkModeUtil.addThemeChangeListener((isDarkMode) -> {
                if (followSystem) {
                    SwingUtilities.invokeLater(() -> {
                        applyTheme(isDarkMode);
                        updateAllWindowsTitleBar(isDarkMode);
                    });
                }
            });
        }
    }

    /**
     * 应用主题到整个Swing应用
     */
    public static void applyTheme(boolean dark) {
        isDark = dark;

        Color bg = dark ? DARK_BG : LIGHT_BG;
        Color bgLighter = dark ? DARK_BG_LIGHTER : LIGHT_BG_LIGHTER;
        Color fg = dark ? DARK_FG : LIGHT_FG;
        Color accent = dark ? DARK_ACCENT : LIGHT_ACCENT;
        Color border = dark ? DARK_BORDER : LIGHT_BORDER;
        Color selection = dark ? DARK_SELECTION : LIGHT_SELECTION;
        Color disabledFg = dark ? new Color(0x6A, 0x6A, 0x6A) : new Color(0x9A, 0x9A, 0x9A);

        // ---------- 第 1 层：System color 键 ----------
        // 外观自身用这些键推导滚动条、箭头、三维边框等「画出来」的元素，
        // 只设组件键的话它们仍然是亮色的。
        putSystemColors(bg, bgLighter, fg, accent, border, selection);

        // ---------- 第 2.5 层：换掉「自己画背景」的 UI 委托 ----------
        // Windows L&F 的按钮 / 滚动条 / 下拉框用系统主题自行绘制，
        // 完全忽略 Button.background、ScrollBar.thumb 这类键 ——
        // 表现就是「文字颜色变了，底色没变」。改用 Basic 委托后，
        // 它们会老实读取组件自身的 background。
        UIManager.put("ButtonUI", "javax.swing.plaf.basic.BasicButtonUI");
        UIManager.put("ToggleButtonUI", "javax.swing.plaf.basic.BasicToggleButtonUI");
        UIManager.put("ScrollBarUI", "javax.swing.plaf.basic.BasicScrollBarUI");
        UIManager.put("ComboBoxUI", "javax.swing.plaf.basic.BasicComboBoxUI");

        // ---------- 第 3 层：组件键 ----------
        UIManager.put("Panel.background", new ColorUIResource(bg));
        UIManager.put("Panel.foreground", new ColorUIResource(fg));
        UIManager.put("Label.background", new ColorUIResource(bg));
        UIManager.put("Label.foreground", new ColorUIResource(fg));
        UIManager.put("Label.disabledForeground", new ColorUIResource(disabledFg));
        UIManager.put("Label.disabledShadow", new ColorUIResource(bg));

        putTextComponentKeys("TextArea", bgLighter, fg, selection, border);
        putTextComponentKeys("TextField", bgLighter, fg, selection, border);
        putTextComponentKeys("TextPane", bgLighter, fg, selection, border);
        putTextComponentKeys("EditorPane", bgLighter, fg, selection, border);
        putTextComponentKeys("FormattedTextField", bgLighter, fg, selection, border);
        putTextComponentKeys("PasswordField", bgLighter, fg, selection, border);

        UIManager.put("Button.background", new ColorUIResource(bgLighter));
        UIManager.put("Button.foreground", new ColorUIResource(fg));
        UIManager.put("Button.select", new ColorUIResource(selection));
        UIManager.put("Button.disabledText", new ColorUIResource(disabledFg));
        UIManager.put("Button.border", BorderFactory.createLineBorder(border, 1));
        UIManager.put("ToggleButton.background", new ColorUIResource(bgLighter));
        UIManager.put("ToggleButton.foreground", new ColorUIResource(fg));
        UIManager.put("ToggleButton.select", new ColorUIResource(selection));
        UIManager.put("CheckBox.background", new ColorUIResource(bg));
        UIManager.put("CheckBox.foreground", new ColorUIResource(fg));
        UIManager.put("CheckBox.disabledText", new ColorUIResource(disabledFg));
        UIManager.put("RadioButton.background", new ColorUIResource(bg));
        UIManager.put("RadioButton.foreground", new ColorUIResource(fg));
        UIManager.put("RadioButton.disabledText", new ColorUIResource(disabledFg));

        // 滚动条：由 L&F 自绘，光靠组件键往往改不动
        UIManager.put("ScrollBar.background", new ColorUIResource(bg));
        UIManager.put("ScrollBar.foreground", new ColorUIResource(fg));
        UIManager.put("ScrollBar.track", new ColorUIResource(bg));
        UIManager.put("ScrollBar.trackHighlight", new ColorUIResource(bg));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(dark
                ? new Color(0x50, 0x50, 0x50) : new Color(0xB0, 0xB0, 0xB0)));
        UIManager.put("ScrollBar.thumbDarkShadow", new ColorUIResource(border));
        UIManager.put("ScrollBar.thumbHighlight", new ColorUIResource(bgLighter));
        UIManager.put("ScrollBar.thumbShadow", new ColorUIResource(border));
        UIManager.put("ScrollBar.arrowButtonBackground", new ColorUIResource(bgLighter));

        UIManager.put("ScrollPane.background", new ColorUIResource(bg));
        UIManager.put("ScrollPane.foreground", new ColorUIResource(fg));
        UIManager.put("ScrollPane.viewport.background", new ColorUIResource(bgLighter));
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(border, 1));
        UIManager.put("Viewport.background", new ColorUIResource(bgLighter));
        UIManager.put("Viewport.foreground", new ColorUIResource(fg));

        // 表格（调试窗口里最容易「漏白」的一块）
        UIManager.put("Table.background", new ColorUIResource(bgLighter));
        UIManager.put("Table.foreground", new ColorUIResource(fg));
        UIManager.put("Table.selectionBackground", new ColorUIResource(selection));
        UIManager.put("Table.selectionForeground", new ColorUIResource(fg));
        UIManager.put("Table.gridColor", new ColorUIResource(border));
        UIManager.put("Table.focusCellBackground", new ColorUIResource(bgLighter));
        UIManager.put("Table.focusCellForeground", new ColorUIResource(fg));
        UIManager.put("TableHeader.background", new ColorUIResource(bg));
        UIManager.put("TableHeader.foreground", new ColorUIResource(fg));
        UIManager.put("TableHeader.cellBorder", BorderFactory.createLineBorder(border, 1));

        // 列表
        UIManager.put("List.background", new ColorUIResource(bgLighter));
        UIManager.put("List.foreground", new ColorUIResource(fg));
        UIManager.put("List.selectionBackground", new ColorUIResource(selection));
        UIManager.put("List.selectionForeground", new ColorUIResource(fg));
        UIManager.put("List.dropLineColor", new ColorUIResource(border));
        UIManager.put("List.border", BorderFactory.createLineBorder(border, 1));

        // 菜单 / 弹出菜单
        UIManager.put("MenuBar.background", new ColorUIResource(bg));
        UIManager.put("MenuBar.foreground", new ColorUIResource(fg));
        UIManager.put("MenuBar.border", BorderFactory.createLineBorder(border, 1));
        UIManager.put("Menu.background", new ColorUIResource(bg));
        UIManager.put("Menu.foreground", new ColorUIResource(fg));
        UIManager.put("Menu.selectionBackground", new ColorUIResource(selection));
        UIManager.put("Menu.selectionForeground", new ColorUIResource(fg));
        UIManager.put("MenuItem.background", new ColorUIResource(bg));
        UIManager.put("MenuItem.foreground", new ColorUIResource(fg));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(selection));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(fg));
        UIManager.put("MenuItem.disabledForeground", new ColorUIResource(disabledFg));
        UIManager.put("PopupMenu.background", new ColorUIResource(bg));
        UIManager.put("PopupMenu.foreground", new ColorUIResource(fg));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("TabbedPane.background", new ColorUIResource(bg));
        UIManager.put("TabbedPane.foreground", new ColorUIResource(fg));
        UIManager.put("TabbedPane.contentBackground", new ColorUIResource(bg));
        UIManager.put("TabbedPane.shadow", new ColorUIResource(border));
        UIManager.put("TabbedPane.darkShadow", new ColorUIResource(border));
        UIManager.put("TabbedPane.light", new ColorUIResource(border));
        UIManager.put("TabbedPane.highlight", new ColorUIResource(border));
        UIManager.put("TabbedPane.tabAreaBackground", new ColorUIResource(bgLighter));
        UIManager.put("TabbedPane.selected", new ColorUIResource(bgLighter));
        UIManager.put("TabbedPane.unselectedBackground", new ColorUIResource(bg));
        UIManager.put("TabbedPane.focus", new ColorUIResource(accent));
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(1, 1, 1, 1));

        UIManager.put("Tree.background", new ColorUIResource(bgLighter));
        UIManager.put("Tree.foreground", new ColorUIResource(fg));
        UIManager.put("Tree.selectionBackground", new ColorUIResource(selection));
        UIManager.put("Tree.selectionForeground", new ColorUIResource(fg));
        UIManager.put("Tree.selectionBorderColor", new ColorUIResource(accent));
        UIManager.put("Tree.textBackground", new ColorUIResource(bgLighter));
        UIManager.put("Tree.textForeground", new ColorUIResource(fg));
        UIManager.put("Tree.hash", new ColorUIResource(border));
        UIManager.put("Tree.line", new ColorUIResource(border));
        UIManager.put("Tree.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("ComboBox.background", new ColorUIResource(bgLighter));
        UIManager.put("ComboBox.foreground", new ColorUIResource(fg));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(selection));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(fg));
        UIManager.put("ComboBox.buttonBackground", new ColorUIResource(bgLighter));
        UIManager.put("ComboBox.disabledBackground", new ColorUIResource(bg));
        UIManager.put("ComboBox.disabledForeground", new ColorUIResource(disabledFg));
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("Spinner.background", new ColorUIResource(bgLighter));
        UIManager.put("Spinner.foreground", new ColorUIResource(fg));
        UIManager.put("Spinner.editorBackground", new ColorUIResource(bgLighter));
        UIManager.put("Spinner.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("ProgressBar.background", new ColorUIResource(bgLighter));
        UIManager.put("ProgressBar.foreground", new ColorUIResource(accent));
        UIManager.put("ProgressBar.selectionBackground", new ColorUIResource(fg));
        UIManager.put("ProgressBar.selectionForeground", new ColorUIResource(bg));

        UIManager.put("Slider.background", new ColorUIResource(bg));
        UIManager.put("Slider.foreground", new ColorUIResource(fg));

        UIManager.put("ToolBar.background", new ColorUIResource(bg));
        UIManager.put("ToolBar.foreground", new ColorUIResource(fg));
        UIManager.put("ToolBar.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("ToolTip.background", new ColorUIResource(dark
                ? new Color(0x3A, 0x3A, 0x3A) : new Color(0xFF, 0xFF, 0xE0)));
        UIManager.put("ToolTip.foreground", new ColorUIResource(fg));
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("Separator.background", new ColorUIResource(border));
        UIManager.put("Separator.foreground", new ColorUIResource(border));
        UIManager.put("Separator.highlight", new ColorUIResource(border));
        UIManager.put("Separator.shadow", new ColorUIResource(border));

        UIManager.put("SplitPane.background", new ColorUIResource(bg));
        UIManager.put("SplitPane.foreground", new ColorUIResource(fg));
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("SplitPane.highlight", new ColorUIResource(border));
        UIManager.put("SplitPane.shadow", new ColorUIResource(border));
        UIManager.put("SplitPane.darkShadow", new ColorUIResource(border));
        UIManager.put("SplitPaneDivider.draggingColor", new ColorUIResource(accent));
        UIManager.put("SplitPaneDivider.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("OptionPane.background", new ColorUIResource(bg));
        UIManager.put("OptionPane.foreground", new ColorUIResource(fg));
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(fg));
        UIManager.put("OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(6, 6, 6, 6));

        UIManager.put("TitledBorder.titleColor", new ColorUIResource(fg));
        UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(border, 1));

        UIManager.put("RootPane.background", new ColorUIResource(bg));
        UIManager.put("RootPane.foreground", new ColorUIResource(fg));

        // 更新所有已存在的窗口
        for (Window window : Window.getWindows()) {
            updateComponentColors(window);
        }

        // 通知需要重建缓存颜色的使用方（TitledBorder / LineBorder 不会自动跟随）
        fireThemeChanged();

        // 窗口重绘
        for (Window window : Window.getWindows()) {
            window.repaint();
        }
    }

    /** System color 键：外观靠它们推导大量默认绘制。 */
    private static void putSystemColors(Color bg, Color bgLighter, Color fg,
                                        Color accent, Color border, Color selection) {
        UIManager.put("control", new ColorUIResource(bgLighter));
        UIManager.put("controlHighlight", new ColorUIResource(border));
        UIManager.put("controlLHighlight", new ColorUIResource(border));
        UIManager.put("controlShadow", new ColorUIResource(border));
        UIManager.put("controlDkShadow", new ColorUIResource(border));
        UIManager.put("info", new ColorUIResource(bgLighter));
        UIManager.put("infoText", new ColorUIResource(fg));
        UIManager.put("text", new ColorUIResource(bgLighter));
        UIManager.put("textText", new ColorUIResource(fg));
        UIManager.put("textHighlight", new ColorUIResource(selection));
        UIManager.put("textHighlightText", new ColorUIResource(fg));
        UIManager.put("textInactiveText", new ColorUIResource(fg));
        UIManager.put("scrollbar", new ColorUIResource(bg));
        UIManager.put("window", new ColorUIResource(bg));
        UIManager.put("windowBorder", new ColorUIResource(border));
        UIManager.put("windowText", new ColorUIResource(fg));
        UIManager.put("menu", new ColorUIResource(bg));
        UIManager.put("menuText", new ColorUIResource(fg));
        UIManager.put("menuSelected", new ColorUIResource(selection));
        UIManager.put("menuSelectedText", new ColorUIResource(fg));
        UIManager.put("activeCaption", new ColorUIResource(bg));
        UIManager.put("activeCaptionText", new ColorUIResource(fg));
        UIManager.put("activeCaptionBorder", new ColorUIResource(border));
        UIManager.put("inactiveCaption", new ColorUIResource(bg));
        UIManager.put("inactiveCaptionText", new ColorUIResource(fg));
        UIManager.put("inactiveCaptionBorder", new ColorUIResource(border));
        UIManager.put("nimbusBase", new ColorUIResource(bgLighter));
        UIManager.put("nimbusBlueGrey", new ColorUIResource(bg));
        UIManager.put("nimbusLightBackground", new ColorUIResource(bgLighter));
        UIManager.put("nimbusSelectionBackground", new ColorUIResource(selection));
        UIManager.put("nimbusFocus", new ColorUIResource(accent));
        UIManager.put("nimbusBorder", new ColorUIResource(border));
    }

    /** 文本类组件的一组通用键（缺一个就会出现「输入框是白的」）。 */
    private static void putTextComponentKeys(String prefix, Color background, Color foreground,
                                             Color selection, Color border) {
        UIManager.put(prefix + ".background", new ColorUIResource(background));
        UIManager.put(prefix + ".foreground", new ColorUIResource(foreground));
        UIManager.put(prefix + ".caretColor", new ColorUIResource(foreground));
        UIManager.put(prefix + ".selectionBackground", new ColorUIResource(selection));
        UIManager.put(prefix + ".selectionForeground", new ColorUIResource(foreground));
        UIManager.put(prefix + ".inactiveForeground", new ColorUIResource(foreground));
        UIManager.put(prefix + ".disabledText", new ColorUIResource(foreground));
        UIManager.put(prefix + ".border", BorderFactory.createLineBorder(border, 1));
    }

    /**
     * 更新所有窗口的标题栏颜色（包括 Swing 和 JavaFX）
     * JavaFX 窗口由 WindowsDarkModeUtil 自动处理
     */
    private static void updateAllWindowsTitleBar(boolean dark) {
        if (!SystemDetector.isWindows()) return;

        // 更新所有 Swing 窗口
        for (Window window : Window.getWindows()) {
            if (window instanceof Frame frame) {
                WindowsDarkModeUtil.enableForSwing(frame, dark);
            }
        }

        // JavaFX 窗口由 WindowsDarkModeUtil.notifyListeners 自动更新
    }

    /**
     * 把<b>当前</b>主题重新应用到指定组件子树（含自身）。
     *
     * <p>用于「组件是在主题生效之前就创建好的」场景。典型例子：
     * {@code DebugWindow} 的字段初始化（{@code new JTextArea()} 之类）先于构造函数体执行，
     * 那些组件装 UI 时读到的还是外观的亮色默认值并缓存了下来；
     * 等到界面搭完再整体刷一遍，它们才会跟上主题。
     *
     * <p>不做这一步的症状很典型：<b>初始进来部分元素是白的，得手动点两下主题按钮才全黑</b>
     * —— 第一下点掉的其实是「残留的亮色」。
     */
    public static void applyTo(Component root) {
        if (root != null) {
            updateComponentColors(root);
        }
    }

    /** 用当前主题把已存在的窗口重刷一遍。 */
    public static void reapplyCurrent() {
        applyTheme(isDark);
    }

    /**
     * 递归更新组件及其子组件的颜色。
     *
     * <p>顺序是 {@code updateUI()} → 显式写色，<b>不能反</b>：
     * {@code updateUI()} 会把组件重置回 UIManager 的当前值，
     * 先写色再 updateUI 等于白写。
     *
     * <p>{@code updateUI()} 也救不了代码里显式 {@code setBorder(...)} 过的边框 ——
     * 那类必须由使用方在主题变化时重建（见 {@link #addThemeChangeListener}）。
     */
    private static void updateComponentColors(Component comp) {
        if (comp instanceof JComponent jc) {
            jc.updateUI();
        }
        applyExplicitColors(comp);

        if (comp instanceof Container container) {
            for (Component child : container.getComponents()) {
                updateComponentColors(child);
            }
        }
        if (comp instanceof JMenu menu) {
            // JMenu 的弹出菜单不在 getComponents() 里，单独走一遍
            JPopupMenu popup = menu.getPopupMenu();
            if (popup != null) {
                updateComponentColors(popup);
            }
        }
    }

    /**
     * 把当前主题色<b>显式写进组件属性</b>。
     *
     * <p>为什么不只依赖 UIManager 键：部分外观（尤其 Windows L&F）会用自己的系统主题
     * 绘制按钮面、滚动条与下拉框，压根不读 {@code Button.background} /
     * {@code ScrollBar.thumb} —— 表现就是「文字颜色变了，底色没变」。
     * 配合上面把委托换成 {@code Basic*UI}，显式写属性是唯一在各类外观下都稳的做法。
     */
    private static void applyExplicitColors(Component comp) {
        Color bg = backgroundColor();
        Color bgLighter = lighterBackgroundColor();
        Color fg = foregroundColor();
        Color border = borderColor();
        Color selection = selectionColor();

        if (comp instanceof AbstractButton button) {
            // 只有不透明的 Basic 按钮才会用 background 填充（ComponentUI.update）
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            if (!(button instanceof JCheckBox) && !(button instanceof JRadioButton)) {
                button.setBackground(new ColorUIResource(bgLighter));
            }
            button.setForeground(new ColorUIResource(fg));
        } else if (comp instanceof JScrollBar bar) {
            bar.setOpaque(true);
            bar.setBackground(new ColorUIResource(bg));
            bar.setForeground(new ColorUIResource(fg));
        } else if (comp instanceof JTextComponent text) {
            text.setBackground(new ColorUIResource(bgLighter));
            text.setForeground(new ColorUIResource(fg));
            text.setCaretColor(new ColorUIResource(fg));
            text.setSelectionColor(new ColorUIResource(selection));
            text.setSelectedTextColor(new ColorUIResource(fg));
        } else if (comp instanceof JTable table) {
            table.setBackground(new ColorUIResource(bgLighter));
            table.setForeground(new ColorUIResource(fg));
            table.setGridColor(new ColorUIResource(border));
            table.setSelectionBackground(new ColorUIResource(selection));
            table.setSelectionForeground(new ColorUIResource(fg));
            JTableHeader header = table.getTableHeader();
            if (header != null) {
                header.setBackground(new ColorUIResource(bg));
                header.setForeground(new ColorUIResource(fg));
            }
        } else if (comp instanceof JList<?> list) {
            list.setBackground(new ColorUIResource(bgLighter));
            list.setForeground(new ColorUIResource(fg));
            list.setSelectionBackground(new ColorUIResource(selection));
            list.setSelectionForeground(new ColorUIResource(fg));
        } else if (comp instanceof JTree tree) {
            tree.setBackground(new ColorUIResource(bgLighter));
            tree.setForeground(new ColorUIResource(fg));
        } else if (comp instanceof JComboBox<?> combo) {
            combo.setBackground(new ColorUIResource(bgLighter));
            combo.setForeground(new ColorUIResource(fg));
        } else if (comp instanceof JTabbedPane tabs) {
            tabs.setBackground(new ColorUIResource(bg));
            tabs.setForeground(new ColorUIResource(fg));
        } else if (comp instanceof JScrollPane scroll) {
            scroll.setBackground(new ColorUIResource(bg));
            if (scroll.getViewport() != null) {
                scroll.getViewport().setBackground(new ColorUIResource(bgLighter));
            }
        } else if (comp instanceof JSeparator separator) {
            separator.setForeground(new ColorUIResource(border));
            separator.setBackground(new ColorUIResource(border));
        } else if (comp instanceof JLabel label) {
            label.setForeground(new ColorUIResource(fg));
        }
    }

    public static boolean isDarkTheme() { return isDark; }

    public static void toggleTheme() {
        followSystem = false;
        applyTheme(!isDark);
    }

    public static void setFollowSystem(boolean follow) {
        followSystem = follow;
        if (follow) applyFromSystem();
    }

    public static boolean isFollowSystem() { return followSystem; }

    public static void applyFromSystem() {
        applyTheme(isSystemDark());
    }

    /**
     * 检测系统主题是否为暗色。
     *
     * <p>本版本只内置 Windows 实现（UXTheme API，不读注册表）。
     *
     * <p>注入点 {@link com.xiaowu.game.starveil.platform.api.PlatformInjectPoints#SYSTEM_DARK_MODE}：
     * 其它平台的适配插件可以用 REPLACE 换成 gsettings / defaults 等检测方式。
     */
    @com.xiaowu.game.starveil.plugin.InjectPoint(
            com.xiaowu.game.starveil.platform.api.PlatformInjectPoints.SYSTEM_DARK_MODE)
    public static boolean isSystemDark() {
        if (SystemDetector.isWindows()) {
            return WindowsDarkModeUtil.isSystemDarkMode();
        }
        return true;
    }
}
