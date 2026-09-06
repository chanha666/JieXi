# 解析 4.1.2 · 在线更新与 Windows 路径兼容修复

- 修复安装 4.1.1 后仍被识别为 4.1.0、重复提示更新的问题。Windows/Android 构建、安装包和程序内版本读取同一份 version.properties；增加一致性测试和打包断言。
- 修复更新模块未使用软件代理配置的问题；“跟随系统”读取 Windows 当前用户代理。使用 HTTP/1.1 避免部分本地代理的 HTTP/2 响应体卡住，保留证书及主机名校验。
- 更新下载显示已下载大小与百分比，支持断点续传、有限重试与校验后缓存复用。服务器忽略 Range 时从头写临时文件，不错误拼接。SHA-256 流式计算，不一次性把整个安装包读入内存。
- 下载完成后的按钮改为“退出并安装”。有进行中的下载或媒体处理时拒绝退出；安装前再次检查文件哈希。
- 修复 GitHub Windows 测试机及用户临时目录的 8.3 短路径别名误判；使用真实路径边界比较，仍拒绝越界。备用成品查找只在当前任务目录一层内进行。
- GitHub CI 在失败时也上传 Windows 测试报告；保留所有原有测试，不通过删除检查来隐藏红叉。
- Android 同步发行版本号；本轮更新网络与短路径修复针对 Windows，保留已有功能。

## 安装

旧版更新模块本身有缺陷时，请手动安装一次 JieXi-4.1.2-Windows-Setup.exe。之后在“我的 → 设置与更新”检查更新。绿色版请解压完整目录，不只替换 EXE。

保留原下载任务；4.1.1 的本地成品恢复功能仍保留。不会为消除报错而清空你的任务、Cookie 或视频。

## 验证范围

自动测试覆盖统一版本、代理选择、HTTPS 更新下载、Range 续传/忽略、哈希错误保护、缓存复用、安装前活动任务拦截及短路径别名。另行启用真实联网测试，通过更新模块下载 GitHub 已签名发布中的 Windows 安装包并校验哈希。

这不等于所有网盘和视频平台的全量下载复测。Windows 安装器仍保留升级数据安全检查；无商业 Authenticode 签名。

## English

Fixes repeated update prompts by using one build-version source. Windows updates now honor proxy settings, use reliable HTTP/1.1 streaming, show progress, resume partial downloads and verify hashes without loading the entire installer into memory. “Exit and install” checks active work and revalidates the package. Windows short-path aliases are accepted without removing real-path containment checks. Failed CI runs retain diagnostic test reports.
