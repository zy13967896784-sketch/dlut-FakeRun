package com.langqi.fakegps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.langqi.fakegps.core.AppPrefs;
import com.langqi.fakegps.core.Formats;
import com.langqi.fakegps.core.MockLocationEngine;
import com.langqi.fakegps.core.Route;
import com.langqi.fakegps.core.RouteStore;
import com.langqi.fakegps.core.SimulationConfig;

/**
 * 前台模拟服务：持有模拟定位引擎，按配速沿路线推进，并把状态广播给界面。
 *
 * <p>把模拟循环放在前台服务里，是为了切到畅动力后也能稳定运行。</p>
 */
public class SimulationService extends Service {

    private static final String TAG = "FakeRun";

    public static final String ACTION_START = "com.langqi.fakegps.action.START";
    public static final String ACTION_PAUSE = "com.langqi.fakegps.action.PAUSE";
    public static final String ACTION_RESUME = "com.langqi.fakegps.action.RESUME";
    public static final String ACTION_STOP = "com.langqi.fakegps.action.STOP";
    public static final String ACTION_STATUS = "com.langqi.fakegps.action.STATUS";

    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_DISTANCE = "distance";
    public static final String EXTRA_TARGET = "target";
    public static final String EXTRA_ELAPSED = "elapsed";
    public static final String EXTRA_ROUTE_NAME = "route_name";
    public static final String EXTRA_MESSAGE = "message";

    public static final int STATE_IDLE = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_FINISHED = 3;
    public static final int STATE_ERROR = 4;

    private static final String CHANNEL_ID = "simulation";
    private static final int NOTIFICATION_ID = 1001;
    private static final int RC_PAUSE = 11;
    private static final int RC_RESUME = 12;
    private static final int RC_STOP = 13;
    private static final int RC_OPEN = 14;

    private static volatile boolean active = false;
    private static volatile StatusSnapshot snapshot = new StatusSnapshot(
            STATE_IDLE, 0.0, SimulationConfig.DEFAULT_TARGET_METERS, 0L, "", "");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            onTick();
        }
    };

    private AppPrefs prefs;
    private RouteStore routeStore;
    private SimulationConfig config;
    private Route route;
    private MockLocationEngine engine;
    private PowerManager.WakeLock wakeLock;

    private int state = STATE_IDLE;
    private double distanceMeters = 0.0;
    private long elapsedMillis = 0L;
    private long lastTickElapsed = 0L;
    private String message = "";
    private long lastNotificationAt = 0L;
    private final java.util.Random random = new java.util.Random();
    private double speedFactor = 1.0;

    /** 界面在进程内直接读取，避免每次重进 App 都等服务广播。 */
    public static StatusSnapshot snapshot() {
        return snapshot;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean start(Context context) {
        try {
            Intent intent = new Intent(context, SimulationService.class).setAction(ACTION_START);
            ContextCompat.startForegroundService(context, intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "启动模拟服务失败", e);
            return false;
        }
    }

    public static void pause(Context context) {
        sendAction(context, ACTION_PAUSE);
    }

    public static void resume(Context context) {
        sendAction(context, ACTION_RESUME);
    }

    public static void stop(Context context) {
        sendAction(context, ACTION_STOP);
    }

    private static void sendAction(Context context, String action) {
        try {
            context.startService(new Intent(context, SimulationService.class).setAction(action));
        } catch (Exception e) {
            Log.e(TAG, "发送服务指令失败: " + action, e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        active = true;
        prefs = new AppPrefs(this);
        routeStore = new RouteStore(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_PAUSE.equals(action)) {
            pauseSimulation();
        } else if (ACTION_RESUME.equals(action)) {
            resumeSimulation();
        } else if (ACTION_STOP.equals(action)) {
            stopSimulation();
        } else if (ACTION_START.equals(action) || action == null) {
            startSimulation();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- 状态机

    private void startSimulation() {
        if (state == STATE_RUNNING || state == STATE_PAUSED) {
            return;
        }
        config = prefs.loadConfig();
        distanceMeters = 0.0;
        elapsedMillis = 0L;
        message = "";
        state = STATE_RUNNING;
        // 先把前台通知挂起来，满足 startForegroundService 的 5 秒限制。
        try {
            startForegroundCompat();
        } catch (Exception e) {
            fail("无法启动前台服务：" + e.getMessage());
            return;
        }
        try {
            route = routeStore.current();
        } catch (Exception e) {
            fail("路线加载失败：" + e.getMessage());
            return;
        }
        engine = new MockLocationEngine(this);
        if (!engine.start()) {
            fail(engine.getError());
            return;
        }
        lastTickElapsed = SystemClock.elapsedRealtime();
        speedFactor = 1.0;
        acquireWakeLock();
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
        publishState();
    }

    private void pauseSimulation() {
        if (state != STATE_RUNNING) {
            return;
        }
        state = STATE_PAUSED;
        handler.removeCallbacks(tickRunnable);
        releaseWakeLock();
        publishState();
    }

    private void resumeSimulation() {
        if (state != STATE_PAUSED) {
            return;
        }
        state = STATE_RUNNING;
        lastTickElapsed = SystemClock.elapsedRealtime();
        acquireWakeLock();
        handler.removeCallbacks(tickRunnable);
        handler.post(tickRunnable);
        publishState();
    }

    private void stopSimulation() {
        if (state == STATE_RUNNING || state == STATE_PAUSED) {
            state = STATE_IDLE;
            publishState();
        }
        finishService();
    }

    private void onTick() {
        if (state != STATE_RUNNING || config == null || route == null || engine == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long delta = Math.max(0L, now - lastTickElapsed);
        lastTickElapsed = now;
        elapsedMillis += delta;
        double currentSpeed = nextSpeed(delta);
        distanceMeters += currentSpeed * delta / 1000.0;

        boolean reachedTarget = distanceMeters >= config.targetMeters;
        double reportedDistance = reachedTarget ? config.targetMeters : distanceMeters;
        try {
            Route.Sample sample = route.sampleAt(reportedDistance);
            engine.push(sample.point, config.altitudeMeters, sample.bearingDegrees,
                    (float) currentSpeed);
        } catch (Exception e) {
            Log.e(TAG, "推送位置失败", e);
        }

        publishState(reportedDistance);
        if (reachedTarget) {
            finishRun();
            return;
        }
        handler.postDelayed(tickRunnable, Math.max(50, config.intervalMs));
    }

    /**
     * 计算下一个 tick 的速度：在配置配速上下 {@code speedVariance} 的范围内平滑随机浮动，
     * 避免出现"机械匀速"的轨迹。每 1.5 秒左右向新的随机目标过渡一次。
     */
    private double nextSpeed(long deltaMillis) {
        double baseSpeed = config.speedMps;
        double variance = config.speedVariance;
        if (variance <= 0.0001) {
            speedFactor = 1.0;
            return baseSpeed;
        }
        double targetFactor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * variance;
        double smoothing = Math.min(1.0, Math.max(0.0, deltaMillis / 1500.0));
        speedFactor += (targetFactor - speedFactor) * smoothing;
        double minFactor = 1.0 - variance;
        double maxFactor = 1.0 + variance;
        speedFactor = Math.max(minFactor, Math.min(maxFactor, speedFactor));
        return baseSpeed * speedFactor;
    }

    private void finishRun() {
        state = STATE_FINISHED;
        distanceMeters = config.targetMeters;
        handler.removeCallbacks(tickRunnable);
        releaseWakeLock();
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        publishState(distanceMeters, false);
        updateNotification(distanceMeters);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                active = false;
                stopForeground(true);
                stopSelf();
            }
        }, 2500L);
    }

    private void fail(String error) {
        Log.e(TAG, "模拟失败: " + error);
        message = error == null ? "未知错误" : error;
        state = STATE_ERROR;
        handler.removeCallbacks(tickRunnable);
        releaseWakeLock();
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        try {
            startForegroundCompat();
        } catch (Exception ignored) {
        }
        publishState();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                active = false;
                stopForeground(true);
                stopSelf();
            }
        }, 4000L);
    }

    private void publishState() {
        publishState(distanceMeters, true);
    }

    private void publishState(double reportedDistance) {
        publishState(reportedDistance, true);
    }

    private void publishState(double reportedDistance, boolean updateNotification) {
        long elapsed = elapsedMillis;
        String routeName = route == null ? "" : route.name();
        snapshot = new StatusSnapshot(state, reportedDistance, config == null
                ? SimulationConfig.DEFAULT_TARGET_METERS : config.targetMeters,
                elapsed, routeName, message);
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATE, snapshot.state);
        intent.putExtra(EXTRA_DISTANCE, snapshot.distanceMeters);
        intent.putExtra(EXTRA_TARGET, snapshot.targetMeters);
        intent.putExtra(EXTRA_ELAPSED, snapshot.elapsedMillis);
        intent.putExtra(EXTRA_ROUTE_NAME, snapshot.routeName);
        intent.putExtra(EXTRA_MESSAGE, snapshot.message);
        sendBroadcast(intent);
        if (updateNotification) {
            updateNotification(reportedDistance);
        }
    }

    // ------------------------------------------------------------ 前台通知

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "模拟跑步", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("模拟跑步状态与快捷控制");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification(distanceMeters);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(double reportedDistance) {
        long now = SystemClock.elapsedRealtime();
        if (state == STATE_RUNNING && now - lastNotificationAt < 1000L) {
            return;
        }
        lastNotificationAt = now;
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(reportedDistance));
        }
    }

    private Notification buildNotification(double reportedDistance) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setOnlyAlertOnce(true)
                .setOngoing(state == STATE_RUNNING || state == STATE_PAUSED)
                .setContentTitle(titleForState())
                .setContentText(textForState(reportedDistance))
                .setContentIntent(openAppIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW);

        if (state == STATE_RUNNING || state == STATE_PAUSED) {
            double target = config == null ? 0.0 : config.targetMeters;
            int progress = target <= 0.0 ? 0
                    : (int) Math.round(Math.min(1.0, reportedDistance / target) * 100.0);
            builder.setProgress(100, progress, false);
        }
        if (state == STATE_RUNNING) {
            builder.addAction(0, "暂停", serviceIntent(ACTION_PAUSE, RC_PAUSE));
            builder.addAction(0, "结束", serviceIntent(ACTION_STOP, RC_STOP));
        } else if (state == STATE_PAUSED) {
            builder.addAction(0, "继续", serviceIntent(ACTION_RESUME, RC_RESUME));
            builder.addAction(0, "结束", serviceIntent(ACTION_STOP, RC_STOP));
        }
        return builder.build();
    }

    private String titleForState() {
        switch (state) {
            case STATE_RUNNING:
                return "模拟跑步中";
            case STATE_PAUSED:
                return "模拟已暂停";
            case STATE_FINISHED:
                return "本次模拟已完成";
            case STATE_ERROR:
                return "模拟启动失败";
            default:
                return "轻跑";
        }
    }

    private String textForState(double reportedDistance) {
        double target = config == null ? SimulationConfig.DEFAULT_TARGET_METERS : config.targetMeters;
        switch (state) {
            case STATE_RUNNING:
            case STATE_PAUSED:
                return Formats.progress(reportedDistance, target) + " · "
                        + Formats.duration(elapsedMillis) + " · "
                        + Formats.averagePace(elapsedMillis, reportedDistance);
            case STATE_FINISHED:
                return Formats.km(target) + " 公里 · 用时 " + Formats.duration(elapsedMillis);
            case STATE_ERROR:
                return message;
            default:
                return "";
        }
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, RC_OPEN, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, SimulationService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ------------------------------------------------------------ 生命周期

    private void acquireWakeLock() {
        releaseWakeLock();
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FakeRun:simulation");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(6 * 60 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            try {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            } catch (Exception ignored) {
            }
            wakeLock = null;
        }
    }

    private void finishService() {
        handler.removeCallbacks(tickRunnable);
        active = false;
        releaseWakeLock();
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tickRunnable);
        if (state == STATE_RUNNING || state == STATE_PAUSED) {
            state = STATE_IDLE;
            publishState();
        }
        releaseWakeLock();
        if (engine != null) {
            engine.stop();
            engine = null;
        }
        active = false;
        super.onDestroy();
    }

    /** 广播给界面的状态快照。 */
    public static final class StatusSnapshot {
        public final int state;
        public final double distanceMeters;
        public final double targetMeters;
        public final long elapsedMillis;
        public final String routeName;
        public final String message;

        StatusSnapshot(int state, double distanceMeters, double targetMeters,
                       long elapsedMillis, String routeName, String message) {
            this.state = state;
            this.distanceMeters = distanceMeters;
            this.targetMeters = targetMeters;
            this.elapsedMillis = elapsedMillis;
            this.routeName = routeName;
            this.message = message;
        }

        public double progress() {
            return targetMeters <= 0.0 ? 0.0 : Math.min(1.0, distanceMeters / targetMeters);
        }
    }
}
