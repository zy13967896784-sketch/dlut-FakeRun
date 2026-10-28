package com.langqi.fakegps.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.langqi.fakegps.core.GeoMath;
import com.langqi.fakegps.core.GeoPoint;
import com.langqi.fakegps.core.Route;

/**
 * 轻量的路线预览：把经纬度轨迹等比缩放到卡片里，并画出当前进度点。
 * 不依赖地图 SDK，离线可用。
 */
public class RoutePreviewView extends View {

    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint startPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private Route route;
    private float progress;
    private float[] screenX;
    private float[] screenY;
    private boolean closeLoop;

    private double midLatRadians;
    private double minX;
    private double minY;
    private double scale;
    private float offsetX;
    private float offsetY;
    private float padding;

    public RoutePreviewView(Context context) {
        this(context, null);
    }

    public RoutePreviewView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RoutePreviewView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        int primary = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorPrimary, Color.parseColor("#1E7A5F"));
        int surface = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorSurface, Color.WHITE);

        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(dp(3.0f));
        routePaint.setStrokeCap(Paint.Cap.ROUND);
        routePaint.setStrokeJoin(Paint.Join.ROUND);
        routePaint.setColor(primary);

        startPaint.setStyle(Paint.Style.FILL);
        startPaint.setColor(primary);
        startPaint.setAlpha(110);

        markerOuterPaint.setStyle(Paint.Style.FILL);
        markerOuterPaint.setColor(surface);

        markerInnerPaint.setStyle(Paint.Style.FILL);
        markerInnerPaint.setColor(primary);
    }

    public void setRoute(@Nullable Route route) {
        this.route = route;
        this.progress = 0.0f;
        rebuild();
        invalidate();
    }

    /** @param progress 0~1，路线循环内的进度。 */
    public void setProgress(float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        if (Math.abs(clamped - this.progress) < 0.001f) {
            return;
        }
        this.progress = clamped;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize((int) dp(300.0f), widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = heightMode == MeasureSpec.UNSPECIFIED
                ? (int) dp(132.0f) : resolveSize((int) dp(132.0f), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuild();
    }

    private void rebuild() {
        path.reset();
        if (route == null || getWidth() <= 0 || getHeight() <= 0 || route.size() < 2) {
            screenX = null;
            screenY = null;
            return;
        }
        int count = route.size();
        screenX = new float[count];
        screenY = new float[count];
        double minLng = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double midLat = 0.0;
        for (GeoPoint point : route.points()) {
            minLng = Math.min(minLng, point.longitude);
            maxLng = Math.max(maxLng, point.longitude);
            minLat = Math.min(minLat, point.latitude);
            maxLat = Math.max(maxLat, point.latitude);
            midLat += point.latitude;
        }
        midLat /= count;
        midLatRadians = Math.toRadians(midLat);

        minX = minLng * Math.cos(midLatRadians);
        minY = minLat;
        double maxX = maxLng * Math.cos(midLatRadians);
        double maxY = maxLat;
        double spanX = Math.max(1e-9, maxX - minX);
        double spanY = Math.max(1e-9, maxY - minY);

        padding = dp(12.0f);
        float usableWidth = Math.max(1.0f, getWidth() - padding * 2.0f);
        float usableHeight = Math.max(1.0f, getHeight() - padding * 2.0f);
        scale = Math.min(usableWidth / spanX, usableHeight / spanY);
        offsetX = (float) ((getWidth() - spanX * scale) / 2.0);
        offsetY = (float) ((getHeight() - spanY * scale) / 2.0);

        for (int i = 0; i < count; i++) {
            screenX[i] = projectX(route.points().get(i));
            screenY[i] = projectY(route.points().get(i));
        }
        closeLoop = GeoMath.distanceMeters(route.points().get(0),
                route.points().get(count - 1)) < 30.0;

        path.moveTo(screenX[0], screenY[0]);
        for (int i = 1; i < count; i++) {
            path.lineTo(screenX[i], screenY[i]);
        }
        if (closeLoop) {
            path.close();
        }
    }

    private float projectX(GeoPoint point) {
        double x = point.longitude * Math.cos(midLatRadians);
        return offsetX + (float) ((x - minX) * scale);
    }

    private float projectY(GeoPoint point) {
        return getHeight() - offsetY - (float) ((point.latitude - minY) * scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (screenX == null || screenY == null || route == null) {
            return;
        }
        canvas.drawPath(path, routePaint);
        canvas.drawCircle(screenX[0], screenY[0], dp(3.0f), startPaint);

        Route.Sample sample = route.sampleAt(progress * route.totalMeters());
        float markerX = projectX(sample.point);
        float markerY = projectY(sample.point);
        canvas.drawCircle(markerX, markerY, dp(7.0f), markerOuterPaint);
        canvas.drawCircle(markerX, markerY, dp(5.0f), markerInnerPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
