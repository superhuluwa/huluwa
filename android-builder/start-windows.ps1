[CmdletBinding()]
param(
    [string]$InstallDirectory = "$env:LOCALAPPDATA\HuluwaAndroidBuilder"
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding

try {
    $root = [IO.Path]::GetFullPath($InstallDirectory)
    $configuration = Join-Path $root 'config.json'
    $runner = Join-Path $root 'run-windows.ps1'
    foreach ($file in @($configuration, $runner)) {
        if (!(Test-Path -LiteralPath $file -PathType Leaf)) {
            throw "构建节点尚未安装，请先运行 android-builder\install-windows.ps1。缺少文件：$file"
        }
    }
    $config = Get-Content -LiteralPath $configuration -Raw -Encoding UTF8 | ConvertFrom-Json
    $stateFile = Join-Path $config.dataDirectory 'worker.json'
    $taskName = 'HuluwaAndroidBuilder'
    $task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
    if (!$task) { throw '找不到 HuluwaAndroidBuilder 计划任务，请先运行安装脚本。' }
    if ($task.State -eq 'Disabled') { throw '构建计划任务已禁用，请先在任务计划程序中启用 HuluwaAndroidBuilder。' }
    $powershell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
    $expectedArguments = '-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "{0}"' -f $runner
    if (@($task.Actions).Count -ne 1 -or $task.Actions[0].Execute -ne $powershell -or $task.Actions[0].Arguments -ne $expectedArguments) {
        throw '计划任务与指定安装目录不匹配，请使用 -InstallDirectory 指定实际目录，或重新运行安装脚本。'
    }
    $started = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $alreadyRunning = $task.State -eq 'Running'
    if (!$alreadyRunning) {
        Start-ScheduledTask -TaskName $taskName
        Write-Host '正在启动 Windows 打包服务……'
    }
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    do {
        $state = $null
        if (Test-Path -LiteralPath $stateFile) {
            try { $state = Get-Content -LiteralPath $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json } catch { $state = $null }
        }
        $task = Get-ScheduledTask -TaskName $taskName
        $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        $minimumHeartbeat = if ($alreadyRunning) { $now - 15 } else { $started }
        if ($task.State -eq 'Running' -and $state -and $state.heartbeat -ge $minimumHeartbeat) {
            Write-Host 'Windows 打包服务已运行，构建进程心跳正常。'
            Write-Host '可在葫芦娃 App 构建管理中选择 Windows 节点；主服务连接状态以页面为准。'
            Write-Host "日志：$(Join-Path $root 'agent.log')"
            exit 0
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "启动后未检测到正常心跳，请检查日志：$(Join-Path $root 'agent.log')"
} catch {
    Write-Host ("启动失败：{0}" -f $_.Exception.Message) -ForegroundColor Red
    exit 1
}
