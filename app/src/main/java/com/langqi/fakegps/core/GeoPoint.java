package com.langqi.fakegps.core;

/**
 * 一个不可变的经纬度坐标点（WGS-84）。
 */
public final class GeoPoint {

    public final double latitude;
    public final double longitude;

    public GeoPoint(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)
                || Double.isInfinite(latitude) || Double.isInfinite(longitude)) {
            throw new IllegalArgumentException("坐标不是有效数字");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("纬度超出范围: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("经度超出范围: " + longitude);
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeoPoint)) {
            return false;
        }
        GeoPoint other = (GeoPoint) o;
        return Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0;
    }

    @Override
    public int hashCode() {
        long latBits = Double.doubleToLongBits(latitude);
        long lngBits = Double.doubleToLongBits(longitude);
        return (int) (latBits ^ (latBits >>> 32)) * 31 + (int) (lngBits ^ (lngBits >>> 32));
    }

    @Override
    public String toString() {
        return "GeoPoint{" + latitude + ", " + longitude + "}";
    }
}
