package com.xiaowu.game.starveil.ui.dialog;

import javafx.application.Platform;
import javafx.geometry.Insets;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 立绘出现 / 收起时，对话框的让位宽度要跟着变回来。
 *
 * <p>踩过的坑：</p>
 * <ol>
 *   <li>放大立绘后容器被顶大 → 高度变化又去放大立绘，无限长大（见
 *       {@link ChatContainerLayoutTest} 与 {@code ChatManager.buildUI} 里的最小尺寸钉死）；</li>
 *   <li>立绘收起来之后，下一个对话框仍然按「立绘还在」的宽度让位 —— 也就是这里钉的：</li>
 * </ol>
 *
 * <p>需要 JavaFX 工具包；起不来就跳过（环境限制，不是代码缺陷）。
 */
class ChatDialogStandeeLayoutTest {

    private static final double GAP = 16;

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
    void theStandeeReservationSurvivesTheNextDialogAndGoesAwayWhenHidden() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过布局测试");
        ChatManager chat = ChatManager.getInstance();
        assertNotNull(chat, "对话管理器应当可用");

        // 1) 立绘出现（对话框在下一个 FX 轮才建好，所以分两步）
        onFxAndWait(() -> {
            mountInScene(chat);
            chat.forceCloseAll();   // 清掉上一个用例留下的立绘/面板状态
            chat.setStandee("starveil:testchat/fixture-standee.png");
            chat.showDialog("甲", "有立绘的一句", null, "starveil:testchat/fixture-standee.png");
            return null;
        });

        // 2) 下一句不带立绘参数 = 立绘保持 → 仍要让位（你报的那个：这里要是不让位，对话框会压在立绘上）
        onFxAndWait(() -> {
            chat.showDialog("甲", "第二句（立绘还在）", null, null);
            return null;
        });
        double secondDialog = onFxAndWait(() -> {
            forceLayout(chat);
            return leftMarginOfLatestDialog(chat);
        });
        assertTrue(secondDialog > GAP + 1,
                "立绘还在时，后续对话框仍要让位（左边距 > gap），实际: " + secondDialog);

        // 3) 收起立绘 → 下一个对话框恢复整幅宽度
        onFxAndWait(() -> {
            chat.setStandee("");
            chat.showDialog("甲", "立绘收起了", null, "");
            return null;
        });
        double afterHide = onFxAndWait(() -> {
            forceLayout(chat);
            return leftMarginOfLatestDialog(chat);
        });
        assertEquals(GAP, afterHide, 0.5,
                "立绘收起后应当恢复整幅宽度（左边距回到 gap），实际: " + afterHide);

        // 4) 章节结束的整层清理（收立绘）之后，新对话同样不该继续让位
        onFxAndWait(() -> {
            chat.setStandee("starveil:testchat/fixture-standee.png");
            chat.showDialog("甲", "又出现立绘", null, "starveil:testchat/fixture-standee.png");
            return null;
        });
        onFxAndWait(() -> {
            chat.forceCloseAll();
            return null;
        });
        onFxAndWait(() -> {
            chat.showDialog("甲", "新一段剧情", null, null);
            return null;
        });
        double afterTeardown = onFxAndWait(() -> {
            forceLayout(chat);
            return leftMarginOfLatestDialog(chat);
        });
        assertEquals(GAP, afterTeardown, 0.5,
                "整层清理后立绘已经收掉，新对话不该继续让位，实际: " + afterTeardown);

        onFxAndWait(() -> {
            chat.setStandee("");
            return null;
        });
    }

    /**
     * 你报的那个：立绘<b>没在画面上</b>，对话框却还按「有立绘」缩着、左边留一大块空白。
     *
     * <p>让位必须看「立绘现在是否真的显示」，而不是「图片是否加载过」——
     * 任何把立绘层藏起来、却没清掉图片的路径，都不该继续占着对话框的地方。
     */
    @Test
    void anInvisibleStandeeReservesNothing() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过布局测试");
        ChatManager chat = ChatManager.getInstance();

        // 立绘确实在画面上：先确认它占着地方（前置条件，避免测试假通过）
        onFxAndWait(() -> {
            mountInScene(chat);
            chat.forceCloseAll();   // 清掉上一个用例留下的立绘/面板状态
            chat.setStandee("starveil:testchat/fixture-standee.png");
            chat.showDialog("甲", "立绘在画面上", null, "starveil:testchat/fixture-standee.png");
            return null;
        });
        waitForStandeeSettled(chat);
        double shown = onFxAndWait(() -> {
            chat.showDialog("甲", "再看一眼", null, null);
            return null;
        }) == null ? onFxAndWait(() -> {
            forceLayout(chat);
            return leftMarginOfLatestDialog(chat);
        }) : 0;
        assertTrue(shown > GAP + 1, "前置条件：立绘在画面上时应当让位，实际: " + shown
                + " [立绘层: " + standeeState(chat) + "]");

        // 立绘层被藏起来（图片还挂在上面）→ 不该再让位
        onFxAndWait(() -> {
            StackPane standeePane = chat.standeePaneForTest();
            assertNotNull(standeePane, "应当能找到立绘层");
            standeePane.setVisible(false);
            chat.showDialog("甲", "立绘没在画面上", null, null);
            return null;
        });
        double margin = onFxAndWait(() -> {
            forceLayout(chat);
            return leftMarginOfLatestDialog(chat);
        });
        assertEquals(GAP, margin, 0.5,
                "立绘不在画面上时不该让位（否则对话框会缩在右边、左边留一大块空白），实际: " + margin);

        onFxAndWait(() -> {
            chat.setStandee("");
            return null;
        });
    }

    /** 立绘层当前状态，断言失败时用来看清到底缺了什么。 */
    private static String standeeState(ChatManager chat) throws Exception {
        return onFxAndWait(() -> {
            StackPane pane = chat.standeePaneForTest();
            return "visible=" + pane.isVisible()
                    + " 子节点=" + pane.getChildren().size()
                    + " translateX=" + Math.round(pane.getTranslateX());
        });
    }

    /** 等立绘层滑到位（translateX 回到 0），避免拿动画中间态做断言。 */
    private static void waitForStandeeSettled(ChatManager chat) throws Exception {
        for (int i = 0; i < 60; i++) {
            Double x = onFxAndWait(() -> chat.standeePaneForTest().getTranslateX());
            if (x != null && Math.abs(x) < 0.5) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private static void mountInScene(ChatManager chat) {
        StackPane host = chat.getChatContainer();
        if (host.getScene() != null) {
            return;
        }
        StackPane canvas = new StackPane(host);
        canvas.setPrefSize(1920, 1080);
        new Scene(canvas, 1920, 1080);
        forceLayout(chat);
    }

    private static void forceLayout(ChatManager chat) {
        Region host = chat.getChatContainer();
        host.applyCss();
        host.layout();
        if (host.getScene() != null && host.getScene().getRoot() != host) {
            host.getScene().getRoot().applyCss();
            host.getScene().getRoot().layout();
        }
    }

    /** 最新那个对话框面板的左边距（让位宽度就体现在这里）。 */
    private static double leftMarginOfLatestDialog(ChatManager chat) {
        StackPane host = chat.getChatContainer();
        Region dialog = null;
        for (javafx.scene.Node node : host.getChildren()) {
            // 对话框是唯一带边距的那个（overlay / standeePane / historyPanel 都没有边距）
            if (node instanceof Region region && StackPane.getMargin(node) != null) {
                dialog = region;
            }
        }
        assertNotNull(dialog, "应当能找到当前对话框面板");
        Insets margin = StackPane.getMargin(dialog);
        assertNotNull(margin, "对话框应当有让位边距");
        return margin.getLeft();
    }

    private static <T> T onFxAndWait(FxSupplier<T> supplier) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(supplier.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(20, TimeUnit.SECONDS), "等待 FX 线程超时");
        if (failure.get() != null) {
            throw new AssertionError("FX 线程执行失败: " + failure.get(), failure.get());
        }
        return result.get();
    }

    private interface FxSupplier<T> {
        T get() throws Exception;
    }
}
