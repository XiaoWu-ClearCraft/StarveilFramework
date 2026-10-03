package com.xiaowu.game.starveil.ui.settings;

import com.xiaowu.game.starveil.infrastructure.ContentConfig;
import com.xiaowu.game.starveil.infrastructure.i18n.I18n;
import com.xiaowu.game.starveil.infrastructure.i18n.LanguageSettings;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;
import com.xiaowu.game.starveil.input.InputHandler;
import com.xiaowu.game.starveil.render.engine.RenderEngineProvider;
import com.xiaowu.game.starveil.render.engine.javafx.JavaFXRenderEngine;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设置界面的语言切换测试。
 *
 * <p>这里真的把面板建出来再检查节点树 —— 设置界面是玩家一定会看到的界面，
 * 「编译通过」不足以说明它没坏（字体为 null、控件创建顺序错乱之类的问题
 * 都只会在真正 build 的时候暴露）。
 *
 * <p>需要 JavaFX 工具包。测试环境起不来工具包时整个类会被 skip 而不是失败 ——
 * 那属于环境限制，不是代码缺陷。
 */
class SettingManagerLanguageTest {

    /** 测试用语言：框架自带的 zh_cn 之外，再注入一个假的英文表。 */
    private static final String TEST_EN = "xx_en";

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
        if (toolkitReady) {
            // 用测试语言覆盖设置界面的文案，验证「切换后界面文字真的变了」
            I18n.inject(TEST_EN, "framework.setting.title", "Settings");
            I18n.inject(TEST_EN, "framework.setting.tab.display", "Display");
            I18n.inject(TEST_EN, "framework.setting.language", "Language");
            I18n.inject(TEST_EN, "framework.setting.display.title", "Display settings");
            I18n.inject(TEST_EN, "framework.setting.aspect_ratio", "Aspect ratio");
            I18n.inject(TEST_EN, "framework.setting.fullscreen", "Fullscreen");
            I18n.inject(TEST_EN, "framework.setting.fullscreen_mode", "Fullscreen mode");
            I18n.inject(TEST_EN, "framework.setting.text_speed", "Text speed");
        }
    }

    @BeforeEach
    @AfterEach
    void reset() {
        ContentConfig.resetForTest();
        DataManager.resetForTest();
        I18n.getInstance().resetForTest();
        I18n.inject(TEST_EN, "framework.setting.title", "Settings");
        I18n.inject(TEST_EN, "framework.setting.tab.display", "Display");
        I18n.inject(TEST_EN, "framework.setting.language", "Language");
        I18n.inject(TEST_EN, "framework.setting.display.title", "Display settings");
        I18n.inject(TEST_EN, "framework.setting.aspect_ratio", "Aspect ratio");
        I18n.inject(TEST_EN, "framework.setting.fullscreen", "Fullscreen");
        I18n.inject(TEST_EN, "framework.setting.fullscreen_mode", "Fullscreen mode");
        I18n.inject(TEST_EN, "framework.setting.text_speed", "Text speed");
    }

    // ==================== 节点树 ====================

    @Test
    void languageRowIsHiddenWhenContentProvidesNoLanguages() throws Exception {
        String tree = buildSettingsTreeAndCollectText();

        assertFalse(tree.contains("界面语言"),
                "内容没提供语言时不该出现语言那一行:\n" + tree);
    }

    @Test
    void languageRowListsEveryLanguageContentProvides() throws Exception {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        ContentConfig.addLanguage(TEST_EN, "English");

        String tree = buildSettingsTreeAndCollectText();

        assertTrue(tree.contains("界面语言"), "应当出现语言那一行:\n" + tree);
        assertTrue(tree.contains("简体中文"), "列出内容登记的语言:\n" + tree);
        assertTrue(tree.contains("English"), "列出内容登记的语言:\n" + tree);
    }

    @Test
    void panelTextFollowsTheActiveLanguage() throws Exception {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        ContentConfig.addLanguage(TEST_EN, "English");

        // 切到测试语言后重建界面，界面文字应当变成英文
        assertTrue(LanguageSettings.apply(TEST_EN), "切换语言应当成功");
        String tree = buildSettingsTreeAndCollectText();

        assertTrue(tree.contains("Settings"), "标题应当变成英文:\n" + tree);
        assertTrue(tree.contains("Display"), "分类按钮应当变成英文:\n" + tree);
        assertTrue(tree.contains("Language"), "语言那一行应当变成英文:\n" + tree);
        assertFalse(tree.contains("界面语言"), "不该残留中文:\n" + tree);
    }

    // ==================== 工具 ====================

    /**
     * 在 FX 线程上建出设置界面，把节点树里的所有文字收集成一个字符串。
     *
     * <p>用文字而不是节点类型来断言：语言的生效方式就是「同样的控件、不同的文字」，
     * 按文字断言最贴近玩家看到的东西。
     */
    private String buildSettingsTreeAndCollectText() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过界面测试");

        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                SettingManager manager = new SettingManager(new InputHandler());
                Object root = manager.createRoot();
                assertNotNull(root);
                // 设置界面默认停在「按键」分类，切到「显示」才能看到语言那一行
                manager.showPanelForTest("framework.setting.tab.display");
                result.set(collectText((Node) root));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "建界面超时");
        if (failure.get() != null) {
            throw new AssertionError("建设置界面时抛异常: " + failure.get(), failure.get());
        }
        return result.get();
    }

    private static String collectText(Node node) {
        List<String> texts = new ArrayList<>();
        collect(node, texts, 0);
        return String.join("\n", texts);
    }

    private static void collect(Node node, List<String> out, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }
        out.add(indent + node.getClass().getSimpleName()
                + (node instanceof javafx.scene.control.Labeled labeled
                        && labeled.getText() != null && !labeled.getText().isEmpty()
                        ? "[" + labeled.getText() + "]" : ""));
        if (node instanceof javafx.scene.control.ScrollPane sp) {
            // ScrollPane 的内容是独立属性而不是 child，得单独走一遍
            if (sp.getContent() != null) {
                collect(sp.getContent(), out, depth + 1);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, out, depth + 1);
            }
        }
    }

    /**
     * 触发渲染引擎的就绪（面板构建需要 {@link RenderEngineProvider} 里有引擎）。
     *
     * <p>放在静态块里是因为它必须在任何 {@link SettingManager} 构造之前完成。
     */
    @BeforeAll
    static void initRenderEngine() {
        if (!RenderEngineProvider.getInstance().isInitialized()) {
            JavaFXRenderEngine engine = new JavaFXRenderEngine();
            engine.initialize(1200, 675);
            RenderEngineProvider.getInstance().setEngine(engine);
        }
    }

    /** 兜底：断言设置界面确实建出来了（避免 collect 到空字符串却「通过」）。 */
    @Test
    void settingsRootActuallyContainsText() throws Exception {
        ContentConfig.addLanguage("zh_cn", "简体中文");
        String tree = buildSettingsTreeAndCollectText();
        assertTrue(tree.contains("设置"), "至少标题应该是当前语言（zh_cn）:\n" + tree);
    }

    /**
     * 「取消」要能走完并回滚文本速度。
     *
     * <p>取消路径里有回滚逻辑（文本速度在编辑期间就即时生效），必须真的能被触发
     * 且不抛异常 —— 它是玩家会点到的按钮，不是冷代码。
     */
    @Test
    void cancelRollsBackLiveAppliedTextSpeed() throws Exception {
        Assumptions.assumeTrue(toolkitReady, "测试环境起不了 JavaFX 工具包，跳过界面测试");

        int original = FrameworkDataKeys.TEXT_SPEED.get();
        int edited = original == 137 ? 42 : 137;

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                SettingManager manager = new SettingManager(new InputHandler());
                manager.createRoot();
                // 模拟玩家在编辑期间把文本速度拖走（这一项是即时生效的）
                FrameworkDataKeys.TEXT_SPEED.setInt(edited);
                assertEquals(edited, FrameworkDataKeys.TEXT_SPEED.get());
                manager.closeWithoutSave();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "取消操作超时");
        if (failure.get() != null) {
            throw new AssertionError("取消设置时抛异常: " + failure.get(), failure.get());
        }
        assertEquals(original, FrameworkDataKeys.TEXT_SPEED.get(),
                "取消后文本速度应当回到打开面板时的值");
    }
}
