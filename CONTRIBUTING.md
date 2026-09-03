# 参与贡献

本仓库仅维护 Windows 桌面版。提交代码前运行：

```powershell
.\gradlew.bat --no-daemon :desktopApp:test :desktopApp:packagePortable
```

平台接口修改必须附带不含真实凭据的单元测试或 MockWebServer 合约测试。不得提交 Cookie、Token、浏览器配置、签名私钥、构建证书和真实分享内容。

Issue 应包含软件版本、Windows 版本、平台、错误代码和脱敏诊断包。不要只写“解析失败”。
