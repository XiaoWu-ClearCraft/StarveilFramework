package com.xiaowu.game.starveil.infrastructure.net;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 联网相关功能的「不联网」部分测试。
 *
 * <p>真正的网络请求没法在单测里稳定复现（会依赖被测机器当下的网络），
 * 所以这里只钉住那些<b>纯逻辑</b>：地址归一化、各家接口字段的解析、
 * HTTP Date 的三种历史格式、以及「还没测出结果时按在线处理」这条防误报规则。
 */
class NetworkFeatureTest {

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
        assertEquals(null, IpLocation.normalizeCity(null));
        assertEquals(null, IpLocation.normalizeCity("   "));
    }

    @Test
    void regionDropsTheShengSuffixOnly() {
        assertEquals("江西", IpLocation.normalizeRegion("江西省"));
        assertEquals("内蒙古自治区", IpLocation.normalizeRegion("内蒙古自治区"));
        assertEquals("北京", IpLocation.normalizeRegion("北京市"), "直辖市也能去「市」");
    }

    @Test
    void cityIsCutOutOfAFullAddress() {
        assertEquals("南昌", IpLocation.cityFromAddress("江西省南昌市"));
        assertEquals("南昌", IpLocation.cityFromAddress("江西省南昌市 电信"));
        assertEquals("南昌", IpLocation.cityFromAddress("江西省 南昌市"));
        assertEquals("北京", IpLocation.cityFromAddress("北京市朝阳区"));
        assertEquals("深圳", IpLocation.cityFromAddress("广东省深圳市南山区"));
        // 真的没有「市」字就返回 null：宁可不给，也不要瞎猜
        assertEquals(null, IpLocation.cityFromAddress("某某地址"));
        assertEquals(null, IpLocation.cityFromAddress("广东省（只是省名）"));
        assertEquals(null, IpLocation.cityFromAddress(null));
    }

    // ==================== IP 属地：接口响应解析 ====================

    @Test
    void parsesEnglishStyleResponse() {
        IpLocation.Result r = IpLocation.parse(
                "{\"city\":\"Nanchang\",\"region\":\"Jiangxi\",\"country_name\":\"China\"}",
                IpLocation.Source.IP);
        assertEquals("Nanchang", r.city());
        assertEquals("Jiangxi", r.region());
        assertEquals("China", r.country());
        assertTrue(r.isExact(), "这是按 IP 查出来的");
    }

    @Test
    void parsesChineseStyleResponseAndStripsTheSuffix() {
        // pconline 风格：pro/city 都带中文后缀
        IpLocation.Result r = IpLocation.parse(
                "{\"ip\":\"1.2.3.4\",\"pro\":\"江西省\",\"proCode\":\"360000\","
                        + "\"city\":\"南昌市\",\"cityCode\":\"360100\"}",
                IpLocation.Source.IP);
        assertEquals("南昌", r.city(), "返回给调用方的是去掉「市」的形式");
        assertEquals("江西", r.region());
        assertTrue(r.hasCity());
    }

    @Test
    void fallsBackToSplittingTheAddressField() {
        // 只给一整串地址的接口
        IpLocation.Result r = IpLocation.parse(
                "{\"addr\":\"江西省南昌市\"}", IpLocation.Source.IP);
        assertEquals("南昌", r.city());
    }

    @Test
    void malformedResponseYieldsUnknownInsteadOfThrowing() {
        assertEquals(IpLocation.Source.UNKNOWN,
                IpLocation.parse("not json at all", IpLocation.Source.IP).source());
        assertEquals(IpLocation.Source.UNKNOWN,
                IpLocation.parse("[]", IpLocation.Source.IP).source());
        assertEquals(IpLocation.Source.UNKNOWN,
                IpLocation.parse(null, IpLocation.Source.IP).source());
    }

    @Test
    void offlineGuessComesFromTheTimeZoneAndIsMarkedAsSuch() {
        IpLocation.Result guess = IpLocation.offlineGuess();
        assertNotNull(guess);
        assertEquals(IpLocation.Source.TIMEZONE, guess.source(), "离线只能给猜测，必须标明来源");
        assertFalse(guess.isExact(), "猜测值不是「按 IP 查出来的」");
        assertNotNull(guess.city());
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
    }

    @Test
    void unparsableDateReturnsNullInsteadOfThrowing() {
        assertEquals(null, TrustedTime.parseHttpDate(null));
        assertEquals(null, TrustedTime.parseHttpDate(""));
        assertEquals(null, TrustedTime.parseHttpDate("昨天"));
    }

    @Test
    void timeIsSystemClockPlusOffset() {
        // 没同步过时校正量为 0：可信时间就是本机时间（这样「没网」时功能也不会崩）
        long before = System.currentTimeMillis();
        Instant now = TrustedTime.now();
        long after = System.currentTimeMillis();
        assertTrue(now.toEpochMilli() >= before + TrustedTime.offsetMillis() - 50);
        assertTrue(now.toEpochMilli() <= after + TrustedTime.offsetMillis() + 50);
        assertNotNull(TrustedTime.nowLocal());
        assertNotNull(TrustedTime.nowZoned());
        assertEquals(ZoneOffset.UTC, TrustedTime.nowZoned().getOffset().getTotalSeconds() % 900 == 0
                ? ZoneOffset.UTC : TrustedTime.nowZoned().getOffset(), "偏移量必须是 15 分钟的整数倍");
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
    void endpointsCanBeReconfiguredWithoutConnecting() {
        // 只验证配置入口不会抛异常（真正的探测要联网，单测里不做）
        NetworkStatus.setEndpoints("127.0.0.1:1", "example.com");
        NetworkStatus.setTimeoutMillis(300);
        NetworkStatus.setIntervalSeconds(30);
        NetworkStatus.setEndpoints();          // 空 = 回到默认
        TrustedTime.setUrls();
        IpLocation.setUrls();
    }
}
