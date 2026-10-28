package com.langqi.fakegps.core;

import java.util.List;
import java.util.Locale;

/**
 * 把本地路线导出为 KML / GPX 文本，便于备份、分享或导入其他轨迹软件。
 *
 * <p>纯 Java 实现，可在 JVM 上单元测试。</p>
 */
public final class RouteExporter {

    private RouteExporter() {
    }

    /** 导出为 KML（Google Earth / 高德等都能识别）。 */
    public static String toKml(Route route) {
        if (route == null) {
            throw new IllegalArgumentException("路线为空");
        }
        StringBuilder builder = new StringBuilder(4096);
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
                .append("  <Document>\n")
                .append("    <name>").append(escapeXml(route.name())).append("</name>\n")
                .append("    <Placemark>\n")
                .append("      <name>").append(escapeXml(route.name())).append("</name>\n")
                .append("      <LineString>\n")
                .append("        <tessellate>1</tessellate>\n")
                .append("        <coordinates>\n");
        List<GeoPoint> points = route.points();
        for (int i = 0; i < points.size(); i++) {
            GeoPoint point = points.get(i);
            builder.append("          ")
                    .append(format(point.longitude)).append(',')
                    .append(format(point.latitude)).append(",0");
            if (i < points.size() - 1) {
                builder.append('\n');
            }
        }
        builder.append("\n        </coordinates>\n")
                .append("      </LineString>\n")
                .append("    </Placemark>\n")
                .append("  </Document>\n")
                .append("</kml>\n");
        return builder.toString();
    }

    /** 导出为 GPX 1.1（运动手表/骑行软件常用）。 */
    public static String toGpx(Route route) {
        if (route == null) {
            throw new IllegalArgumentException("路线为空");
        }
        StringBuilder builder = new StringBuilder(4096);
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<gpx version=\"1.1\" creator=\"FakeRun\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
                .append("  <trk>\n")
                .append("    <name>").append(escapeXml(route.name())).append("</name>\n")
                .append("    <trkseg>\n");
        for (GeoPoint point : route.points()) {
            builder.append("      <trkpt lat=\"").append(format(point.latitude))
                    .append("\" lon=\"").append(format(point.longitude)).append("\"></trkpt>\n");
        }
        builder.append("    </trkseg>\n")
                .append("  </trk>\n")
                .append("</gpx>\n");
        return builder.toString();
    }

    /** 生成安全文件名，例如 "宁大操场.kml"。 */
    public static String suggestedFileName(String routeName, String extension) {
        String name = routeName == null ? "" : routeName.trim();
        name = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").trim();
        if (name.isEmpty()) {
            name = "route";
        }
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        return name + "." + (extension == null || extension.isEmpty() ? "kml" : extension);
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.7f", value);
    }

    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
