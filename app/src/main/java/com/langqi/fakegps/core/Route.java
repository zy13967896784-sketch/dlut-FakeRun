package com.langqi.fakegps.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一条不可变的跑步路线：若干坐标点 + 累计距离，支持按里程采样（循环）。
 */
public final class Route {

    private final String name;
    private final List<GeoPoint> points;
    private final double[] cumulative;
    private final double totalMeters;

    public Route(String name, List<GeoPoint> inputPoints) {
        List<GeoPoint> cleaned = new ArrayList<>();
        if (inputPoints != null) {
            for (GeoPoint point : inputPoints) {
                if (point == null) {
                    continue;
                }
                if (cleaned.isEmpty() || !GeoMath.samePoint(cleaned.get(cleaned.size() - 1), point)) {
                    cleaned.add(point);
                }
            }
        }
        // 闭合轨迹常见首尾相同，去掉重复的收尾点。
        while (cleaned.size() >= 3 && GeoMath.samePoint(cleaned.get(0), cleaned.get(cleaned.size() - 1))) {
            cleaned.remove(cleaned.size() - 1);
        }
        if (cleaned.size() < 2) {
            throw new IllegalArgumentException("路径至少需要两个不重合的坐标点");
        }
        this.name = (name == null || name.trim().isEmpty()) ? "未命名路线" : name.trim();
        this.points = Collections.unmodifiableList(cleaned);
        this.cumulative = new double[cleaned.size()];
        for (int i = 1; i < cleaned.size(); i++) {
            cumulative[i] = cumulative[i - 1] + GeoMath.distanceMeters(cleaned.get(i - 1), cleaned.get(i));
        }
        this.totalMeters = cumulative[cumulative.length - 1];
        if (totalMeters <= 0.0) {
            throw new IllegalArgumentException("路径总长度必须大于 0");
        }
    }

    public String name() {
        return name;
    }

    public List<GeoPoint> points() {
        return points;
    }

    public int size() {
        return points.size();
    }

    public double totalMeters() {
        return totalMeters;
    }

    /** 沿路线前进 {@code distanceMeters} 后的位置，超过总长度时自动循环。 */
    public Sample sampleAt(double distanceMeters) {
        if (distanceMeters < 0.0) {
            distanceMeters = 0.0;
        }
        double d = distanceMeters % totalMeters;
        int low = 1;
        int high = cumulative.length - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (cumulative[mid] < d) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        int index = low;
        double segmentStart = cumulative[index - 1];
        double segmentLength = cumulative[index] - segmentStart;
        double fraction = segmentLength <= 0.0 ? 0.0 : (d - segmentStart) / segmentLength;
        GeoPoint point = GeoMath.interpolate(points.get(index - 1), points.get(index), fraction);
        double bearing = GeoMath.initialBearingDegrees(points.get(index - 1), points.get(index));
        return new Sample(point, bearing, d);
    }

    /** 采样结果。 */
    public static final class Sample {
        public final GeoPoint point;
        public final double bearingDegrees;
        public final double distanceIntoLoop;

        Sample(GeoPoint point, double bearingDegrees, double distanceIntoLoop) {
            this.point = point;
            this.bearingDegrees = bearingDegrees;
            this.distanceIntoLoop = distanceIntoLoop;
        }
    }
}
