package com.xiaowu.game.starveil.ui.overlay;

import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.net.NetworkStatus;
import com.xiaowu.game.starveil.render.TextureNodeFactory;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 断网提示：强制挡在画面上，直到联网（或用「特殊键」跳过）。
 *
 * <h2>为什么要强制</h2>
 * 这东西只在内容主动开启后才会出现（
 * {@code ContentConfig.setOfflinePromptEnabled(true)}，默认关闭）。
 * 开启的场景通常是「这游戏不联网就没法正常玩」：那就没必要给一个「知道了」按钮
 * 让玩家点掉继续玩一个坏掉的游戏 —— 直接挡住，联网后自动消失。
 *
 * <h2>为什么还要留一个特殊键</h2>
 * 因为网络判断会有假阴性（公共 WiFi 的强制门户、公司代理、探测端点刚好被墙……），
 * 一旦判断错了，玩家就被一个永远不消失的弹窗关在门外 —— 那比断网本身严重得多。
 * 所以留一个不显眼的键作为逃生口：按下去这一次会话不再提示（联网后再断开仍会提示）。
 * 默认 {@code F8}，内容可用
 * {@link ContentConfig#setOfflineBypassKey(String)} 改。
 *
 * <h2>挂在哪</h2>
 * 挂到<b>场景根</b>（{@code GameManager} 的 rootContainer）而不是画布里的
 * modalHost：主菜单阶段还没有画布，而「没网」这件事在主菜单就该说。
 */
public final class OfflinePrompt {

    private static final OfflinePrompt INSTANCE = new OfflinePrompt();

    private StackPane root;
    private StackPane overlay;
    private Scene boundScene;
    private Label hintLabel;

    /** 本次会话是否已经用特殊键跳过。 */
    private boolean bypassed;

    private OfflinePrompt() {
    }

    public static OfflinePrompt getInstance() {
        return INSTANCE;
    }

    /**
     * 绑定到场景与一个全屏宿主（{@code GameManager.initialize/updateScene} 调）。
     *
     * <p>会顺带启动联网探测（仅在功能开启时），并注册特殊键的按键过滤。
     */
    public void attach(StackPane host, Scene scene) {
        if (host == null || scene == null) {
            return;
        }
        this.root = host;
        bindScene(scene);
        if (!ContentConfig.offlinePromptEnabled()) {
            return;
        }
        NetworkStatus.addListener(this::onNetworkStateChanged);
        NetworkStatus.start();
    }

    /** 切换场景时重新绑定（宿主仍是 rootContainer，跨场景复用）。 */
    private void bindScene(Scene scene) {
        if (scene == boundScene) {
            return;
        }
        boundScene = scene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    /** 功能是否开启。 */
    private boolean enabled() {
        return ContentConfig.offlinePromptEnabled();
    }

    // ==================== 状态变化 ====================

    private void onNetworkStateChanged(NetworkStatus.State state) {
        Platform.runLater(() -> {
            if (state == NetworkStatus.State.OFFLINE && !bypassed && enabled()) {
                showOverlay();
            } else {
                hideOverlay();
                if (state == NetworkStatus.State.ONLINE) {
                    // 恢复联网后重新武装：下一次断开还要提示
                    bypassed = false;
                }
            }
        });
    }

    // ==================== 特殊键 ====================

    private void onKeyPressed(KeyEvent event) {
        if (!enabled() || overlay == null || bypassed) {
            return;
        }
        if (event.getCode() == bypassKey()) {
            bypassed = true;
            Logger("INFO", "已用特殊键跳过断网提示（本次会话不再提示）");
            hideOverlay();
            event.consume();
        }
    }

    /** 配置里的特殊键；解析不出来就退回 F8。 */
    private KeyCode bypassKey() {
        String name = ContentConfig.offlineBypassKey();
        if (name != null) {
            try {
                return KeyCode.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                Logger("WARNING", "断网提示的跳过键无法识别: " + name + "，使用 F8");
            }
        }
        return KeyCode.F8;
    }

    // ==================== 显示 / 隐藏 ====================

    private void showOverlay() {
        if (root == null || overlay != null) {
            return;
        }
        overlay = buildOverlay();
        root.getChildren().add(overlay);
        FadeTransition fade = new FadeTransition(Duration.millis(200), overlay);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        Logger("INFO", "断网提示已显示（按 " + bypassKey() + " 可跳过）");
    }

    private void hideOverlay() {
        if (overlay == null) {
            return;
        }
        StackPane node = overlay;
        overlay = null;
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            if (root != null) {
                root.getChildren().remove(node);
            }
        });
        fade.play();
    }

    private StackPane buildOverlay() {
        StackPane pane = new StackPane();
        pane.setBackground(new Background(new BackgroundFill(
                Color.rgb(0, 0, 0, 0.82), CornerRadii.EMPTY, Insets.EMPTY)));
        // 吃掉所有鼠标与按键：这是「强制」的部分
        pane.setPickOnBounds(true);
        pane.addEventFilter(KeyEvent.ANY, KeyEvent::consume);
        pane.addEventFilter(javafx.scene.input.MouseEvent.ANY,
                javafx.scene.input.MouseEvent::consume);

        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(28, 36, 28, 36));
        box.setMaxSize(560, javafx.scene.layout.Region.USE_PREF_SIZE);
        box.setBackground(new Background(new BackgroundFill(
                Color.rgb(20, 20, 20, 0.95),
                new CornerRadii(14), Insets.EMPTY)));

        Object iconPath = ContentConfig.appIcon();
        if (iconPath != null) {
            Image icon = TextureNodeFactory.loadImage(String.valueOf(iconPath));
            if (icon != null) {
                ImageView iv = new ImageView(icon);
                iv.setFitWidth(72);
                iv.setFitHeight(72);
                iv.setPreserveRatio(true);
                box.getChildren().add(iv);
            }
        }

        Label title = new Label("未检测到网络连接");
        title.setTextFill(Color.WHITE);
        title.setFont(Font.font(24));

        Label detail = new Label("这个游戏需要联网才能继续。\n请连接互联网 —— 连上后本提示会自动消失。");
        detail.setTextFill(Color.rgb(220, 220, 220));
        detail.setFont(Font.font(15));
        detail.setWrapText(true);
        detail.setMaxWidth(480);
        detail.setAlignment(Pos.CENTER);
        detail.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        hintLabel = new Label("（实在连不上？按 " + bypassKey() + " 跳过本次提示）");
        hintLabel.setTextFill(Color.rgb(150, 150, 150));
        hintLabel.setFont(Font.font(12));

        box.getChildren().addAll(title, detail, hintLabel);

        Label state = new Label();
        state.setTextFill(Color.rgb(150, 150, 150));
        state.setFont(Font.font(11));
        state.setText("正在持续检测网络…");
        box.getChildren().add(state);

        pane.getChildren().add(box);
        return pane;
    }
}
