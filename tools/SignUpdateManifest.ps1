param(
    [Parameter(Mandatory = $true)][string]$ReleaseDirectory,
    [string]$Version = ((Get-Content -LiteralPath (Join-Path $PSScriptRoot '../version.properties') -Raw | ConvertFrom-StringData).versionName),
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
    body = "解析 ${Version}：统一版本号，修复重复提示更新；更新支持系统/软件代理、下载进度、断点续传和退出安装，兼容 Windows 短路径。`nJieXi ${Version}: unified versioning, proxy-aware resumable updates, progress reporting, exit-to-install and Windows short-path compatibility."
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
