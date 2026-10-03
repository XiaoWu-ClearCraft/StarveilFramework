package com.xiaowu.game.starveil.infrastructure.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生命周期事件总线测试。
 *
 * <p>核心语义：<b>广播方不需要知道有谁在听；没有监听者时就是一次空操作。</b>
 * 这几条是发布点可以随便加、而不会给框架增加耦合的前提。
 */
class LifecycleTest {

    @BeforeEach
    @AfterEach
    void reset() {
        Lifecycle.resetForTest();
    }

    // ==================== 基本广播 ====================

    @Test
    void publishReachesListener() {
        List<String> seen = new ArrayList<>();
        LifecycleEvents.onMenuShown(e -> seen.add(e.title()));

        LifecycleEvents.menuShown("主菜单");

        assertEquals(List.of("主菜单"), seen);
    }

    @Test
    void publishWithNoListenerIsANoOp() {
        // 这就是「没有就抛弃」：不抛异常、不产生任何副作用，
        // 发布方不需要为「没人听」写兜底分支
        assertDoesNotThrow(() -> LifecycleEvents.menuShown("主菜单"));
        assertDoesNotThrow(() -> LifecycleEvents.tutorialCompleted());
        assertEquals(0, Lifecycle.listenerCount(LifecycleEvents.MenuShown.class));
    }

    @Test
    void allListenersReceiveTheEventInRegistrationOrder() {
        List<String> order = new ArrayList<>();
        LifecycleEvents.onChapterChanged(e -> order.add("first"));
        LifecycleEvents.onChapterChanged(e -> order.add("second"));
        LifecycleEvents.onChapterChanged(e -> order.add("third"));

        LifecycleEvents.chapterChanged(3, "NORMAL");

        assertEquals(List.of("first", "second", "third"), order);
    }

    @Test
    void listenersOfOtherEventsAreNotCalled() {
        List<String> seen = new ArrayList<>();
        LifecycleEvents.onGameSaved(e -> seen.add("saved"));
        LifecycleEvents.onGameLoaded(e -> seen.add("loaded"));

        LifecycleEvents.gameSaved(2);

        assertEquals(List.of("saved"), seen, "只有同一事件的监听者会被调用");
    }

    @Test
    void eventCarriesItsPayload() {
        List<LifecycleEvents.ChapterChanged> seen = new ArrayList<>();
        LifecycleEvents.onChapterChanged(seen::add);

        LifecycleEvents.chapterChanged(7, "VISUAL_NOVEL");

        assertEquals(1, seen.size());
        assertEquals(7, seen.get(0).chapter());
        assertEquals("VISUAL_NOVEL", seen.get(0).mode());
    }

    // ==================== 容错 ====================

    @Test
    void oneFailingListenerDoesNotStopTheOthers() {
        List<String> seen = new ArrayList<>();
        LifecycleEvents.onMenuShown(e -> {
            throw new IllegalStateException("这个监听者坏了");
        });
        LifecycleEvents.onMenuShown(e -> seen.add("还是收到了"));

        assertDoesNotThrow(() -> LifecycleEvents.menuShown("主菜单"),
                "监听者出错不该把广播方拖垮");
        assertEquals(List.of("还是收到了"), seen,
                "一个监听者出错不该影响其它监听者");
    }

    @Test
    void nullEventIsIgnored() {
        List<String> seen = new ArrayList<>();
        LifecycleEvents.onMenuShown(e -> seen.add("x"));

        assertDoesNotThrow(() -> Lifecycle.publish(null));
        assertTrue(seen.isEmpty());
    }

    @Test
    void nullListenerOrTypeIsIgnored() {
        assertDoesNotThrow(() -> Lifecycle.on(LifecycleEvents.MenuShown.class, null));
        assertDoesNotThrow(() -> Lifecycle.on(null, e -> { }));
        assertEquals(0, Lifecycle.listenerCount(LifecycleEvents.MenuShown.class));
    }

    // ==================== 一次性与取消 ====================

    @Test
    void onceListenerFiresOnlyOnce() {
        List<String> seen = new ArrayList<>();
        LifecycleEvents.onceMenuShown(e -> seen.add(e.title()));

        LifecycleEvents.menuShown("第一次");
        LifecycleEvents.menuShown("第二次");

        assertEquals(List.of("第一次"), seen);
        assertEquals(0, Lifecycle.listenerCount(LifecycleEvents.MenuShown.class),
                "一次性监听触发后应当自动摘掉");
    }

    @Test
    void cancelStopsTheListener() {
        List<String> seen = new ArrayList<>();
        Lifecycle.Subscription<LifecycleEvents.MenuShown> sub =
                LifecycleEvents.onMenuShown(e -> seen.add(e.title()));
        assertTrue(sub.isActive());

        LifecycleEvents.menuShown("收到");
        sub.cancel();
        LifecycleEvents.menuShown("不该收到");

        assertEquals(List.of("收到"), seen);
        assertFalse(sub.isActive());
        assertDoesNotThrow(sub::cancel, "重复取消应当无副作用");
    }

    @Test
    void subscriptionMayBeDiscarded() {
        // 「没有就抛弃」的另一面：拿到句柄不用也没关系，监听依然有效
        LifecycleEvents.onMenuShown(e -> { });
        assertEquals(1, Lifecycle.listenerCount(LifecycleEvents.MenuShown.class));
    }

    @Test
    void cancellingTheOnlyListenerMakesTheEventDiscardedAgain() {
        Lifecycle.Subscription<LifecycleEvents.MenuShown> sub =
                LifecycleEvents.onMenuShown(e -> { });

        sub.cancel();

        assertDoesNotThrow(() -> LifecycleEvents.menuShown("没人听了"));
    }

    // ==================== 监听者清单 ====================

    @Test
    void listenerCountAndTypesReflectSubscriptions() {
        assertEquals(0, Lifecycle.listenerCount(LifecycleEvents.LanguageChanged.class));

        LifecycleEvents.onLanguageChanged(e -> { });
        LifecycleEvents.onGameSaved(e -> { });

        assertEquals(1, Lifecycle.listenerCount(LifecycleEvents.LanguageChanged.class));
        assertEquals(1, Lifecycle.listenerCount(LifecycleEvents.GameSaved.class));
        assertEquals(0, Lifecycle.listenerCount(LifecycleEvents.MenuShown.class));

        List<String> types = Lifecycle.listenerTypes();
        assertTrue(types.contains(LifecycleEvents.LanguageChanged.class.getName()));
        assertTrue(types.contains(LifecycleEvents.GameSaved.class.getName()));
    }

    /**
     * 事件类型匹配按<b>类名</b>而不是 {@code Class} 对象。
     *
     * <p>插件可能由独立类加载器加载，同一个事件类在两个加载器里会得到两个不同的
     * {@code Class} 对象；用对象当键会让插件注册的监听看不见框架的广播。
     * 这里断言键就是类名 —— 类名相同即命中同一份列表。
     */
    @Test
    void listenerLookupIsKeyedByNameNotByClassIdentity() {
        assertEquals(LifecycleEvents.MenuShown.class.getName(),
                Lifecycle.keyOf(LifecycleEvents.MenuShown.class),
                "键必须只由类名决定，不能带上加载器身份");

        LifecycleEvents.onMenuShown(e -> { });
        assertEquals(1, Lifecycle.listenerCount(LifecycleEvents.MenuShown.class));
    }

    // ==================== 框架发布点 ====================

    @Test
    void frameworkPublishHelpersBuildTheExpectedEvents() {
        List<Integer> savedSlots = new ArrayList<>();
        List<Boolean> started = new ArrayList<>();

        LifecycleEvents.onGameSaved(e -> savedSlots.add(e.slot()));
        LifecycleEvents.onGameStarted(e -> started.add(e.fromSave()));

        LifecycleEvents.gameSaved(4);
        LifecycleEvents.gameStarted(2, false);
        LifecycleEvents.gameStarted(5, true);

        assertEquals(List.of(4), savedSlots);
        assertEquals(List.of(false, true), started);
    }

    @Test
    void settingsClosedCarriesWhetherItWasApplied() {
        List<Boolean> applied = new ArrayList<>();
        LifecycleEvents.onSettingsClosed(e -> applied.add(e.applied()));

        LifecycleEvents.settingsClosed(true);
        LifecycleEvents.settingsClosed(false);

        assertEquals(List.of(true, false), applied);
    }
}
