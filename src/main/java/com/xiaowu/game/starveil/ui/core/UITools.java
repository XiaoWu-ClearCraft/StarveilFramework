package com.xiaowu.game.starveil.ui.core;
import com.xiaowu.game.starveil.infrastructure.ContentConfig;

import com.xiaowu.game.starveil.config.GameConstants;
import com.xiaowu.game.starveil.render.engine.RenderEngine;
import com.xiaowu.game.starveil.render.engine.RenderEngineProvider;
import com.xiaowu.game.starveil.render.engine.handles.ButtonHandle;
import com.xiaowu.game.starveil.render.engine.TextStyle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * UI工具类 - 提供通用的UI组件创建和样式设置方法
 * 注意：此类保持向后兼容，返回JavaFX类型但使用RenderEngine创建
 */
public class UITools {

    /**
     * 创建样式化的按钮
     *
     * @param text 按钮文本
     * @param width 按钮宽度
     * @param height 按钮高度
     * @return 样式化的按钮
     */
    public static Button createStyledButton(String text, double width, double height) {
        RenderEngine engine = RenderEngineProvider.getInstance().getEngine();
        ButtonHandle handle = engine.createButton(
            text,
            TextStyle.simple(16, engine.createColor("white")),
            engine.createColor(ContentConfig.primaryColor()),
            engine.createColor(ContentConfig.secondaryColor()),
            10
        );
        Button button = (Button) handle.getNativeHandle();
        button.setPrefSize(width, height);
        return button;
    }

    /**
     * 创建样式化的按钮（使用默认尺寸）
     */
    public static Button createStyledButton(String text) {
        return createStyledButton(text, 200, 50);
    }

    /**
     * 创建半透明遮罩
     */
    public static Pane createOverlayPane() {
        Pane overlay = new Pane();
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        overlay.setMouseTransparent(false);
        return overlay;
    }

    /**
     * 创建圆角面板
     */
    public static VBox createRoundedPanel(double width, double height, double radius) {
        VBox panel = new VBox();
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(20));
        panel.setPrefSize(width, height);

        BackgroundFill panelFill = new BackgroundFill(
                Color.rgb(0, 0, 0, 0.5), new CornerRadii(radius), Insets.EMPTY
        );
        panel.setBackground(new Background(panelFill));

        return panel;
    }

    /**
     * 创建圆角面板（使用默认半径）
     */
    public static VBox createRoundedPanel(double width, double height) {
        return createRoundedPanel(width, height, 20);
    }

    /**
     * 创建带圆角的容器
     */
    public static StackPane createRoundedContainer(double radius) {
        StackPane container = new StackPane();
        container.setStyle("-fx-background-color: transparent;");

        BackgroundFill fill = new BackgroundFill(
                Color.TRANSPARENT, new CornerRadii(radius), Insets.EMPTY
        );
        container.setBackground(new Background(fill));

        return container;
    }

    /**
     * 创建带圆角的容器（使用默认半径）
     */
    public static StackPane createRoundedContainer() {
        return createRoundedContainer(10);
    }

    /**
     * 为Pane设置圆角背景
     */
    public static void setRoundedBackground(Pane pane, Color color, double radius) {
        BackgroundFill fill = new BackgroundFill(
                color, new CornerRadii(radius), Insets.EMPTY
        );
        pane.setBackground(new Background(fill));
    }

    /**
     * 创建渐变背景
     */
    public static javafx.scene.shape.Rectangle createRadialGradient(double width, double height,
                                                                     Color centerColor, Color edgeColor) {
        javafx.scene.shape.Rectangle rectangle = new javafx.scene.shape.Rectangle();
        rectangle.setWidth(width);
        rectangle.setHeight(height);

        javafx.scene.paint.RadialGradient gradient = new javafx.scene.paint.RadialGradient(
                0, 0,
                0, 1,
                1.2,
                true, javafx.scene.paint.CycleMethod.NO_CYCLE,
                new javafx.scene.paint.Stop(0, centerColor),
                new javafx.scene.paint.Stop(1, edgeColor)
        );
        rectangle.setFill(gradient);

        return rectangle;
    }
}
