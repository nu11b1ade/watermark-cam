# 水印相机（watermark-cam）

一个**无广告**的 Android 水印相机，拍照时把时间、地点等信息直接写进照片，适合日常打卡拍照后发微信使用。

## 功能

- 全屏相机预览，拍照后水印烧进照片并保存到相册
- 水印内容：时间（日期、星期）、地点、经纬度、天气、自定义文字，每项可单独开关
  - 默认不显示经纬度和自定义文字
- 纯文字水印（带淡阴影），位于左下角，大小可选：小 / 中 / 大 / 特大
- 可手动设置时间和地点；输入地点后自动查出对应经纬度，天气也按该地点查询；支持「用当前位置」一键保存
- 定位使用 Android 系统定位，不依赖谷歌服务，国行手机可用；地址解析使用 OpenStreetMap
- 历史照片重新加水印：拍照时在 App 私有目录保留一份原图（默认保留 7 天，可改为 30 天 / 90 天 / 永久），可修改时间、地点、天气、水印大小后另存为新照片，原照片不动
- 应用内检查更新：打开 App 自动检查（每天最多一次），设置里可手动检查；下载优先 GitHub Releases，失败自动切换 Gitee 镜像，并校验 SHA-256
- 无广告、无统计、无多余权限

系统要求：Android 8.0（API 26）及以上。

## 安装

到 [Releases](https://github.com/nu11b1ade/watermark-cam/releases/latest) 下载最新的 `watermark-cam-x.y.z.apk`，在手机上打开安装（需允许“安装未知来源应用”）。

之后有新版本时，App 内会提示更新，点「更新」即可覆盖升级，设置会保留。

## 从源码构建

需要 JDK 21 和 Android SDK（在 `local.properties` 中设置 `sdk.dir`，或设置 `ANDROID_HOME`）。

```bash
# 调试版
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest
```

### 正式版签名

正式版签名所需的密钥**不在本仓库中**，构建时从以下任一来源读取：

- 仓库外的 `~/keys/watermark-cam-keystore.properties`（包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`）；或
- 环境变量 `WMCAM_KEYSTORE`、`WMCAM_STORE_PASSWORD`、`WMCAM_KEY_ALIAS`、`WMCAM_KEY_PASSWORD`。

配置好后执行：

```bash
./gradlew assembleRelease
```

发版可使用 `scripts/release.sh <versionCode> <versionName> [notes-file]`，会生成 `dist/` 下的 APK 和 `latest.json`（`dist/` 不提交到仓库）。

> 请勿把 `*.jks`、`*.keystore`、`keystore.properties`、`local.properties` 提交到仓库，`.gitignore` 已默认忽略。
