param(
    [string]$Version = '4.1.1',
    [string]$BuildRoot = 'D:\CodexBuilds\JieXi'
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$versionRoot = [IO.Path]::GetFullPath((Join-Path $BuildRoot $Version))
$windowsRoot = [IO.Path]::GetFullPath((Join-Path $versionRoot 'windows'))
$installerTemp = [IO.Path]::GetFullPath((Join-Path $windowsRoot 'installer-temp'))
$auditRoot = [IO.Path]::GetFullPath((Join-Path $windowsRoot 'installer-audit'))
$helperPath = [IO.Path]::GetFullPath((Join-Path $repoRoot 'desktopApp\installerHelper\publish-win-x64\JieXiPreserveData.exe'))
$darkPath = [IO.Path]::GetFullPath((Join-Path $repoRoot 'tools\wix311\dark.exe'))

foreach ($path in @($installerTemp, $auditRoot)) {
    if (-not $path.StartsWith($windowsRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "安装器审计目录越界：$path"
    }
}
if (-not (Test-Path -LiteralPath $installerTemp -PathType Container)) {
    throw "缺少 jpackage 安装器临时目录：$installerTemp"
}
if (-not (Test-Path -LiteralPath $helperPath -PathType Leaf)) {
    throw "缺少已构建的迁移助手：$helperPath"
}
if (-not (Test-Path -LiteralPath $darkPath -PathType Leaf)) {
    throw "缺少 WiX 反编译工具：$darkPath"
}

$msiFiles = @(Get-ChildItem -LiteralPath $installerTemp -Filter '*.msi' -File -Recurse)
if ($msiFiles.Count -ne 1) {
    throw "jpackage 临时目录必须且只能包含一个 MSI，实际为 $($msiFiles.Count) 个。"
}
$msiPath = $msiFiles[0].FullName

if (Test-Path -LiteralPath $auditRoot) {
    Remove-Item -LiteralPath $auditRoot -Recurse -Force
}
$extractRoot = Join-Path $auditRoot 'extracted'
$decompiledWxs = Join-Path $auditRoot 'main.wxs'
[IO.Directory]::CreateDirectory($extractRoot) | Out-Null

& $darkPath '-x' $extractRoot '-o' $decompiledWxs $msiPath
if ($LASTEXITCODE -ne 0) { throw "MSI 反编译失败，退出码：$LASTEXITCODE" }

$embeddedHelper = Join-Path $extractRoot 'Binary\JieXiMigrateBin'
if (-not (Test-Path -LiteralPath $embeddedHelper -PathType Leaf)) {
    throw 'MSI Binary 表中缺少 JieXiMigrateBin。'
}
$sourceHash = (Get-FileHash -LiteralPath $helperPath -Algorithm SHA256).Hash
$embeddedHash = (Get-FileHash -LiteralPath $embeddedHelper -Algorithm SHA256).Hash
if ($sourceHash -ne $embeddedHash) {
    throw "MSI 内迁移助手与已审计构建不一致：$embeddedHash != $sourceHash"
}

$installer = New-Object -ComObject WindowsInstaller.Installer
$database = $installer.OpenDatabase($msiPath, 0)
function Read-MsiRow {
    param([string]$Sql, [int]$Columns)
    $view = $database.OpenView($Sql)
    try {
        $view.Execute()
        $record = $view.Fetch()
        if ($null -eq $record) { throw "MSI 查询没有结果：$Sql" }
        $values = for ($index = 1; $index -le $Columns; $index++) { $record.StringData($index) }
        if ($null -ne $view.Fetch()) { throw "MSI 查询返回了多行：$Sql" }
        return [pscustomobject]@{ Values = [object[]]$values }
    } finally {
        $view.Close()
    }
}

$customAction = (Read-MsiRow "SELECT ``Type``, ``Source``, ``Target`` FROM ``CustomAction`` WHERE ``Action``='JieXiPreserveLegacyData'" 3).Values
if ([int]$customAction[0] -ne 2 -or $customAction[1] -ne 'JieXiMigrateBin' -or $customAction[2] -ne '"[LocalAppDataFolder]."') {
    throw "迁移 CustomAction 类型错误：Type=$($customAction[0]), Source=$($customAction[1]), Target=$($customAction[2])"
}
$legacyBlockAction = (Read-MsiRow "SELECT ``Type``, ``Target`` FROM ``CustomAction`` WHERE ``Action``='JieXiBlockUnsupportedLegacy'" 2).Values
$expectedLegacyBlockMessage = "检测到解析 3.0.x。为避免任务、历史和收藏数据损坏，本安装包不会覆盖旧版。请先备份旧版数据，在 Windows 设置中卸载旧版，再全新安装 $Version。当前旧版和数据均未被修改。"
if ([int]$legacyBlockAction[0] -ne 19 -or $legacyBlockAction[1] -cne $expectedLegacyBlockMessage) {
    throw "3.0.x 阻止动作错误：Type=$($legacyBlockAction[0]), Message=$($legacyBlockAction[1])"
}

$actionNames = @('FindRelatedProducts', 'JieXiBlockUnsupportedLegacy', 'InstallInitialize', 'JieXiPreserveLegacyData', 'RemoveExistingProducts', 'ProcessComponents')
$sequence = @{}
foreach ($actionName in $actionNames) {
    $row = (Read-MsiRow "SELECT ``Condition``, ``Sequence`` FROM ``InstallExecuteSequence`` WHERE ``Action``='$actionName'" 2).Values
    $sequence[$actionName] = [int]$row[1]
    if ($actionName -eq 'JieXiPreserveLegacyData' -and $row[0] -ne 'JP_UPGRADABLE_FOUND OR JP_DOWNGRADABLE_FOUND') {
        throw "迁移动作条件错误：$($row[0])"
    }
    if ($actionName -eq 'JieXiBlockUnsupportedLegacy' -and $row[0] -ne 'JIEXI_UNSUPPORTED_LEGACY_FOUND') {
        throw "3.0.x 阻止动作条件错误：$($row[0])"
    }
}
if (-not (
    $sequence.FindRelatedProducts -lt $sequence.JieXiBlockUnsupportedLegacy -and
    $sequence.JieXiBlockUnsupportedLegacy -lt $sequence.InstallInitialize -and
    $sequence.InstallInitialize -lt $sequence.JieXiPreserveLegacyData -and
    $sequence.JieXiPreserveLegacyData + 1 -eq $sequence.RemoveExistingProducts -and
    $sequence.RemoveExistingProducts -lt $sequence.ProcessComponents
)) {
    throw "安装顺序错误：InstallInitialize=$($sequence.InstallInitialize), Preserve=$($sequence.JieXiPreserveLegacyData), RemoveExistingProducts=$($sequence.RemoveExistingProducts), ProcessComponents=$($sequence.ProcessComponents)"
}

$unsupportedRule = (Read-MsiRow "SELECT ``VersionMin``, ``VersionMax``, ``Attributes`` FROM ``Upgrade`` WHERE ``ActionProperty``='JIEXI_UNSUPPORTED_LEGACY_FOUND'" 3).Values
$supportedRule = (Read-MsiRow "SELECT ``VersionMin``, ``VersionMax``, ``Attributes`` FROM ``Upgrade`` WHERE ``ActionProperty``='JP_UPGRADABLE_FOUND'" 3).Values
$onlyDetect = 0x2
$minimumInclusive = 0x100
$maximumInclusive = 0x200
if ($unsupportedRule[0] -ne '' -or $unsupportedRule[1] -ne '3.1.0' -or (([int]$unsupportedRule[2] -band $onlyDetect) -eq 0) -or (([int]$unsupportedRule[2] -band $maximumInclusive) -ne 0)) {
    throw "3.0.x 检测范围错误：Min=$($unsupportedRule[0]), Max=$($unsupportedRule[1]), Attributes=$($unsupportedRule[2])"
}
if ($supportedRule[0] -ne '3.1.0' -or $supportedRule[1] -ne $Version -or (([int]$supportedRule[2] -band $minimumInclusive) -eq 0)) {
    throw "可升级版本范围错误：Min=$($supportedRule[0]), Max=$($supportedRule[1]), Attributes=$($supportedRule[2])"
}

$upgradeCode = (Read-MsiRow "SELECT ``Value`` FROM ``Property`` WHERE ``Property``='UpgradeCode'" 1).Values
if ($upgradeCode -ne '{FA714901-4D4B-4EF0-88BF-FB934613727C}') {
    throw "UpgradeCode 意外变化：$upgradeCode"
}
$productVersion = (Read-MsiRow "SELECT ``Value`` FROM ``Property`` WHERE ``Property``='ProductVersion'" 1).Values
if ($productVersion -ne $Version) { throw "MSI 版本错误：$productVersion != $Version" }

Write-Host "MSI 升级数据保护审计通过：$msiPath"
Write-Host "迁移助手 SHA-256：$sourceHash"
Write-Host "直接升级范围：3.1.0 <= 旧版本 < $Version；3.0.x 安全阻止"
Write-Host "执行顺序：$($sequence.InstallInitialize) < $($sequence.JieXiPreserveLegacyData) < $($sequence.RemoveExistingProducts) < $($sequence.ProcessComponents)"
