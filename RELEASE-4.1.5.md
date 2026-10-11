# 解析 4.1.5 — 下载自适应与失败恢复

Windows 安装版、绿色版与 Android 版同步更新。

## 修复与改进

- 下载前刷新公开视频地址；刷新失败时改为重新解析原链接，不再继续尝试旧直链。
- 手机端抖音兼容多种公开页数据结构，并支持主地址缺失时使用有效备用地址。
- 手机端自动适配服务器：连接探测失败可重试，不接受分段则切换单连接；失败或取消时关闭分段工作线程与连接。
- 拒绝把 HTML/JSON 错误页或播放清单误存为视频；播放清单及需要后处理的任务使用媒体核心。
- 两端不再仅凭 Cookie 字样或网址中的 401 数字误判网盘登录失效。
- 电脑版数字编号抖音链接优先只请求一次公开页，减少无效跳转。

## 测试边界

Android API 35 隔离测试包实际运行前台下载服务，通过首次请求失败重试、正常分段合并、分段被拒绝后回退、拒绝 HTML、永久 HTTP 错误停止重试等检查。成功下载经 MediaStore 保存后逐字节比对。Windows 抖音公开样本完整下载并确认包含音频和视频，B站公开样本解析通过。

本版没有解除 YouTube 的机器人验证，也未验证真实网盘账号、所有视频平台或所有手机。网盘认证失效、会员权限和源站访问限制不会因为升级而自动消失。本次没有用户提供的失败链接，不能宣称所有故障已解决。完整记录见 QA-4.1.5.md。

## 下载哪个

- Windows 日常安装：`JieXi-4.1.5-Windows-Setup.exe`
- Windows 免安装：`JieXi-4.1.5-Windows-Portable.zip`
- Android：`JieXi-4.1.5-Android.apk`

## English

Adaptive Android downloads now retry transient probe failures, fall back to a single connection when ranges are ignored, stop failed segment workers promptly, and reject HTML/JSON/manifest responses as finished media. Signed public media URLs are re-extracted on refresh failure on both platforms. Douyin parsing handles additional response shapes; numeric Douyin links on Windows avoid unnecessary redirects. Authentication classification no longer relies on the mere word Cookie or numeric substrings in URLs.

Android foreground-service tests verified byte-exact MediaStore outputs for controlled retry, segmented and fallback transfers. A Windows public Douyin download and Bilibili analysis were tested. This does not remove YouTube bot challenges or validate every account, platform and physical device.
