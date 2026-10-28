package com.langqi.fakegps.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/**
 * 每日跑步提醒的调度器：负责开关、提醒时间持久化以及 AlarmManager 的安排/取消。
 *
 * <p>使用独立的 SharedPreferences 文件 {@value #PREFS_NAME}，避免与主页其他偏好互相影响。</p>
 *
 * <p>调度策略：</p>
 * <ul>
 *     <li>RTC_WAKEUP 闹钟，触发时由 {@link ReminderReceiver} 弹通知并再次调用
 *     {@link #reschedule(Context)} 安排次日。</li>
 *     <li>Android 12（API 31）及以上若没有精确闹钟权限，自动降级为
 *     {@link AlarmManager#setAndAllowWhileIdle}，保证不崩溃，只是触发时间可能有偏差。</li>
 *     <li>若当前时间已晚于今天的提醒时间，{@link ReminderTime} 会自动安排到次日。</li>
 * </ul>
 */
public final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    /** 提醒开关/时间所在的 SharedPreferences 文件名。 */
    public static final String PREFS_NAME = "reminder_prefs";
    /** 布尔键：每日提醒是否开启。 */
    public static final String KEY_ENABLED = "enabled";
    /** 整型键：每天第几分钟触发（0~1439）。 */
    public static final String KEY_MINUTE_OF_DAY = "minute_of_day";
    /** 默认提醒时间 18:30，即当天第 1110 分钟。 */
    public static final int DEFAULT_MINUTE_OF_DAY = 18 * 60 + 30;
    /** 提醒广播的 action，与 {@link ReminderReceiver} 中注册的一致。 */
    public static final String ACTION_REMIND = "com.langqi.fakegps.action.REMIND";

    /** 提醒闹钟固定使用的 requestCode，保证重复安排时覆盖同一个 PendingIntent。 */
    private static final int REQUEST_CODE_REMIND = 1001;

    private ReminderScheduler() {
        // 工具类，禁止实例化
    }

    /**
     * 每日提醒是否开启。
     *
     * @param context 任意 Context
     * @return 开启返回 true，默认关闭
     */
    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return getPrefs(context).getBoolean(KEY_ENABLED, false);
    }

    /**
     * 读取每天提醒时间（当天第几分钟）。
     *
     * @param context 任意 Context
     * @return 归一化到 [0, 1439] 的分钟数，默认 1110（18:30）
     */
    public static int getMinuteOfDay(Context context) {
        if (context == null) {
            return DEFAULT_MINUTE_OF_DAY;
        }
        int stored = getPrefs(context).getInt(KEY_MINUTE_OF_DAY, DEFAULT_MINUTE_OF_DAY);
        return ReminderTime.normalizeMinuteOfDay(stored);
    }

    /**
     * 保存提醒时间；若提醒已开启则立即重新安排下一次触发。
     *
     * @param context     任意 Context
     * @param minuteOfDay 当天第几分钟，可为任意整数（内部归一化到 [0, 1439]）
     */
    public static void setMinuteOfDay(Context context, int minuteOfDay) {
        if (context == null) {
            return;
        }
        getPrefs(context).edit()
                .putInt(KEY_MINUTE_OF_DAY, ReminderTime.normalizeMinuteOfDay(minuteOfDay))
                .apply();
        if (isEnabled(context)) {
            // 用户修改提醒时间后立即生效
            reschedule(context);
        }
    }

    /**
     * 开启或关闭每日提醒：开启时立即安排下一次，关闭时取消已安排的闹钟。
     *
     * @param context 任意 Context
     * @param enabled 是否开启
     */
    public static void setEnabled(Context context, boolean enabled) {
        if (context == null) {
            return;
        }
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            scheduleNext(context);
        } else {
            cancel(context);
        }
    }

    /**
     * 按当前开关状态重排：开启则安排下一次，关闭则取消。
     *
     * <p>开机、系统时间/时区变化、应用升级后由 {@link BootReceiver} 调用，
     * 每日通知展示后由 {@link ReminderReceiver} 调用以安排次日。</p>
     *
     * @param context 任意 Context
     */
    public static void reschedule(Context context) {
        if (context == null) {
            return;
        }
        if (isEnabled(context)) {
            scheduleNext(context);
        } else {
            cancel(context);
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 安排下一次提醒，内部会吞掉异常并记录日志，保证不把崩溃抛给调用方。 */
    private static void scheduleNext(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager 不可用，无法安排每日提醒");
            return;
        }

        long now = System.currentTimeMillis();
        long triggerAtMillis = ReminderTime.nextTriggerMillis(getMinuteOfDay(app), now);
        PendingIntent pendingIntent = getReminderPendingIntent(app);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !alarmManager.canScheduleExactAlarms()) {
                // Android 12+ 没有精确闹钟权限：降级为非精确闹钟，不崩溃
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                Log.i(TAG, "无精确闹钟权限，已降级 setAndAllowWhileIdle，触发时间=" + triggerAtMillis);
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                Log.d(TAG, "已安排精确闹钟，触发时间=" + triggerAtMillis);
            }
        } catch (SecurityException e) {
            // 部分 ROM 即使 canScheduleExactAlarms() 返回 true 仍可能抛出安全异常
            Log.w(TAG, "安排精确闹钟被拒绝，降级为非精确闹钟", e);
            try {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } catch (RuntimeException fallbackError) {
                Log.e(TAG, "安排每日提醒失败", fallbackError);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "安排每日提醒失败", e);
        }
    }

    /** 取消已安排的提醒。 */
    private static void cancel(Context context) {
        Context app = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager 不可用，无法取消每日提醒");
            return;
        }
        try {
            alarmManager.cancel(getReminderPendingIntent(app));
            Log.d(TAG, "已取消每日提醒闹钟");
        } catch (RuntimeException e) {
            Log.e(TAG, "取消每日提醒失败", e);
        }
    }

    /** 构造指向 {@link ReminderReceiver} 的显式广播 PendingIntent。 */
    private static PendingIntent getReminderPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMIND);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE_REMIND, intent, flags);
    }
}
