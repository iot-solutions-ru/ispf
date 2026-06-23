# Deploy MQTT Temperature Lab to ISPF (register app, bundle deploy, models, drivers, map coords).
param(
    [string]$BaseUrl = "https://ispf.iot-solutions.ru",
    [string]$Username = "admin",
    [string]$Password = "admin",
    [string]$AppId = "mqtt-temperature-lab",
    [ValidateSet("virtual", "mqtt")]
    [string]$DriverMode = "virtual"
)

$ErrorActionPreference = "Stop"
$BundlePath = Join-Path $PSScriptRoot "..\examples\lab-mqtt-temperature\bundle.json"
$ModelName = "mqtt-sensor-v1"

function Get-AuthHeaders {
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST -Body $loginBody -ContentType "application/json"
    return @{ Authorization = "Bearer $($login.token)" }
}

$headers = Get-AuthHeaders

try {
    $regBody = @{
        appId = $AppId
        displayName = "MQTT Temperature Lab"
        tablePrefix = "mlt_"
        schemaName = "mqtt_temperature_lab"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications" -Method POST -Headers $headers -Body $regBody -ContentType "application/json" | Out-Null
    Write-Host "Application registered: $AppId"
} catch {
    Write-Host "Application register skipped (may already exist)"
}

$bundle = Get-Content -Raw -Encoding UTF8 $BundlePath
$deploy = Invoke-RestMethod -Uri "$BaseUrl/api/v1/applications/$AppId/deploy" -Method POST -Headers $headers -Body $bundle -ContentType "application/json; charset=utf-8"
Write-Host "Bundle deploy status: $($deploy.status)"
if ($deploy.errors) {
    $deploy.errors | ForEach-Object { Write-Host "  error: $_" }
    $fatal = $deploy.errors | Where-Object { $_ -notmatch '^(register|applicationSync):' }
    if ($fatal.Count -gt 0) { throw "Bundle deploy reported errors" }
}

$modelId = (Invoke-RestMethod -Uri "$BaseUrl/api/v1/models" -Headers $headers | Where-Object { $_.name -eq $ModelName }).id
if (-not $modelId) { throw "Model not found: $ModelName" }

$sensors = @(
    @{ id = "sensor-01"; topic = "esp2/1wire_test1/temperature"; lat = 55.761; lon = 37.638; label = "1wire test 1"; base = "21.69" },
    @{ id = "sensor-02"; topic = "esp2/1wire_test2/temperature"; lat = 55.765; lon = 37.645; label = "1wire test 2"; base = "21.81" },
    @{ id = "sensor-03"; topic = "esp1/aqua_coldcorner_water/temperature"; lat = 55.752; lon = 37.621; label = "Aqua cold corner"; base = "22.37" },
    @{ id = "sensor-04"; topic = "esp1/aqua_island_air/temperature"; lat = 55.748; lon = 37.615; label = "Aqua island air"; base = "21.56" }
)

$mqttBinding = @(
    @{
        id = "parse-mqtt-temperature"
        name = "Parse MQTT temperature payload"
        enabled = $true
        order = 10
        activators = @{
            onStartup = $true
            onVariableChange = @(@{ objectPath = "self"; variableName = "temperature" })
            onEvent = $null
            periodicMs = 0
        }
        condition = ""
        expression = "double(self.temperature.raw)"
        target = @{ variableName = "temperature"; field = "value" }
    }
) | ConvertTo-Json -Depth 8 -Compress

foreach ($s in $sensors) {
    $path = "root.platform.devices.mqtt-lab.sensors.$($s.id)"
    Write-Host "Configuring $path ..."

    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/models/$modelId/apply?objectPath=$path" -Method POST -Headers $headers | Out-Null
    } catch {
        Write-Host "  model apply: $($_.Exception.Message)"
    }

    $coordCreate = @{
        name = "coordinates"
        schema = @{
            name = "coordinates"
            fields = @(
                @{ name = "latitude"; type = "DOUBLE" },
                @{ name = "longitude"; type = "DOUBLE" }
            )
        }
        readable = $true
        writable = $true
        historyEnabled = $false
    } | ConvertTo-Json -Depth 6 -Compress
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/objects/by-path/variables?path=$path" -Method POST -Headers $headers -Body $coordCreate -ContentType "application/json" | Out-Null
    } catch { }
    $coordValue = @{
        schema = @{
            name = "coordinates"
            fields = @(
                @{ name = "latitude"; type = "DOUBLE" },
                @{ name = "longitude"; type = "DOUBLE" }
            )
        }
        rows = @(@{ latitude = $s.lat; longitude = $s.lon })
    } | ConvertTo-Json -Depth 6 -Compress
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/objects/by-path/variables?path=$path&name=coordinates" -Method PUT -Headers $headers -Body $coordValue -ContentType "application/json" | Out-Null
    } catch { }

    $labelCreate = @{
        name = "sensorLabel"
        schema = @{ name = "sensorLabel"; fields = @(@{ name = "value"; type = "STRING" }) }
        readable = $true
        writable = $false
        historyEnabled = $false
    } | ConvertTo-Json -Depth 6 -Compress
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/objects/by-path/variables?path=$path" -Method POST -Headers $headers -Body $labelCreate -ContentType "application/json" | Out-Null
    } catch { }
    $labelValue = @{
        schema = @{ name = "sensorLabel"; fields = @(@{ name = "value"; type = "STRING" }) }
        rows = @(@{ value = $s.label })
    } | ConvertTo-Json -Depth 6 -Compress
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/v1/objects/by-path/variables?path=$path&name=sensorLabel" -Method PUT -Headers $headers -Body $labelValue -ContentType "application/json" | Out-Null
    } catch { }

    if ($DriverMode -eq "mqtt") {
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/v1/objects/by-path/binding-rules?path=$path" -Method PUT -Headers $headers -Body $mqttBinding -ContentType "application/json" | Out-Null
        } catch {
            Write-Host "  binding: $($_.Exception.Message)"
        }
        $driverConfig = @{
            driverId = "mqtt"
            pollIntervalMs = 5000
            configuration = @{ brokerUrl = "tcp://m5.wqtt.ru:11296"; topicPrefix = "" }
            pointMappings = @{ temperature = $s.topic }
            autoStart = $true
        }
    } else {
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/v1/objects/by-path/binding-rules/parse-mqtt-temperature?path=$path" -Method DELETE -Headers $headers | Out-Null
        } catch { }
        $driverConfig = @{
            driverId = "virtual"
            pollIntervalMs = 3000
            configuration = @{
                profile = "demo"
                baseTemperature = $s.base
                amplitude = "0.3"
                periodSec = "90"
            }
            pointMappings = @{ temperature = "sim" }
            autoStart = $true
        }
    }

    $cfgJson = $driverConfig | ConvertTo-Json -Depth 6 -Compress
    try {
        $drv = Invoke-RestMethod -Uri "$BaseUrl/api/v1/drivers/runtime/configure?devicePath=$path" -Method PUT -Headers $headers -Body $cfgJson -ContentType "application/json"
        Write-Host "  driver: $($drv.status) connected=$($drv.connected) lastError=$($drv.lastError)"
    } catch {
        Write-Host "  driver error: $($_.ErrorDetails.Message)"
    }
}

$bffBody = @{
    objectPath = "root.platform.devices.mqtt-lab.sensors.sensor-01"
    functionName = "listSensors"
    input = @{ schema = @{ name = "in"; fields = @() }; rows = @(@{}) }
    wireProfile = "anima-operator-v1"
} | ConvertTo-Json -Depth 6 -Compress
$bff = Invoke-RestMethod -Uri "$BaseUrl/api/v1/bff/invoke" -Method POST -Headers $headers -Body $bffBody -ContentType "application/json"
Write-Host "BFF listSensors: $($bff.error_code) rows=$($bff.result.Count)"

Write-Host ""
Write-Host "Done."
Write-Host "Operator UI: $BaseUrl  -> login -> Operator -> app '$AppId'"
Write-Host "Admin dashboards: root.platform.dashboards.mqtt-lab-overview | mqtt-lab-detail"
if ($DriverMode -eq "virtual") {
    Write-Host "DriverMode=virtual (demo temperatures). For live MQTT use: -DriverMode mqtt and WQTT credentials in driver configuration."
}
