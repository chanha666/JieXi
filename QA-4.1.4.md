# 4.1.4 验证范围

日期：2026-10-11。

## 核心与代码

- Windows yt-dlp.exe：`e7cafc44db8aa433da8dd78303b9157f2c1dd509c4cbfcd8418081b7e1d26a7a`。
- Android yt-dlp zipimport：`f40b6a9e4c5786360953911cab5d2f87507626b8fb7bfee3f8f1c2e5cf341fae`。
- 两者均与官方 nightly 2026.10.10.233141 的 SHA2-256SUMS 一致。
- 新增日期版本比较测试，覆盖旧稳定版、旧 nightly、未知版本、相同版本和较新在线更新版本。
- 新增双端机器人验证错误分类测试。

## 真实网络和设备

- DesktopPublicMediaLiveTest：2 项通过。抖音样本完整下载并检查包含 video/audio；B站样本取得公开下载地址。
- Windows 首次抖音网络下载在 90 秒截止时未完成，记录为失败；独立复测完整完成。首轮旧错误文案断言不再匹配新文案，已更新断言并增加单独分类测试。
- Android API 35 / emulator-5556 / 隔离包 com.yunx.app.qa：MEDIA_SMOKE_OK。本地 HTTP 7482 字节一致，9 次回调；抖音 148915518 字节，56 次回调。
- YouTube：旧核心和新核心均返回“Sign in to confirm you're not a bot”，包含显式使用电脑现有系统代理的复测。没有解除机器人验证，没有进行自动登录或导出浏览器 Cookie。
- 本轮未收到用户实际失败链接，不能归因为所有下载故障同一个原因。

## 发布门禁

发布脚本必须依次通过 sharedCore:test、app:testDebugUnitTest、app:lintDebug、app:verifyAndroidRelease、desktopApp:test、desktopApp:packageInstaller 和安装器 MSI 审核。在线网络测试需显式设置 JIEXI_PUBLIC_MEDIA_TEST=1；默认发布门禁会跳过这些测试，真实网络结果单独以上述记录为准。

## 未验证范围

实体手机、Android UI/前台服务完整路径、真实网盘账号、用户失败链接、所有平台/画质、完整 4K/8K 和软件内更新下载安装全流程，均不在本次通过清单中。不能把本记录解释为“所有问题已修复”。
