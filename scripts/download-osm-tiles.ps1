<#
  Downloads OSM raster tiles into src/map_web/tiles/{z}/{x}/{y}.png for offline Leaflet.

  Default: full world, zoom MinZoom..MaxZoom (default 0..6, ~5461 tiles, ~tens of MB).

  OpenStreetMap tile usage policy: https://operations.osmfoundation.org/policies/tiles/
  Use for personal/educational offline caching only; do not hammer the service.
  This script rate-limits (~150ms between requests) and identifies via User-Agent.

  Examples:
    .\download-osm-tiles.ps1
    .\download-osm-tiles.ps1 -MaxZoom 8 -BoundingBox "120.4,16.3,120.7,16.5"
#>
param(
    [int] $MinZoom = 0,
    [int] $MaxZoom = 6,
    [string] $BoundingBox = "",
    [string] $BaseUrl = "https://tile.openstreetmap.org",
    [int] $DelayMs = 150
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $root "src\map_web\tiles"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$UserAgent = "LoraxBotanicalJournal/1.0 (educational offline tiles; not for bulk redistribution)"

function Get-LonTileX([double] $lon, [int] $z) {
    $n = [math]::Pow(2, $z)
    return [int][math]::Floor(($lon + 180.0) / 360.0 * $n)
}

function Get-LatTileY([double] $lat, [int] $z) {
    $n = [math]::Pow(2, $z)
    $latRad = $lat * [math]::PI / 180.0
    $tan = [math]::Tan($latRad)
    $sec = 1.0 / [math]::Cos($latRad)
    $yy = (1.0 - [math]::Log($tan + $sec) / [math]::PI) / 2.0 * $n
    return [int][math]::Floor($yy)
}

function Get-TileIndices([int] $z, [double] $minLon, [double] $minLat, [double] $maxLon, [double] $maxLat) {
    $n = [int][math]::Pow(2, $z)
    $xMin = [math]::Max(0, [math]::Min($n - 1, (Get-LonTileX $minLon $z)))
    $xMax = [math]::Max(0, [math]::Min($n - 1, (Get-LonTileX $maxLon $z)))
    $yN = Get-LatTileY $maxLat $z
    $yS = Get-LatTileY $minLat $z
    $yMin = [math]::Max(0, [math]::Min($n - 1, [math]::Min($yN, $yS)))
    $yMax = [math]::Max(0, [math]::Min($n - 1, [math]::Max($yN, $yS)))
    return @{
        XMin = [math]::Min($xMin, $xMax)
        XMax = [math]::Max($xMin, $xMax)
        YMin = [math]::Min($yMin, $yMax)
        YMax = [math]::Max($yMin, $yMax)
    }
}

$useBbox = -not [string]::IsNullOrWhiteSpace($BoundingBox)
$minLon = -180.0; $minLat = -85.05112878; $maxLon = 180.0; $maxLat = 85.05112878
if ($useBbox) {
    $parts = $BoundingBox.Split(",") | ForEach-Object { $_.Trim() }
    if ($parts.Count -ne 4) { throw "BoundingBox must be minLon,minLat,maxLon,maxLat" }
    $minLon = [double]$parts[0]; $minLat = [double]$parts[1]; $maxLon = [double]$parts[2]; $maxLat = [double]$parts[3]
}

$count = 0
$fail = 0
for ($z = $MinZoom; $z -le $MaxZoom; $z++) {
    $n = [int][math]::Pow(2, $z)
    if ($useBbox) {
        $r = Get-TileIndices $z $minLon $minLat $maxLon $maxLat
        $xStart = $r.XMin; $xEnd = $r.XMax
        $yStart = $r.YMin; $yEnd = $r.YMax
    } else {
        $xStart = 0; $xEnd = $n - 1
        $yStart = 0; $yEnd = $n - 1
    }
    for ($x = $xStart; $x -le $xEnd; $x++) {
        for ($y = $yStart; $y -le $yEnd; $y++) {
            $dir = Join-Path $OutDir "$z\$x"
            New-Item -ItemType Directory -Force -Path $dir | Out-Null
            $path = Join-Path $dir "$y.png"
            $url = "$BaseUrl/$z/$x/$y.png"
            try {
                Invoke-WebRequest -Uri $url -OutFile $path -Headers @{ "User-Agent" = $UserAgent } -UseBasicParsing
                $count++
                if (($count % 50) -eq 0) { Write-Host "Downloaded $count tiles (z=$z)..." }
            } catch {
                $fail++
                Write-Warning "Failed: $url"
            }
            Start-Sleep -Milliseconds $DelayMs
        }
    }
}
Write-Host "Finished. OK=$count Failed=$fail Output=$OutDir"
