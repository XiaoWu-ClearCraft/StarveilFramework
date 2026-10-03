package com.xiaowu.game.starveil.render;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * 法阵纹理绘制测试 - 使用 JavaFX Canvas API
 */
public class MagicCircleTest extends Application {

    private static final int SIZE = 600;
    private static final Color BG_COLOR = Color.rgb(30, 30, 40);
    private static final Color PURPLE_DARK = Color.rgb(60, 20, 100);
    private static final Color PURPLE_MID = Color.rgb(120, 50, 180);
    private static final Color PURPLE_LIGHT = Color.rgb(180, 120, 230);
    private static final Color LINE_COLOR = Color.rgb(240, 220, 255);
    private static final Color GLOW_COLOR = Color.rgb(200, 160, 255, 0.3);

    // 光晕动画参数
    private double flowProgress = 0.0; // 0~1 光晕流动进度
    private static final double FLOW_DURATION = 3.0; // 秒

    // 正方形旋转动画参数
    private double squareAngle = 0.0; // 度
    private static final double SQUARE_ROTATION_SPEED = 15.0; // 度/秒

    // 装饰小圆旋转动画参数（与正方形反方向）
    private double circleAngle = 0.0; // 度
    private static final double CIRCLE_ROTATION_SPEED = 10.0; // 度/秒

    private Canvas canvas;
    private double cx, cy, maxRadius;

    @Override
    public void start(Stage stage) {
        canvas = new Canvas(SIZE, SIZE);
        cx = SIZE / 2.0;
        cy = SIZE / 2.0;
        maxRadius = SIZE * 0.45;

        drawFrame();

        StackPane root = new StackPane(canvas);
        root.setStyle("-fx-background-color: #1e1e28;");
        Scene scene = new Scene(root, SIZE, SIZE);
        scene.setFill(BG_COLOR);

        // 光晕流动动画
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                flowProgress += 0.016 / FLOW_DURATION;
                if (flowProgress >= 1.0) {
                    flowProgress = 0.0;
                }
                squareAngle += 0.016 * SQUARE_ROTATION_SPEED;
                if (squareAngle >= 360.0) {
                    squareAngle -= 360.0;
                }
                circleAngle -= 0.016 * CIRCLE_ROTATION_SPEED;
                if (circleAngle <= -360.0) {
                    circleAngle += 360.0;
                }
                drawFrame();
            }
        };
        timer.start();

        stage.setTitle("法阵绘制测试");
        stage.setScene(scene);
        stage.show();
    }

    private void drawFrame() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, SIZE, SIZE);
        drawMagicCircle(gc, cx, cy, maxRadius);
    }

    /**
     * 绘制完整法阵
     */
    private void drawMagicCircle(GraphicsContext gc, double cx, double cy, double maxRadius) {
        // 1. 径向渐变背景（带流动光晕）
        drawGradientBackground(gc, cx, cy, maxRadius);

        // 2. 外层装饰圆环
        drawOuterRing(gc, cx, cy, maxRadius);

        // 3. 外圈文字（沿圆弧排列）— 位于第3根白线内侧
        drawOuterText(gc, cx, cy, maxRadius * 0.78);

        // 4. 中层圆环
        double midRadius = maxRadius * 0.7;
        drawMidRing(gc, cx, cy, midRadius);

        // 5. 内圈文字
        drawInnerText(gc, cx, cy, maxRadius * 0.55);

        // 6. 旋转正方形（菱形）
        drawRotatedSquare(gc, cx, cy, midRadius * 0.85);

        // 7. 放射状线条
        drawRadialLines(gc, cx, cy, maxRadius * 0.95, midRadius * 0.5);

        // 8. 中心圆
        drawCenterCircle(gc, cx, cy, maxRadius * 0.18);

        // 9. 装饰性小圆（外圈4个方位）— 叠在第2根白线上
        drawDecorativeCircles(gc, cx, cy, maxRadius * 0.95, maxRadius * 0.050);
    }

    /**
     * 径向渐变背景 + 流动光晕
     * 光晕从中心向外扩散，progress=0 时在中心，progress=1 时完全扩散消失
     */
    private void drawGradientBackground(GraphicsContext gc, double cx, double cy, double radius) {
        // 底层静态渐变（深色底）
        RadialGradient baseGradient = new RadialGradient(
                0, 0, cx, cy, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, PURPLE_MID),
                new Stop(0.4, PURPLE_DARK),
                new Stop(1.0, Color.rgb(40, 15, 70))
        );
        gc.setFill(baseGradient);
        gc.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // 流动光晕 — 从中心向外扩散的亮色脉冲
        double glowRadius = radius * flowProgress;
        if (glowRadius > 0) {
            double opacity = 1.0 - flowProgress; // 越往外越透明
            Color glowColor = Color.rgb(180, 120, 230, opacity * 0.8);

            RadialGradient glowGradient = new RadialGradient(
                    0, 0, cx, cy, glowRadius, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, glowColor),
                    new Stop(0.5, Color.rgb(180, 120, 230, opacity * 0.3)),
                    new Stop(1.0, Color.TRANSPARENT)
            );
            gc.setFill(glowGradient);
            gc.fillOval(cx - glowRadius, cy - glowRadius, glowRadius * 2, glowRadius * 2);
        }
    }

    /**
     * 外层圆环（三根白线）
     * 从外向内：第1根(radius)、第2根(radius*0.95)、第3根(radius*0.90)
     */
    private void drawOuterRing(GraphicsContext gc, double cx, double cy, double radius) {
        gc.setStroke(LINE_COLOR);
        // 第1根 — 最外
        gc.setLineWidth(2);
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);

        // 第2根
        gc.setLineWidth(0.2);
        double ring2 = radius * 0.95;
        gc.strokeOval(cx - ring2, cy - ring2, ring2 * 2, ring2 * 2);

        // 第3根
        gc.setLineWidth(1);
        double ring3 = radius * 0.90;
        gc.strokeOval(cx - ring3, cy - ring3, ring3 * 2, ring3 * 2);
    }

    /**
     * 外圈文字（沿圆弧排列的符号）
     */
    private void drawOuterText(GraphicsContext gc, double cx, double cy, double radius) {
        String[] symbols = {"π", "ω", "Ω", "Σ", "π", "ω", "Ω", "Σ"};
        double angleStep = 360.0 / symbols.length;

        gc.setFont(Font.font("Serif", FontWeight.BOLD, 24));
        gc.setFill(LINE_COLOR);
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);

        for (int i = 0; i < symbols.length; i++) {
            double angle = Math.toRadians(i * angleStep - 90);
            double x = cx + radius * Math.cos(angle);
            double y = cy + radius * Math.sin(angle);

            gc.save();
            gc.translate(x, y);
            gc.rotate(Math.toDegrees(angle) + 90);
            gc.fillText(symbols[i], 0, 0);
            gc.restore();
        }
    }

    /**
     * 中层圆环
     */
    private void drawMidRing(GraphicsContext gc, double cx, double cy, double radius) {
        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);

        gc.setLineWidth(0.8);
        gc.setStroke(GLOW_COLOR);
        double innerR = radius * 0.9;
        gc.strokeOval(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
    }

    /**
     * 内圈文字
     */
    private void drawInnerText(GraphicsContext gc, double cx, double cy, double radius) {
        String[] symbols = {"π", "ω", "Ω", "Σ", "π", "ω", "Ω", "Σ"};
        double angleStep = 360.0 / symbols.length;

        gc.setFont(Font.font("Serif", 16));
        gc.setFill(Color.rgb(200, 180, 230, 0.8));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.setTextBaseline(javafx.geometry.VPos.CENTER);

        for (int i = 0; i < symbols.length; i++) {
            double angle = Math.toRadians(i * angleStep - 90);
            double x = cx + radius * Math.cos(angle);
            double y = cy + radius * Math.sin(angle);

            gc.save();
            gc.translate(x, y);
            gc.rotate(Math.toDegrees(angle) + 90);
            gc.fillText(symbols[i], 0, 0);
            gc.restore();
        }
    }

    /**
     * 旋转正方形（菱形）— 带旋转动画
     */
    private void drawRotatedSquare(GraphicsContext gc, double cx, double cy, double size) {
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(squareAngle);
        gc.translate(-cx, -cy);

        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1);

        double halfSize = size * 0.830;
        gc.strokeRect(cx - halfSize, cy - halfSize, halfSize * 2, halfSize * 2);

        gc.setStroke(GLOW_COLOR);
        gc.setLineWidth(0.5);
        double innerHalf = halfSize * 0.6;
        gc.strokeRect(cx - innerHalf, cy - innerHalf, innerHalf * 2, innerHalf * 2);

        gc.restore();
    }

    /**
     * 放射状线条
     */
    private void drawRadialLines(GraphicsContext gc, double cx, double cy, double outerR, double innerR) {
        gc.setStroke(Color.rgb(200, 180, 230, 0.4));
        gc.setLineWidth(0.5);

        int lineCount = 16;
        double angleStep = 360.0 / lineCount;

        for (int i = 0; i < lineCount; i++) {
            double angle = Math.toRadians(i * angleStep);
            double x1 = cx + innerR * Math.cos(angle);
            double y1 = cy + innerR * Math.sin(angle);
            double x2 = cx + outerR * Math.cos(angle);
            double y2 = cy + outerR * Math.sin(angle);
            gc.strokeLine(x1, y1, x2, y2);
        }
    }

    /**
     * 中心圆
     */
    private void drawCenterCircle(GraphicsContext gc, double cx, double cy, double radius) {
        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - radius, cy - radius, radius * 2, radius * 2);

        gc.setFill(PURPLE_DARK);
        gc.fillOval(cx - radius * 0.7, cy - radius * 0.7, radius * 1.4, radius * 1.4);
    }

    /**
     * 装饰性小圆（4个方位）— 带反向旋转动画
     * 每个小圆内有两个叠放的符号
     */
    private void drawDecorativeCircles(GraphicsContext gc, double cx, double cy, double orbitRadius, double circleRadius) {
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(circleAngle);
        gc.translate(-cx, -cy);

        gc.setStroke(LINE_COLOR);
        gc.setLineWidth(1);

        String[][] symbolPairs = {
                {">", "<"},
                {"S", "V"},
                {"<", ">"},
                {"V", "S"}
        };
        int count = 4;
        double angleStep = 360.0 / count;

        for (int i = 0; i < count; i++) {
            double angle = Math.toRadians(i * angleStep - 90);
            double x = cx + orbitRadius * Math.cos(angle);
            double y = cy + orbitRadius * Math.sin(angle);

            gc.strokeOval(x - circleRadius, y - circleRadius, circleRadius * 2, circleRadius * 2);
            gc.setLineWidth(0.5);
            gc.strokeOval(x - circleRadius * 0.75, y - circleRadius * 0.75, circleRadius * 1.5, circleRadius * 1.5);
            gc.setLineWidth(1);

            gc.setFont(Font.font("Serif", FontWeight.BOLD, circleRadius * 0.9));
            gc.setFill(LINE_COLOR);
            gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
            gc.setTextBaseline(javafx.geometry.VPos.CENTER);
            gc.fillText(symbolPairs[i][0], x, y - circleRadius * 0.35);
            gc.fillText(symbolPairs[i][1], x, y + circleRadius * 0.35);
        }

        gc.restore();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
