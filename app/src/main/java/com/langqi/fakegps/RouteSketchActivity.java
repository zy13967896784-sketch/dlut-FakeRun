package com.langqi.fakegps;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.langqi.fakegps.core.GeoMath;
import com.langqi.fakegps.core.GeoPoint;
import com.langqi.fakegps.core.Route;
import com.langqi.fakegps.core.RouteParser;
import com.langqi.fakegps.core.RouteStore;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 在真实地图上手绘跑步路线：双指缩放/拖动地图，单点地图添加轨迹点，
 * 保存后写入本地路线库并设为当前路线。
 *
 * <p>底图使用 OpenStreetMap 瓦片（需联网），仅用于绘制参考；轨迹数据只保存在本机。</p>
 */
public class RouteSketchActivity extends AppCompatActivity {

    /** 传入已有路线的 id 时为"编辑轨迹"模式，否则为新建手绘路线。 */
    public static final String EXTRA_ROUTE_ID = "route_id";

    private static final double DEFAULT_LAT = 39.0855;
    private static final double DEFAULT_LNG = 121.8088;
    private static final int MAX_POINTS = 500;

    private final List<org.osmdroid.util.GeoPoint> sketchPoints = new ArrayList<>();

    private RouteStore routeStore;
    private MapView mapView;
    private Polyline polyline;
    private TextView tvPointCount;
    private MaterialButton btnUndo;
    private MaterialButton btnClear;
    private MaterialButton btnSave;
    private String editingRouteId;
    private String editingRouteName;
    private String editingRouteFormat = RouteParser.FORMAT_TEXT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IConfigurationProvider configuration = Configuration.getInstance();
        configuration.setUserAgentValue(getPackageName());
        configuration.setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        configuration.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));

        setContentView(R.layout.activity_route_editor);

        routeStore = new RouteStore(this);
        String requestedRouteId = getIntent().getStringExtra(EXTRA_ROUTE_ID);
        Route editingRoute = requestedRouteId == null ? null : routeStore.get(requestedRouteId);
        if (editingRoute != null) {
            editingRouteId = requestedRouteId;
            editingRouteName = editingRoute.name();
            editingRouteFormat = routeStore.formatOf(requestedRouteId);
            for (GeoPoint point : editingRoute.points()) {
                sketchPoints.add(new org.osmdroid.util.GeoPoint(point.latitude, point.longitude));
            }
        }

        MaterialToolbar toolbar = findViewById(R.id.editor_toolbar);
        toolbar.setTitle(getString(editingRouteId == null
                ? R.string.route_sketch_title : R.string.route_sketch_edit_title));
        toolbar.setNavigationOnClickListener(v -> finish());

        mapView = findViewById(R.id.map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(17.0);
        mapView.getController().setCenter(initialCenter(editingRoute));

        polyline = new Polyline(mapView);
        polyline.setColor(ContextCompat.getColor(this, R.color.brand_primary));
        polyline.setWidth(dp(6.0f));
        mapView.getOverlays().add(polyline);

        MapEventsOverlay eventsOverlay = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(org.osmdroid.util.GeoPoint point) {
                addPoint(point);
                return true;
            }

            @Override
            public boolean longPressHelper(org.osmdroid.util.GeoPoint point) {
                return false;
            }
        });
        mapView.getOverlays().add(eventsOverlay);

        tvPointCount = findViewById(R.id.tv_point_count);
        btnUndo = findViewById(R.id.btn_undo);
        btnClear = findViewById(R.id.btn_clear);
        btnSave = findViewById(R.id.btn_save_route);

        btnUndo.setOnClickListener(v -> undo());
        btnClear.setOnClickListener(v -> {
            sketchPoints.clear();
            refresh();
        });
        btnSave.setOnClickListener(v -> saveRoute());
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mapView != null) {
            mapView.onDetach();
        }
        super.onDestroy();
    }

    private org.osmdroid.util.GeoPoint initialCenter(Route editingRoute) {
        try {
            Route route = editingRoute != null ? editingRoute : routeStore.current();
            if (route != null && route.size() > 0) {
                double latitude = 0.0;
                double longitude = 0.0;
                for (GeoPoint point : route.points()) {
                    latitude += point.latitude;
                    longitude += point.longitude;
                }
                return new org.osmdroid.util.GeoPoint(latitude / route.size(), longitude / route.size());
            }
        } catch (Exception ignored) {
            // 没有可用路线时使用大工操场默认位置
        }
        return new org.osmdroid.util.GeoPoint(DEFAULT_LAT, DEFAULT_LNG);
    }

    private void addPoint(org.osmdroid.util.GeoPoint point) {
        if (sketchPoints.size() >= MAX_POINTS) {
            toast("最多 " + MAX_POINTS + " 个点，请精简后再保存");
            return;
        }
        if (!sketchPoints.isEmpty()) {
            org.osmdroid.util.GeoPoint last = sketchPoints.get(sketchPoints.size() - 1);
            double distance = GeoMath.distanceMeters(
                    new GeoPoint(last.getLatitude(), last.getLongitude()),
                    new GeoPoint(point.getLatitude(), point.getLongitude()));
            if (distance < 3.0) {
                return;
            }
        }
        sketchPoints.add(point);
        refresh();
    }

    private void undo() {
        if (!sketchPoints.isEmpty()) {
            sketchPoints.remove(sketchPoints.size() - 1);
            refresh();
        }
    }

    private void refresh() {
        polyline.setPoints(new ArrayList<>(sketchPoints));
        mapView.invalidate();
        tvPointCount.setText("已选 " + sketchPoints.size() + " 个点");
        boolean hasPoints = !sketchPoints.isEmpty();
        btnUndo.setEnabled(hasPoints);
        btnClear.setEnabled(hasPoints);
        btnSave.setEnabled(sketchPoints.size() >= 2);
    }

    private void saveRoute() {
        if (sketchPoints.size() < 2) {
            toast("至少在地图上点 2 个点");
            return;
        }
        List<GeoPoint> points = new ArrayList<>(sketchPoints.size() + 1);
        for (org.osmdroid.util.GeoPoint point : sketchPoints) {
            points.add(new GeoPoint(point.getLatitude(), point.getLongitude()));
        }
        GeoPoint first = points.get(0);
        GeoPoint last = points.get(points.size() - 1);
        double gap = GeoMath.distanceMeters(first, last);
        if (gap > 3.0 && gap < 80.0) {
            // 起点终点很近时自动补一个起点，让每圈自然闭合
            points.add(first);
        }
        String name = "手绘路线 " + new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(new Date());
        try {
            if (editingRouteId != null) {
                Route updated = routeStore.updatePoints(editingRouteId, editingRouteName, points,
                        editingRouteFormat);
                toast("已保存修改：" + updated.name() + "（"
                        + String.format(Locale.US, "%.2f", updated.totalMeters() / 1000.0) + " 公里）");
            } else {
                Route route = routeStore.importRoute(name, points, RouteParser.FORMAT_TEXT);
                toast("已保存：" + route.name() + "（"
                        + String.format(Locale.US, "%.2f", route.totalMeters() / 1000.0) + " 公里）");
            }
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            toast("保存失败：" + e.getMessage());
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
