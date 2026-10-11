# 解析 4.1.4 — 双端媒体核心与解析回退更新

## 本次修改

- Windows 和 Android 内置 yt-dlp 更新至官方 nightly `2026.10.10.233141`，下载后与官方 `SHA2-256SUMS` 比对。
- Android 的抖音和 B站公开页解析失败后，继续尝试通用核心，不再直接结束解析。
- Windows“更新核心”使用 nightly 通道，避免把较新的 nightly 核心降回旧稳定版。
- Android 升级应用时保留比内置版本更新的核心。
- 双端明确显示“源站触发了机器人验证”，不再把这类错误混同为视频私有或删除。

## 实际验证及限制（2026-10-11）

- Windows：抖音公开样本解析及完整下载通过，149 MB 级别；ffprobe 检查音视频流，进度正常。第一次网络下载超时，随后独立复测完成，不保证所有网络均成功。
- Android API 35 模拟器：真实核心解析本地 HTTP 样本，7482 字节一致、9 次进度回调；抖音公开样本完整下载 148,915,518 字节、56 次回调。
- Windows B站公开样本解析通过；不等于全部画质和全部 B站视频均能下载。
- YouTube 样本 `JXZ_CUfTweo` 在当前网络出口仍返回机器人验证，更新核心没有解除该限制。本版不能承诺 YouTube 匿名下载恢复。
- 尚未收到本次用户失败链接。真实网盘账号、实体手机、Android 前台服务/UI 全流程未完成此次复测。未调整网盘认证策略，也不宣称网盘故障已经修复。

## English

Bundled official yt-dlp nightly 2026.10.10.233141 on Windows and Android. Android now tries the general extractor after Douyin/Bilibili public-page extraction fails. Core updates avoid downgrades; bot challenges receive a distinct message. Public Douyin download checks passed on Windows and an Android API 35 emulator. YouTube still returned an anti-bot challenge on the tested network; authenticated cloud-drive flows and physical devices were not revalidated. This is not a universal download fix.
