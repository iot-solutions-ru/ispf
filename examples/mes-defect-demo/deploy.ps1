param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$BundlePath = (Join-Path $PSScriptRoot "bundle.json")
)

$resolved = Resolve-Path -LiteralPath $BundlePath
$body = [System.IO.File]::ReadAllText($resolved, [System.Text.UTF8Encoding]::new($false))

$response = Invoke-RestMethod `
    -Method POST `
    -Uri "$BaseUrl/api/v1/applications/mes-defect-demo/deploy" `
    -Headers @{
        "X-ISPF-Role"   = "admin"
        "Content-Type"  = "application/json; charset=utf-8"
    } `
    -Body $body

$response | ConvertTo-Json -Depth 6
