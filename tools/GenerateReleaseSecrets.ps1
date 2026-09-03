param(
    [string]$SecretRoot = 'D:\CodexSecrets\解析'
)

$ErrorActionPreference = 'Stop'
$keystore = Join-Path $SecretRoot 'android-release.jks'
$properties = Join-Path $SecretRoot 'release.properties'
$updatePrivateKey = Join-Path $SecretRoot 'update-private-key.pem'
$updatePublicKey = Join-Path $SecretRoot 'update-public-key.der'

if ((Test-Path -LiteralPath $properties) -or (Test-Path -LiteralPath $keystore)) {
    throw "发布密钥已存在，脚本拒绝覆盖：$SecretRoot"
}

New-Item -ItemType Directory -Force -Path $SecretRoot | Out-Null
$passwordBytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($passwordBytes)
$password = -join ($passwordBytes | ForEach-Object { $_.ToString('x2') })

& keytool -genkeypair -v -keystore $keystore -storetype PKCS12 -storepass $password `
    -keypass $password -alias jiexi -keyalg RSA -keysize 4096 -validity 10000 `
    -dname 'CN=JieXi Release, OU=Release, O=JieXi, L=Local, ST=Local, C=CN'
if ($LASTEXITCODE -ne 0) { throw 'keytool 生成 Android 发布密钥失败' }

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
    "storeFile=$($keystore.Replace('\', '/'))",
    "storePassword=$password",
    'keyAlias=jiexi',
    "keyPassword=$password",
    'updateManifestUrl=',
    "updatePublicKeyBase64=$publicBase64"
)
[IO.File]::WriteAllLines($properties, $lines, [Text.UTF8Encoding]::new($false))

# Only the current Windows account and SYSTEM may read the release secrets.
& icacls $SecretRoot /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '发布密钥目录权限收紧失败' }
Write-Host "发布密钥已生成到 $SecretRoot；更新地址尚未配置，因此 2.3 会安全地禁用在线自更新。"
