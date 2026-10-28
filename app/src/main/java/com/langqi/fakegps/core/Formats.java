package com.langqi.fakegps.core;

import java.util.Locale;

/**
 * 界面展示用的格式化工具。纯 Java，方便单元测试。
 */
public final class Formats {

    private Formats() {
    }

    /** 保留两位小数的公里数，例如 "3.09"。 */
    public static String km(double meters) {
        return String.format(Locale.US, "%.2f", Math.max(0.0, meters) / 1000.0);
    }

    /** 带单位的距离，1 公里以下用米。 */
    public static String distance(double meters) {
        if (meters < 1000.0) {
            return Math.round(Math.max(0.0, meters)) + " 米";
        }
        return km(meters) + " 公里";
    }

    /** 毫秒 -> "18:20" 或 "1:02:03"。 */
    public static String duration(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /** 每公里秒数 -> "5'30\""。 */
    public static String paceFromSecondsPerKm(double secondsPerKm) {
        if (secondsPerKm <= 0.0 || Double.isNaN(secondsPerKm) || Double.isInfinite(secondsPerKm)) {
            return "--'--\"";
        }
        long totalSeconds = Math.round(secondsPerKm);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%d'%02d\"", minutes, seconds);
    }

    /** 速度（m/s）-> "5'30\"" 配速。 */
    public static String paceFromSpeed(double metersPerSecond) {
        if (metersPerSecond <= 0.01) {
            return "--'--\"";
        }
        return paceFromSecondsPerKm(1000.0 / metersPerSecond);
    }

    /** 配速（秒/公里）-> 速度（m/s）。 */
    public static double speedFromPaceSeconds(double secondsPerKm) {
        if (secondsPerKm <= 0.0) {
            return 0.0;
        }
        return 1000.0 / secondsPerKm;
    }

    /** 平均配速：总秒数 / 总米数。 */
    public static String averagePace(long elapsedMillis, double meters) {
        if (meters <= 1.0 || elapsedMillis <= 0L) {
            return "--'--\"";
        }
        return paceFromSecondsPerKm((elapsedMillis / 1000.0) * 1000.0 / meters);
    }

    public static String speed(double metersPerSecond) {
        return String.format(Locale.US, "%.2f m/s", metersPerSecond);
    }

    /** 例如 "3.10 / 3.10 公里"。 */
    public static String progress(double doneMeters, double targetMeters) {
        return km(doneMeters) + " / " + km(targetMeters) + " 公里";
    }

    public static String percent(double fraction) {
        int pct = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 100.0);
        return pct + "%";
    }
}
