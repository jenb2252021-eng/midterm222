$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $root "src\map_web\tiles\0\0"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$path = Join-Path $dir "0.png"
Invoke-WebRequest -Uri "https://tile.openstreetmap.org/0/0/0.png" -OutFile $path `
    -Headers @{ "User-Agent" = "LoraxBotanicalJournal/1.0 (educational; sample tile)" } -UseBasicParsing
Write-Host "Wrote $path"
