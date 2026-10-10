$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
foreach ($command in @('node', 'npm.cmd')) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command was not found. Install Node.js with npm and configure PATH."
    }
}
$javaBin = (Get-Item (Get-Command java).Source).Directory.Parent.FullName
$env:JAVA_HOME = $javaBin
Write-Host 'Preparing frontend assets and starting Huluwa Console...'
& "$root\mvnw.cmd" -q -Pfrontend spring-boot:run
if ($LASTEXITCODE -ne 0) {
    throw "Huluwa Console stopped with exit code $LASTEXITCODE."
}