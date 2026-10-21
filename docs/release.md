# 发布与应用内更新

## 本地生成发布文件

需要 Android SDK、JDK 21、Python 3、GNU `sed` 与 `sha256sum`。使用仓库的 Gradle wrapper。

先配置 release 签名，使用与已安装版本相同的密钥，才能覆盖安装：

- `~/keys/watermark-cam-keystore.properties`，包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`；或
- 环境变量 `WMCAM_KEYSTORE`、`WMCAM_STORE_PASSWORD`、`WMCAM_KEY_ALIAS`、`WMCAM_KEY_PASSWORD`。

密钥和密码不要放入仓库。release 已开启 R8 minify 与资源压缩；FileProvider 由 Manifest 引用，AAPT 自动生成保留规则，清单使用 `org.json` 显式解析，无需反射保留规则。

```bash
scripts/release.sh <versionCode> <versionName> [notes-file]
# 例如（由发布者手动执行）：
scripts/release.sh 2 0.2.0 release-notes.txt
```

- `versionCode` 必须是比 `app/build.gradle.kts` 当前值大的正整数，且不超过 Android Int 范围。
- `versionName` 使用 `0.2.0`、`0.2.0-rc.1` 或 `0.2.0+build.1` 等格式。
- 更新说明从 UTF-8 文件读取，支持中文、换行和引号；未提供时使用「修复问题，提升稳定性」。
- 脚本用 `sed` 修改版本，运行 `./gradlew testDebugUnitTest assembleRelease`，然后使用 SDK build-tools 中的 `apksigner verify` 验证签名；找不到工具时打印警告。
- 未配置签名时 Gradle 通常生成 `app-release-unsigned.apk`；脚本要求 `app-release.apk`，此时会报错，请先配置签名。
- 失败时保留已修改的版本，重新运行前检查 `app/build.gradle.kts`（同一个 versionCode 不会再次通过递增检查）。

生成的文件位于已忽略的 `dist/`：

```text
dist/watermark-cam-0.2.0.apk
dist/latest.json
```

脚本只构建本地文件并打印操作步骤；所有创建 release、上传、tag、commit 和 push 操作由发布者手动完成。

## 清单与来源约定

应用的更新地址集中在 `com.nu11b1ade.watermarkcam.update.UpdateConfig`。`GITEE_OWNER = "nu11b1ade"` 是待确认的镜像所有者占位值；镜像账号不同时修改它。发布脚本也读取此配置生成 APK 地址。

应用按以下顺序获取 `latest.json`，连接和读取超时分别为 5 秒；网络异常或无效清单时尝试下一来源：

1. GitHub：`https://github.com/nu11b1ade/watermark-cam/releases/latest/download/latest.json`
2. Gitee：`https://gitee.com/nu11b1ade/watermark-cam/releases/download/latest/latest.json`

**Gitee 没有 `/releases/latest` 别名。** 必须保留一个固定 tag 为 **`latest`** 的滚动 release，每次发布都替换它的 `latest.json` 和 APK 附件。它与不可变的版本 release（如 `v0.2.0`）同时存在，不能仅上传版本 release。

清单示例（示例 SHA-256 是 `abc` 的已知向量，真实值由脚本从 APK 计算）：

```json
{
  "versionCode": 2,
  "versionName": "0.2.0",
  "notes": "新增应用内更新\n修复问题，提升稳定性",
  "apkUrls": {
    "github": "https://github.com/nu11b1ade/watermark-cam/releases/download/v0.2.0/watermark-cam-0.2.0.apk",
    "gitee": "https://gitee.com/nu11b1ade/watermark-cam/releases/download/v0.2.0/watermark-cam-0.2.0.apk"
  },
  "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
}
```

- 版本只比较 `versionCode`，不比较 `versionName`。
- `minSupported` 是可选的正整数，可由发布者在 JSON 中添加。低于此值的版本显示「当前版本过旧，建议立即更新」，仍可点「以后再说」。
- `apkUrls.github` 和 `apkUrls.gitee` 可以缺少一个，但必须至少有一个，且只能使用 HTTPS。APK 同样先 GitHub 后 Gitee；下载或 SHA-256 校验失败时切换来源。
- `sha256` 是 APK 的 64 位十六进制 SHA-256，脚本输出小写。两处上传的 APK 必须是同一个文件，不能各自重新构建。
- APK 地址始终指向不可变的 `v<versionName>` release；滚动 Gitee release 提供最新清单的固定入口，并保存相同 APK。

## 手动发布步骤

1. 在 GitHub 创建 tag 为 `v<versionName>` 的 release，上传 `dist/` 中的 APK 与 `latest.json`，将其设为最新正式 release，供 `/releases/latest/download/latest.json` 使用。
2. 在 Gitee 创建同名 `v<versionName>` release，上传完全相同的两个文件。
3. 在 Gitee 的滚动 **`latest`** release 中移除旧附件并上传相同的新 APK 和 `latest.json`，保留 `latest` tag 与固定清单文件名。
4. 手动获取两个清单地址，确认版本、说明、SHA-256 正确；检查清单内的两个 APK 链接可下载。

先上传 APK，再替换滚动清单，避免新清单暂时指向不存在的附件。

## 应用内行为与本地验证

- 启动时静默自动检查：每 24 小时至多一次，成功或失败的尝试完成后才记录时间；自动检查失败不弹提示。
- 设置页底部可手动检查，不受 24 小时节流限制。
- APK 先下载到 `cacheDir/updates/` 的临时文件，校验通过才改为最终文件名；取消或失败时删除临时文件。
- Android 8 及以上未授予安装应用权限时，显示中文说明并进入本应用的「允许来自此来源的应用」设置；返回后重新检查并继续安装同一 APK。
- FileProvider 仅共享 `cacheDir/updates/`；系统安装器通过带临时读取授权的 content URI 打开 APK。

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleRelease
```

Robolectric 截图测试生成 `.scratch/screenshots/v5-update.png` 与 `.scratch/screenshots/v5-update-dialog.png`，无需模拟器。
