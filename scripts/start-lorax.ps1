param(
    [ValidateSet('existing', 'demo')]
    [string]$Dataset = 'existing'
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$jdk = Join-Path $env:USERPROFILE '.jdks\temurin-25.0.5'
$maven = Join-Path $project 'mvnw.cmd'

if (-not (Test-Path -LiteralPath $jdk)) {
    throw "JDK 25 was not found at $jdk. Install JDK 25 or update the path in this script."
}

$env:JAVA_HOME = $jdk
Set-Location -LiteralPath $project

if ($Dataset -eq 'demo') {
    $env:LORAX_DATA_DIR = Join-Path $project 'demo-data'
    & $maven -o -Dlorax.data.dir=$env:LORAX_DATA_DIR javafx:run
} else {
    Remove-Item Env:LORAX_DATA_DIR -ErrorAction SilentlyContinue
    & $maven -o javafx:run
}

if ($LASTEXITCODE -ne 0) {
    throw "The Lorax stopped with exit code $LASTEXITCODE."
}
