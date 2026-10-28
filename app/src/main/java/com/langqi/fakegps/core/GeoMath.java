package com.langqi.fakegps.core;

/**
 * 经纬度几何计算。纯 Java，无 Android 依赖，可 JVM 单元测试。
 *
 * <p>距离默认使用 Vincenty 反算（WGS-84 椭球），失败时退回半正矢公式。</p>
 */
public final class GeoMath {

    private static final double WGS84_A = 6378137.0;
    private static final double WGS84_F = 1.0 / 298.257223563;
    private static final double WGS84_B = (1.0 - WGS84_F) * WGS84_A;
    private static final double MEAN_EARTH_RADIUS = 6371000.0;

    private GeoMath() {
    }

    /** 两点间距离（米）。 */
    public static double distanceMeters(GeoPoint p1, GeoPoint p2) {
        double distance = vincentyDistance(p1, p2);
        if (Double.isNaN(distance) || distance < 0.0) {
            distance = haversineDistance(p1, p2);
        }
        return distance;
    }

    /** 半正矢公式，作为 Vincenty 不收敛时的兜底。 */
    public static double haversineDistance(GeoPoint p1, GeoPoint p2) {
        double lat1 = Math.toRadians(p1.latitude);
        double lat2 = Math.toRadians(p2.latitude);
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(p2.longitude - p1.longitude);
        double sinLat = Math.sin(dLat / 2.0);
        double sinLng = Math.sin(dLng / 2.0);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
        return 2.0 * MEAN_EARTH_RADIUS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /** Vincenty 反算；不收敛或同点时返回 NaN / 0。 */
    private static double vincentyDistance(GeoPoint p1, GeoPoint p2) {
        double a = WGS84_A;
        double b = WGS84_B;
        double f = WGS84_F;

        double l = Math.toRadians(p2.longitude - p1.longitude);
        double u1 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(p1.latitude)));
        double u2 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(p2.latitude)));
        double sinU1 = Math.sin(u1);
        double cosU1 = Math.cos(u1);
        double sinU2 = Math.sin(u2);
        double cosU2 = Math.cos(u2);

        double lambda = l;
        double lambdaPrevious;
        double sinSigma;
        double cosSigma;
        double sigma;
        double sinAlpha;
        double cosSqAlpha;
        double cos2SigmaM;
        double sinLambda;
        double cosLambda;
        int iterations = 0;

        do {
            sinLambda = Math.sin(lambda);
            cosLambda = Math.cos(lambda);
            double t1 = cosU2 * sinLambda;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
            sinSigma = Math.sqrt(t1 * t1 + t2 * t2);
            if (sinSigma == 0.0) {
                return 0.0;
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
            cos2SigmaM = cosSqAlpha != 0.0 ? cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha : 0.0;
            if (Double.isNaN(cos2SigmaM)) {
                cos2SigmaM = 0.0;
            }
            double c = f / 16.0 * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
            lambdaPrevious = lambda;
            lambda = l + (1.0 - c) * f * sinAlpha
                    * (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma
                    * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)));
        } while (Math.abs(lambda - lambdaPrevious) > 1e-12 && ++iterations < 200);

        if (iterations >= 200) {
            return Double.NaN;
        }

        double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
        double bigA = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)));
        double bigB = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)));
        double deltaSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4.0
                * (cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)
                - bigB / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma)
                * (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)));
        return b * bigA * (sigma - deltaSigma);
    }

    /** 起点指向终点的初始方位角，范围 [0, 360)。 */
    public static double initialBearingDegrees(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude);
        double lat2 = Math.toRadians(to.latitude);
        double dLng = Math.toRadians(to.longitude - from.longitude);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        bearing = bearing % 360.0;
        if (bearing < 0.0) {
            bearing += 360.0;
        }
        return bearing;
    }

    /** 按比例在两点之间线性插值（短距离足够精确）。 */
    public static GeoPoint interpolate(GeoPoint from, GeoPoint to, double fraction) {
        double t = Math.max(0.0, Math.min(1.0, fraction));
        return new GeoPoint(
                from.latitude + (to.latitude - from.latitude) * t,
                from.longitude + (to.longitude - from.longitude) * t);
    }

    /** 判断两点是否几乎重合（小于 1 厘米级）。 */
    public static boolean samePoint(GeoPoint a, GeoPoint b) {
        return Math.abs(a.latitude - b.latitude) < 1e-9
                && Math.abs(a.longitude - b.longitude) < 1e-9;
    }
}
