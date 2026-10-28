package com.langqi.fakegps.reminder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.langqi.fakegps.MainActivity;
import com.langqi.fakegps.R;

/**
 * 每日提醒广播接收器：由 {@link ReminderScheduler} 安排的 AlarmManager 闹钟触发。
 *
 * <p>职责：</p>
 * <ol>
 *     <li>提醒开关关闭时直接忽略。</li>
 *     <li>创建通知渠道 {@value #CHANNEL_ID}，展示「该跑步啦」通知。</li>
 *     <li>Android 13+ 未授予 POST_NOTIFICATIONS 时不弹通知，但仍安排次日提醒。</li>
 *     <li>通知内容点击打开 {@link MainActivity}，附加动作「一键开始」启动
 *     {@value #SIMULATION_SERVICE_CLASS}（action 为 {@value #ACTION_START}）。</li>
 * </ol>
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    /** 通知渠道 id。 */
    public static final String CHANNEL_ID = "daily_reminder";
    /** 通知渠道名称。 */
    public static final String CHANNEL_NAME = "每日跑步提醒";
    /** 「一键开始」动作启动模拟服务使用的 action，与 Service 侧契约保持一致。 */
    public static final String ACTION_START = "com.langqi.fakegps.action.START";
    /** 模拟服务全限定类名，由 Lead 实现；此处用字符串引用，避免提前依赖该类文件。 */
    public static final String SIMULATION_SERVICE_CLASS = "com.langqi.fakegps.SimulationService";

    /** 每日提醒通知 id，固定值保证同一天只会保留一条通知。 */
    private static final int NOTIFICATION_ID = 2001;
    /** 点击通知内容打开主页的 requestCode。 */
    private static final int REQUEST_CODE_CONTENT = 1002;
    /** 「一键开始」启动服务的 requestCode。 */
    private static final int REQUEST_CODE_START = 1003;

    private static final String NOTIFICATION_TITLE = "该跑步啦";
    private static final String NOTIFICATION_TEXT = "今天的校园跑还差一点，打开轻跑一键开始";
    private static final String ACTION_START_LABEL = "一键开始";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        if (!ReminderScheduler.isEnabled(app)) {
            Log.d(TAG, "每日提醒已关闭，忽略本次触发");
            return;
        }
        try {
            showNotification(app);
        } catch (RuntimeException e) {
            // 通知失败（如 ROM 限制）不应影响后续的次日重排
            Log.e(TAG, "展示每日提醒通知失败", e);
        } finally {
            // 无论通知是否成功，都要安排次日提醒，避免提醒链断掉
            ReminderScheduler.reschedule(app);
        }
    }

    private static void showNotification(Context app) {
        NotificationManager notificationManager =
                (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            Log.w(TAG, "NotificationManager 不可用，跳过通知");
            return;
        }
        ensureChannel(notificationManager);

        if (!canPostNotifications(app, notificationManager)) {
            Log.w(TAG, "通知未授权或已被用户关闭，跳过本次通知（次日仍会提醒）");
            return;
        }

        Notification notification = buildNotification(app);
        notificationManager.notify(NOTIFICATION_ID, notification);
        Log.d(TAG, "每日提醒通知已展示");
    }

    /** 创建通知渠道（API 26+，minSdk 26 恒成立）。 */
    private static void ensureChannel(NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("每天固定时间提醒你完成校园跑");
        notificationManager.createNotificationChannel(channel);
    }

    /** Android 13+ 需要 POST_NOTIFICATIONS 权限；此外系统或用户也可能关闭应用通知。 */
    private static boolean canPostNotifications(Context app, NotificationManager notificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean granted = ContextCompat.checkSelfPermission(
                    app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                return false;
            }
        }
        return notificationManager.areNotificationsEnabled();
    }

    private static Notification buildNotification(Context app) {
        PendingIntent contentIntent = PendingIntent.getActivity(
                app,
                REQUEST_CODE_CONTENT,
                new Intent(app, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                immutableFlags());

        Intent startServiceIntent = new Intent(ACTION_START);
        startServiceIntent.setClassName(app.getPackageName(), SIMULATION_SERVICE_CLASS);
        PendingIntent startServicePendingIntent = PendingIntent.getForegroundService(
                app, REQUEST_CODE_START, startServiceIntent, immutableFlags());

        return new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(NOTIFICATION_TEXT)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentIntent)
                .addAction(0, ACTION_START_LABEL, startServicePendingIntent)
                .build();
    }

    private static int immutableFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
