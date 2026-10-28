package com.langqi.fakegps.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class RouteParserTest {

    private static RouteParser.Result parse(String content, String fileName) throws IOException {
        return RouteParser.parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), fileName);
    }

    private static final String KML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document>\n"
            + "  <name>宁大操场</name>\n"
            + "  <Placemark><LineString><coordinates>\n"
            + "    121.8088,39.0845,0 121.8089,39.0846,0 121.8090,39.0847,0\n"
            + "  </coordinates></LineString></Placemark>\n"
            + "</Document></kml>";

    private static final String GPX = "<?xml version=\"1.0\"?>\n"
            + "<gpx version=\"1.1\" creator=\"test\"><trk>\n"
            + "  <name>晨跑路线</name><trkseg>\n"
            + "    <trkpt lat=\"39.0845\" lon=\"121.8088\"><ele>12</ele></trkpt>\n"
            + "    <trkpt lat=\"39.0846\" lon=\"121.8089\"/>\n"
            + "  </trkseg>\n"
            + "</trk></gpx>";

    @Test
    public void parsesKmlCoordinatesAndName() throws Exception {
        RouteParser.Result result = parse(KML, "default_route.kml");
        assertEquals(3, result.points.size());
        assertEquals("宁大操场", result.name);
        assertEquals(RouteParser.FORMAT_KML, result.format);
        assertEquals(39.0845, result.points.get(0).latitude, 1e-9);
        assertEquals(121.8088, result.points.get(0).longitude, 1e-9);
        assertTrue(result.totalMeters() > 20.0);
    }

    @Test
    public void parsesGpxTrackPoints() throws Exception {
        RouteParser.Result result = parse(GPX, "morning.gpx");
        assertEquals(2, result.points.size());
        assertEquals("晨跑路线", result.name);
        assertEquals(RouteParser.FORMAT_GPX, result.format);
        assertEquals(39.0846, result.points.get(1).latitude, 1e-9);
        assertEquals(121.8089, result.points.get(1).longitude, 1e-9);
    }

    @Test
    public void parsesTextWithLatitudeFirst() throws Exception {
        RouteParser.Result result = parse("39.0845,121.8088\n39.0846,121.8089\n", "route.txt");
        assertEquals(2, result.points.size());
        assertEquals(RouteParser.FORMAT_TEXT, result.format);
        assertEquals(39.0845, result.points.get(0).latitude, 1e-9);
        assertEquals(121.8088, result.points.get(0).longitude, 1e-9);
    }

    @Test
    public void autoDetectsLongitudeFirstText() throws Exception {
        RouteParser.Result result = parse("121.8088,39.0845\n121.8089,39.0846\n", "route.txt");
        assertEquals(39.0845, result.points.get(0).latitude, 1e-9);
        assertEquals(121.8088, result.points.get(0).longitude, 1e-9);
    }

    @Test
    public void ignoresCommentsAndHeaderLines() throws Exception {
        RouteParser.Result result = parse("# 我的路线\nlatitude,longitude\n\n"
                + "39.0845,121.8088\n39.0846,121.8089\n", "route.csv");
        assertEquals(2, result.points.size());
    }

    @Test
    public void rejectsContentWithoutTwoPoints() {
        assertThrows(IOException.class, () -> parse("hello world", "bad.txt"));
        assertThrows(IOException.class, () -> parse("39.0845,121.8088", "one.txt"));
        assertThrows(IOException.class, () -> parse("", "empty.txt"));
    }

    @Test
    public void deduplicatesRepeatedPoints() throws Exception {
        RouteParser.Result result = parse("39.0845,121.8088\n39.0845,121.8088\n39.0846,121.8089\n",
                "dupe.txt");
        assertEquals(2, result.points.size());
    }

    @Test
    public void fallsBackToFileNameWhenKmlNameIsUntitled() throws Exception {
        String kml = "<?xml version=\"1.0\"?><kml><Document><name>无标题项目</name>"
                + "<Placemark><LineString><coordinates>121.8,39.0 121.81,39.01</coordinates>"
                + "</LineString></Placemark></Document></kml>";
        RouteParser.Result result = parse(kml, "my_campus.kml");
        assertEquals("my_campus", result.name);
    }
}
