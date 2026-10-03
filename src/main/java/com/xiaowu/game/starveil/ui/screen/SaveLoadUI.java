package com.xiaowu.game.starveil.ui.screen;
import com.xiaowu.game.starveil.infrastructure.Fonts;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.infrastructure.persistence.SaveManager;
import com.xiaowu.game.starveil.infrastructure.persistence.SaveManager.SaveSlot;
import com.xiaowu.game.starveil.ui.overlay.PopupManager;
import com.xiaowu.game.starveil.game.state.RPGManager;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import com.xiaowu.game.starveil.render.engine.RenderEngineProvider;
import com.xiaowu.game.starveil.render.engine.handles.AnimationHandle;
import com.xiaowu.game.starveil.render.engine.handles.LabelHandle;
import com.xiaowu.game.starveil.render.engine.TextStyle;
import com.xiaowu.game.starveil.render.engine.ColorDef;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 存档/读档UI - 使用RenderEngine进行渲染
 */
public class SaveLoadUI {
    private Pane root;
    private Pane overlay;
    private VBox mainContainer;
    private Runnable onCloseCallback;
    private boolean isSaveMode;
    private volatile boolean isClosing = false;

    // 字体缓存
    private static Font cachedTitleFont = null;
    private static Font cachedSlotFont = null;
    private static Font cachedTextFont = null;

    private final RenderEngine renderEngine;

    public SaveLoadUI(boolean isSaveMode) {
        this.isSaveMode = isSaveMode;
        this.renderEngine = RenderEngineProvider.getInstance().getEngine();
        initFonts();
        createUI();
        setupAnimations();
    }

    private void initFonts() {
        if (cachedTitleFont == null) {
            cachedTitleFont = Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 32);
        }
        if (cachedSlotFont == null) {
            cachedSlotFont = Fonts.safeFont("starveil:fonts/xiaolai-sc-regular.ttf", 16);
        }
        if (cachedTextFont == null) {
            cachedTextFont = Fonts.safeFont("starveil:fonts/zhengjing.ttf", 12);
        }
    }

    private void createUI() {
        root = new StackPane();

        // 半透明遮罩层
        overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);");
        overlay.setMouseTransparent(false);
        overlay.setFocusTraversable(true);

        // 主容器
        mainContainer = new VBox(20);
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setPadding(new Insets(30));
        mainContainer.setMaxWidth(800);
        mainContainer.setMaxHeight(600);
        mainContainer.setStyle("-fx-background-color: rgba(255, 192, 203, 0.95); " +
                "-fx-background-radius: 20; " +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 20, 0, 0, 5);");

        // 标题 - 使用RenderEngine
        LabelHandle titleHandle = renderEngine.createLabel(
            isSaveMode ? "保存游戏" : "加载游戏",
            new TextStyle("System", 32, true, false, renderEngine.createColor("#FF69B4"), 0, 1.5)
        );
        Node titleLabel = (Node) titleHandle.getNativeHandle();

        // 九宫格存档槽
        GridPane saveGrid = new GridPane();
        saveGrid.setHgap(15);
        saveGrid.setVgap(15);
        saveGrid.setAlignment(Pos.CENTER);

        SaveSlot[] slots = SaveManager.getInstance().getAllSaveSlots();

        for (int i = 0; i < 9; i++) {
            SaveSlot slot = slots[i];
            VBox slotBox = createSlotBox(slot, i);
            saveGrid.add(slotBox, i % 3, i / 3);
        }

        // 底部按钮
        HBox bottomBox = new HBox(15);
        bottomBox.setAlignment(Pos.CENTER);

        Button cancelButton = createStyledButton("取消", Color.rgb(169, 169, 169));
        cancelButton.setOnAction(e -> close());

        bottomBox.getChildren().add(cancelButton);

        mainContainer.getChildren().addAll(titleLabel, saveGrid, bottomBox);

        root.getChildren().addAll(overlay, mainContainer);
    }

    private void setupAnimations() {
        mainContainer.setOpacity(0);
        overlay.setOpacity(0);

        // 渐入动画 - 使用RenderEngine
        AnimationHandle fadeInMain = renderEngine.createFadeIn(mainContainer, 300, 0, 1);
        AnimationHandle fadeInOverlay = renderEngine.createFadeIn(overlay, 300, 0, 1);
        renderEngine.playAnimation(fadeInMain);
        renderEngine.playAnimation(fadeInOverlay);
    }

    private VBox createSlotBox(SaveSlot slot, int index) {
        VBox slotBox = new VBox(10);
        slotBox.setPrefSize(200, 150);
        slotBox.setAlignment(Pos.CENTER);
        slotBox.setPadding(new Insets(15));
        slotBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); " +
                "-fx-background-radius: 10; " +
                "-fx-border-color: #FF69B4; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 10;");
        slotBox.setCursor(Cursor.HAND);

        if (slot.isEmpty) {
            LabelHandle emptyHandle = renderEngine.createLabel(
                "空存档",
                new TextStyle("System", 16, false, false, renderEngine.createColor("#8B4513"), 0, 1.5)
            );
            slotBox.getChildren().add((Node) emptyHandle.getNativeHandle());
        } else {
            SaveManager.SaveData saveData = slot.saveData;

            LabelHandle slotLabelHandle = renderEngine.createLabel(
                "存档 " + (index + 1),
                new TextStyle("System", 16, false, false, renderEngine.createColor("#FF69B4"), 0, 1.5)
            );
            Node slotLabel = (Node) slotLabelHandle.getNativeHandle();

            Text timeText = new Text(SaveManager.formatSaveTime(saveData.saveTime));
            timeText.setFill(Color.rgb(139, 69, 19));
            timeText.setFont(cachedTextFont);
            timeText.setWrappingWidth(170);

            Text mapText = new Text("地图: " + (saveData.worldMapName != null ? saveData.worldMapName : "未知"));
            mapText.setFill(Color.rgb(139, 69, 19));
            mapText.setFont(cachedTextFont);
            mapText.setWrappingWidth(170);

            slotBox.getChildren().addAll(slotLabel, timeText, mapText);
        }

        // 点击事件
        slotBox.setOnMouseClicked(e -> {
            if (isSaveMode) {
                handleSaveClick(slot, index);
            } else {
                handleLoadClick(slot, index);
            }
        });

        // 悬停效果
        slotBox.setOnMouseEntered(e -> {
            slotBox.setStyle("-fx-background-color: rgba(255, 228, 225, 0.9); " +
                    "-fx-background-radius: 10; " +
                    "-fx-border-color: #FF1493; " +
                    "-fx-border-width: 3; " +
                    "-fx-border-radius: 10;");
        });

        slotBox.setOnMouseExited(e -> {
            slotBox.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8); " +
                    "-fx-background-radius: 10; " +
                    "-fx-border-color: #FF69B4; " +
                    "-fx-border-width: 2; " +
                    "-fx-border-radius: 10;");
        });

        return slotBox;
    }

    private void handleSaveClick(SaveSlot slot, int index) {
        if (!slot.isEmpty) {
            // 已有存档，询问是否覆盖 - 使用PopupManager
            PopupManager.getInstance().showConfirm(
                "确认覆盖",
                "存档 " + (index + 1) + " 已有数据\n是否要覆盖现有存档？",
                () -> performSave(slot, index),
                () -> {} // 取消
            );
        } else {
            performSave(slot, index);
        }
    }

    private void performSave(SaveSlot slot, int index) {
        SaveManager.getInstance().saveGame(index);
        LoggerManager.Logger("INFO", "游戏已保存到槽位 " + (index + 1));
        refreshUI();
        showNotification("保存成功", "游戏已保存到槽位 " + (index + 1), 5);
    }

    private void handleLoadClick(SaveSlot slot, int index) {
        if (slot.isEmpty) {
            showNotification("无存档", "槽位 " + (index + 1) + " 没有存档数据", 3);
            return;
        }
        startGameFromSave(index);
    }

    private void startGameFromSave(int slotIndex) {
        try {
            close();

            javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
            delay.setOnFinished(e -> {
                try {
                    RPGManager rpgManager = new RPGManager();
                    rpgManager.startGameFromSave(slotIndex);
                } catch (Exception ex) {
                    LoggerManager.Logger("ERROR", "从存档启动游戏失败: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
            delay.play();

        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "启动游戏失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showNotification(String title, String message, double seconds) {
        PopupManager.getInstance().showPopup(title, message);
    }

    private void refreshUI() {
        GridPane saveGrid = null;
        for (javafx.scene.Node node : mainContainer.getChildren()) {
            if (node instanceof GridPane) {
                saveGrid = (GridPane) node;
                break;
            }
        }
        
        if (saveGrid != null) {
            saveGrid.getChildren().clear();
            
            SaveSlot[] slots = SaveManager.getInstance().getAllSaveSlots();
            for (int i = 0; i < 9; i++) {
                SaveSlot slot = slots[i];
                VBox slotBox = createSlotBox(slot, i);
                saveGrid.add(slotBox, i % 3, i / 3);
            }
        }
    }

    public void close() {
        if (isClosing) return;
        isClosing = true;

        // 渐出动画 - 使用RenderEngine
        AnimationHandle fadeOutMain = renderEngine.createFadeOut(mainContainer, 300, 1, 0);
        AnimationHandle fadeOutOverlay = renderEngine.createFadeOut(overlay, 300, 1, 0);

        fadeOutOverlay.setOnComplete(() -> {
            if (onCloseCallback != null) {
                onCloseCallback.run();
            }
        });

        renderEngine.playAnimation(fadeOutMain);
        renderEngine.playAnimation(fadeOutOverlay);
    }

    public void setOnClose(Runnable callback) {
        this.onCloseCallback = callback;
    }

    public Pane getRoot() {
        return root;
    }

    private Button createStyledButton(String text, Color color) {
        Button button = new Button(text);
        button.setPrefSize(120, 40);
        button.setTextFill(Color.WHITE);
        button.setFont(Font.font(16));

        BackgroundFill buttonFill = new BackgroundFill(
                color,
                new CornerRadii(20),
                Insets.EMPTY
        );
        button.setBackground(new Background(buttonFill));

        button.setOnMouseEntered(e -> {
            BackgroundFill hoverFill = new BackgroundFill(
                    color.darker(),
                    new CornerRadii(20),
                    Insets.EMPTY
            );
            button.setBackground(new Background(hoverFill));
        });

        button.setOnMouseExited(e -> {
            BackgroundFill originalFill = new BackgroundFill(
                    color,
                    new CornerRadii(20),
                    Insets.EMPTY
            );
            button.setBackground(new Background(originalFill));
        });

        return button;
    }
}