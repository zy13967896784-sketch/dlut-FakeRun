package com.langqi.fakegps.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用配置读写。所有值都有安全范围，读取时自动纠正。
 */
public final class AppPrefs {

    private static final String FILE = "fakerun_prefs";
    private static final String KEY_SPEED = "speed_mps";
    private static final String KEY_TARGET = "target_meters";
    private static final String KEY_INTERVAL = "interval_ms";
    private static final String KEY_ALTITUDE = "altitude_meters";
    private static final String KEY_ACCELERATION = "acceleration";
    private static final String KEY_KEEP_SCREEN = "keep_screen_on";
    private static final String KEY_SPEED_VARIANCE = "speed_variance";
    private static final String KEY_ROUTE_ID = "current_route_id";
    private static final String KEY_GUIDE_SHOWN = "guide_shown";

    private final SharedPreferences preferences;

    public AppPrefs(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SimulationConfig loadConfig() {
        return new SimulationConfig(
                preferences.getFloat(KEY_SPEED, (float) SimulationConfig.DEFAULT_SPEED_MPS),
                preferences.getFloat(KEY_TARGET, (float) SimulationConfig.DEFAULT_TARGET_METERS),
                preferences.getInt(KEY_INTERVAL, SimulationConfig.DEFAULT_INTERVAL_MS),
                preferences.getFloat(KEY_ALTITUDE, (float) SimulationConfig.DEFAULT_ALTITUDE_METERS),
                preferences.getFloat(KEY_ACCELERATION, (float) SimulationConfig.DEFAULT_ACCELERATION),
                preferences.getBoolean(KEY_KEEP_SCREEN, SimulationConfig.DEFAULT_KEEP_SCREEN_ON),
                preferences.getFloat(KEY_SPEED_VARIANCE, (float) SimulationConfig.DEFAULT_SPEED_VARIANCE));
    }

    public void saveSpeedMps(double speedMps) {
        preferences.edit().putFloat(KEY_SPEED, (float) speedMps).apply();
    }

    public void saveTargetMeters(double targetMeters) {
        preferences.edit().putFloat(KEY_TARGET, (float) targetMeters).apply();
    }

    public void saveIntervalMs(int intervalMs) {
        preferences.edit().putInt(KEY_INTERVAL, intervalMs).apply();
    }

    public void saveAltitudeMeters(double altitudeMeters) {
        preferences.edit().putFloat(KEY_ALTITUDE, (float) altitudeMeters).apply();
    }

    public void saveAcceleration(double acceleration) {
        preferences.edit().putFloat(KEY_ACCELERATION, (float) acceleration).apply();
    }

    public void saveKeepScreenOn(boolean keepScreenOn) {
        preferences.edit().putBoolean(KEY_KEEP_SCREEN, keepScreenOn).apply();
    }

    public void saveSpeedVariance(double speedVariance) {
        preferences.edit().putFloat(KEY_SPEED_VARIANCE, (float) speedVariance).apply();
    }

    public String getRouteId() {
        return preferences.getString(KEY_ROUTE_ID, null);
    }

    public void setRouteId(String routeId) {
        preferences.edit().putString(KEY_ROUTE_ID, routeId).apply();
    }

    public boolean isGuideShown() {
        return preferences.getBoolean(KEY_GUIDE_SHOWN, false);
    }

    public void setGuideShown(boolean shown) {
        preferences.edit().putBoolean(KEY_GUIDE_SHOWN, shown).apply();
    }
}
