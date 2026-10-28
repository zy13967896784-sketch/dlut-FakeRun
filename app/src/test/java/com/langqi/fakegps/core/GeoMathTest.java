package com.langqi.fakegps.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GeoMathTest {

    @Test
    public void distanceOfSamePointIsZero() {
        GeoPoint point = new GeoPoint(39.0845, 121.8088);
        assertEquals(0.0, GeoMath.distanceMeters(point, point), 0.001);
    }

    @Test
    public void oneDegreeLongitudeAtEquator() {
        double distance = GeoMath.distanceMeters(new GeoPoint(0, 0), new GeoPoint(0, 1));
        assertEquals(111319.49, distance, 1.0);
    }

    @Test
    public void distanceIsSymmetric() {
        GeoPoint a = new GeoPoint(39.0845, 121.8088);
        GeoPoint b = new GeoPoint(39.0862, 121.8078);
        assertEquals(GeoMath.distanceMeters(a, b), GeoMath.distanceMeters(b, a), 0.001);
    }

    @Test
    public void shortCampusDistanceIsAbout170Meters() {
        double distance = GeoMath.distanceMeters(
                new GeoPoint(39.0845, 121.8088),
                new GeoPoint(39.0860, 121.8088));
        assertEquals(166.9, distance, 1.0);
    }

    @Test
    public void bearingEastIsNinety() {
        double bearing = GeoMath.initialBearingDegrees(new GeoPoint(0, 0), new GeoPoint(0, 1));
        assertEquals(90.0, bearing, 0.01);
    }

    @Test
    public void bearingNorthIsZero() {
        double bearing = GeoMath.initialBearingDegrees(new GeoPoint(0, 0), new GeoPoint(1, 0));
        assertEquals(0.0, bearing, 0.01);
    }

    @Test
    public void bearingIsAlwaysNormalized() {
        double bearing = GeoMath.initialBearingDegrees(new GeoPoint(0, 0), new GeoPoint(-1, 0));
        assertTrue(bearing >= 0.0 && bearing < 360.0);
    }

    @Test
    public void interpolateReturnsEndpointsAndMiddle() {
        GeoPoint a = new GeoPoint(39.0, 121.0);
        GeoPoint b = new GeoPoint(39.2, 121.2);
        assertEquals(39.0, GeoMath.interpolate(a, b, 0.0).latitude, 1e-9);
        assertEquals(39.2, GeoMath.interpolate(a, b, 1.0).latitude, 1e-9);
        assertEquals(39.1, GeoMath.interpolate(a, b, 0.5).latitude, 1e-9);
        assertEquals(121.1, GeoMath.interpolate(a, b, 0.5).longitude, 1e-9);
    }

    @Test
    public void samePointHandlesTinyDifference() {
        assertTrue(GeoMath.samePoint(new GeoPoint(39.0, 121.0), new GeoPoint(39.0, 121.0)));
        assertTrue(!GeoMath.samePoint(new GeoPoint(39.0, 121.0), new GeoPoint(39.0001, 121.0)));
    }
}
