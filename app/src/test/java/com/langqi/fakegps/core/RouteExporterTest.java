package com.langqi.fakegps.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class RouteExporterTest {

    private static Route sampleRoute() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(39.0845, 121.8088));
        points.add(new GeoPoint(39.0850, 121.8089));
        points.add(new GeoPoint(39.0855, 121.8090));
        return new Route("测试 & 路线", points);
    }

    @Test
    public void kmlRoundTripsThroughParser() throws Exception {
        Route route = sampleRoute();
        String kml = RouteExporter.toKml(route);
        RouteParser.Result parsed = RouteParser.parse(
                new ByteArrayInputStream(kml.getBytes(StandardCharsets.UTF_8)), "export.kml");
        assertEquals(3, parsed.points.size());
        assertEquals(route.totalMeters(), parsed.totalMeters(), 0.5);
        assertTrue(kml.contains("测试 &amp; 路线"));
        assertTrue(kml.contains("<kml"));
    }

    @Test
    public void gpxRoundTripsThroughParser() throws Exception {
        Route route = sampleRoute();
        String gpx = RouteExporter.toGpx(route);
        RouteParser.Result parsed = RouteParser.parse(
                new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)), "export.gpx");
        assertEquals(3, parsed.points.size());
        assertEquals(route.totalMeters(), parsed.totalMeters(), 0.5);
        assertTrue(gpx.contains("<gpx"));
        assertTrue(gpx.contains("trkpt"));
    }

    @Test
    public void suggestedFileNameSanitizesInvalidCharacters() {
        assertEquals("a_b_c.kml", RouteExporter.suggestedFileName("a/b:c", "kml"));
        assertEquals("route.gpx", RouteExporter.suggestedFileName("   ", "gpx"));
        assertEquals("route.kml", RouteExporter.suggestedFileName(null, "kml"));
    }
}
