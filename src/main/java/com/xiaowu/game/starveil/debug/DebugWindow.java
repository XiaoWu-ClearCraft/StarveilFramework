package com.xiaowu.game.starveil.debug;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.infrastructure.audio.BGMManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.FileCrypto;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.game.story.ChatHistory;
import com.xiaowu.game.starveil.game.quest.AchievementManager;
import com.xiaowu.game.starveil.game.story.DialogSequence;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.ui.screen.CreditsManager;
import com.xiaowu.game.starveil.ui.dialog.ChatManager;
import com.xiaowu.game.starveil.platform.common.SystemDetector;
import com.xiaowu.game.starveil.platform.windows.WindowsDarkModeUtil;
import com.xiaowu.game.starveil.platform.api.SystemManagerFactory;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.tree.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DebugWindow {
    private static DebugWindow INSTANCE;
    public static DebugWindow getInstance() {
        if (INSTANCE == null) INSTANCE = new DebugWindow();
        return INSTANCE;
    }

    /* -------------------- UI -------------------- */
    private final JFrame frame = new JFrame("ClearCraft DebugV2");
    private final JTabbedPane topTabs = new JTabbedPane();
    private final JTextArea logArea = new JTextArea();
    private final JScrollPane logScroll;          // 底部独立日志区

    /* 中间面板：左 JavaFX 结构树 / 右 聊天记录 */
    private final JSplitPane fxAndChatSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JTree  fxTree   = new JTree();          // 左侧 JavaFX 结构树
    private final JTextArea historyArea = new JTextArea(); // 右侧聊天记录
    private final JLabel historyCountLabel = new JLabel();
    private final JCheckBox historyAutoScroll = new JCheckBox("自动滚动", true);
    private JScrollPane historyScroll;

    /* 成就 / 配置页 */
    private final JTextArea achArea = new JTextArea();
    private final JTextArea cfgArea = new JTextArea();
    private JScrollPane achScroll;
    private JScrollPane cfgScroll;

    /* 配置页的内存覆盖编辑控件 */
    private final JComboBox<String> cfgKeyCombo = new JComboBox<>();
    private final JTextField cfgValueField = new JTextField(14);
    private int lastLogCount = 0;

    /* 滚动位置缓存 */
    private int lastLogPos = 0;

    /* 定时器 */
    private final Timer refreshTimer;

    private DebugWindow() {
        // 初始化主题（检测系统主题 + 注册动态监听）
        DebugWindowTheme.init();

        // 只展示打开之后产生的新日志（不一次性倾倒历史）
        lastLogCount = LoggerManager.getLogCount();

        /* 1. 下部日志区 */
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 180)); // 初始高度

        /* 2. 上部内容区（已含左右分割的 fxAndChatSplit） */
        buildCenterPanel(); // 内部生成 fxAndChatSplit
        topTabs.addTab("结构 & 聊天记录", fxAndChatSplit);
        topTabs.addTab("成就", createAchievementPanel());
        topTabs.addTab("配置", createConfigPanel());
        topTabs.addTab("BGM", createBGMPanel());
        topTabs.addTab("物品", createItemPanel());

        /* 3. 上下可拖拽分割 */
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topTabs, logScroll);
        mainSplit.setResizeWeight(0.75);
        mainSplit.setContinuousLayout(true);
        mainSplit.setOneTouchExpandable(true);

        /* 4. 框架组合 & 关闭动作 */
        frame.setLayout(new BorderLayout());
        frame.add(buildGlobalToolbar(), BorderLayout.NORTH);
        frame.add(mainSplit, BorderLayout.CENTER);

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                LoggerManager.close();
                System.exit(0);
            }
        });

        frame.setSize(1200, 800);
        try {
            frame.setIconImage(ImageIO.read(Objects.requireNonNull(ResourceResolver.getResourceAsStream("starveil:textures/icons/app-icon.png"))));
        } catch (Exception ignored) {
        }
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // 根据当前主题设置标题栏颜色（仅Windows支持）
        if (SystemDetector.isWindows()) {
            WindowsDarkModeUtil.enableForSwing(frame, DebugWindowTheme.isDarkTheme());
        }

        /* 5. 定时刷新 */
        refreshTimer = new Timer(500, e -> refreshAll());
        refreshTimer.start();

        /* 6. 主题切换时重建会缓存颜色的边框。
              UIManager 只管组件默认值，setBorder 过的边框不会自动跟随。 */
        applyThemedBorders();
        DebugWindowTheme.addThemeChangeListener(this::applyThemedBorders);

        /* 7. 用当前主题把整棵组件树再刷一遍。
              字段初始化（new JTextArea()、new JTree() 等）发生在构造函数体之前，
              也就是 DebugWindowTheme.init() 之前 —— 那些组件装 UI 时读到的是
              外观的亮色默认值并缓存了下来。这里界面已经搭完，统一刷一次，
              否则会出现「默认暗色时进来部分元素是白的，要点两下主题按钮才正常」。 */
        DebugWindowTheme.applyTo(frame);

        /* 8. 数据变化时才把「需要重读解密文件」的脏标记立起来。
              这样定时刷新不会每秒四次去读盘 + 解密。 */
        DataManager.addChangeListener(() -> cfgFileDirty = true);
        AchievementManager.addChangeListener(() -> achFileDirty = true);
    }

    /**
     * 重建那些<b>会缓存颜色</b>的边框与底色。
     *
     * <p>{@code updateUI()} 只会把组件重置回 UIManager 当前值：
     * {@code setBorder(new TitledBorder(...))} 这类显式设置过的边框不在其列，
     * 主题切换后必须自己重建，否则就会出现「切了暗色，边框还是亮的」。
     */
    private void applyThemedBorders() {
        SwingUtilities.invokeLater(() -> {
            logScroll.setBorder(DebugWindowTheme.lineBorder());
            fxTree.setBorder(DebugWindowTheme.lineBorder());
            historyArea.setBorder(DebugWindowTheme.lineBorder());
            historyCountLabel.setForeground(DebugWindowTheme.secondaryForegroundColor());
            if (backpackPanel != null) {
                backpackPanel.setBorder(DebugWindowTheme.titledBorder("背包"));
            }
            if (registryPanel != null) {
                registryPanel.setBorder(DebugWindowTheme.titledBorder("所有物品"));
            }
            historyCountLabel.setText(historyLabelText());
            frame.repaint();
        });
    }

    private void buildCenterPanel() {
        /* 左侧 JavaFX 树 */
        fxTree.setModel(new DefaultTreeModel(null));
        fxTree.setBorder(DebugWindowTheme.lineBorder());
        JScrollPane fxScroll = new JScrollPane(fxTree);
        fxScroll.setPreferredSize(new Dimension(500, 0));

        /* 右侧：聊天记录 */
        historyArea.setEditable(false);
        historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        historyArea.setBorder(DebugWindowTheme.lineBorder());
        historyScroll = new JScrollPane(historyArea);

        JPanel historyToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        historyCountLabel.setForeground(DebugWindowTheme.secondaryForegroundColor());
        historyCountLabel.setText(historyLabelText());
        historyToolbar.add(historyCountLabel);

        JButton clearHistoryBtn = new JButton("清空记录");
        clearHistoryBtn.setToolTipText("清空内存中的聊天记录（上限 "
                + ChatHistory.MAX_ENTRIES + " 条）");
        clearHistoryBtn.addActionListener(e -> {
            ChatHistory.getInstance().clear();
            refreshChatHistory();
        });
        historyToolbar.add(clearHistoryBtn);

        historyAutoScroll.setToolTipText("仅在视图已经停在底部时才自动跟随新记录");
        historyToolbar.add(historyAutoScroll);

        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.add(historyToolbar, BorderLayout.NORTH);
        historyPanel.add(historyScroll, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(historyPanel, BorderLayout.CENTER);

        fxAndChatSplit.setLeftComponent(fxScroll);
        fxAndChatSplit.setRightComponent(rightPanel);
        fxAndChatSplit.setDividerLocation(500);
    }

    /**
     * 顶部全局工具栏。
     *
     * <p>主题切换、致谢、扣血/回血是<b>应用级</b>操作，不属于任何标签页。
     * 原先它们挤在「结构」标签页底部，切到别的标签就找不到了。
     */
    private JPanel buildGlobalToolbar() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        // 主题切换按钮
        String themeBtnText = DebugWindowTheme.isFollowSystem() ?
            "跟随系统(" + (DebugWindowTheme.isDarkTheme() ? "暗" : "亮") + ")" :
            (DebugWindowTheme.isDarkTheme() ? "切换亮色" : "切换暗色");
        JButton themeBtn = new JButton(themeBtnText);
        themeBtn.addActionListener(e -> {
            DebugWindowTheme.toggleTheme();
            themeBtn.setText(DebugWindowTheme.isDarkTheme() ? "切换亮色" : "切换暗色");
            // 同步更新标题栏颜色（仅Windows支持）
            if (SystemDetector.isWindows()) {
                WindowsDarkModeUtil.enableForSwing(frame, DebugWindowTheme.isDarkTheme());
            }
        });
        controlPanel.add(themeBtn);

        // 分隔线
        JSeparator themeSeparator = new JSeparator(JSeparator.VERTICAL);
        themeSeparator.setPreferredSize(new Dimension(2, 25));
        controlPanel.add(themeSeparator);

        // 显示致谢按钮
        JButton creditsBtn = new JButton("显示致谢");
        creditsBtn.addActionListener(e -> showCredits());
        controlPanel.add(creditsBtn);

        // 分隔线
        JSeparator separator2 = new JSeparator(JSeparator.VERTICAL);
        separator2.setPreferredSize(new Dimension(2, 25));
        controlPanel.add(separator2);

        // 扣血按钮（自动触发受击闪烁）
        JButton damageBtn = new JButton("扣10血");
        damageBtn.addActionListener(e -> Platform.runLater(() -> {
            com.xiaowu.game.starveil.game.ecs.comp.Health h =
                com.xiaowu.game.starveil.game.state.GameInstance.getPlayerHealthComp();
            if (h != null) {
                h.takeDamage(10);
                com.xiaowu.game.starveil.ui.core.GameUI.getInstance().triggerHitFlash();
                com.xiaowu.game.starveil.ui.core.GameUI.getInstance().setHealth(h.percentage());
            }
        }));
        controlPanel.add(damageBtn);

        // 满血按钮
        JButton healBtn = new JButton("回满血");
        healBtn.addActionListener(e -> Platform.runLater(() -> {
            com.xiaowu.game.starveil.game.ecs.comp.Health h =
                com.xiaowu.game.starveil.game.state.GameInstance.getPlayerHealthComp();
            if (h != null) {
                h.reset();
                com.xiaowu.game.starveil.ui.core.GameUI.getInstance().setHealth(h.percentage());
            }
        }));
        controlPanel.add(healBtn);

        return controlPanel;
    }

    /* -------------------- BGM 调试面板 -------------------- */
    private JPanel createBGMPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 状态信息区
        JTextArea statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane statusScroll = new JScrollPane(statusArea);
        statusScroll.setPreferredSize(new Dimension(0, 100));

        // 控制按钮区
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton startBtn = new JButton("开始随机播放");
        JButton stopBtn = new JButton("停止随机播放");
        JButton pauseBtn = new JButton("暂停");
        JButton resumeBtn = new JButton("恢复");
        JButton randomBtn = new JButton("立即随机一首");

        controlPanel.add(startBtn);
        controlPanel.add(stopBtn);
        controlPanel.add(pauseBtn);
        controlPanel.add(resumeBtn);
        controlPanel.add(randomBtn);

        // 手动选择播放区
        JPanel manualPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        manualPanel.add(new JLabel("手动选择播放:"));

        JComboBox<String> bgmCombo = new JComboBox<>();
        JButton playSelectedBtn = new JButton("播放选中");

        manualPanel.add(bgmCombo);
        manualPanel.add(playSelectedBtn);

        // 间隔时间设置
        JPanel intervalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        intervalPanel.add(new JLabel("间隔时间(秒):"));

        JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 300, 5));
        JButton setIntervalBtn = new JButton("设置间隔");

        intervalPanel.add(intervalSpinner);
        intervalPanel.add(setIntervalBtn);

        // 组装面板
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(controlPanel, BorderLayout.NORTH);
        topPanel.add(manualPanel, BorderLayout.CENTER);
        topPanel.add(intervalPanel, BorderLayout.SOUTH);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(statusScroll, BorderLayout.CENTER);

        // 加载 BGM 列表
        refreshBGMList(bgmCombo);

        // 按钮事件
        startBtn.addActionListener(e -> {
            BGMManager.getInstance().startRandomBGM();
            refreshBGMStatus(statusArea);
        });

        stopBtn.addActionListener(e -> {
            BGMManager.getInstance().stopRandomBGM();
            refreshBGMStatus(statusArea);
        });

        pauseBtn.addActionListener(e -> {
            BGMManager.getInstance().pauseBGM();
            refreshBGMStatus(statusArea);
        });

        resumeBtn.addActionListener(e -> {
            BGMManager.getInstance().resumeBGM();
            refreshBGMStatus(statusArea);
        });

        randomBtn.addActionListener(e -> {
            BGMManager.getInstance().playRandomBGM();
            refreshBGMStatus(statusArea);
        });

        playSelectedBtn.addActionListener(e -> {
            String selected = (String) bgmCombo.getSelectedItem();
            if (selected != null && !selected.isEmpty()) {
                AudioManager.playBackgroundMusic(selected, false);
                refreshBGMStatus(statusArea);
            }
        });

        setIntervalBtn.addActionListener(e -> {
            int seconds = (Integer) intervalSpinner.getValue();
            BGMManager.getInstance().setInterval(seconds * 1000L);
            refreshBGMStatus(statusArea);
        });

        // 初始刷新状态
        refreshBGMStatus(statusArea);

        return panel;
    }

    private void refreshBGMList(JComboBox<String> combo) {
        combo.removeAllItems();
        List<String> bgmFiles = BGMManager.getInstance().getBGMFiles();
        for (String file : bgmFiles) {
            combo.addItem(file);
        }
    }

    private void refreshBGMStatus(JTextArea area) {
        StringBuilder sb = new StringBuilder();
        BGMManager bgm = BGMManager.getInstance();

        sb.append("=== BGM 状态 ===\n");
        sb.append("当前播放: ").append(bgm.getCurrentBGM() != null ? bgm.getCurrentBGM() : "无").append("\n");
        sb.append("暂停状态: ").append(bgm.isPaused() ? "已暂停" : "未暂停").append("\n");
        sb.append("间隔时间: ").append(bgm.getInterval() / 1000).append(" 秒\n");
        sb.append("BGM 数量: ").append(bgm.getBGMFiles().size()).append("\n");
        sb.append("AudioManager 播放中: ").append(AudioManager.getInstance().isBackgroundMusicPlaying() ? "是" : "否").append("\n");

        area.setText(sb.toString());
    }

    /* -------------------- 刷新 -------------------- */
    private void refreshAll() {
        refreshLog();
        refreshFxTree();
        refreshChatHistory();
        refreshAch();
        refreshCfg();
        refreshItemPanel();
    }

    /* ---------- 日志刷新：从 LoggerManager 内存缓冲增量追底 ---------- */
    private void refreshLog() {
        java.util.List<String> logs = LoggerManager.getRecentLogLines();
        int total = logs.size();
        if (total <= lastLogCount) return;          // 无新内容
        // 内存缓冲可能因环形裁剪过而回退，此时从头补（避免越界）
        int from = Math.max(0, lastLogCount);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < total; i++) {
            sb.append(logs.get(i)).append('\n');
        }
        lastLogCount = total;

        SwingUtilities.invokeLater(() -> {
            /* 1. 记录追加前的视图位置 */
            JViewport vp   = logScroll.getViewport();
            Point viewPos  = vp.getViewPosition();
            boolean atBottom = vp.getViewSize().height - (viewPos.y + vp.getHeight()) <= 2;

            /* 2. 追加文本 */
            logArea.append(sb.toString());

            /* 3. 仅当原先已到底才主动滚到底，否则保持原位 */
            if (atBottom) {
                logArea.setCaretPosition(logArea.getDocument().getLength());
                EventQueue.invokeLater(() ->
                        vp.setViewPosition(new Point(0, vp.getViewSize().height - vp.getHeight())));
            }
        });
    }

    /**
     * 聊天记录刷新。
     *
     * <p>记录本身由 {@link ChatHistory} 维护（内存环形缓冲，上限
     * {@link ChatHistory#MAX_ENTRIES} 条），这里只负责把快照渲染出来。
     * 面板上的「清空记录」按钮、章节切换、以及章节代码都可以独立清空它。
     */
    private void refreshChatHistory() {
        ChatHistory history = ChatHistory.getInstance();
        String text = String.join("\n", history.lines());

        SwingUtilities.invokeLater(() -> {
            if (text.equals(historyArea.getText())) {
                return;
            }
            JViewport vp = historyScroll != null ? historyScroll.getViewport() : null;
            boolean atBottom = true;
            if (vp != null) {
                Point pos = vp.getViewPosition();
                atBottom = vp.getViewSize().height - (pos.y + vp.getHeight()) <= 2;
            }

            historyArea.setText(text);
            historyCountLabel.setText(historyLabelText());

            // 只有「原本就停在底部」且开了自动滚动才跟到底，
            // 否则用户正在往上翻历史，会被不断拽回末尾
            if (atBottom && historyAutoScroll.isSelected()) {
                historyArea.setCaretPosition(historyArea.getDocument().getLength());
            }
        });
    }

    private String historyLabelText() {
        String keep = ChatHistory.readKeepAcrossChapters() ? "，跨章节保留" : "";
        return "共 " + ChatHistory.getInstance().size()
                + " / " + ChatHistory.MAX_ENTRIES + " 条" + keep;
    }

    /* 仅更新文本，不重建节点，展开状态保持 */
    private void refreshFxTree() {
        try {
            // 尝试获取GameManager来检查JavaFX是否已初始化
            GameManager gm = GameManager.getInstance();
            if (gm == null) return; // JavaFX未初始化

            // 尝试调用Platform.runLater，如果JavaFX未初始化会抛出异常
            try {
                Platform.runLater(() -> {
                    try {
                        Scene sc = gm.getPrimaryStage().getScene();
                        if (sc == null) return;
                        DefaultMutableTreeNode root = (DefaultMutableTreeNode) fxTree.getModel().getRoot();
                        if (root == null) {        // 第一次
                            root = buildFxNode(sc.getRoot(), 0);
                            DefaultMutableTreeNode finalRoot = root;
                            SwingUtilities.invokeLater(() -> fxTree.setModel(new DefaultTreeModel(finalRoot)));
                        } else {                   // 后续只更新文本
                            updateNodeText(root, sc.getRoot());
                        }
                    } catch (Exception ignore) {
                    }
                });
            } catch (IllegalStateException e) {
                // JavaFX toolkit未初始化，跳过刷新，不影响DebugWindow显示
                // DebugWindow的其他功能（日志、配置等）仍然可以正常工作
            }
        } catch (Exception ignore) {
            // 忽略所有异常，确保DebugWindow能够正常显示
        }
    }

    /* 递归同步文本 */
    private void updateNodeText(DefaultMutableTreeNode swingNode, Node fxNode) {
        swingNode.setUserObject(buildNodeText(fxNode));   // 仅替换文本
        if (fxNode instanceof Parent) {
            List<Node> fxKids = ((Parent) fxNode).getChildrenUnmodifiable();
            for (int i = 0; i < fxKids.size(); i++) {
                /* 如果 swing 树子节点不够就补，多了就删，保证一一对应 */
                while (i >= swingNode.getChildCount()) swingNode.add(new DefaultMutableTreeNode());
                while (swingNode.getChildCount() > fxKids.size()) {
                    swingNode.remove(swingNode.getChildCount() - 1);   // ← 用这一行代替 removeLast()
                }
                updateNodeText((DefaultMutableTreeNode) swingNode.getChildAt(i), fxKids.get(i));
            }
        }
    }

    /* 把节点关键信息拼成一行字符串 */
    private String buildNodeText(Node n) {
        String cls  = n.getClass().getSimpleName();
        String id   = n.getId();
        String vis  = String.valueOf(n.isVisible());
        String css  = n.getStyle();
        if (css != null && css.length() > 60) css = css.substring(0, 60) + "...";
        return String.format("%s [id=%s, visible=%s, bounds=%s] %s",
                cls, id, vis, n.getBoundsInLocal(), css == null ? "" : css);
    }

    private DefaultMutableTreeNode buildFxNode(Node n, int depth) {
        String cls = n.getClass().getSimpleName();
        String id  = n.getId();
        String style = n.getStyle();
        boolean vis = n.isVisible();
        String bounds = n.getBoundsInLocal().toString();
        String txt = String.format("%s [id=%s, visible=%b, bounds=%s]", cls, id, vis, bounds);
        if (style != null && !style.isEmpty()) txt += "  style=" + style.substring(0, Math.min(60, style.length()));
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(txt);
        if (n instanceof Parent) {
            for (Node c : ((Parent) n).getChildrenUnmodifiable()) {
                node.add(buildFxNode(c, depth + 1));
            }
        }
        return node;
    }

    /* 成就页 / 配置页的编辑控件 */
    private final JComboBox<String> achCombo = new JComboBox<>();

    /*
     * 解密文件缓存。
     *
     * 刷新定时器每 500ms 跑一次，如果每次都去读磁盘 + 解密两个 .dat 文件，
     * 那是每秒四次的纯浪费。改成「只在数据真的变了时才重读」：
     * DataManager / AchievementManager 的写入都会发出变更通知，把对应的脏标记立起来。
     */
    private String cachedCfgFile = "";
    private String cachedAchFile = "";
    private volatile boolean cfgFileDirty = true;
    private volatile boolean achFileDirty = true;

    /* 成就页 */
    private javax.swing.JComponent createAchievementPanel() {
        achArea.setEditable(false);
        achArea.setLineWrap(true);
        achArea.setWrapStyleWord(true);
        achScroll = new JScrollPane(achArea);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.add(new JLabel("成就:"));
        achCombo.setEditable(true);
        achCombo.setPreferredSize(new Dimension(300, 26));
        toolbar.add(achCombo);

        JButton unlockBtn = new JButton("解锁");
        unlockBtn.setToolTipText("直接解锁该成就（会写入 achievements.dat）");
        unlockBtn.addActionListener(e -> changeAchievement(true));
        toolbar.add(unlockBtn);

        JButton lockBtn = new JButton("锁定");
        lockBtn.setToolTipText("撤销该成就的解锁状态，用于验证未解锁分支");
        lockBtn.addActionListener(e -> changeAchievement(false));
        toolbar.add(lockBtn);

        JButton resetBtn = new JButton("全部重置");
        resetBtn.addActionListener(e -> {
            AchievementManager.getInstance().resetAllAchievements();
            refreshAch();
        });
        toolbar.add(resetBtn);

        JButton reloadBtn = new JButton("重载文件");
        reloadBtn.setToolTipText("丢弃内存中的成就状态，从磁盘重新读取");
        reloadBtn.addActionListener(e -> {
            AchievementManager.getInstance().reloadFromDisk();
            refreshAch();
        });
        toolbar.add(reloadBtn);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(achScroll, BorderLayout.CENTER);
        return panel;
    }

    private void changeAchievement(boolean unlock) {
        String id = currentAchId();
        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先选择或输入一个成就 ID",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            if (unlock) {
                AchievementManager.unlockAchievement(id);
            } else {
                AchievementManager.lockAchievement(id);
            }
        } catch (Exception ex) {
            // unlockAchievement 内部会弹 JavaFX 通知，JavaFX 尚未就绪时可能抛异常。
            // 状态本身已经改完了，这里只记一笔，不打断调试。
            LoggerManager.Logger("WARNING", "成就操作失败: " + ex.getMessage());
        }
        refreshAch();
    }

    private String currentAchId() {
        Object editing = achCombo.isEditable()
                ? achCombo.getEditor().getItem()
                : achCombo.getSelectedItem();
        return editing == null ? "" : editing.toString().trim();
    }

    private void refreshAch() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 已解锁 ===\n");
        AchievementManager.getInstance().getUnlockedAchievements()
                .forEach(id -> sb.append(id).append(" -> ")
                        .append(achievementName(id)).append('\n'));
        sb.append("\n=== 未解锁 ===\n");
        AchievementManager.getInstance().getLockedAchievements()
                .forEach((id, a) -> sb.append(id).append(" -> ").append(a.name).append('\n'));
        sb.append("\n=== 原始文件(解密后) ===\n");
        sb.append(achFileText());

        updateTextArea(achArea, achScroll, sb.toString());
        SwingUtilities.invokeLater(this::refreshAchCombo);
    }

    private static String achievementName(String id) {
        AchievementManager.Achievement a = AchievementManager.getInstance().getAllAchievements().get(id);
        return a == null ? "(未知成就)" : a.name;
    }

    /** 键列表变了才重建，避免每 500ms 打断正在输入的内容。 */
    private void refreshAchCombo() {
        if (achCombo.isFocusOwner()) {
            return;
        }
        List<String> sorted = AchievementManager.getInstance().getAllAchievements()
                .keySet().stream().sorted().collect(java.util.stream.Collectors.toList());

        List<String> current = new java.util.ArrayList<>();
        for (int i = 0; i < achCombo.getItemCount(); i++) {
            current.add(achCombo.getItemAt(i));
        }
        if (sorted.equals(current)) {
            return;
        }

        String keep = currentAchId();
        achCombo.removeAllItems();
        for (String id : sorted) {
            achCombo.addItem(id);
        }
        if (!keep.isEmpty()) {
            achCombo.getEditor().setItem(keep);
        }
    }

    /* -------------------- 配置页 -------------------- */

    private javax.swing.JComponent createConfigPanel() {
        cfgArea.setEditable(false);
        cfgArea.setLineWrap(true);
        cfgArea.setWrapStyleWord(true);
        cfgScroll = new JScrollPane(cfgArea);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        toolbar.add(new JLabel("键:"));
        cfgKeyCombo.setEditable(true);
        cfgKeyCombo.setPreferredSize(new Dimension(280, 26));
        // 选中某个键时把当前值填进值输入框，省得手打
        cfgKeyCombo.addActionListener(e -> syncCfgValueField());
        toolbar.add(cfgKeyCombo);

        toolbar.add(new JLabel("值:"));
        cfgValueField.setPreferredSize(new Dimension(180, 26));
        toolbar.add(cfgValueField);

        JButton applyBtn = new JButton("设为内存覆盖");
        applyBtn.setToolTipText("只改内存、不写入文件；代码再次写入该键时覆盖会自动失效");
        applyBtn.addActionListener(e -> applyMemoryOverride());
        toolbar.add(applyBtn);

        JButton clearBtn = new JButton("清除覆盖");
        clearBtn.setToolTipText("清除当前键的内存覆盖，回到文件中的值");
        clearBtn.addActionListener(e -> {
            DataManager.clearMemoryOverride(currentCfgKey());
            refreshCfg();
        });
        toolbar.add(clearBtn);

        JButton clearAllBtn = new JButton("清除全部覆盖");
        clearAllBtn.addActionListener(e -> {
            DataManager.clearAllMemoryOverrides();
            refreshCfg();
        });
        toolbar.add(clearAllBtn);

        JButton reloadBtn = new JButton("重载文件");
        reloadBtn.setToolTipText("丢弃内存中的配置，从磁盘重新读取");
        reloadBtn.addActionListener(e -> {
            DataManager.reload();
            refreshCfg();
        });
        toolbar.add(reloadBtn);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(cfgScroll, BorderLayout.CENTER);
        return panel;
    }

    /** 当前输入框里的键名（组合框可编辑，所以取编辑器内容）。 */
    private String currentCfgKey() {
        Object editing = cfgKeyCombo.isEditable()
                ? cfgKeyCombo.getEditor().getItem()
                : cfgKeyCombo.getSelectedItem();
        return editing == null ? "" : editing.toString().trim();
    }

    private void syncCfgValueField() {
        String key = currentCfgKey();
        if (key.isEmpty()) {
            return;
        }
        String value = DataManager.getString(key);
        if (value != null) {
            cfgValueField.setText(value);
        }
    }

    private void applyMemoryOverride() {
        String key = currentCfgKey();
        if (key.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "请先选择或输入一个键名",
                    "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DataManager.setMemoryOverride(key, cfgValueField.getText());
        refreshCfg();
    }

    private void refreshCfg() {
        StringBuilder sb = new StringBuilder();
        Map<String, String> overrides = DataManager.getMemoryOverrides();
        Map<String, String> stored = DataManager.getAll();

        sb.append("=== 内存配置（").append(stored.size()).append(" 项，覆盖 ")
                .append(overrides.size()).append(" 项） ===\n");
        stored.forEach((k, v) -> sb.append(k).append(" = ").append(v)
                .append(overrides.containsKey(k) ? "   [内存覆盖]" : "")
                .append('\n'));
        // 只存在于覆盖层、文件里没有的键也要显示出来
        overrides.forEach((k, v) -> {
            if (!stored.containsKey(k)) {
                sb.append(k).append(" = ").append(v).append("   [仅内存覆盖]\n");
            }
        });

        sb.append("\n=== 原始文件(解密后) ===\n");
        sb.append(cfgFileText());

        updateTextArea(cfgArea, cfgScroll, sb.toString());
        SwingUtilities.invokeLater(this::refreshCfgKeyCombo);
    }

    /**
     * 只在内容真的变了时才 {@code setText}，并恢复原来的滚动位置。
     *
     * <p>每 500ms 无条件 {@code setText} 会让视图被拉回角落 ——
     * 而且因为写入位置在文本末尾，看着就是「每次都跳到底部」。
     * 内容没变就什么都不做，既治跳转也省掉无谓的重排。
     */
    private static void updateTextArea(JTextArea area, JScrollPane scroll, String text) {
        if (area == null || text.equals(area.getText())) {
            return;
        }
        Point viewPos = scroll != null ? scroll.getViewport().getViewPosition() : null;
        final int caret = area.getCaretPosition();

        area.setText(text);
        area.setCaretPosition(Math.min(caret, area.getDocument().getLength()));

        if (viewPos != null) {
            // setText 会把视图拉回起点，等布局完成后再恢复
            SwingUtilities.invokeLater(() -> scroll.getViewport().setViewPosition(viewPos));
        }
    }

    /** 键列表变了才重建，避免每 500ms 打断正在输入的内容。 */
    private void refreshCfgKeyCombo() {
        if (cfgKeyCombo.isFocusOwner() || cfgValueField.isFocusOwner()) {
            return;
        }
        List<String> keys = new java.util.ArrayList<>(DataManager.getAll().keySet());
        keys.addAll(DataManager.getMemoryOverrides().keySet());
        List<String> sorted = keys.stream().distinct().sorted().collect(java.util.stream.Collectors.toList());

        List<String> current = new java.util.ArrayList<>();
        for (int i = 0; i < cfgKeyCombo.getItemCount(); i++) {
            current.add(cfgKeyCombo.getItemAt(i));
        }
        if (sorted.equals(current)) {
            return;
        }

        String keep = currentCfgKey();
        cfgKeyCombo.removeAllItems();
        for (String k : sorted) {
            cfgKeyCombo.addItem(k);
        }
        if (!keep.isEmpty()) {
            cfgKeyCombo.getEditor().setItem(keep);
        }
    }

    /**
     * 把解密出来的 JSON 格式化后再显示。
     *
     * <p>密文里存的是<b>压成一整行</b>的 JSON，直接贴出来是一坨没法读。
     * 不是合法 JSON 时原样返回，不要把「看不懂的内容」也吞掉。
     */
    private static String prettyJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return PRETTY_GSON.toJson(com.google.gson.JsonParser.parseString(json));
        } catch (Exception e) {
            return json;
        }
    }

    private static final com.google.gson.Gson PRETTY_GSON =
            new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create();

    /* -------------------- 解密文件缓存 -------------------- */

    /**
     * {@code game.dat} 解密并格式化后的内容。
     *
     * <p>只在脏标记立起时（{@code DataManager} 有写入 / 显式重载）才重读磁盘。
     */
    private String cfgFileText() {
        if (cfgFileDirty) {
            cachedCfgFile = readDecryptedFile("game.dat");
            cfgFileDirty = false;
        }
        return cachedCfgFile;
    }

    /** {@code achievements.dat} 解密并格式化后的内容。 */
    private String achFileText() {
        if (achFileDirty) {
            cachedAchFile = readDecryptedFile("achievements.dat");
            achFileDirty = false;
        }
        return cachedAchFile;
    }

    /** 读文件 + 解密 + 格式化；出错时返回说明文字而不是抛异常。 */
    private static String readDecryptedFile(String fileName) {
        try {
            byte[] enc = Files.readAllBytes(new File(
                    com.xiaowu.game.starveil.config.GameConstants.DATA_DIR, fileName).toPath());
            return prettyJson(FileCrypto.decrypt(enc));
        } catch (Exception ex) {
            return "解密失败: " + ex.getMessage();
        }
    }

    /* -------------------- 显示致谢 -------------------- */
    private void showCredits() {
        Platform.runLater(() -> {
            try {
                CreditsManager.getInstance().showCredits();
            } catch (Exception ex) {
                LoggerManager.Logger("ERROR", "显示致谢失败: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    /* -------------------- 物品面板 -------------------- */
    // 用于缓存面板引用
    private JPanel backpackPanel;
    private JPanel registryPanel;

    private javax.swing.JComponent createItemPanel() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(400);

        // 左侧：背包
        backpackPanel = new JPanel(new BorderLayout(5, 5));
        backpackPanel.setBorder(DebugWindowTheme.titledBorder("背包"));
        backpackPanel.add(new JLabel("游戏未运行"), BorderLayout.CENTER);

        // 右侧：物品注册表
        registryPanel = new JPanel(new GridLayout(0, 3, 8, 8));
        registryPanel.setBorder(DebugWindowTheme.titledBorder("所有物品"));
        registryPanel.setLayout(new GridLayout(0, 3, 8, 8));
        JScrollPane registryScroll = new JScrollPane(registryPanel);
        buildRegistryPanel();

        split.setLeftComponent(new JScrollPane(backpackPanel));
        split.setRightComponent(registryScroll);
        return split;
    }

    private void buildRegistryPanel() {
        registryPanel.removeAll();
        try {
            com.xiaowu.game.starveil.game.item.ItemRegistry registry = com.xiaowu.game.starveil.game.item.ItemRegistry.getInstance();
            for (String id : new String[]{"health_potion", "magic_crystal", "mystery_key", "ancient_map", "stamina_elixir", "test_item"}) {
                com.xiaowu.game.starveil.game.item.Item item = registry.getItem(id);
                if (item == null) continue;
                JPanel cell = createRegistryItemCell(item);
                registryPanel.add(cell);
            }
        } catch (Exception e) {
            registryPanel.add(new JLabel("加载失败: " + e.getMessage()));
        }
        registryPanel.revalidate();
        registryPanel.repaint();
    }

    private JPanel createRegistryItemCell(com.xiaowu.game.starveil.game.item.Item item) {
        JPanel cell = new JPanel(new BorderLayout(4, 4));
        cell.setBorder(DebugWindowTheme.lineBorder());
        cell.setPreferredSize(new java.awt.Dimension(120, 80));

        // 图标
        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        try {
            java.io.InputStream is = ResourceResolver.getResourceAsStream(item.getIconPath());
            if (is != null) {
                java.awt.image.BufferedImage img = ImageIO.read(is);
                if (img != null) {
                    java.awt.Image scaled = img.getScaledInstance(32, 32, java.awt.Image.SCALE_SMOOTH);
                    iconLabel.setIcon(new ImageIcon(scaled));
                }
            }
        } catch (Exception ignored) {}

        // 名称
        JLabel nameLabel = new JLabel(item.getName());
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        // 给予按钮
        JButton giveBtn = new JButton("给予");
        giveBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        giveBtn.addActionListener(e -> giveItemToSlot(item.getId(), item.getName()));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(iconLabel, BorderLayout.CENTER);
        centerPanel.add(nameLabel, BorderLayout.SOUTH);

        cell.add(centerPanel, BorderLayout.CENTER);
        cell.add(giveBtn, BorderLayout.SOUTH);
        return cell;
    }

    private void giveItemToSlot(String itemId, String itemName) {
        String[] options = {"槽位 0", "槽位 1", "槽位 2", "槽位 3", "槽位 4", "槽位 5", "槽位 6", "槽位 7", "槽位 8", "手上"};
        String choice = (String) JOptionPane.showInputDialog(frame, "将 " + itemName + " 给予到：", "选择槽位", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == null) return;

        com.xiaowu.game.starveil.game.state.GameInstance gi = com.xiaowu.game.starveil.game.state.GameInstance.getCurrentInstance();
        if (gi == null || gi.getInventory() == null) {
            JOptionPane.showMessageDialog(frame, "游戏未运行", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        com.xiaowu.game.starveil.game.item.Inventory inv = gi.getInventory();
        if (choice.equals("手上")) {
            inv.setHandSlot(itemId);
        } else {
            int slot = Integer.parseInt(choice.split(" ")[1]);
            inv.setBackpackSlot(slot, itemId);
        }
        refreshItemPanel();
    }

    private void refreshItemPanel() {
        if (backpackPanel == null) return;

        SwingUtilities.invokeLater(() -> {
            com.xiaowu.game.starveil.game.state.GameInstance gi = com.xiaowu.game.starveil.game.state.GameInstance.getCurrentInstance();
            if (gi == null || gi.getInventory() == null) {
                backpackPanel.removeAll();
                backpackPanel.add(new JLabel("游戏未运行"), BorderLayout.CENTER);
                backpackPanel.revalidate();
                backpackPanel.repaint();
                return;
            }

            com.xiaowu.game.starveil.game.item.Inventory inv = gi.getInventory();
            backpackPanel.removeAll();
            backpackPanel.setLayout(new BorderLayout(5, 5));

            // 手上格子
            JPanel handPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
            handPanel.setBorder(DebugWindowTheme.titledBorder("手上"));
            handPanel.add(createBackpackItemCell("手上", -1, inv));
            backpackPanel.add(handPanel, BorderLayout.NORTH);

            // 3x3 网格
            JPanel gridPanel = new JPanel(new GridLayout(3, 3, 8, 8));
            gridPanel.setBorder(DebugWindowTheme.titledBorder("背包"));
            for (int i = 0; i < 9; i++) {
                final int idx = i;
                gridPanel.add(createBackpackItemCell("槽位 " + i, idx, inv));
            }
            backpackPanel.add(new JScrollPane(gridPanel), BorderLayout.CENTER);

            backpackPanel.revalidate();
            backpackPanel.repaint();
        });
    }

    private JPanel createBackpackItemCell(String label, int index, com.xiaowu.game.starveil.game.item.Inventory inv) {
        JPanel cell = new JPanel(new BorderLayout(2, 2));
        cell.setPreferredSize(new java.awt.Dimension(80, 70));
        cell.setBorder(DebugWindowTheme.lineBorder());

        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel textLabel = new JLabel();
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        textLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));

        String itemId = (index >= 0) ? inv.getBackpackSlot(index) : inv.getHandSlot();
        if (itemId != null && !itemId.isEmpty()) {
            com.xiaowu.game.starveil.game.item.Item item = com.xiaowu.game.starveil.game.item.ItemRegistry.getInstance().getItem(itemId);
            if (item != null) {
                textLabel.setText(item.getName());
                try {
                    java.io.InputStream is = ResourceResolver.getResourceAsStream(item.getIconPath());
                    if (is != null) {
                        java.awt.image.BufferedImage img = ImageIO.read(is);
                        if (img != null) {
                            java.awt.Image scaled = img.getScaledInstance(28, 28, java.awt.Image.SCALE_SMOOTH);
                            iconLabel.setIcon(new ImageIcon(scaled));
                        }
                    }
                } catch (Exception ignored) {}
            }
        } else {
            textLabel.setText("空");
        }

        // 右键菜单
        JPopupMenu popup = new JPopupMenu();
        JMenuItem removeItem = new JMenuItem("移除物品");
        removeItem.addActionListener(e -> {
            if (index >= 0) {
                inv.setBackpackSlot(index, null);
            } else {
                inv.setHandSlot(null);
            }
            refreshItemPanel();
        });
        popup.add(removeItem);
        cell.setComponentPopupMenu(popup);
        cell.setInheritsPopupMenu(true);

        cell.add(iconLabel, BorderLayout.CENTER);
        cell.add(textLabel, BorderLayout.SOUTH);
        return cell;
    }
}