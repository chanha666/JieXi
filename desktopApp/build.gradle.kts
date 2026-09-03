import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
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
            packageName = "解析"
            packageVersion = "3.1.0"
            description = "网盘分享链接解析与高速下载工具"
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

// WiX is only required for an installer. This task creates a self-contained
// portable Windows application with the JDK jpackage tool, so a transient WiX
// download failure never blocks distribution.
val preparePortableInput by tasks.registering(Sync::class) {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("portable-input"))
    from(configurations.runtimeClasspath)
    from(tasks.jar)
    from(project.file("browserHelper/publish-net48-x64")) {
        into("browser-helper")
    }
}

tasks.register<Exec>("packagePortable") {
    dependsOn(preparePortableInput, tasks.named("createRuntimeImage"))
    val outputDir = layout.buildDirectory.dir("portable").get().asFile
    doFirst { project.delete(outputDir) }
    commandLine(
        "jpackage",
        "--type", "app-image",
        "--name", "解析",
        "--dest", outputDir.absolutePath,
        "--input", layout.buildDirectory.dir("portable-input").get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", "com.yunx.desktop.MainKt",
        "--runtime-image", layout.buildDirectory.dir("compose/tmp/main/runtime").get().asFile.absolutePath,
        "--icon", project.file("src/main/resources/icon.ico").absolutePath,
        "--vendor", "解析",
        "--app-version", "3.1.0",
        "--description", "网盘分享链接解析与高速下载工具"
    )
}

tasks.register<Exec>("packageInstaller") {
    dependsOn(preparePortableInput, tasks.named("createRuntimeImage"))
    val outputDir = layout.buildDirectory.dir("installer").get().asFile
    doFirst {
        project.delete(outputDir)
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
        "--name", "解析",
        "--dest", outputDir.absolutePath,
        "--input", layout.buildDirectory.dir("portable-input").get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", "com.yunx.desktop.MainKt",
        "--runtime-image", layout.buildDirectory.dir("compose/tmp/main/runtime").get().asFile.absolutePath,
        "--icon", project.file("src/main/resources/icon.ico").absolutePath,
        "--vendor", "解析",
        "--app-version", "3.1.0",
        "--description", "解析 - 网盘分享链接解析与高速下载工具",
        "--license-file", rootProject.file("LICENSE").absolutePath,
        "--win-dir-chooser",
        "--win-per-user-install",
        "--win-menu",
        "--win-menu-group", "解析",
        "--win-shortcut",
        "--win-upgrade-uuid", "fa714901-4d4b-4ef0-88bf-fb934613727c"
    )
}
