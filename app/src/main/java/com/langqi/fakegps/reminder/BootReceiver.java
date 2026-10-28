package com.langqi.fakegps.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 系统事件接收器：开机、系统时间/时区变化、应用升级后重新安排每日提醒。
 *
 * <p>因为这些事件会破坏已安排的 RTC 闹钟的语义（例如关机后闹钟丢失、时区变化后
 * 原触发时刻不再对应当地提醒时间），所以每次都要按当前开关状态重排。</p>
 *
 * <p>Manifest 中由 Lead 声明：
 * BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED。</p>
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!isHandledAction(action)) {
            Log.d(TAG, "忽略无关广播: " + action);
            return;
        }
        Log.d(TAG, "收到 " + action + "，重新安排每日提醒");
        ReminderScheduler.reschedule(context.getApplicationContext());
    }

    private static boolean isHandledAction(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                // Manifest 中写的是 android.intent.action.TIME_SET
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
    }
}
