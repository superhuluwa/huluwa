[CmdletBinding()]
param(
    [string]$ServerUrl,
    [string]$NodeId,
    [string]$Token,
    [string]$InstallDirectory = "$env:LOCALAPPDATA\HuluwaAndroidBuilder",
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$AndroidHome = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$SigningDirectory,
    [string]$SdkDownloadAddress,
    [switch]$NoStart
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$env:PYTHONUTF8 = '1'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
if (!$SigningDirectory) { $SigningDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'android' }
if (!$ServerUrl) { $ServerUrl = Read-Host '葫芦娃主服务地址' }
if (!$NodeId) { $NodeId = Read-Host '构建节点 ID（网页添加节点后显示）' }
if (!$Token) {
    $secureToken = Read-Host '构建节点密钥' -AsSecureString
    $Token = [Net.NetworkCredential]::new('', $secureToken).Password
}
$uri = [Uri]$ServerUrl
if ($uri.Scheme -notin @('http','https') -or !$uri.Host -or $uri.UserInfo) { throw '主服务地址无效' }
if ($NodeId -notmatch '^[a-f0-9]{32}$' -or $Token -notmatch '^[a-f0-9]{64}$') { throw '节点 ID 或密钥格式无效' }
if (!$JavaHome) {
    $JavaHome = Get-ChildItem "$env:USERPROFILE\.jdks\jdk-21*" -Directory | Select-Object -First 1 -ExpandProperty FullName
}
foreach ($file in @("$JavaHome\bin\java.exe", "$JavaHome\bin\keytool.exe", "$AndroidHome\platform-tools\adb.exe", "$SigningDirectory\keystore.properties")) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "缺少构建依赖：$file" }
}
$python = (Get-Command python.exe -ErrorAction Stop).Source
& $python -c 'import sys; assert sys.version_info >= (3,10), "Python 3.10+ required"'
if ($LASTEXITCODE -ne 0) { throw '需要 Python 3.10 或更高版本' }
Get-Command git.exe, curl.exe -ErrorAction Stop | Out-Null
$sdkArguments = @((Join-Path $PSScriptRoot 'setup-sdk.py'), '--sdk', $AndroidHome)
if ($SdkDownloadAddress) { $sdkArguments += @('--download-address', $SdkDownloadAddress) }
& $python @sdkArguments
if ($LASTEXITCODE -ne 0) { throw 'Android SDK 组件准备失败' }
$taskName = 'HuluwaAndroidBuilder'
$previousConfig = $null
$configFile = Join-Path $InstallDirectory 'config.json'
if (Test-Path -LiteralPath $configFile) {
    $previousConfig = Get-Content -LiteralPath $configFile -Raw -Encoding UTF8 | ConvertFrom-Json
}
$stateFile = if ($previousConfig) { Join-Path $previousConfig.dataDirectory 'worker.json' } else { Join-Path $InstallDirectory 'data\worker.json' }
if (Test-Path -LiteralPath $stateFile) {
    $state = Get-Content -LiteralPath $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($state.currentTask) { throw '构建节点正在执行任务，请先等待任务完成或在 App 中取消' }
}
$oldTask = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($oldTask) { Stop-ScheduledTask -TaskName $taskName; Start-Sleep -Seconds 3 }
$root = [IO.Path]::GetFullPath($InstallDirectory)
New-Item -ItemType Directory -Path $root -Force | Out-Null
# Restrict local enrollment and signing material to this user and SYSTEM.
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $root /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '无法保护构建节点配置目录' }
foreach ($name in @('agent.py','worker.py','process_tree.py','android.init.gradle','git-askpass.py','run-windows.ps1')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $root $name) -Force
}
$signing = Join-Path $root 'signing'
New-Item -ItemType Directory -Path $signing -Force | Out-Null
$properties = [IO.File]::ReadAllText((Join-Path $SigningDirectory 'keystore.properties'))
$match = [regex]::Match($properties, '(?m)^\s*storeFile\s*=\s*(.+?)\s*$')
if (!$match.Success) { throw '签名配置缺少 storeFile' }
$storeFile = $match.Groups[1].Value.Replace('\\','\').Replace('\:',':')
$keyPath = if ([IO.Path]::IsPathRooted($storeFile)) { $storeFile } else { Join-Path $SigningDirectory $storeFile }
if (!(Test-Path -LiteralPath $keyPath -PathType Leaf)) { throw '现有 Release 签名文件不存在' }
$destinationKey = Join-Path $signing 'release.keystore'
if (Test-Path -LiteralPath $destinationKey) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $existingHash = [Convert]::ToBase64String($sha.ComputeHash([IO.File]::ReadAllBytes($destinationKey)))
        $inputHash = [Convert]::ToBase64String($sha.ComputeHash([IO.File]::ReadAllBytes($keyPath)))
    } finally { $sha.Dispose() }
    if ($existingHash -ne $inputHash) { throw '已部署的签名与输入签名不同，拒绝覆盖' }
} else { Copy-Item -LiteralPath $keyPath -Destination $destinationKey }
$properties = [regex]::Replace($properties, '(?m)^\s*storeFile\s*=.*$', 'storeFile=release.keystore')
$utf8 = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path $signing 'keystore.properties'), $properties, $utf8)
$dataDirectory = Join-Path $root "data\nodes\$NodeId"
if ($previousConfig -and $previousConfig.nodeId -eq $NodeId -and $previousConfig.serverUrl.TrimEnd('/') -eq $ServerUrl.TrimEnd('/')) {
    $dataDirectory = $previousConfig.dataDirectory
}
$config = @{
    serverUrl = $ServerUrl.TrimEnd('/'); nodeId = $NodeId; token = $Token
    javaHome = $JavaHome; androidHome = $AndroidHome; signingDirectory = $signing
    dataDirectory = $dataDirectory; workDirectory = (Join-Path $root 'workspace')
    python = $python
}
[IO.File]::WriteAllText((Join-Path $root 'config.json'), ($config | ConvertTo-Json), $utf8)
$powershell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$action = New-ScheduledTaskAction -Execute $powershell -Argument ('-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "{0}"' -f (Join-Path $root 'run-windows.ps1')) -WorkingDirectory $root
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $identity
$settings = New-ScheduledTaskSettingsSet -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew
$principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
if (!$NoStart) { Start-ScheduledTask -TaskName $taskName }
Write-Host "已部署 Windows 后台构建节点：$root"
Write-Host "任务计划：$taskName；当前用户登录后自动运行；日志：$root\agent.log"
