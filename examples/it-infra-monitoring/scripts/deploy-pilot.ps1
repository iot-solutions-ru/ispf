param(
    [string]$BaseUrl = $(if ($env:ISPF_BASE_URL) { $env:ISPF_BASE_URL } else { "http://185.246.66.158:8080" }),
    [string]$Token = $env:ISPF_TOKEN
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")

function Invoke-JsonPost {
    param([string]$Url, [string]$BodyPath, [hashtable]$Query = @{})
    $uri = $Url
    if ($Query.Count -gt 0) {
        $qs = ($Query.GetEnumerator() | ForEach-Object { "{0}={1}" -f $_.Key, [uri]::EscapeDataString([string]$_.Value) }) -join "&"
        $uri = "$Url`?$qs"
    }
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $body = Get-Content -Raw -Encoding UTF8 $BodyPath
    Write-Host "POST $uri"
    $response = Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -Body $body -UseBasicParsing
    Write-Host "  -> $($response.StatusCode)"
}

Write-Host "=== ITM pilot deploy ==="
Write-Host "Base URL: $BaseUrl"
if (-not $Token) {
    Write-Warning "ISPF_TOKEN not set — API calls may return 403. Set `$env:ISPF_TOKEN before deploy."
}

# 1. Register application (idempotent)
$registerBody = @{
    appId = "it-infra-monitoring"
    schemaName = "it_infra_monitoring"
    tablePrefix = "itm_"
    displayName = "IT Infra Monitoring"
} | ConvertTo-Json
$registerPath = Join-Path $env:TEMP "itm-register.json"
$registerBody | Set-Content -Encoding UTF8 $registerPath
try {
    Invoke-JsonPost -Url "$BaseUrl/api/v1/applications" -BodyPath $registerPath
} catch {
    Write-Host "  (register skipped or already exists): $($_.Exception.Message)"
}

# 2. Product bundle
$productBundle = Join-Path $repoRoot "examples\it-infra-monitoring\bundle.json"
Invoke-JsonPost -Url "$BaseUrl/api/v1/platform/packages/import" -BodyPath $productBundle -Query @{ packageId = "it-infra-monitoring" }

# 3. Site plugins
$plugins = @(
    @{ id = "itm-plugin-inventory-m11"; path = "plugins\itm-site-inventory\sites\m11\bundle.json" },
    @{ id = "itm-plugin-topology-m11"; path = "plugins\itm-site-topology\sites\m11\bundle.json" },
    @{ id = "itm-plugin-integrations-m11"; path = "plugins\itm-site-integrations\sites\m11\bundle.json" }
)
foreach ($p in $plugins) {
    $bundlePath = Join-Path $repoRoot $p.path
    Invoke-JsonPost -Url "$BaseUrl/api/v1/platform/packages/import" -BodyPath $bundlePath -Query @{ packageId = $p.id }
}

# 4. Mimic diagram
$mimicPath = "root.platform.mimics.itm-m11-dcn"
$diagramPath = Join-Path $repoRoot "plugins\itm-site-topology\sites\m11\mimic-diagram.json"
if (Test-Path $diagramPath) {
    $diagramJson = Get-Content -Raw -Encoding UTF8 $diagramPath
    $saveBody = (@{ diagramJson = $diagramJson } | ConvertTo-Json -Compress)
    $savePath = Join-Path $env:TEMP "itm-mimic.json"
    $saveBody | Set-Content -Encoding UTF8 $savePath
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $mimicUrl = "$BaseUrl/api/v1/mimics/by-path/diagram?path=$([uri]::EscapeDataString($mimicPath))"
    Write-Host "PUT $mimicUrl"
    try {
        Invoke-WebRequest -Uri $mimicUrl -Method PUT -Headers $headers -Body (Get-Content -Raw $savePath) -UseBasicParsing | Out-Null
        Write-Host "  -> mimic diagram saved"
    } catch {
        Write-Warning "Mimic PUT failed: $($_.Exception.Message)"
    }
}

Write-Host "=== Done. Upload SVG to /itm-assets/m11/main_topology.svg on the web static host. ==="
Write-Host "Local asset: plugins\itm-site-topology\sites\m11\assets\main_topology.svg"
