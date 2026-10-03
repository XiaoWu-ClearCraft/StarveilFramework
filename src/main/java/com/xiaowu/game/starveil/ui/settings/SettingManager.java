package com.xiaowu.game.starveil.ui.settings;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;

import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.infrastructure.audio.BGMManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.DataKey;
import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;
import com.xiaowu.game.starveil.game.state.GameManager;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import com.xiaowu.game.starveil.render.engine.RenderEngineProvider;
import com.xiaowu.game.starveil.render.engine.handles.*;
import com.xiaowu.game.starveil.render.engine.TextStyle;
import com.xiaowu.game.starveil.render.engine.ColorDef;
import com.xiaowu.game.starveil.ui.overlay.NotificationManager;
import com.xiaowu.game.starveil.ui.overlay.PopupManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置管理器 - 负责管理游戏设置界面
 * 完全使用RenderEngine渲染，不依赖JavaFX
 */
public class SettingManager {
    private final InputHandler inputHandler;
    private final Map<String, LabelHandle> keyLabels = new HashMap<>();
    private ButtonHandle waitingForInputButton = null;
    private String waitingFunction = null;
    private Runnable onCloseCallback;
    private Object currentSettingsPanel;
    private ButtonHandle activeCategoryButton = null;
    private Object mainContainer;
    private Object settingsScrollPane;

    // 当前设置的值（用于取消时恢复）
    private Map<String, Object> currentSettings = new HashMap<>();

    // 面板缓存
    private Object cachedKeyPanel = null;
    private Object cachedAudioPanel = null;
    private Object cachedDisplayPanel = null;

    private final RenderEngine renderEngine;

    public SettingManager(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
        this.renderEngine = RenderEngineProvider.getInstance().getEngine();
        initFonts();
    }

    private void initFonts() {
        renderEngine.loadFont("starveil:fonts/xiaolai-sc-regular.ttf", 32);
        renderEngine.loadFont("starveil:fonts/xiaolai-sc-regular.ttf", 20);
        renderEngine.loadFont("starveil:fonts/xiaolai-sc-regular.ttf", 16);
        renderEngine.loadFont("starveil:fonts/xiaolai-sc-regular.ttf", 14);
    }

    /**
     * 创建设置界面的根节点
     */
    public Object createRoot() {
        Object root = renderEngine.createStackPane();

        // 半透明遮罩层
        Object overlay = renderEngine.createStackPane();
        renderEngine.setBackground(overlay, "-fx-background-color: rgba(0,0,0,0.5);");
        renderEngine.setMouseTransparent(overlay, true);

        // 主容器
        mainContainer = renderEngine.createVBox(0, 0);
        renderEngine.setAlignment(mainContainer, "center");
        renderEngine.setPadding(mainContainer, 20, 20, 20, 20);
        renderEngine.setMaxWidth(mainContainer, 600);
        renderEngine.setMaxHeight(mainContainer, 550);
        renderEngine.setBackground(mainContainer,
            "-fx-background-color: rgba(255, 192, 203, 0.95); " +
            "-fx-background-radius: 20; " +
            "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 20, 0, 0, 5);");

        // 标题
        LabelHandle titleHandle = renderEngine.createLabel(
            "设置",
            TextStyle.title(32, renderEngine.createColor("#FF69B4"))
        );
        Object titleLabel = titleHandle.getNativeHandle();

        // 分类按钮区域
        Object categoryBox = renderEngine.createHBox(15, 0);
        renderEngine.setAlignment(categoryBox, "center");
        renderEngine.setPadding(categoryBox, 20, 0, 20, 0);

        ButtonHandle keyButton = createCategoryButton("按键", true);
        ButtonHandle audioButton = createCategoryButton("音频", false);
        ButtonHandle displayButton = createCategoryButton("显示", false);

        renderEngine.addChild(categoryBox, keyButton.getNativeHandle());
        renderEngine.addChild(categoryBox, audioButton.getNativeHandle());
        renderEngine.addChild(categoryBox, displayButton.getNativeHandle());

        // 创建滚动区域
        settingsScrollPane = renderEngine.createScrollPane();
        renderEngine.setPrefHeight(settingsScrollPane, 320);
        renderEngine.setMaxHeight(settingsScrollPane, 320);
        renderEngine.setScrollPaneFitToWidth(settingsScrollPane, true);
        renderEngine.setScrollPaneBarPolicy(settingsScrollPane, "never", "as_needed");
        renderEngine.setBackground(settingsScrollPane,
            "-fx-background-color: transparent; -fx-background: transparent;");

        // 设置内容区域
        if (cachedKeyPanel == null) {
            cachedKeyPanel = createKeySettingsPanel();
        }
        currentSettingsPanel = cachedKeyPanel;
        renderEngine.setScrollPaneContent(settingsScrollPane, currentSettingsPanel);

        // 底部按钮
        Object bottomBox = renderEngine.createHBox(15, 0);
        renderEngine.setAlignment(bottomBox, "center_right");
        renderEngine.setPadding(bottomBox, 20, 0, 0, 0);

        ButtonHandle cancelButton = createStyledButton("取消", renderEngine.createColor("#A9A9A9"));
        renderEngine.setButtonAction(cancelButton, () -> closeWithoutSave());

        ButtonHandle applyButton = createStyledButton("应用", renderEngine.createColor("#FFA500"));
        renderEngine.setButtonAction(applyButton, () -> applySettings());

        ButtonHandle confirmButton = createStyledButton("确定", renderEngine.createColor("#FF69B4"));
        renderEngine.setButtonAction(confirmButton, () -> saveAndClose());

        renderEngine.addChild(bottomBox, cancelButton.getNativeHandle());
        renderEngine.addChild(bottomBox, applyButton.getNativeHandle());
        renderEngine.addChild(bottomBox, confirmButton.getNativeHandle());

        renderEngine.addChild(mainContainer, titleLabel);
        renderEngine.addChild(mainContainer, categoryBox);
        renderEngine.addChild(mainContainer, settingsScrollPane);
        renderEngine.addChild(mainContainer, bottomBox);

        renderEngine.addChild(root, overlay);
        renderEngine.addChild(root, mainContainer);
        renderEngine.setAlignment(mainContainer, "center");

        // 淡入动画
        AnimationHandle fadeIn = renderEngine.createFadeIn(mainContainer, 300, 0, 1);
        renderEngine.playAnimation(fadeIn);

        return root;
    }

    public void setOnClose(Runnable callback) {
        this.onCloseCallback = callback;
    }

    public void saveAndClose() {
        saveSettings();
        AnimationHandle fadeOut = renderEngine.createFadeOut(mainContainer, 200, 1, 0);
        fadeOut.setOnComplete(() -> {
            if (onCloseCallback != null) onCloseCallback.run();
        });
        renderEngine.playAnimation(fadeOut);
    }

    public void applySettings() {
        saveSettings();
    }

    public void closeWithoutSave() {
        restoreSettings();
        AnimationHandle fadeOut = renderEngine.createFadeOut(mainContainer, 200, 1, 0);
        fadeOut.setOnComplete(() -> {
            if (onCloseCallback != null) onCloseCallback.run();
        });
        renderEngine.playAnimation(fadeOut);
    }

    public void close() {
        saveAndClose();
    }

    private ButtonHandle createCategoryButton(String text, boolean isActive) {
        ButtonHandle handle = renderEngine.createButton(
            text,
            new TextStyle("System", 16, false, false,
                isActive ? renderEngine.createColor("#FFFFFF") : renderEngine.createColor("#FF69B4"),
                0, 1.5),
            isActive ? renderEngine.createColor(ContentConfig.primaryColor()) : null,
            null,
            20
        );
        renderEngine.setSize(handle.getNativeHandle(), 100, 40);

        if (!isActive) {
            // 非激活状态：透明背景+边框
            renderEngine.setBackground(handle.getNativeHandle(),
                "-fx-background-color: transparent; " +
                "-fx-border-color: " + ContentConfig.primaryColor() + "; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 20;");
        }

        if (isActive) {
            activeCategoryButton = handle;
        }

        // 保存分类信息到用户数据
        handle.getNativeHandle();
        renderEngine.setUserData(handle.getNativeHandle(), text);

        renderEngine.setButtonAction(handle, () -> switchCategory(handle, text));
        return handle;
    }

    private void switchCategory(ButtonHandle clickedButton, String category) {
        if (clickedButton == activeCategoryButton) return;

        updateCategoryButtonStyle(clickedButton, true);
        updateCategoryButtonStyle(activeCategoryButton, false);
        activeCategoryButton = clickedButton;

        switch (category) {
            case "按键":
                if (cachedKeyPanel == null) cachedKeyPanel = createKeySettingsPanel();
                switchSettingsPanel(cachedKeyPanel);
                break;
            case "音频":
                if (cachedAudioPanel == null) cachedAudioPanel = createAudioSettingsPanel();
                switchSettingsPanel(cachedAudioPanel);
                break;
            case "显示":
                if (cachedDisplayPanel == null) cachedDisplayPanel = createDisplaySettingsPanel();
                switchSettingsPanel(cachedDisplayPanel);
                break;
        }
    }

    private void updateCategoryButtonStyle(ButtonHandle handle, boolean isActive) {
        renderEngine.setButtonAction(handle, () -> {
            String category = (String) renderEngine.getUserData(handle.getNativeHandle());
            if (category != null) switchCategory(handle, category);
        });

        if (isActive) {
            renderEngine.setBackground(handle.getNativeHandle(),
                "-fx-background-color: " + ContentConfig.primaryColor() + "; " +
                "-fx-background-radius: 20;");
        } else {
            renderEngine.setBackground(handle.getNativeHandle(),
                "-fx-background-color: transparent; " +
                "-fx-border-color: " + ContentConfig.primaryColor() + "; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 20;");
        }
    }

    private void switchSettingsPanel(Object newPanel) {
        currentSettingsPanel = newPanel;
        renderEngine.setScrollPaneContent(settingsScrollPane, newPanel);
    }

    private Object createKeySettingsPanel() {
        Object panel = renderEngine.createVBox(15, 20);
        renderEngine.setAlignment(panel, "center");
        renderEngine.setMaxWidth(panel, 550);
        renderEngine.setBackground(panel,
            "-fx-background-color: rgba(255, 255, 255, 0.7); -fx-background-radius: 15;");

        LabelHandle panelTitleHandle = renderEngine.createLabel(
            "按键绑定",
            TextStyle.title(20, renderEngine.createColor("#FF69B4"))
        );

        Object keyGrid = renderEngine.createGridPane(20, 15);
        renderEngine.setAlignment(keyGrid, "center");

        addKeyBindingRow(keyGrid, 0, "向上移动", "MOVE_UP");
        addKeyBindingRow(keyGrid, 1, "向下移动", "MOVE_DOWN");
        addKeyBindingRow(keyGrid, 2, "向左移动", "MOVE_LEFT");
        addKeyBindingRow(keyGrid, 3, "向右移动", "MOVE_RIGHT");
        addKeyBindingRow(keyGrid, 4, "加速", "ACCELERATE");
        addKeyBindingRow(keyGrid, 5, "交互", "INTERACT");
        addKeyBindingRow(keyGrid, 6, "法阵攻击", "MAGIC_ATTACK");
        addKeyBindingRow(keyGrid, 7, "背包", "BACKPACK");

        renderEngine.addChild(panel, panelTitleHandle.getNativeHandle());
        renderEngine.addChild(panel, keyGrid);
        return panel;
    }

    private void addKeyBindingRow(Object grid, int row, String label, String function) {
        LabelHandle nameLabelHandle = renderEngine.createLabel(
            label,
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        LabelHandle keyLabelHandle = renderEngine.createLabel(
            getKeyName(function),
            new TextStyle("System", 14, true, false, renderEngine.createColor("#FF69B4"), 0, 1.5)
        );
        renderEngine.setPrefWidth(keyLabelHandle.getNativeHandle(), 120);
        renderEngine.setAlignment(keyLabelHandle.getNativeHandle(), "center");
        renderEngine.setBackground(keyLabelHandle.getNativeHandle(),
            "-fx-background-color: #FFE4E1; " +
            "-fx-background-radius: 10; " +
            "-fx-padding: 8;");

        keyLabels.put(function, keyLabelHandle);

        ButtonHandle rebindButton = renderEngine.createButton(
            "更改",
            TextStyle.simple(14, renderEngine.createColor("#FFFFFF")),
            renderEngine.createColor(ContentConfig.primaryColor()),
            renderEngine.createColor(ContentConfig.secondaryColor()),
            10
        );
        renderEngine.setSize(rebindButton.getNativeHandle(), 80, 35);
        renderEngine.setButtonAction(rebindButton, () -> startKeyBinding(function, rebindButton));

        renderEngine.addToGrid(grid, nameLabelHandle.getNativeHandle(), 0, row);
        renderEngine.addToGrid(grid, keyLabelHandle.getNativeHandle(), 1, row);
        renderEngine.addToGrid(grid, rebindButton.getNativeHandle(), 2, row);
    }

    private void startKeyBinding(String function, ButtonHandle button) {
        if (waitingForInputButton != null && waitingForInputButton != button) {
            renderEngine.setButtonAction(waitingForInputButton, () -> {
                String f = (String) renderEngine.getUserData(waitingForInputButton.getNativeHandle());
                if (f != null) startKeyBinding(f, waitingForInputButton);
            });
            renderEngine.setBackground(waitingForInputButton.getNativeHandle(),
                "-fx-background-color: " + ContentConfig.primaryColor() + "; " +
                "-fx-background-radius: 10;");
        }

        waitingForInputButton = button;
        waitingFunction = function;

        renderEngine.setUserData(button.getNativeHandle(), "等待按键");
        renderEngine.setBackground(button.getNativeHandle(),
            "-fx-background-color: #FFB6C1; -fx-background-radius: 10;");
    }

    public void handleKeyBinding(int keyCodeOrdinal) {
        if (waitingForInputButton == null || waitingFunction == null) return;

        // 使用javafx KeyCode获取名称
        javafx.scene.input.KeyCode fxKeyCode = javafx.scene.input.KeyCode.values()[keyCodeOrdinal];
        inputHandler.remapKey(waitingFunction, keyCodeOrdinal);

        LabelHandle keyLabel = keyLabels.get(waitingFunction);
        if (keyLabel != null) {
            renderEngine.setLabelText(keyLabel, fxKeyCode.getName());
        }

        renderEngine.setBackground(waitingForInputButton.getNativeHandle(),
            "-fx-background-color: " + ContentConfig.primaryColor() + "; " +
            "-fx-background-radius: 10;");
        waitingForInputButton = null;
        waitingFunction = null;
    }

    private String getKeyName(String function) {
        int keyCode = inputHandler.getKeyForFunction(function);
        return javafx.scene.input.KeyCode.values()[keyCode].getName();
    }

    /**
     * 设置键盘事件处理（供外部调用）
     * 注意：此方法内部使用JavaFX Scene，仅用于向后兼容
     * 未来应通过RenderEngine的事件系统替代
     */
    public void setupScene(javafx.scene.Scene scene) {
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (waitingForInputButton != null && waitingFunction != null) {
                handleKeyBinding(e.getCode().ordinal());
                e.consume();
            }
        });
    }

    private Object createDisplaySettingsPanel() {
        Object panel = renderEngine.createVBox(15, 20);
        renderEngine.setAlignment(panel, "center");
        renderEngine.setMaxWidth(panel, 550);
        renderEngine.setBackground(panel,
            "-fx-background-color: rgba(255, 255, 255, 0.7); -fx-background-radius: 15;");

        LabelHandle panelTitleHandle = renderEngine.createLabel(
            "显示设置",
            TextStyle.title(20, renderEngine.createColor("#FF69B4"))
        );

        Object displayGrid = renderEngine.createGridPane(20, 15);
        renderEngine.setAlignment(displayGrid, "center");

        // 比例设置
        LabelHandle ratioLabelHandle = renderEngine.createLabel(
            "画面比例",
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        ComboBoxHandle ratioComboHandle = renderEngine.createComboBox(
            List.of("4:3", "16:9", "16:10", "21:9"),
            200
        );
        renderEngine.setComboBoxSelectedValue(ratioComboHandle, GameManager.getInstance().getAspectRatioString());
        renderEngine.setBackground(ratioComboHandle.getNativeHandle(),
            "-fx-background-color: #FFE4E1; -fx-background-radius: 10;");

        renderEngine.setComboBoxOnAction(ratioComboHandle, () -> {
            String selectedRatio = renderEngine.getComboBoxSelectedValue(ratioComboHandle);
            // 只暂存选择，点击“应用/确定”时才真正应用，避免改比例立刻重建并把面板关掉
            currentSettings.put("aspectRatio", selectedRatio);
        });

        renderEngine.addToGrid(displayGrid, ratioLabelHandle.getNativeHandle(), 0, 0);
        renderEngine.addToGrid(displayGrid, ratioComboHandle.getNativeHandle(), 1, 0);

        // 全屏设置
        LabelHandle fullscreenLabelHandle = renderEngine.createLabel(
            "全屏模式",
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        CheckBoxHandle fullscreenHandle = renderEngine.createCheckBox("");
        renderEngine.setCheckBoxSelected(fullscreenHandle, GameManager.getInstance().isFullscreen());
        renderEngine.addCheckBoxListener(fullscreenHandle, (oldVal, newVal) -> {
            currentSettings.put("fullscreen", newVal);
            GameManager.getInstance().setFullscreen(newVal);
        });

        renderEngine.addToGrid(displayGrid, fullscreenLabelHandle.getNativeHandle(), 0, 1);
        renderEngine.addToGrid(displayGrid, fullscreenHandle.getNativeHandle(), 1, 1);

        // 全屏方式设置
        LabelHandle fullscreenModeLabelHandle = renderEngine.createLabel(
            "全屏方式",
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        ComboBoxHandle fullscreenModeHandle = renderEngine.createComboBox(
            List.of("无边框窗口", "全屏"),
            200
        );
        String currentFullscreenMode = FrameworkDataKeys.FULLSCREEN_MODE.get();
        renderEngine.setComboBoxSelectedValue(fullscreenModeHandle, currentFullscreenMode);
        renderEngine.setBackground(fullscreenModeHandle.getNativeHandle(),
            "-fx-background-color: #FFE4E1; -fx-background-radius: 10;");

        renderEngine.setComboBoxOnAction(fullscreenModeHandle, () -> {
            String selectedMode = renderEngine.getComboBoxSelectedValue(fullscreenModeHandle);
            currentSettings.put("fullscreenMode", selectedMode);
        });

        renderEngine.addToGrid(displayGrid, fullscreenModeLabelHandle.getNativeHandle(), 0, 2);
        renderEngine.addToGrid(displayGrid, fullscreenModeHandle.getNativeHandle(), 1, 2);

        // 文本速度设置
        LabelHandle textSpeedLabelHandle = renderEngine.createLabel(
            "文本速度",
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );
        int currentSpeed = FrameworkDataKeys.TEXT_SPEED.get();
        SliderHandle textSpeedHandle = renderEngine.createSlider(10, 200, currentSpeed, 200);
        renderEngine.setSliderTickConfig(textSpeedHandle, true, false, 50, 4, false);
        renderEngine.setBackground(textSpeedHandle.getNativeHandle(),
            "-fx-background-color: #FFE4E1; -fx-background-radius: 10;");
        renderEngine.addSliderListener(textSpeedHandle, (oldVal, newVal) -> {
            int val = newVal.intValue();
            currentSettings.put(GameConstants.SETTING_TEXT_SPEED, val);
            FrameworkDataKeys.TEXT_SPEED.setInt(val);
        });

        // 显示当前速度值的标签
        LabelHandle textSpeedValueHandle = renderEngine.createLabel(
            currentSpeed + "ms",
            TextStyle.simple(12, renderEngine.createColor("#8B4513"))
        );
        renderEngine.addSliderListener(textSpeedHandle, (oldVal, newVal) -> {
            renderEngine.setLabelText(textSpeedValueHandle, String.format("%.0fms", newVal));
        });

        renderEngine.addToGrid(displayGrid, textSpeedLabelHandle.getNativeHandle(), 0, 3);
        renderEngine.addToGrid(displayGrid, textSpeedHandle.getNativeHandle(), 1, 3);
        renderEngine.addToGrid(displayGrid, textSpeedValueHandle.getNativeHandle(), 2, 3);

        renderEngine.addChild(panel, panelTitleHandle.getNativeHandle());
        renderEngine.addChild(panel, displayGrid);
        return panel;
    }

    private Object createAudioSettingsPanel() {
        Object panel = renderEngine.createVBox(15, 20);
        renderEngine.setAlignment(panel, "center");
        renderEngine.setMaxWidth(panel, 550);
        renderEngine.setBackground(panel,
            "-fx-background-color: rgba(255, 255, 255, 0.7); -fx-background-radius: 15;");

        LabelHandle panelTitleHandle = renderEngine.createLabel(
            "音频设置",
            TextStyle.title(20, renderEngine.createColor("#FF69B4"))
        );

        Object audioGrid = renderEngine.createGridPane(20, 15);
        renderEngine.setAlignment(audioGrid, "center");

        // 背景音乐音量
        LabelHandle bgmLabelHandle = renderEngine.createLabel(
            "背景音乐音量",
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        double bgmVolume = AudioManager.getBackgroundMusicVolume();
        SliderHandle bgmSliderHandle = renderEngine.createSlider(0, 100, bgmVolume * 100, 200);
        renderEngine.setSliderTickConfig(bgmSliderHandle, true, true, 25, 4, true);

        LabelHandle bgmValueLabelHandle = renderEngine.createLabel(
            String.format("%.0f%%", bgmVolume * 100),
            new TextStyle("System", 14, false, false, renderEngine.createColor("#FF69B4"), 0, 1.5)
        );

        renderEngine.addSliderListener(bgmSliderHandle, (oldVal, newVal) -> {
            double volume = newVal / 100.0;
            currentSettings.put("bgmVolume", volume);
            AudioManager.setBackgroundMusicVolumeGlobal(volume);
            renderEngine.setLabelText(bgmValueLabelHandle, String.format("%.0f%%", newVal));
        });

        Object bgmSliderBox = renderEngine.createHBox(10, 0);
        renderEngine.addChild(bgmSliderBox, bgmSliderHandle.getNativeHandle());
        renderEngine.addChild(bgmSliderBox, bgmValueLabelHandle.getNativeHandle());

        renderEngine.addToGrid(audioGrid, bgmLabelHandle.getNativeHandle(), 0, 0);
        renderEngine.addToGrid(audioGrid, bgmSliderBox, 1, 0);

        // 音效音量
        LabelHandle sfxLabelHandle = renderEngine.createLabel(
            "音效音量",
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        double sfxVolume = AudioManager.getSoundEffectsVolume();
        SliderHandle sfxSliderHandle = renderEngine.createSlider(0, 100, sfxVolume * 100, 200);
        renderEngine.setSliderTickConfig(sfxSliderHandle, true, true, 25, 4, true);

        LabelHandle sfxValueLabelHandle = renderEngine.createLabel(
            String.format("%.0f%%", sfxVolume * 100),
            new TextStyle("System", 14, false, false, renderEngine.createColor("#FF69B4"), 0, 1.5)
        );

        renderEngine.addSliderListener(sfxSliderHandle, (oldVal, newVal) -> {
            double volume = newVal / 100.0;
            currentSettings.put("sfxVolume", volume);
            AudioManager.setSoundEffectsVolumeGlobal(volume);
            renderEngine.setLabelText(sfxValueLabelHandle, String.format("%.0f%%", newVal));
        });

        Object sfxSliderBox = renderEngine.createHBox(10, 0);
        renderEngine.addChild(sfxSliderBox, sfxSliderHandle.getNativeHandle());
        renderEngine.addChild(sfxSliderBox, sfxValueLabelHandle.getNativeHandle());

        renderEngine.addToGrid(audioGrid, sfxLabelHandle.getNativeHandle(), 0, 1);
        renderEngine.addToGrid(audioGrid, sfxSliderBox, 1, 1);

        // BGM间隔时间
        LabelHandle bgmIntervalLabelHandle = renderEngine.createLabel(
            "BGM 间隔时间 (秒)",
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        long bgmInterval = BGMManager.getInstance().getInterval() / 1000;
        SliderHandle bgmIntervalSliderHandle = renderEngine.createSlider(10, 300, bgmInterval, 200);
        renderEngine.setSliderTickConfig(bgmIntervalSliderHandle, true, true, 60, 5, true);

        LabelHandle bgmIntervalValueLabelHandle = renderEngine.createLabel(
            String.format("%.0f秒", (double) bgmInterval),
            new TextStyle("System", 14, false, false, renderEngine.createColor("#FF69B4"), 0, 1.5)
        );

        renderEngine.addSliderListener(bgmIntervalSliderHandle, (oldVal, newVal) -> {
            long interval = (long) (newVal * 1000);
            currentSettings.put("bgmInterval", interval);
            BGMManager.getInstance().setInterval(interval);
            renderEngine.setLabelText(bgmIntervalValueLabelHandle, String.format("%.0f秒", newVal));
        });

        Object bgmIntervalSliderBox = renderEngine.createHBox(10, 0);
        renderEngine.addChild(bgmIntervalSliderBox, bgmIntervalSliderHandle.getNativeHandle());
        renderEngine.addChild(bgmIntervalSliderBox, bgmIntervalValueLabelHandle.getNativeHandle());

        renderEngine.addToGrid(audioGrid, bgmIntervalLabelHandle.getNativeHandle(), 0, 2);
        renderEngine.addToGrid(audioGrid, bgmIntervalSliderBox, 1, 2);

        renderEngine.addChild(panel, panelTitleHandle.getNativeHandle());
        renderEngine.addChild(panel, audioGrid);
        return panel;
    }

    private ButtonHandle createStyledButton(String text, ColorDef color) {
        ButtonHandle handle = renderEngine.createButton(
            text,
            TextStyle.simple(16, renderEngine.createColor("#FFFFFF")),
            color,
            color, // TODO: 添加hover颜色
            20
        );
        renderEngine.setSize(handle.getNativeHandle(), 120, 40);
        return handle;
    }

    private void saveSettings() {
        // 比例：优先用设置面板里选择的值，未改动则沿用当前值；点击 应用/确定 时才应用
        String aspectRatio = (String) currentSettings.get("aspectRatio");
        if (aspectRatio == null || aspectRatio.isEmpty()) {
            aspectRatio = GameManager.getInstance().getAspectRatioString();
        }
        FrameworkDataKeys.ASPECT_RATIO.set(aspectRatio);
        GameManager.getInstance().setAspectRatio(aspectRatio);

        FrameworkDataKeys.FULLSCREEN.set(GameManager.getInstance().isFullscreen());

        String fullscreenMode = (String) currentSettings.get("fullscreenMode");
        if (fullscreenMode != null) {
            FrameworkDataKeys.FULLSCREEN_MODE.set(fullscreenMode);
        }

        FrameworkDataKeys.BGM_VOLUME.set(AudioManager.getBackgroundMusicVolume());
        FrameworkDataKeys.SFX_VOLUME.set(AudioManager.getSoundEffectsVolume());

        saveKeyBindings();
        NotificationManager.getInstance()
                .showNotification("设置已加载", "已将设置应用到您的游戏", null, 5);
        LoggerManager.Logger("INFO", "设置已保存");
    }

    private void loadSettings() {
        GameManager.getInstance().setAspectRatio(FrameworkDataKeys.ASPECT_RATIO.get());
        GameManager.getInstance().setFullscreen(FrameworkDataKeys.FULLSCREEN.get());

        currentSettings.put("fullscreenMode", FrameworkDataKeys.FULLSCREEN_MODE.get());

        AudioManager.setBackgroundMusicVolumeGlobal(FrameworkDataKeys.BGM_VOLUME.get());
        AudioManager.setSoundEffectsVolumeGlobal(FrameworkDataKeys.SFX_VOLUME.get());

        String[] keyFunctions = {"MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT", "ACCELERATE", "INTERACT", "MAGIC_ATTACK", "BACKPACK"};
        for (String function : keyFunctions) {
            DataKey<Integer> binding = keyBinding(function);
            if (binding != null && binding.isSet()) {
                inputHandler.remapKey(function, binding.getInt());
            }
        }

        LoggerManager.Logger("INFO", "设置已加载");
    }

    private void restoreSettings() {
        loadSettings();
    }

    private void saveKeyBindings() {
        String[] keyFunctions = {"MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT", "ACCELERATE", "INTERACT", "MAGIC_ATTACK", "BACKPACK"};
        for (String function : keyFunctions) {
            DataKey<Integer> binding = keyBinding(function);
            if (binding == null) {
                continue;
            }
            binding.setInt(inputHandler.getKeyForFunction(function));
        }
    }

    /**
     * 取按键绑定对应的数据键。
     *
     * <p>绑定键是<b>逐个注册</b>的（见 {@code FrameworkDataKeys.KEY_BIND_*}
     * 与 {@code SettingManagerDataKeys}）：只有注册过的键才能读写，
     * 因此这里必须把功能名映射到具体键，不能再靠字符串拼一个键名出来。
     *
     * @return 对应键；功能名未知时返回 {@code null} 并告警
     */
    private static DataKey<Integer> keyBinding(String function) {
        return switch (function) {
            case "MOVE_UP" -> FrameworkDataKeys.KEY_BIND_MOVE_UP;
            case "MOVE_DOWN" -> FrameworkDataKeys.KEY_BIND_MOVE_DOWN;
            case "MOVE_LEFT" -> FrameworkDataKeys.KEY_BIND_MOVE_LEFT;
            case "MOVE_RIGHT" -> FrameworkDataKeys.KEY_BIND_MOVE_RIGHT;
            case "ACCELERATE" -> FrameworkDataKeys.KEY_BIND_ACCELERATE;
            case "INTERACT" -> FrameworkDataKeys.KEY_BIND_INTERACT;
            case "MAGIC_ATTACK" -> FrameworkDataKeys.KEY_BIND_MAGIC_ATTACK;
            case "BACKPACK" -> FrameworkDataKeys.KEY_BIND_BACKPACK;
            default -> {
                LoggerManager.Logger("WARNING", "未知的按键功能名，无法保存绑定: " + function);
                yield null;
            }
        };
    }
}
