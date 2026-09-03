param(
    [string]$SecretRoot = 'D:\CodexSecrets\解析'
)

$ErrorActionPreference = 'Stop'
$properties = Join-Path $SecretRoot 'release.properties'
$updatePrivateKey = Join-Path $SecretRoot 'update-private-key.pem'
$updatePublicKey = Join-Path $SecretRoot 'update-public-key.der'

if (Test-Path -LiteralPath $properties) {
    throw "发布密钥已存在，脚本拒绝覆盖：$SecretRoot"
}

New-Item -ItemType Directory -Force -Path $SecretRoot | Out-Null
$rsa = [Security.Cryptography.RSA]::Create(3072)
try {
    $privateBytes = $rsa.ExportPkcs8PrivateKey()
    $publicBytes = $rsa.ExportSubjectPublicKeyInfo()
    $privateText = "-----BEGIN PRIVATE KEY-----`n" +
        [Convert]::ToBase64String($privateBytes, [Base64FormattingOptions]::InsertLineBreaks) +
        "`n-----END PRIVATE KEY-----`n"
    [IO.File]::WriteAllText($updatePrivateKey, $privateText, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllBytes($updatePublicKey, $publicBytes)
    $publicBase64 = [Convert]::ToBase64String($publicBytes)
} finally {
    $rsa.Dispose()
}

$lines = @(
    'updateManifestUrl=',
    "updatePublicKeyBase64=$publicBase64",
    'githubFeedbackUrl=https://github.com/chanha666/JieXi/issues/new'
)
[IO.File]::WriteAllLines($properties, $lines, [Text.UTF8Encoding]::new($false))

# Only the current Windows account and SYSTEM may read the release secrets.
& icacls $SecretRoot /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '发布密钥目录权限收紧失败' }
Write-Host "Windows 更新签名密钥已生成到 $SecretRoot；填入 updateManifestUrl 后应用内更新才会启用。"
