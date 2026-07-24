# Start local n8n with ISPF community nodes loaded.
# Requires: n8n on PATH (npm i -g n8n@2.5.0 recommended for Node 22.14).

$ErrorActionPreference = "Stop"
$PackageRoot = Split-Path -Parent $PSScriptRoot

npm run build --prefix $PackageRoot | Out-Host

$env:N8N_PORT = if ($env:N8N_PORT) { $env:N8N_PORT } else { "5678" }
$env:N8N_SECURE_COOKIE = "false"
$env:N8N_DIAGNOSTICS_ENABLED = "false"
$env:N8N_PERSONALIZATION_ENABLED = "false"
$env:N8N_VERSION_NOTIFICATIONS_ENABLED = "false"
$env:N8N_CUSTOM_EXTENSIONS = $PackageRoot

Write-Host "N8N_CUSTOM_EXTENSIONS=$env:N8N_CUSTOM_EXTENSIONS"
Write-Host "Starting n8n on http://localhost:$env:N8N_PORT ..."
n8n start
