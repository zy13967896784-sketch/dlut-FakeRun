# Android 构建环境搭建与编译/测试验证报告（build-verify · 冻结版 v4 / 最终交付）

> 本文件由 build-verify 生成，记录最终冻结版（2026-10-28 20:53）的环境与验证结果。
> 本角色未修改任何 app 源码（Java / res / Manifest / Gradle 脚本）。
> 构建命令、日志、APK 校验值均可复现。本文件现存放于 `docs/build-report.md`（由 Lead 归档入库）。

## 1. 结论速览（冻结版 v4，最终交付）

| 项目 | 结果 |
|---|---|
| Gradle 配置阶段（`help`） | **BUILD SUCCESSFUL** |
| `assembleDebug` | **BUILD SUCCESSFUL**，产出 debug APK |
| `testDebugUnitTest` | **53 个单测全部通过**（0 失败 / 0 错误 / 0 跳过，7 个 suite） |
| `lintDebug`（可选） | **BUILD SUCCESSFUL：0 errors / 52 warnings** |
| SDK 工具链 | 已安装并验证（见 §2） |
| 第三方依赖 | `org.osmdroid:osmdroid-android:6.1.18`（Maven Central，AAR 已合入） |

最终产物：
`C:\Users\zy330\Documents\deepseek-harness\default-workspace\dlut-FakeRun\app\build\outputs\apk\debug\app-debug.apk`
（**7,366,134 字节 = 7.02 MiB / 7.37 MB**，SHA-256 `91B2A53517542C26E95D00A9C4E05043111E4DDE2E63666B944F44469ECC899F`）

## 2. 环境与版本

- 工作区：`C:\Users\zy330\Documents\deepseek-harness\default-workspace`
- 仓库：`<ws>\dlut-FakeRun`（Gradle wrapper 8.7，AGP 8.6.0，compileSdk 34，minSdk 26，targetSdk 34）
- JDK：`C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot`（Microsoft OpenJDK 21.0.7+6-LTS）
- Android SDK 根：`C:\Users\zy330\Documents\deepseek-harness\default-workspace\.android-sdk`（约 394 MB）
  | 包 | 版本 | 验证文件 |
  |---|---|---|
  | cmdline-tools | 12.0（commandlinetools-win-11076708_latest） | `cmdline-tools\latest\bin\sdkmanager.bat`、`source.properties` |
  | platforms;android-34 | revision 3（Android 14 / API 34） | `platforms\android-34\android.jar`（26,361,808 B） |
  | build-tools;34.0.0 | 34.0.0 | `build-tools\34.0.0\aapt2.exe`（4,566,296 B） |
  | platform-tools | 37.0.1 | `platform-tools\adb.exe`（8,273,560 B） |
  | licenses | 全部已接受 | `sdkmanager --licenses` exit 0 |
- `dlut-FakeRun/local.properties`（已被 `.gitignore` 忽略，不入库）：
  ```
  sdk.dir=C:/Users/zy330/Documents/deepseek-harness/default-workspace/.android-sdk
  ```
- 项目依赖（`gradle/libs.versions.toml`）：appcompat 1.6.1、material 1.10.0、activity 1.8.0、constraintlayout 2.1.4、recyclerview 1.3.2、osmdroid-android 6.1.18、junit 4.13.2、androidx.test.ext:junit 1.1.5、espresso-core 3.5.1。

## 3. 必读：本机（DSH 沙箱）环境适配

本会话文件沙箱为 workspace-write，**workspace 之外的写入（含 `%USERPROFILE%\.gradle`、`%USERPROFILE%\.android`、`%TEMP%`）会被拒绝**，直接用 `gradlew.bat` 会分别报：

- `Could not create parent directory for lock file C:\Users\zy330\.gradle\wrapper\dists\...`
- `IO exception while downloading manifest` / `java.nio.file.NoSuchFileException: C:\Users\zy330\.android\cache\...`
- AGP 报 `Several environment variables ... different paths to the Android Preferences folder`（若同时设了 `ANDROID_PREFS_ROOT` 与 `ANDROID_USER_HOME`）

因此每次构建前必须注入（PowerShell，`$ws` = 工作区绝对路径）：

```powershell
$ws = 'C:\Users\zy330\Documents\deepseek-harness\default-workspace'
$env:JAVA_HOME         = 'C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot'
$env:Path              = "$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME  = Join-Path $ws '.gradle-home'
$env:ANDROID_USER_HOME = Join-Path $ws '.android-user-home'   # 不要同时设置 ANDROID_PREFS_ROOT
$env:ANDROID_SDK_ROOT  = Join-Path $ws '.android-sdk'
$env:ANDROID_HOME      = $env:ANDROID_SDK_ROOT
$env:TEMP              = Join-Path $ws 'tmp\java-tmp'
$env:TMP               = $env:TEMP
```

其它两个已解决的问题：

1. Gradle wrapper 自带下载对 `services.gradle.org` 报 `Read timed out`（Java 读超时）。已用 Python（`urllib`，HTTPS 正常）预下载 `gradle-8.7-bin.zip` 到
   `<ws>\.gradle-home\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7-bin.zip`，wrapper 直接解压即可。
2. Gradle 文件监视线程在沙箱下抛 `Couldn't open current thread, error = 5`。构建命令统一加 `--no-watch-fs`。

非致命、可忽略的噪声：
- AGP：`Unable to initialize metrics, ensure C:\Users\zy330\.android is writable`（analytics 写用户目录失败，不影响产物）
- javac：`Java compiler version 21 has deprecated support for compiling with source/target version 8`（项目 `compileOptions` 为 1.8，仅警告）

## 4. 构建与单测结果（冻结版 v4）

### 4.1 复现命令

```powershell
# 先注入 §3 的环境变量，然后：
cd <ws>\dlut-FakeRun
.\gradlew.bat --no-daemon --no-watch-fs assembleDebug testDebugUnitTest 2>&1 | Tee-Object build-log.txt
.\gradlew.bat --no-daemon --no-watch-fs lintDebug                            2>&1 | Tee-Object lint-log.txt
```

结果：
- 构建+单测：`BUILD SUCCESSFUL in 10s`，`36 actionable tasks: 7 executed, 29 up-to-date`
- lint：`BUILD SUCCESSFUL in 43s`，`0 errors, 52 warnings`
- 本轮（v4）仅改 `strings.xml` 的 `repo_url` 与 README，一次通过，无编译/资源/lint 错误。

### 4.2 APK（最终交付）

- 绝对路径：
  `C:\Users\zy330\Documents\deepseek-harness\default-workspace\dlut-FakeRun\app\build\outputs\apk\debug\app-debug.apk`
- 大小：**7,366,134 字节（7.02 MiB / 7.37 MB）**
- SHA-256：`91B2A53517542C26E95D00A9C4E05043111E4DDE2E63666B944F44469ECC899F`
- MD5：`D55F2D983255D80EFABBDAEC54C478BF`
- 构建时间：2026-10-28 20:53:10（覆盖截至 20:52:51 的全部源码）
- 历史版本（均已被本版取代）：
  | 版本 | 时间 | 大小 | SHA-256 |
  |---|---|---|---|
  | v3 | 20:35:48 | 7,366,122 B | `390FF36E9DD5CC234BAA19DE4C02DC8445EE6D3D8482F8F9AB3A5CB3A51F3DB1` |
  | v2 | 19:55:25 | 7,365,229 B | `C6D0C7D2B16CBDC2F333191D07002E6201BADB1174740DDEDC8CC35FB8B07783` |
  | v1 | 19:21:04 | 6,322,122 B | `90F6309AFAFEAB9C94184FF88E408A860478291104915A987C94A0B8C10DA64F` |
  | 19:15:30 | 19:15:30 | 6,761,676 B | `01E6C81604EAB86F00151922A4C8A0D3B711F585325D996BA6ADFC5B3CDB1B76` |
  | 初版 | 19:12:19 | 5,751,784 B | `B2D6E229ED66BB5440054B849F314C623043D51C8945BA5F51391975C8EC745A` |
- aapt2 badging 关键项：
  - `package=com.langqi.fakegps`，`versionName=2.0.0`，`versionCode=2`
  - `minSdk=26`，`targetSdk=34`，`compileSdk=34`
  - `application-label='轻跑'`，`launchable-activity=com.langqi.fakegps.MainActivity`
  - 应用图标：`application-icon-{160,240,320,480,640,65534}` → `res/mipmap-anydpi-v21/ic_launcher.xml`（新插画自适应图标）
  - 无 `native-code`（纯 Java，无 NDK）
  - 权限：ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION / ACCESS_MOCK_LOCATION / FOREGROUND_SERVICE / FOREGROUND_SERVICE_LOCATION / WAKE_LOCK / POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED / SCHEDULE_EXACT_ALARM / INTERNET / ACCESS_NETWORK_STATE
  - 组件：`MainActivity`、`RouteSketchActivity`、`SimulationService`、`reminder.ReminderReceiver`、`reminder.BootReceiver`
  - FileProvider：`androidx.core.content.FileProvider`，`authorities="com.langqi.fakegps.fileprovider"`，`grantUriPermissions="true"`，meta-data `android.support.FILE_PROVIDER_PATHS`（`app/src/main/res/xml/file_paths.xml` → `<cache-path name="share" path="share/" />`）
  - 内置路线资源：APK 内为 `raw/default_route`（`res/raw/default_route.kml`，4,007 B）；`raw/three_km` 已不存在
  - **仓库地址字符串（v4 新增校验）**：`aapt2 dump strings` 在 APK 字符串池中确认
    `String #1259 : https://github.com/zy13967896784-sketch/dlut-FakeRun` 与 `String #1246 : 访问 GitHub 仓库`，即改名后的新仓库链接已打包进 APK。
- 构建日志：`<ws>\dlut-FakeRun\build-log.txt`（5,270 B；.gitignore 已忽略）

### 4.3 单元测试（53 个，全绿）

结果 XML：`app\build\test-results\testDebugUnitTest\TEST-*.xml`
HTML：`app\build\reports\tests\testDebugUnitTest\index.html`

| suite | tests | failures | errors | skipped |
|---|---|---|---|---|
| com.langqi.fakegps.core.FormatsTest | 8 | 0 | 0 | 0 |
| com.langqi.fakegps.core.GeoMathTest | 9 | 0 | 0 | 0 |
| com.langqi.fakegps.core.RouteExporterTest | 3 | 0 | 0 | 0 |
| com.langqi.fakegps.core.RouteParserTest | 8 | 0 | 0 | 0 |
| com.langqi.fakegps.core.RouteTest | 8 | 0 | 0 | 0 |
| com.langqi.fakegps.reminder.ReminderTimeTest | 16 | 0 | 0 | 0 |
| com.langqi.fakegps.ExampleUnitTest（baseline 保留） | 1 | 0 | 0 | 0 |
| **合计** | **53** | **0** | **0** | **0** |

说明：v4 仅改 `strings.xml` / README，测试代码未变，因此 `testDebugUnitTest` 为 UP-TO-DATE 并复用 v3 的测试结果（同一份测试字节码），结果仍为 53/53 全绿。

## 5. lintDebug（最终 0 errors / 52 warnings，通过）

命令：`.\gradlew.bat --no-daemon --no-watch-fs lintDebug` → `BUILD SUCCESSFUL in 43s`，`0 errors, 52 warnings`。

报告位置：
- HTML：`app\build\reports\lint-results-debug.html`
- 文本：`app\build\intermediates\lint_intermediate_text_report\debug\lintReportDebug\lint-results-debug.txt`
- 控制台日志：`<ws>\dlut-FakeRun\lint-log.txt`（.gitignore 已忽略）

52 warnings 分布（全部非阻断）：UnusedResources 24、SetTextI18n 9、HardcodedText 5、Overdraw 2、MonochromeLauncherIcon 2、InflateParams 2、UseCompatTextViewDrawableXml 2、ObsoleteSdkInt 2，其余 DisableBaselineAlignment / AlwaysShowAction / VectorPath / NotifyDataSetChanged 各 1。

### 5.1 lint 修复轨迹（供追溯）

| 轮次 | 时间 | 结果 | 说明 |
|---|---|---|---|
| 第 1 轮 | 19:13 | 9 errors / 48 warnings | WrongConstant（MockLocationEngine:99/100）、AppLinkUrlError（Manifest:43）、UnspecifiedRegisterReceiverFlag（MainActivity:888）、UseAppTint×4 |
| 第 2 轮 | 19:16 | 1 error / 48 warnings | 上述修复生效，仅剩 AppLinkUrlError |
| 第 3 轮（v1） | 19:21 | 0 errors / 48 warnings | Manifest 加 `tools:ignore="AppLinkUrlError"` 后清零 |
| 第 4 轮（v2） | 19:56 | 0 errors / 52 warnings | 新图标引入 MonochromeLauncherIcon 2 + UnusedResources +2 |
| 第 5 轮（v3） | 20:36 | 0 errors / 52 warnings | 内置路线/设置入口改动均为资源与字符串层，无新增 lint 问题 |
| 第 6 轮（v4 最终） | 20:53 | **0 errors / 52 warnings** | 仅 `repo_url`/README 改动，lint 结果不变 |

## 6. 构建过程中发现并修复的源码问题（由 Lead 修复，本角色仅验证）

| # | 时间 | 失败点 | 根因与修复 |
|---|---|---|---|
| 1 | 19:07 | `app/build.gradle.kts:47` `Unresolved reference: recyclerview` | version catalog 缺 `recyclerview` 别名；19:09:42 补 `recyclerview = "1.3.2"` 后通过 |
| 2 | 19:20 | `RouteSketchActivity.java:56` `IConfigurationProvider 无法转换为 Configuration`，:57–:59 找不到符号 | osmdroid 6.1.18 的 `Configuration.getInstance()` 返回 `IConfigurationProvider`（`javap` 反查 AAR 证实）；改声明类型 + 补 import |
| 3 | 19:54 | `MainActivity.java:440` `变量 adapter 可能尚未初始化` | 匿名 Listener 在 `RouteAdapter adapter = new RouteAdapter(...)` 初始化表达式内引用自身；改为 `(RouteAdapter) recycler.getAdapter()` |
| 4 | 20:35 | （无错误） | v3 一次通过 |
| 5 | 20:53 | （无错误） | v4 一次通过 |

## 7. 各轮功能改动与验证覆盖

### v4（最终轮，20:53）
- **仓库改名适配**：GitHub 仓库改为 `zy13967896784-sketch/dlut-FakeRun`；`app/src/main/res/values/strings.xml` 的 `repo_url` 与 `README.md` 仓库链接同步更新，其它源码不动。
- 验证：构建通过；`aapt2 dump strings` 在 APK 中确认新 URL `https://github.com/zy13967896784-sketch/dlut-FakeRun` 与「访问 GitHub 仓库」文案已打包；README 不参与 APK 构建。

### v3（20:35）
- 内置路线替换：删 `res/raw/three_km.kml`，新增 `res/raw/default_route.kml`（宁大操场，105 点，约 1.03 km/圈，4,007 B）；`RouteStore.BUILTIN_ID = "builtin_ningda_track"`、内置名「宁大操场」、解析源改 `default_route.kml`，自动清理旧 `builtin_dlut_3k` 缓存；`RouteSketchActivity` 默认中心改宁大坐标。
- 字符串：`route_default_name` / `route_item_name_default` = 宁大操场，meta = 1.03 公里 · 105 个点；新增 `open_repo` / `repo_url`。
- 设置页：`sheet_settings` 新增 `btn_open_repo`，MainActivity 读取 `repo_url` 打开 GitHub。
- 单测：`RouteParserTest` 示例名称同步（仅文案，断言不变），总数仍 53。

### v2（19:55）
- 应用图标替换为新插画（`mipmap-*/ic_launcher*.png` + anydpi adaptive-icon 引用 `@mipmap` + `@color/launcher_background`）。
- 路线管理：`item_route`「⋯」菜单（`ic_more_vert`）、`RouteAdapter.Listener` 拆为 `onRouteSelected` / `onRouteActions`、`RouteStore.get/rename/updatePoints/formatOf`、`core/RouteExporter`（KML/GPX）、`RouteSketchActivity` 支持 `EXTRA_ROUTE_ID` 编辑、`MainActivity` 重命名/地图编辑/导出 KML、GPX（SAF）/分享（FileProvider + ACTION_SEND）。
- FileProvider：Manifest `provider` + `res/xml/file_paths.xml`。
- 新增 `RouteExporterTest` 3 个 → 53。

### v1（19:21）
- 手绘路线 `RouteSketchActivity`（osmdroid 6.1.18 地图）、配速随机浮动 `speedVariance`、完整 UI/主题/图标改造、纯 JVM 单测 50 个。

## 8. 时效性说明

- 冻结版 v4：磁盘上最新源码 mtime = `2026-10-28 20:52:51`（`app/src/main/res/values/strings.xml` 与 `README.md`），构建于 `20:53:10` 完成，**APK / 测试 / lint 三项均基于该时刻源码**；20:54 复核无更新。
- 若此后任何 Java / res / Manifest / Gradle 被修改，需按 §4.1 复跑并更新本报告中的 APK 大小、SHA-256、测试计数与 lint 状态。
- 交付时请只使用 §4.2 的「最终交付」APK 与其 SHA-256，勿与历史版本混淆。

## 9. 未修改 app 源码的声明

本角色只写入了：`.android-sdk/**`（SDK）、`<ws>\.gradle-home`、`<ws>\.android-user-home`、`tmp/**`（下载/检查脚本）、`dlut-FakeRun/local.properties`（gitignored）、仓库根 `build-log.txt`、`lint-log.txt`、本报告（现位于 `docs/build-report.md`）。未修改任何 Java / res / AndroidManifest / Gradle 构建脚本。
