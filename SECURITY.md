# 安全策略

## 支持版本

只为最新正式版提供安全修复。请先在软件“设置 → 检查更新”升级，再复现问题。

## 报告安全问题

不要在公开 Issue 中提交 Cookie、Token、完整私人分享链接、私人文件名、签名材料或诊断原始日志。请发送邮件至 `3316109338@qq.com`，包含应用版本、Windows/Android 版本、复现步骤和软件导出的脱敏诊断包。

项目不会索取网盘密码。Windows 登录凭据使用当前用户 DPAPI 加密；Android 使用 Android Keystore。公开媒体解析器不得向第三方接口发送网盘凭据。

## 发布验证

- 正式 Release 必须同时提供 Windows 安装版、绿色版、Android APK、`SHA256SUMS.txt` 和 `BUILD-INFO.txt`。
- 两端只接受受信 RSA 私钥签名的 HTTPS 更新清单，并在下载后核对清单中的 SHA-256。
- Android 普通公开 APK 必须使用项目固定的新 RSA 4096 位发行证书；覆盖升级由系统再次核对签名。API 24–32 的旧 debug 测试版不能直接覆盖，API 33+ 的保数据迁移只能使用单独迁移 APK，迁移包不得混入普通公开附件。
- Windows 发布包中的 yt-dlp、FFmpeg、Deno、插件和许可证必须通过 `MANIFEST.sha256` 逐文件校验。
- 当前 Windows 安装包的 Authenticode 状态为 `NotSigned`；发布说明必须明确标记，不能把 RSA 更新清单签名描述成 Windows 代码签名。

## 能力边界

本项目只处理公开资源或用户有权访问的内容，不接受绕过 DRM、会员权益、付费墙或平台访问控制的功能提交。
