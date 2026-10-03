package com.xiaowu.game.starveil.game.ecs;

import com.xiaowu.game.starveil.game.ecs.sys.SpriteSyncSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剧情暂停测试。
 *
 * <p>剧情（视觉小说）期间默认冻结整个世界模拟，但：
 * <ul>
 *   <li>纯表现层的 {@link SpriteSyncSystem} 必须继续跑，否则被豁免的 NPC 动了、画面不动；</li>
 *   <li>章节显式豁免的系统（如 NPC AI）必须继续跑。</li>
 * </ul>
 */
class WorldStoryPauseTest {

    /** 普通系统：剧情暂停时应当停下。 */
    private static final class PlainSystem implements EcsSystem {
        int calls = 0;

        @Override
        public void update(World world, double deltaTime) {
            calls++;
        }
    }

    /** 可豁免的系统：被 exemptFromStoryPause 后应当继续跑。 */
    private static final class ExemptableSystem implements EcsSystem {
        int calls = 0;

        @Override
        public void update(World world, double deltaTime) {
            calls++;
        }
    }

    /** 表现层系统：不需要显式豁免就应当一直跑。 */
    private static final class PresentationSystem implements EcsSystem {
        int calls = 0;

        @Override
        public boolean runsDuringStoryPause() {
            return true;
        }

        @Override
        public void update(World world, double deltaTime) {
            calls++;
        }
    }

    @Test
    void notPausedByDefault() {
        World w = new World();
        assertFalse(w.isStoryPaused());
        PlainSystem s = new PlainSystem();
        w.addSystem(s);
        w.update(1.0 / 60);
        assertEquals(1, s.calls, "未暂停时系统应正常跑");
    }

    @Test
    void storyPauseFreezesPlainSystems() {
        World w = new World();
        PlainSystem s = new PlainSystem();
        w.addSystem(s);

        w.update(1.0 / 60);
        w.setStoryPaused(true);
        w.update(1.0 / 60);
        w.update(1.0 / 60);

        assertEquals(1, s.calls, "剧情暂停后普通系统不应再被驱动");
    }

    @Test
    void resumingRunsSystemsAgain() {
        World w = new World();
        PlainSystem s = new PlainSystem();
        w.addSystem(s);

        w.setStoryPaused(true);
        w.update(1.0 / 60);
        assertEquals(0, s.calls);

        w.setStoryPaused(false);
        w.update(1.0 / 60);
        assertEquals(1, s.calls, "恢复后系统应继续跑");
    }

    @Test
    void exemptedSystemKeepsRunning() {
        World w = new World();
        ExemptableSystem keep = new ExemptableSystem();
        PlainSystem stopped = new PlainSystem();
        w.addSystem(keep);
        w.addSystem(stopped);

        w.setStoryPaused(true);
        w.exemptFromStoryPause(ExemptableSystem.class);
        w.update(1.0 / 60);

        assertEquals(1, keep.calls, "被豁免的系统应继续跑（例如 NPC 移动）");
        assertEquals(0, stopped.calls, "未豁免的系统仍应冻结");
    }

    @Test
    void presentationSystemAlwaysRuns() {
        World w = new World();
        PresentationSystem s = new PresentationSystem();
        w.addSystem(s);

        w.setStoryPaused(true);
        w.update(1.0 / 60);

        assertEquals(1, s.calls,
                "表现层系统必须一直跑，否则被豁免的 NPC 动了画面却不动");
    }

    @Test
    void shouldUpdateReportsPerSystem() {
        World w = new World();
        ExemptableSystem exempt = new ExemptableSystem();
        PlainSystem plain = new PlainSystem();
        PresentationSystem pres = new PresentationSystem();
        w.addSystem(exempt);
        w.addSystem(plain);
        w.addSystem(pres);

        // 未暂停时全部放行
        assertTrue(w.shouldUpdate(exempt));
        assertTrue(w.shouldUpdate(plain));
        assertTrue(w.shouldUpdate(pres));

        w.setStoryPaused(true);
        assertFalse(w.shouldUpdate(exempt), "未豁免的系统应被拦下");
        assertFalse(w.shouldUpdate(plain));
        assertTrue(w.shouldUpdate(pres), "表现层始终放行");

        w.exemptFromStoryPause(ExemptableSystem.class);
        assertTrue(w.shouldUpdate(exempt));
        assertFalse(w.shouldUpdate(plain));
    }

    @Test
    void clearExemptionsRestoresFullFreeze() {
        World w = new World();
        ExemptableSystem s = new ExemptableSystem();
        w.addSystem(s);

        w.exemptFromStoryPause(ExemptableSystem.class);
        w.setStoryPaused(true);
        w.update(1.0 / 60);
        assertEquals(1, s.calls);

        w.clearStoryPauseExemptions();
        w.update(1.0 / 60);
        assertEquals(1, s.calls, "清掉豁免后应重新冻结");
    }

    @Test
    void spriteSyncIsAlwaysExempt() {
        World w = new World();
        SpriteSyncSystem sync = new SpriteSyncSystem();
        w.addSystem(sync);
        w.setStoryPaused(true);
        assertTrue(w.shouldUpdate(sync), "精灵同步是纯表现层，剧情暂停时也必须跑");
    }

    // ==================== 系统管线重复注册（移速翻倍的根因） ====================

    @Test
    void clearSystemsEmptiesThePipeline() {
        World w = new World();
        w.addSystem(new PlainSystem());
        w.addSystem(new ExemptableSystem());
        assertEquals(2, w.systemCount());

        w.clearSystems();
        assertEquals(0, w.systemCount());
    }

    /**
     * 回归：卸下世界再重新挂载时，必须先 clearSystems 再 addSystem。
     *
     * <p>addSystem 按类型追加实例（不去重），而 mountWorld 每次都 new 一套系统。
     * 于是「挂载 → 卸下（系统没被清）→ 再挂载」会让同类型的两个实例都作用在
     * 同一个玩家实体上，每帧推进两次位移 —— 表现为「玩家和 NPC 移速翻倍」。
     */
    @Test
    void duplicateRegistrationWouldDoubleApplyTheSameLogic() {
        // 共享的「位移累加器」，模拟两个 PlayerControlSystem 作用于同一玩家
        int[] playerMoved = {0};

        World buggy = new World();
        buggy.addSystem(new MoverSystem(playerMoved));   // 第一次挂载
        buggy.addSystem(new MoverSystem(playerMoved));   // 重新挂载时重复注册
        buggy.update(1.0 / 60);
        assertEquals(2, playerMoved[0],
                "同类型系统注册两份时，位移会被叠加两次 —— 这就是移速翻倍");

        // 正确写法：重新挂载前先清空
        playerMoved[0] = 0;
        World fixed = new World();
        fixed.addSystem(new MoverSystem(playerMoved));
        fixed.clearSystems();
        fixed.addSystem(new MoverSystem(playerMoved));
        fixed.update(1.0 / 60);
        assertEquals(1, playerMoved[0], "先清后建后，位移每帧只应推进一次");
        assertEquals(1, fixed.systemCount());
    }

    /** 每次 update 都推进一格位移，用来观察「同一逻辑被执行了几次」。 */
    private static final class MoverSystem implements EcsSystem {
        private final int[] counter;

        MoverSystem(int[] counter) {
            this.counter = counter;
        }

        @Override
        public void update(World world, double deltaTime) {
            counter[0]++;
        }
    }
}
