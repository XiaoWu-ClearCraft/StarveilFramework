package com.xiaowu.game.starveil.ui.overlay;

import com.xiaowu.game.starveil.game.state.GameInstance;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.render.engine.*;
import com.xiaowu.game.starveil.render.engine.handles.*;

/**
 * 弹窗管理器 - 负责显示游戏内UI弹窗（在GameManager主页面内显示）
 * 已迁移到使用统一的渲染引擎
 */
public class PopupManager {
    private static PopupManager instance;
    private RenderEngine renderEngine;
    private Object popupContainer;
    private Object overlay;
    private Object popupContent;
    private boolean isPopupVisible = false;
    private Runnable onPopupClosed;
    private boolean initialized = false;

    private static final String TITLE_FONT = "starveil:fonts/xiaolai-sc-regular.ttf";
    private static final String TEXT_FONT = "starveil:fonts/zhengjing.ttf";

    private PopupManager() {
        this.renderEngine = null; // 延迟初始化
    }

    private RenderEngine getRenderEngine() {
        ensureInitialized();
        return renderEngine;
    }

    private void ensureInitialized() {
        if (!initialized) {
            initializePopupContainer();
            initialized = true;
        }
    }

    public static PopupManager getInstance() {
        if (instance == null) {
            instance = new PopupManager();
        }
        return instance;
    }

    /**
     * 初始化弹窗容器
     */
    private void initializePopupContainer() {
        // 直接获取引擎，避免递归调用
        renderEngine = com.xiaowu.game.starveil.render.engine.RenderEngineProvider.getInstance().getEngine();
        
        // 创建主容器
        popupContainer = renderEngine.createStackPane();
        renderEngine.setAlignment(popupContainer, "center");
        renderEngine.setMouseTransparent(popupContainer, true);
        renderEngine.setVisible(popupContainer, false);

        // 创建遮罩层
        overlay = renderEngine.createStackPane();
        renderEngine.setBackground(overlay, "-fx-background-color: rgba(0, 0, 0, 0.6);");
        renderEngine.setMouseTransparent(overlay, false);
        renderEngine.setFocusTraversable(overlay, true);

        // 创建内容区域
        popupContent = renderEngine.createVBox(20, 30);
        renderEngine.setAlignment(popupContent, "center");
        renderEngine.setPrefWidth(popupContent, 450);
        renderEngine.setMaxWidth(popupContent, 450);
        // 使用 USE_PREF_SIZE 策略，让高度根据内容自适应 (-1 表示 USE_PREF_SIZE)
        renderEngine.setMaxHeight(popupContent, -1);
        renderEngine.setBackground(popupContent, "-fx-background-color: linear-gradient(to bottom, #FFC0CB, #FFB6C1); " +
                "-fx-background-radius: 15; " +
                "-fx-border-color: #FF69B4; " +
                "-fx-border-width: 3; " +
                "-fx-border-radius: 15; " +
                "-fx-effect: dropshadow(gaussian, rgba(255, 105, 180, 0.5), 20, 0, 0, 5);");
        renderEngine.setOpacity(popupContent, 0);

        // 将遮罩和内容添加到容器
        renderEngine.addChild(popupContainer, overlay);
        renderEngine.addChild(popupContainer, popupContent);

        renderEngine.registerKeyHandler(event -> {
            if (event.getKeyCode() == KeyCodeEvent.KeyCode.ESCAPE && isPopupVisible) {
                hidePopup();
            }
        });
    }

    /**
     * 获取弹窗容器（用于添加到GameManager）
     */
    public Object getPopupContainer() {
        ensureInitialized();
        return popupContainer;
    }

    /**
     * 显示提示弹窗（必须点确认关闭）
     * @param title 标题
     * @param message 消息内容
     */
    public void showPopup(String title, String message) {
        ensureInitialized();
        getRenderEngine().runLater(() -> {
            // 创建弹窗内容
            Object content = getRenderEngine().createVBox(15, 0);
            getRenderEngine().setAlignment(content, "center");
            getRenderEngine().setMaxWidth(content, 380);

            // 标题
            TextStyle titleStyle = getRenderEngine().createTextStyle(TITLE_FONT, 22, false, false, getRenderEngine().createColor("#FF1493"));
            LabelHandle titleLabel = getRenderEngine().createLabel(title, titleStyle);
            getRenderEngine().addChild(content, titleLabel.getNativeHandle());

            // 消息文本
            TextStyle msgStyle = getRenderEngine().createTextStyle(TEXT_FONT, 14, false, false, getRenderEngine().createColor("#8B008B"));
            Object messageText = getRenderEngine().createText(message, msgStyle);
            getRenderEngine().setMaxWidth(messageText, 340);
            getRenderEngine().addChild(content, messageText);

            // 确认按钮
            ButtonHandle confirmButton = createStyledButton("确认", getRenderEngine().createColor("#FF69B4"));
            if (confirmButton.getNativeHandle() instanceof javafx.scene.control.Button cb) {
                cb.setOnMouseReleased(e -> hidePopup());
            }
            getRenderEngine().addChild(content, confirmButton.getNativeHandle());

            // 显示弹窗
            showPopupContent(content);
        });
    }

    /**
     * 显示确认弹窗
     * @param title 标题
     * @param message 消息内容
     * @param onConfirm 确认回调
     * @param onCancel 取消回调
     */
    public void showConfirm(String title, String message, Runnable onConfirm, Runnable onCancel) {
        ensureInitialized();
        getRenderEngine().runLater(() -> {
            // 创建弹窗内容
            Object content = getRenderEngine().createVBox(15, 0);
            getRenderEngine().setAlignment(content, "center");
            getRenderEngine().setMaxWidth(content, 380);

            // 标题
            TextStyle titleStyle = getRenderEngine().createTextStyle(TITLE_FONT, 22, false, false, getRenderEngine().createColor("#FF1493"));
            LabelHandle titleLabel = getRenderEngine().createLabel(title, titleStyle);
            getRenderEngine().addChild(content, titleLabel.getNativeHandle());

            // 消息文本
            TextStyle msgStyle = getRenderEngine().createTextStyle(TEXT_FONT, 14, false, false, getRenderEngine().createColor("#8B008B"));
            Object messageText = getRenderEngine().createText(message, msgStyle);
            getRenderEngine().setMaxWidth(messageText, 340);
            getRenderEngine().addChild(content, messageText);

            // 按钮区域
            Object buttonBox = getRenderEngine().createHBox(20, 0);
            getRenderEngine().setAlignment(buttonBox, "center");

            ButtonHandle confirmButton = createStyledButton("确定", getRenderEngine().createColor("#FF69B4"));
            if (confirmButton.getNativeHandle() instanceof javafx.scene.control.Button cb) {
                cb.setOnMouseReleased(e -> {
                    hidePopup();
                    if (onConfirm != null) onConfirm.run();
                });
            }
            getRenderEngine().addChild(buttonBox, confirmButton.getNativeHandle());

            ButtonHandle cancelButton = createStyledButton("取消", getRenderEngine().createColor("#FFB6C1"));
            if (cancelButton.getNativeHandle() instanceof javafx.scene.control.Button cb2) {
                cb2.setOnMouseReleased(e -> {
                    hidePopup();
                    if (onCancel != null) onCancel.run();
                });
            }
            getRenderEngine().addChild(buttonBox, cancelButton.getNativeHandle());

            getRenderEngine().addChild(content, buttonBox);

            // 显示弹窗
            showPopupContent(content);
        });
    }

    /**
     * 显示弹窗内容
     * @param content 内容容器
     */
    private void showPopupContent(Object content) {
        getRenderEngine().clearChildren(popupContent);
        getRenderEngine().addChild(popupContent, content);

        getRenderEngine().setVisible(popupContainer, true);
        getRenderEngine().setMouseTransparent(popupContainer, false);
        isPopupVisible = true;

        // 保证弹窗永远盖在其它已打开的页面（如存档页）之上
        if (popupContainer instanceof javafx.scene.Node n && n.getParent() != null) {
            n.toFront();
        }

        disableGameControls();

        // 渐入动画
        AnimationHandle fadeIn = getRenderEngine().createFadeIn(popupContent, 300, 0, 1);
        getRenderEngine().playAnimation(fadeIn);

        AnimationHandle fadeInOverlay = getRenderEngine().createFadeIn(overlay, 300, 0, 1);
        getRenderEngine().playAnimation(fadeInOverlay);

        AnimationHandle scaleIn = getRenderEngine().createScaleAnimation(popupContent, 300, 0.9, 0.9, 1, 1);
        getRenderEngine().playAnimation(scaleIn);
    }

    /**
     * 隐藏弹窗
     */
    public void hidePopup() {
        if (!isPopupVisible) return;

        AnimationHandle fadeOut = getRenderEngine().createFadeOut(popupContent, 200, 1, 0);
        AnimationHandle fadeOutOverlay = getRenderEngine().createFadeOut(overlay, 200, 1, 0);
        AnimationHandle scaleOut = getRenderEngine().createScaleAnimation(popupContent, 200, 1, 1, 0.9, 0.9);

        fadeOut.setOnComplete(() -> {
            getRenderEngine().clearChildren(popupContent);
            getRenderEngine().setVisible(popupContainer, false);
            getRenderEngine().setMouseTransparent(popupContainer, true);
            isPopupVisible = false;

            if (onPopupClosed != null) {
                Runnable callback = onPopupClosed;
                onPopupClosed = null;
                callback.run();
            }

            enableGameControls();
        });

        getRenderEngine().playAnimation(fadeOut);
        getRenderEngine().playAnimation(fadeOutOverlay);
        getRenderEngine().playAnimation(scaleOut);
    }

    /**
     * 检查弹窗是否可见
     */
    public boolean isPopupVisible() {
        return isPopupVisible;
    }

    /**
     * 设置弹窗关闭回调
     * @param callback 弹窗关闭时执行的回调
     */
    public void setOnPopupClosed(Runnable callback) {
        this.onPopupClosed = callback;
    }

    /**
     * 禁用游戏控制（如果游戏正在进行）
     */
    private void disableGameControls() {
        try {
            WorldMap worldMap = GameInstance.getWorldMap();
            if (worldMap != null) {
                InputHandler inputHandler = GameInstance.getInputHandlerStatic();
                if (inputHandler != null) {
                    inputHandler.lockControls(InputHandler.LOCK_POPUP);
                    LoggerManager.Logger("DEBUG", "弹窗显示，禁用游戏控制");
                }
            }
        } catch (Exception e) {
            // 游戏可能未启动，忽略错误
        }
    }

    /**
     * 启用游戏控制（如果游戏正在进行）
     */
    private void enableGameControls() {
        try {
            WorldMap worldMap = GameInstance.getWorldMap();
            if (worldMap != null) {
                InputHandler inputHandler = GameInstance.getInputHandlerStatic();
                if (inputHandler != null) {
                    inputHandler.unlockControls(InputHandler.LOCK_POPUP);
                    LoggerManager.Logger("DEBUG", "弹窗关闭，启用游戏控制");
                }
            }
        } catch (Exception e) {
            // 游戏可能未启动，忽略错误
        }
    }

    /**
     * 创建样式化按钮（粉色系）
     */
    private ButtonHandle createStyledButton(String text, ColorDef color) {
        TextStyle style = getRenderEngine().createTextStyle(null, 14, false, false, getRenderEngine().createColor("white"));
        ButtonHandle button = getRenderEngine().createButton(text, style);
        getRenderEngine().setSize(button.getNativeHandle(), 100, 40);
        getRenderEngine().setBackground(button.getNativeHandle(),
                String.format("-fx-background-color: %s; -fx-background-radius: 10;", color.getValue()));

        if (button.getNativeHandle() instanceof javafx.scene.control.Button javafxButton) {
            javafxButton.setOnMouseEntered(e -> {
                getRenderEngine().setBackground(button.getNativeHandle(),
                        String.format("-fx-background-color: %s; -fx-background-radius: 10;", color.darker().getValue()));
            });

            javafxButton.setOnMouseExited(e -> {
                getRenderEngine().setBackground(button.getNativeHandle(),
                        String.format("-fx-background-color: %s; -fx-background-radius: 10;", color.getValue()));
            });
        }

        return button;
    }
}
