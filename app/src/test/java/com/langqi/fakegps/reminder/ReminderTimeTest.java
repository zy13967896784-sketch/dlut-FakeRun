package com.langqi.fakegps.reminder;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ReminderTime} 的纯 JVM 单元测试（不依赖 Android）。
 *
 * <p>覆盖：当天之前、之后、正好相等、跨天/跨月/跨年、分钟数归一化，
 * 以及显式指定 Asia/Shanghai 与 UTC 时结果不受系统默认时区影响。</p>
 */
public class ReminderTimeTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final ZoneId UTC = ZoneId.of("UTC");

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static long at(ZoneId zone, int year, int month, int day, int hour, int minute) {
        return at(zone, year, month, day, hour, minute, 0, 0);
    }

    private static long at(ZoneId zone, int year, int month, int day, int hour, int minute,
                           int second, int nanoOfSecond) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, nanoOfSecond, zone)
                .toInstant()
                .toEpochMilli();
    }

    private static ZonedDateTime local(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone);
    }

    private static void assertLocalTime(long epochMillis, ZoneId zone,
                                        int year, int month, int day, int hour, int minute) {
        LocalDateTime localDateTime = local(epochMillis, zone).toLocalDateTime();
        assertEquals("本地日期不符: " + localDateTime,
                LocalDateTime.of(year, month, day, hour, minute, 0, 0), localDateTime);
    }

    // ------------------------------------------------------------------
    // 当天 / 次日 基本规则
    // ------------------------------------------------------------------

    @Test
    public void beforeTodayTime_schedulesToday() {
        long now = at(SHANGHAI, 2026, 5, 20, 10, 0);
        long trigger = ReminderTime.nextTriggerMillis(1110, now, SHANGHAI);

        assertEquals(at(SHANGHAI, 2026, 5, 20, 18, 30), trigger);
        assertLocalTime(trigger, SHANGHAI, 2026, 5, 20, 18, 30);
        assertTrue("触发时刻应该晚于当前时刻", trigger > now);
    }

    @Test
    public void afterTodayTime_schedulesNextDay() {
        long now = at(SHANGHAI, 2026, 5, 20, 19, 0);
        long trigger = ReminderTime.nextTriggerMillis(1110, now, SHANGHAI);

        assertEquals(at(SHANGHAI, 2026, 5, 21, 18, 30), trigger);
        assertLocalTime(trigger, SHANGHAI, 2026, 5, 21, 18, 30);
        assertTrue(trigger > now);
    }

    @Test
    public void exactlyAtTodayTime_schedulesNextDay() {
        long now = at(SHANGHAI, 2026, 5, 20, 18, 30);
        long trigger = ReminderTime.nextTriggerMillis(1110, now, SHANGHAI);

        assertEquals(at(SHANGHAI, 2026, 5, 21, 18, 30), trigger);
        assertTrue("正好等于提醒时间时应顺延到次日", trigger > now);
    }

    @Test
    public void oneMillisecondBeforeTodayTime_schedulesToday() {
        long now = at(SHANGHAI, 2026, 5, 20, 18, 30) - 1;
        long trigger = ReminderTime.nextTriggerMillis(1110, now, SHANGHAI);

        assertEquals(at(SHANGHAI, 2026, 5, 20, 18, 30), trigger);
    }

    // ------------------------------------------------------------------
    // 跨天 / 跨月 / 跨年
    // ------------------------------------------------------------------

    @Test
    public void lateNight_schedulesNextDayEarlyMorning() {
        long now = at(SHANGHAI, 2026, 5, 20, 23, 59, 59, 0);
        long trigger = ReminderTime.nextTriggerMillis(2 * 60, now, SHANGHAI); // 02:00

        assertEquals(at(SHANGHAI, 2026, 5, 21, 2, 0), trigger);
        assertLocalTime(trigger, SHANGHAI, 2026, 5, 21, 2, 0);
    }

    @Test
    public void crossMonth_schedulesFirstDayOfNextMonth() {
        long now = at(SHANGHAI, 2026, 1, 31, 20, 0);
        long trigger = ReminderTime.nextTriggerMillis(600, now, SHANGHAI); // 10:00

        assertEquals(at(SHANGHAI, 2026, 2, 1, 10, 0), trigger);
        assertLocalTime(trigger, SHANGHAI, 2026, 2, 1, 10, 0);
    }

    @Test
    public void crossYear_schedulesFirstDayOfNextYear() {
        long now = at(SHANGHAI, 2026, 12, 31, 23, 59, 59, 0);
        long trigger = ReminderTime.nextTriggerMillis(150, now, SHANGHAI); // 02:30

        assertEquals(at(SHANGHAI, 2027, 1, 1, 2, 30), trigger);
        assertLocalTime(trigger, SHANGHAI, 2027, 1, 1, 2, 30);
    }

    @Test
    public void midnightMinuteOfDay_exactlyNow_schedulesNextMidnight() {
        long now = at(SHANGHAI, 2026, 3, 1, 0, 0);
        long trigger = ReminderTime.nextTriggerMillis(0, now, SHANGHAI);

        assertEquals(at(SHANGHAI, 2026, 3, 2, 0, 0), trigger);
    }

    @Test
    public void midnightMinuteOfDay_justBeforeMidnight_schedulesNextMidnight() {
        long now = at(SHANGHAI, 2026, 2, 28, 23, 59);
        long trigger = ReminderTime.nextTriggerMillis(0, now, SHANGHAI);

        assertEquals(at(SHANGHAI, 2026, 3, 1, 0, 0), trigger);
    }

    // ------------------------------------------------------------------
    // 分钟数归一化
    // ------------------------------------------------------------------

    @Test
    public void normalizeMinuteOfDay_wrapsIntoOneDay() {
        assertEquals(0, ReminderTime.normalizeMinuteOfDay(0));
        assertEquals(1110, ReminderTime.normalizeMinuteOfDay(1110));
        assertEquals(1439, ReminderTime.normalizeMinuteOfDay(1439));
        assertEquals(1110, ReminderTime.normalizeMinuteOfDay(1110 + 24 * 60));
        assertEquals(0, ReminderTime.normalizeMinuteOfDay(24 * 60 * 3));
        assertEquals(1110, ReminderTime.normalizeMinuteOfDay(-330)); // -05:30
        assertEquals(1439, ReminderTime.normalizeMinuteOfDay(-1));
        assertEquals(0, ReminderTime.normalizeMinuteOfDay(-24 * 60));
    }

    @Test
    public void outOfRangeMinuteOfDay_behavesAsNormalizedValue() {
        long now = at(SHANGHAI, 2026, 5, 20, 10, 0);
        long expected = at(SHANGHAI, 2026, 5, 20, 18, 30);

        assertEquals(expected, ReminderTime.nextTriggerMillis(1110, now, SHANGHAI));
        assertEquals(expected, ReminderTime.nextTriggerMillis(1110 + 24 * 60 * 2, now, SHANGHAI));
        assertEquals(expected, ReminderTime.nextTriggerMillis(-330, now, SHANGHAI));
    }

    // ------------------------------------------------------------------
    // 指定 ZoneId：结果与系统默认时区无关
    // ------------------------------------------------------------------

    @Test
    public void asiaShanghaiZone_explicit() {
        long now = at(SHANGHAI, 2026, 5, 20, 10, 0);

        assertEquals(at(SHANGHAI, 2026, 5, 20, 18, 30),
                ReminderTime.nextTriggerMillis(1110, now, SHANGHAI));
        assertEquals(at(SHANGHAI, 2026, 5, 21, 18, 30),
                ReminderTime.nextTriggerMillis(1110, at(SHANGHAI, 2026, 5, 20, 20, 0), SHANGHAI));
    }

    @Test
    public void utcZone_explicit() {
        long now = Instant.parse("2026-05-20T10:00:00Z").toEpochMilli();

        assertEquals(Instant.parse("2026-05-20T18:30:00Z").toEpochMilli(),
                ReminderTime.nextTriggerMillis(1110, now, UTC));
        assertEquals(Instant.parse("2026-05-21T18:30:00Z").toEpochMilli(),
                ReminderTime.nextTriggerMillis(1110, Instant.parse("2026-05-20T19:00:00Z").toEpochMilli(), UTC));
    }

    @Test
    public void sameInstant_differentZones_giveDifferentTriggers() {
        long now = Instant.parse("2026-05-20T10:00:00Z").toEpochMilli(); // 上海 18:00 / UTC 10:00

        long shanghaiTrigger = ReminderTime.nextTriggerMillis(1110, now, SHANGHAI);
        long utcTrigger = ReminderTime.nextTriggerMillis(1110, now, UTC);

        assertEquals(Instant.parse("2026-05-20T10:30:00Z").toEpochMilli(), shanghaiTrigger);
        assertEquals(Instant.parse("2026-05-20T18:30:00Z").toEpochMilli(), utcTrigger);
        // 同一时刻下，两个时区的触发时间相差 8 小时
        assertEquals(8 * 60 * 60 * 1000L, utcTrigger - shanghaiTrigger);
    }

    @Test
    public void explicitZoneIgnoresSystemDefaultTimezone() {
        long now = Instant.parse("2026-05-20T10:00:00Z").toEpochMilli();
        long expectedShanghai = Instant.parse("2026-05-20T10:30:00Z").toEpochMilli();
        long expectedUtc = Instant.parse("2026-05-20T18:30:00Z").toEpochMilli();

        TimeZone originalDefault = TimeZone.getDefault();
        try {
            String[] systemZones = {"America/Los_Angeles", "Pacific/Kiritimati", "UTC", "Asia/Shanghai"};
            for (String systemZone : systemZones) {
                TimeZone.setDefault(TimeZone.getTimeZone(systemZone));

                assertEquals("系统时区=" + systemZone + " 时上海时区结果被污染",
                        expectedShanghai, ReminderTime.nextTriggerMillis(1110, now, SHANGHAI));
                assertEquals("系统时区=" + systemZone + " 时 UTC 时区结果被污染",
                        expectedUtc, ReminderTime.nextTriggerMillis(1110, now, UTC));
                // 两参数重载按系统默认时区计算
                assertEquals("系统时区=" + systemZone + " 时默认时区重载不一致",
                        ReminderTime.nextTriggerMillis(1110, now, ZoneId.systemDefault()),
                        ReminderTime.nextTriggerMillis(1110, now));
            }
        } finally {
            TimeZone.setDefault(originalDefault);
        }
    }

    // ------------------------------------------------------------------
    // 不变量
    // ------------------------------------------------------------------

    @Test
    public void triggerIsAlwaysStrictlyAfterNow() {
        int[] minutes = {0, 1, 600, 1110, 1439};
        long[] nows = {
                at(SHANGHAI, 2026, 5, 20, 0, 0),
                at(SHANGHAI, 2026, 5, 20, 12, 0),
                at(SHANGHAI, 2026, 5, 20, 23, 59, 59, 999_000_000),
                at(UTC, 2026, 1, 1, 0, 0)
        };
        for (int minute : minutes) {
            for (long now : nows) {
                long trigger = ReminderTime.nextTriggerMillis(minute, now, SHANGHAI);
                assertTrue("minute=" + minute + ", now=" + now + " 时结果未严格晚于当前",
                        trigger > now);
                assertTrue("触发时间不应超过当前时间 1 天以上",
                        trigger - now <= 24L * 60 * 60 * 1000);
            }
        }
    }
}
