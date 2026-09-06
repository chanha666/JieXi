# 解析 4.1.1 · Windows 下载完成误报修复

## 修复内容

- 已复现旧版问题：中文目录下载成功后，媒体核心输出路径的编码与 UTF-8 读取方式不一致，导致路径乱码并误报“工作目录之外的文件”。
- Windows 下载显式使用 UTF-8，忽略外部 yt-dlp 配置，避免用户其他工具的输出路径设置干扰任务目录。
- 成品确认支持任务内相对路径；返回路径不可用时，只接受本任务目录中唯一的有效类型成品。不会搜索其他下载目录或采用“最新的任意文件”。
- 排除空文件、分片、临时文件、字幕和封面；拒绝目录跳转。无法确认时保留本地文件，不覆盖、不删除成品。
- 对旧版此类路径失败任务，点击“重试”会先使用 FFprobe 检查本任务已存在的成品；检查通过直接完成，无需重新解析或下载。
- Android 同步版本号与发行包，保留 4.1 多语言功能；本次路径修复针对 Windows，不声称修复了未复现的 Android 问题。

## 怎么使用

退出旧版后安装 Windows 更新包，打开“下载”，找到原失败任务，点击一次“重试”。保留原任务与工作目录，不要先删除任务。

Windows 安装包：JieXi-4.1.1-Windows-Setup.exe；绿色版：JieXi-4.1.1-Windows-Portable.zip；Android：JieXi-4.1.1-Android.apk。

## 验证范围

真实随包 yt-dlp 的本地 HTTP 下载测试先复现中文路径失败，修复后通过；覆盖中文、空格、百分号及标题符号。控制器恢复测试使用真实 FFmpeg/FFprobe，确认完成状态、文件字节不变，并断言源站请求次数为零。另有目录越界、多个候选成品、空文件、旧文件及辅助文件排除测试。

这不是所有第三方平台下载的全量复测。网盘账号要求、入口和原有平台限制不变，见 README。

## English

Fixes Windows downloads incorrectly marked as failed after completion due to mismatched Chinese-path encoding. Output is explicitly UTF-8. On retry, an old path-failure task can recover its unique, verified local media file without contacting the source. Recovery remains confined to that task's workspace, rejects ambiguous/temporary files and does not overwrite user files. Android is version-aligned; this fix targets Windows.
