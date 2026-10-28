# Android 构建环境搭建与编译/测试验证报告（build-verify · 冻结版）

> 本文件由 build-verify 生成，记录最终冻结版（2026-10-28 19:21）的环境与验证结果。
> 本角色未修改任何 app 源码（Java / res / Manifest / Gradle 脚本）。
> 构建命令、日志、APK 校验值均可复现。

## 1. 结论速览（最终冻结版）

| 项目 | 结果 |
|---|---|
| Gradle 配置阶段（`help`） | **BUILD SUCCESSFUL** |
| `assembleDebug` | **BUILD SUCCESSFUL**，产出 debug APK |
| `testDebugUnitTest` | **50 个单测全部通过**（0 失败 / 0 错误 / 0 跳过，6 个 suite） |
| `lintDebug`（可选） | **BUILD SUCCESSFUL：0 errors / 48 warnings** |
| SDK 工具链 | 已安装并验证（见 §2） |
| 新增第三方依赖 | `org.osmdroid:osmdroid-android:6.1.18`（Maven Central，AAR 已下载并合入） |

最终产物：
`C:\Users\zy330\Documents\deepseek-harness\default-workspace\dlut-FakeRun\app\build\outputs\apk\debug\app-debug.apk`
（**6,322,122 字节 = 6.03 MiB / 6.32 MB**，SHA-256 `90F6309AFAFEAB9C94184FF88E408A860478291104915A987C94A0B8C10DA64F`）

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
- `dlut-FakeRun/local.properties`（已被 `.gitignore:17` 忽略，不入库）：
  ```
  sdk.dir=C:/Users/zy330/Documents/deepseek-harness/default-workspace/.android-sdk
  ```
- 项目依赖（`gradle/libs.versions.toml`）：appcompat 1.6.1、material 1.10.0、activity 1.8.0、constraintlayout 2.1.4、recyclerview 1.3.2、**osmdroid-android 6.1.18**、junit 4.13.2、androidx.test.ext:junit 1.1.5、espresso-core 3.5.1。

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

## 4. 构建与单测结果（冻结版）

### 4.1 复现命令

```powershell
# 先注入 §3 的环境变量，然后：
cd <ws>\dlut-FakeRun
.\gradlew.bat --no-daemon --no-watch-fs assembleDebug testDebugUnitTest 2>&1 | Tee-Object build-log.txt
.\gradlew.bat --no-daemon --no-watch-fs lintDebug                            2>&1 | Tee-Object lint-log.txt
```

结果：
- 构建+单测：`BUILD SUCCESSFUL in 14s`，`36 actionable tasks: 14 executed, 22 up-to-date`
- lint：`BUILD SUCCESSFUL in 43s`，`0 errors, 48 warnings`

### 4.2 APK

- 绝对路径：
  `C:\Users\zy330\Documents\deepseek-harness\default-workspace\dlut-FakeRun\app\build\outputs\apk\debug\app-debug.apk`
- 大小：**6,322,122 字节（6.03 MiB / 6.32 MB）**
- SHA-256：`90F6309AFAFEAB9C94184FF88E408A860478291104915A987C94A0B8C10DA64F`
- MD5：`6110DC886AD1F7888C64791CF4DE3CC3`
- 构建时间：2026-10-28 19:21:04（覆盖截至 19:20:45 的全部源码）
- 历史版本（均已被本版取代）：
  - 19:15:30 版：6,761,676 B，SHA-256 `01E6C81604EAB86F00151922A4C8A0D3B711F585325D996BA6ADFC5B3CDB1B76`（无 osmdroid / 无手绘路线）
  - 19:12:19 版：5,751,784 B，SHA-256 `B2D6E229ED66BB5440054B849F314C623043D51C8945BA5F51391975C8EC745A`
- aapt2 badging 关键项：
  - `package=com.langqi.fakegps`，`versionName=2.0.0`，`versionCode=2`
  - `minSdk=26`，`targetSdk=34`，`compileSdk=34`
  - `application-label='轻跑'`，`launchable-activity=com.langqi.fakegps.MainActivity`
  - 无 `native-code`（纯 Java，无 NDK）
  - 权限：ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION / ACCESS_MOCK_LOCATION / FOREGROUND_SERVICE / FOREGROUND_SERVICE_LOCATION / WAKE_LOCK / POST_NOTIFICATIONS / RECEIVE_BOOT_COMPLETED / SCHEDULE_EXACT_ALARM / **INTERNET** / **ACCESS_NETWORK_STATE**（后两项为 osmdroid 联网瓦片所需，已生效）
  - 合并后 Manifest 组件：`MainActivity`、`RouteSketchActivity`（手绘路线页）、`SimulationService`、`reminder.ReminderReceiver`、`reminder.BootReceiver`
- 构建日志：`<ws>\dlut-FakeRun\build-log.txt`（8,862 B）

### 4.3 单元测试（50 个，全绿）

结果 XML：`app\build\test-results\testDebugUnitTest\TEST-*.xml`
HTML：`app\build\reports\tests\testDebugUnitTest\index.html`

| suite | tests | failures | errors | skipped |
|---|---|---|---|---|
| com.langqi.fakegps.core.FormatsTest | 8 | 0 | 0 | 0 |
| com.langqi.fakegps.core.GeoMathTest | 9 | 0 | 0 | 0 |
| com.langqi.fakegps.core.RouteParserTest | 8 | 0 | 0 | 0 |
| com.langqi.fakegps.core.RouteTest | 8 | 0 | 0 | 0 |
| com.langqi.fakegps.reminder.ReminderTimeTest | 16 | 0 | 0 | 0 |
| com.langqi.fakegps.ExampleUnitTest（baseline 保留） | 1 | 0 | 0 | 0 |
| **合计** | **50** | **0** | **0** | **0** |

说明：本版新增的 `RouteSketchActivity`（手绘路线）与 `speedVariance`（配速随机浮动）属 UI/服务层，未新增纯 JVM 单测，故测试总数仍为 50。

## 5. lintDebug（最终 0 errors / 48 warnings，通过）

命令：`.\gradlew.bat --no-daemon --no-watch-fs lintDebug` → `BUILD SUCCESSFUL in 43s`，`0 errors, 48 warnings`。

报告位置：
- HTML：`app\build\reports\lint-results-debug.html`
- 文本：`app\build\intermediates\lint_intermediate_text_report\debug\lintReportDebug\lint-results-debug.txt`
- 控制台日志：`<ws>\dlut-FakeRun\lint-log.txt`

48 warnings 分布（全部非阻断）：UnusedResources 22、SetTextI18n 9、HardcodedText 5、InflateParams 2、Overdraw 2、UseCompatTextViewDrawableXml 2、ObsoleteSdkInt 2，其余 AlwaysShowAction / DisableBaselineAlignment / VectorPath / NotifyDataSetChanged 各 1。

### 5.1 lint 修复轨迹（供追溯）

| 轮次 | 时间 | 结果 | 说明 |
|---|---|---|---|
| 第 1 轮 | 19:13 | 9 errors / 48 warnings | WrongConstant（MockLocationEngine:99/100）、AppLinkUrlError（Manifest:43）、UnspecifiedRegisterReceiverFlag（MainActivity:888）、UseAppTint×4（activity_main.xml:468/527、item_route.xml:49） |
| 第 2 轮 | 19:16 | 1 error / 48 warnings | MockLocationEngine 改用 `ProviderProperties.*` + `@SuppressLint`、`app:tint`、MainActivity 分支、`BROWSABLE` 移除均已生效；仅剩 Manifest:43 的 AppLinkUrlError |
| 第 3 轮（冻结） | 19:21 | **0 errors / 48 warnings** | Manifest 第 47 行 `<intent-filter tools:ignore="AppLinkUrlError">` 后清零 |

## 6. 构建过程中发现并修复的源码问题（由 Lead 修复，本角色仅验证）

1. **19:07 首次 `help` 失败**：`app/build.gradle.kts:47 implementation(libs.recyclerview)` 时 `gradle/libs.versions.toml` 尚无 `recyclerview` 别名 → 19:09:42 补充 `recyclerview = "1.3.2"` 后通过。教训：新增依赖必须同步加 version catalog 别名，否则配置阶段就失败。
2. **19:20 冻结版首跑编译失败**（4 个错误，均在 `RouteSketchActivity.java`）：
   - `:56` `Configuration configuration = Configuration.getInstance();` → `IConfigurationProvider 无法转换为 Configuration`
   - `:57/:58/:59` `setUserAgentValue(…)` / `setOsmdroidBasePath(…)` / `setOsmdroidTileCache(…)` 找不到符号
   - 根因：osmdroid 6.1.18 的 `Configuration.getInstance()` 返回 `IConfigurationProvider`（已用 `javap` 反查 AAR 内 `classes.jar` 确认）。
   - 修复：`IConfigurationProvider configuration = Configuration.getInstance();` + `import org.osmdroid.config.IConfigurationProvider;`（57–59 行不变）→ 19:20:45 修复后本轮全绿。

## 7. 时效性说明

- 冻结版：磁盘上最新源码 mtime = `2026-10-28 19:20:45`（`RouteSketchActivity.java`），构建于 `19:21:04` 完成，**APK / 测试 / lint 三项均基于该时刻源码**，之后源码未再变动（19:21:55 复核无更新）。
- 若此后任何 Java / res / Manifest / Gradle 被修改，需按 §4.1 复跑并更新本报告中的 APK 大小、SHA-256、测试计数与 lint 状态。
- 本轮之前的两份旧 APK 记录见 §4.2「历史版本」，避免交付时混淆。

## 8. 未修改 app 源码的声明

本角色只写入了：`.android-sdk/**`（SDK）、`<ws>\.gradle-home`、`<ws>\.android-user-home`、`tmp/**`（下载/检查脚本）、`dlut-FakeRun/local.properties`（gitignored）、仓库根 `build-log.txt`、`lint-log.txt`、本报告。未修改任何 Java / res / AndroidManifest / Gradle 构建脚本。
