# Loads variables from ../.env into the process environment, then starts the
# Spring Boot backend. This makes local runs work on Windows the same way
# start.sh sources .env on Linux. Run from the repo root with:
#   powershell -ExecutionPolicy Bypass -File start-local.ps1
# (LocalStack must already be running: docker compose up -d)

$ErrorActionPreference = "Stop"
$repoRoot = $PSScriptRoot
$envFile = Join-Path $repoRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env not found at $envFile. Copy the values you need (JWT_SECRET, AWS_*) there first."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $eq = $line.IndexOf('=')
        if ($eq -gt 0) {
            $key = $line.Substring(0, $eq).Trim()
            $val = $line.Substring($eq + 1).Trim().Trim('"')
            [Environment]::SetEnvironmentVariable($key, $val, "Process")
        }
    }
}

Write-Host "Loaded .env -> JWT_SECRET set: $([bool]$env:JWT_SECRET), AWS_ENDPOINT: $env:AWS_ENDPOINT"

$serverDir = Join-Path $repoRoot "server"
Set-Location $serverDir
& ".\mvnw.cmd" spring-boot:run
