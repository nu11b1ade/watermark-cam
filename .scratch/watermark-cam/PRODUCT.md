# 水印相机：首个纵向切片

## 产品与用户

- 给妈妈日常个人打卡拍照使用；中文界面，无广告。
- 独立相机应用，不替换系统相机。显示名 **水印相机**，包名 `com.nu11b1ade.watermarkcam`。
- 流程：打开 → 带水印的实时取景 → 拍照 → 把水印烧录进 JPEG → 保存到系统相册 → 用户自行在微信中选择照片发送。
- 交付 debug APK，不发布 Play Store。本切片不内置微信 SDK 或自动分享。

## 水印与设置

- 默认显示时间（`yyyy-MM-dd HH:mm:ss`）、地点/地址、纬度/经度、天气（摄氏温度 + 简短天气）、自定义文字（默认“今日打卡”）。
- 预览与成片均为左下角纵向排列、白字、深色半透明圆角背景。预览使用 Compose 叠层；成片使用 Bitmap/Canvas 实际烧录，文字来源与相对布局一致。
- 设置可分别开关各字段、编辑自定义文字、开启/关闭覆盖时间与覆盖地点，并编辑覆盖值。设置保存在本机，重启保留。
- 覆盖时间输入格式为 `yyyy-MM-dd HH:mm:ss`，按设备时区解释，启用后使用固定时间。
- 覆盖地点必须有地点文字，可选成对填写纬度/经度（纬度 -90…90，经度 -180…180）。自定义文字与手动地点最多 120 字；多余空白合并。

### 重要：覆盖值同时用于图片和 EXIF

**开启覆盖时间或覆盖地点时，烧录水印和 EXIF 必须使用同一份有效值，不能出现水印是手动值、EXIF 仍是真实值的情况。**

- 覆盖时间用于图片时间、EXIF DateTime/DateTimeOriginal/DateTimeDigitized、时区偏移以及存在坐标时的 GPS UTC 时间。
- 覆盖地点用于图片地址与 EXIF UserComment 中的地点；UserComment 是 ASCII-safe JSON，中文以 Unicode 转义保存，解码后即原地点。
- 覆盖坐标用于图片经纬度和 EXIF GPS。**开启覆盖地点但未填坐标时，删除真实坐标，不写 GPS，天气显示占位**，不从真实定位补回。
- 隐藏水印字段仅影响图片显示，不改变有效值或 EXIF。无定位时不写 GPS。
- 天气只匹配有效坐标；手动坐标存在时按手动坐标查天气，不沿用真实位置天气。
- 快门按下时冻结配置、时间、地点与天气，后续异步处理不改变这张照片的内容。

## 定位、天气与降级

- 定位使用 Fused Location，在线时用 Android Geocoder 逆地理编码；仅前台按需请求，不做后台跟踪。
- Play Services 不可用、模拟器定位异常、拒绝定位或逆地理编码失败都不阻塞拍照。显示“位置获取中”/“位置暂不可用”等占位；手动地点始终可用。
- 天气使用无需 API key 的 Open-Meteo（温度 + weather code），只发送有效经纬度；相近坐标缓存 10 分钟。超时、离线或无坐标时显示“天气--”。
- 相机权限在首屏申请。定位权限由“启用定位”按钮请求，粗略定位也可用，拒绝不影响拍照。

## Android 实现约束

- 使用 CameraX **`androidx.camera.view.PreviewView` + Compose `AndroidView`**，兼容模拟器虚拟相机；不是仅用 Canvas 模拟取景。
- 成片按相机 JPEG 方向转正后烧录水印；输出 JPEG。首片优先后置相机、竖向 3:4 取景。
- Android 10+ 通过 MediaStore insert + pending 写入，目录 `Pictures/WatermarkCam/`，成功后发布到相册；失败清理未完成记录和临时文件。
- 权限：`CAMERA`、`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`INTERNET`。API 37 不申请宽泛存储权限。
- minSdk 26 保持不变。仅 Android 8–9 在首次保存时申请 `WRITE_EXTERNAL_STORAGE`（manifest 限定 `maxSdkVersion=28`），同样保存到公开 Pictures/WatermarkCam；API 29+ 完全不申请存储权限。
- 保持 AGP 9.4.1、Gradle wrapper、AGP 内置 Kotlin、Compose plugin 2.4.21、Compose BOM 2026.09.00、CameraX 1.6.2、compile/target 37、min 26、JDK 21。添加 EXIF 读写依赖即可，不应用 `org.jetbrains.kotlin.android`。

## 测试接口与验收

非交互执行，不等待访谈确认；用户要求的纯逻辑测试接口采用：

1. `composeWatermark(config, observed)`：验证中文字段顺序、时间/坐标格式、字段隐藏、有效覆盖值与天气匹配；不验证 Android 像素或系统权限。
2. `parseOverrides(input)`：验证设置输入会拒绝无效日期、不完整/越界坐标；不验证 Compose 文本框。
3. `photoMetadata(content)`：验证与有效值一致的 EXIF 日期、时区、GPS 时间与地点编码；相册交互另由设备验收覆盖。
4. `WatermarkedJpegWriter.write(source, destination, watermark)`：Robolectric/native graphics 验证真实 JPEG 像素、方向与 EXIF 回读；不替代设备上的 CameraX 与 MediaStore 验收。

验收（界面和系统交互已实现，下列运行时项目待连接设备确认）：

- [ ] 中文首屏及相机权限申请；拒绝后有重试/系统设置入口。
- [ ] PreviewView 真实取景 + 水印叠层 + 快门。
- [ ] 拍照 → 烧录 → EXIF → MediaStore，相册可见且有成功/失败提示。
- [ ] 设置字段开关、自定义文字、时间/地点/坐标覆盖可用且重启保留。
- [x] 纯逻辑测试覆盖格式化与有效覆盖值选择，先红后绿。
- [x] `./gradlew assembleDebug testDebugUnitTest` 通过。

设备验收优先模拟器。当前环境如无连接设备，不运行 connectedAndroidTest；在交付记录中明确待人工确认的真实取景、成片和微信相册选择体验。

首片的构建、审查与设备操作清单见 [VERIFICATION.md](VERIFICATION.md)。
