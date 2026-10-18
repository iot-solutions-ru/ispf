param(
    [switch]$SkipInstall,
    [switch]$KeepCache
)

# Отладочная веб-консоль ISPF (Vite, Windows, PowerShell).
#
# Запуск из любой папки:
#   .\scripts\run-web-console.ps1
#
# Консоль: http://localhost:5173 (исходники apps\web-console\src)
# API проксируется на http://localhost:8080 (сервер: .\scripts\rebuild-and-run.ps1)
# Остановка: Ctrl+C
#
# Параметры: -SkipInstall (без npm ci), -KeepCache (не чистить node_modules\.vite)
# Перед стартом освобождает порт 5173 (завершает прежний Vite, если окно потеряно).

$ErrorActionPreference = "Stop"

$DevPort = 5173

$Root = Split-Path -Parent $PSScriptRoot
$Console = Join-Path $Root "apps\web-console"
$LockFile = Join-Path $Console "package-lock.json"
$NodeModules = Join-Path $Console "node_modules"
$InstallStamp = Join-Path $NodeModules ".run-web-console-install-stamp"
$ViteCache = Join-Path $NodeModules ".vite"

if (-not (Test-Path (Join-Path $Console "package.json"))) {
    Write-Error "Ne naiden apps\web-console\package.json. Zapuskajte iz klona repozitorija ISPF."
}

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Error "npm ne naiden v PATH. Ustanovite Node.js 20+."
}

function Test-WebConsoleDepsReady {
    return Test-Path (Join-Path $NodeModules ".bin\vite.cmd")
}

function Test-WebConsoleDepsStale {
    if (-not (Test-Path $NodeModules)) { return $true }
    if (-not (Test-Path $LockFile)) { return $false }
    if (-not (Test-Path $InstallStamp)) { return $true }
    $lockTime = (Get-Item $LockFile).LastWriteTimeUtc
    $stampTime = (Get-Item $InstallStamp).LastWriteTimeUtc
    return $lockTime -gt $stampTime
}

function Install-WebConsoleDeps {
    Write-Host "npm ci (web-console)..."
    Set-Location $Console
    npm ci --no-audit
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    New-Item -ItemType Directory -Force -Path $NodeModules | Out-Null
    Set-Content -Path $InstallStamp -Value ((Get-Item $LockFile).LastWriteTimeUtc.ToString("o")) -Encoding ascii
}

function Clear-VitePrebundleCache {
    if (-not (Test-Path $ViteCache)) { return }
    Write-Host "Clear Vite cache: $ViteCache"
    Remove-Item -Recurse -Force $ViteCache
}

function Stop-ListenerOnPort {
    param([int]$Port)

    $pids = [System.Collections.Generic.HashSet[int]]::new()
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            ForEach-Object { [void]$pids.Add($_.OwningProcess) }
    }
    if ($pids.Count -eq 0) {
        $pattern = ":$Port\s"
        netstat -ano | Select-String $pattern | ForEach-Object {
            $line = $_.Line.Trim()
            if ($line -match 'LISTENING\s+(\d+)\s*$') {
                [void]$pids.Add([int]$Matches[1])
            }
        }
    }
    foreach ($procId in $pids) {
        if ($procId -le 0) { continue }
        Write-Host "Port $Port in use by PID $procId - stopping..."
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    }
    if ($pids.Count -gt 0) {
        Start-Sleep -Milliseconds 400
    }
}

if (Get-Command git -ErrorAction SilentlyContinue) {
    Push-Location $Root
    try {
        $branch = git branch --show-current 2>$null
        if ($branch) { Write-Host "Git branch: $branch" }
    } finally {
        Pop-Location
    }
}

Set-Location $Console

if (-not (Test-WebConsoleDepsReady)) {
    if ($SkipInstall) {
        Write-Host "node_modules incomplete (-SkipInstall ignored, running npm ci)..."
    }
    Install-WebConsoleDeps
} elseif (-not $SkipInstall -and (Test-WebConsoleDepsStale)) {
    Install-WebConsoleDeps
} elseif (-not (Test-Path $NodeModules)) {
    Install-WebConsoleDeps
}

if (-not (Test-WebConsoleDepsReady)) {
    Write-Error "npm ci did not install vite. Run manually: cd apps\web-console; npm ci"
}

if (-not $KeepCache) {
    Clear-VitePrebundleCache
}

Stop-ListenerOnPort -Port $DevPort

Write-Host ""
Write-Host "Web console: http://localhost:$DevPort"
Write-Host "For React UI use 5173 only (not 8080 embedded dist)."
Write-Host "After git pull or branch switch restart this script (Ctrl+C, run again)."
Write-Host "API expected at http://localhost:8080"
Write-Host "Stop: Ctrl+C"
Write-Host ""

$viteCmd = Join-Path $NodeModules ".bin\vite.cmd"
& $viteCmd --force
exit $LASTEXITCODE
