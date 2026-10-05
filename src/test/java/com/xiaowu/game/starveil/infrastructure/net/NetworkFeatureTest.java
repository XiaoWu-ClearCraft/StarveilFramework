package com.xiaowu.game.starveil.infrastructure.net;

import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import com.xiaowu.game.starveil.infrastructure.persistence.FrameworkDataKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联网相关功能的「不联网」部分测试。
 *
 * <p>真正的网络请求没法在单测里稳定复现（依赖被测机器当下的网络），
 * 所以这里只钉住纯逻辑与状态机：地址归一化、各家接口字段解析、HTTP Date 的三种格式、
 * 可信时间的「基准 + 单调时钟」推进方式，以及「没测出来 / 没查到」时的返回值。
 */
class NetworkFeatureTest {

    @BeforeEach
    @AfterEach
    void reset() {
        DataManager.resetForTest();
        TrustedTime.resetForTest();
        IpLocation.clearCacheForTest();
    }

    // ==================== IP 属地：城市名归一化 ====================

    @Test
    void cityNameDropsTheShiSuffix() {
        assertEquals("南昌", IpLocation.normalizeCity("南昌市"), "去掉「市」字");
        assertEquals("北京", IpLocation.normalizeCity("北京市"), "直辖市同样去掉");
        assertEquals("上海", IpLocation.normalizeCity("上海市 "), "顺带去掉空白");
        assertEquals("南昌", IpLocation.normalizeCity(" 南 昌 市 "), "名字里不该有空格");
        assertEquals("香港", IpLocation.normalizeCity("香港"), "没有「市」就原样返回");
        assertEquals("湘西土家族苗族自治州", IpLocation.normalizeCity("湘西土家族苗族自治州"),
                "自治州的「州」不能去，去了就废了");
        assertEquals("市", IpLocation.normalizeCity("市"), "只有一个「市」字时保留");
    }

    @Test
    void cityNameHandlesEmptyInput() {
        assertNull(IpLocation.normalizeCity(null));
        assertNull(IpLocation.normalizeCity("   "));
    }

    @Test
    void regionDropsTheSuffixOnly() {
        assertEquals("江西", IpLocation.normalizeRegion("江西省"));
        assertEquals("北京", IpLocation.normalizeRegion("北京市"), "直辖市也能去「市」");
        assertEquals("内蒙古自治区", IpLocation.normalizeRegion("内蒙古自治区"),
                "自治区的「区」不能去");
    }

    @Test
    void cityIsCutOutOfAFullAddress() {
        assertEquals("南昌", IpLocation.cityFromAddress("江西省南昌市"));
        assertEquals("南昌", IpLocation.cityFromAddress("江西省南昌市 电信"));
        assertEquals("南昌", IpLocation.cityFromAddress("江西省 南昌市"));
        assertEquals("北京", IpLocation.cityFromAddress("北京市朝阳区"));
        assertEquals("深圳", IpLocation.cityFromAddress("广东省深圳市南山区"));
        // 真的没有「市」字就返回 null：宁可不给，也不要瞎猜
        assertNull(IpLocation.cityFromAddress("某某地址"));
        assertNull(IpLocation.cityFromAddress("广东省（只是省名）"));
        assertNull(IpLocation.cityFromAddress(null));
    }

    // ==================== IP 属地：接口响应解析 ====================

    @Test
    void parsesEnglishStyleResponse() {
        IpLocation.Result r = IpLocation.parse(
                "{\"city\":\"Nanchang\",\"region\":\"Jiangxi\",\"country_name\":\"China\"}");
        assertEquals("Nanchang", r.city());
        assertEquals("Jiangxi", r.region());
        assertEquals("China", r.country());
        assertTrue(r.hasCity());
    }

    @Test
    void parsesChineseStyleResponseAndStripsTheSuffix() {
        // pconline 风格：pro/city 都带中文后缀
        IpLocation.Result r = IpLocation.parse(
                "{\"ip\":\"1.2.3.4\",\"pro\":\"江西省\",\"proCode\":\"360000\","
                        + "\"city\":\"南昌市\",\"cityCode\":\"360100\"}");
        assertEquals("南昌", r.city(), "返回给调用方的是去掉「市」的形式");
        assertEquals("江西", r.region());
    }

    @Test
    void fallsBackToSplittingTheAddressField() {
        IpLocation.Result r = IpLocation.parse("{\"addr\":\"江西省南昌市\"}");
        assertEquals("南昌", r.city());
    }

    @Test
    void malformedResponseYieldsNothingInsteadOfThrowing() {
        assertNull(IpLocation.parse("not json at all").city());
        assertNull(IpLocation.parse("[]").city());
        assertNull(IpLocation.parse(null).city());
    }

    // ==================== IP 属地：只查一次、查不到就是 null ====================

    @Test
    void cityIsNullUntilFetchedOnce() {
        // 启动时 preload 之后才会有值；测试里不发请求，所以这里必须是 null（不猜）
        assertFalse(IpLocation.isAvailable());
        assertNull(IpLocation.city(), "没查到就返回 null，而不是猜一个城市");
        assertNull(IpLocation.region());
        assertNull(IpLocation.current());
    }

    @Test
    void endpointsCanBeReconfiguredWithoutConnecting() {
        IpLocation.setUrls("https://example.invalid/geo");
        IpLocation.setUrls();   // 空 = 回到默认
    }

    // ==================== 可信时间 ====================

    @Test
    void parsesRfc1123HttpDate() {
        Long millis = TrustedTime.parseHttpDate("Tue, 3 Jun 2008 11:05:30 GMT");
        assertNotNull(millis);
        assertEquals(Instant.parse("2008-06-03T11:05:30Z").toEpochMilli(), millis);
    }

    @Test
    void parsesLegacyHttpDateFormats() {
        // RFC 850 与 asctime：规范要求接收方也能解析，虽然现实中几乎见不到
        Long rfc850 = TrustedTime.parseHttpDate("Tuesday, 03-Jun-08 11:05:30 GMT");
        assertNotNull(rfc850, "RFC 850 也要认");
        assertEquals(Instant.parse("2008-06-03T11:05:30Z").toEpochMilli(), rfc850);

        Long asctime = TrustedTime.parseHttpDate("Tue Jun 3 11:05:30 2008");
        assertNotNull(asctime, "asctime 也要认");
        assertEquals(Instant.parse("2008-06-03T11:05:30Z").toEpochMilli(), asctime,
                "asctime 没写时区，按 GMT 解释");
    }

    @Test
    void unparsableDateReturnsNullInsteadOfThrowing() {
        assertNull(TrustedTime.parseHttpDate(null));
        assertNull(TrustedTime.parseHttpDate(""));
        assertNull(TrustedTime.parseHttpDate("昨天"));
    }

    @Test
    void withoutSyncTheTrustedTimeIsJustTheSystemClock() {
        assertFalse(TrustedTime.isSynced());
        long before = System.currentTimeMillis();
        Instant now = TrustedTime.now();
        long after = System.currentTimeMillis();
        assertTrue(now.toEpochMilli() >= before - 50 && now.toEpochMilli() <= after + 50,
                "没同步过就退回本机时钟，不能让游戏跑不动");
    }

    /**
     * 可信时间的核心行为：<b>基准（联网要回来的时间戳）+ 单调时钟推进</b>。
     *
     * <p>这里不联网，直接往配置里塞一个「60 秒前同步过」的基准，然后验证
     * 恢复之后读到的是「基准 + 那 60 秒」—— 也就是玩家此刻把系统时间改掉也不影响它。
     */
    @Test
    void restoredBaseKeepsCountingWithTheMonotonicClock() {
        long trustedAtSync = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
        long wallAtSync = System.currentTimeMillis() - 60_000;   // 假装 60 秒前同步过

        FrameworkDataKeys.TRUSTED_TIME_BASE.set(trustedAtSync);
        FrameworkDataKeys.TRUSTED_TIME_BASE_WALL.set(wallAtSync);

        TrustedTime.loadFromConfig();

        assertTrue(TrustedTime.isSynced());
        assertEquals(trustedAtSync, TrustedTime.baseTrustedMillis() - 60_000, 2000,
                "基准 = 同步时的可信时间 + 本机时钟走过的那 60 秒");
        long expected = trustedAtSync + 60_000;
        assertTrue(Math.abs(TrustedTime.now().toEpochMilli() - expected) < 2000,
                "恢复后现在的时间约等于「基准 + 已过去的时间」，实测 "
                        + TrustedTime.now());
    }

    @Test
    void clockRolledBackwardsIsNotTrusted() {
        long trustedAtSync = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
        // 本机时钟比「上次同步时间」还早：说明被往回拨过，这段时间差不可信
        FrameworkDataKeys.TRUSTED_TIME_BASE.set(trustedAtSync);
        FrameworkDataKeys.TRUSTED_TIME_BASE_WALL.set(System.currentTimeMillis() + 3_600_000);

        TrustedTime.loadFromConfig();

        assertTrue(TrustedTime.isSynced());
        assertEquals(trustedAtSync, TrustedTime.now().toEpochMilli(), 2000,
                "往回拨时不把负的时间差算进去，直接从基准起算");
    }

    @Test
    void trustedTimeAdvancesWithRealTime() throws Exception {
        long trustedAtSync = Instant.parse("2030-01-01T00:00:00Z").toEpochMilli();
        FrameworkDataKeys.TRUSTED_TIME_BASE.set(trustedAtSync);
        FrameworkDataKeys.TRUSTED_TIME_BASE_WALL.set(System.currentTimeMillis());
        TrustedTime.loadFromConfig();

        long first = TrustedTime.now().toEpochMilli();
        Thread.sleep(120);
        long second = TrustedTime.now().toEpochMilli();
        assertTrue(second - first >= 100, "可信时间必须自己往前走，实测走了 " + (second - first) + "ms");
        assertNotNull(TrustedTime.nowLocal());
        assertNotNull(TrustedTime.nowZoned());
    }

    @Test
    void missingConfigMeansNotSynced() {
        TrustedTime.loadFromConfig();
        assertFalse(TrustedTime.isSynced(), "配置里没有基准时不该假装同步过");
    }

    // ==================== 联网状态 ====================

    @Test
    void beforeTheFirstProbeWeAssumeOnline() {
        // 宁可在还没测出来时当「有网」，也不要给玩家弹一个假的「你没网」
        assertEquals(NetworkStatus.State.UNKNOWN, NetworkStatus.state());
        assertTrue(NetworkStatus.isOnline());
        assertFalse(NetworkStatus.isKnown());
    }

    @Test
    void canResolveIsFalseForNonsenseHosts() {
        assertFalse(NetworkStatus.canResolve("this-host-should-not-exist.invalid"));
    }

    @Test
    void pollingCanBeTurnedOffInFavourOfInterfaceEvents() {
        NetworkStatus.setIntervalSeconds(0);
        assertEquals(0, NetworkStatus.intervalSeconds(), "0 = 只靠接口事件触发");
        NetworkStatus.setIntervalSeconds(30);
        assertEquals(30, NetworkStatus.intervalSeconds());
    }

    @Test
    void eventDrivenModeIsOffUntilStarted() {
        // 没启动就不该声称在用事件（测试里刻意不去发真实网络请求）
        assertFalse(NetworkStatus.isEventDriven());
    }

    @Test
    void networkEndpointsCanBeReconfiguredWithoutConnecting() {
        // 只验证配置入口不会抛异常（真正的探测要联网，单测里不做）
        NetworkStatus.setEndpoints("127.0.0.1:1", "example.com");
        NetworkStatus.setTimeoutMillis(300);
        NetworkStatus.setEndpoints();          // 空 = 回到默认
        TrustedTime.setUrls();
    }
}
