package com.langqi.fakegps;

import android.Manifest;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.langqi.fakegps.core.AppPrefs;
import com.langqi.fakegps.core.Formats;
import com.langqi.fakegps.core.Route;
import com.langqi.fakegps.core.RouteExporter;
import com.langqi.fakegps.core.RouteParser;
import com.langqi.fakegps.core.RouteStore;
import com.langqi.fakegps.core.SimulationConfig;
import com.langqi.fakegps.reminder.ReminderScheduler;
import com.langqi.fakegps.ui.RouteAdapter;
import com.langqi.fakegps.ui.RoutePreviewView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 单屏主页：选择路线 / 目标距离 / 配速，开始模拟，设置每日提醒。
 */
public class MainActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private RouteStore routeStore;
    private SimulationConfig config;
    private Route route;

    private MaterialToolbar toolbar;
    private TextView tvState;
    private TextView tvStateHint;
    private TextView tvDistance;
    private TextView tvElapsed;
    private TextView tvPace;
    private TextView tvTargetLeft;
    private TextView tvRouteName;
    private TextView tvRouteMeta;
    private TextView tvPaceValue;
    private TextView tvPaceMin;
    private TextView tvPaceMax;
    private TextView tvEta;
    private TextView tvReminderTime;
    private TextView tvSetup;
    private LinearProgressIndicator progressStatus;
    private View cardSetup;
    private MaterialButton btnChangeRoute;
    private MaterialButton btnPrimary;
    private MaterialButton btnStop;
    private MaterialButton btnPickTime;
    private MaterialButton btnOpenDevSettings;
    private TextInputEditText etTarget;
    private ChipGroup chipGroupTarget;
    private Chip chip2;
    private Chip chip3;
    private Chip chip5;
    private Slider sliderPace;
    private SwitchMaterial switchReminder;
    private RoutePreviewView routePreview;

    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<String[]> documentLauncher;
    private ActivityResultLauncher<Intent> sketchLauncher;
    private ActivityResultLauncher<String> createDocumentLauncher;

    private RouteStore.RouteSummary pendingExportRoute;
    private String pendingExportFormat = "kml";

    private boolean receiverRegistered;
    private boolean updatingTarget;
    private boolean updatingReminder;
    private int uiState = SimulationService.STATE_IDLE;
    private String lastErrorShown = "";
    private Uri handledImportUri;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !SimulationService.ACTION_STATUS.equals(intent.getAction())) {
                return;
            }
            renderStatus(new SimulationService.StatusSnapshot(
                    intent.getIntExtra(SimulationService.EXTRA_STATE, SimulationService.STATE_IDLE),
                    intent.getDoubleExtra(SimulationService.EXTRA_DISTANCE, 0.0),
                    intent.getDoubleExtra(SimulationService.EXTRA_TARGET, config.targetMeters),
                    intent.getLongExtra(SimulationService.EXTRA_ELAPSED, 0L),
                    intent.getStringExtra(SimulationService.EXTRA_ROUTE_NAME),
                    intent.getStringExtra(SimulationService.EXTRA_MESSAGE)));
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new AppPrefs(this);
        routeStore = new RouteStore(this);

        bindViews();
        setupLaunchers();
        setupToolbar();
        setupListeners();
        loadConfigToUi();
        loadRoute();
        refreshReminderCard();
        refreshSetupCard();
        renderStatus(SimulationService.snapshot());
        requestLocationPermission();

        if (!prefs.isGuideShown()) {
            prefs.setGuideShown(true);
            showGuideDialog(true);
        }
        if (savedInstanceState == null) {
            handleImportIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleImportIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerStatusReceiver();
        renderStatus(SimulationService.snapshot());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshSetupCard();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (receiverRegistered) {
            try {
                unregisterReceiver(statusReceiver);
            } catch (Exception ignored) {
            }
            receiverRegistered = false;
        }
    }

    // ------------------------------------------------------------------ 初始化

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        tvState = findViewById(R.id.tv_state);
        tvStateHint = findViewById(R.id.tv_state_hint);
        tvDistance = findViewById(R.id.tv_distance);
        tvElapsed = findViewById(R.id.tv_elapsed);
        tvPace = findViewById(R.id.tv_pace);
        tvTargetLeft = findViewById(R.id.tv_target_left);
        progressStatus = findViewById(R.id.progress_status);
        tvRouteName = findViewById(R.id.tv_route_name);
        tvRouteMeta = findViewById(R.id.tv_route_meta);
        routePreview = findViewById(R.id.route_preview);
        btnChangeRoute = findViewById(R.id.btn_change_route);
        etTarget = findViewById(R.id.et_target);
        chipGroupTarget = findViewById(R.id.chip_group_target);
        chip2 = findViewById(R.id.chip_2);
        chip3 = findViewById(R.id.chip_3);
        chip5 = findViewById(R.id.chip_5);
        tvPaceValue = findViewById(R.id.tv_pace_value);
        tvPaceMin = findViewById(R.id.tv_pace_min);
        tvPaceMax = findViewById(R.id.tv_pace_max);
        tvEta = findViewById(R.id.tv_eta);
        sliderPace = findViewById(R.id.slider_pace);
        switchReminder = findViewById(R.id.switch_reminder);
        tvReminderTime = findViewById(R.id.tv_reminder_time);
        btnPickTime = findViewById(R.id.btn_pick_time);
        cardSetup = findViewById(R.id.card_setup);
        tvSetup = findViewById(R.id.tv_setup);
        btnOpenDevSettings = findViewById(R.id.btn_open_dev_settings);
        btnPrimary = findViewById(R.id.btn_primary);
        btnStop = findViewById(R.id.btn_stop);
    }

    private void setupLaunchers() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    refreshSetupCard();
                    if (Boolean.FALSE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                        toast("未授予定位权限，模拟定位可能无法启动");
                    }
                });
        documentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        importRouteFromUri(uri);
                    }
                });
        sketchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadRoute();
                        toast("路线已保存并设为当前路线");
                    }
                });
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        writeExportFile(uri);
                    }
                });
    }

    private void setupToolbar() {
        toolbar.setTitle(getString(R.string.app_name));
        toolbar.setSubtitle("畅动力 · 校园跑");
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_settings) {
                showSettingsSheet();
                return true;
            }
            if (id == R.id.action_help) {
                showGuideDialog(false);
                return true;
            }
            return false;
        });
    }

    private void setupListeners() {
        btnChangeRoute.setOnClickListener(v -> showRouteSheet());
        btnPrimary.setOnClickListener(v -> onPrimaryClicked());
        btnStop.setOnClickListener(v -> SimulationService.stop(this));
        btnPickTime.setOnClickListener(v -> showTimePicker());
        btnOpenDevSettings.setOnClickListener(v -> openDeveloperSettings());
        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingReminder) {
                return;
            }
            onReminderToggled(isChecked);
        });

        sliderPace.addOnChangeListener((slider, value, fromUser) -> {
            double speed = Formats.speedFromPaceSeconds(value);
            prefs.saveSpeedMps(speed);
            config = prefs.loadConfig();
            updatePaceLabel(value);
            updateEta();
        });

        etTarget.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingTarget) {
                    return;
                }
                Double km = parseDouble(s == null ? null : s.toString());
                if (km == null || km < 0.1 || km > 100.0) {
                    return;
                }
                prefs.saveTargetMeters(km * 1000.0);
                config = prefs.loadConfig();
                updateEta();
                updatingTarget = true;
                updateChipSelection(config.targetMeters);
                updatingTarget = false;
            }
        });

        chipGroupTarget.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (updatingTarget || checkedIds.isEmpty()) {
                return;
            }
            int chipId = checkedIds.get(0);
            int km = 0;
            if (chipId == R.id.chip_2) {
                km = 2;
            } else if (chipId == R.id.chip_3) {
                km = 3;
            } else if (chipId == R.id.chip_5) {
                km = 5;
            }
            if (km > 0) {
                updatingTarget = true;
                etTarget.setText(String.format(Locale.US, "%.1f", (double) km));
                updatingTarget = false;
                prefs.saveTargetMeters(km * 1000.0);
                config = prefs.loadConfig();
                updateEta();
            }
        });
    }

    private void loadConfigToUi() {
        config = prefs.loadConfig();
        double paceSeconds = 1000.0 / config.speedMps;
        float sliderValue = (float) (Math.round((paceSeconds - 180.0) / 15.0) * 15.0 + 180.0);
        sliderValue = Math.max(180.0f, Math.min(540.0f, sliderValue));
        updatingTarget = true;
        sliderPace.setValue(sliderValue);
        updatePaceLabel(sliderValue);
        etTarget.setText(Formats.km(config.targetMeters));
        updatingTarget = false;
        updateChipSelection(config.targetMeters);
        tvPaceMin.setText(Formats.paceFromSecondsPerKm(180.0));
        tvPaceMax.setText(Formats.paceFromSecondsPerKm(540.0));
        updateEta();
    }

    private void updatePaceLabel(double paceSecondsPerKm) {
        tvPaceValue.setText(Formats.paceFromSecondsPerKm(paceSecondsPerKm));
    }

    private void updateEta() {
        double speed = config.speedMps;
        long estimated = Math.round(config.targetMeters / speed * 1000.0);
        String varianceText = config.speedVariance > 0.001
                ? " · 配速浮动 ±" + Math.round(config.speedVariance * 100.0) + "%"
                : "";
        tvEta.setText("预计用时 " + Formats.duration(estimated)
                + " · 平均配速 " + Formats.paceFromSpeed(speed) + varianceText);
    }

    private void updateChipSelection(double targetMeters) {
        int km = (int) Math.round(targetMeters / 1000.0);
        if (km == 2) {
            chipGroupTarget.check(R.id.chip_2);
        } else if (km == 3) {
            chipGroupTarget.check(R.id.chip_3);
        } else if (km == 5) {
            chipGroupTarget.check(R.id.chip_5);
        } else {
            chipGroupTarget.clearCheck();
        }
    }

    // ------------------------------------------------------------------ 路线

    private void loadRoute() {
        try {
            route = routeStore.current();
        } catch (Exception e) {
            route = null;
            toast("路线加载失败：" + e.getMessage());
        }
        renderRoute();
    }

    private void renderRoute() {
        if (route == null) {
            tvRouteName.setText("还没有路线");
            tvRouteMeta.setText("点「更换」导入 KML/GPX，或直接粘贴坐标");
            routePreview.setRoute(null);
            return;
        }
        tvRouteName.setText(route.name());
        tvRouteMeta.setText(Formats.km(route.totalMeters()) + " 公里 · "
                + route.size() + " 个点");
        routePreview.setRoute(route);
    }

    private void showRouteSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View content = getLayoutInflater().inflate(R.layout.sheet_routes, null);
        dialog.setContentView(content);

        RecyclerView recycler = content.findViewById(R.id.recycler_routes);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        RouteAdapter adapter = new RouteAdapter(new RouteAdapter.Listener() {
            @Override
            public void onRouteSelected(RouteStore.RouteSummary summary) {
                routeStore.setCurrentId(summary.id);
                loadRoute();
                dialog.dismiss();
                toast("已切换到「" + summary.name + "」");
            }

            @Override
            public void onRouteActions(RouteStore.RouteSummary summary) {
                showRouteActions(dialog, (RouteAdapter) recycler.getAdapter(), summary);
            }
        });
        recycler.setAdapter(adapter);
        adapter.submit(routeStore.list(), prefs.getRouteId());

        content.findViewById(R.id.btn_import_file).setOnClickListener(v -> {
            dialog.dismiss();
            try {
                documentLauncher.launch(new String[]{"*/*"});
            } catch (Exception e) {
                toast("无法打开文件选择器");
            }
        });
        content.findViewById(R.id.btn_draw_route).setOnClickListener(v -> {
            dialog.dismiss();
            sketchLauncher.launch(new Intent(this, RouteSketchActivity.class));
        });
        content.findViewById(R.id.btn_paste_route).setOnClickListener(v -> {
            dialog.dismiss();
            showPasteDialog();
        });
        dialog.show();
    }

    private void confirmDeleteRoute(BottomSheetDialog sheet, RouteStore.RouteSummary summary) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除路线")
                .setMessage("确定删除「" + summary.name + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    routeStore.delete(summary.id);
                    if (summary.id.equals(prefs.getRouteId())) {
                        loadRoute();
                    }
                    toast("已删除");
                    sheet.dismiss();
                })
                .show();
    }

    private void showRouteActions(BottomSheetDialog sheet, RouteAdapter adapter,
                                  RouteStore.RouteSummary summary) {
        final String[] actions = {"重命名", "在地图上编辑轨迹", "导出 KML 文件", "导出 GPX 文件",
                "分享路线", "删除路线"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(summary.name)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showRenameDialog(adapter, summary);
                            break;
                        case 1:
                            sheet.dismiss();
                            sketchLauncher.launch(new Intent(this, RouteSketchActivity.class)
                                    .putExtra(RouteSketchActivity.EXTRA_ROUTE_ID, summary.id));
                            break;
                        case 2:
                            startExport(summary, "kml");
                            break;
                        case 3:
                            startExport(summary, "gpx");
                            break;
                        case 4:
                            shareRoute(summary);
                            break;
                        case 5:
                            confirmDeleteRoute(sheet, summary);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void showRenameDialog(RouteAdapter adapter, RouteStore.RouteSummary summary) {
        EditText input = new EditText(this);
        input.setText(summary.name);
        input.setSelection(input.getText().length());
        FrameLayout container = new FrameLayout(this);
        int padding = dp(20);
        container.setPadding(padding, dp(8), padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new MaterialAlertDialogBuilder(this)
                .setTitle("重命名路线")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("名称不能为空");
                        return;
                    }
                    if (routeStore.rename(summary.id, name)) {
                        if (summary.id.equals(prefs.getRouteId())) {
                            loadRoute();
                        }
                        adapter.submit(routeStore.list(), prefs.getRouteId());
                        toast("已重命名为「" + name + "」");
                    } else {
                        toast("重命名失败，路线可能已被删除");
                    }
                })
                .show();
    }

    private void startExport(RouteStore.RouteSummary summary, String format) {
        pendingExportRoute = summary;
        pendingExportFormat = format;
        try {
            createDocumentLauncher.launch(RouteExporter.suggestedFileName(summary.name, format));
        } catch (Exception e) {
            toast("无法打开文件保存器：" + e.getMessage());
        }
    }

    private void writeExportFile(Uri uri) {
        RouteStore.RouteSummary summary = pendingExportRoute;
        if (summary == null) {
            return;
        }
        Route exportRoute = routeStore.get(summary.id);
        if (exportRoute == null) {
            toast("路线不存在，无法导出");
            pendingExportRoute = null;
            return;
        }
        String content = "gpx".equals(pendingExportFormat)
                ? RouteExporter.toGpx(exportRoute) : RouteExporter.toKml(exportRoute);
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new IOException("无法写入文件");
            }
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            toast("已导出：" + RouteExporter.suggestedFileName(summary.name, pendingExportFormat));
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        } finally {
            pendingExportRoute = null;
        }
    }

    private void shareRoute(RouteStore.RouteSummary summary) {
        Route shareRoute = routeStore.get(summary.id);
        if (shareRoute == null) {
            toast("路线不存在，无法分享");
            return;
        }
        try {
            File shareDir = new File(getCacheDir(), "share");
            if (!shareDir.exists() && !shareDir.mkdirs()) {
                throw new IOException("无法创建临时目录");
            }
            File file = new File(shareDir,
                    RouteExporter.suggestedFileName(shareRoute.name(), "kml"));
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                outputStream.write(RouteExporter.toKml(shareRoute)
                        .getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("application/vnd.google-earth.kml+xml")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, shareRoute.name())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "分享路线"));
        } catch (Exception e) {
            toast("分享失败：" + e.getMessage());
        }
    }

    private void showPasteDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_paste_route, null);
        TextInputEditText nameInput = content.findViewById(R.id.et_route_name);
        TextInputEditText coordsInput = content.findViewById(R.id.et_coords);
        if (nameInput.getText() == null || nameInput.getText().toString().trim().isEmpty()) {
            nameInput.setText("自定义路线");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("粘贴坐标")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("导入", (dialog, which) -> {
                    String text = coordsInput.getText() == null ? "" : coordsInput.getText().toString();
                    String name = nameInput.getText() == null ? "" : nameInput.getText().toString();
                    try {
                        RouteParser.Result parsed = RouteParser.parseText(text, name);
                        Route imported = routeStore.importRoute(parsed.name, parsed.points, parsed.format);
                        loadRoute();
                        toast("已导入：" + imported.name() + "（"
                                + Formats.km(imported.totalMeters()) + " 公里）");
                    } catch (Exception e) {
                        toast("坐标格式有误：" + e.getMessage());
                    }
                })
                .show();
    }

    private void importRouteFromUri(Uri uri) {
        if (uri == null || uri.equals(handledImportUri)) {
            return;
        }
        handledImportUri = uri;
        String displayName = queryDisplayName(uri);
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("无法读取文件");
            }
            RouteParser.Result parsed = RouteParser.parse(inputStream, displayName);
            Route imported = routeStore.importRoute(parsed.name, parsed.points, parsed.format);
            loadRoute();
            toast("已导入：" + imported.name() + "（"
                    + Formats.km(imported.totalMeters()) + " 公里）");
        } catch (Exception e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    private void handleImportIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Uri data = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            data = intent.getData();
        } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
            data = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (data != null) {
            importRouteFromUri(data);
        }
    }

    private String queryDisplayName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }
        return name == null ? "导入路线" : name;
    }

    // ------------------------------------------------------------------ 模拟

    private void onPrimaryClicked() {
        switch (uiState) {
            case SimulationService.STATE_RUNNING:
                SimulationService.pause(this);
                break;
            case SimulationService.STATE_PAUSED:
                SimulationService.resume(this);
                break;
            default:
                startSimulation();
                break;
        }
    }

    private void startSimulation() {
        if (!commitTargetFromInput()) {
            return;
        }
        if (route == null) {
            toast("请先选择一条路线");
            return;
        }
        if (!isMockLocationAllowed()) {
            showMockPermissionDialog();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            toast("请先授予定位权限");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        }
        if (!SimulationService.start(this)) {
            toast("模拟服务启动失败，请重试");
            return;
        }
        tvState.setText("启动中");
        tvStateHint.setText("正在准备模拟定位…");
    }

    private boolean commitTargetFromInput() {
        Double km = parseDouble(etTarget.getText() == null ? null : etTarget.getText().toString());
        if (km == null || km < 0.1 || km > 100.0) {
            toast("目标距离请填写 0.1 ~ 100 公里");
            return false;
        }
        prefs.saveTargetMeters(km * 1000.0);
        config = prefs.loadConfig();
        updateEta();
        return true;
    }

    private void renderStatus(SimulationService.StatusSnapshot status) {
        if (status == null) {
            return;
        }
        uiState = status.state;
        double target = status.targetMeters > 0.0 ? status.targetMeters : config.targetMeters;
        double distance = status.distanceMeters;
        int progress = (int) Math.round(Math.min(1.0, target <= 0.0 ? 0.0 : distance / target) * 100.0);
        progressStatus.setProgressCompat(progress, true);
        tvDistance.setText(Formats.km(distance));
        tvElapsed.setText(Formats.duration(status.elapsedMillis));

        boolean active = uiState == SimulationService.STATE_RUNNING
                || uiState == SimulationService.STATE_PAUSED;
        boolean editable = !active;

        double paceSeconds;
        if (distance > 20.0 && status.elapsedMillis > 2000L) {
            paceSeconds = (status.elapsedMillis / 1000.0) * 1000.0 / distance;
        } else {
            paceSeconds = 1000.0 / config.speedMps;
        }
        tvPace.setText(Formats.paceFromSecondsPerKm(paceSeconds));

        switch (uiState) {
            case SimulationService.STATE_RUNNING:
                tvState.setText("模拟中");
                tvStateHint.setText("保持轻跑后台运行，切到畅动力开始跑步即可");
                tvTargetLeft.setText("剩余 " + Formats.km(Math.max(0.0, target - distance)) + " 公里");
                btnPrimary.setText("暂停");
                btnStop.setVisibility(View.VISIBLE);
                break;
            case SimulationService.STATE_PAUSED:
                tvState.setText("已暂停");
                tvStateHint.setText("点「继续」恢复模拟；停下来的时间不会计入用时");
                tvTargetLeft.setText("剩余 " + Formats.km(Math.max(0.0, target - distance)) + " 公里");
                btnPrimary.setText("继续");
                btnStop.setVisibility(View.VISIBLE);
                break;
            case SimulationService.STATE_FINISHED:
                tvState.setText("已完成");
                tvStateHint.setText(Formats.km(target) + " 公里 · 用时 "
                        + Formats.duration(status.elapsedMillis) + " · 平均配速 "
                        + Formats.averagePace(status.elapsedMillis, distance));
                tvTargetLeft.setText("目标已达成");
                btnPrimary.setText("再来一次");
                btnStop.setVisibility(View.GONE);
                break;
            case SimulationService.STATE_ERROR:
                tvState.setText("启动失败");
                tvStateHint.setText(status.message == null || status.message.isEmpty()
                        ? "请检查「开发者选项 → 选择模拟位置信息应用」" : status.message);
                tvTargetLeft.setText("--");
                btnPrimary.setText("重试");
                btnStop.setVisibility(View.GONE);
                if (status.message != null && !status.message.isEmpty()
                        && !status.message.equals(lastErrorShown)) {
                    lastErrorShown = status.message;
                    toast(status.message);
                }
                break;
            default:
                tvState.setText("准备就绪");
                tvStateHint.setText("选择路线与配速，点「开始模拟」");
                tvTargetLeft.setText("目标 " + Formats.km(target) + " 公里");
                btnPrimary.setText("开始模拟");
                btnStop.setVisibility(View.GONE);
                break;
        }

        setControlsEditable(editable);

        if (route != null && route.totalMeters() > 0.0) {
            float previewProgress = uiState == SimulationService.STATE_FINISHED
                    ? 1.0f
                    : (float) ((distance % route.totalMeters()) / route.totalMeters());
            routePreview.setProgress(previewProgress);
        }
        applyKeepScreenOn(uiState == SimulationService.STATE_RUNNING && config.keepScreenOn);
    }

    private void setControlsEditable(boolean editable) {
        etTarget.setEnabled(editable);
        sliderPace.setEnabled(editable);
        btnChangeRoute.setEnabled(editable);
        chip2.setEnabled(editable);
        chip3.setEnabled(editable);
        chip5.setEnabled(editable);
    }

    private void applyKeepScreenOn(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // ------------------------------------------------------------------ 提醒

    private void refreshReminderCard() {
        int minute = ReminderScheduler.getMinuteOfDay(this);
        tvReminderTime.setText(String.format(Locale.US, "每天 %02d:%02d", minute / 60, minute % 60));
        updatingReminder = true;
        switchReminder.setChecked(ReminderScheduler.isEnabled(this));
        updatingReminder = false;
    }

    private void onReminderToggled(boolean enabled) {
        if (enabled) {
            ensureNotificationPermission();
            checkExactAlarmPermission();
            ReminderScheduler.setEnabled(this, true);
            toast("已开启每日提醒");
        } else {
            ReminderScheduler.setEnabled(this, false);
            toast("已关闭每日提醒");
        }
        refreshReminderCard();
    }

    private void showTimePicker() {
        int minute = ReminderScheduler.getMinuteOfDay(this);
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(minute / 60)
                .setMinute(minute % 60)
                .setTitleText("每日提醒时间")
                .build();
        picker.addOnPositiveButtonClickListener(v -> {
            int selectedMinute = picker.getHour() * 60 + picker.getMinute();
            ReminderScheduler.setMinuteOfDay(this, selectedMinute);
            if (!ReminderScheduler.isEnabled(this)) {
                ensureNotificationPermission();
                checkExactAlarmPermission();
                ReminderScheduler.setEnabled(this, true);
            } else {
                ReminderScheduler.reschedule(this);
            }
            refreshReminderCard();
            toast(String.format(Locale.US, "已设置每天 %02d:%02d 提醒",
                    picker.getHour(), picker.getMinute()));
        });
        picker.show(getSupportFragmentManager(), "reminder_time_picker");
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
        }
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || alarmManager.canScheduleExactAlarms()) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("让提醒更准时")
                .setMessage("系统默认会稍微延迟提醒。允许「闹钟和提醒」权限后，每日提醒可以准点送达。")
                .setNegativeButton("暂不", null)
                .setPositiveButton("去授权", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        toast("请手动在系统设置中允许闹钟和提醒权限");
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------ 设置

    private void showSettingsSheet() {
        View content = getLayoutInflater().inflate(R.layout.sheet_settings, null);
        TextInputEditText intervalInput = content.findViewById(R.id.et_interval);
        TextInputEditText altitudeInput = content.findViewById(R.id.et_altitude);
        TextInputEditText accelerationInput = content.findViewById(R.id.et_acceleration);
        TextInputEditText varianceInput = content.findViewById(R.id.et_speed_variance);
        SwitchMaterial keepScreenSwitch = content.findViewById(R.id.switch_keep_screen);
        MaterialButton doneButton = content.findViewById(R.id.btn_settings_done);

        intervalInput.setText(String.format(Locale.US, "%.1f", config.intervalMs / 1000.0));
        altitudeInput.setText(String.format(Locale.US, "%.0f", config.altitudeMeters));
        accelerationInput.setText(String.format(Locale.US, "%.1f", config.acceleration));
        varianceInput.setText(String.format(Locale.US, "%.0f", config.speedVariance * 100.0));
        keepScreenSwitch.setChecked(config.keepScreenOn);

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(content);
        doneButton.setOnClickListener(v -> {
            Double interval = parseDouble(textOf(intervalInput));
            Double altitude = parseDouble(textOf(altitudeInput));
            Double acceleration = parseDouble(textOf(accelerationInput));
            Double variancePercent = parseDouble(textOf(varianceInput));
            if (interval == null || interval < 0.1 || interval > 5.0) {
                toast("推送间隔请填写 0.1 ~ 5 秒");
                return;
            }
            if (altitude == null || altitude < -500 || altitude > 9000) {
                toast("海拔请填写 -500 ~ 9000 米");
                return;
            }
            if (acceleration == null || acceleration < -20 || acceleration > 20) {
                toast("加速度请填写 -20 ~ 20");
                return;
            }
            if (variancePercent == null || variancePercent < 0 || variancePercent > 30) {
                toast("配速浮动请填写 0 ~ 30（百分比）");
                return;
            }
            prefs.saveIntervalMs((int) Math.round(interval * 1000.0));
            prefs.saveAltitudeMeters(altitude);
            prefs.saveAcceleration(acceleration);
            prefs.saveSpeedVariance(variancePercent / 100.0);
            prefs.saveKeepScreenOn(keepScreenSwitch.isChecked());
            config = prefs.loadConfig();
            updateEta();
            dialog.dismiss();
            toast("设置已保存，下次开始模拟时生效");
        });
        dialog.show();
    }

    private void refreshSetupCard() {
        boolean allowed = isMockLocationAllowed();
        cardSetup.setVisibility(allowed ? View.GONE : View.VISIBLE);
        tvSetup.setText("模拟定位尚未授权：打开「开发者选项 → 选择模拟位置信息应用」，选择「轻跑」");
    }

    private boolean isMockLocationAllowed() {
        try {
            android.app.AppOpsManager appOps =
                    (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return true;
            }
            int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return true;
        }
    }

    private void showMockPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("需要开启模拟定位")
                .setMessage("请先在「开发者选项 → 选择模拟位置信息应用」中选择「轻跑」，然后重新开始模拟。")
                .setNegativeButton("取消", null)
                .setNeutralButton("使用说明", (dialog, which) -> showGuideDialog(false))
                .setPositiveButton("去设置", (dialog, which) -> openDeveloperSettings())
                .show();
    }

    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                toast("请手动打开系统设置 → 开发者选项");
            }
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void showGuideDialog(boolean firstRun) {
        String message = "1. 打开系统「开发者选项」\n"
                + "2. 找到「选择模拟位置信息应用」，选择「轻跑」\n"
                + "3. 回到轻跑，选好路线和配速，点「开始模拟」\n"
                + "4. 切到「畅动力」开始跑步，位置会按路线自动推进\n"
                + "5. 跑完后先在畅动力结束，再回轻跑点「结束」\n\n"
                + "小提示：\n"
                + "· 默认目标 3.10 公里、配速 5'30\"，可按学校要求调整\n"
                + "· 路线支持 KML / GPX 文件、粘贴坐标，导入后本地保存\n"
                + "· 每日提醒到点通知，可直接从通知开始模拟\n\n"
                + "本项目仅供学习与测试，请遵守学校规定。";
        new MaterialAlertDialogBuilder(this)
                .setTitle(firstRun ? "欢迎使用轻跑" : "使用说明")
                .setMessage(message)
                .setNegativeButton("关闭", null)
                .setPositiveButton("开发者选项", (dialog, which) -> openDeveloperSettings())
                .show();
    }

    // ------------------------------------------------------------------ 工具

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStatusReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(SimulationService.ACTION_STATUS);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(statusReceiver, filter);
            }
            receiverRegistered = true;
        } catch (Exception e) {
            receiverRegistered = false;
        }
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private static Double parseDouble(String text) {
        if (text == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim());
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void toast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
