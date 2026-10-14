# Отладочная веб-консоль ISPF (Vite, Windows, PowerShell).
#
# Запуск из любой папки:
#   .\scripts\run-web-console.ps1
#
# Консоль: http://localhost:5173
# Остановка: Ctrl+C в этом окне.
#
# Это режим разработки: Vite отдаёт исходники React и пересобирает страницу
# при сохранении файла. Сборка dist\ и встраивание в JAR здесь не выполняются.
#
# API консоль сама не поднимает. Запросы /api и /ws Vite пересылает на
# http://localhost:8080 (см. server.proxy в apps\web-console\vite.config.ts).
# Сервер перед этим запускают отдельно: .\scripts\rebuild-and-run.ps1

$ErrorActionPreference = "Stop"

# Каталог репозитория — родитель папки scripts.
$Root = Split-Path -Parent $PSScriptRoot
$Console = Join-Path $Root "apps\web-console"

if (-not (Test-Path (Join-Path $Console "package.json"))) {
    Write-Error "Не найден apps\web-console\package.json. Запускайте скрипт из клона репозитория ISPF."
}

# npm должен быть в PATH (Node.js 20+).
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    Write-Error "npm не найден в PATH. Установите Node.js 20 или новее и откройте терминал заново."
}

Set-Location $Console

# Зависимости ставим только если каталога node_modules ещё нет.
# npm ci ставит версии ровно из package-lock.json, без обновления пакетов.
# Повторный запуск этот шаг пропускает.
if (-not (Test-Path (Join-Path $Console "node_modules"))) {
    Write-Host "Зависимости консоли ещё не установлены. Выполняю npm ci."
    npm ci
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# npm run dev запускает Vite и не возвращает управление, пока процесс жив.
# Порт 5173 задан в vite.config.ts. Если порт занят, Vite сообщит об этом сам.
Write-Host ""
Write-Host "Отладочная консоль: http://localhost:5173"
Write-Host "API ожидается на http://localhost:8080"
Write-Host "Остановка: Ctrl+C в этом окне."
Write-Host ""
npm run dev
exit $LASTEXITCODE
