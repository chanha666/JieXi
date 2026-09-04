# 更新记录

## 4.0.0

- 恢复 Android 原生应用，与 Windows 端统一为“解析”品牌、4.0 版本和同一 GitHub 更新通道。
- Windows 新增本机公开视频下载页、批量媒体队列、yt-dlp/FFmpeg/Deno 内置核心和组件自检。
- Windows 下载页合并网盘与媒体任务，新增图片局部修复、MP3 提取、视频截图和媒体信息导出。
- Windows 运行数据迁移到 `%LOCALAPPDATA%\JieXi\Data`；安装器仅允许 3.1.0 及以上版本直接升级，并只迁移 `state-v3.bin`、`state-v3.bin.bak`、`media-tasks-v1.json` 三个白名单文件，不覆盖已有数据。
- Windows 3.0.x 及更早版本会被安全阻止直接升级，需备份旧数据、卸载旧版后再安装 4.0.0；异常或冲突时安装器保留旧版和旧数据。
- Android 修复媒体下载初始化闪退、进度不动、暂停/恢复、后台前台服务和 MediaStore 保存。
- Android 增加公开直链快速下载、抖音/X 快速解析、哔哩哔哩官方匿名接口回退、图片局部修复、音频提取和视频截图。
- 两端统一作者图标、暖白微橘视觉、QQ 邮箱反馈、GitHub Issues 和赞赏入口。
- Android 普通公开 APK 改用新的 RSA 4096 位发行证书；API 24–32 旧 debug 测试版需卸载一次，API 33+ 的保数据迁移使用单独迁移 APK。
- 发布流程新增 Windows 媒体组件逐文件校验、双端 CI 和一键正式打包脚本。
- 在线更新清单同时发布 Windows 安装包、绿色版与 Android APK，并使用作者 RSA 私钥签名。
- 发布门禁覆盖 Windows 81 项测试（1 项凭据测试跳过）、Android 78 项、共享核心 6 项和安装迁移助手 12 项；CI 会独立执行迁移助手验证，CodeQL 分别分析 Java/Kotlin 与 C#。
- 正式 Release 固定包含 Windows 安装版、Windows 绿色版、Android APK、`SHA256SUMS.txt`、`BUILD-INFO.txt` 五个附件。Android APK 使用作者 RSA 4096 位发行证书签名；Windows 安装包仍未做 Authenticode 签名。

## 3.1.0

- 工程正式调整为纯 Windows 桌面项目，移除 Android 模块和 Android 构建要求。
- 修复 GitHub Actions 分支和平台，新增 Windows 测试、便携版构建、哈希与产物上传。
- 新增单实例保护、系统托盘、任务完成/失败通知和下载期间防休眠。
- 新增系统代理、直连、HTTP 与 SOCKS5 代理设置，API 与下载统一生效。
- 新增一键导出脱敏诊断包，自动清除认证字段和链接查询参数。
- 下载重试重新解析直链，避免继续使用已经过期的临时地址。
- 统一工程名、版本号、Windows-only 文档与安全报告流程。
