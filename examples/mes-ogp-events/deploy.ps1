param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$BundlePath = (Join-Path $PSScriptRoot "bundle.json")
)

$resolved = Resolve-Path -LiteralPath $BundlePath
$uri = "$BaseUrl/api/v1/applications/mes-ogp-events/deploy"

# curl --data-binary avoids PowerShell UTF-8 / body encoding issues on Windows
$responseJson = curl.exe -s -X POST $uri `
    -H "X-ISPF-Role: admin" `
    -H "Content-Type: application/json; charset=utf-8" `
    --data-binary "@$resolved"

if ($LASTEXITCODE -ne 0) {
    throw "Deploy request failed (curl exit $LASTEXITCODE)"
}

$response = $responseJson | ConvertFrom-Json
$response | ConvertTo-Json -Depth 6

if ($response.status -ne "OK") {
    throw "Deploy status: $($response.status). Errors: $($response.errors -join '; ')"
}
