# 解析 4.1.3 — 媒体任务修复版

## 修复

- Windows / Android 的 YouTube 解析改为读取真实格式；失败不再用视频编号和预设画质伪装成功。解析最长等待约 90 秒。
- 根据实际返回的分辨率提供画质预设；识别到 4K 不等于已经下载成功。
- Windows 强制输出下载进度，显示获取信息、连接、重试与合并阶段；媒体解析和下载使用当前选定的系统/软件代理，不修改系统设置。
- 双端媒体核心下载连续约 120 秒没有文件数据增长时终止本任务并保留断点；合并阶段另有 15 分钟上限。重复重试日志不会延长等待。
- Android YouTube 使用内置下载器，不再强制使用外部 Aria2 多连接；成品筛选排除字幕、空文件和未合并轨道，多个候选时明确失败，保留文件。
- 修复 Windows 全部重试丢失本地成品恢复信息、切换输入后显示旧解析结果的问题。
- 修复 Android 准备下载期间暂停/取消状态被重置的问题；补充 Windows 子进程清理。

## 当前实测边界

- 2026-09-07，Windows 实际解析用户反馈的视频 `JXZ_CUfTweo`：取得真实标题及 2160p 格式。
- 同一视频使用低流量格式完成 17,167,137 字节下载，ffprobe 确认有视频和音频两个流，下载进度正常。
- 同一视频的 4K 选项实际传入 261,120 字节后主动停止测试；这不是整部 4K 下载验收，也不是速度保证。
- Windows 源站无响应、暂停/取消、已完成文件恢复以及 Android 成品筛选有独立回归测试。
- Android API 35 模拟器核心实测通过：7482 字节样本逐字节一致、9 次回调；用户 YouTube 视频解析出 2160p，低流量画质完整下载并合并为 17,167,085 字节，3908 次回调。未登录，测试使用电脑现有代理。实体手机及 UI/下载服务全流程尚未验收。
- 其他网盘/视频平台不代表已逐个平台完成真实账号和完整下载复测。不得把上述结果宣传为“所有平台都已正常”。

经用户确认发布到 GitHub，并更新签名在线更新清单。上述未测范围仍然保留。

## English

Media reliability release: real YouTube format extraction, visible progress, bounded stalled transfers, proxy consistency on Windows, safer Android output selection and cancellation handling. Windows and Android API 35 emulator core checks passed, including a complete low-bandwidth YouTube download. Physical-device and full UI/service workflow validation remain pending. This is not a claim that every platform or every quality has been fully tested.
