package com.langqi.fakegps.core;

/**
 * 一次模拟的配置。单位统一为米 / 秒。
 */
public final class SimulationConfig {

    /** 默认配速 5'30"/km。 */
    public static final double DEFAULT_SPEED_MPS = Formats.speedFromPaceSeconds(330.0);
    /** 默认目标 3.10 公里（校园跑常见要求）。 */
    public static final double DEFAULT_TARGET_METERS = 3100.0;
    /** 默认每 300ms 推送一次位置。 */
    public static final int DEFAULT_INTERVAL_MS = 300;
    public static final double DEFAULT_ALTITUDE_METERS = 0.0;
    public static final double DEFAULT_ACCELERATION = 0.0;
    public static final boolean DEFAULT_KEEP_SCREEN_ON = false;
    /** 默认配速浮动 5%，让轨迹速度更像真人。 */
    public static final double DEFAULT_SPEED_VARIANCE = 0.05;

    public final double speedMps;
    public final double targetMeters;
    public final int intervalMs;
    public final double altitudeMeters;
    public final double acceleration;
    public final boolean keepScreenOn;
    /** 配速随机浮动比例，0~0.3；0 表示匀速。 */
    public final double speedVariance;

    public SimulationConfig(double speedMps, double targetMeters, int intervalMs,
                            double altitudeMeters, double acceleration, boolean keepScreenOn,
                            double speedVariance) {
        this.speedMps = clamp(speedMps, 0.3, 12.0, DEFAULT_SPEED_MPS);
        this.targetMeters = clamp(targetMeters, 100.0, 100000.0, DEFAULT_TARGET_METERS);
        this.intervalMs = (int) Math.round(clamp(intervalMs, 50.0, 5000.0, DEFAULT_INTERVAL_MS));
        this.altitudeMeters = clamp(altitudeMeters, -500.0, 9000.0, DEFAULT_ALTITUDE_METERS);
        this.acceleration = clamp(acceleration, -20.0, 20.0, DEFAULT_ACCELERATION);
        this.keepScreenOn = keepScreenOn;
        this.speedVariance = clamp(speedVariance, 0.0, 0.3, DEFAULT_SPEED_VARIANCE);
    }

    public static SimulationConfig defaults() {
        return new SimulationConfig(DEFAULT_SPEED_MPS, DEFAULT_TARGET_METERS, DEFAULT_INTERVAL_MS,
                DEFAULT_ALTITUDE_METERS, DEFAULT_ACCELERATION, DEFAULT_KEEP_SCREEN_ON,
                DEFAULT_SPEED_VARIANCE);
    }

    /** 预计用时（毫秒）。 */
    public long estimatedDurationMillis() {
        return Math.round(targetMeters / speedMps * 1000.0);
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
