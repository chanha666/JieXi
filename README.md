# 解析

“解析”是一款面向 Windows 与 Android 的网盘分享链接解析和下载工具。3.0.2 版提供统一的暖色界面、批量队列、断点续传、预设、历史收藏、平台状态、诊断中心、主题设置、GitHub 反馈与经过签名校验的应用内更新。

## 下载

请从本仓库的 [Releases](https://github.com/chanha666/JieXi/releases) 页面下载：

- Windows 安装版：`解析-3.0.2-安装版.exe`
- Windows 绿色版：`解析-3.0.2-绿色版.zip`
- Android：`解析-3.0.2-Android.apk`
- 完整源码：仓库内容或 Release 中的源码包

每个正式版本都附带 `SHA256SUMS.txt`。软件的在线更新清单使用独立 RSA 私钥签名，客户端内置公钥并在安装前再次校验安装包 SHA-256；私钥和 Android 发布密钥不进入仓库。

## 核心功能

- 解析夸克、UC、迅雷、百度、123、139 等网盘分享链接
- 内置网盘登录流程，并在本机保存所需认证信息
- Range 分片并发、断点续传、失败重试、批量队列和任务删除
- 单任务最高速预设，并可按网络状况调整并发
- 历史记录、收藏、下载预设和平台可用状态
- Windows 与 Android 诊断日志、敏感字段脱敏和问题反馈入口
- 浅色、深色、跟随系统及自定义主题
- Windows 安装版/绿色版与签名 Android APK

## 使用

1. 在“网盘”页登录需要使用的平台。
2. 在“解析”页粘贴分享链接和提取码。
3. 选择文件并开始下载。
4. 在“下载”页暂停、继续、删除或打开任务。

默认优先把 Windows 文件保存到 `D:\解析下载`；如果没有可写的 D 盘，则退回当前用户的下载目录。下载位置可在设置中修改。

## 构建

环境要求：JDK 17、Android SDK，以及构建 Windows 安装包所需的 WiX 3。项目包含 Gradle Wrapper。

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
.\gradlew.bat :desktopApp:test :desktopApp:packagePortable :desktopApp:packageInstaller
```

发布密钥与在线更新地址由 `D:\CodexSecrets\解析\release.properties` 在构建时注入。该文件不属于源码，也不得提交。公开源码默认仍可构建，但正式签名和在线更新需要发布者自行配置密钥。

## 来源与许可证

本项目在 [CYQawa/YunX](https://github.com/CYQawa/YunX) 基础上重新设计并扩展 Windows 桌面端、发布安全、诊断及批量下载功能。原项目及本项目依照 [GNU AGPL-3.0](LICENSE) 发布。分发修改版本时必须保留相应版权和许可证声明，并按 AGPL-3.0 提供对应源代码。

本工具不提供或绕过网盘会员权益、DRM 或平台访问控制。平台接口可能调整；如遇失败，可通过本仓库的 [Issues](https://github.com/chanha666/JieXi/issues) 提交诊断信息。
