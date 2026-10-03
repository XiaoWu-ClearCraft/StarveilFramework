package com.xiaowu.game.starveil.ui.overlay;

import com.xiaowu.game.starveil.render.engine.*;
import com.xiaowu.game.starveil.render.engine.handles.*;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;


/**
 * 通知管理器 - 负责显示游戏内通知
 * 已迁移到使用统一的渲染引擎
 *
 * <p>两类通知：
 * <ul>
 *   <li>{@link #showNotification} —— 短暂提示，几秒后自动滑出；</li>
 *   <li>{@link #showPersistent} —— <b>常驻</b>通知，带可选进度条，直到调用方
 *       {@link PersistentNotification#close()} 才消失。用于新手教程里
 *       「按住方向键直到条件满足」这类需要一直显示的过程性提示。</li>
 * </ul>
 */
public class NotificationManager {

    private static NotificationManager instance;

    private RenderEngine renderEngine;
    private Object notificationContainer;
    private final List<Notification> activeNotifications = new ArrayList<>();
    private boolean initialized = false;

    /** 通知之间的垂直间距。 */
    private static final double NOTIFICATION_GAP = 30;
    private static final double SLIDE_DISTANCE = 400;
    private static final double DEFAULT_PANEL_HEIGHT = 70;
    /** 常驻通知面板（带进度条）的高度。 */
    private static final double PERSISTENT_PANEL_HEIGHT = 86;
    /** 常驻通知进度条的尺寸。 */
    private static final double BAR_WIDTH = 320;
    private static final double BAR_HEIGHT = 10;

    private NotificationManager() {
        // 延迟初始化,等待第一次使用时再初始化
    }

    private void ensureInitialized() {
        if (!initialized) {
            renderEngine = RenderEngineProvider.getInstance().getEngine();
            notificationContainer = renderEngine.createStackPane();
            renderEngine.setAlignment(notificationContainer, "top_right");
            renderEngine.setMouseTransparent(notificationContainer, true);
            initialized = true;
        }
    }

    private RenderEngine getRenderEngine() {
        ensureInitialized();
        return renderEngine;
    }


    public static NotificationManager getInstance() {
        if (instance == null) instance = new NotificationManager();
        return instance;
    }


    public Object getNotificationContainer() {
        ensureInitialized();
        return notificationContainer;
    }


    public void showNotification(String title, String message, String iconPath, double seconds) {
        ensureInitialized();
        if (title == null) throw new IllegalArgumentException("title cannot be null");
        getRenderEngine().runLater(() -> pushNotification(title, message, iconPath, seconds));
    }


    private void pushNotification(String title, String message, String iconPath, double seconds) {
        Notification noti = new Notification(createPanel(title, message, iconPath));
        noti.height = DEFAULT_PANEL_HEIGHT;
        attachNotification(noti);

        // 滑入完成后等 seconds 秒，然后自动滑出
        AnimationHandle pause = getRenderEngine().createPause(seconds * 1000, null);
        pause.setOnComplete(() -> slideOutAndRemove(noti));
        getRenderEngine().playAnimation(pause);
    }

    /**
     * 显示一个 <b>常驻</b> 通知：不自动消失，可带进度条。
     *
     * <p>典型用途是新手教程里「按住方向键直到条件满足」的过程提示 ——
     * 玩家需要一直看到当前进度，条件满足后由调用方 {@link PersistentNotification#close()}。
     *
     * @param withProgressBar 是否显示进度条
     * @return 用于更新进度 / 改文字 / 关闭的句柄
     */
    public PersistentNotification showPersistent(String title, String message, boolean withProgressBar) {
        ensureInitialized();
        if (title == null) {
            throw new IllegalArgumentException("title cannot be null");
        }
        PersistentNotification handle = new PersistentNotification(title, message, withProgressBar);
        // 构建节点必须在 FX 线程；调用方通常是游戏主循环（本身就在 FX 线程）
        if (Platform.isFxApplicationThread()) {
            buildPersistent(handle);
        } else {
            getRenderEngine().runLater(() -> buildPersistent(handle));
        }
        return handle;
    }

    private void buildPersistent(PersistentNotification handle) {
        if (handle.closed) {
            return;
        }
        Object panel = createPersistentPanel(handle);
        handle.panel = panel;

        Notification noti = new Notification(panel);
        noti.persistent = true;
        noti.height = handle.withProgressBar ? PERSISTENT_PANEL_HEIGHT : DEFAULT_PANEL_HEIGHT;
        handle.noti = noti;

        attachNotification(noti);
        if (handle.pendingProgress >= 0) {
            handle.setProgress(handle.pendingProgress);
        }
    }

    /** 计算 Y、设初始偏移、加进容器并播放滑入。 */
    private void attachNotification(Notification noti) {
        // ⚠️ 必须先算 Y 再入列 —— 反过来的话 nextFreeY() 会把新通知自己也算进去，
        //    第一条通知就会从 20+高度+间距 开始，表现为顶部莫名空出一块。
        double initY = nextFreeY();
        activeNotifications.add(noti);
        noti.currentY = initY;
        noti.targetY = initY;

        // 使用 setTranslate 设置初始偏移（与 TranslateTransition 一致）
        getRenderEngine().setPosition(noti.panel, SLIDE_DISTANCE, initY);
        getRenderEngine().addChild(notificationContainer, noti.panel);

        noti.isAnimating = true;
        AnimationHandle slideIn = getRenderEngine().createTranslateAnimation(noti.panel, 500, 0, initY, "EASE_OUT");
        slideIn.setOnComplete(() -> {
            noti.isAnimating = false;
            noti.isSlidingOut = false;
        });
        getRenderEngine().playAnimation(slideIn);
    }

    /** 滑出并从容器移除。 */
    private void slideOutAndRemove(Notification noti) {
        if (noti.isSlidingOut) {
            return;
        }
        noti.isSlidingOut = true;
        double currentY = noti.currentY;
        AnimationHandle slideOut = getRenderEngine().createTranslateAnimation(
                noti.panel, 500, SLIDE_DISTANCE, currentY, "EASE_IN");
        slideOut.setOnComplete(() -> {
            getRenderEngine().removeChild(notificationContainer, noti.panel);
            activeNotifications.remove(noti);
            recomputePositions();
        });
        getRenderEngine().playAnimation(slideOut);
    }

    /** 下一个通知应该放的 Y（按各面板实际高度累加，避免高度不同时重叠）。 */
    private double nextFreeY() {
        double y = 20;
        for (Notification n : activeNotifications) {
            y += n.height + NOTIFICATION_GAP;
        }
        return y;
    }

    /** 常驻通知面板：标题 + 说明 + 可选进度条。 */
    private Object createPersistentPanel(PersistentNotification handle) {
        Object root = getRenderEngine().createVBox(4, 10);
        getRenderEngine().setAlignment(root, "center_left");

        String style = "-fx-background-color: linear-gradient(to right, #2b5876, #4e4376); "
                + "-fx-background-radius: 10; -fx-border-color: #ffd700; -fx-border-width: 2; "
                + "-fx-border-radius: 10; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0.5, 0, 2);";
        getRenderEngine().setBackground(root, style);
        double h = handle.withProgressBar ? PERSISTENT_PANEL_HEIGHT : DEFAULT_PANEL_HEIGHT;
        getRenderEngine().setSize(root, 350, h);
        getRenderEngine().setMaxWidth(root, 350);
        getRenderEngine().setMaxHeight(root, h);

        TextStyle titleStyle = getRenderEngine().createTextStyle(
                null, 12, true, false, getRenderEngine().createColor("#ffd700"));
        LabelHandle titleLabel = getRenderEngine().createLabel(handle.title, titleStyle);
        getRenderEngine().addChild(root, titleLabel.getNativeHandle());

        TextStyle msgStyle = getRenderEngine().createTextStyle(
                null, 13, false, false, getRenderEngine().createColor("white"));
        handle.messageLabel = getRenderEngine().createLabel(handle.message, msgStyle);
        getRenderEngine().addChild(root, handle.messageLabel.getNativeHandle());

        if (handle.withProgressBar) {
            handle.bar = new SlimBar(getRenderEngine(), BAR_WIDTH, BAR_HEIGHT);
            getRenderEngine().addChild(root, handle.bar.node());
        }
        return root;
    }


    /**
     * 自绘的细进度条：一条圆角轨道 + 一条圆角填充。
     *
     * <p>为什么不用 {@code ProgressBar}：它的填充是子节点 {@code .bar}，
     * 而 modena 主题给 {@code .bar} 定死了 {@code -fx-background-insets: 3 3 4 3}。
     * 内联样式只能作用于控件本身、改不到子节点，于是 8px 高的条里填充只剩 1px，
     * 看起来像没画出来。用两个 Region 自绘就没有这层干扰，圆角、渐变、高度都可控。
     */
    private static final class SlimBar {
        private final RenderEngine engine;
        private final Object root;
        private final Object fill;
        private final double width;
        private final double height;

        SlimBar(RenderEngine engine, double width, double height) {
            this.engine = engine;
            this.width = width;
            this.height = height;
            double radius = height / 2.0;

            Object track = engine.createRegion(0, 0);
            engine.setSize(track, width, height);
            engine.setBackground(track, "-fx-background-color: rgba(0, 0, 0, 0.45);"
                    + "-fx-background-radius: " + radius + ";");

            fill = engine.createRegion(0, 0);
            engine.setSize(fill, 0, height);
            engine.setBackground(fill, "-fx-background-color: linear-gradient(to right, #ffe9a8, #ffc93c);"
                    + "-fx-background-radius: " + radius + ";");

            Object container = engine.createStackPane();
            engine.setSize(container, width, height);
            engine.setAlignment(container, "center_left");
            engine.addChild(container, track);
            engine.addChild(container, fill);
            root = container;
        }

        Object node() {
            return root;
        }

        void setProgress(double ratio) {
            double r = Math.max(0, Math.min(1, ratio));
            engine.setSize(fill, width * r, height);
        }
    }


    /**
     * 常驻通知句柄。持有它就能持续更新进度、改文字或关闭。
     */
    public class PersistentNotification {
        final String title;
        final String message;
        final boolean withProgressBar;

        Object panel;
        LabelHandle messageLabel;
        SlimBar bar;
        Notification noti;
        boolean closed = false;
        /** 面板还没构建好时先缓存进度，构建后补上。 */
        double pendingProgress = -1;

        PersistentNotification(String title, String message, boolean withProgressBar) {
            this.title = title;
            this.message = message;
            this.withProgressBar = withProgressBar;
        }

        /** 更新进度条（0.0~1.0）。没有进度条时是空操作。 */
        public void setProgress(double ratio) {
            double r = Math.max(0, Math.min(1, ratio));
            if (bar == null) {
                pendingProgress = r;
                return;
            }
            bar.setProgress(r);
        }

        /** 更新说明文字。 */
        public void setMessage(String text) {
            if (messageLabel != null) {
                getRenderEngine().setLabelText(messageLabel, text);
            }
        }

        /** 关闭并滑出。可重复调用。 */
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            Runnable remove = () -> {
                if (noti != null) {
                    slideOutAndRemove(noti);
                }
            };
            if (Platform.isFxApplicationThread()) {
                remove.run();
            } else {
                getRenderEngine().runLater(remove);
            }
        }

        public boolean isClosed() {
            return closed;
        }
    }


    private void recomputePositions() {
        // 为所有通知更新 Y 位置
        // 正在滑出的通知也需要更新 Y，这样滑出动画会从正确的位置开始
        double y = 20;
        for (int i = 0; i < activeNotifications.size(); i++) {
            Notification n = activeNotifications.get(i);
            double targetY = y;
            y += n.height + NOTIFICATION_GAP;

            // 跳过正在滑入动画中的通知（等滑入完成后位置自然正确）
            if (n.isAnimating && !n.isSlidingOut) {
                // 更新目标 Y，滑入完成后会自动使用正确位置
                n.targetY = targetY;
                continue;
            }

            // 如果 Y 位置已经正确，不需要动画
            if (Math.abs(n.currentY - targetY) < 1) {
                n.targetY = targetY;
                continue;
            }

            // 更新状态
            n.currentY = targetY;
            n.targetY = targetY;

            // 创建 Y 位置动画，保持当前 X 值
            // 如果正在滑出，X 会从当前滑出进度处开始
            double currentX = getRenderEngine().getNodeTranslateX(n.panel);
            AnimationHandle moveAnim = getRenderEngine().createTranslateAnimation(n.panel, 300, currentX, targetY, "EASE_OUT");
            getRenderEngine().playAnimation(moveAnim);
        }
    }


    private Object createPanel(String title, String message, String iconPath) {
        Object root = getRenderEngine().createHBox(10, 10);
        getRenderEngine().setAlignment(root, "center_left");

        // 设置背景样式
        String style = "-fx-background-color: linear-gradient(to right, #2b5876, #4e4376); "
                + "-fx-background-radius: 10; -fx-border-color: #ffd700; -fx-border-width: 2; "
                + "-fx-border-radius: 10; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 10, 0.5, 0, 2);";
        getRenderEngine().setBackground(root, style);
        getRenderEngine().setSize(root, 350, 70);
        getRenderEngine().setMaxWidth(root, 350);
        getRenderEngine().setMaxHeight(root, 70);

        // 添加图标（如果有）
        if (iconPath != null && !iconPath.isEmpty()) {
            try {
                Object iconView = getRenderEngine().createImageView(iconPath, 50, 50);
                if (iconView != null) {
                    getRenderEngine().addChild(root, iconView);
                }
            } catch (Exception ignore) {}
        }

        // 文本容器
        Object textBox = getRenderEngine().createVBox(3, 0);
        getRenderEngine().setAlignment(textBox, "center_left");
        getRenderEngine().setMaxHeight(textBox, 50);

        TextStyle titleStyle = getRenderEngine().createTextStyle(null, 12, true, false, getRenderEngine().createColor("#ffd700"));
        LabelHandle titleLabel = getRenderEngine().createLabel(title, titleStyle);
        getRenderEngine().addChild(textBox, titleLabel.getNativeHandle());

        if (message != null && !message.isEmpty()) {
            TextStyle msgStyle = getRenderEngine().createTextStyle(null, 13, false, false, getRenderEngine().createColor("white"));
            LabelHandle msgLabel = getRenderEngine().createLabel(message, msgStyle);
            getRenderEngine().setMaxHeight(msgLabel.getNativeHandle(), 30);
            getRenderEngine().addChild(textBox, msgLabel.getNativeHandle());
        }

        getRenderEngine().addChild(root, textBox);
        return root;
    }


    private static class Notification {
        final Object panel;
        double currentY = 0;    // 当前 Y 位置
        double targetY = 0;     // 目标 Y 位置
        boolean isAnimating = false;   // 是否正在滑入/滑出动画中
        boolean isSlidingOut = false;  // 是否正在滑出
        /** 面板高度，用于堆叠定位（常驻通知带进度条会更高）。 */
        double height = DEFAULT_PANEL_HEIGHT;
        /** 常驻通知不自动滑出，只由调用方 close()。 */
        boolean persistent = false;

        Notification(Object panel) { this.panel = panel; }
    }
}
