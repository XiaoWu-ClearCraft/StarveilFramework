package com.xiaowu.game.starveil.render.effects;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sun.jna.platform.WindowUtils;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;
import com.xiaowu.game.starveil.platform.api.PlatformInjectPoints;
import com.xiaowu.game.starveil.platform.common.SystemDetector;
import com.xiaowu.game.starveil.platform.windows.WinOpsManager;
import com.xiaowu.game.starveil.plugin.InjectPoint;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 伪蓝屏（BSOD）效果 - 模拟系统崩溃画面
 */
public class BlueScreenEffect {

    private static final Color BSOD_BLUE = Color.rgb(0x02, 0x78, 0xD8);

    private boolean blueScreenActive = false;
    private final List<Node> blueScreenNodes = new ArrayList<>();
    private Timeline blueScreenTimeline;

    /**
     * 当前是否处于蓝屏状态
     */
    public boolean isBlueScreenActive() {
        return blueScreenActive;
    }

    /**
     * 显示伪蓝屏
     *
     * @param layer 目标覆盖层
     * @param stage 覆盖层所在窗口
     */
    public void show(Pane layer, Stage stage) {
        if (blueScreenActive) return;
        blueScreenActive = true;

        Platform.runLater(() -> {
            layer.setMouseTransparent(false);

            // 蓝屏时取消鼠标穿透，让用户能交互
            activateWindowForBlueScreen(stage);

            layer.getChildren().clear();
            blueScreenNodes.clear();

            double sw = stage.getWidth();
            double sh = stage.getHeight();

            Rectangle bg = new Rectangle(0, 0, sw, sh);
            bg.setFill(BSOD_BLUE);
            blueScreenNodes.add(bg);

            double ml = sw * 0.1;
            double mt = Math.max((sh - 420) / 3, sh * 0.05);

            Text face = new Text(":(");
            face.setFont(Font.font("Segoe UI", FontWeight.BOLD, 100));
            face.setFill(Color.WHITE);
            face.setLayoutX(ml);
            face.setLayoutY(mt + 80);
            blueScreenNodes.add(face);

            Text para1 = new Text("你的设备遇到问题，需要重新启动。");
            para1.setFont(Font.font("Microsoft YaHei", 26));
            para1.setFill(Color.WHITE);
            para1.setLayoutX(ml + 8);
            para1.setLayoutY(mt + 142);
            blueScreenNodes.add(para1);

            Text para2 = new Text("我们只收集某些错误信息，然后为你重新启动。");
            para2.setFont(Font.font("Microsoft YaHei", 26));
            para2.setFill(Color.WHITE);
            para2.setLayoutX(ml + 8);
            para2.setLayoutY(mt + 184);
            blueScreenNodes.add(para2);

            Text progressPrefix = new Text("0%");
            progressPrefix.setFont(Font.font("Microsoft YaHei", 26));
            progressPrefix.setFill(Color.WHITE);
            progressPrefix.setLayoutX(ml + 8);
            progressPrefix.setLayoutY(mt + 226);
            blueScreenNodes.add(progressPrefix);

            Text progressSuffix = new Text("  完成");
            progressSuffix.setFont(Font.font("Microsoft YaHei", 26));
            progressSuffix.setFill(Color.WHITE);
            progressSuffix.setLayoutX(ml + 8 + 60);
            progressSuffix.setLayoutY(mt + 226);
            blueScreenNodes.add(progressSuffix);

            double qrW = drawQRCode(sw, sh, ml, mt);

            String[] tipsLines = {
                "请不要关闭计算机电源，或拔出电源线，以防止系统损坏。",
                "有关此问题的详细信息和可能的解决方法，请扫描二维码。",
                "",
                "如果致电支持人员，请向他们提供以下信息：",
                "终止代码：NMI_HARDWARE_FAILURE"
            };
            double tipsX = ml + 8 + qrW + 16;
            double tipsY = mt + 262 + 15;
            for (String line : tipsLines) {
                Text t = new Text(line);
                t.setFont(Font.font("Microsoft YaHei", 16));
                t.setFill(Color.WHITE);
                t.setLayoutX(tipsX);
                t.setLayoutY(tipsY);
                blueScreenNodes.add(t);
                tipsY += 32;
            }

            layer.getChildren().addAll(blueScreenNodes);

            int[] pct = {0};
            blueScreenTimeline = new Timeline(
                new KeyFrame(Duration.seconds(6.0), e -> {
                    pct[0]++;
                    if (pct[0] <= 100) {
                        progressPrefix.setText(pct[0] + "%");
                    }
                })
            );
            blueScreenTimeline.setCycleCount(101);
            blueScreenTimeline.play();
        });
    }

    /**
     * 关闭伪蓝屏
     *
     * @param layer 目标覆盖层
     * @param stage 覆盖层所在窗口
     */
    public void dismiss(Pane layer, Stage stage) {
        if (!blueScreenActive) return;
        blueScreenActive = false;
        if (blueScreenTimeline != null) {
            blueScreenTimeline.stop();
            blueScreenTimeline = null;
        }

        Platform.runLater(() -> {
            layer.setMouseTransparent(true);

            // 恢复鼠标穿透
            restoreWindowAfterBlueScreen(stage);

            layer.getChildren().removeAll(blueScreenNodes);
            blueScreenNodes.clear();
        });
    }

    /**
     * 进入伪蓝屏：取消鼠标穿透、抢占焦点。
     *
     * <p>注入点 {@link PlatformInjectPoints#BLUE_SCREEN_ACTIVATE}：
     * 非 Windows 平台的窗口行为由平台适配插件用 REPLACE 接管。
     */
    @InjectPoint(PlatformInjectPoints.BLUE_SCREEN_ACTIVATE)
    private void activateWindowForBlueScreen(Stage stage) {
        if (SystemDetector.isWindows()) {
            WinOpsManager.setAlwaysOnTop(stage, true);
            WinOpsManager.setForegroundWindow(stage);
        }
        SwingUtilities.invokeLater(() -> setAwtWindowsTransparent(false));
    }

    /**
     * 退出伪蓝屏：恢复置顶与鼠标穿透。
     *
     * <p>注入点 {@link PlatformInjectPoints#BLUE_SCREEN_RESTORE}。
     */
    @InjectPoint(PlatformInjectPoints.BLUE_SCREEN_RESTORE)
    private void restoreWindowAfterBlueScreen(Stage stage) {
        if (SystemDetector.isWindows()) {
            WinOpsManager.setAlwaysOnTop(stage, true);
        }
        SwingUtilities.invokeLater(() -> setAwtWindowsTransparent(true));
    }

    /**
     * 强制重置蓝屏状态（用于覆盖层整体关闭时清理）
     */
    public void reset() {
        blueScreenActive = false;
        if (blueScreenTimeline != null) {
            blueScreenTimeline.stop();
            blueScreenTimeline = null;
        }
        blueScreenNodes.clear();
    }

    private void setAwtWindowsTransparent(boolean transparent) {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            if (w.isVisible()) {
                try {
                    WindowUtils.setWindowTransparent(w, transparent);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private double drawQRCode(double sw, double sh, double ml, double mt) {
        try {
            String url = "https://stveil.clearcraft.cn/game/qrcode";
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0);

            int size = matrix.getWidth();
            int scale = 4;
            int px = size * scale;
            BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
            int blueRgb = 0x0278D8;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int rgb = matrix.get(x, y) ? blueRgb : 0xFFFFFF;
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            img.setRGB(x * scale + dx, y * scale + dy, rgb);
                        }
                    }
                }
            }

            ImageView qrView = new ImageView(SwingFXUtils.toFXImage(img, null));
            double startX = ml + 8;
            double startY = mt + 256;
            qrView.setLayoutX(startX);
            qrView.setLayoutY(startY);
            blueScreenNodes.add(qrView);
            return px;
        } catch (WriterException e) {
            LoggerManager.Logger("WARN", "生成二维码失败: " + e.getMessage());
            return 0;
        }
    }
}
