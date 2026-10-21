# 首个纵向切片交付记录

## 构建与测试

环境：JDK 21，`ANDROID_HOME=/home/box/Android/Sdk`，仓库 Gradle wrapper。

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/box/Android/Sdk ./gradlew assembleDebug testDebugUnitTest lintDebug
```

2026-10-21 最终结果：**BUILD SUCCESSFUL，13/13 测试通过，0 失败/跳过；lint 0 errors、13 warnings（依赖版本提示及竖屏提示）**。

- debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- 单测报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- lint 报告：`app/build/reports/lint-results-debug.html`
- 11 个纯逻辑测试：中文字段/时间/坐标格式、开关、覆盖值、天气匹配、设置校验、EXIF 数据生成。
- 2 个 Robolectric/native graphics 测试（SDK 35）：真实 JPEG 转正、底部背景与白字像素、隐藏全部字段、不保留原始 GPS、手动坐标半球、EXIF 日期/时区/GPS UTC 时间/地点回读。
- APK 元信息检查：显示名水印相机，包名 `com.nu11b1ade.watermarkcam`，min 26 / target 37，debuggable；存储权限仅限 API 28 及以下。

## Code review

仓库尚无首次提交，审查范围为本次初始工作树的源码、测试与规格；采用 Standards / Spec 两个独立视角，并复查修复结果。

### Standards

- 无硬性约定违规。
- 修复两处非阻塞重复策略：设置默认值改从 `WatermarkConfig` 获取；120 字上限统一为 `MAX_WATERMARK_TEXT_LENGTH`。
- 图像处理集中到单入口 `WatermarkedJpegWriter.write`，相机捕获/媒体发布由 `PhotoSaver` 管理。

### Spec

- 修复后台环境任务：暂停或销毁界面时取消时钟、定位与天气任务，非前台不启动刷新。
- 修复旧系统授权期间的快门快照：点击时创建 `CaptureRequest`，授权后等待界面恢复，再使用原快照拍摄。
- 修复行距差异：烧录按字体真实度量补齐到共享行高，预览显式使用相同字体与零字距。
- 修复取消时临时文件归属：在同一个 IO 上下文中创建文件并进入清理范围；CameraX 晚到回调也会清理取消的原片。
- 复查未发现阻塞交付的源码问题；MediaStore 成功前失败会删除未完成记录，临时 JPEG 与 Bitmap 也有清理。

## 设备验收（待人工）

`adb devices -l` 无连接设备，因此没有运行安装、模拟器界面或 connectedAndroidTest。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

1. 打开水印相机，允许相机，确认 PreviewView 显示虚拟相机/真实取景，左下角有水印。拒绝相机时确认重试和系统设置入口。
2. 拒绝定位也能拍照；手动输入地点始终可用。启用定位后确认本机 Play Services / Geocoder，断网确认天气占位且可以保存。
3. 拍照后在系统相册确认 `Pictures/WatermarkCam/` 成片、水印方向、中文与底部留白；“查看照片”能打开成片。
4. 设置固定时间和地点：先不填写坐标，确认 EXIF 不含原始 GPS；再填写手动坐标，确认图片与 EXIF 时间/GPS 均为手动值。
5. 关闭覆盖应恢复自动值；字段开关只影响水印显示。重启应用，确认设置持久化。
6. 在微信相册中选择成片发送；由妈妈确认字号、文字和拍照按钮是否易用。

## 人工待定项

无阻塞产品问题。设备确认后可决定是否调整默认自定义文字“今日打卡”、水印字号，以及妈妈常用地点的默认输入体验。
