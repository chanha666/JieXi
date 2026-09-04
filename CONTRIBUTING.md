# 参与贡献

本仓库同时维护 Windows 与 Android。提交代码前至少运行：

```powershell
.\gradlew.bat --no-daemon :sharedCore:test :desktopApp:test :app:testDebugUnitTest :app:lintDebug
```

涉及 Windows 打包时再运行 `:desktopApp:packagePortable`；涉及 Android 安装、服务、数据库或资源时再运行 `:app:assembleDebug` 并在模拟器或真机复现。

平台接口修改必须附带不含真实凭据的单元测试或 MockWebServer 合约测试。下载、暂停、恢复、删除、更新或数据库迁移修改必须覆盖失败路径，不能只验证成功状态。

不得提交 Cookie、Token、浏览器配置、签名私钥、构建证书、私人分享链接或原始诊断日志。大型媒体二进制只能进入 `tools/media-engine` 的既定目录，并同步许可证与打包校验列表。

Issue 应包含应用版本、Windows/Android 版本、平台、问题阶段、错误信息和脱敏诊断包。不要只写“解析失败”。
