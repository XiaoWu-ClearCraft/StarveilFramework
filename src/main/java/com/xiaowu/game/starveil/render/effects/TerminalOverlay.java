package com.xiaowu.game.starveil.render.effects;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 终端文本覆盖层 - 在覆盖层上打印等宽字体文本，超出屏幕高度时自动滚动
 */
public class TerminalOverlay {

    private double textCursorY = 20;

    /**
     * 打印一行终端文本
     *
     * @param layer     目标覆盖层
     * @param line      文本内容
     * @param color     文本颜色
     * @param maxHeight 覆盖层可用高度（用于触发滚动）
     */
    public void print(Pane layer, String line, Color color, double maxHeight) {
        if (layer == null) return;
        Platform.runLater(() -> {
            Text text = new Text(line);
            text.setFont(Font.font("Consolas", 14));
            text.setFill(color);
            text.setLayoutX(10);
            text.setLayoutY(textCursorY);
            layer.getChildren().add(text);

            textCursorY += 18;
            if (textCursorY > maxHeight - 20) {
                scroll(layer);
            }
        });
    }

    /**
     * 清除覆盖层上的所有终端文本
     */
    public void clear(Pane layer) {
        if (layer == null) return;
        Platform.runLater(() -> {
            List<Node> texts = new ArrayList<>();
            for (var n : layer.getChildren()) {
                if (n instanceof Text) texts.add(n);
            }
            layer.getChildren().removeAll(texts);
            textCursorY = 20;
        });
    }

    private void scroll(Pane layer) {
        List<Node> texts = new ArrayList<>();
        for (var n : layer.getChildren()) {
            if (n instanceof Text) {
                Text t = (Text) n;
                t.setLayoutY(t.getLayoutY() - 18);
                if (t.getLayoutY() < 0) texts.add(t);
            }
        }
        layer.getChildren().removeAll(texts);
        textCursorY -= 18;
    }
}
