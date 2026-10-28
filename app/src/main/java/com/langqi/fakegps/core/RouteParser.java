package com.langqi.fakegps.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * 路线文件解析：KML（Google Earth 导出）、GPX（运动手表/手机轨迹）以及纯文本坐标。
 *
 * <p>纯 Java 实现，可在 JVM 上单元测试。</p>
 */
public final class RouteParser {

    public static final String FORMAT_KML = "KML";
    public static final String FORMAT_GPX = "GPX";
    public static final String FORMAT_TEXT = "TXT";

    public static final int MAX_POINTS = 20000;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private RouteParser() {
    }

    /** 解析结果。 */
    public static final class Result {
        public final String name;
        public final List<GeoPoint> points;
        public final String format;

        Result(String name, List<GeoPoint> points, String format) {
            this.name = name;
            this.points = points;
            this.format = format;
        }

        public double totalMeters() {
            double total = 0.0;
            for (int i = 1; i < points.size(); i++) {
                total += GeoMath.distanceMeters(points.get(i - 1), points.get(i));
            }
            return total;
        }
    }

    /**
     * 从输入流解析路线；自动识别 KML / GPX / 文本。
     *
     * @param fileName 文件名，用于识别格式与兜底命名
     */
    public static Result parse(InputStream inputStream, String fileName) throws IOException {
        if (inputStream == null) {
            throw new IOException("无法读取文件");
        }
        byte[] data = readAll(inputStream);
        if (data.length == 0) {
            throw new IOException("文件为空");
        }
        String fallbackName = nameFromFileName(fileName);
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.length() > 0 && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IOException("文件内容为空");
        }
        String lower = trimmed.toLowerCase(Locale.US);

        boolean looksXml = lower.startsWith("<?xml") || lower.startsWith("<kml")
                || lower.startsWith("<gpx") || lower.startsWith("<document");
        if (looksXml || lower.contains("<kml") || lower.contains("<gpx")) {
            Document document = parseXml(data);
            if (lower.contains("<gpx") || lower.contains("<trkpt") || lower.contains("<rtept")) {
                return parseGpx(document, fallbackName);
            }
            return parseKml(document, fallbackName);
        }
        return parseText(trimmed, fallbackName);
    }

    /** 解析纯文本坐标，支持 lib 形如 "39.0845,121.8088" 或 "121.8088 39.0845"。 */
    public static Result parseText(String text, String name) throws IOException {
        List<GeoPoint> points = new ArrayList<>();
        if (text == null) {
            throw new IOException("没有可解析的坐标");
        }
        String[] lines = text.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            if (line.toLowerCase(Locale.US).contains("latitude")) {
                continue;
            }
            String normalized = line.replace('，', ',')
                    .replace(';', ' ')
                    .replace('；', ' ')
                    .replace('(', ' ')
                    .replace(')', ' ')
                    .replace('（', ' ')
                    .replace('）', ' ')
                    .replace('\t', ' ')
                    .trim();
            String[] parts = normalized.split("[,\\s]+");
            if (parts.length < 2) {
                continue;
            }
            double first;
            double second;
            try {
                first = Double.parseDouble(parts[0]);
                second = Double.parseDouble(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            double latitude;
            double longitude;
            // "经度,纬度" 的经度绝对值通常大于 90，可据此自动识别顺序。
            if (Math.abs(first) > 90.0 && Math.abs(second) <= 90.0) {
                longitude = first;
                latitude = second;
            } else {
                latitude = first;
                longitude = second;
            }
            try {
                points.add(new GeoPoint(latitude, longitude));
            } catch (IllegalArgumentException ignored) {
                // 跳过非法行
            }
        }
        return finish(name, points, FORMAT_TEXT);
    }

    private static Result parseKml(Document document, String fallbackName) throws IOException {
        List<GeoPoint> best = new ArrayList<>();
        NodeList coordinateBlocks = document.getElementsByTagName("coordinates");
        for (int i = 0; i < coordinateBlocks.getLength(); i++) {
            List<GeoPoint> parsed = parseKmlCoordinates(coordinateBlocks.item(i).getTextContent());
            if (parsed.size() > best.size()) {
                best = parsed;
            }
        }
        if (best.size() < 2) {
            // 退回 gx:Track 的 <gx:coord> 格式
            NodeList gxCoords = document.getElementsByTagName("gx:coord");
            if (gxCoords.getLength() == 0) {
                gxCoords = document.getElementsByTagName("coord");
            }
            List<GeoPoint> track = new ArrayList<>();
            for (int i = 0; i < gxCoords.getLength(); i++) {
                String[] parts = gxCoords.item(i).getTextContent().trim().split("[,\\s]+");
                if (parts.length >= 2) {
                    try {
                        track.add(new GeoPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[0])));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            best = track;
        }
        String name = firstNonEmptyText(document, "name");
        return finish(displayName(name, fallbackName), best, FORMAT_KML);
    }

    private static Result parseGpx(Document document, String fallbackName) throws IOException {
        List<GeoPoint> points = new ArrayList<>();
        NodeList trackPoints = document.getElementsByTagName("trkpt");
        if (trackPoints.getLength() == 0) {
            trackPoints = document.getElementsByTagName("rtept");
        }
        if (trackPoints.getLength() == 0) {
            trackPoints = document.getElementsByTagName("wpt");
        }
        for (int i = 0; i < trackPoints.getLength(); i++) {
            Node node = trackPoints.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            try {
                double latitude = Double.parseDouble(element.getAttribute("lat"));
                double longitude = Double.parseDouble(element.getAttribute("lon"));
                points.add(new GeoPoint(latitude, longitude));
            } catch (IllegalArgumentException ignored) {
                // 跳过非法点
            }
        }
        String name = firstNonEmptyText(document, "name");
        return finish(displayName(name, fallbackName), points, FORMAT_GPX);
    }

    private static List<GeoPoint> parseKmlCoordinates(String content) {
        List<GeoPoint> points = new ArrayList<>();
        if (content == null) {
            return points;
        }
        String[] tuples = content.trim().split("\\s+");
        for (String tuple : tuples) {
            String cleaned = tuple.replace('\n', ' ').replace('\r', ' ').trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            String[] parts = cleaned.split(",");
            if (parts.length < 2) {
                continue;
            }
            try {
                double longitude = Double.parseDouble(parts[0]);
                double latitude = Double.parseDouble(parts[1]);
                points.add(new GeoPoint(latitude, longitude));
            } catch (IllegalArgumentException ignored) {
                // 跳过非法坐标
            }
        }
        return points;
    }

    private static Result finish(String name, List<GeoPoint> rawPoints, String format) throws IOException {
        List<GeoPoint> deduped = new ArrayList<>();
        for (GeoPoint point : rawPoints) {
            if (deduped.isEmpty() || !GeoMath.samePoint(deduped.get(deduped.size() - 1), point)) {
                deduped.add(point);
            }
        }
        if (deduped.size() < 2) {
            throw new IOException("没有找到有效坐标，至少需要两个点");
        }
        if (deduped.size() > MAX_POINTS) {
            deduped = deduped.subList(0, MAX_POINTS);
        }
        return new Result(name, new ArrayList<>(deduped), format);
    }

    private static Document parseXml(byte[] data) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            enableHardening(factory);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(data));
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("无法解析 XML 轨迹文件：" + e.getMessage());
        }
    }

    private static void enableHardening(DocumentBuilderFactory factory) {
        String[] features = {
                "http://apache.org/xml/features/disallow-doctype-decl",
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities"
        };
        for (String feature : features) {
            try {
                factory.setFeature(feature, false);
            } catch (ParserConfigurationException ignored) {
                // Android 的部分解析器不支持这些特性，忽略即可。
            }
        }
        try {
            factory.setExpandEntityReferences(false);
        } catch (UnsupportedOperationException ignored) {
        }
    }

    private static String firstNonEmptyText(Document document, String tag) {
        NodeList nodes = document.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            String text = nodes.item(i).getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return "";
    }

    private static String displayName(String candidate, String fallback) {
        if (candidate == null || candidate.trim().isEmpty()) {
            return fallback;
        }
        String lower = candidate.trim().toLowerCase(Locale.US);
        if (lower.equals("无标题项目") || lower.equals("无标题") || lower.equals("untitled")
                || lower.equals("untitled document") || lower.equals("無標題項目")) {
            return fallback;
        }
        return candidate.trim();
    }

    static String nameFromFileName(String fileName) {
        if (fileName == null) {
            return "导入路线";
        }
        String name = fileName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.trim();
        return name.isEmpty() ? "导入路线" : name;
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        int total = 0;
        while ((read = inputStream.read(chunk)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IOException("文件过大（上限 8 MB）");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    // 供 UI 选择文件时使用
    public static String[] supportedExtensions() {
        return new String[]{".kml", ".gpx", ".txt", ".csv"};
    }
}
