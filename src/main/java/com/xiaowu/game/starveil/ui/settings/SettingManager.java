package com.xiaowu.game.starveil.ui.settings;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;

import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import com.xiaowu.game.starveil.infrastructure.audio.BGMManager;
import com.xiaowu.game.starveil.infrastructure.i18n.I18n;
import com.xiaowu.game.starveil.infrastructure.i18n.LanguageSettings;
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
import java.util.LinkedHashMap;
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

    /**
     * {@link #createRoot()} 返回的根节点。
     *
     * <p>留着它是为了让调用方在「重建界面」时能把旧层摘下来 ——
     * 只靠创建时的局部变量，切换语言后就会找不到该移除谁。
     */
    private Object root;

    /**
     * 语言切换后的回调。
     *
     * <p>切换语言要重建整个界面（所有文字都变了），而「重建」必须由持有设置层
     * 父节点的调用方来做 —— 本类只负责把新语言落盘并通知出去。
     */
    private java.util.function.Consumer<String> onLanguageChanged;

    // 当前设置的值（用于取消时恢复）
    private Map<String, Object> currentSettings = new HashMap<>();

    /**
     * 打开面板时的文本速度。
     *
     * <p>这一项在编辑期间就即时生效（拖滑杆立刻看到效果），因此取消时需要回滚。
     * 回滚值必须是「打开面板那一刻」的值，不能现读配置 —— 拖滑杆时它已经被改过了。
     */
    private int initialTextSpeed = FrameworkDataKeys.TEXT_SPEED.get();

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
        this.root = root;

        // 半透明遮罩层
        Object overlay = renderEngine.createStackPane();
        renderEngine.setBackground(overlay, "-fx-background-color: rgba(0,0,0,0.5);");
        renderEngine.setMouseTransparent(overlay, true);

        // 面板缓存作废：语言切换会重建整个界面，缓存里的文字是旧语言的
        cachedKeyPanel = null;
        cachedAudioPanel = null;
        cachedDisplayPanel = null;

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
            tr("framework.setting.title", "设置"),
            TextStyle.title(32, renderEngine.createColor("#FF69B4"))
        );
        Object titleLabel = titleHandle.getNativeHandle();

        // 分类按钮区域
        Object categoryBox = renderEngine.createHBox(15, 0);
        renderEngine.setAlignment(categoryBox, "center");
        renderEngine.setPadding(categoryBox, 20, 0, 20, 0);

        ButtonHandle keyButton = createCategoryButton(CATEGORY_KEYS, true);
        ButtonHandle audioButton = createCategoryButton(CATEGORY_AUDIO, false);
        ButtonHandle displayButton = createCategoryButton(CATEGORY_DISPLAY, false);

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

        ButtonHandle cancelButton = createStyledButton(tr("framework.setting.cancel", "取消"), renderEngine.createColor("#A9A9A9"));
        renderEngine.setButtonAction(cancelButton, () -> closeWithoutSave());

        ButtonHandle applyButton = createStyledButton(tr("framework.setting.apply", "应用"), renderEngine.createColor("#FFA500"));
        renderEngine.setButtonAction(applyButton, () -> applySettings());

        ButtonHandle confirmButton = createStyledButton(tr("framework.setting.confirm", "确定"), renderEngine.createColor("#FF69B4"));
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

    /** {@link #createRoot()} 建出来的根节点；还没建时为 {@code null}。 */
    public Object getRootOrNull() {
        return root;
    }

    /**
     * 设置语言切换回调。
     *
     * <p>回调参数是切换后的语言代码。调用方应当<b>重建整个设置界面</b>
     * （所有文字都变了，只换几处文本会留下一半旧语言一半新语言的界面），
     * 并且要保证重建时拿到的 {@code I18n} 已经是新语言。
     */
    public void setOnLanguageChanged(java.util.function.Consumer<String> callback) {
        this.onLanguageChanged = callback;
    }

    /**
     * 仅测试使用：把某个分类的面板准备好并显示出来。
     *
     * <p>正常流程是玩家点分类按钮，但那需要模拟鼠标事件、还会动到按钮样式。
     * 测试要验的是「分类里的内容对不对」，直接换面板更直接，也不会因为
     * 按钮样式的改动而误报。
     *
     * @param category 分类标识（{@code framework.setting.tab.*}）
     */
    void showPanelForTest(String category) {
        switch (category) {
            case CATEGORY_KEYS -> {
                if (cachedKeyPanel == null) cachedKeyPanel = createKeySettingsPanel();
                switchSettingsPanel(cachedKeyPanel);
            }
            case CATEGORY_AUDIO -> {
                if (cachedAudioPanel == null) cachedAudioPanel = createAudioSettingsPanel();
                switchSettingsPanel(cachedAudioPanel);
            }
            case CATEGORY_DISPLAY -> {
                if (cachedDisplayPanel == null) cachedDisplayPanel = createDisplaySettingsPanel();
                switchSettingsPanel(cachedDisplayPanel);
            }
            default -> LoggerManager.Logger("WARNING", "未知的设置分类: " + category);
        }
    }

    /**
     * 取界面文案：先查语言表，查不到就用给定的中文原文。
     *
     * <p>为什么不像别处那样直接用 {@code I18n.get(key)}：那个方法找不到键时返回
     * <b>键名本身</b>，界面会显示一串 {@code framework.setting.title}。
     * 设置界面是玩家一定会看到的界面，宁可回退到写死的中文原文，也不要露键名。
     */
    private static String tr(String key, String fallback) {
        return I18n.getInstance().getOrDefault(key, fallback);
    }

    public void saveAndClose() {
        saveSettings();
        AnimationHandle fadeOut = renderEngine.createFadeOut(mainContainer, 200, 1, 0);
        fadeOut.setOnComplete(() -> {
            // 「关掉了」这件事在动画结束后才广播：此时界面确实已经不在了
            com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents.settingsClosed(true);
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
            com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents.settingsClosed(false);
            if (onCloseCallback != null) onCloseCallback.run();
        });
        renderEngine.playAnimation(fadeOut);
    }

    public void close() {
        saveAndClose();
    }

    /** 分类标识（同时是按钮文案的翻译键后缀）。 */
    private static final String CATEGORY_KEYS = "framework.setting.tab.keys";
    private static final String CATEGORY_AUDIO = "framework.setting.tab.audio";
    private static final String CATEGORY_DISPLAY = "framework.setting.tab.display";

    /** 分类标识 → 中文原文（翻译缺失时的回退）。 */
    private static final Map<String, String> CATEGORY_FALLBACK = Map.of(
            CATEGORY_KEYS, "按键",
            CATEGORY_AUDIO, "音频",
            CATEGORY_DISPLAY, "显示");

    private ButtonHandle createCategoryButton(String category, boolean isActive) {
        ButtonHandle handle = renderEngine.createButton(
            tr(category, CATEGORY_FALLBACK.getOrDefault(category, category)),
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

        // userData 存的是【分类标识】而不是按钮上的文字：
        // 文字会随语言变，分类标识不会
        renderEngine.setUserData(handle.getNativeHandle(), category);

        renderEngine.setButtonAction(handle, () -> switchCategory(handle, category));
        return handle;
    }

    private void switchCategory(ButtonHandle clickedButton, String category) {
        if (clickedButton == activeCategoryButton) return;

        updateCategoryButtonStyle(clickedButton, true);
        updateCategoryButtonStyle(activeCategoryButton, false);
        activeCategoryButton = clickedButton;

        switch (category) {
            case CATEGORY_KEYS:
                if (cachedKeyPanel == null) cachedKeyPanel = createKeySettingsPanel();
                switchSettingsPanel(cachedKeyPanel);
                break;
            case CATEGORY_AUDIO:
                if (cachedAudioPanel == null) cachedAudioPanel = createAudioSettingsPanel();
                switchSettingsPanel(cachedAudioPanel);
                break;
            case CATEGORY_DISPLAY:
                if (cachedDisplayPanel == null) cachedDisplayPanel = createDisplaySettingsPanel();
                switchSettingsPanel(cachedDisplayPanel);
                break;
            default:
                LoggerManager.Logger("WARNING", "未知的设置分类: " + category);
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
            tr("framework.setting.keys.title", "按键绑定"),
            TextStyle.title(20, renderEngine.createColor("#FF69B4"))
        );

        // 上/下这两个键的含义会随地图的玩法模式变（俯视图里是移动，有重力的图里是跳/下穿），
        // 但绑定的是同一个功能，所以不拆成两行 —— 用一行说明把两种含义都讲清楚。
        LabelHandle gravityHintHandle = renderEngine.createLabel(
            tr("framework.setting.keys.gravity_hint",
               "在有重力的地图里：向上键 = 跳跃，向下键 = 从单向平台上跳下去"),
            new TextStyle("System", 12, false, false, renderEngine.createColor("#666666"), 0, 1.2)
        );
        renderEngine.setMaxWidth(gravityHintHandle.getNativeHandle(), 480);

        Object keyGrid = renderEngine.createGridPane(20, 15);
        renderEngine.setAlignment(keyGrid, "center");

        addKeyBindingRow(keyGrid, 0, "framework.setting.key.move_up", "向上移动", "MOVE_UP");
        addKeyBindingRow(keyGrid, 1, "framework.setting.key.move_down", "向下移动", "MOVE_DOWN");
        addKeyBindingRow(keyGrid, 2, "framework.setting.key.move_left", "向左移动", "MOVE_LEFT");
        addKeyBindingRow(keyGrid, 3, "framework.setting.key.move_right", "向右移动", "MOVE_RIGHT");
        addKeyBindingRow(keyGrid, 4, "framework.setting.key.accelerate", "加速", "ACCELERATE");
        addKeyBindingRow(keyGrid, 5, "framework.setting.key.interact", "交互", "INTERACT");
        addKeyBindingRow(keyGrid, 6, "framework.setting.key.magic_attack", "法阵攻击", "MAGIC_ATTACK");
        addKeyBindingRow(keyGrid, 7, "framework.setting.key.backpack", "背包", "BACKPACK");

        renderEngine.addChild(panel, panelTitleHandle.getNativeHandle());
        renderEngine.addChild(panel, keyGrid);
        renderEngine.addChild(panel, gravityHintHandle.getNativeHandle());
        return panel;
    }

    private void addKeyBindingRow(Object grid, int row, String labelKey, String labelFallback,
                                  String function) {
        LabelHandle nameLabelHandle = renderEngine.createLabel(
            tr(labelKey, labelFallback),
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
            tr("framework.setting.rebind", "更改"),
            TextStyle.simple(14, renderEngine.createColor("#FFFFFF")),
            renderEngine.createColor(ContentConfig.primaryColor()),
            renderEngine.createColor(ContentConfig.secondaryColor()),
            10
        );
        renderEngine.setSize(rebindButton.getNativeHandle(), 80, 35);
        renderEngine.setButtonAction(rebindButton, () -> startKeyBinding(function, rebindButton));
        // 记录这个按钮绑的是哪个功能 —— 重绑过程中会被临时改成提示文字，靠它恢复
        renderEngine.setUserData(rebindButton.getNativeHandle(), function);

        renderEngine.addToGrid(grid, nameLabelHandle.getNativeHandle(), 0, row);
        renderEngine.addToGrid(grid, keyLabelHandle.getNativeHandle(), 1, row);
        renderEngine.addToGrid(grid, rebindButton.getNativeHandle(), 2, row);
    }

    private void startKeyBinding(String function, ButtonHandle button) {
        if (waitingForInputButton != null && waitingForInputButton != button) {
            // 之前那个按钮还停在「等待按键」状态，恢复它自己的功能标识
            String previous = (String) renderEngine.getUserData(waitingForInputButton.getNativeHandle());
            if (previous != null) {
                renderEngine.setUserData(waitingForInputButton.getNativeHandle(), previous);
            }
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

        // 只改按钮上的文字，userData 继续保存功能标识（见 addKeyBindingRow）
        renderEngine.setButtonText(button, tr("framework.setting.rebinding", "等待按键"));
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

        // 恢复按钮文字，并把功能标识放回 userData
        renderEngine.setButtonText(waitingForInputButton, tr("framework.setting.rebind", "更改"));
        renderEngine.setUserData(waitingForInputButton.getNativeHandle(), waitingFunction);
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
            tr("framework.setting.display.title", "显示设置"),
            TextStyle.title(20, renderEngine.createColor("#FF69B4"))
        );

        Object displayGrid = renderEngine.createGridPane(20, 15);
        renderEngine.setAlignment(displayGrid, "center");

        // 比例设置
        LabelHandle ratioLabelHandle = renderEngine.createLabel(
            tr("framework.setting.aspect_ratio", "画面比例"),
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
            tr("framework.setting.fullscreen", "全屏模式"),
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        CheckBoxHandle fullscreenHandle = renderEngine.createCheckBox("");
        renderEngine.setCheckBoxSelected(fullscreenHandle, GameManager.getInstance().isFullscreen());
        // 只暂存选择，和比例 / 全屏方式一样等「应用 / 确定」才生效 ——
        // 立刻改的话，玩家点「取消」就会留下已经改过的窗口状态
        renderEngine.addCheckBoxListener(fullscreenHandle, (oldVal, newVal) ->
            currentSettings.put("fullscreen", newVal));

        renderEngine.addToGrid(displayGrid, fullscreenLabelHandle.getNativeHandle(), 0, 1);
        renderEngine.addToGrid(displayGrid, fullscreenHandle.getNativeHandle(), 1, 1);

        // 全屏方式设置
        LabelHandle fullscreenModeLabelHandle = renderEngine.createLabel(
            tr("framework.setting.fullscreen_mode", "全屏方式"),
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        // 下拉框里显示的是翻译后的模式名，存进配置的仍是稳定标识
        ComboBoxHandle fullscreenModeHandle = renderEngine.createComboBox(
            List.of(fullscreenModeDisplay(FrameworkDataKeys.FULLSCREEN_MODE_BORDERLESS),
                    fullscreenModeDisplay(FrameworkDataKeys.FULLSCREEN_MODE_EXCLUSIVE)),
            200
        );
        String currentFullscreenMode = FrameworkDataKeys.FULLSCREEN_MODE.get();
        renderEngine.setComboBoxSelectedValue(fullscreenModeHandle,
                fullscreenModeDisplay(currentFullscreenMode));
        renderEngine.setBackground(fullscreenModeHandle.getNativeHandle(),
            "-fx-background-color: #FFE4E1; -fx-background-radius: 10;");

        renderEngine.setComboBoxOnAction(fullscreenModeHandle, () -> {
            String selected = renderEngine.getComboBoxSelectedValue(fullscreenModeHandle);
            String mode = fullscreenModeOfDisplay(selected);
            if (mode != null) {
                currentSettings.put("fullscreenMode", mode);
            }
        });

        renderEngine.addToGrid(displayGrid, fullscreenModeLabelHandle.getNativeHandle(), 0, 2);
        renderEngine.addToGrid(displayGrid, fullscreenModeHandle.getNativeHandle(), 1, 2);

        // 文本速度设置
        LabelHandle textSpeedLabelHandle = renderEngine.createLabel(
            tr("framework.setting.text_speed", "文本速度"),
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
            // 拖一次滑杆会触发几十次 value 变化；不合并的话每次都会
            // 完整序列化 + 加密 + 写一遍文件，而且都在 FX 线程上
            DataManager.batch(() -> FrameworkDataKeys.TEXT_SPEED.setInt(val));
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

        // 语言切换：只有内容提供了语言列表才显示这一行
        addLanguageRow(displayGrid, 4);

        renderEngine.addChild(panel, panelTitleHandle.getNativeHandle());
        renderEngine.addChild(panel, displayGrid);
        return panel;
    }

    /**
     * 添加「界面语言」一行。
     *
     * <p><b>内容没提供语言列表时整行都不显示</b> —— 显示一个只有一项的下拉框，
     * 或者显示一个切了没反应的控件，都比不显示更让人困惑（见
     * {@code ContentConfig.addLanguage}）。
     *
     * <p>选中即刻生效：语言属于「看得见的东西」，改完要等点确定才变，玩家
     * 反而不知道刚才选了什么。切换后由 {@link #onLanguageChanged} 通知调用方
     * 重建界面。
     */
    private void addLanguageRow(Object grid, int row) {
        if (!LanguageSettings.isSwitchable()) {
            LoggerManager.Logger("DEBUG",
                    "内容未提供可选语言，设置界面不显示语言切换");
            return;
        }

        LabelHandle labelHandle = renderEngine.createLabel(
            tr("framework.setting.language", "界面语言"),
            new TextStyle("System", 14, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
        );

        // 当前语言：优先看已经生效的语言表，其次才是存下来的偏好。
        // 重建界面时 I18n 已经加载好新语言，所以这里读到的就是「现在看到的语言」。
        String active = I18n.getInstance().language();
        if (!LanguageSettings.isAvailable(active)) {
            active = LanguageSettings.current();
        }
        final String currentLang = active;

        Object languageRow = renderEngine.createHBox(10, 0);
        renderEngine.setAlignment(languageRow, "center_left");

        // 用横向排列的可点击文字而不是下拉框：此处没有下拉框的样式控制能力，
        // 而语言数量通常只有两三个，一次全列出来反而少一次点击。
        for (Map.Entry<String, String> option : LanguageSettings.options().entrySet()) {
            final String code = option.getKey();
            boolean isCurrent = LanguageSettings.same(code, currentLang);

            LabelHandle chip = renderEngine.createLabel(
                option.getValue(),
                new TextStyle("System", 15, isCurrent, false,
                    renderEngine.createColor(isCurrent ? "#FF69B4" : "#8B4513"), 0, 1.5)
            );
            renderEngine.setPadding(chip.getNativeHandle(), 6, 12, 6, 12);
            renderEngine.setBackground(chip.getNativeHandle(),
                "-fx-background-color: " + (isCurrent ? "#FFE4E1" : "rgba(255,255,255,0.55)") + "; " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: " + ContentConfig.primaryColor() + "; " +
                "-fx-border-width: " + (isCurrent ? 2 : 1) + "; " +
                "-fx-border-radius: 10;");
            renderEngine.setUserData(chip.getNativeHandle(), code);
            renderEngine.setCursorHand(chip.getNativeHandle());
            renderEngine.setLabelClickAction(chip, () -> selectLanguage(code));

            renderEngine.addChild(languageRow, chip.getNativeHandle());
        }

        renderEngine.addToGrid(grid, labelHandle.getNativeHandle(), 0, row);
        renderEngine.addToGrid(grid, languageRow, 1, row);
    }

    /** 玩家点了某个语言：切换 + 通知调用方重建界面。点了当前语言则什么都不做。 */
    private void selectLanguage(String code) {
        if (LanguageSettings.same(code, I18n.getInstance().language())) {
            return;
        }
        if (!LanguageSettings.apply(code)) {
            // 失败原因已经在 LanguageSettings 里记了日志，这里只提示玩家
            NotificationManager.getInstance()
                    .showNotification(tr("framework.setting.language", "界面语言"),
                            "该语言不可用，已保持当前语言", null, 4);
            return;
        }
        NotificationManager.getInstance()
                .showNotification(tr("framework.setting.language", "界面语言"),
                        LanguageSettings.displayName(code), null, 3);
        // 广播语言切换：界面需要重建的（例如标题栏文案）自己监听，
        // 不必让本类去认识每一个关心语言的地方
        com.xiaowu.game.starveil.infrastructure.event.LifecycleEvents.languageChanged(code);
        if (onLanguageChanged != null) {
            onLanguageChanged.accept(code);
        }
    }

    /** 全屏方式的显示名（随语言变化）。 */
    private static String fullscreenModeDisplay(String modeId) {
        return switch (FrameworkDataKeys.normalizeFullscreenMode(modeId)) {
            case FrameworkDataKeys.FULLSCREEN_MODE_EXCLUSIVE ->
                    tr("framework.setting.fullscreen_mode.exclusive", "全屏");
            default ->
                    tr("framework.setting.fullscreen_mode.borderless", "无边框窗口");
        };
    }

    /** 显示名 → 稳定标识；不是已知项时返回 null（不改动设置）。 */
    private static String fullscreenModeOfDisplay(String displayName) {
        if (displayName == null) {
            return null;
        }
        if (displayName.equals(tr("framework.setting.fullscreen_mode.borderless", "无边框窗口"))) {
            return FrameworkDataKeys.FULLSCREEN_MODE_BORDERLESS;
        }
        if (displayName.equals(tr("framework.setting.fullscreen_mode.exclusive", "全屏"))) {
            return FrameworkDataKeys.FULLSCREEN_MODE_EXCLUSIVE;
        }
        // 界面文字不是预期值时（例如语言刚切换、缓存面板没重建），退回按标识认
        String normalized = FrameworkDataKeys.normalizeFullscreenMode(displayName);
        return normalized;
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
            // setInterval 会写配置；拖一次滑杆触发几十次，合并成一次落盘
            DataManager.batch(() -> BGMManager.getInstance().setInterval(interval));
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
        final String ratio = aspectRatio;

        // 全屏：面板里改过就用面板的选择，否则沿用当前状态
        Boolean pendingFullscreen = (Boolean) currentSettings.get("fullscreen");
        final boolean fullscreen = pendingFullscreen != null
                ? pendingFullscreen
                : GameManager.getInstance().isFullscreen();

        // 这一组写入合并成一次落盘（含 saveKeyBindings 里的八次，它自己也会批量嵌套）
        DataManager.batch(() -> {
            FrameworkDataKeys.ASPECT_RATIO.set(ratio);

            FrameworkDataKeys.FULLSCREEN.set(fullscreen);

            String fullscreenMode = (String) currentSettings.get("fullscreenMode");
            if (fullscreenMode != null) {
                FrameworkDataKeys.FULLSCREEN_MODE.set(fullscreenMode);
            }

            FrameworkDataKeys.BGM_VOLUME.set(AudioManager.getBackgroundMusicVolume());
            FrameworkDataKeys.SFX_VOLUME.set(AudioManager.getSoundEffectsVolume());

            saveKeyBindings();
        });

        // 窗口调整放在批量之外：它们不写配置，
        // 和「攒几次写入再落盘」是两件事，混在一起只会让顺序更难读
        GameManager.getInstance().setFullscreen(fullscreen);
        GameManager.getInstance().setAspectRatio(ratio);

        NotificationManager.getInstance()
                .showNotification(tr("framework.setting.saved.title", "设置已加载"),
                        tr("framework.setting.saved.body", "已将设置应用到您的游戏"), null, 5);
        LoggerManager.Logger("INFO", "设置已保存");
    }

    /**
     * 把「暂存在面板里」的设置重新套回运行时（取消时用）。
     *
     * <p>只处理**编辑期间就立即生效**的那些项 —— 目前是文本速度（拖滑杆时即刻生效）
     * 与界面语言（切换即刻生效且已落盘）。比例、全屏、音量、按键都只暂存在
     * {@code currentSettings} 里、等「应用 / 确定」才生效，取消时无需回滚。
     */
    /**
     * 把「编辑期间就立即生效」的设置回滚到打开面板时的值（取消时用）。
     *
     * <p>目前只有文本速度属于这类：拖滑杆即刻生效。其它项（比例、全屏、音量、按键）
     * 都只暂存在 {@code currentSettings} 里、等「应用 / 确定」才生效，无需回滚。
     *
     * <p>回滚值来自 {@link #initialTextSpeed} —— <b>不能</b>现读配置：
     * 拖滑杆时值已经被写进内存配置了，现读只会读回刚改掉的那个值。
     */
    private void restoreSettings() {
        currentSettings.remove(GameConstants.SETTING_TEXT_SPEED);
        FrameworkDataKeys.TEXT_SPEED.setInt(initialTextSpeed);

        LoggerManager.Logger("INFO", "设置已放弃，运行时状态回到打开面板时的值");
    }

    private void saveKeyBindings() {
        String[] keyFunctions = {"MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT", "ACCELERATE", "INTERACT", "MAGIC_ATTACK", "BACKPACK"};
        // 八个键合并成一次落盘，而不是写八次文件
        DataManager.batch(() -> {
            for (String function : keyFunctions) {
                DataKey<Integer> binding = keyBinding(function);
                if (binding == null) {
                    continue;
                }
                binding.setInt(inputHandler.getKeyForFunction(function));
            }
        });
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
