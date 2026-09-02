$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$archivePath = Join-Path $projectRoot 'assets/freedoom-0.13.0.zip'
$extractPath = Join-Path $projectRoot 'build/freedoom-download'
New-Item -ItemType Directory -Force (Join-Path $projectRoot 'assets'), (Join-Path $projectRoot 'licenses') | Out-Null
Invoke-WebRequest 'https://github.com/freedoom/freedoom/releases/download/v0.13.0/freedoom-0.13.0.zip' -OutFile $archivePath
$expected = '3f9b264f3e3ce503b4fb7f6bdcb1f419d93c7b546f4df3e874dd878db9688f59'
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $expected) { throw 'Freedoom archive checksum mismatch' }
Expand-Archive -LiteralPath $archivePath -DestinationPath $extractPath -Force
Copy-Item -LiteralPath (Join-Path $extractPath 'freedoom-0.13.0/freedoom1.wad') -Destination (Join-Path $projectRoot 'assets/freedoom1.wad')
Copy-Item -LiteralPath (Join-Path $extractPath 'freedoom-0.13.0/COPYING.txt') -Destination (Join-Path $projectRoot 'licenses/FREEDOOM.txt')
Copy-Item -LiteralPath (Join-Path $extractPath 'freedoom-0.13.0/CREDITS.txt'), (Join-Path $extractPath 'freedoom-0.13.0/CREDITS-MUSIC.txt') -Destination (Join-Path $projectRoot 'licenses')
Write-Output 'Freedoom 0.13.0 installed and verified.'
