param(
    [string]$Version = '4.0.0',
    [string]$BuildRoot = "D:\CodexBuilds\JieXi",
    [string]$Repository = 'chanha666/JieXi'
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$versionRoot = Join-Path $BuildRoot $Version
$releaseDirectory = Join-Path $versionRoot 'release'
$windowsInstaller = Join-Path $versionRoot "windows\installer\解析-$Version.exe"
$windowsPortable = Join-Path $versionRoot 'windows\portable\解析'
$androidApk = Join-Path $versionRoot "android\解析-Android-$Version.apk"
$releaseWindowsInstaller = Join-Path $releaseDirectory "JieXi-$Version-Windows-Setup.exe"
$releaseWindowsPortable = Join-Path $releaseDirectory "JieXi-$Version-Windows-Portable.zip"
$releaseAndroidApk = Join-Path $releaseDirectory "JieXi-$Version-Android.apk"
$checksumsPath = Join-Path $releaseDirectory 'SHA256SUMS.txt'
$buildInfoPath = Join-Path $releaseDirectory 'BUILD-INFO.txt'
$manifestPath = Join-Path $repoRoot 'update-manifest.json'

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    throw "版本号格式错误：$Version"
}
if (-not ([IO.Path]::GetFullPath($versionRoot)).StartsWith([IO.Path]::GetFullPath($BuildRoot), [StringComparison]::OrdinalIgnoreCase)) {
    throw "发布目录越界：$versionRoot"
}

$hadMirrorSetting = Test-Path Env:JIEXI_USE_MIRROR
$previousMirrorSetting = $env:JIEXI_USE_MIRROR
$hadNugetPackages = Test-Path Env:NUGET_PACKAGES
$previousNugetPackages = $env:NUGET_PACKAGES
$env:JIEXI_USE_MIRROR = 'false'
$env:NUGET_PACKAGES = 'D:\CodexCache\nuget'
Push-Location $repoRoot
try {
    $workingTree = @(& git status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw '无法读取 Git 工作区状态。' }
    if ($workingTree.Count -gt 0) { throw '正式发布必须从无修改、无未跟踪文件的 Git 提交构建。' }
    $sourceCommit = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $sourceCommit -notmatch '^[0-9a-f]{40}$') { throw '无法确定发布源码提交。' }
    $releaseTagOutput = @(& git rev-list -n 1 "v$Version" 2>$null)
    $releaseTagExitCode = $LASTEXITCODE
    $releaseTagCommit = ($releaseTagOutput -join '').Trim()
    if ($releaseTagExitCode -ne 0 -or $releaseTagCommit -ne $sourceCommit) {
        throw "正式发布要求标签 v$Version 精确指向当前提交。"
    }

    & .\gradlew.bat --no-daemon `
        :sharedCore:test `
        :app:testDebugUnitTest `
        :app:lintDebug `
        :app:verifyAndroidRelease `
        :desktopApp:test `
        :desktopApp:packageInstaller
    if ($LASTEXITCODE -ne 0) { throw '自动测试或正式打包失败。' }
} finally {
    Pop-Location
    if ($hadMirrorSetting) {
        $env:JIEXI_USE_MIRROR = $previousMirrorSetting
    } else {
        Remove-Item Env:JIEXI_USE_MIRROR -ErrorAction SilentlyContinue
    }
    if ($hadNugetPackages) {
        $env:NUGET_PACKAGES = $previousNugetPackages
    } else {
        Remove-Item Env:NUGET_PACKAGES -ErrorAction SilentlyContinue
    }
}

foreach ($required in @($windowsInstaller, $androidApk)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "缺少发布文件：$required" }
}
if (-not (Test-Path -LiteralPath $windowsPortable -PathType Container)) {
    throw "缺少 Windows 绿色版目录：$windowsPortable"
}

[IO.Directory]::CreateDirectory($releaseDirectory) | Out-Null
foreach ($knownFile in @($releaseWindowsInstaller, $releaseWindowsPortable, $releaseAndroidApk, $checksumsPath, $buildInfoPath)) {
    if (Test-Path -LiteralPath $knownFile -PathType Leaf) { Remove-Item -LiteralPath $knownFile -Force }
}

Copy-Item -LiteralPath $windowsInstaller -Destination $releaseWindowsInstaller
Copy-Item -LiteralPath $androidApk -Destination $releaseAndroidApk
Compress-Archive -LiteralPath $windowsPortable -DestinationPath $releaseWindowsPortable -CompressionLevel Optimal

$buildInfo = @(
    "product=JieXi",
    "version=$Version",
    "sourceCommit=$sourceCommit",
    "repository=https://github.com/$Repository",
    "builtAtUtc=$([DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))"
)
[IO.File]::WriteAllLines($buildInfoPath, $buildInfo, [Text.UTF8Encoding]::new($false))

$releaseFiles = @($releaseWindowsInstaller, $releaseWindowsPortable, $releaseAndroidApk, $buildInfoPath)
$checksumLines = foreach ($file in $releaseFiles) {
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $([IO.Path]::GetFileName($file))"
}
[IO.File]::WriteAllLines($checksumsPath, $checksumLines, [Text.Encoding]::ASCII)

& (Join-Path $PSScriptRoot 'SignUpdateManifest.ps1') `
    -ReleaseDirectory $releaseDirectory `
    -Version $Version `
    -Repository $Repository `
    -OutputPath $manifestPath
if ($LASTEXITCODE -ne 0) { throw '签名更新清单生成失败。' }

Write-Host "发布验收完成：$releaseDirectory"
Write-Host "签名更新清单：$manifestPath"
