package com.langqi.fakegps.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 每日提醒的时间计算工具。
 *
 * <p>纯 Java 实现，不依赖任何 Android API，因此可以直接在 JVM 单元测试中验证。</p>
 *
 * <p>规则：取「当天该时分秒为 0」的时间点；若该时间点不晚于 {@code nowMillis}
 * （即已经过去或正好相等），则安排到次日。返回值是 {@link System#currentTimeMillis()}
 * 同一时间基准下的毫秒时间戳，可直接交给 {@link android.app.AlarmManager} 的
 * {@code RTC_WAKEUP} 类型闹钟使用。</p>
 */
public final class ReminderTime {

    /** 一天的分钟数。 */
    public static final int MINUTES_PER_DAY = 24 * 60;

    private ReminderTime() {
        // 工具类，禁止实例化
    }

    /**
     * 把任意整数分钟数归一化到 [0, 1439]。
     *
     * <p>例如 1440 归一化为 0，-330 归一化为 1110（18:30）。</p>
     *
     * @param minuteOfDay 当天第几分钟，允许为任意整数
     * @return 归一化后的分钟数，范围 [0, 1439]
     */
    public static int normalizeMinuteOfDay(int minuteOfDay) {
        int normalized = minuteOfDay % MINUTES_PER_DAY;
        if (normalized < 0) {
            normalized += MINUTES_PER_DAY;
        }
        return normalized;
    }

    /**
     * 使用系统默认时区计算下一次触发时间。
     *
     * @param minuteOfDay 当天第几分钟（0 表示 00:00，1110 表示 18:30），可为任意整数
     * @param nowMillis   当前时间戳（毫秒）
     * @return 下一次触发的毫秒时间戳
     */
    public static long nextTriggerMillis(int minuteOfDay, long nowMillis) {
        return nextTriggerMillis(minuteOfDay, nowMillis, ZoneId.systemDefault());
    }

    /**
     * 使用指定时区计算下一次触发时间。
     *
     * @param minuteOfDay 当天第几分钟（0 表示 00:00，1110 表示 18:30），可为任意整数
     * @param nowMillis   当前时间戳（毫秒）
     * @param zone        计算时使用的时区，不能为 {@code null}
     * @return 下一次触发的毫秒时间戳
     */
    public static long nextTriggerMillis(int minuteOfDay, long nowMillis, ZoneId zone) {
        if (zone == null) {
            throw new NullPointerException("zone == null");
        }
        int normalized = normalizeMinuteOfDay(minuteOfDay);
        LocalTime triggerTime = LocalTime.of(normalized / 60, normalized % 60);

        Instant now = Instant.ofEpochMilli(nowMillis);
        ZonedDateTime nowZoned = now.atZone(zone);
        LocalDate triggerDate = nowZoned.toLocalDate();

        ZonedDateTime candidate = triggerDate.atTime(triggerTime).atZone(zone);
        if (!candidate.toInstant().isAfter(now)) {
            // 当天的时间点已过（或正好等于当前时刻），顺延到次日
            triggerDate = triggerDate.plusDays(1);
            candidate = triggerDate.atTime(triggerTime).atZone(zone);
        }
        return candidate.toInstant().toEpochMilli();
    }
}
