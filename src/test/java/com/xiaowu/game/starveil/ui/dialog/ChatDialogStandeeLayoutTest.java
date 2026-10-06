package com.xiaowu.game.starveil.ui.dialog;

import com.xiaowu.game.starveil.infrastructure.ContentConfig;
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

    /**
     * 推进一句（真实关闭路径：点对话框）之后，立绘要还在画面上。
     *
     * <p>游戏里对话是一句句「点掉」的，关闭/淡出动画跑过一轮再显示下一句；
     * 之前测试都是直接连着 showDialog，没走这条路径。
     */
    @Test
    void theStandeeStaysOnScreenAfterAdvancing() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过布局测试");
        ChatManager chat = ChatManager.getInstance();

        onFxAndWait(() -> {
            mountInScene(chat);
            chat.forceCloseAll();
            chat.setStandee("starveil:testchat/fixture-standee.png");
            chat.showDialog("甲", "第一句", null, "starveil:testchat/fixture-standee.png");
            return null;
        });
        waitForStandeeSettled(chat);
        assertEquals(0.0, onFxAndWait(() -> chat.standeePaneForTest().getTranslateX()), 0.5,
                "前置条件：立绘应当已经滑到位");

        // 点一下对话框推进（走真实的关闭/淡出路径）
        onFxAndWait(() -> {
            Region dialog = latestDialogNode(chat);
            assertNotNull(dialog, "应当有对话框可以点");
            javafx.event.Event.fireEvent(dialog, clickOn(dialog));
            return null;
        });
        Thread.sleep(800);

        // 下一句不带立绘参数（立绘保持）
        onFxAndWait(() -> {
            chat.showDialog("甲", "第二句", null, null);
            return null;
        });
        double x = onFxAndWait(() -> {
            forceLayout(chat);
            return chat.standeePaneForTest().getTranslateX();
        });
        assertEquals(0.0, x, 0.5,
                "推进一句之后立绘应当还在画面上（不该被关闭动画挪到画面外），实际 x=" + x);

        onFxAndWait(() -> {
            chat.setStandee("");
            return null;
        });
    }

    /** 造一个点击事件（Pane 上的 onMouseClicked 只需要类型和按钮）。 */
    private static javafx.scene.input.MouseEvent clickOn(Region node) {
        return new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                node.getWidth() / 2, node.getHeight() / 2, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, true, false, false, null);
    }

    /** 最新那个对话框面板节点。 */
    private static Region latestDialogNode(ChatManager chat) {
        StackPane host = chat.getChatContainer();
        Region dialog = null;
        for (javafx.scene.Node node : host.getChildren()) {
            if (node instanceof Region region && StackPane.getMargin(node) != null) {
                dialog = region;
            }
        }
        return dialog;
    }
    /**
     * 图片被留在 opacity 0（淡入切换被打断）时，下一个对话框要把它恢复成可见。
     *
     * <p>这就是现场日志里的状态：层可见=true、层X=0、子节点=1，唯独「图片不透明=0」，
     * 于是立绘看不见、但对话框还按有立绘让位。
     */
    @Test
    void anImageStuckAtZeroOpacityIsRestored() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过布局测试");
        ChatManager chat = ChatManager.getInstance();

        onFxAndWait(() -> {
            mountInScene(chat);
            chat.forceCloseAll();
            chat.setStandee("starveil:testchat/fixture-standee.png");
            chat.showDialog("甲", "第一句", null, "starveil:testchat/fixture-standee.png");
            return null;
        });
        waitForStandeeSettled(chat);
        assertEquals(1.0, onFxAndWait(() -> standeeImage(chat).getOpacity()), 0.01,
                "前置条件：立绘应当是可见的");

        // 模拟「淡入没跑完」：图片停在 opacity 0
        onFxAndWait(() -> {
            standeeImage(chat).setOpacity(0);
            chat.showDialog("甲", "第二句", null, null);
            return null;
        });
        double opacity = onFxAndWait(() -> standeeImage(chat).getOpacity());
        assertEquals(1.0, opacity, 0.01,
                "图片停在不可见状态时，下一个对话框应当把它恢复显示，实际 opacity=" + opacity);

        onFxAndWait(() -> {
            chat.setStandee("");
            return null;
        });
    }

    /**
     * 预留宽度有上限：超过上限时立绘纵向居中，并且对话框压在立绘之上。
     */
    @Test
    void anOversizedStandeeIsCappedCentredAndStaysBehindTheDialog() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过布局测试");
        ChatManager chat = ChatManager.getInstance();
        double oldRatio = ContentConfig.standeeReservedMaxRatio();
        ContentConfig.setStandeeReservedMaxRatio(0.05);   // 故意压到很小，逼出「立绘比预留宽」
        try {
            onFxAndWait(() -> {
                mountInScene(chat);
                chat.forceCloseAll();
                chat.setStandee("starveil:testchat/fixture-standee.png");
                chat.showDialog("甲", "第一句", null, "starveil:testchat/fixture-standee.png");
                return null;
            });
            waitForStandeeSettled(chat);

            double[] state = onFxAndWait(() -> {
                forceLayout(chat);
                StackPane host = chat.getChatContainer();
                StackPane pane = chat.standeePaneForTest();
                javafx.scene.Node image = pane.getChildren().get(0);
                double reserved = leftMarginOfLatestDialog(chat) - GAP;   // 左边距去掉 gap
                return new double[]{
                        reserved,
                        pane.getTranslateX(),
                        ((javafx.scene.image.ImageView) image).getFitHeight(),
                        image.getTranslateY(),
                        host.getChildren().indexOf(pane),
                        host.getChildren().indexOf(latestDialogNode(chat)),
                        host.getWidth()
                };
            });
            double reserved = state[0];
            double canvasW = state[6];
            double fitHeight = state[2];
            assertTrue(reserved <= canvasW * 0.05 + 1,
                    "预留宽度应当被上限截住（<= 画布 5%），实际: " + reserved);
            assertEquals(0.0, state[1], 0.5, "立绘不该被滑出动画留在画面外");
            assertEquals((fitHeight - 1080) / 2, state[3], 1.0,
                    "立绘比预留位置宽时应当纵向居中（顶部超出多少、底部就超出多少）");
            assertTrue(state[5] > state[4],
                    "对话框必须压在立绘之上（对话框下标 " + state[5] + " 应大于立绘 " + state[4] + "）");
        } finally {
            ContentConfig.setStandeeReservedMaxRatio(oldRatio);
            onFxAndWait(() -> {
                chat.setStandee("");
                return null;
            });
        }
    }

    /** 立绘层里那张图。 */
    private static javafx.scene.Node standeeImage(ChatManager chat) {
        return chat.standeePaneForTest().getChildren().get(0);
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
