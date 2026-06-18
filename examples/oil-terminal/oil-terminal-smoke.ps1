# Smoke-тест reference-стенда «нефтебаза — наряд на отгрузку» (ISPF)
# Запуск:
#   cd examples/oil-terminal
#   .\oil-terminal-smoke.ps1
#
# Требования: ispf-server запущен (профиль local/test, порт 8080)
# Переменные: $env:OIL_TEST_BASE_URL = "http://127.0.0.1:8080"

param(
    [string]$BaseUrl = $(if ($env:OIL_TEST_BASE_URL) { $env:OIL_TEST_BASE_URL } else { "http://127.0.0.1:8080" })
)

$ErrorActionPreference = "Stop"
$Api = "$BaseUrl/api/v1"

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Get-VarValue([string]$Path, [string]$Name) {
    $uri = "$Api/objects/by-path/variables/detail?path=$([uri]::EscapeDataString($Path))&name=$([uri]::EscapeDataString($Name))"
    $json = Invoke-RestMethod -Uri $uri -Method GET
    return [string]$json.value.rows[0].value
}

function Invoke-Function([string]$Path, [string]$Name, [hashtable]$Body = $null) {
    $uri = "$Api/objects/by-path/functions/invoke?path=$([uri]::EscapeDataString($Path))&name=$([uri]::EscapeDataString($Name))"
    if ($Body) {
        $payload = @{ schema = @{ name = "input"; fields = @() }; rows = @($Body) } | ConvertTo-Json -Depth 5 -Compress
        return Invoke-RestMethod -Uri $uri -Method POST -ContentType "application/json" -Body $payload
    }
    return Invoke-RestMethod -Uri $uri -Method POST -ContentType "application/json" -Body "{}"
}

function Assert-Equal([string]$Actual, [string]$Expected, [string]$Label) {
    if ([string]$Actual -ne [string]$Expected) {
        throw "ASSERT FAILED ($Label): expected '$Expected', got '$Actual'"
    }
    Write-Host "  OK $Label = $Actual" -ForegroundColor Green
}

$OrderPath = "root.platform.oil-terminal.orders.dispatch4521"
$TankPath = "root.platform.oil-terminal.tanks.rvs3"
$RackPath = "root.platform.oil-terminal.racks.rack2"

Write-Host "Oil terminal smoke test (ISPF)" -ForegroundColor Yellow
Write-Host "Base URL: $BaseUrl"

Write-Step "1. Проверка дерева объектов"
$objects = Invoke-RestMethod -Uri "$Api/objects?parent=root.platform.oil-terminal.orders" -Method GET
if (-not ($objects | Where-Object { $_.path -like "*.dispatch4521" })) {
    throw "Demo order dispatch4521 not found — is oil-terminal stand seeded?"
}

Write-Step "2. Назначение РВС и эстакады (assign)"
$result = Invoke-Function -Path $OrderPath -Name "assign" -Body @{ tankName = "rvs3"; rackName = "rack2" }
if (-not $result.rows[0].success) { throw "assign failed: $($result.rows[0].message)" }
$status = Get-VarValue $OrderPath "status"
Assert-Equal $status "ready" "dispatch4521.status"

Write-Step "3. Старт налива (start)"
$result = Invoke-Function -Path $OrderPath -Name "start"
if (-not $result.rows[0].success) { throw "start failed: $($result.rows[0].message)" }
$status = Get-VarValue $OrderPath "status"
Assert-Equal $status "filling" "dispatch4521.status"
$busy = Get-VarValue $RackPath "busy"
Assert-Equal $busy "True" "rack2.busy"

Write-Step "4. Эмуляция счётчика (TankMonitor + manual bump)"
Start-Sleep -Seconds 2
$liters = Get-VarValue $OrderPath "actualLiters"
if ([double]$liters -le 0) {
    throw "actualLiters should grow during filling, got $liters"
}
Write-Host "  OK actualLiters growing: $liters" -ForegroundColor Green

Write-Step "5. Завершение налива (complete)"
$result = Invoke-Function -Path $OrderPath -Name "complete"
if (-not $result.rows[0].success) { throw "complete failed: $($result.rows[0].message)" }
$status = Get-VarValue $OrderPath "status"
Assert-Equal $status "completed" "dispatch4521.status"
$busy = Get-VarValue $RackPath "busy"
Assert-Equal $busy "False" "rack2.busy"

Write-Step "6. Закрытие наряда (close)"
$result = Invoke-Function -Path $OrderPath -Name "close"
if (-not $result.rows[0].success) { throw "close failed: $($result.rows[0].message)" }
$status = Get-VarValue $OrderPath "status"
Assert-Equal $status "closed" "dispatch4521.status"

Write-Step "7. ERP import API"
$importBody = @{
    orderNo = "9999"
    productCode = "DT"
    plannedLiters = 15000
    vehiclePlate = "X999XX99"
    orderName = "dispatch9999"
} | ConvertTo-Json
$imported = Invoke-RestMethod -Uri "$Api/oil/dispatch/import" -Method POST -ContentType "application/json" -Body $importBody
if ($imported.status -ne "planned") { throw "import status expected planned" }
Write-Host "  OK imported order dispatch9999" -ForegroundColor Green

Write-Host "`nSMOKE TEST PASSED" -ForegroundColor Green
