package com.langqi.fakegps.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

/**
 * 通过 LocationManager 的测试 Provider 注入模拟位置。
 *
 * <p>需要先在系统「开发者选项 → 选择模拟位置信息应用」中选择本应用。</p>
 */
public final class MockLocationEngine {

    private static final String TAG = "FakeRun";

    private final Context context;
    private LocationManager locationManager;
    private boolean gpsReady;
    private boolean networkReady;
    private String error;

    public MockLocationEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public String getError() {
        return error;
    }

    public boolean isReady() {
        return gpsReady || networkReady;
    }

    /** 创建测试 Provider，失败时返回 false 并记录可展示的错误。 */
    public boolean start() {
        error = null;
        gpsReady = false;
        networkReady = false;
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            error = "系统定位服务不可用";
            return false;
        }
        gpsReady = addProvider(LocationManager.GPS_PROVIDER, false);
        networkReady = addProvider(LocationManager.NETWORK_PROVIDER, true);
        if (!isReady()) {
            error = "无法创建模拟定位。请先在「开发者选项 → 选择模拟位置信息应用」中选择本应用";
            return false;
        }
        return true;
    }

    /** 推送一次位置。 */
    public void push(GeoPoint point, double altitudeMeters, double bearingDegrees, float speedMps) {
        if (!isReady()) {
            return;
        }
        if (gpsReady) {
            pushToProvider(LocationManager.GPS_PROVIDER, point, altitudeMeters, bearingDegrees, speedMps, 4.0f, true);
        }
        if (networkReady) {
            pushToProvider(LocationManager.NETWORK_PROVIDER, point, altitudeMeters, bearingDegrees, speedMps, 20.0f, false);
        }
    }

    /** 移除测试 Provider（可重复调用）。 */
    public void stop() {
        if (locationManager == null) {
            return;
        }
        if (gpsReady) {
            removeProvider(LocationManager.GPS_PROVIDER);
            gpsReady = false;
        }
        if (networkReady) {
            removeProvider(LocationManager.NETWORK_PROVIDER);
            networkReady = false;
        }
    }

    @SuppressLint("WrongConstant")
    private boolean addProvider(String provider, boolean network) {
        try {
            removeProvider(provider);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(provider,
                        network, !network, false, false, true, true, true,
                        network ? ProviderProperties.POWER_USAGE_LOW : ProviderProperties.POWER_USAGE_HIGH,
                        network ? ProviderProperties.ACCURACY_COARSE : ProviderProperties.ACCURACY_FINE);
            } else {
                locationManager.addTestProvider(provider,
                        network, !network, false, false, true, true, true,
                        network ? Criteria.POWER_LOW : Criteria.POWER_HIGH,
                        network ? Criteria.ACCURACY_COARSE : Criteria.ACCURACY_FINE);
            }
            locationManager.setTestProviderEnabled(provider, true);
            Bundle statusExtras = new Bundle();
            statusExtras.putInt("satellites", network ? 5 : 10);
            try {
                locationManager.setTestProviderStatus(provider, LocationProvider.AVAILABLE,
                        statusExtras, SystemClock.elapsedRealtime());
            } catch (Exception ignored) {
                // 部分机型不支持 Provider 状态更新，忽略。
            }
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "addTestProvider 被拒绝: " + provider, e);
            error = "没有模拟位置权限。请先在「开发者选项 → 选择模拟位置信息应用」中选择本应用";
            return false;
        } catch (Exception e) {
            Log.e(TAG, "addTestProvider 失败: " + provider, e);
            return false;
        }
    }

    private void removeProvider(String provider) {
        try {
            locationManager.setTestProviderEnabled(provider, false);
        } catch (Exception ignored) {
        }
        try {
            locationManager.removeTestProvider(provider);
        } catch (Exception ignored) {
        }
    }

    private void pushToProvider(String provider, GeoPoint point, double altitudeMeters,
                                double bearingDegrees, float speedMps, float accuracyMeters,
                                boolean withSatellites) {
        try {
            Location location = new Location(provider);
            location.setLatitude(point.latitude);
            location.setLongitude(point.longitude);
            location.setAltitude(altitudeMeters);
            location.setAccuracy(accuracyMeters);
            location.setVerticalAccuracyMeters(8.0f);
            location.setSpeed(speedMps);
            location.setSpeedAccuracyMetersPerSecond(0.4f);
            location.setBearing((float) bearingDegrees);
            location.setBearingAccuracyDegrees(5.0f);
            location.setTime(System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            if (withSatellites) {
                Bundle extras = new Bundle();
                extras.putInt("satellites", 10);
                extras.putDouble("meanSeaLevelPressure", 1013.25);
                location.setExtras(extras);
            }
            locationManager.setTestProviderLocation(provider, location);
        } catch (SecurityException e) {
            Log.e(TAG, "setTestProviderLocation 被拒绝: " + provider, e);
            error = "没有模拟位置权限，请检查「选择模拟位置信息应用」设置";
        } catch (Exception e) {
            Log.e(TAG, "setTestProviderLocation 失败: " + provider, e);
        }
    }
}
