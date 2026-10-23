$ErrorActionPreference = 'Stop'
$env:PYTHONUTF8 = '1'
$env:PYTHONUNBUFFERED = '1'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
$OutputEncoding = [Console]::OutputEncoding
$configuration = Join-Path $PSScriptRoot 'config.json'
$config = Get-Content -LiteralPath $configuration -Raw -Encoding UTF8 | ConvertFrom-Json
$log = Join-Path $PSScriptRoot 'agent.log'
if ((Test-Path -LiteralPath $log) -and (Get-Item -LiteralPath $log).Length -gt 20MB) {
    Move-Item -LiteralPath $log -Destination (Join-Path $PSScriptRoot 'agent.previous.log') -Force
}
& $config.python -u (Join-Path $PSScriptRoot 'agent.py') --config $configuration *>> $log
exit $LASTEXITCODE
