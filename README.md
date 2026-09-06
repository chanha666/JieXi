# 解析（JieXi）— 网盘分享与公开视频下载工具

**简体中文** | [English](README.en.md)

“解析”4.1 是一个同时提供 Windows 与 Android 安装包的本地下载工具。它把网盘分享解析、公开视频下载、批量任务、断点续传、历史收藏、诊断反馈和常用媒体处理放进同一品牌应用中。

## 开始前：网盘账号登录在哪里？

Windows 和 Android 都从 **「我的 → 网盘与账号」** 进入登录。Windows 的个人网盘文件管理位于「我的 → 我的网盘文件」；Android 在账号卡片中打开网盘。

| 操作 | 登录要求 |
| --- | --- |
| 软件启动、媒体小工具、历史收藏 | 不需要登录 |
| 浏览自己的网盘、空间容量、移动/重命名/删除/分享、转存分享文件 | 必须登录对应网盘账号 |
| 迅雷分享解析和下载 | 必须登录迅雷账号 |
| 夸克、百度、123 分享下载 | 当前适配接口需要对应账号；匿名查看目录不代表能下载 |
| UC、139 分享下载 | 会尝试平台开放的匿名通道；失效、风控或权限不足时需要登录 |
| 公开视频下载 | 优先匿名；实际画质与可用性取决于原站，不能保证每个链接免登录 |

Windows：夸克、UC、百度、139 打开软件内官方登录窗口；123 支持账号密码，迅雷支持账号密码和短信验证，仍保留手动凭据及浏览器导入入口。Android 使用各平台已有的应用内登录流程。认证备份在 Windows「我的 → 认证备份」、Android「我的 → 下载与应用设置 → 导出/导入网盘认证」。备份必须设置密码，账号风控可能要求重新验证。

## 下载

正式版本从本仓库的 [Releases](https://github.com/chanha666/JieXi/releases) 下载：

- Windows 安装版：`JieXi-4.1.1-Windows-Setup.exe`
- Windows 绿色版：`JieXi-4.1.1-Windows-Portable.zip`
- Android 安装包：`JieXi-4.1.1-Android.apk`
- 文件校验：`SHA256SUMS.txt`
- 构建信息：`BUILD-INFO.txt`

Windows 绿色版解压后双击“解析.exe”，不需要另装 Java、Python、yt-dlp 或 FFmpeg。Android 公开 APK 使用新的 RSA 4096 位发行证书：首次安装以及今后同一发行证书签署的版本可以正常覆盖升级。早期 debug 测试版不属于同一公开发行链；Android 7–12（API 24–32）需要先卸载旧测试版再安装。Android 13 及以上（API 33+）如需保留旧测试版数据，只能使用单独提供的迁移 APK；该迁移包不作为普通公开 Release 附件。

Windows 3.1.0 及以上版本可直接升级到 4.1.1，安装器只迁移三个任务数据文件且不会覆盖已有新数据。3.0.x 及更早版本会被安全阻止；请先备份 `%LOCALAPPDATA%\解析`，在 Windows 设置中卸载旧版，再安装 4.1.1。新版持久数据保存在 `%LOCALAPPDATA%\JieXi\Data`，正常卸载不会删除它。

## 能做什么

### 网盘分享

- 夸克网盘、UC 网盘、迅雷云盘、百度网盘、123 云盘和中国移动云盘（139）
- 分享链接识别、提取码、目录浏览、批量选择和下载
- 软件内登录、凭据本机加密保存、失效状态提示和重新登录
- 分片并发、断点续传、失败重试、暂停、继续、取消和任务删除

### 公开视频

- 可识别 YouTube、哔哩哔哩、抖音、X、TikTok、小红书、微博、AcFun、视频号及 yt-dlp 支持的公开页面；能否下载仍取决于原站当时实际开放的格式与风控
- MP4/HLS 等媒体直链、批量粘贴、最高开放画质、4K/8K 格式识别、仅音频和字幕参数
- 抖音与 X 的公开页面快速解析，失败后回退本地 yt-dlp 核心
- Windows 内置 yt-dlp、FFmpeg、ffprobe 与 Deno；Android 内置 youtube-dl-android、FFmpeg 与 Aria2

“最高画质”只表示链接在匿名状态或当前账号权限下实际开放的最高格式。软件不会把低清视频放大成真正的 4K/8K，也不绕过 DRM、会员权益、付费墙或平台访问控制。部分网站会要求登录、验证码或 Cookie；这由原站决定，软件不会伪造权限。

4.0 发布时的历史样本记录（不是本版全平台验收结论）：抖音已在 Android 完成 148,915,518 字节成品下载，Windows 完成解析与分段读取；X 已完成 H.264 720p 成品下载；哔哩哔哩已通过官方匿名接口完成 720p 成品下载，并在 Android 模拟器完成解析。哔哩哔哩压力或频繁请求仍可能返回 403。YouTube 匿名样本受到站点挑战，不能标记为匿名下载通过。其余列出的站点是适配目标，不代表全部已经逐站实测通过。

### 本地媒体工具

- 图片局部修复：框选图片中的水印或遮挡区域，离线生成新文件，不覆盖原图
- 从本地视频提取 MP3
- 按时间点截取视频画面
- Windows 可导出分辨率、编码、码率和时长等媒体信息

## 两个平台的使用方法

### 切换语言

Windows 和 Android 都从 **「我的 → 外观 → 语言 / Language」** 选择简体中文、繁體中文、English 或跟随系统。切换立即刷新界面并在本机保存，不会重建下载队列。英文界面的路径为 **My → Appearance → 语言 / Language**。

本版共用离线翻译词库，覆盖主导航、常用操作、账号页面、设置和确认提示。文件名、视频标题、链接、账号内容不翻译；官方登录网页、第三方返回的错误和部分详细诊断信息保留原文。GitHub 页面顶部的「简体中文 / English」单独切换项目介绍，不改变软件设置。

Windows：在「解析」首页粘贴网盘或视频链接，自动分流；批量视频和格式选项也可从「我的 → 视频下载选项」进入。任务统一显示在「下载」页，支持搜索和分类。默认保存到 `D:\解析下载`，D 盘不可用时退回当前用户下载目录，也可以在设置中修改。

Android：底部只有「解析 / 下载 / 我的」；首页统一处理网盘与视频链接，工具、账号和偏好归入「我的」；系统分享菜单也可以把文字链接发送到“解析”。公开视频成品写入系统 `Download/解析`，网盘文件写入系统下载目录或用户在设置中选择的目录。

更详细的操作说明见 [Windows 使用说明](README-Desktop.md) 和 [Android 使用说明](README-Android.md)。

4.1 改造、功能对应关系及实测边界见 [双端功能与验收记录](FEATURES-4.1.md)。

## 更新、安全与反馈

- Windows 与 Android 只读取作者发布的 HTTPS 更新地址和 RSA 签名清单。
- Windows 与 Android 下载更新后都会核对清单中的 SHA-256；Android 还会预检包名、版本号和发行证书，并由系统安装器再次核对签名。
- 登录凭据只保存在本机：Windows 使用 DPAPI，Android 使用 Android Keystore；导出的诊断日志会脱敏。
- 每个 Release 同时提供 `SHA256SUMS.txt`，可以独立核对下载文件。
- Windows 安装包目前未使用商业 Authenticode 证书，首次下载可能出现 SmartScreen 提示；这与应用内更新清单签名是两件不同的事。
- 问题反馈：[GitHub Issues](https://github.com/chanha666/JieXi/issues/new) 或 `3316109338@qq.com`。

## 从源码构建

需要 JDK 17；构建 Android 还需要 Android SDK；构建 Windows 登录组件需要 .NET 8 SDK，安装版还需要 WiX 3。项目自带 Gradle Wrapper。Windows 媒体二进制使用 Git LFS 保存，首次克隆后请先执行 `git lfs pull`。

```powershell
# Windows 编译、测试与绿色版
.\gradlew.bat :sharedCore:test :desktopApp:test :desktopApp:packagePortable

# Android 调试验收
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# 本机正式发布（需要 D:\CodexSecrets\解析 中的作者私钥）
.\tools\BuildRelease.ps1
```

Windows 媒体核心位于 `tools/media-engine`，打包时会生成并复核逐文件 SHA-256 清单。正式签名配置放在 `D:\CodexSecrets\解析\release.properties`，不属于源码且不得提交。

## 来源与许可证

本项目在 [CYQawa/YunX](https://github.com/CYQawa/YunX) 基础上扩展，并融合了独立公开视频下载与本地媒体工具。项目依照 [GNU AGPL-3.0](LICENSE) 发布；分发修改版本时必须保留许可证和上游归属，并按 AGPL-3.0 提供对应源代码。内置 yt-dlp、FFmpeg、Deno 及站点插件保留各自许可证，随 Windows 包一并分发。
