param(
    [Parameter(Mandatory = $true)][string]$ReleaseDirectory,
    [string]$Version = '4.1.0',
    [string]$Repository = 'chanha666/JieXi',
    [string]$PrivateKeyPath = 'D:\CodexSecrets\解析\update-private-key.pem',
    [string]$OutputPath = (Join-Path $ReleaseDirectory 'update-manifest.json')
)

$ErrorActionPreference = 'Stop'
$assetNames = @(
    "JieXi-$Version-Windows-Setup.exe",
    "JieXi-$Version-Windows-Portable.zip",
    "JieXi-$Version-Android.apk"
)
$assets = foreach ($name in $assetNames) {
    $path = Join-Path $ReleaseDirectory $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "缺少发布文件：$path" }
    $url = [Uri]::new("https://github.com/$Repository/releases/download/v$Version/$name").AbsoluteUri
    [ordered]@{
        name = $name
        url = $url
        sha256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$payloadObject = [ordered]@{
    tag_name = "v$Version"
    body = "解析 $Version 整合版：统一 Windows 与 Android 的网盘分享解析、公开视频下载、批量任务、断点续传、媒体工具、诊断反馈、品牌图标与签名更新。"
    published_at = [DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    assets = @($assets)
}
$payloadBytes = [Text.Encoding]::UTF8.GetBytes(($payloadObject | ConvertTo-Json -Depth 5 -Compress))
$privatePem = [IO.File]::ReadAllText($PrivateKeyPath)
$rsa = [Security.Cryptography.RSA]::Create()
try {
    $rsa.ImportFromPem($privatePem)
    $signature = $rsa.SignData(
        $payloadBytes,
        [Security.Cryptography.HashAlgorithmName]::SHA256,
        [Security.Cryptography.RSASignaturePadding]::Pkcs1
    )
} finally {
    $rsa.Dispose()
}

$envelope = [ordered]@{
    payload = [Convert]::ToBase64String($payloadBytes)
    signature = [Convert]::ToBase64String($signature)
}
[IO.File]::WriteAllText($OutputPath, ($envelope | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
Write-Host "已生成签名更新清单：$OutputPath"
