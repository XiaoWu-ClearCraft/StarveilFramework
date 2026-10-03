package com.xiaowu.game.starveil.platform.common;

import com.xiaowu.game.starveil.platform.api.SystemManagerInterface;
import javafx.stage.Stage;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 与平台无关的对话框回退实现。
 *
 * <p>只在「非 Windows 平台且平台适配插件尚未接管」的启动早期使用——
 * 主要是为了把“当前平台不受支持”这类提示显示出来，
 * 而不是在还没有任何平台实现时直接抛异常。
 *
 * <p>优先使用 Swing；无图形环境（headless）时退化为标准输出。
 */
public class SwingSystemManager implements SystemManagerInterface {

    @Override
    public void showInfo(String title, String message) {
        show(title, message, JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void showWarning(String title, String message) {
        show(title, message, JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void showError(String title, String message) {
        show(title, message, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public boolean showConfirm(String title, String message) {
        Boolean answer = runOnEdt(() -> JOptionPane.showConfirmDialog(null, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION, false);
        return Boolean.TRUE.equals(answer);
    }

    @Override
    public int showCustom(String title, String message, int buttons, int icon) {
        int optionType = switch (buttons) {
            case 0x00000001 -> JOptionPane.OK_CANCEL_OPTION;
            case 0x00000004 -> JOptionPane.YES_NO_OPTION;
            default -> JOptionPane.DEFAULT_OPTION;
        };
        int messageType = switch (icon) {
            case 0x00000010 -> JOptionPane.ERROR_MESSAGE;
            case 0x00000030 -> JOptionPane.WARNING_MESSAGE;
            case 0x00000020 -> JOptionPane.QUESTION_MESSAGE;
            default -> JOptionPane.INFORMATION_MESSAGE;
        };
        Integer result = runOnEdt(() -> switch (
                JOptionPane.showConfirmDialog(null, message, title, optionType, messageType)) {
            // JOptionPane.OK_OPTION 与 YES_OPTION 同为 0，用同一个分支处理
            case JOptionPane.YES_OPTION -> 1;
            case JOptionPane.NO_OPTION -> 7;
            case JOptionPane.CANCEL_OPTION -> 2;
            default -> 0;
        }, 0);
        return result == null ? 0 : result;
    }

    @Override
    public void shakeWindow(Stage stage, int soundType) {
        if (stage == null) {
            return;
        }
        double originalX = stage.getX();
        double originalY = stage.getY();
        int distance = com.xiaowu.game.starveil.config.GameConstants.SHAKE_DISTANCE;
        for (int i = 0; i < com.xiaowu.game.starveil.config.GameConstants.SHAKE_COUNT; i++) {
            stage.setX(originalX + (i % 2 == 0 ? -distance : distance));
            stage.setY(originalY + (i % 2 == 0 ? distance : -distance));
            try {
                Thread.sleep(com.xiaowu.game.starveil.config.GameConstants.SHAKE_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        stage.setX(originalX);
        stage.setY(originalY);
    }

    @Override
    public void shakeWindow(Stage stage) {
        shakeWindow(stage, 2);
    }

    @Override
    public void testShake(Stage stage) {
        shakeWindow(stage, 2);
    }

    private void show(String title, String message, int messageType) {
        runOnEdt(() -> {
            JOptionPane.showMessageDialog(null, message, title, messageType);
            return null;
        }, null);
    }

    /**
     * 在事件分发线程上执行；headless 或 EDT 不可用时返回 fallback。
     */
    private <T> T runOnEdt(java.util.function.Supplier<T> action, T fallback) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return fallback;
        }
        AtomicReference<T> result = new AtomicReference<>(fallback);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                result.set(action.get());
            } else {
                SwingUtilities.invokeAndWait(() -> result.set(action.get()));
            }
        } catch (Exception e) {
            com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger(
                    "WARNING", "[SwingSystemManager] 对话框显示失败: " + e.getMessage());
        }
        return result.get();
    }
}
