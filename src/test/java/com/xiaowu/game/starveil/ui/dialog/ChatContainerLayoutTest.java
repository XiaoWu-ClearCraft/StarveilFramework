package com.xiaowu.game.starveil.ui.dialog;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对话层的「容器最小尺寸必须钉死」回归测试。
 *
 * <p>踩过的坑：立绘放大（半身框）后图片比容器高，图片的 min 顺着布局往上顶，
 * 容器被顶大 → 容器高度变化触发按新高度重新缩放立绘 → 又更大……
 * 表现就是「设置了缩放后立绘不断变大」。根因是 <b>Region 的最小尺寸会被子节点撑开</b>，
 * 所以容器与立绘层都要显式 {@code setMinSize(0,0)}。
 *
 * <p>需要 JavaFX 工具包；起不来就跳过（环境限制，不是代码缺陷）。
 */
class ChatContainerLayoutTest {

    private static boolean toolkitReady;

    @BeforeAll
    static void startToolkit() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            toolkitReady = latch.await(20, TimeUnit.SECONDS);
        } catch (IllegalStateException alreadyRunning) {
            toolkitReady = true;
        } catch (Throwable t) {
            toolkitReady = false;
        }
    }

    @Test
    void anOversizedChildCannotInflateTheChatContainer() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过布局测试");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Double> minW = new AtomicReference<>();
        AtomicReference<Double> minH = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                StackPane chat = ChatManager.getInstance().getChatContainer();
                if (chat.getScene() == null) {
                    new Scene(chat, 1920, 1080);   // 已挂过就别再挂（同一个单例，跨用例会冲突）
                }
                chat.applyCss();
                chat.layout();

                // 模拟「放大后的立绘」：一个远大于容器的子节点
                Region huge = new Region();
                huge.setMinSize(4000, 4000);
                chat.getChildren().add(huge);
                try {
                    chat.applyCss();
                    chat.layout();
                    minW.set(chat.minWidth(-1));
                    minH.set(chat.minHeight(-1));
                } finally {
                    chat.getChildren().remove(huge);
                }
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(20, TimeUnit.SECONDS), "建布局超时");
        if (failure.get() != null) {
            throw new AssertionError("布局过程中抛异常: " + failure.get(), failure.get());
        }
        assertEquals(0.0, minW.get(), 0.001,
                "容器最小宽度必须为 0：否则溢出的立绘会把容器顶宽");
        assertEquals(0.0, minH.get(), 0.001,
                "容器最小高度必须为 0：否则放大的立绘会把容器顶高 → 高度变化又去放大立绘，无限长大");
    }
}
