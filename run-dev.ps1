$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
# 统一控制台为 UTF-8，避免 vite / Maven 的 UTF-8 输出在 GBK(936) 控制台下乱码
chcp 65001 > $null
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Text.UTF8Encoding]::new($false)

foreach ($command in @('node', 'npm.cmd')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command was not found. Install Node.js with npm and configure PATH."
    }
}
$javaBin = (Get-Item (Get-Command java).Source).Directory.Parent.FullName
$env:JAVA_HOME = $javaBin
Write-Host 'Preparing frontend assets and starting 葫芦娃助手...'
& "$root\mvnw.cmd" -q -Pfrontend spring-boot:run
if ($LASTEXITCODE -ne 0) {
    throw "葫芦娃助手 stopped with exit code $LASTEXITCODE."
}