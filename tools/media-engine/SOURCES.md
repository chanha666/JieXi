# Windows 媒体核心来源锁定

本目录是“解析”4.0 Windows 包内置的媒体核心。可执行文件和 DLL 使用 Git LFS 保存；`LOCK.sha256` 锁定仓库中的原始载荷，打包时还会重新生成并验证 `MANIFEST.sha256`。

| 组件 | 固定版本 | 上游来源 | 许可 |
|---|---|---|---|
| yt-dlp | 2026.08.19 | `https://github.com/yt-dlp/yt-dlp/releases/tag/2026.08.19`，资产 `yt-dlp.exe` | Unlicense；第三方许可见 `licenses/yt-dlp-THIRD_PARTY_LICENSES.txt` |
| Deno | 2.9.6 x86_64-pc-windows-msvc | `https://github.com/denoland/deno/releases/tag/v2.9.6` | MIT |
| FFmpeg | `N-126337-g818cecc6e1-20260830`，win64 GPL shared | 构建项目 `https://github.com/BtbN/FFmpeg-Builds`；FFmpeg 源码提交 `https://github.com/FFmpeg/FFmpeg/commit/818cecc6e1` | GPL-3.0-or-later；构建启用 `--enable-gpl --enable-version3` |
| 视频号公开分享提取器 | yt-dlp PR 17390，提交 `d841cddceacbc0bd770c03bbffc94aed7692a08c` 的本地插件适配 | `https://github.com/yt-dlp/yt-dlp/pull/17390` | Unlicense |

## 发布复核

```powershell
git lfs pull
.\gradlew.bat :desktopApp:verifyMediaEngineSource
```

`yt-dlp.exe` 的固定 SHA-256 为 `66674953fe251b89f4d08c5f0e35e0728679bd67ab3d7d05c0562af101dd3e7a`，与上游 2026.08.19 Release 的官方校验值一致。FFmpeg 的完整配置可以用 `ffmpeg.exe -buildconf` 查看；对应源码可从上表固定提交获取。所有其他文件以 `LOCK.sha256` 为唯一二进制基线。
