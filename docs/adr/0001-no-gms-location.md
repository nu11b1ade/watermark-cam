# 无 GMS 自动定位与备用逆地理编码

地址查询顺序自 v0.2.1 起由 [ADR 0002](0002-amap-consent-and-coordinates.md) 更新；本决策的无 GMS 自动定位机制继续适用。

目标设备 Redmi K70 Ultra 使用中国版 HyperOS（Android 14），没有 Google Play Services，系统 Geocoder 也可能没有可用结果。因此自动定位采用 Android LocationManager 的已启用 GPS / 网络提供者：仅有粗略位置权限时只用网络，定位请求共享 10 秒期限，近期结果（不超过 2 分钟）优先精度，未取得结果时采用不超过 10 分钟的最新缓存坐标，不依赖 GMS。

逆地理编码先使用简体中文系统 Geocoder，空结果或失败时再查询 Nominatim；Nominatim 请求使用应用 User-Agent、5 秒连接 / 读取超时、进程内统一串行限速（请求间至少 1.1 秒）及四位小数坐标缓存。地址从 `display_name` 由大到小去重组合，以保留上海等地区在 `address` 对象中缺失的区名；失败继续使用现有可手动设置提示，覆盖地点与覆盖时间仍决定水印和 EXIF 的有效值。
