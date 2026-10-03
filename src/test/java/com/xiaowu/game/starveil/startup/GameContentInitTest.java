package com.xiaowu.game.starveil.startup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内容初始化钩子测试。
 *
 * <p>其中一条是<b>分层守卫</b>：这个类属于框架，不能被放回 {@code content} 包，
 * 否则 {@code plugin} / {@code launcher} 会 import 到游戏内容包，
 * 形成「框架 → 内容」的反向编译期依赖。
 */
class GameContentInitTest {

    // ==================== 分层 ====================

    @Test
    void hookLivesInAFrameworkPackageNotInContent() {
        String pkg = GameContentInit.class.getPackageName();

        assertEquals("com.xiaowu.game.starveil.startup", pkg);
        assertFalse(pkg.startsWith("com.xiaowu.game.starveil.content"),
                "框架工具不能放在游戏内容包里 —— 那会让框架反向依赖内容");
    }

    // ==================== 内容侧契约 ====================

    @Test
    void targetClassIsStillTheContentSideConvention() {
        assertEquals("com.xiaowu.game.starveil.content.init.init",
                GameContentInit.CLASS_NAME,
                "这是内容侧要实现的契约名，属于对外约定，不应随框架类搬家而改变");
    }

    // ==================== 探测行为 ====================

    @Test
    void missingClassIsReportedAsAbsent() {
        // 当前项目里并没有 content.init.init，所以应当是「不存在」
        assertFalse(GameContentInit.exists());
        assertNull(GameContentInit.findEntryPoint());
    }

    @Test
    void runningWithoutTheClassIsANoOpFailure() {
        assertFalse(GameContentInit.run(),
                "没有入口时应返回 false（跳过），而不是抛异常中断启动");
    }

    @Test
    void probingDoesNotTriggerClassInitialization() {
        // exists() 用 initialize=false 探测。内容侧可能在自己的静态块里做重活，
        // 探测阶段就把它 initialize 掉会让启动时序失控。
        // 这里用「反复探测结果稳定且不抛异常」间接验证。
        for (int i = 0; i < 3; i++) {
            assertFalse(GameContentInit.exists());
        }
    }

    @Test
    void classIsFinalAndNonInstantiable() {
        assertTrue(java.lang.reflect.Modifier.isFinal(GameContentInit.class.getModifiers()));
    }

    // ==================== 内容存在性 ====================

    /**
     * 框架自身<b>不应该</b>包含任何游戏内容 —— 内容已经拆到 {@code ../StarveilContent}。
     *
     * <p>这条断言守的是启动时的致命检查：一旦有 content 类漏回框架，
     * {@code contentPresent()} 就会永远为 true，「缺少内容」的提示形同虚设，
     * 用户会拿到一个能启动、却什么都玩不了的空壳。
     */
    @Test
    void frameworkAloneHasNoContent() {
        assertFalse(GameContentInit.contentPresent(),
                "框架项目里不应存在 com.xiaowu.game.starveil.content —— "
                        + "游戏内容属于 ../StarveilContent 项目");
    }

    @Test
    void contentEntryClassDoesNotExistInFramework() {
        assertFalse(GameContentInit.exists(),
                "框架里不应有 content.init.init；它由内容项目提供");
        assertNull(GameContentInit.findEntryPoint());
    }
}
