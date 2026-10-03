package com.xiaowu.game.starveil.debug;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主题设置的回归测试。
 *
 * <p>这里的核心是「UI 委托必须被换掉」这一条：Windows L&F 的按钮 / 滚动条 /
 * 下拉框会用自己的系统主题绘制，<b>完全忽略</b> {@code Button.background}、
 * {@code ScrollBar.thumb} 这类键 —— 只设键不换委托，症状就是
 * 「文字颜色变了，底色没变」。这条断言就是为了防止以后有人把委托覆盖删掉。
 *
 * <p>不需要显示环境：{@code UIManager.put} 与 {@code Window.getWindows()}
 * 在无头模式下都能正常工作（后者返回空数组）。
 */
class DebugWindowThemeTest {

    @AfterEach
    void restoreLightTheme() {
        DebugWindowTheme.applyTheme(false);
    }

    // ==================== UI 委托（本 bug 的根因） ====================

    @Test
    void backgroundPaintingDelegatesAreOverridden() {
        DebugWindowTheme.applyTheme(true);

        assertEquals("javax.swing.plaf.basic.BasicButtonUI", UIManager.get("ButtonUI"),
                "按钮必须换成 Basic 委托，否则 Windows L&F 会忽略 Button.background");
        assertEquals("javax.swing.plaf.basic.BasicToggleButtonUI",
                UIManager.get("ToggleButtonUI"));
        assertEquals("javax.swing.plaf.basic.BasicScrollBarUI", UIManager.get("ScrollBarUI"),
                "滚动条同理：Metal/Windows 的滚动条 UI 不读 ScrollBar.thumb");
        assertEquals("javax.swing.plaf.basic.BasicComboBoxUI", UIManager.get("ComboBoxUI"));
    }

    // ==================== 调色板 ====================

    @Test
    void darkThemePaintsComponentsDark() {
        DebugWindowTheme.applyTheme(true);

        assertTrue(DebugWindowTheme.isDarkTheme());
        assertEquals(DebugWindowTheme.backgroundColor(),
                UIManager.getColor("Panel.background"));
        assertEquals(DebugWindowTheme.foregroundColor(),
                UIManager.getColor("Button.foreground"));
        assertEquals(DebugWindowTheme.lighterBackgroundColor(),
                UIManager.getColor("Button.background"));
    }

    @Test
    void lightThemeIsBrighterThanDark() {
        DebugWindowTheme.applyTheme(true);
        Color darkPanel = UIManager.getColor("Panel.background");
        Color darkText = UIManager.getColor("TextArea.background");

        DebugWindowTheme.applyTheme(false);
        Color lightPanel = UIManager.getColor("Panel.background");
        Color lightText = UIManager.getColor("TextArea.background");

        assertFalse(DebugWindowTheme.isDarkTheme());
        assertTrue(luminance(lightPanel) > luminance(darkPanel),
                "亮色主题的面板必须比暗色更亮");
        assertTrue(luminance(lightText) > luminance(darkText),
                "输入区在亮色主题下也应变亮");
    }

    @Test
    void scrollBarThumbDiffersBetweenThemes() {
        DebugWindowTheme.applyTheme(true);
        Color darkThumb = UIManager.getColor("ScrollBar.thumb");

        DebugWindowTheme.applyTheme(false);
        Color lightThumb = UIManager.getColor("ScrollBar.thumb");

        assertNotNull(darkThumb);
        assertNotNull(lightThumb);
        assertNotEquals(darkThumb, lightThumb, "滚动条滑块必须跟随主题");
        assertTrue(luminance(lightThumb) > luminance(darkThumb));
    }

    @Test
    void tableAndListKeysAreThemed() {
        DebugWindowTheme.applyTheme(true);

        assertNotNull(UIManager.getColor("Table.background"));
        assertNotNull(UIManager.getColor("Table.gridColor"));
        assertNotNull(UIManager.getColor("TableHeader.background"));
        assertNotNull(UIManager.getColor("List.background"));
        assertNotNull(UIManager.getColor("Tree.background"));
        assertNotNull(UIManager.getColor("ToolTip.background"));
        assertNotNull(UIManager.getColor("Menu.background"));
        assertNotNull(UIManager.getColor("MenuItem.selectionBackground"));
    }

    /** System color 键：外观靠它们推导滚动条箭头、三维边框等「画出来」的元素。 */
    @Test
    void systemColorKeysAreSet() {
        DebugWindowTheme.applyTheme(true);

        assertNotNull(UIManager.getColor("control"));
        assertNotNull(UIManager.getColor("info"));
        assertNotNull(UIManager.getColor("text"));
        assertNotNull(UIManager.getColor("scrollbar"));
        assertNotNull(UIManager.getColor("window"));
        assertNotNull(UIManager.getColor("menu"));
    }

    @Test
    void textComponentsGetCaretAndSelection() {
        DebugWindowTheme.applyTheme(true);

        assertEquals(DebugWindowTheme.foregroundColor(), UIManager.getColor("TextArea.caretColor"));
        assertNotNull(UIManager.getColor("TextArea.selectionBackground"));
        assertNotNull(UIManager.getColor("TextField.selectionBackground"));
    }

    // ==================== 边框工厂 ====================

    @Test
    void borderFactoriesFollowTheTheme() {
        DebugWindowTheme.applyTheme(true);
        Color darkBorder = DebugWindowTheme.borderColor();
        Color darkFg = DebugWindowTheme.foregroundColor();
        assertEquals(darkFg, DebugWindowTheme.titledBorder("x").getTitleColor(),
                "标题文字色取前景色");

        DebugWindowTheme.applyTheme(false);
        assertNotEquals(darkBorder, DebugWindowTheme.borderColor());
        assertNotEquals(darkFg, DebugWindowTheme.foregroundColor());
        assertEquals(DebugWindowTheme.foregroundColor(),
                DebugWindowTheme.titledBorder("x").getTitleColor());
    }

    // ==================== 变更通知 ====================

    @Test
    void listenersAreNotifiedOnThemeChange() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;
        DebugWindowTheme.addThemeChangeListener(listener);
        try {
            DebugWindowTheme.applyTheme(true);
            assertEquals(1, calls.get());

            DebugWindowTheme.applyTheme(false);
            assertEquals(2, calls.get(), "每次换主题都要通知，否则边框不会重建");
        } finally {
            DebugWindowTheme.removeThemeChangeListener(listener);
        }

        DebugWindowTheme.applyTheme(true);
        assertEquals(2, calls.get(), "移除后不应再收到通知");
    }

    private static int luminance(Color c) {
        return c.getRed() + c.getGreen() + c.getBlue();
    }

    // ==================== 已有组件的重新着色 ====================

    /**
     * 回归：{@code applyTheme} 只遍历 {@code Window.getWindows()}，
     * 刷不到还没挂进窗口树的组件。
     *
     * <p>这正是「默认暗色时进来部分元素是白的、要点两下主题按钮才全黑」的成因：
     * {@code DebugWindow} 的字段初始化早于 {@code DebugWindowTheme.init()}，
     * 那些组件装 UI 时缓存了外观的亮色默认值。
     */
    @Test
    void applyThemeAloneCannotReachComponentsOutsideTheWindowTree() {
        DebugWindowTheme.applyTheme(false);
        JButton orphan = new JButton("x");
        Color whenCreated = orphan.getBackground();

        DebugWindowTheme.applyTheme(true);

        assertEquals(whenCreated, orphan.getBackground(),
                "不在窗口树里的组件刷不到 —— 所以必须有 applyTo 这一步兜底");
    }

    @Test
    void applyToThemesAnAlreadyCreatedComponentTree() {
        JPanel root = new JPanel();
        JButton button = new JButton("x");
        JTextArea area = new JTextArea();
        root.add(button);
        root.add(new JScrollPane(area));

        DebugWindowTheme.applyTheme(true);
        DebugWindowTheme.applyTo(root);

        assertEquals(DebugWindowTheme.lighterBackgroundColor(), button.getBackground(),
                "已创建好的按钮也必须被刷成主题色");
        assertTrue(button.isOpaque(), "不透明才会真正填充背景，否则底色不显示");
        assertEquals(DebugWindowTheme.lighterBackgroundColor(), area.getBackground());
        assertEquals(DebugWindowTheme.foregroundColor(), button.getForeground());
    }

    @Test
    void applyToCoversCommonContainers() {
        JPanel root = new JPanel();
        JTable table = new JTable(new Object[][]{{"a"}}, new Object[]{"c"});
        JList<String> list = new JList<>(new String[]{"a"});
        JTree tree = new JTree();
        JLabel label = new JLabel("x");
        root.add(table);
        root.add(list);
        root.add(tree);
        root.add(label);

        DebugWindowTheme.applyTheme(true);
        DebugWindowTheme.applyTo(root);

        assertEquals(DebugWindowTheme.lighterBackgroundColor(), table.getBackground());
        assertEquals(DebugWindowTheme.lighterBackgroundColor(), list.getBackground());
        assertEquals(DebugWindowTheme.lighterBackgroundColor(), tree.getBackground());
        assertEquals(DebugWindowTheme.foregroundColor(), label.getForeground());
    }

    @Test
    void applyToIgnoresNull() {
        DebugWindowTheme.applyTo(null);
    }

    @Test
    void reapplyCurrentKeepsTheTheme() {
        DebugWindowTheme.applyTheme(true);
        DebugWindowTheme.reapplyCurrent();
        assertTrue(DebugWindowTheme.isDarkTheme(), "重新应用不应把主题翻过去");

        DebugWindowTheme.applyTheme(false);
        DebugWindowTheme.reapplyCurrent();
        assertFalse(DebugWindowTheme.isDarkTheme());
    }
}
