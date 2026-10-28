package com.langqi.fakegps.core;

import android.content.Context;

import com.langqi.fakegps.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 路线仓库：内置路线 + 用户导入的路线，全部保存在应用私有目录。
 *
 * <p>保存为 JSON，避免每次启动重复解析 KML/GPX。</p>
 */
public final class RouteStore {

    public static final String BUILTIN_ID = "builtin_dlut_3k";
    private static final String BUILTIN_NAME = "大工操场 3 公里";
    private static final String DIR_NAME = "routes";
    private static final int MAX_NAME_LENGTH = 40;

    private final Context context;
    private final File directory;
    private final AppPrefs prefs;
    private final Map<String, Route> cache = new HashMap<>();

    public RouteStore(Context context) {
        this.context = context.getApplicationContext();
        this.directory = new File(this.context.getFilesDir(), DIR_NAME);
        this.prefs = new AppPrefs(this.context);
        if (!directory.exists() && !directory.mkdirs()) {
            // 目录创建失败时后续操作会各自报错，这里不中断。
        }
    }

    /** 路线列表，内置路线固定排在最前。 */
    public List<RouteSummary> list() {
        List<RouteSummary> summaries = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - ".json".length());
                try {
                    Route route = load(id);
                    summaries.add(new RouteSummary(id, route.name(), route.totalMeters(), route.size(),
                            readFormat(file), BUILTIN_ID.equals(id)));
                } catch (Exception ignored) {
                    // 损坏的文件被跳过
                }
            }
        }
        Collections.sort(summaries, new Comparator<RouteSummary>() {
            @Override
            public int compare(RouteSummary a, RouteSummary b) {
                if (a.builtin != b.builtin) {
                    return a.builtin ? -1 : 1;
                }
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return summaries;
    }

    /** 当前选中的路线；没有则自动准备内置路线。 */
    public Route current() {
        String id = prefs.getRouteId();
        if (id != null) {
            Route cached = loadSafely(id);
            if (cached != null) {
                return cached;
            }
        }
        List<RouteSummary> all = list();
        if (!all.isEmpty()) {
            Route route = loadSafely(all.get(0).id);
            if (route != null) {
                prefs.setRouteId(all.get(0).id);
                return route;
            }
        }
        return ensureBuiltin();
    }

    public void setCurrent(Route route) {
        String id = findId(route);
        if (id != null && loadSafely(id) != null) {
            prefs.setRouteId(id);
        }
    }

    public void setCurrentId(String id) {
        if (loadSafely(id) != null) {
            prefs.setRouteId(id);
        }
    }

    /** 导入一条新路线，返回保存后的路线。 */
    public Route importRoute(String name, List<GeoPoint> points, String format) {
        Route route = new Route(sanitizeName(name), points);
        String id = "r" + System.currentTimeMillis();
        int suffix = 1;
        while (new File(directory, id + ".json").exists()) {
            id = "r" + System.currentTimeMillis() + "_" + suffix++;
        }
        writeRoute(id, route, format == null ? RouteParser.FORMAT_TEXT : format, false);
        cache.put(id, route);
        prefs.setRouteId(id);
        return route;
    }

    /** 删除路线；内置路线允许删除，但全部删光后下次会自动补回。 */
    public boolean delete(String id) {
        cache.remove(id);
        boolean deleted = new File(directory, id + ".json").delete();
        if (deleted && id.equals(prefs.getRouteId())) {
            prefs.setRouteId(null);
        }
        return deleted;
    }

    /** 确保内置的 3 公里操场路线存在。 */
    public Route ensureBuiltin() {
        File file = new File(directory, BUILTIN_ID + ".json");
        if (file.exists()) {
            Route cached = loadSafely(BUILTIN_ID);
            if (cached != null) {
                prefs.setRouteId(BUILTIN_ID);
                return cached;
            }
        }
        try (InputStream inputStream = context.getResources().openRawResource(R.raw.three_km)) {
            RouteParser.Result parsed = RouteParser.parse(inputStream, "three_km.kml");
            Route route = new Route(BUILTIN_NAME, parsed.points);
            writeRoute(BUILTIN_ID, route, RouteParser.FORMAT_KML, true);
            cache.put(BUILTIN_ID, route);
            prefs.setRouteId(BUILTIN_ID);
            return route;
        } catch (IOException e) {
            throw new IllegalStateException("内置路线读取失败：" + e.getMessage(), e);
        }
    }

    private String findId(Route route) {
        for (Map.Entry<String, Route> entry : cache.entrySet()) {
            if (entry.getValue() == route || entry.getValue().name().equals(route.name())) {
                return entry.getKey();
            }
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - ".json".length());
                try {
                    if (load(id).name().equals(route.name())) {
                        return id;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private Route loadSafely(String id) {
        try {
            return load(id);
        } catch (Exception e) {
            return null;
        }
    }

    private Route load(String id) throws IOException, JSONException {
        Route cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        File file = new File(directory, id + ".json");
        if (!file.exists()) {
            throw new IOException("路线不存在: " + id);
        }
        String json = readFile(file);
        JSONObject object = new JSONObject(json);
        JSONArray pointsJson = object.getJSONArray("points");
        List<GeoPoint> points = new ArrayList<>(pointsJson.length());
        for (int i = 0; i < pointsJson.length(); i++) {
            JSONArray pair = pointsJson.getJSONArray(i);
            points.add(new GeoPoint(pair.getDouble(0), pair.getDouble(1)));
        }
        Route route = new Route(object.optString("name", "未命名路线"), points);
        cache.put(id, route);
        return route;
    }

    private void writeRoute(String id, Route route, String format, boolean builtin) {
        JSONObject object = new JSONObject();
        try {
            object.put("name", route.name());
            object.put("format", format);
            object.put("builtin", builtin);
            object.put("created", System.currentTimeMillis());
            JSONArray pointsJson = new JSONArray();
            for (GeoPoint point : route.points()) {
                JSONArray pair = new JSONArray();
                pair.put(point.latitude);
                pair.put(point.longitude);
                pointsJson.put(pair);
            }
            object.put("points", pointsJson);
            File file = new File(directory, id + ".json");
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (JSONException | IOException e) {
            throw new IllegalStateException("保存路线失败：" + e.getMessage(), e);
        }
    }

    private String readFormat(File file) {
        try {
            JSONObject object = new JSONObject(readFile(file));
            return object.optString("format", RouteParser.FORMAT_KML);
        } catch (Exception e) {
            return RouteParser.FORMAT_KML;
        }
    }

    private static String readFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String sanitizeName(String name) {
        String cleaned = name == null ? "" : name.trim().replaceAll("[\\r\\n\\t]", " ");
        if (cleaned.isEmpty()) {
            cleaned = "自定义路线";
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        }
        return cleaned;
    }

    /** 路线列表项。 */
    public static final class RouteSummary {
        public final String id;
        public final String name;
        public final double meters;
        public final int pointCount;
        public final String format;
        public final boolean builtin;

        RouteSummary(String id, String name, double meters, int pointCount, String format, boolean builtin) {
            this.id = id;
            this.name = name;
            this.meters = meters;
            this.pointCount = pointCount;
            this.format = format;
            this.builtin = builtin;
        }

        public String meta() {
            return String.format(Locale.US, "%.2f 公里 · %d 个点%s", meters / 1000.0, pointCount,
                    builtin ? " · 内置" : "");
        }
    }
}
