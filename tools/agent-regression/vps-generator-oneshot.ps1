# BL-180: call live solution generator on a remote ISPF stand and write soft-budget evidence.
# Usage:
#   pwsh tools/agent-regression/vps-generator-oneshot.ps1 -BaseUrl https://ispf.iot-solutions.ru
param(
    [string]$BaseUrl = "https://ispf.iot-solutions.ru",
    [string]$Domain = "hvac",
    [string]$Username = "admin",
    [string]$Password = "admin",
    [string]$Out = "build/agent-regression/live-generator-results.json",
    [int]$SoftBudgetMs = 900000
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path (Split-Path $Out) | Out-Null

$prompts = @{
    hvac = "Describe a building HVAC plant with one AHU and zone comfort monitoring. Need an overview dashboard and a high-status alert on the hub."
    mes  = "Describe a factory MES cell with one packaging line and OEE monitoring. Need an overview dashboard and a high-status alert on the hub."
    scada = "Describe a SCADA plant with one pump station and tank level monitoring. Need an overview dashboard and a high-status alert on the hub."
}
if (-not $prompts.ContainsKey($Domain)) {
    throw "Domain must be hvac|mes|scada"
}
$prompt = $prompts[$Domain]

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$loginPath = Join-Path $env:TEMP "ispf-vps-login.json"
Set-Content -Path $loginPath -Value $loginBody -NoNewline -Encoding utf8

Write-Host "=== Login $BaseUrl ==="
$loginRaw = curl.exe -sS --max-time 30 -X POST "$BaseUrl/api/v1/auth/login" -H "Content-Type: application/json" --data-binary "@$loginPath"
$login = $loginRaw | ConvertFrom-Json
if (-not $login.token) {
    throw "Login failed: $loginRaw"
}
$token = $login.token

Write-Host "=== AI provider ==="
$providerRaw = curl.exe -sS --max-time 30 -H "Authorization: Bearer $token" "$BaseUrl/api/v1/ai/provider"
Write-Host $providerRaw
$provider = $providerRaw | ConvertFrom-Json
if (-not $provider.available) {
    throw "AI provider not available: $providerRaw"
}

$genBody = @{ prompt = $prompt; apply = $true } | ConvertTo-Json -Compress
$genPath = Join-Path $env:TEMP "ispf-vps-generate.json"
Set-Content -Path $genPath -Value $genBody -NoNewline -Encoding utf8

Write-Host "=== Generate apply domain=$Domain ==="
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$genRaw = curl.exe -sS --max-time 960 -X POST "$BaseUrl/api/v1/ai/solutions/generate" `
    -H "Authorization: Bearer $token" `
    -H "Content-Type: application/json" `
    --data-binary "@$genPath"
$sw.Stop()
$elapsedMs = [int]$sw.Elapsed.TotalMilliseconds
Write-Host "elapsedMs=$elapsedMs"
Write-Host ($genRaw.Substring(0, [Math]::Min(500, $genRaw.Length)))

$gen = $genRaw | ConvertFrom-Json
$functionalOk = ($gen.mode -eq "live") -and $gen.appId -and $gen.hubPath -and $gen.dashboardPath -and $gen.alertPath
$softBudgetMet = $elapsedMs -le $SoftBudgetMs

$uiCode = ""
if ($functionalOk) {
    $uiCode = curl.exe -sS --max-time 30 -o NUL -w "%{http_code}" -H "Authorization: Bearer $token" `
        "$BaseUrl/api/v1/operator-apps/$($gen.appId)/ui"
    if ($uiCode -ne "200") { $functionalOk = $false }
}

$evidence = [ordered]@{
    generatedAt    = (Get-Date).ToUniversalTime().ToString("o")
    runStartedAt   = (Get-Date).ToUniversalTime().ToString("o")
    source         = "vps-generator-oneshot.ps1"
    baseUrl        = $BaseUrl
    softBudgetMs   = $SoftBudgetMs
    softBudgetMet  = [bool]($softBudgetMet -and $functionalOk)
    functionalOk   = [bool]$functionalOk
    domains        = @(
        [ordered]@{
            domain         = $Domain
            status         = $(if ($functionalOk) { "OK" } else { "ERROR" })
            elapsedMs      = $elapsedMs
            softBudgetMs   = $SoftBudgetMs
            softBudgetMet  = [bool]$softBudgetMet
            appId          = "$($gen.appId)"
            hubPath        = "$($gen.hubPath)"
            dashboardPath  = "$($gen.dashboardPath)"
            alertPath      = "$($gen.alertPath)"
            mode           = "$($gen.mode)"
            composition    = "$($gen.composition)"
            bundleTrust    = "$($gen.bundleTrust)"
            operatorUiHttp = "$uiCode"
        }
    )
}

($evidence | ConvertTo-Json -Depth 6) | Set-Content -Path $Out -Encoding utf8
Write-Host "Wrote $Out"
Write-Host "functionalOk=$functionalOk softBudgetMet=$($evidence.softBudgetMet) elapsedMs=$elapsedMs ui=$uiCode"

if (-not $functionalOk) { exit 1 }
if (-not $softBudgetMet) {
    Write-Host "WARN: soft budget exceeded (soft signal)"
    exit 2
}
exit 0
