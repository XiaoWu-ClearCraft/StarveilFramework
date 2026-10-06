package com.xiaowu.game.starveil.game.story;

import com.xiaowu.game.starveil.infrastructure.net.NetworkStatus;
import com.xiaowu.game.starveil.infrastructure.net.TrustedTime;
import com.xiaowu.game.starveil.infrastructure.persistence.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 剧情脚本的时间/网络查询（{@code s.hour()} 那批）。
 *
 * <p>都是只读转发，所以这里钉的是「写剧情时按时间打招呼」这条路径真的能走通：
 * 固定时间戳 → 日期时间/小时，以及格式化。
 */
class StoryScriptTimeTest {

    private StoryScript script;

    @BeforeEach
    @AfterEach
    void reset() {
        DataManager.resetForTest();
        TrustedTime.loadFromConfig();       // 没存过基准 → 未同步，退回本机时钟
        script = new StoryScript("test");
    }

    @Test
    void aFixedTimestampConvertsToDateAndHour() {
        LocalDateTime local = LocalDateTime.of(2026, 10, 6, 8, 30);
        long t = local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        assertEquals(local, script.dateTimeOf(t));
        assertEquals(8, script.hourOf(t));
        assertEquals("2026-10-06 08:30", script.formatTime(t, "yyyy-MM-dd HH:mm"));
        assertEquals("08:30", script.formatTime(t, "HH:mm"));
    }

    @Test
    void nowIsAlwaysAValidMoment() {
        assertEquals(script.timestamp(), script.dateTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 1);
        int hour = script.hour();
        assertTrue(hour >= 0 && hour <= 23, "小时数要在 0-23: " + hour);
        assertTrue(script.time("HH:mm").matches("\\d{2}:\\d{2}"),
                "格式化结果长得像时刻: " + script.time("HH:mm"));
    }

    @Test
    void theTrustedFlagMirrorsTheFramework() {
        assertEquals(TrustedTime.isSynced(), script.isTimeTrusted());
    }

    @Test
    void onlineIsOptimisticBeforeTheFirstProbe() {
        // 与框架语义一致：还没测出来就先当有网
        assertEquals(NetworkStatus.isOnline(), script.isOnline());
        assertTrue(script.isOnline());
    }

    @Test
    void greetingBucketsCoverTheWholeDay() {
        // 就是文档里那段问候语示例的逻辑：0-23 每个小时都能落到一个档里
        String[] greetings = {"还没睡呀？", "早上好", "中午好", "下午好", "晚上好"};
        for (int h = 0; h < 24; h++) {
            int index = h < 5 ? 0 : h < 11 ? 1 : h < 14 ? 2 : h < 18 ? 3 : 4;
            assertNotNull(greetings[index]);
            assertFalse(greetings[index].isEmpty());
        }
    }
}
