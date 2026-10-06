package com.xiaowu.game.starveil.api;

import com.xiaowu.game.starveil.infrastructure.net.TrustedTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 时间 API —— 本机时间与<b>可信时间</b>。
 *
 * <h2>两种「现在」，别弄混</h2>
 * <ul>
 *   <li>{@link #trustedNow()}：<b>该用这个</b>。联网取回来的基准时间戳 + 单调时钟推进，
 *       玩家在游戏运行期间改系统时钟也影响不了它（冷却、每日重置、限时活动都用它）。</li>
 *   <li>{@link #systemNow()}：本机时钟原样返回。玩家想改就改，只适合当「显示用的挂钟」
 *       或者调试对比，<b>不要用来做任何判定</b>。</li>
 * </ul>
 *
 * <h2>没网 / 还没同步过怎么办</h2>
 * {@link #trustedNow()} 会退回本机时钟 —— 游戏不该因为取不到时间就跑不动。
 * 但要判断「这个时间值不值得信」，请用 {@link #isSynced()}：
 *
 * <pre>{@code
 * Instant now = Starveil.time().trustedNow();
 * if (!Starveil.time().isSynced()) {
 *     // 还没拿到可信基准（首次启动 + 没网），此时的 now 其实就是本机时钟
 * }
 * }</pre>
 *
 * <p>启动时框架会自动恢复上次的基准并尝试联网同步（见 {@code docs/network.md}），
 * 内容一般不需要自己调 {@link #syncAsync()}。
 */
public class StarveilTime {

    private static final StarveilTime INSTANCE = new StarveilTime();

    private StarveilTime() {
    }

    public static StarveilTime getInstance() {
        return INSTANCE;
    }

    /**
     * 可信时间。运行期间不受系统时钟调整影响；从没同步过时退回本机时钟
     * （用 {@link #isSynced()} 判断可不可信）。
     */
    public Instant trustedNow() {
        return TrustedTime.now();
    }

    /** 可信时间的本地日期时间（按本机时区）。 */
    public LocalDateTime trustedNowLocal() {
        return TrustedTime.nowLocal();
    }

    /** 可信时间的带时区日期时间。 */
    public ZonedDateTime trustedNowZoned() {
        return TrustedTime.nowZoned();
    }

    /** 可信时间的毫秒时间戳 —— 存档、比较大小的便捷写法。 */
    public long trustedEpochMillis() {
        return TrustedTime.now().toEpochMilli();
    }

    /** 本机时钟（不校正）。玩家能改，别拿来做判定。 */
    public Instant systemNow() {
        return TrustedTime.systemNow();
    }

    /** 本机时钟的毫秒时间戳。 */
    public long systemEpochMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 可信时间是否真的可信：同步成功过，或上次运行留下了基准。
     * {@code false} 时 {@link #trustedNow()} 返回的是本机时钟。
     */
    public boolean isSynced() {
        return TrustedTime.isSynced();
    }

    /** 当前偏差：可信时间 − 本机时间（毫秒）。玩家把表调快调慢时能看出差多少。 */
    public long offsetMillis() {
        return TrustedTime.offsetMillis();
    }

    /** 可信时间的来源（取时用的那个地址），没同步过是 {@code null}。 */
    public String source() {
        return TrustedTime.source();
    }

    /**
     * 立刻联网同步一次（异步，不阻塞调用方；结果 {@code true} = 拿到了）。
     *
     * <p>启动时框架已经同步过，一般不需要再调。失败不影响游戏，只是可信度还是旧基准。
     */
    public CompletableFuture<Boolean> syncAsync() {
        return TrustedTime.syncAsync();
    }

    // ==================== 时间戳 ⇄ 日期时间 ====================

    /**
     * 时间戳 → 本地日期时间（按玩家机器的时区）。
     *
     * <p>存档、冷却里存的都是毫秒时间戳；要显示或者判断「现在几点」就得转过来。
     *
     * <pre>{@code
     * long t = Starveil.time().trustedEpochMillis();
     * LocalDateTime dt = Starveil.time().toLocalDateTime(t);
     * int hour = dt.getHour();          // 或者直接 Starveil.time().hourOfDay(t)
     * }</pre>
     */
    public LocalDateTime toLocalDateTime(long epochMillis) {
        return TrustedTime.toLocalDateTime(epochMillis);
    }

    /** 时间戳 → 本地日期（年月日）。 */
    public LocalDate toLocalDate(long epochMillis) {
        return TrustedTime.toLocalDate(epochMillis);
    }

    /** 时间戳 → 本地时刻（时分秒）。 */
    public LocalTime toLocalTime(long epochMillis) {
        return TrustedTime.toLocalTime(epochMillis);
    }

    /** 时间戳 → 带时区的日期时间。 */
    public ZonedDateTime toZonedDateTime(long epochMillis) {
        return TrustedTime.toZonedDateTime(epochMillis);
    }

    /** 本地日期时间 → 时间戳（毫秒）。想算「明天 6 点」这种时刻时用。 */
    public long toEpochMillis(LocalDateTime localDateTime) {
        return TrustedTime.toEpochMillis(localDateTime);
    }

    /**
     * 时间戳 → 格式化字符串，格式用 {@link java.time.format.DateTimeFormatter} 的写法：
     * {@code format(millis, "yyyy-MM-dd HH:mm")} → {@code "2026-10-06 08:30"}。
     */
    public String format(long epochMillis, String pattern) {
        return TrustedTime.format(epochMillis, pattern);
    }

    /** 现在是几点（0-23，可信时间）。 */
    public int hourOfDay() {
        return hourOfDay(trustedEpochMillis());
    }

    /** 这个时间戳是几点（0-23）。挑问候语（早上好 / 下午好）最常用的一个。 */
    public int hourOfDay(long epochMillis) {
        return TrustedTime.hourOfDay(epochMillis);
    }

    /** 会话用的时区（玩家机器的时区）。 */
    public ZoneId zone() {
        return TrustedTime.zone();
    }
}
