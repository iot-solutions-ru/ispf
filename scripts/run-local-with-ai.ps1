# Load .env and start ispf-server with local profile + AI provider.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
        $name, $value = $_ -split '=', 2
        if ($name) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
    Write-Host "Loaded $envFile"
} else {
    Write-Warning "No .env file at $envFile - set ISPF_AI_API_KEY manually."
}

Set-Location $root
.\gradlew :packages:ispf-server:bootRun --args='--spring.profiles.active=local'
