# Local profile. Does not print the token.
# GET /api/v1/drivers is operator or above. POST start and deploy need developer or admin. operator is 403 on those.
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin",
    [string]$DevicePath = "root.platform.devices.demo-sensor-01",
    [string]$AppId = "",
    [string]$BundlePath = "",
    [switch]$Strict,
    [switch]$WithSeeds
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

$config = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/config"
if ($config.mode -ne "local") {
    throw "auth/config mode is '$($config.mode)'. admin/admin is the local profile."
}

$loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/auth/login" -ContentType "application/json" -Body $loginBody
if (-not $login.token) {
    throw "login response has no token"
}
$headers = @{ Authorization = "Bearer $($login.token)" }

$info = Invoke-RestMethod -Uri "$BaseUrl/api/v1/info"
$health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health"
Write-Output "$($info.shortName) $($info.version) $($info.environment) health=$($health.status)"

$drivers = @(Invoke-RestMethod -Uri "$BaseUrl/api/v1/drivers" -Headers $headers)
$ids = @($drivers | ForEach-Object { $_.id })
if ($ids -notcontains "virtual") {
    throw "virtual is not in GET /api/v1/drivers ($($ids.Count) drivers)"
}
Write-Output "drivers=$($ids.Count) virtual=present"

$started = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/drivers/runtime/start?devicePath=$([uri]::EscapeDataString($DevicePath))" -Headers $headers
Write-Output "start $($started.devicePath) driver=$($started.driverId) status=$($started.status) connected=$($started.connected)"

if ($BundlePath) {
    if (-not $AppId) { throw "-AppId is required with -BundlePath" }
    $strict = if ($Strict) { "true" } else { "false" }
    $seeds = if ($WithSeeds) { "true" } else { "false" }
    $uri = "$BaseUrl/api/v1/applications/$([uri]::EscapeDataString($AppId))/deploy?strict=$strict&withSeeds=$seeds"
    $deployed = Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType "application/json" -InFile $BundlePath
    Write-Output "deploy $AppId status=$($deployed.status) snapshotActive=$($deployed.snapshotActive)"
    if ($deployed.status -ne "OK") {
        $first = @($deployed.errors) | Select-Object -First 1
        throw "deploy $AppId status=$($deployed.status) $first"
    }
}
