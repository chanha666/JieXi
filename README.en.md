# JieXi — Cloud-share and public-video downloader

[简体中文](README.md) | **English**

JieXi (解析) 4.1 is a local application for **Windows and Android**. It combines cloud-share browsing, public-video downloads, batch queues, resumable transfers, history, bookmarks, diagnostics, and local media tools.

## Before you start: account requirements

On both platforms, open **My (我的) → Cloud drives & accounts (网盘与账号)** to sign in. On Windows, personal files are under **My → My cloud files (我的网盘文件)**. On Android, open a drive from its account card.

| Operation | Sign-in requirement |
| --- | --- |
| Launching JieXi, local media tools, history and bookmarks | No account required |
| Personal files, storage quota, moving, renaming, deleting, sharing, or saving shared files to your drive | Sign in to the relevant cloud provider |
| Xunlei share parsing and downloads | Xunlei account required |
| Quark, Baidu and 123 share downloads | The currently integrated download APIs require an account; anonymous folder browsing does not imply anonymous downloads |
| UC and China Mobile Cloud (139) share downloads | Anonymous access is attempted when the provider permits it; authorization may still be required |
| Public-video downloads | Anonymous access is preferred; available formats and account requirements depend on the source website |

On Windows, Quark, UC, Baidu and 139 use embedded official sign-in pages. 123 supports password sign-in, and Xunlei supports password/SMS sign-in. Browser credential import and manual credentials remain available as fallback options. Android retains its in-app provider sign-in flows.

Encrypted account backups are available under **Windows: My → Credential backup (认证备份)** and **Android: My → Download & app settings (下载与应用设置) → Export/import cloud credentials**. Backups require a password. A restored session may still require provider verification.

## Downloads

Get official builds from [GitHub Releases](https://github.com/chanha666/JieXi/releases).

| Platform / file | What to do |
| --- | --- |
| `JieXi-4.1.0-Windows-Setup.exe` | Run the installer, then open JieXi from the desktop or Start menu |
| `JieXi-4.1.0-Windows-Portable.zip` | Extract the entire archive, then run `解析.exe`; keep the bundled `app`, `runtime`, media engine and license folders |
| `JieXi-4.1.0-Android.apk` | Install the APK using Android's system installer |
| `SHA256SUMS.txt` | Verify downloaded files against their SHA-256 hashes |
| `BUILD-INFO.txt` | Check the exact source commit used for the build |

Windows packages include Java and the required media components; no separate Python, yt-dlp or FFmpeg installation is needed. Portable and installed builds provide the same functionality. The installer adds shortcuts and standard uninstall support.

### Upgrading

- **Windows 3.1.0 or later:** run the 4.1.0 installer. The installer migrates only the three known task-data files and never overwrites existing destination data. An occupied file, conflict or unsafe path stops the upgrade rather than discarding data.
- **Windows 3.0.x or earlier:** direct upgrades are blocked. Back up `%LOCALAPPDATA%\解析` first, uninstall the old application through Windows Settings, and then install the new version. Keep your backup until the new installation is verified.
- Current Windows task data lives in `%LOCALAPPDATA%\JieXi\Data`, separately from the program installation; normal uninstall does not remove it.
- **Android public releases:** 4.1 retains the public RSA-4096 release certificate introduced in 4.0. Later builds signed with the same certificate can be installed over it.
- **Early Android debug builds:** these use a different signing certificate. Android 7–12 requires uninstalling the old test build first, which removes its local app data. On Android 13+, a separate migration APK may be requested through Issues or email if test-build data must be preserved; it is not an ordinary public-release asset.

## Features

### Cloud drives

- Quark, UC, Xunlei, Baidu, 123 and China Mobile Cloud (139).
- Share-link recognition, extraction codes, folder browsing, multiple selection and downloads.
- In-app account sign-in, locally encrypted credentials and expired-session messages.
- Personal cloud files and quota, folder downloads, rename, move, delete and share operations.
- Segmented transfers, resume, retries, pause, continue, cancellation and task removal.
- Searchable history and categorized bookmarks.

### Public video and audio

- Recognizes public pages from YouTube, Bilibili, Douyin, X, TikTok, Xiaohongshu, Weibo, AcFun, WeChat Channels and sites supported by yt-dlp. Recognition is not a guarantee that every site or link can currently be downloaded.
- Direct media URLs, batch input, available high-quality formats including 4K/8K, audio-only output and subtitle options.
- Fast paths for public Douyin and X pages, with fallback to the local yt-dlp engine.
- Windows bundles yt-dlp, FFmpeg, ffprobe and Deno; Android bundles youtube-dl-android, FFmpeg and Aria2.

“Best quality” means the highest format actually available to anonymous access or the current account. JieXi does not turn low-resolution video into genuine 4K/8K and does not bypass DRM, subscriptions, paywalls or provider access controls. Some websites require sign-in, CAPTCHA or cookies.

Historical checks from the **4.0 release**, not a full-site acceptance result for 4.1: a Douyin file of 148,915,518 bytes was downloaded on Android, with parsing and segmented reads checked on Windows; X H.264 720p and Bilibili anonymous 720p downloads were completed. Bilibili may still return HTTP 403 under pressure or frequent requests. The YouTube anonymous sample hit a site challenge and was not marked as a successful anonymous download.

### Local media tools

- Repair a selected image region and write a new image without overwriting the original.
- Extract MP3 audio from a local video.
- Capture a video frame at a chosen timestamp.
- Export media metadata on Windows, including resolution, codec, bitrate and duration.

## Everyday use

### Interface language

On both platforms, open **My → Appearance → 语言 / Language** (Chinese: **我的 → 外观 → 语言 / Language**). Choose **English**, **简体中文**, **繁體中文**, or **System**. The choice is saved locally and updates the interface without restarting download queues.

The offline catalog covers primary navigation, common actions, account pages, settings and confirmation messages. File names, video titles, URLs and account data are never translated. Official sign-in pages, provider errors and some detailed diagnostics retain their original language. The language links at the top of this README switch only the project documentation, independently of the app.

Both versions use three primary sections: **Parse (解析), Downloads (下载), and My (我的)**. Paste a cloud-share or video link into Parse. Manage files and media together in Downloads. Accounts, tools, appearance, preferences, diagnostics, updates and author support are grouped under My.

### Windows

The default download location is `D:\解析下载`. If D: is unavailable or unwritable, JieXi falls back to the current user's Downloads folder. Change the location in settings. Video format and batch options are also available from My → Video download options.

Single-task downloads are uncapped by default. More connections do not necessarily mean more speed: server limits, network conditions and file size still matter.

### Android

Public media is saved to `Download/解析`. Cloud-drive files use the system Downloads directory or a folder authorized in settings. The Android system share menu can send text links to JieXi.

Background downloads use a foreground service. On Android 13+, allow notifications to see background download progress and controls. Battery optimization may affect long-running transfers.

### Feature parity and validation

The Windows version now includes personal-drive management, categorized bookmarks, encrypted account backups, download preferences and completed-file actions in addition to the existing media tools. Android-specific system sharing, wallpaper colors and launcher aliases are implemented through Android; they are not reproduced pixel-for-pixel on Windows.

- Android: 79 unit tests passed.
- Windows: 88 tests passed; one live-credential test is skipped by default. A separate read-only Baidu credential check passed.
- Android 35 small-screen navigation and light/dark themes, plus Windows navigation, scrolling and theme switching, were inspected.
- Remote move, delete, share and save-to-drive operations have **not** been exercised with real accounts across all six providers. This release is not a claim that all provider operations or public-video sites have passed live testing.
- Windows Wi-Fi-only behavior still needs a physical wireless-adapter test.

The detailed Chinese feature matrix is in [FEATURES-4.1.md](FEATURES-4.1.md).

## Updates, security and feedback

- Both applications use the author's HTTPS update endpoint and RSA-signed update manifest.
- Downloaded updates are checked against SHA-256 hashes. Android also checks package identity, version and release certificate before handing the APK to the system installer.
- Credentials stay on the local device: Windows uses DPAPI; Android uses Android Keystore. Exported diagnostic logs are redacted.
- Windows installers do **not** currently have a commercial Authenticode signature. SmartScreen may show a warning. Authenticode signing and the application's signed update manifest are separate mechanisms.
- Report issues through [GitHub Issues](https://github.com/chanha666/JieXi/issues/new) or `3316109338@qq.com`. English reports are welcome. Include the app version, platform, reproduction steps and redacted logs; do not submit cookies, tokens or backup passwords.

## Building from source

JDK 17 is required. Android builds also require the Android SDK. Windows sign-in components require the .NET 8 SDK, and the Windows installer requires WiX 3. The repository includes the Gradle Wrapper. Windows media binaries are tracked with Git LFS: run `git lfs pull` after cloning.

```powershell
# Shared tests, Windows tests and portable build
.\gradlew.bat :sharedCore:test :desktopApp:test :desktopApp:packagePortable

# Android debug validation
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

# Author's release workflow; requires private signing material
.\tools\BuildRelease.ps1
```

Windows media components are under `tools/media-engine`. Packaging generates and verifies a per-file SHA-256 manifest. Private release signing settings are stored outside the repository in `D:\CodexSecrets\解析\release.properties`; those keys are not supplied with the source and must never be committed.

## Credits and license

JieXi extends [CYQawa/YunX](https://github.com/CYQawa/YunX) and integrates public-video downloads and local media tools. It is released under [GNU AGPL-3.0](LICENSE). Modified distributions must retain license and attribution notices and provide corresponding source code as required by the license. Bundled yt-dlp, FFmpeg, Deno and site plugins retain their respective licenses.
