import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val desktopReleaseProperties = Properties().apply {
    val config = file("D:/CodexSecrets/解析/release.properties")
    if (config.isFile) config.reader(Charsets.UTF_8).use(::load)
}

// GitHub's English Windows runners use a legacy ANSI code page that cannot
// represent the Chinese product name. Keep the release name as "解析" locally,
// while allowing CI to use the ASCII-safe launcher name "JieXi".
val windowsPackageName = providers.environmentVariable("JIEXI_PACKAGE_NAME")
    .orElse("解析")
    .get()

val windowsPackageVersion = "4.0.0"
val mediaEngineSourceDir = rootProject.layout.projectDirectory.dir("tools/media-engine")
val requiredMediaEngineFiles = listOf(
    "yt-dlp.exe",
    "deno.exe",
    "ffmpeg/bin/ffmpeg.exe",
    "ffmpeg/bin/ffprobe.exe",
    "ffmpeg/bin/avcodec-63.dll",
    "ffmpeg/bin/avdevice-63.dll",
    "ffmpeg/bin/avfilter-12.dll",
    "ffmpeg/bin/avformat-63.dll",
    "ffmpeg/bin/avutil-61.dll",
    "ffmpeg/bin/swresample-7.dll",
    "ffmpeg/bin/swscale-10.dll",
    "plugins/wechat/yt_dlp_plugins/extractor/wechat.py",
    "licenses/Deno-LICENSE.txt",
    "licenses/FFmpeg-LICENSE.txt",
    "licenses/WECHAT-EXTRACTOR.txt",
    "licenses/yt-dlp-LICENSE.txt",
    "licenses/yt-dlp-THIRD_PARTY_LICENSES.txt",
    "LOCK.sha256",
    "SOURCES.md",
    "THIRD-PARTY-NOTICES.txt",
    "SBOM.spdx.json"
)

fun verifyMediaEngineFiles(root: File, label: String) {
    val missing = requiredMediaEngineFiles.filter { relative ->
        val candidate = root.resolve(relative)
        !candidate.isFile || candidate.length() <= 0L
    }
    check(missing.isEmpty()) {
        "$label 缺少或包含空文件:\n${missing.joinToString("\n") { " - $it" }}"
    }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

fun verifyMediaEngineManifest(root: File, label: String) {
    val manifest = root.resolve("MANIFEST.sha256")
    check(manifest.isFile && manifest.length() > 0L) { "$label 缺少媒体引擎校验清单" }
    val entries = manifest.readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() }
        .associate { line ->
            val splitAt = line.indexOf("  ")
            check(splitAt == 64) { "$label 校验清单格式错误: $line" }
            line.substring(splitAt + 2) to line.substring(0, splitAt)
        }
    check(entries.keys == requiredMediaEngineFiles.toSet()) {
        "$label 校验清单与必需文件集合不一致"
    }
    entries.forEach { (relative, expected) ->
        val actual = sha256(root.resolve(relative))
        check(actual.equals(expected, ignoreCase = true)) {
            "$label 文件校验失败: $relative"
        }
    }
}

val defaultWindowsOutputPath = if (file("D:/").isDirectory) {
    "D:/CodexBuilds/JieXi/$windowsPackageVersion/windows"
} else {
    layout.buildDirectory.dir("distributions/windows").get().asFile.absolutePath
}
val windowsDistributionRoot = file(
    providers.gradleProperty("jiexi.windows.outputDir")
        .orElse(providers.environmentVariable("JIEXI_WINDOWS_OUTPUT_DIR"))
        .orElse(defaultWindowsOutputPath)
        .get()
)
val portableOutputDir = windowsDistributionRoot.resolve("portable")
val installerOutputDir = windowsDistributionRoot.resolve("installer")
val portableAppImageDir = portableOutputDir.resolve(windowsPackageName)
val mediaEngineManifestFile = layout.buildDirectory.file("generated/media-engine/MANIFEST.sha256")

tasks.processResources {
    filesMatching("update.properties") {
        expand(
            "manifestUrl" to desktopReleaseProperties.getProperty("updateManifestUrl", ""),
            "publicKeyBase64" to desktopReleaseProperties.getProperty("updatePublicKeyBase64", ""),
            "githubFeedbackUrl" to desktopReleaseProperties.getProperty("githubFeedbackUrl", "")
        )
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedCore"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20250107")
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

compose.desktop {
    application {
        mainClass = "com.yunx.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = windowsPackageName
            packageVersion = windowsPackageVersion
            description = "网盘与公开视频解析、高速下载及媒体工具"
            vendor = "解析"
            licenseFile.set(rootProject.file("LICENSE"))

            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
                dirChooser = true
                perUserInstall = true
                menuGroup = "解析"
                upgradeUuid = "fa714901-4d4b-4ef0-88bf-fb934613727c"
            }
        }
    }
}

tasks.test {
    useJUnit()
}

// Development runs use the source-controlled engine directly. Packaged runs
// receive the equivalent property through jpackage's $APPDIR macro below.
tasks.withType<JavaExec>().configureEach {
    val engineDir = mediaEngineSourceDir.asFile.absolutePath
    systemProperty("jiexi.media.engine.dir", engineDir)
    environment("JIEXI_MEDIA_ENGINE_DIR", engineDir)
}

val verifyMediaEngineSource by tasks.registering {
    group = "verification"
    description = "Verify every executable, plug-in and license required by the Windows media engine."
    inputs.dir(mediaEngineSourceDir)
    doLast {
        verifyMediaEngineFiles(mediaEngineSourceDir.asFile, "Windows 媒体引擎源目录")
    }
}

val generateMediaEngineManifest by tasks.registering {
    group = "distribution"
    description = "Generate SHA-256 checksums for the media engine bundled with Windows packages."
    dependsOn(verifyMediaEngineSource)
    inputs.dir(mediaEngineSourceDir)
    outputs.file(mediaEngineManifestFile)
    doLast {
        val lines = requiredMediaEngineFiles.sorted().map { relative ->
            val source = mediaEngineSourceDir.asFile.resolve(relative)
            val checksum = sha256(source)
            "$checksum  ${relative.replace('\\', '/')}"
        }
        val target = mediaEngineManifestFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(lines.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
    }
}

val browserHelperOutputDir = project.file("browserHelper/publish-net48-x64")
val portableDotnetSdk = file("D:/CodexTools/dotnet/dotnet.exe")
val dotnetExecutable = providers.environmentVariable("DOTNET_EXE")
    .orElse(if (portableDotnetSdk.isFile) portableDotnetSdk.absolutePath else "dotnet")
    .get()
val publishBrowserHelper by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Rebuild the sensitive WebView2 login helper from the audited source."
    inputs.file(project.file("browserHelper/EmbeddedLogin.csproj"))
    inputs.file(project.file("browserHelper/Program.cs"))
    inputs.file(project.file("src/main/resources/icon.ico"))
    inputs.property("dotnetExecutable", dotnetExecutable)
    outputs.file(browserHelperOutputDir.resolve("解析登录.exe"))
    outputs.upToDateWhen { false }
    doFirst {
        project.delete(browserHelperOutputDir)
        browserHelperOutputDir.mkdirs()
    }
    commandLine(
        dotnetExecutable, "publish",
        project.file("browserHelper/EmbeddedLogin.csproj").absolutePath,
        "--configuration", "Release",
        "--runtime", "win-x64",
        "--no-self-contained",
        "--output", browserHelperOutputDir.absolutePath
    )
}

// WiX is only required for an installer. This task creates a self-contained
// portable Windows application with the JDK jpackage tool, so a transient WiX
// download failure never blocks distribution.
val preparePortableInput by tasks.registering(Sync::class) {
    dependsOn(tasks.jar, generateMediaEngineManifest, publishBrowserHelper)
    into(layout.buildDirectory.dir("portable-input"))
    from(configurations.runtimeClasspath)
    from(tasks.jar)
    from(browserHelperOutputDir) {
        into("browser-helper")
    }
    from(mediaEngineSourceDir) {
        into("vendor")
    }
    from(mediaEngineManifestFile) {
        into("vendor")
    }
    from(rootProject.file("LICENSE")) {
        into("licenses")
        rename { "JieXi-LICENSE.txt" }
    }
    doLast {
        val stagedRoot = destinationDir.resolve("vendor")
        verifyMediaEngineFiles(stagedRoot, "jpackage 输入目录")
        check(stagedRoot.resolve("MANIFEST.sha256").isFile) {
            "jpackage 输入目录缺少 vendor/MANIFEST.sha256"
        }
        verifyMediaEngineManifest(stagedRoot, "jpackage 输入目录")
        check(destinationDir.resolve("licenses/JieXi-LICENSE.txt").isFile) {
            "jpackage 输入目录缺少项目许可证"
        }
    }
}

tasks.register<Exec>("packagePortable") {
    dependsOn(preparePortableInput, tasks.named("createRuntimeImage"))
    group = "distribution"
    description = "Build the verified Windows 4.0.0 portable application image."
    inputs.dir(layout.buildDirectory.dir("portable-input"))
    inputs.dir(layout.buildDirectory.dir("compose/tmp/main/runtime"))
    inputs.file(project.file("src/main/resources/icon.ico"))
    inputs.property("packageName", windowsPackageName)
    inputs.property("packageVersion", windowsPackageVersion)
    outputs.dir(portableAppImageDir)
    doFirst {
        project.delete(portableOutputDir)
        portableOutputDir.mkdirs()
    }
    commandLine(
        "jpackage",
        "--type", "app-image",
        "--name", windowsPackageName,
        "--dest", portableOutputDir.absolutePath,
        "--input", layout.buildDirectory.dir("portable-input").get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", "com.yunx.desktop.MainKt",
        "--runtime-image", layout.buildDirectory.dir("compose/tmp/main/runtime").get().asFile.absolutePath,
        "--icon", project.file("src/main/resources/icon.ico").absolutePath,
        "--vendor", "解析",
        "--app-version", windowsPackageVersion,
        "--description", "网盘与公开视频解析、高速下载及媒体工具",
        "--java-options", "-Djiexi.media.engine.dir=\$APPDIR/vendor"
    )
    doLast {
        val packagedEngine = portableAppImageDir.resolve("app/vendor")
        verifyMediaEngineFiles(packagedEngine, "Windows 便携版")
        check(packagedEngine.resolve("MANIFEST.sha256").isFile) {
            "Windows 便携版缺少媒体引擎校验清单"
        }
        verifyMediaEngineManifest(packagedEngine, "Windows 便携版")
        check(portableAppImageDir.resolve("app/licenses/JieXi-LICENSE.txt").isFile) {
            "Windows 便携版缺少项目许可证"
        }
        val launcherConfig = portableAppImageDir.resolve("app/$windowsPackageName.cfg")
        check(launcherConfig.isFile && launcherConfig.readText().contains("jiexi.media.engine.dir=\$APPDIR/vendor")) {
            "Windows 便携版启动器未写入媒体引擎定位参数"
        }
    }
}

tasks.register("verifyPortablePackage") {
    group = "verification"
    description = "Verify the complete Windows portable package payload."
    dependsOn("packagePortable")
    doLast {
        val packagedEngine = portableAppImageDir.resolve("app/vendor")
        verifyMediaEngineFiles(packagedEngine, "Windows 便携版")
        verifyMediaEngineManifest(packagedEngine, "Windows 便携版")
    }
}

tasks.register<Exec>("packageInstaller") {
    dependsOn("verifyPortablePackage")
    group = "distribution"
    description = "Wrap the already verified Windows 4.0.0 app image in an EXE installer."
    inputs.dir(portableAppImageDir)
    inputs.file(rootProject.file("LICENSE"))
    inputs.file(project.file("src/main/resources/icon.ico"))
    inputs.property("packageName", windowsPackageName)
    inputs.property("packageVersion", windowsPackageVersion)
    outputs.dir(installerOutputDir)
    doFirst {
        project.delete(installerOutputDir)
        installerOutputDir.mkdirs()
        val bundledWix = rootProject.file("tools/wix311")
        val configuredWix = System.getenv("WIX_PATH")?.let(::file)
        val wixDir = configuredWix?.takeIf { it.isDirectory }
            ?: bundledWix.takeIf { it.isDirectory }
            ?: error("WiX 3 is required. Set WIX_PATH or extract it to tools/wix311.")
        environment("PATH", "${wixDir.absolutePath};${System.getenv("PATH")}")
    }
    commandLine(
        "jpackage",
        "--type", "exe",
        "--name", windowsPackageName,
        "--dest", installerOutputDir.absolutePath,
        "--app-image", portableAppImageDir.absolutePath,
        "--icon", project.file("src/main/resources/icon.ico").absolutePath,
        "--vendor", "解析",
        "--app-version", windowsPackageVersion,
        "--description", "解析 - 网盘与公开视频解析、高速下载及媒体工具",
        "--license-file", rootProject.file("LICENSE").absolutePath,
        "--win-dir-chooser",
        "--win-per-user-install",
        "--win-menu",
        "--win-menu-group", "解析",
        "--win-shortcut",
        "--win-upgrade-uuid", "fa714901-4d4b-4ef0-88bf-fb934613727c"
    )
    doLast {
        val installers = installerOutputDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("exe", ignoreCase = true) && it.length() > 0L }
        check(installers.isNotEmpty()) { "Windows 安装版未产生有效的 EXE 文件" }
        // The installer is built exclusively from the verified app image above;
        // retaining that image also makes the bundled payload auditable.
        verifyMediaEngineFiles(portableAppImageDir.resolve("app/vendor"), "Windows 安装版源应用镜像")
        verifyMediaEngineManifest(portableAppImageDir.resolve("app/vendor"), "Windows 安装版源应用镜像")
    }
}
