# 时钟面板 · Clock

将闲置 Android 手机变成桌面时钟和电脑性能显示器。

应用以横屏全屏方式运行：电脑性能 API 可访问时显示 CPU、内存和 GPU 使用情况；接口不可用时自动回到大字时钟。前台保持屏幕常亮，适合旧手机长期摆放在桌面使用。

<img width="2400" height="1080" alt="performance-date" src="https://github.com/user-attachments/assets/dab67273-bd70-404c-951e-76cb3933cd0f" />
<img width="2400" height="1080" alt="offline" src="https://github.com/user-attachments/assets/f0cdf694-e204-4e82-a015-34b174123a2b" />
## 功能

- **双模式自动切换**：启动先显示时钟，收到有效性能数据后自动切换到性能面板。
- **大字时钟**：显示年月日、中文星期和 24 小时时间，小时与分钟使用大字，秒数使用小字。
- **电脑性能面板**：左侧三张横向卡片竖向排列，显示名称、加粗进度条、占用百分比和用量说明；右侧显示日期、时间和星期。
- **内存与显存用量**：显示内存已用 / 总容量，以及 GPU 显存已用 / 总容量。GPU 不可用时，CPU 和内存仍可正常显示。
- **前台常亮**：应用在前台显示时阻止屏幕自动休眠；切到后台或退出后恢复系统休眠规则。手动按电源键仍可锁屏。
- **按状态调整轮询**：在线每秒刷新，离线每 30 秒检测一次；后台停止轮询和时钟刷新，并取消进行中的请求。
- **可配置接口**：支持 HTTP / HTTPS 地址，修改后本地保存，下次启动自动使用。

## 运行要求

| 项目 | 要求 |
| --- | --- |
| 手机系统 | Android 11（API 30）及以上 |
| 显示方式 | 横屏，支持左右两个横屏方向 |
| 网络 | 手机能够访问电脑上的性能 API，通常需要处于同一局域网 |
| 电脑端 | 独立运行的性能采集服务，提供下文约定的 HTTP JSON 接口 |


本仓库包含 Android 客户端，不包含电脑端性能采集服务。纯时钟模式无需连接电脑，也无需联网。

## 快速开始

1. 构建并安装 APK，构建方法见下文。
2. 在电脑上启动兼容的性能 API 服务，确保手机能够访问它。
3. 打开应用，点击屏幕，在右上角选择 **设置**。
4. 将 **API 地址** 改为实际地址并保存。
5. 接口返回有效数据后，应用自动显示性能面板。

首次启动预填地址：

```text
http://192.168.50.10:18888/api/v1/metrics
```

这是默认配置，请按实际电脑 IP 和服务端口修改。建议为电脑配置固定的局域网地址，或在路由器上设置 DHCP 地址保留。

设置入口会在约 5 秒后自动隐藏，再次点击屏幕即可显示。设置窗口内可查看连接状态、错误信息，并恢复默认地址。

### 自动切换规则

| 状态或事件 | 应用行为 |
| --- | --- |
| 首次启动 / 返回前台 | 先显示时钟，立即检测 API |
| 收到有效性能数据 | 显示性能面板，按每秒一次的节奏刷新 |
| 连接失败、超时、非 HTTP 200 或必需数据无效 | 清除性能数据并显示时钟，30 秒后再检测 |
| 离线检测成功 | 自动恢复性能面板和每秒刷新 |
| 应用进入后台 | 暂停轮询和时钟刷新，取消进行中的网络请求 |

请求串行执行，慢请求不会叠加并发请求。离线的 30 秒间隔从确认失败后开始计算。连接与读取超时分别为 2 秒，单次请求总超时为 4 秒。

应用根据接口是否可用判断显示模式，不直接检测电脑电源状态。因此，电脑休眠、网络断开或性能服务退出时，也会切换到时钟模式。

## 性能 API

### 请求

```http
GET /api/v1/metrics
Accept: application/json
Cache-Control: no-cache
```

设置中保存的是完整 URL，路径不必固定为 `/api/v1/metrics`。接口应直接返回 HTTP `200` 和 JSON，客户端不跟随重定向，目前没有自定义认证请求头的设置入口。

### 响应示例

以下示例包含当前面板使用的主要字段：

```json
{
  "hostname": "MY-PC",
  "cpu": {
    "usage_percent": 42.5
  },
  "memory": {
    "usage_percent": 50.0,
    "used_gb": 16.0,
    "total_gb": 32.0
  },
  "gpu": {
    "available": true,
    "name": "Example GPU",
    "utilization_percent": 35.0,
    "vram_used_mb": 2048.0,
    "vram_total_mb": 8192.0,
    "vram_usage_percent": 25.0
  }
}
```

### 字段约定

| 字段 | 必需 | 用途 |
| --- | --- | --- |
| `cpu.usage_percent` | 是 | CPU 使用率，有限数值，范围 `0–100` |
| `memory.usage_percent` | 是 | 内存使用率，有限数值，范围 `0–100` |
| `hostname` | 否 | 性能区顶部的电脑名称，缺省显示“电脑” |
| `memory.used_gb` / `memory.total_gb` | 否 | 内存已用 / 总容量，非负有限数值 |
| `gpu.available` | 否 | 为 `true` 时读取 GPU 数据，否则显示显卡不可用 |
| `gpu.utilization_percent` | 否 | GPU 使用率，范围 `0–100`；无有效值时显示破折号 |
| `gpu.vram_used_mb` / `gpu.vram_total_mb` | 否 | 显存已用 / 总容量，除以 `1024` 后以 GB 展示 |
| `gpu.vram_usage_percent` | 否 | 缺少显存容量数据时，作为显存用量说明的回退值 |

只有 CPU 和内存使用率是进入性能模式的必需数据。可选容量数据缺失时显示不可用，不影响其他指标。

兼容响应中的额外字段会被忽略，例如 `service`、`timestamp`、`platform` 和 `gpus`。当前使用单个 `gpu` 对象，不展示多 GPU 列表；日期和时间来自手机本地系统，而非接口时间戳。

## 构建

### 开发环境

- JDK 17。
- Android SDK Platform 35。
- 支持当前项目 Gradle 配置的 Android Studio，或命令行构建环境。
- 项目自带 Gradle Wrapper，无需单独安装 Gradle。

当前项目使用 Kotlin、Jetpack Compose、Material 3、协程和 OkHttp，`compileSdk` / `targetSdk` 为 35，最低支持 API 30。

### Android Studio

1. 打开仓库根目录。
2. 将 Gradle JDK 配置为 JDK 17，安装项目需要的 Android SDK。
3. 完成 Gradle 同步。
4. 连接开启 USB 调试的手机，或启动兼容的模拟器。
5. 选择 `app` 模块并运行。

### 命令行

确保 `JAVA_HOME` 指向 JDK 17，并通过 Android Studio 生成本机的 `local.properties`，或正确配置 Android SDK 路径。首次构建需要下载 Gradle 和依赖。

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS / Linux：

```bash
bash ./gradlew :app:assembleDebug
```

生成的调试安装包：

```text
app/build/outputs/apk/debug/app-debug.apk
```

通过 ADB 安装（需要将 Android SDK 的 `platform-tools` 加入 PATH）：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

调试 APK 用于开发验证。发布正式版本前，请自行配置 release 签名；签名密钥和密码不应提交到仓库。

## 测试

Windows PowerShell：

```powershell
# 本地单元测试与 Android 静态检查
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug

# 连接已解锁、允许 USB 安装的手机后运行设备测试
.\gradlew.bat :app:connectedDebugAndroidTest
```

macOS / Linux 使用 `bash ./gradlew` 替换 `.\gradlew.bat`。

测试覆盖地址验证、轮询策略、JSON 解析、GPU 不可用、在线刷新、离线 30 秒重试、连接恢复、设置持久化、前后台切换和网络请求取消。设备测试使用手机上的本地测试服务器，不依赖真实电脑性能服务；正常结束时恢复原 API 配置。部分用例会实际等待 30 秒，执行期间请保持设备解锁，避免手动切换应用。

## 项目结构

```text
app/src/
├── main/
│   ├── AndroidManifest.xml             # 网络权限与横屏配置
│   └── java/com/lyx/clock/
│       ├── MainActivity.kt             # 窗口、生命周期、配置保存和轮询
│       ├── ClockScreen.kt              # 时钟、性能卡片和设置界面
│       ├── Metrics.kt                  # 数据模型、JSON 解析和 HTTP 请求
│       └── MonitorPolicy.kt            # 轮询间隔与地址验证
├── test/                               # 本地单元测试
└── androidTest/                        # 设备端解析、网络和界面测试
```

## 常见问题

### 电脑本机可以访问 API，手机无法访问

先在手机浏览器中打开相同地址。如果浏览器也无法访问，请检查：

- 地址是否为电脑的局域网 IP，而不是 `localhost` 或 `127.0.0.1`。
- 服务是否监听局域网网卡或 `0.0.0.0`，而不是只监听回环地址。
- 电脑防火墙是否允许局域网设备访问对应 TCP 端口，默认端口为 `18888`。
- 手机是否连接访客网络，路由器是否启用了客户端隔离。

### 手机浏览器能打开接口，但应用仍显示时钟

检查响应是否为 HTTP `200`，且包含有效的 `cpu.usage_percent` 和 `memory.usage_percent`。登录页面、重定向、HTML 内容或无效 JSON 都不会进入性能模式。设置窗口会显示最近的连接状态；离线时最多需要等待一个 30 秒检测间隔。

### 手机会一直亮屏吗

应用在前台显示时保持常亮。切到后台或退出后恢复系统休眠规则；手动锁屏仍然有效，应用不会自动唤醒屏幕。

### 时钟时间不准确

应用使用手机本地时间和时区。请检查系统的自动校时和时区设置；修改 API 返回的时间不会改变时钟。

### GPU 显示不可用

检查服务是否返回 `gpu.available: true`，并提供有效的 GPU 指标。GPU 缺失不会阻止 CPU 和内存正常展示。

