# 安卓电视安装与调试指南

## 目录

- [环境准备](#环境准备)
- [在真实电视上安装](#在真实电视上安装)
  - [开启 ADB 调试](#开启-adb-调试)
  - [有线连接安装](#有线连接安装)
  - [无线连接安装](#无线连接安装)
- [构建 APK](#构建-apk)
- [调试方法](#调试方法)
- [模拟器调试](#模拟器调试)
- [常见问题](#常见问题)

---

## 环境准备

### 必要工具

| 工具 | 版本要求 | 下载 |
|------|---------|------|
| JDK | 17 | `brew install openjdk@17` |
| Android SDK | API 34 | Android Studio 内置 |
| ADB | 任意 | Android SDK platform-tools |
| Gradle | 8.x（Wrapper 自动处理） | — |

### macOS 快速安装

```bash
# 安装 JDK 17
brew install openjdk@17

# 安装 Android 命令行工具
brew install android-commandlinetools

# 安装 platform-tools（含 adb）
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# 配置环境变量（加入 ~/.zshrc 或 ~/.bashrc）
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH
```

---

## 在真实电视上安装

### 开启 ADB 调试

不同品牌电视的操作路径略有差异，通用步骤如下：

**小米电视 / Redmi 电视**
1. 设置 → 关于 → 连续点击「版本号」7 次，开启开发者选项
2. 设置 → 设备偏好设置 → 开发者选项 → 打开「USB 调试」和「网络 ADB 调试」

**华为智慧屏 / 荣耀**
1. 设置 → 关于 → 连续点击「版本信息」7 次，开启开发者模式
2. 系统 → 开发者选项 → 打开「ADB 调试」

**索尼 Android TV**
1. 设置 → 设备偏好设置 → 关于 → 连续点击「版本号」7 次
2. 设置 → 设备偏好设置 → 开发者选项 → 打开「USB 调试」

**飞利浦 / 其他通用 Android TV**
1. 设置 → 更多设置 → 关于 → 连续点击「版本号」7 次
2. 返回到更多设置 → 开发者选项 → 打开「USB 调试」

> 开启后电视屏幕会弹出授权提示，选择「允许」，建议勾选「始终允许」。

---

### 有线连接安装

适用于电视有 USB 口且支持 ADB over USB 的情况（部分型号支持）。

```bash
# 用 USB 线连接电视与电脑，确认设备已识别
adb devices
# 输出示例：
# List of devices attached
# XXXXXXXX    device

# 构建并安装 Debug 版本
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.iptv.player/.MainActivity
```

---

### 无线连接安装

**最常用方式**，电视和电脑在同一局域网即可，无需 USB 线。

#### 第一步：查看电视 IP 地址

- 设置 → 网络 → 查看 IP 地址
- 或：开发者选项内通常显示当前 IP

#### 第二步：连接

```bash
# 替换为你电视的实际 IP 地址
TV_IP=192.168.1.100

adb connect $TV_IP:5555

# 确认连接成功
adb devices
# 输出示例：
# List of devices attached
# 192.168.1.100:5555    device
```

#### 第三步：安装应用

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 安装到电视
adb -s $TV_IP:5555 install -r app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb -s $TV_IP:5555 shell am start -n com.iptv.player/.MainActivity
```

#### 一键脚本

```bash
# 修改脚本中的 IP 后执行
TV_IP=192.168.1.100 ./run_emulator.sh
```

---

## 构建 APK

### Debug 版本（推荐用于安装调试）

```bash
./gradlew assembleDebug
# APK 路径：app/build/outputs/apk/debug/app-debug.apk
```

### Release 版本

Release 版本需要签名文件 `iptv-release.jks`（未包含在代码仓库中，需自行放置）。

```bash
# 将 iptv-release.jks 放到项目根目录后执行
./gradlew assembleRelease
# APK 路径：app/build/outputs/apk/release/app-release.apk
```

> **注意**：Release APK 体积更小、性能更好，适合正式分发。

---

## 调试方法

### 查看实时日志

```bash
# 查看全部日志（连接电视后）
adb logcat

# 只看本应用的日志（推荐）
adb logcat --pid=$(adb shell pidof -s com.iptv.player)

# 过滤指定 Tag
adb logcat -s IPTVPlayer:D

# 保存日志到文件
adb logcat > tv_log.txt
```

### Android Studio 无线调试（Android 11+）

Android 11 及以上的电视支持更便捷的无线配对方式：

1. 开发者选项 → 无线调试 → 开启
2. 点击「使用配对码配对设备」，记录 **IP 地址**、**端口** 和 **配对码**
3. 在 Android Studio 中：运行菜单 → 「Pair Devices Using Wi-Fi」→ 输入配对码
4. 配对成功后，在设备列表中选择电视直接运行

### 截图与录屏

```bash
# 截图并导出到本地
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./

# 录屏（按 Ctrl+C 停止）
adb shell screenrecord /sdcard/demo.mp4
adb pull /sdcard/demo.mp4 ./
```

### 模拟遥控器按键

```bash
# 常用遥控器键码
adb shell input keyevent KEYCODE_DPAD_UP      # 方向键上
adb shell input keyevent KEYCODE_DPAD_DOWN    # 方向键下
adb shell input keyevent KEYCODE_DPAD_LEFT    # 方向键左
adb shell input keyevent KEYCODE_DPAD_RIGHT   # 方向键右
adb shell input keyevent KEYCODE_DPAD_CENTER  # 确认键
adb shell input keyevent KEYCODE_BACK         # 返回键
adb shell input keyevent KEYCODE_HOME         # 主页键
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE  # 播放/暂停
```

---

## 模拟器调试

如果没有实体电视，可以使用 Android TV 模拟器。

### 创建 Android TV 模拟器

```bash
# 安装模拟器和 TV 系统镜像
sdkmanager "emulator" "system-images;android-34;google_atv;x86_64"

# 创建 TV AVD（名为 iptv_tv）
avdmanager create avd \
  --name iptv_tv \
  --package "system-images;android-34;google_atv;x86_64" \
  --device "tv_1080p"
```

### 启动并安装

```bash
# 使用项目内置脚本（已配置好环境变量）
chmod +x run_emulator.sh
./run_emulator.sh
```

脚本会自动完成：构建 APK → 启动模拟器 → 安装 → 运行。

### 手动启动模拟器

```bash
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH=$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH

# 启动模拟器
emulator -avd iptv_tv &

# 等待启动完成后安装
adb wait-for-device
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.iptv.player/.MainActivity
```

---

## 常见问题

### `adb: device unauthorized`

电视屏幕上弹出了授权提示但未确认。  
→ 在电视端找到弹框，选择「允许」，建议勾选「始终允许此计算机」。

### `adb connect` 连接失败

- 检查电视和电脑是否在**同一局域网**（同一路由器）
- 确认电视已开启「网络 ADB 调试」
- 部分电视每次重启后需要重新开启 ADB
- 尝试关闭电脑防火墙后重试

### 安装时提示 `INSTALL_FAILED_VERSION_DOWNGRADE`

```bash
# 先卸载旧版本再安装
adb uninstall com.iptv.player
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 视频无法播放（黑屏）

- 检查 M3U 源地址是否可访问
- 部分 HTTP 明文流需要确认 `AndroidManifest.xml` 中已配置 `usesCleartextTraffic="true"`（本项目已配置）
- 查看日志定位具体错误：`adb logcat -s ExoPlayer:D`

### 应用安装成功但在电视桌面找不到

本应用已配置 `LEANBACK_LAUNCHER`，正常显示在 Android TV 桌面的「应用」栏中。若找不到，尝试：

```bash
# 直接启动
adb shell am start -n com.iptv.player/.MainActivity
```
