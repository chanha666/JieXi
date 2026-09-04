# 解析 4.0.0 快速使用

## Windows

安装版：双击 `JieXi-4.0.0-Windows-Setup.exe`，安装后从桌面打开“解析”。

从 **3.1.0 及以上版本**可直接升级：安装器只迁移 `state-v3.bin`、`state-v3.bin.bak`、`media-tasks-v1.json` 到 `%LOCALAPPDATA%\JieXi\Data`，不复制其他文件，也不覆盖已有新数据。**3.0.x 及更早版本会被安全阻止直接升级**；先备份 `%LOCALAPPDATA%\解析`，卸载旧版，再安装 4.0.0。

绿色版：完整解压 `JieXi-4.0.0-Windows-Portable.zip`，双击“解析.exe”。不能只复制 EXE，旁边的 `app`、`runtime` 和媒体核心也必须保留。

1. 网盘分享粘贴到“首页”；需要账号时，在“网盘”页完成软件内登录。
2. 公开视频粘贴到“视频下载”，选择画质后加入队列。
3. 所有 Windows 任务在“下载”页管理。
4. 默认保存到 `D:\解析下载`，可在“设置”修改。

关闭主窗口后，下载任务可继续留在托盘；右键托盘图标选择“退出”才会完全结束。

## Android

安装 `JieXi-4.0.0-Android.apk` 后：

1. 主界面用于网盘分享和账号管理。
2. 点击“视频工具”处理公开视频链接、图片局部修复、音频提取和视频截图。
3. 也可以在浏览器或其他应用点“分享”，选择“解析”。
4. 公开媒体成品默认保存到系统 `Download/解析`。

正式 Release 应完整包含五个附件：Windows 安装版、Windows 绿色版、Android APK、`SHA256SUMS.txt`、`BUILD-INFO.txt`。请选择对应系统的软件包，并使用 `SHA256SUMS.txt` 核对。Windows 安装包当前未做 Authenticode 签名，Android APK 已使用作者固定发行证书签名。

发生故障时，在设置中导出脱敏日志，然后到 [GitHub Issues](https://github.com/chanha666/JieXi/issues/new) 反馈；不要发送原始 Cookie、Token、账号密码或私人链接。
