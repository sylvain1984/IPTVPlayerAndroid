# IPTV Player Android

Android TV 版 IPTV 播放器，支持 M3U 直播源、RTC 独家直播和频道管理。

## 功能

- M3U 直播源订阅，后台定期刷新
- 火山引擎 RTC 独家直播（PIN 码鉴权）
- 流有效性后台检测，自动过滤失效频道
- Firebase 实时数据库同步频道列表
- Jetpack Compose + Android TV 专属 UI

## 配置

复制 `local.properties.example` 为 `local.properties` 并填入实际值：

```
sdk.dir=/path/to/android/sdk

RTC_APP_ID=<火山引擎 RTC App ID>
RTC_TOKEN_URL=<Token 服务端点>
LIVE_REGISTRY_URL=<Firebase 实时数据库 URL>

# 可选：发布签名（不填则用调试签名）
ANDROID_SIGNING_STORE_FILE=/path/to/release.jks
ANDROID_SIGNING_STORE_PASSWORD=...
ANDROID_SIGNING_KEY_ALIAS=...
ANDROID_SIGNING_KEY_PASSWORD=...
```

## 部署到 TCL 电视

TCL 电视内置 **TGuard** 安全系统，会拦截所有带 `debuggable=true` 标志的调试版 APK（返回 `INSTALL_FAILED_VERIFICATION_FAILURE`）。**不能直接 `adb install` debug APK**。

### 解决方案

使用 `deploy_tv.sh` 脚本，流程为：编译 release APK → 用 debug keystore 签名 → 安装。

```bash
# 修改脚本中的 TV_IP（默认 192.168.1.6:5555）
./deploy_tv.sh
```

### 连接新电视

1. 开启开发者模式：设置 → 设备偏好设置 → 关于 → 连按 7 次版本号
2. 开启 ADB 调试（开发者选项中）
3. `adb connect <电视IP>:5555`
4. 修改 `deploy_tv.sh` 中的 `TV_IP`，运行脚本

### 注意事项

- `build.gradle.kts` 的 release 构建保留了 `isDebuggable = true`，可用 Android Studio 附加调试器
- Gson TypeToken 不能用匿名子类（R8 会破坏泛型签名），改用 `TypeToken.getParameterized()`

## 调试

```bash
# 实时日志
adb -s <TV_IP>:5555 logcat | grep com.iptv.player

# 重启应用
adb -s <TV_IP>:5555 shell am start -n com.iptv.player/.MainActivity
```
