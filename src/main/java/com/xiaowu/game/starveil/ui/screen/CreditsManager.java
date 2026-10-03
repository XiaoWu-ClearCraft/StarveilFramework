package com.xiaowu.game.starveil.ui.screen;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.ui.core.Menu;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.platform.common.DialogCleanup;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class CreditsManager {

    private static CreditsManager instance;

    private AnchorPane creditsContainer;
    private VBox creditsContent;
    private Label finalMessageLabel; // 最终消息标签
    private boolean isPlaying = false;
    private boolean pendingCreditsStart = false; // 标记是否有待启动的致谢名单（在游戏暂停时启动）
    private Timeline scrollTimeline;
    private Timeline finalMessageTimer; // 最终消息定时器
    private double scrollSpeed = 1.5;
    private double totalHeight = 0;
    private long scrollStartTime = 0; // 滚动开始时间

    private List<CreditsSection> sections;
    private String finalMessage;
    private String bgmPath; // 背景音乐路径
    private double bgmDuration = 0.0; // BGM时长（秒）

    // 长按空格键退出相关
    private VBox spaceKeyIndicator;
    private Rectangle spaceKeyProgress;
    private boolean isSpacePressed = false;
    private boolean spaceHoldCompleted = false; // 标记空格键长按进度条是否已完成
    private Timeline spaceHoldTimeline;
    private final double spaceHoldDuration = 2000.0; // 长按2秒退出
    private double spaceHoldProgress = 0.0;
    private Timeline hideIndicatorTimeline;
    private final double indicatorShowDuration = 3.0; // 显示3秒后自动隐藏

    // 常量定义
    // 注意这几个是【实例】字段而非 static：主题色由内容在 content.init.init 里设定，
    // 而 static 字段在类加载时就求值 —— 启动顺序是「INIT 插件 → content init」，
    // 若某个 INIT 插件在 onLoad 里碰到本类，static 会把默认色永久冻住。
    private final String PRIMARY = ContentConfig.primaryColor();
    private final String SECONDARY = ContentConfig.secondaryColor();
    private static final String TEXT_PRIMARY = "#ffffff";
    private final String TEXT_SECONDARY = ContentConfig.tertiaryColor();
    private static final String ACCENT = "#FFB6C1";

    // 动画相关常量
    private static final double DEFAULT_SCROLL_SPEED = 1.5;
    private static final double SCROLL_SPEED_MIN = 0.5;
    private static final double SCROLL_SPEED_MAX = 5.0;
    private static final int FRAME_RATE = 60; // FPS
    private static final double FRAME_DURATION = 1000.0 / FRAME_RATE; // 毫秒

    // 时间相关常量
    private static final double FADE_DURATION = 1.0; // 淡入淡出时间（秒）
    private static final double FINAL_MESSAGE_DISPLAY_TIME = 3.0; // 最终消息显示时间（秒）
    private static final double FINAL_MESSAGE_FADE_IN_TIME = 1.0; // 最终消息淡入时间（秒）
    private static final double INDICATOR_SHOW_DURATION = 3.0; // 指示器显示时间（秒）
    private static final double SPACE_HOLD_DURATION = 2000.0; // 长按退出时间（毫秒）
    private static final double BGM_START_DELAY = 0.5; // BGM启动延迟（秒）
    private static final double SCROLL_COMPLETE_DELAY = 0.016; // 滚动完成检查延迟（秒）

    // UI相关常量
    private static final double TITLE_FONT_SIZE = 28.0;
    private static final double CONTENT_FONT_SIZE = 18.0;
    private static final double VOICE_TITLE_FONT_SIZE = 20.0;
    private static final double VOICE_CHAR_FONT_SIZE = 18.0;
    private static final double VOICE_ACTOR_FONT_SIZE = 16.0;
    private static final double FINAL_MESSAGE_FONT_SIZE = 36.0;
    private static final double INDICATOR_FONT_SIZE = 12.0;
    private static final double PROGRESS_BAR_WIDTH = 60.0;
    private static final double PROGRESS_BAR_HEIGHT = 8.0;
    private static final double PROGRESS_BAR_RADIUS = 4.0;
    private static final double CREDITS_CONTENT_SPACING = 20.0;
    private static final double SECTION_SPACING = 40.0;
    private static final double TITLE_PADDING = 20.0;
    private static final double TITLE_BOTTOM_PADDING = 10.0;
    private static final double VOICE_TITLE_PADDING = 15.0;
    private static final double VOICE_ENTRY_SPACING = 2.0;
    private static final double CREDITS_CONTENT_PADDING = 100.0;
    private static final double CREDITS_TOP_BOTTOM_PADDING = 50.0;
    private static final double INDICATOR_MARGIN = 30.0;
    private static final double INDICATOR_INNER_PADDING = 10.0;
    private static final double INDICATOR_SPACING = 5.0;
    private static final double CREDITS_CONTENT_MAX_WIDTH = 600.0;

    // 默认值
    private static final String DEFAULT_FINAL_MESSAGE = "特别感谢屏幕前的你";
    private static final String XIAOLAI_FONT_PATH = "starveil:fonts/xiaolai-sc-regular.ttf";

    private Font xiaolaiFont;
    private boolean fontLoaded = false;

    // 指定要显示的结局名称（null 则使用第一个结局）
    private String targetEndingName = null;

    public static CreditsManager getInstance() {
        if (instance == null) {
            instance = new CreditsManager();
        }
        return instance;
    }

    /**
     * 设置要显示的结局名称
     * @param endingName 结局名称，对应 JSON 中 credits 下的键名
     *                   设为 null 则使用第一个结局
     */
    public void setEndingName(String endingName) {
        this.targetEndingName = endingName;
    }

    /**
     * 获取当前设置的结局名称
     */
    public String getEndingName() {
        return targetEndingName;
    }

    private CreditsManager() {
        loadCustomFont();
        initializeUI();
    }

    private void loadCustomFont() {
        try (java.io.InputStream is = ResourceResolver.getResourceAsStream(XIAOLAI_FONT_PATH)) {
            if (is != null) {
                xiaolaiFont = Font.loadFont(is, 16);
                fontLoaded = true;
                Logger("DEBUG", "Custom font loaded successfully");
            } else {
                xiaolaiFont = Font.font(16);
                Logger("WARN", "Custom font not found, using system font");
            }
        } catch (Exception e) {
            xiaolaiFont = Font.font(16);
            Logger("ERROR", "Error loading custom font: " + e.getMessage());
        }
    }

    private String getFontStyle() {
        return fontLoaded ? "-fx-font-family: '" + xiaolaiFont.getFamily() + "'; " : "-fx-font-family: system; ";
    }

    /**
     * 创建带有指定颜色和字体大小的Text节点
     */
    private Text createStyledText(String text, String color, int size) {
        Text textNode = new Text(text);
        textNode.setFill(Color.web(color));
        textNode.setFont(fontLoaded ? Font.font(xiaolaiFont.getFamily(), size) : Font.font(size));
        return textNode;
    }

    /**
     * 创建带有默认颜色的Text节点
     */
    private Text createDefaultText(String text, int size) {
        return createStyledText(text, TEXT_PRIMARY, size);
    }

    private void initializeUI() {
        // 使用 AnchorPane 作为主容器，提供精确的定位控制
        creditsContainer = new AnchorPane();
        creditsContainer.setStyle("-fx-background-color: black;");
        creditsContainer.setVisible(false);
        creditsContainer.setMouseTransparent(true);
        creditsContainer.setFocusTraversable(true);  // 使容器可以获取焦点以接收键盘事件

        creditsContent = new VBox(CREDITS_CONTENT_SPACING);
        creditsContent.setAlignment(Pos.TOP_CENTER);
        creditsContent.setPadding(new Insets(CREDITS_TOP_BOTTOM_PADDING, CREDITS_CONTENT_PADDING, CREDITS_TOP_BOTTOM_PADDING, CREDITS_CONTENT_PADDING));
        creditsContent.setStyle("-fx-background-color: transparent;");
        creditsContent.setOpacity(1.0);

        // 滚动包装器 - 用于内容滚动
        StackPane scrollWrapper = new StackPane();
        scrollWrapper.getChildren().add(creditsContent);
        scrollWrapper.setAlignment(Pos.TOP_LEFT); // 设置为左上对齐，避免居中
        scrollWrapper.setMouseTransparent(true);

        // 将scrollWrapper设置为填满整个容器
        AnchorPane.setTopAnchor(scrollWrapper, 0.0);
        AnchorPane.setBottomAnchor(scrollWrapper, 0.0);
        AnchorPane.setLeftAnchor(scrollWrapper, 0.0);
        AnchorPane.setRightAnchor(scrollWrapper, 0.0);

        // 创建最终消息标签（屏幕中央）
        finalMessageLabel = new Label();
        finalMessageLabel.setAlignment(Pos.CENTER);
        finalMessageLabel.setTextAlignment(TextAlignment.CENTER);
        finalMessageLabel.setStyle(getFontStyle() + "-fx-text-fill: " + PRIMARY + "; -fx-font-size: " + FINAL_MESSAGE_FONT_SIZE + "px; -fx-font-weight: bold;");
        finalMessageLabel.setOpacity(0.0);
        finalMessageLabel.setVisible(false);
        finalMessageLabel.setMouseTransparent(true); // 防止遮挡下层内容

        // 将最终消息居中（只在显示时才设置锚点）
        // AnchorPane.setTopAnchor(finalMessageLabel, 0.0);
        // AnchorPane.setBottomAnchor(finalMessageLabel, 0.0);
        // AnchorPane.setLeftAnchor(finalMessageLabel, 0.0);
        // AnchorPane.setRightAnchor(finalMessageLabel, 0.0);

        // 初始化空格键指示器
        initializeSpaceKeyIndicator();

        // 按顺序添加子元素：滚动内容、最终消息、SPACE指示器
        creditsContainer.getChildren().addAll(scrollWrapper, finalMessageLabel, spaceKeyIndicator);
    }

    private void initializeSpaceKeyIndicator() {
        spaceKeyIndicator = new VBox(INDICATOR_SPACING);
        spaceKeyIndicator.setAlignment(Pos.CENTER);
        spaceKeyIndicator.setPadding(new Insets(INDICATOR_INNER_PADDING));
        spaceKeyIndicator.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.3); -fx-border-radius: 8;");
        spaceKeyIndicator.setOpacity(0.0);
        spaceKeyIndicator.setManaged(true);
        spaceKeyIndicator.setVisible(true);

        Label spaceKeyLabel = new Label("SPACE");
        spaceKeyLabel.setStyle(getFontStyle() + "-fx-text-fill: white; -fx-font-size: " + INDICATOR_FONT_SIZE + "px; -fx-font-weight: bold;");

        // 进度条背景
        Rectangle progressBg = new Rectangle(PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT);
        progressBg.setFill(Color.rgb(255, 255, 255, 0.3));
        progressBg.setArcWidth(PROGRESS_BAR_RADIUS);
        progressBg.setArcHeight(PROGRESS_BAR_RADIUS);

        // 进度条
        spaceKeyProgress = new Rectangle(PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT);
        spaceKeyProgress.setFill(Color.WHITE);
        spaceKeyProgress.setArcWidth(PROGRESS_BAR_RADIUS);
        spaceKeyProgress.setArcHeight(PROGRESS_BAR_RADIUS);
        spaceKeyProgress.setWidth(0);

        StackPane progressContainer = new StackPane();
        progressContainer.getChildren().addAll(progressBg, spaceKeyProgress);

        spaceKeyIndicator.getChildren().addAll(spaceKeyLabel, progressContainer);

        // 将SPACE指示器固定在右下角（使用锚点约束）
        AnchorPane.setRightAnchor(spaceKeyIndicator, INDICATOR_MARGIN);
        AnchorPane.setBottomAnchor(spaceKeyIndicator, INDICATOR_MARGIN);

        Logger("DEBUG", "Space key indicator initialized. AnchorPane children: " + creditsContainer.getChildren().size());
    }

    /**
     * 监听场景大小变化，动态更新creditsContainer大小和SPACE指示器位置
     */
    private void setupSceneSizeChangeListener(StackPane contentWithOverlays, Scene scene) {
        scene.widthProperty().addListener((_, _, _) -> Platform.runLater(() -> {
            if (creditsContainer == null || !creditsContainer.isVisible()) return;

            double newWidth = scene.getWidth();
            double newHeight = scene.getHeight();

            // 更新容器固定大小
            creditsContainer.setMinSize(newWidth, newHeight);
            creditsContainer.setMaxSize(newWidth, newHeight);
            creditsContainer.setPrefSize(newWidth, newHeight);

            // 重新应用锚点约束
            AnchorPane.setRightAnchor(spaceKeyIndicator, INDICATOR_MARGIN);
            AnchorPane.setBottomAnchor(spaceKeyIndicator, INDICATOR_MARGIN);

            // 强制刷新布局
            creditsContainer.requestLayout();

            Logger("DEBUG", "Scene size changed: " + newWidth + "x" + newHeight + ", container resized and indicator repositioned");
        }));

        scene.heightProperty().addListener((_, _, _) -> Platform.runLater(() -> {
            if (creditsContainer == null || !creditsContainer.isVisible()) return;

            double newWidth = scene.getWidth();
            double newHeight = scene.getHeight();

            // 更新容器固定大小
            creditsContainer.setMinSize(newWidth, newHeight);
            creditsContainer.setMaxSize(newWidth, newHeight);
            creditsContainer.setPrefSize(newWidth, newHeight);

            // 重新应用锚点约束
            AnchorPane.setRightAnchor(spaceKeyIndicator, INDICATOR_MARGIN);
            AnchorPane.setBottomAnchor(spaceKeyIndicator, INDICATOR_MARGIN);

            // 强制刷新布局
            creditsContainer.requestLayout();

            Logger("DEBUG", "Scene size changed: " + newWidth + "x" + newHeight + ", container resized and indicator repositioned");
        }));
    }

    public void showCredits() {
        // 检查是否已经在播放
        if (isPlaying) {
            Logger("WARN", "Credits already playing");
            return;
        }

        Logger("INFO", "Starting to show credits...");

        // 立即停止随机BGM，确保在致谢名单开始前完全停止随机BGM播放
        com.xiaowu.game.starveil.infrastructure.audio.BGMManager.getInstance().stopRandomBGM();
        Logger("INFO", "Stopped random BGM for credits");

        if (!loadCreditsData()) {
            Logger("ERROR", "Failed to load credits data");
            return;
        }

        Logger("INFO", "Credits data loaded successfully. Sections: " + (sections != null ? sections.size() : 0));

        GameManager gm = GameManager.getInstance();
        if (gm == null) {
            Logger("ERROR", "GameManager not initialized");
            return;
        }

        // 确保UI组件已初始化
        if (creditsContainer == null || creditsContent == null) {
            Logger("ERROR", "UI components not initialized");
            initializeUI();
        }

        // 检查游戏是否暂停
        if (isGamePaused()) {
            // 游戏暂停时，设置待启动标志，等待游戏恢复后再启动
            pendingCreditsStart = true;
            Logger("INFO", "游戏暂停中，致谢名单将在恢复后显示");
            return;
        }

        // 重置状态变量
        resetState();
        // 禁用游戏控制
        disableGameControls();
        // 不在这里播放BGM，而是在内容淡入完成之后再播放
        Platform.runLater(() -> {
            isPlaying = true;
            Logger("INFO", "Building credits content...");
            buildCreditsContent();

            Logger("INFO", "Credits content children count: " + creditsContent.getChildren().size());

            // 其他设置将在buildCreditsContent完成后通过回调完成
        });
    }

    private void resetState() {
        // 重置状态变量
        isPlaying = false;
        pendingCreditsStart = false; // 重置待启动标志
        if (scrollTimeline != null) {
            scrollTimeline.stop();
            scrollTimeline = null;
        }
        if (finalMessageTimer != null) {
            finalMessageTimer.stop();
            finalMessageTimer = null;
        }
        if (spaceHoldTimeline != null) {
            spaceHoldTimeline.stop();
            spaceHoldTimeline = null;
        }
        if (hideIndicatorTimeline != null) {
            hideIndicatorTimeline.stop();
            hideIndicatorTimeline = null;
        }
        scrollStartTime = 0;
        scrollSpeed = DEFAULT_SCROLL_SPEED;
        totalHeight = 0;
        spaceHoldProgress = 0.0;
        isSpacePressed = false;
        spaceHoldCompleted = false; // 重置长按完成标志
        spaceKeyProgress.setWidth(0);
        spaceKeyIndicator.setOpacity(0.0);
        finalMessageLabel.setOpacity(0.0);
        finalMessageLabel.setVisible(false);
        // 重置内容位置和透明度，确保每次播放时都从正确的初始状态开始
        if (creditsContent != null) {
            creditsContent.setTranslateY(0);
            creditsContent.setOpacity(0.0);
            // 清除可能存在的绑定
            creditsContent.prefHeightProperty().unbind();
            // 重置布局状态（但保留宽度设置）
            creditsContent.setMinHeight(Region.USE_COMPUTED_SIZE);
            creditsContent.setMaxHeight(Region.USE_COMPUTED_SIZE);
            creditsContent.setPrefHeight(Region.USE_COMPUTED_SIZE);
        }
        if (creditsContainer != null) {
            creditsContainer.setOpacity(1.0);
        }
    }

    private void playBGM() {
        if (bgmPath != null && !bgmPath.trim().isEmpty()) {
            try {
                AudioManager.playCreditsBGM(bgmPath);
                Logger("INFO", "Playing credits BGM: " + bgmPath);
            } catch (Exception e) {
                Logger("ERROR", "Failed to play credits BGM: " + e.getMessage());
                // 即使BGM播放失败，也要继续显示制作者名单
            }
        }
    }

    private void stopBGM() {
        try {
            AudioManager.stopBackgroundMusic();
            Logger("INFO", "Stopped credits BGM");
        } catch (Exception e) {
            Logger("ERROR", "Failed to stop credits BGM: " + e.getMessage());
        }
    }

    private boolean loadCreditsData() {
        try {
            String jsonPath = "starveil:data/config/credits.json";
            String jsonContent = DataManager.loadJson(jsonPath);

            if (jsonContent == null || jsonContent.isEmpty()) {
                Logger("ERROR", "Credits JSON file is empty or not found: " + jsonPath);
                return false;
            }

            // 先解析整个 JSON 为 Map
            Map<String, Object> root = DataManager.parseJsonToMap(jsonContent);
            if (root == null) {
                Logger("ERROR", "Failed to parse JSON root");
                return false;
            }

            // 解析变量定义
            Map<String, Object> vars = parseVars(root);

            // 获取致谢数据（支持多结局）
            Map<String, Object> creditsData = getCreditsData(root);
            if (creditsData == null) {
                Logger("ERROR", "No credits data found in JSON");
                return false;
            }

            // 解析 sections 数组，应用变量替换
            sections = parseCreditsJson(creditsData, vars);

            if (sections == null || sections.isEmpty()) {
                Logger("ERROR", "Failed to parse credits sections");
                return false;
            }

            // 获取最终消息（可选）
            String finalMsg = (String) creditsData.get("finalMessage");
            if (finalMsg != null) {
                finalMessage = parseColorText(finalMsg);
            } else {
                finalMessage = parseColorText(DEFAULT_FINAL_MESSAGE);
            }

            // 获取背景音乐路径（可选）
            bgmPath = (String) creditsData.get("bgm");

            // 获取BGM时长（可选，单位：秒）
            Object bgmDurObj = creditsData.get("bgmDuration");
            if (bgmDurObj instanceof Number) {
                bgmDuration = ((Number) bgmDurObj).doubleValue();
            } else if (bgmDurObj instanceof String) {
                try {
                    bgmDuration = Double.parseDouble((String) bgmDurObj);
                } catch (NumberFormatException e) {
                    bgmDuration = 0.0;
                }
            } else {
                bgmDuration = 0.0;
            }

            return true;
        } catch (Exception e) {
            Logger("ERROR", "Error loading credits data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 解析变量定义
     * 支持格式: { "vars": { "var1": [...], "var2": {...} } }
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVars(Map<String, Object> root) {
        Map<String, Object> vars = new HashMap<>();
        Object varsObj = root.get("vars");
        if (varsObj instanceof Map) {
            vars.putAll((Map<String, Object>) varsObj);
            Logger("INFO", "Loaded " + vars.size() + " variables from JSON");
        }
        return vars;
    }

    /**
     * 获取致谢数据，支持多结局格式
     * 旧格式: { "bgm": ..., "sections": [...] }
     * 新格式: { "credits": { "ending1": { "bgm": ..., "sections": [...] }, "ending2": {...} } }
     * 默认使用第一个结局，或通过 setEndingName() 指定结局
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getCreditsData(Map<String, Object> root) {
        // 先检查是否有 credits 字段（多结局格式）
        Object creditsObj = root.get("credits");
        if (creditsObj instanceof Map) {
            Map<String, Object> creditsMap = (Map<String, Object>) creditsObj;
            if (!creditsMap.isEmpty()) {
                // 如果指定了结局名称，使用指定的
                if (targetEndingName != null && creditsMap.containsKey(targetEndingName)) {
                    Logger("INFO", "Using specified credits ending: " + targetEndingName);
                    return (Map<String, Object>) creditsMap.get(targetEndingName);
                }

                // 否则使用第一个结局
                String firstKey = creditsMap.keySet().iterator().next();
                Logger("INFO", "Using first credits ending: " + firstKey);
                return (Map<String, Object>) creditsMap.get(firstKey);
            }
        }

        // 旧格式：直接使用 root
        return root;
    }

    /**
     * 解析致谢 JSON，支持变量替换
     */
    @SuppressWarnings("unchecked")
    private List<CreditsSection> parseCreditsJson(Map<String, Object> creditsData, Map<String, Object> vars) {
        List<CreditsSection> result = new ArrayList<>();

        Logger("INFO", "Parsing credits JSON with " + vars.size() + " variables...");

        // 解析 sections 数组
        Object sectionsObj = creditsData.get("sections");
        if (sectionsObj instanceof List) {
            List<Map<String, Object>> sectionsList = (List<Map<String, Object>>) sectionsObj;
            Logger("INFO", "Found " + sectionsList.size() + " sections in JSON");

            for (Map<String, Object> sectionData : sectionsList) {
                CreditsSection section = parseSection(sectionData, vars);
                if (section != null && !section.isEmpty()) {
                    result.add(section);
                }
            }
        } else if (sectionsObj instanceof String) {
            // sections 可能是变量引用字符串 "{var1}"
            String varRef = (String) sectionsObj;
            List<CreditsSection> varSections = resolveVarSections(varRef, vars);
            if (varSections != null) {
                result.addAll(varSections);
            }
        } else {
            Logger("WARN", "No sections found in JSON");
        }

        Logger("INFO", "Parsed " + result.size() + " sections from JSON");
        return result;
    }

    /**
     * 解析单个 section，支持变量替换
     */
    @SuppressWarnings("unchecked")
    private CreditsSection parseSection(Map<String, Object> sectionData, Map<String, Object> vars) {
        CreditsSection section = new CreditsSection();

        // 标题 - 支持变量引用
        Object titleObj = sectionData.get("title");
        if (titleObj instanceof String) {
            String title = resolveVarString((String) titleObj, vars);
            section.title = parseColorText(title);
        }

        // 内容列表 - 支持变量引用
        Object contentObj = sectionData.get("content");
        if (contentObj instanceof List) {
            List<String> contentList = (List<String>) contentObj;
            for (String content : contentList) {
                String resolved = resolveVarString(content, vars);
                section.content.add(parseColorText(resolved));
            }
        }

        // 检查是否有 "fill" 字段，表示直接填充变量内容
        Object fillObj = sectionData.get("fill");
        if (fillObj instanceof String) {
            String varName = (String) fillObj;
            fillSectionFromVar(section, varName, vars);
        }

        // 配音映射
        Map<String, String> actorsMap = (Map<String, String>) sectionData.get("Actors");
        if (actorsMap != null) {
            section.Actors.putAll(actorsMap);
            section.hasActors = true;
        }

        return section;
    }

    /**
     * 解析字符串中的变量引用
     * 支持格式: "{varName}" -> 替换为变量内容
     */
    @SuppressWarnings("unchecked")
    private String resolveVarString(String text, Map<String, Object> vars) {
        if (text == null) return "";

        // 检查是否是纯变量引用 "{varName}"
        String trimmed = text.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String varName = trimmed.substring(1, trimmed.length() - 1);
            if (vars.containsKey(varName)) {
                Object varValue = vars.get(varName);
                if (varValue instanceof String) {
                    return (String) varValue;
                }
            }
        }

        return text;
    }

    /**
     * 从变量填充 section 内容
     */
    @SuppressWarnings("unchecked")
    private void fillSectionFromVar(CreditsSection section, String varName, Map<String, Object> vars) {
        Object varValue = vars.get(varName);
        if (varValue == null) {
            Logger("WARN", "Variable not found: " + varName);
            return;
        }

        if (varValue instanceof List) {
            // 变量是 section 数组
            List<Map<String, Object>> sectionList = (List<Map<String, Object>>) varValue;
            for (Map<String, Object> subSection : sectionList) {
                CreditsSection sub = parseSection(subSection, vars);
                if (sub != null && !sub.isEmpty()) {
                    // 将子 section 的内容合并到当前 section
                    if (section.title == null || section.title.isEmpty()) {
                        section.title = sub.title;
                    }
                    section.content.addAll(sub.content);
                    section.Actors.putAll(sub.Actors);
                    if (sub.hasActors) section.hasActors = true;
                }
            }
        } else if (varValue instanceof Map) {
            // 变量是单个 section
            Map<String, Object> subSection = (Map<String, Object>) varValue;
            CreditsSection sub = parseSection(subSection, vars);
            if (sub != null) {
                if (section.title == null || section.title.isEmpty()) {
                    section.title = sub.title;
                }
                section.content.addAll(sub.content);
                section.Actors.putAll(sub.Actors);
                if (sub.hasActors) section.hasActors = true;
            }
        }
    }

    /**
     * 解析 sections 字段中的变量引用字符串
     * 如: "{var1}" -> 返回变量中定义的 sections 列表
     */
    @SuppressWarnings("unchecked")
    private List<CreditsSection> resolveVarSections(String varRef, Map<String, Object> vars) {
        String trimmed = varRef.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            String varName = trimmed.substring(1, trimmed.length() - 1);
            Object varValue = vars.get(varName);
            if (varValue instanceof List) {
                List<Map<String, Object>> sectionList = (List<Map<String, Object>>) varValue;
                List<CreditsSection> result = new ArrayList<>();
                for (Map<String, Object> sectionData : sectionList) {
                    CreditsSection section = parseSection(sectionData, vars);
                    if (section != null && !section.isEmpty()) {
                        result.add(section);
                    }
                }
                return result;
            }
        }
        return null;
    }

    private String parseColorText(String text) {
        if (text == null) return "";

        // 支持颜色标识格式：<#color> 或 <color name>
        // 例如：<#FF0000>红色文本</#FF0000> 或 <red>红色文本</red>
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < text.length()) {
            if (text.charAt(i) == '<' && i + 1 < text.length()) {
                // 检查是否是颜色标记
                int tagEnd = text.indexOf('>', i);
                if (tagEnd != -1) {
                    String tagContent = text.substring(i + 1, tagEnd);
                    String colorCode = parseColorTag(tagContent);
                    if (colorCode != null) {
                        // 查找对应的结束标签
                        String endTag = "</" + tagContent + ">";
                        int textEnd = text.indexOf(endTag, tagEnd);
                        if (textEnd != -1) {
                            String coloredText = text.substring(tagEnd + 1, textEnd);
                            // 保留颜色标记用于后续处理
                            result.append("<").append(colorCode).append(">").append(coloredText).append("</").append(colorCode).append(">");
                            i = textEnd + endTag.length();
                            continue;
                        }
                    }
                }
            }
            result.append(text.charAt(i));
            i++;
        }

        return result.toString();
    }

    private String parseColorTag(String tagContent) {
        // 检查是否是十六进制颜色 <#color>
        if (tagContent.startsWith("#")) {
            String colorCode = tagContent.substring(1);
            if (isValidColorCode(colorCode)) {
                return "#" + colorCode;
            }
        }

        // 检查是否是颜色名称
        String hexColor = getColorHexFromName(tagContent.toLowerCase());
        if (hexColor != null) {
            return hexColor;
        }

        return null;
    }

    private String getColorHexFromName(String colorName) {
        switch (colorName) {
            case "red":
                return "#FF0000";
            case "green":
                return "#00FF00";
            case "blue":
                return "#0000FF";
            case "yellow":
                return "#FFFF00";
            case "orange":
                return "#FFA500";
            case "purple":
                return "#800080";
            case "pink":
                return "#FFC0CB";
            case "white":
                return "#FFFFFF";
            case "black":
                return "#000000";
            case "gray":
                return "#808080";
            case "grey":
                return "#808080";
            case "lightgray":
                return "#D3D3D3";
            case "lightgrey":
                return "#D3D3D3";
            case "darkgray":
                return "#A9A9A9";
            case "darkgrey":
                return "#A9A9A9";
            case "cyan":
                return "#00FFFF";
            case "magenta":
                return "#FF00FF";
            case "lime":
                return "#00FF00";
            case "maroon":
                return "#800000";
            case "navy":
                return "#000080";
            case "olive":
                return "#808000";
            case "teal":
                return "#008080";
            case "silver":
                return "#C0C0C0";
            default:
                return null;
        }
    }

    private boolean isValidColorCode(String code) {
        return code.matches("^[0-9a-fA-F]{6}$") || code.matches("^[0-9a-fA-F]{3}$");
    }

    private void buildCreditsContent() {
        creditsContent.getChildren().clear();

        Logger("INFO", "Building credits content with " + sections.size() + " sections");

        // 构建每个section
        for (CreditsSection section : sections) {
            if (section.title != null && !section.title.isEmpty()) {
                Label titleLabel = createStyledLabel(section.title, true, (int) TITLE_FONT_SIZE);
                titleLabel.setStyle(getFontStyle() + "-fx-text-fill: " + TEXT_PRIMARY + "; " + "-fx-font-size: " + TITLE_FONT_SIZE + "px; " + "-fx-font-weight: bold; " + "-fx-padding: " + TITLE_PADDING + " 0 " + TITLE_BOTTOM_PADDING + " 0;");
                creditsContent.getChildren().add(titleLabel);
            }

            // 添加普通内容
            for (String content : section.content) {
                Label contentLabel = createStyledLabel(content, false, (int) CONTENT_FONT_SIZE);
                creditsContent.getChildren().add(contentLabel);
            }

            // 添加配音列表
            if (section.hasActors && !section.Actors.isEmpty()) {
                for (Map.Entry<String, String> entry : section.Actors.entrySet()) {
                    String character = entry.getKey();
                    String actor = entry.getValue();

                    VBox voiceEntry = new VBox(VOICE_ENTRY_SPACING);
                    voiceEntry.setAlignment(Pos.CENTER);

                    Label charLabel = createStyledLabel(character, true, (int) VOICE_CHAR_FONT_SIZE);
                    charLabel.setStyle(getFontStyle() + "-fx-text-fill: " + TEXT_SECONDARY + "; " + "-fx-font-size: " + VOICE_CHAR_FONT_SIZE + "px;");

                    Label actorLabel = createStyledLabel(actor, false, (int) VOICE_ACTOR_FONT_SIZE);
                    actorLabel.setStyle(getFontStyle() + "-fx-text-fill: " + TEXT_PRIMARY + "; " + "-fx-font-size: " + VOICE_ACTOR_FONT_SIZE + "px;");

                    voiceEntry.getChildren().addAll(charLabel, actorLabel);
                    creditsContent.getChildren().add(voiceEntry);
                }
            }

            // 添加section之间的间距
            Region sectionSpacer = new Region();
            sectionSpacer.setMinHeight(SECTION_SPACING);
            creditsContent.getChildren().add(sectionSpacer);
        }

        // 最终消息单独显示，不包含在滚动内容中

        Logger("INFO", "Credits content built with total children: " + creditsContent.getChildren().size());

        // 高度计算将在 setupInitialPositionAndStartAnimation 中进行
        // 此时 creditsContainer 还没有被添加到场景，布局计算可能不准确
        setupInitialPositionAndStartAnimation();
    }

    private Label createStyledLabel(String text, boolean isTitle, int defaultSize) {
        Label label = new Label();
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setWrapText(true);
        label.setMaxWidth(CREDITS_CONTENT_MAX_WIDTH);

        // 解析颜色标记并创建富文本
        List<Text> textNodes = parseColoredText(text, defaultSize);
        if (textNodes.size() == 1) {
            Text textNode = textNodes.get(0);
            label.setText(textNode.getText());
            // 确保样式被正确应用 - 使用完整的样式字符串
            String fontStyle = getFontStyle();
            String colorStyle = "-fx-text-fill: " + TEXT_PRIMARY + "; ";
            String sizeStyle = "-fx-font-size: " + defaultSize + "px; ";
            label.setStyle(fontStyle + colorStyle + sizeStyle);
        } else {
            // 使用TextFlow显示多色文本
            TextFlow textFlow = new TextFlow();
            textFlow.setTextAlignment(TextAlignment.CENTER);
            textFlow.getChildren().addAll(textNodes);
            label.setGraphic(textFlow);
            label.setText("");
        }

        return label;
    }

    private List<Text> parseColoredText(String text, int defaultSize) {
        if (text == null || text.isEmpty()) {
            return List.of(createDefaultText("", defaultSize));
        }

        List<Text> result = new ArrayList<>();
        int i = 0;
        String currentColor = TEXT_PRIMARY;

        while (i < text.length()) {
            int nextTagStart = text.indexOf('<', i);
            if (nextTagStart == -1) {
                // 没有更多标签，添加剩余文本
                if (i < text.length()) {
                    result.add(createStyledText(text.substring(i), currentColor, defaultSize));
                }
                break;
            }

            // 添加标签前的普通文本
            if (nextTagStart > i) {
                result.add(createStyledText(text.substring(i, nextTagStart), currentColor, defaultSize));
            }

            // 查找标签结束
            int tagEnd = text.indexOf('>', nextTagStart);
            if (tagEnd == -1) {
                // 格式错误，添加剩余文本并退出
                result.add(createStyledText(text.substring(nextTagStart), currentColor, defaultSize));
                break;
            }

            String tagContent = text.substring(nextTagStart + 1, tagEnd);

            if (tagContent.startsWith("/")) {
                // 结束标签
                String endTagName = tagContent.substring(1);
                String parsedColor = parseColorTag(endTagName);
                if (parsedColor != null) {
                    // 恢复默认颜色
                    currentColor = TEXT_PRIMARY;
                }
            } else {
                // 开始标签
                String parsedColor = parseColorTag(tagContent);
                if (parsedColor != null) {
                    currentColor = parsedColor;
                }
            }

            i = tagEnd + 1;
        }

        if (result.isEmpty()) {
            result.add(createDefaultText("", defaultSize));
        }

        return result;
    }

    private Text createTextNode(String text, String color, int size) {
        Text textNode = new Text(text);
        textNode.setFill(Color.web(color));
        textNode.setFont(fontLoaded ? Font.font(xiaolaiFont.getFamily(), size) : Font.font(size));
        return textNode;
    }

    private void fadeOutAndStartCredits() {
        StackPane contentWithOverlays = GameManager.getInstance().getContentWithOverlays();

        // 检查 creditsContainer 是否已经在 contentWithOverlays 中
        boolean creditsContainerExists = contentWithOverlays.getChildren().contains(creditsContainer);

        if (creditsContainerExists) {
            // 如果 creditsContainer 已经存在，直接开始动画，不需要淡出
            Logger("DEBUG", "Credits container already exists, starting animation directly");
            creditsContainer.setVisible(true);
            creditsContainer.setMouseTransparent(false);
            creditsContainer.toFront();
            creditsContainer.setOpacity(1.0);
            creditsContainer.requestFocus();

            // 确保 creditsContent 的宽度被正确设置，并重新计算高度
            Scene scene = GameManager.getInstance().getPrimaryStage().getScene();
            creditsContent.setPrefWidth(scene.getWidth() * 0.8);
            creditsContent.applyCss();
            creditsContent.layout();
            creditsContent.autosize();
            totalHeight = creditsContent.getBoundsInLocal().getHeight();
            Logger("DEBUG", "Final content height calculation for existing container: " + totalHeight);

            // 直接开始制作者名单动画
            startCreditsAnimation();
        } else {
            // 淡出游戏内容（creditsContainer 保持透明）
            FadeTransition fadeOutGame = new FadeTransition(Duration.seconds(FADE_DURATION), contentWithOverlays);
            fadeOutGame.setFromValue(1.0);
            fadeOutGame.setToValue(0.0);

            fadeOutGame.setOnFinished(event -> {
                contentWithOverlays.getChildren().clear();
                // 隐藏游戏内容（但保留在场景中以便恢复）
                contentWithOverlays.setVisible(false);

                // 添加 creditsContainer 到场景中，设置为透明
                contentWithOverlays.getChildren().add(creditsContainer);
                creditsContainer.setVisible(true);
                creditsContainer.setMouseTransparent(false);
                creditsContainer.toFront();
                creditsContainer.setOpacity(0.0); // 初始透明
                creditsContainer.requestFocus();  // 请求焦点以接收键盘事件

                // 获取场景引用，避免重复定义
                Scene scene = GameManager.getInstance().getPrimaryStage().getScene();

                // 强制设置容器大小，防止 StackPane 自动调整
                // 先解除之前可能的绑定
                creditsContainer.prefWidthProperty().unbind();
                creditsContainer.prefHeightProperty().unbind();
                creditsContainer.setMinSize(scene.getWidth(), scene.getHeight());
                creditsContainer.setMaxSize(scene.getWidth(), scene.getHeight());
                creditsContainer.setPrefSize(scene.getWidth(), scene.getHeight());

                // 监听场景大小变化，动态更新容器大小和SPACE指示器位置
                setupSceneSizeChangeListener(contentWithOverlays, scene);

                // 在淡入creditsContainer之前立即停止当前的背景音乐
                stopBGM();

                // 淡入 creditsContainer 显示背景
                FadeTransition fadeInContainer = new FadeTransition(Duration.seconds(FADE_DURATION / 2), creditsContainer);
                fadeInContainer.setFromValue(0.0);
                fadeInContainer.setToValue(1.0);

                fadeInContainer.setOnFinished(fadeEvent -> {
                    contentWithOverlays.setVisible(true);
                    contentWithOverlays.setOpacity(1.0);

                    Logger("INFO", "Credits container added to scene. Container visible: " + creditsContainer.isVisible() + ", Content visible: " + creditsContent.isVisible());

                    // 现在 creditsContainer 已经在场景中，重新计算高度
                    // 使用之前获取的 scene 引用
                    creditsContent.setPrefWidth(scene.getWidth() * 0.8);
                    creditsContent.applyCss();
                    creditsContent.layout();
                    creditsContent.autosize();
                    totalHeight = creditsContent.getBoundsInLocal().getHeight();
                    Logger("DEBUG", "Final content height calculation: " + totalHeight);

                    // 初始位置已在setupInitialPositionAndStartAnimation中设置
                    // 直接开始制作者名单动画
                    startCreditsAnimation();
                });

                fadeInContainer.play();
            });

            fadeOutGame.play();
        }
    }

    private void startCreditsAnimation() {
        Logger("DEBUG", "Starting credits animation...");

        // 在淡入开始时停止当前的背景音乐
        stopBGM();

        // 等待一小段时间后开始淡入内容
        Timeline delayTimeline = new Timeline(new KeyFrame(Duration.seconds(BGM_START_DELAY), event -> {
            // 确保内容完全不可见
            creditsContent.setOpacity(0.0);

            // 淡入内容
            FadeTransition fadeInContent = new FadeTransition(Duration.seconds(FADE_DURATION), creditsContent);
            fadeInContent.setFromValue(0.0);
            fadeInContent.setToValue(1.0);

            fadeInContent.setOnFinished(fadeEvent -> {
                Logger("DEBUG", "Content fade in complete, starting BGM...");
                // 淡入完成后播放BGM
                playBGM();
                // 淡入完成后开始滚动
                startScrolling();
                // 显示空格键指示器
                showIndicatorTemporarily();
            });

            fadeInContent.play();
        }));
        delayTimeline.play();
    }

    private void showFinalMessage() {
        if (finalMessage != null && !finalMessage.isEmpty()) {
            // 解析最终消息
            List<Text> textNodes = parseColoredText(finalMessage, (int) FINAL_MESSAGE_FONT_SIZE);
            if (textNodes.size() == 1) {
                finalMessageLabel.setText(textNodes.get(0).getText());
            } else {
                TextFlow textFlow = new TextFlow();
                textFlow.setTextAlignment(TextAlignment.CENTER);
                textFlow.getChildren().addAll(textNodes);
                finalMessageLabel.setGraphic(textFlow);
                finalMessageLabel.setText("");
            }

            // 显示最终消息
            finalMessageLabel.setVisible(true);
            finalMessageLabel.setOpacity(0.0);

            // 设置锚点使最终消息居中
            AnchorPane.setTopAnchor(finalMessageLabel, 0.0);
            AnchorPane.setBottomAnchor(finalMessageLabel, 0.0);
            AnchorPane.setLeftAnchor(finalMessageLabel, 0.0);
            AnchorPane.setRightAnchor(finalMessageLabel, 0.0);

            FadeTransition fadeInFinal = new FadeTransition(Duration.seconds(FINAL_MESSAGE_FADE_IN_TIME), finalMessageLabel);
            fadeInFinal.setFromValue(0.0);
            fadeInFinal.setToValue(1.0);

            fadeInFinal.setOnFinished(event -> {
                Logger("DEBUG", "Final message fade in complete, showing for " + FINAL_MESSAGE_DISPLAY_TIME + " seconds...");
                // 显示3秒后退出 - 现在正好与BGM同步结束
                Timeline showTimeline = new Timeline(new KeyFrame(Duration.seconds(FINAL_MESSAGE_DISPLAY_TIME), event2 -> {
                    Logger("DEBUG", "Final message display complete, returning to menu...");
                    fadeOutContentAndReturnToMenu();
                }));
                showTimeline.play();
            });

            fadeInFinal.play();
        } else {
            // 没有最终消息，直接返回菜单
            fadeOutContentAndReturnToMenu();
        }
    }

    private void disableGameControls() {
        try {
            // 通过WorldMap检查游戏是否正在进行
            WorldMap worldMap = GameInstance.getWorldMap();
            if (worldMap != null) {
                // 游戏正在进行，禁用控制
                InputHandler inputHandler = GameInstance.getInputHandlerStatic();
                if (inputHandler != null) {
                    inputHandler.lockControls(InputHandler.LOCK_CREDITS);
                    Logger("DEBUG", "制作者名单显示，禁用游戏控制");
                }
            }
        } catch (Exception e) {
            // 游戏可能未启动，忽略错误
        }
    }

    private void enableGameControls() {
        try {
            // 通过WorldMap检查游戏是否正在进行
            WorldMap worldMap = GameInstance.getWorldMap();
            if (worldMap != null) {
                // 游戏正在进行，启用控制
                InputHandler inputHandler = GameInstance.getInputHandlerStatic();
                if (inputHandler != null) {
                    inputHandler.unlockControls(InputHandler.LOCK_CREDITS);
                    Logger("DEBUG", "制作者名单关闭，启用游戏控制");
                }
            }
        } catch (Exception e) {
            // 游戏可能未启动，忽略错误
        }
    }

    private void startScrolling() {
        GameManager gm = GameManager.getInstance();
        if (gm == null) {
            Logger("ERROR", "GameManager not initialized");
            return;
        }

        Scene scene = gm.getPrimaryStage().getScene();

        if (scrollTimeline != null) {
            scrollTimeline.stop();
        }
        // 记录滚动开始时间
        scrollStartTime = System.currentTimeMillis();
        // 计算滚动速度：根据内容高度和BGM时长动态调整
        final double screenHeight = creditsContainer.getPrefHeight();
        Logger("DEBUG", "Starting scroll. Screen height: " + screenHeight + ", Content height: " + totalHeight + ", BGM duration: " + bgmDuration + "s");
        // 使用当前位置作为起始位置（已经在 fadeOutAndStartCredits 中设置好了）
        final double initialPosition = creditsContent.getTranslateY();
        Logger("DEBUG", "Starting scroll from position: " + initialPosition);
        // 滚动结束位置：让整个内容完全离开屏幕
        // 初始位置translateY = screenHeight，内容顶部在屏幕底部
        // 结束位置：内容底部完全离开屏幕顶部
        // 内容底部位置 = translateY + totalHeight，当translateY = -totalHeight时，内容底部在屏幕顶部
        // 要让内容底部完全离开，需要 translateY = -totalHeight - screenHeight
        // 这样滚动距离 = initialPosition - endPosition = screenHeight - (-totalHeight - screenHeight) = totalHeight + 2 * screenHeight
        final double endPosition = -totalHeight - screenHeight;
        Logger("DEBUG", "Scroll end position: " + endPosition + " (totalHeight: " + totalHeight + ", screenHeight: " + screenHeight + ")");
        // 计算滚动速度
        double scrollSpeed = DEFAULT_SCROLL_SPEED; // 默认速度
        if (bgmDuration > 0) {
            // 有 BGM 时，计算滚动速度
            // 滚动距离 = 从初始位置到结束位置的实际距离
            double totalScrollDistance = initialPosition - endPosition;
            // 实际时间线：
            // - t=0: showCredits()被调用
            // - fadeOutAndStartCredits: 淡出游戏内容（FADE_DURATION = 1.0秒）
            // - t=1.0s: 淡出完成，开始淡入creditsContainer（FADE_DURATION/2 = 0.5秒）
            // - t=1.5s: creditsContainer淡入完成，调用startCreditsAnimation()
            // - 在startCreditsAnimation中立即停止BGM
            // - 等待BGM_START_DELAY = 0.5秒
            // - t=2.0s: 开始淡入内容（FADE_DURATION = 1.0秒）
            // - t=3.0s: 内容淡入完成，播放BGM，滚动开始
            // - 滚动时间：从滚动开始到显示最终消息的时间
            // 预留时间：最终消息需要在BGM结束时显示（包含淡入+显示时间）
            double finalMessageTime = FINAL_MESSAGE_DISPLAY_TIME;
            double fadeTime = FINAL_MESSAGE_FADE_IN_TIME; // 最终消息淡入时间
            double totalMessageDisplayTime = finalMessageTime + fadeTime; // 总的最终消息时间
            // 实际可用的滚动时间 = BGM时长 - 最终消息显示时间
            double availableTime = bgmDuration - totalMessageDisplayTime;

            Logger("DEBUG", "=== Scroll Speed Calculation ===");
            Logger("DEBUG", "BGM Duration: " + bgmDuration + "s");
            Logger("DEBUG", "Total Message Display Time: " + totalMessageDisplayTime + "s");
            Logger("DEBUG", "Available Scroll Time: " + availableTime + "s");
            Logger("DEBUG", "Initial Position: " + initialPosition);
            Logger("DEBUG", "End Position: " + endPosition);
            Logger("DEBUG", "Total Scroll Distance: " + totalScrollDistance);
            Logger("DEBUG", "Screen Height: " + screenHeight);
            Logger("DEBUG", "Content Height: " + totalHeight);

            if (availableTime > 2.0) {
                // 如果有足够时间，根据可用时间计算速度
                scrollSpeed = totalScrollDistance / (availableTime * FRAME_RATE);
                Logger("DEBUG", "Calculated scroll speed: " + scrollSpeed + " pixels/frame");
                Logger("DEBUG", "Expected scroll time: " + (totalScrollDistance / (scrollSpeed * FRAME_RATE)) + "s");
                Logger("DEBUG", "Final message will show after: " + availableTime + "s from scroll start");
            } else {
                // 时间不足，使用较快速度
                scrollSpeed = 2.0;
                Logger("WARN", "BGM duration too short, using default speed: " + scrollSpeed);
            }
        }

        final double[] currentPosition = {initialPosition};
        double finalScrollSpeed = scrollSpeed;
        // 使用 Timeline 以固定帧率更新滚动位置，确保动画流畅
        scrollTimeline = new Timeline(new KeyFrame(Duration.millis(FRAME_DURATION), event -> {
            currentPosition[0] -= finalScrollSpeed;
            creditsContent.setTranslateY(currentPosition[0]);
            // 检查是否滚动完成
            if (currentPosition[0] <= endPosition) {
                Logger("DEBUG", "Scroll complete. Final position: " + currentPosition[0]);
                onScrollComplete();
            }
        }));
        scrollTimeline.setCycleCount(Animation.INDEFINITE); // 改为无限循环，手动控制停止
        scrollTimeline.play();
        // 如果有BGM时长，创建定时器在BGM结束前显示最终消息
        if (bgmDuration > 0) {
            // 计算延迟时间：从滚动开始到显示最终消息的时间
            // 确保最终消息在BGM结束时显示
            // 时间线：
            // - 滚动开始后availableTime秒：滚动完成（内容完全离开屏幕），开始显示最终消息
            // - 最终消息淡入1秒
            // - 最终消息显示3秒
            // - 总共：availableTime + 1 + 3 = bgmDuration秒
            // 计算实际滚动时间
            double actualScrollTime = (initialPosition - endPosition) / (scrollSpeed * FRAME_RATE);
            double finalMessageTime = FINAL_MESSAGE_DISPLAY_TIME;
            double fadeTime = FINAL_MESSAGE_FADE_IN_TIME;
            double totalMessageDisplayTime = finalMessageTime + fadeTime;
            double finalMessageDelay = actualScrollTime; // 滚动完成后立即显示最终消息

            Logger("DEBUG", "=== Final Message Timer ===");
            Logger("DEBUG", "Actual Scroll Time: " + actualScrollTime + "s");
            Logger("DEBUG", "Final Message Fade Time: " + fadeTime + "s");
            Logger("DEBUG", "Final Message Display Time: " + finalMessageTime + "s");
            Logger("DEBUG", "Total Message Display Time: " + totalMessageDisplayTime + "s");
            Logger("DEBUG", "Final Message Delay from scroll start: " + finalMessageDelay + "s");
            Logger("DEBUG", "Expected final message show time: " + (3.0 + finalMessageDelay) + "s from credits start");

            if (finalMessageDelay > 0) {
                finalMessageTimer = new Timeline(new KeyFrame(Duration.seconds(finalMessageDelay), event -> {
                    // 滚动可能还没完成，但我们要确保最终消息在BGM结束时显示
                    if (scrollTimeline != null && scrollTimeline.getStatus() == Animation.Status.RUNNING) {
                        // 如果滚动还在进行，停止滚动并立即显示最终消息
                        scrollTimeline.stop();
                        Logger("WARN", "Scroll stopped early at position: " + currentPosition[0] + " (expected: " + endPosition + ")");
                    }
                    showFinalMessage();
                }));
                finalMessageTimer.setCycleCount(1);
                finalMessageTimer.play();
            } else {
                Logger("WARN", "BGM duration too short, showing final message immediately after scroll completes");
            }
        }
    }


    private void onScrollComplete() {
        if (scrollTimeline != null) {
            scrollTimeline.stop();
            scrollTimeline = null;
        }
        Logger("DEBUG", "Scroll complete");
        // 滚动完成后，我们不立即显示最终消息
        // 最终消息的显示由 startScrolling 中设置的定时器控制，以确保与BGM时长同步
        Logger("DEBUG", "Scroll complete, waiting for final message timer to show final message at correct BGM timing...");

    }

    private void fadeOutContentAndReturnToMenu() {
        // 停止滚动
        if (scrollTimeline != null) {
            scrollTimeline.stop();
            scrollTimeline = null;
        }

        // 停止长按时间轴
        if (spaceHoldTimeline != null) {
            spaceHoldTimeline.stop();
            spaceHoldTimeline = null;
        }

        // 停止背景音乐
        stopBGM();

        // 淡出内容
        FadeTransition fadeOutContent = new FadeTransition(Duration.seconds(FADE_DURATION), creditsContent);
        fadeOutContent.setFromValue(1.0);
        fadeOutContent.setToValue(0.0);

        // 淡出空格键指示器
        FadeTransition fadeOutIndicator = new FadeTransition(Duration.seconds(FADE_DURATION), spaceKeyIndicator);
        fadeOutIndicator.setFromValue(0.8);
        fadeOutIndicator.setToValue(0.0);

        fadeOutContent.setOnFinished(event -> {
            // 返回到Menu
            returnToMenu();
        });

        fadeOutContent.play();
        fadeOutIndicator.play();
    }

    private void returnToMenu() {
        Platform.runLater(() -> {
            // 移除键盘事件
            Scene scene = creditsContainer.getScene();
            if (scene != null) {
                Object handler = creditsContainer.getUserData();
                if (handler instanceof EventHandler[]) {
                    EventHandler[] handlers = (EventHandler[]) handler;
                    for (EventHandler h : handlers) {
                        scene.removeEventHandler(KeyEvent.KEY_PRESSED, h);
                        scene.removeEventHandler(KeyEvent.KEY_RELEASED, h);
                    }
                }
            }

            GameManager gm = GameManager.getInstance();
            if (gm != null) {
                // 先移除creditsContainer
                gm.getContentWithOverlays().getChildren().remove(creditsContainer);

                creditsContainer.setVisible(false);
                creditsContainer.setMouseTransparent(true);
                isPlaying = false;

                // 恢复游戏内容可见性
                gm.getContentWithOverlays().setVisible(true);
                gm.getContentWithOverlays().setOpacity(1.0);

                // 显示Menu
                showMenuScene();

                Logger("INFO", "Returned to Menu from credits");
            }
        });
    }

    private void showMenuScene() {
        DialogCleanup.closeAllDialogs("章节");
        try {
            GameManager gm = GameManager.getInstance();
            if (gm == null) return;

            // 创建Menu实例并显示
            Menu menu = new Menu();
            Stage primaryStage = gm.getPrimaryStage();

            // 使用淡入效果切换到Menu
            Scene currentScene = primaryStage.getScene();

            // 创建黑色遮罩用于淡入
            Pane fadeOverlay = new Pane();
            fadeOverlay.setStyle("-fx-background-color: black;");
            fadeOverlay.setOpacity(0.0);
            fadeOverlay.prefWidthProperty().bind(currentScene.widthProperty());
            fadeOverlay.prefHeightProperty().bind(currentScene.heightProperty());

            StackPane overlayContainer = new StackPane();
            overlayContainer.getChildren().add(fadeOverlay);

            if (currentScene.getRoot() instanceof Pane root) {
                root.getChildren().add(overlayContainer);
            }

            // 淡入黑色
            FadeTransition fadeInBlack = new FadeTransition(Duration.seconds(0.5), fadeOverlay);
            fadeInBlack.setFromValue(0.0);
            fadeInBlack.setToValue(1.0);

            fadeInBlack.setOnFinished(event -> {
                // 启动Menu
                menu.start(primaryStage);
            });

            fadeInBlack.play();

        } catch (Exception e) {
            Logger("ERROR", "Failed to return to Menu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void fadeOutAndClose() {
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(FADE_DURATION * 1.5), creditsContainer);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> {
            closeCredits();
        });
        fadeOut.play();
    }

    private void setupKeyHandlers(Scene scene) {
        Logger("DEBUG", "Setting up key handlers for credits...");

        // 使用事件过滤器(Event Filter)而不是事件处理器(Handler)
        // 事件过滤器在捕获阶段处理,优先级高于事件处理器,可以确保拦截所有按键
        EventHandler<KeyEvent> keyPressFilter = e -> {
            // 放行F11键，使其传递到GameManager的全屏切换监听器
            if (e.getCode() == KeyCode.F11) {
                return; // 不consume，让事件继续传递
            }

            // 拦截所有按键，防止传递到底层游戏
            e.consume();

            Logger("DEBUG", "Key pressed: " + e.getCode());

            if (e.getCode() == KeyCode.SPACE) {
                onSpaceKeyPressed();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                // ESC键也显示指示器，但不做其他处理
                showIndicatorTemporarily();
            } else {
                // 按其他任意键显示指示器
                showIndicatorTemporarily();
            }
        };

        EventHandler<KeyEvent> keyReleaseFilter = e -> {
            // 放行F11键
            if (e.getCode() == KeyCode.F11) {
                return; // 不consume，让事件继续传递
            }

            // 拦截所有按键释放事件
            e.consume();

            if (e.getCode() == KeyCode.SPACE) {
                onSpaceKeyReleased();
            }
            // ESC键和其他键的释放不需要特殊处理
        };

        // 在 Scene 上添加事件过滤器(优先级最高)
        // 使用addEventFilter而不是addEventHandler,确保在GameInstance的过滤器之前处理
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyPressFilter);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, keyReleaseFilter);

        Logger("DEBUG", "Key filters added to scene");

        // 保存filter引用以便移除
        creditsContainer.setUserData(new EventHandler[]{keyPressFilter, keyReleaseFilter});
    }

    private void showIndicatorTemporarily() {
        // 如果进度条已经完成，不再显示指示器
        if (spaceHoldCompleted) {
            Logger("DEBUG", "Space hold already completed, skipping indicator display");
            return;
        }

        Logger("DEBUG", "Showing space key indicator temporarily. Current opacity: " + spaceKeyIndicator.getOpacity());

        Logger("DEBUG", "Container size - Width: " + creditsContainer.getWidth() +
                ", Height: " + creditsContainer.getHeight());

        // 先将指示器从容器中移除，然后重新添加到最上层
        if (creditsContainer.getChildren().contains(spaceKeyIndicator)) {
            creditsContainer.getChildren().remove(spaceKeyIndicator);
        }
        creditsContainer.getChildren().add(spaceKeyIndicator);

        // 重新应用锚点约束（因为移除后重新添加可能丢失锚点）
        AnchorPane.setRightAnchor(spaceKeyIndicator, INDICATOR_MARGIN);
        AnchorPane.setBottomAnchor(spaceKeyIndicator, INDICATOR_MARGIN);

        // 显示指示器
        spaceKeyIndicator.setOpacity(0.8);
        spaceKeyIndicator.setVisible(true);

        Logger("DEBUG", "Container children: " + creditsContainer.getChildren());
        Logger("DEBUG", "Last child (should be indicator): " + creditsContainer.getChildren().get(creditsContainer.getChildren().size() - 1));
        Logger("DEBUG", "Indicator properties - Visible: " + spaceKeyIndicator.isVisible() +
                ", Opacity: " + spaceKeyIndicator.getOpacity() +
                ", LayoutX: " + spaceKeyIndicator.getLayoutX() +
                ", LayoutY: " + spaceKeyIndicator.getLayoutY() +
                ", Managed: " + spaceKeyIndicator.isManaged() +
                ", Bounds: " + spaceKeyIndicator.getBoundsInParent() +
                ", Parent: " + spaceKeyIndicator.getParent());

        // 取消之前的隐藏计时器
        if (hideIndicatorTimeline != null) {
            hideIndicatorTimeline.stop();
        }

        // 创建新的隐藏计时器
        hideIndicatorTimeline = new Timeline(new KeyFrame(Duration.seconds(INDICATOR_SHOW_DURATION), event -> {
            // 隐藏指示器（除非正在按住空格键且进度条未完成）
            if (!isSpacePressed && !spaceHoldCompleted) {
                spaceKeyIndicator.setOpacity(0.0);
                Logger("DEBUG", "Hiding space key indicator");
            }
        }));
        hideIndicatorTimeline.play();
    }

    private void onSpaceKeyPressed() {
        if (!isPlaying) return;

        // 如果进度条已经完成，不再处理按键
        if (spaceHoldCompleted) {
            Logger("DEBUG", "Space hold already completed, ignoring key press");
            return;
        }

        // 如果已经在按住空格键，忽略重复触发
        if (isSpacePressed) {
            Logger("DEBUG", "SPACE key already pressed, ignoring repeat");
            return;
        }

        Logger("DEBUG", "SPACE key pressed, starting hold timer");

        isSpacePressed = true;
        spaceHoldProgress = 0.0;
        spaceKeyProgress.setWidth(0);

        // 确保指示器可见
        spaceKeyIndicator.setOpacity(0.8);

        // 取消隐藏计时器
        if (hideIndicatorTimeline != null) {
            hideIndicatorTimeline.stop();
        }

        if (spaceHoldTimeline != null) {
            spaceHoldTimeline.stop();
        }

        // 创建长按时间轴
        spaceHoldTimeline = new Timeline(new KeyFrame(Duration.millis(FRAME_DURATION), event -> {
            spaceHoldProgress += FRAME_DURATION;
            double progress = Math.min(spaceHoldProgress / SPACE_HOLD_DURATION, 1.0);
            spaceKeyProgress.setWidth(PROGRESS_BAR_WIDTH * progress);

            Logger("DEBUG", "Hold progress: " + progress + " (" + spaceHoldProgress + "ms)");

            if (progress >= 1.0) {
                // 长按完成，退出
                onSpaceHoldComplete();
            }
        }));
        spaceHoldTimeline.setCycleCount(Animation.INDEFINITE);
        spaceHoldTimeline.play();
    }

    private void onSpaceKeyReleased() {
        if (!isPlaying) return;

        // 如果进度条已经完成，不再处理按键释放
        if (spaceHoldCompleted) {
            Logger("DEBUG", "Space hold already completed, ignoring key release");
            return;
        }

        isSpacePressed = false;
        spaceHoldProgress = 0.0;
        spaceKeyProgress.setWidth(0);

        if (spaceHoldTimeline != null) {
            spaceHoldTimeline.stop();
            spaceHoldTimeline = null;
        }

        // 松开空格后，延迟隐藏指示器
        showIndicatorTemporarily();
    }

    private void onSpaceHoldComplete() {
        if (!isPlaying) return;

        isSpacePressed = false;
        spaceHoldCompleted = true; // 标记进度条已完成

        if (spaceHoldTimeline != null) {
            spaceHoldTimeline.stop();
            spaceHoldTimeline = null;
        }

        // 隐藏指示器
        spaceKeyIndicator.setOpacity(0.0);

        // 先淡出当前显示的字符，然后退出到Menu
        fadeOutContentAndReturnToMenu();
    }

    private void closeCredits() {
        if (!isPlaying) return;

        Platform.runLater(() -> {
            // 停止所有动画和计时器
            if (scrollTimeline != null) {
                scrollTimeline.stop();
                scrollTimeline = null;
            }

            if (spaceHoldTimeline != null) {
                spaceHoldTimeline.stop();
                spaceHoldTimeline = null;
            }

            if (hideIndicatorTimeline != null) {
                hideIndicatorTimeline.stop();
                hideIndicatorTimeline = null;
            }

            // 移除键盘事件过滤器
            Scene scene = creditsContainer.getScene();
            if (scene != null) {
                Object handler = creditsContainer.getUserData();
                if (handler instanceof EventHandler[]) {
                    EventHandler[] handlers = (EventHandler[]) handler;
                    for (EventHandler h : handlers) {
                        scene.removeEventFilter(KeyEvent.KEY_PRESSED, h);
                        scene.removeEventFilter(KeyEvent.KEY_RELEASED, h);
                    }
                }
            }

            // 从场景中移除制作者名单
            GameManager gm = GameManager.getInstance();
            if (gm != null) {
                gm.getContentWithOverlays().getChildren().remove(creditsContainer);
            }

            creditsContainer.setVisible(false);
            creditsContainer.setMouseTransparent(true);

            // 停止背景音乐
            stopBGM();

            // 恢复游戏内容
            fadeInGameContent();

            Logger("INFO", "Credits closed");
        });
    }

    private void fadeInGameContent() {
        StackPane contentWithOverlays = GameManager.getInstance().getContentWithOverlays();

        // 显示游戏内容
        contentWithOverlays.setVisible(true);
        contentWithOverlays.setOpacity(0.0);

        // 淡入动画
        FadeTransition fadeInGame = new FadeTransition(Duration.seconds(FADE_DURATION), contentWithOverlays);
        fadeInGame.setFromValue(0.0);
        fadeInGame.setToValue(1.0);

        fadeInGame.setOnFinished(event -> {
            // 启用游戏控制
            enableGameControls();
            isPlaying = false;
        });

        fadeInGame.play();
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void setScrollSpeed(double speed) {
        this.scrollSpeed = Math.max(SCROLL_SPEED_MIN, Math.min(SCROLL_SPEED_MAX, speed));
    }

    public double getScrollSpeed() {
        return scrollSpeed;
    }

    // 在内容高度计算完成后设置初始位置并开始动画
    private void setupInitialPositionAndStartAnimation() {
        // 获取场景的屏幕高度
        GameManager gm = GameManager.getInstance();
        if (gm == null) {
            Logger("ERROR", "GameManager not initialized");
            return;
        }

        Scene scene = gm.getPrimaryStage().getScene();
        if (scene == null) {
            Logger("ERROR", "Scene not available");
            return;
        }

        double screenHeight = scene.getHeight();

        // 绑定容器大小
        creditsContainer.prefWidthProperty().bind(scene.widthProperty());
        creditsContainer.prefHeightProperty().bind(scene.heightProperty());

        // 计算初始位置：屏幕高度
        // 这样VBox的顶部与屏幕底部对齐，VBox主体在屏幕下方，会从屏幕下方开始滚动进入
        double initialY = screenHeight;
        creditsContent.setTranslateY(initialY);

        Logger("DEBUG", "Initial positions set in callback - Screen height: " + screenHeight + ", Initial Y: " + initialY);
        Logger("DEBUG", "Space indicator anchors - Right: " + AnchorPane.getRightAnchor(spaceKeyIndicator) + ", Bottom: " + AnchorPane.getBottomAnchor(spaceKeyIndicator));

        // 设置ESC键退出
        setupKeyHandlers(scene);

        // 淡出其他元素并开始滚动
        fadeOutAndStartCredits();
    }

    /**
     * 更新方法（需要在外部定期调用，用于检查待启动的致谢名单）
     * 当游戏恢复且有待启动的致谢名单时，自动启动
     */
    public void update() {
        // 检查是否有待启动的致谢名单
        if (pendingCreditsStart) {
            // 检查游戏是否已恢复（未暂停）
            if (!isGamePaused()) {
                startPendingCredits();
            }
        }
    }

    /**
     * 检查游戏是否暂停
     */
    private boolean isGamePaused() {
        try {
            GameInstance instance = GameInstance.getCurrentInstance();
            if (instance == null) {
                return false;
            }
            return instance.isPaused();
        } catch (Exception e) {
            Logger("ERROR", "检查游戏暂停状态时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 启动待启动的致谢名单（游戏恢复后调用）
     */
    private void startPendingCredits() {
        if (!pendingCreditsStart) {
            return;
        }

        // 清除待启动标志
        pendingCreditsStart = false;

        // 执行实际的致谢名单启动
        performCreditsStart();

        Logger("INFO", "待启动的致谢名单已启动");
    }

    /**
     * 执行实际的致谢名单启动逻辑
     */
    private void performCreditsStart() {
        // 检查是否已经在播放
        if (isPlaying) {
            Logger("WARN", "Credits already playing");
            return;
        }

        Logger("INFO", "Starting to show credits...");

        if (!loadCreditsData()) {
            Logger("ERROR", "Failed to load credits data");
            return;
        }

        Logger("INFO", "Credits data loaded successfully. Sections: " + (sections != null ? sections.size() : 0));

        GameManager gm = GameManager.getInstance();
        if (gm == null) {
            Logger("ERROR", "GameManager not initialized");
            return;
        }

        // 确保UI组件已初始化
        if (creditsContainer == null || creditsContent == null) {
            Logger("ERROR", "UI components not initialized");
            initializeUI();
        }

        // 重置状态变量
        resetState();

        // 禁用游戏控制
        disableGameControls();

        // 播放背景音乐
        playBGM();

        Platform.runLater(() -> {
            isPlaying = true;
            Logger("INFO", "Building credits content...");
            buildCreditsContent();

            Logger("INFO", "Credits content children count: " + creditsContent.getChildren().size());

            // 其他设置将在buildCreditsContent完成后通过回调完成
        });
    }

    // 内部类：存储一个section的数据
    private static class CreditsSection {
        String title;
        List<String> content = new ArrayList<>();
        Map<String, String> Actors = new HashMap<>();
        boolean hasActors = false;

        boolean isEmpty() {
            return (title == null || title.isEmpty()) && content.isEmpty() && (!hasActors || Actors.isEmpty());
        }
    }

}