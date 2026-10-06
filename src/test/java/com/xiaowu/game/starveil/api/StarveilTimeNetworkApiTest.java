package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.infrastructure.net.NetworkStatus;
import com.xiaowu.game.starveil.infrastructure.net.TrustedTime;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 面向内容的 {@code api} 门面：时间与网络状况。
 *
 * <p>门面本身只是转发，所以这里钉的是「转发没错、语义写清楚」：
 * 可信时间与系统时间各走各的、没同步时的返回值、以及联网状态那几个乐观默认值。
 * 真正的网络请求不在这里测（见 {@code NetworkFeatureTest} 的说明）。
 */
class StarveilTimeNetworkApiTest {

    @BeforeEach
    @AfterEach
    void reset() {
        DataManager.resetForTest();
        // 只用公开入口复位：没存过基准时 loadFromConfig 会把状态清回「未同步」
        TrustedTime.loadFromConfig();
    }

    // ==================== 门面接线 ====================

    @Test
    void facadesAreSingletonsHangingOffStarveil() {
        assertSame(StarveilTime.getInstance(), Starveil.time());
        assertSame(StarveilNetwork.getInstance(), Starveil.network());
    }

    // ==================== 时间 ====================

    @Test
    void trustedTimeForwardsToTheFrameworkClock() {
        TrustedTime.loadFromConfig();       // 没有基准 → 未同步，返回本机时钟
        assertFalse(Starveil.time().isSynced());
        assertEquals(TrustedTime.now().toEpochMilli(),
                Starveil.time().trustedNow().toEpochMilli());
        assertNotNull(Starveil.time().trustedNowLocal());
        assertNotNull(Starveil.time().trustedNowZoned());
    }

    @Test
    void trustedEpochMillisMatchesTrustedNow() {
        TrustedTime.loadFromConfig();
        assertEquals(Starveil.time().trustedNow().toEpochMilli(),
                Starveil.time().trustedEpochMillis());
    }

    @Test
    void systemTimeIsTheRawClockAndCloseToNow() {
        long before = System.currentTimeMillis();
        long system = Starveil.time().systemEpochMillis();
        long after = System.currentTimeMillis();
        assertTrue(system >= before && system <= after, "系统时间就该是原样的本机时钟");
        assertEquals(Starveil.time().systemNow().toEpochMilli(), system);
    }

    @Test
    void nothingSyncedMeansNoSourceAndNoOffset() {
        TrustedTime.loadFromConfig();
        assertNull(Starveil.time().source(), "没同步过就没有来源");
        // 未同步时可信时间就是本机时钟，偏差应当≈0
        assertTrue(Math.abs(Starveil.time().offsetMillis()) < 2000,
                "未同步时偏差不该离谱: " + Starveil.time().offsetMillis());
    }

    @Test
    void trustedTimeAdvancesLikeRealTime() throws Exception {
        TrustedTime.loadFromConfig();
        Instant first = Starveil.time().trustedNow();
        Thread.sleep(30);
        Instant second = Starveil.time().trustedNow();
        assertTrue(second.isAfter(first), "可信时间要往前走");
    }

    // ==================== 时间戳 ⇄ 日期时间 ====================

    @Test
    void aTimestampConvertsToThePlayersLocalDateTime() {
        // 用「本地时间 → 时间戳 → 本地时间」转一圈：跑在哪个时区都对
        LocalDateTime local = LocalDateTime.of(2026, 10, 6, 8, 30, 15);
        long t = Starveil.time().toEpochMillis(local);

        assertEquals(local, Starveil.time().toLocalDateTime(t));
        assertEquals(local.toLocalDate(), Starveil.time().toLocalDate(t));
        assertEquals(local.toLocalTime(), Starveil.time().toLocalTime(t));
        assertEquals(local.atZone(ZoneId.systemDefault()), Starveil.time().toZonedDateTime(t));
        assertEquals(ZoneId.systemDefault(), Starveil.time().zone());
    }

    @Test
    void hourOfDayIsWhatGreetingsNeed() {
        assertEquals(8, Starveil.time().hourOfDay(
                Starveil.time().toEpochMillis(LocalDateTime.of(2026, 10, 6, 8, 30))));
        assertEquals(0, Starveil.time().hourOfDay(
                Starveil.time().toEpochMillis(LocalDateTime.of(2026, 10, 6, 0, 5))));
        assertEquals(23, Starveil.time().hourOfDay(
                Starveil.time().toEpochMillis(LocalDateTime.of(2026, 10, 6, 23, 59))));

        int now = Starveil.time().hourOfDay();
        assertTrue(now >= 0 && now <= 23, "现在的小时数要在 0-23: " + now);
    }

    @Test
    void formatUsesDateTimeFormatterPatterns() {
        long t = Starveil.time().toEpochMillis(LocalDateTime.of(2026, 10, 6, 8, 30));
        assertEquals("2026-10-06 08:30", Starveil.time().format(t, "yyyy-MM-dd HH:mm"));
        assertEquals("08:30", Starveil.time().format(t, "HH:mm"));
        assertEquals("10月6日", Starveil.time().format(t, "M月d日"));
        // 空格式串退化成 ISO 文本，而不是抛异常
        assertNotNull(Starveil.time().format(t, ""));
    }

    // ==================== 网络状况 ====================

    @Test
    void beforeTheFirstProbeTheApiPretendsOnline() {
        // 与框架语义一致：还没测出来就先当有网，不给玩家弹假的「你没网」
        assertEquals(StarveilNetwork.State.UNKNOWN, Starveil.network().state());
        assertFalse(Starveil.network().isKnown());
        assertTrue(Starveil.network().isOnline());
    }

    @Test
    void stateEnumMirrorsTheInternalStateMachine() {
        assertEquals(StarveilNetwork.State.valueOf(NetworkStatus.state().name()),
                Starveil.network().state());
    }

    @Test
    void sourceAndEventDrivenFlagsAreQueryable() {
        assertNotNull(Starveil.network().source());
        assertFalse(Starveil.network().source().isEmpty());
        // 没启动过探测，就还没在听接口事件（测试里刻意不发真实网络请求）
        assertFalse(Starveil.network().isEventDriven());
    }

    @Test
    void nonsenseHostDoesNotResolve() {
        assertFalse(Starveil.network().canResolve("this-host-should-not-exist.invalid"));
    }

    @Test
    void listenersCanBeAddedAndRemovedByTheSameInstance() {
        java.util.function.Consumer<StarveilNetwork.State> listener = state -> { /* 不会触发 */ };
        Starveil.network().addListener(listener);
        Starveil.network().addListener(listener);      // 同实例重复注册只算一次
        Starveil.network().addListener(null);          // 空值忽略，别炸
        Starveil.network().removeListener(listener);
        Starveil.network().removeListener(listener);   // 再取消一次也不该炸
        Starveil.network().removeListener(null);
    }
}
