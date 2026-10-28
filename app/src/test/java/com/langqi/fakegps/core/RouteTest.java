package com.langqi.fakegps.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RouteTest {

    private static Route straightNorthRoute() {
        List<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            points.add(new GeoPoint(39.0845 + i * 0.0001, 121.8088));
        }
        return new Route("测试直线", points);
    }

    @Test
    public void totalDistanceIsPositive() {
        Route route = straightNorthRoute();
        assertTrue(route.totalMeters() > 100.0);
        assertEquals(11.0, route.size(), 0.0);
    }

    @Test
    public void sampleAtZeroReturnsFirstPoint() {
        Route route = straightNorthRoute();
        Route.Sample sample = route.sampleAt(0.0);
        assertEquals(39.0845, sample.point.latitude, 1e-6);
        assertEquals(121.8088, sample.point.longitude, 1e-6);
        assertEquals(0.0, sample.distanceIntoLoop, 1e-9);
    }

    @Test
    public void sampleAtHalfIsInTheMiddle() {
        Route route = straightNorthRoute();
        Route.Sample sample = route.sampleAt(route.totalMeters() / 2.0);
        assertEquals(39.0850, sample.point.latitude, 1e-4);
        assertEquals(0.0, sample.bearingDegrees, 1.0);
    }

    @Test
    public void sampleWrapsAroundWhenDistanceExceedsRoute() {
        Route route = straightNorthRoute();
        Route.Sample inner = route.sampleAt(50.0);
        Route.Sample wrapped = route.sampleAt(route.totalMeters() + 50.0);
        assertEquals(inner.point.latitude, wrapped.point.latitude, 1e-9);
        assertEquals(inner.point.longitude, wrapped.point.longitude, 1e-9);
    }

    @Test
    public void duplicateConsecutivePointsAreRemoved() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(39.0, 121.0));
        points.add(new GeoPoint(39.0, 121.0));
        points.add(new GeoPoint(39.001, 121.0));
        points.add(new GeoPoint(39.001, 121.0));
        points.add(new GeoPoint(39.002, 121.0));
        Route route = new Route("去重", points);
        assertEquals(3, route.size());
    }

    @Test
    public void closedLoopDropsDuplicateFinalPoint() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(39.0, 121.0));
        points.add(new GeoPoint(39.001, 121.0));
        points.add(new GeoPoint(39.001, 121.001));
        points.add(new GeoPoint(39.0, 121.0));
        Route route = new Route("闭环", points);
        assertEquals(3, route.size());
        assertNotEquals(route.points().get(0), route.points().get(route.size() - 1));
    }

    @Test
    public void tooFewPointsAreRejected() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(39.0, 121.0));
        assertThrows(IllegalArgumentException.class, () -> new Route("无效", points));
    }

    @Test
    public void blankNameFallsBack() {
        Route route = new Route("  ", straightNorthRoute().points());
        assertEquals("未命名路线", route.name());
    }
}
