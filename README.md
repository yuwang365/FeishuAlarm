# 飞书牛马助手（FeishuAlarm）

基于定时提醒与 **目标 WiFi（SSID）** 判定的 Android 小工具：在设定的工作日时间点，若手机已连接指定 WiFi，则自动唤起飞书（Lark）；否则通过通知与前台服务等待连上目标网络后再唤起。不依赖 Google Play 服务。

## 功能说明

- **多组监控时间**：在应用内添加/删除时间点（`HH:mm`），使用 `AlarmManager` 在每天对应时刻触发；**周六、周日不执行**闹钟逻辑（仍会处理开机等广播以维持调度）。
- **默认时间点**：首次进入且列表中不含 `09:15` 时，会自动加入 `09:15` 与 `18:50` 两个默认时间（见 `MainActivity`）。
- **打卡条件（当前实现）**：到达设定时间后，若当前 WiFi 的 SSID 与代码中的目标一致，则进入「打开飞书」流程；否则发送「请连接目标 WiFi」类通知，并启动前台服务内的 **WiFi 网络监听**，连上目标 SSID 后再执行同一套打开逻辑。
- **锁屏与解锁**：若已满足 WiFi 条件但设备仍处于锁屏，会发送高优先级提醒通知，并记录「待打开飞书」标记；用户解锁后由 `ACTION_USER_PRESENT` 广播触发自动打开飞书。
- **立即打开飞书**：主界面提供按钮，直接尝试启动包名为 `com.ss.android.lark` 的飞书客户端。
- **前台服务**：使用 `foregroundServiceType="specialUse"`（Android 14+ 适配），降低后台被系统回收的概率；等待 WiFi 时通知上提供「关闭监控」操作。
- **开机自恢复**：监听 `BOOT_COMPLETED`，重新注册所有闹钟。

## 目标 WiFi 如何修改

目标 SSID 写死在 `WifiNetworkHelper` 中：

```kotlin
const val TARGET_SSID = "candymobi"
```

请按实际办公网络名称修改该常量并重新编译安装。

## 权限与系统设置说明

| 权限 / 能力 | 用途 |
|-------------|------|
| `ACCESS_FINE_LOCATION` | Android 读取当前 WiFi SSID 的常规前置条件之一（与系统策略相关）。 |
| `ACCESS_BACKGROUND_LOCATION` | 在后台仍能读取 WiFi 信息时使用（应用内会引导用户改为「始终允许」等）。 |
| `NEARBY_WIFI_DEVICES`（Android 13+，`neverForLocation`） | 符合新规下与 WiFi/附近设备相关的声明。 |
| `POST_NOTIFICATIONS` | 发送打卡与等待 WiFi 等通知。 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 尽量在允许时注册精确闹钟。 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 前台服务类型声明。 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复闹钟。 |
| `WAKE_LOCK` | 提醒时短暂点亮屏幕等。 |
| `SYSTEM_ALERT_WINDOW` | 应用会检查并引导开启「在其他应用上层显示」，用于部分机型上辅助唤起场景。 |
| `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 网络与 WiFi 状态相关能力。 |

**重要**：为稳定后台行为，请在系统设置中为该应用开启**自启动 / 后台运行 / 忽略电池优化**（名称因厂商而异），并确认 **系统定位服务已打开**；否则部分机型上 SSID 可能读不到（代码中会记录日志并提示无法读取 WiFi 名称）。

## 技术栈与版本

- **语言**：Kotlin **2.2.10**
- **构建**：Android Gradle Plugin **9.1.0**（Version Catalog：`gradle/libs.versions.toml`）
- **SDK**：`minSdk` **26**，`compileSdk` / `targetSdk` **35**
- **UI**：Jetpack **Compose** + **Material 3**（`composeBom` 见 `libs.versions.toml`）
- **核心组件**：`AlarmManager`、`BroadcastReceiver`（`AlarmReceiver`）、前台 `Service`（`AlarmService`）、`ConnectivityManager.NetworkCallback`（监听 WiFi 连接变化）

## 本地构建

1. 安装 **Android Studio**（建议支持 AGP 9.x 的版本）与 **JDK 11**（工程 `compileOptions` 为 Java 11）。
2. 克隆工程后，用 Android Studio 打开根目录，同步 Gradle。
3. 修改 `WifiNetworkHelper.TARGET_SSID` 为你的目标 WiFi 名称（若与默认不同）。
4. 运行 **app** 模块到真机（闹钟、WiFi SSID、锁屏与厂商限制建议在真机上验证）。

命令行示例（需本机已配置 Android SDK）：

```bash
./gradlew :app:assembleDebug
```

## 工程结构（简要）

- `app/src/main/java/com/ai/feishualarm/ui/page/MainActivity.kt` — Compose 主界面、权限引导、闹钟列表与飞书按钮。
- `app/.../helper/AlarmHelper.kt` — 闹钟时间的持久化与 `AlarmManager` 调度。
- `app/.../receiver/AlarmReceiver.kt` — 闹钟与开机广播，工作日判断与 WiFi 检查入口。
- `app/.../service/AlarmService.kt` — 前台服务、等待目标 WiFi 的网络回调与通知更新。
- `app/.../helper/WifiNetworkHelper.kt` — 目标 SSID、SSID 读取与权限/定位服务检查。
- `app/.../helper/AlarmActionHandler.kt` — 通知渠道、锁屏提醒、唤醒屏幕与打开飞书流程协调。
- `app/.../helper/FeishuLauncher.kt` — 通过包名 `com.ss.android.lark` 启动飞书。
- `app/.../receiver/UnlockReceiver.kt` — 解锁后消费「待打开飞书」标记并启动飞书。

## 免责声明

本工具仅用于在本人设备上辅助唤起已安装的飞书客户端，不涉及对飞书服务端或打卡规则的破解或篡改。使用请遵守公司与飞书的使用条款，并自行承担因系统限制、权限或电池策略导致的提醒失败风险。
