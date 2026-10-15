# Пересборка и запуск платформы ISPF из исходников (Windows, PowerShell).
#
# Запуск из любой папки:
#   .\scripts\rebuild-and-run.ps1
#
# Что получится:
#   API и встроенная H2 — http://localhost:8080
#   Проверка живости — http://localhost:8080/actuator/health
#   База — packages\ispf-server\data\ispf-local.mv.db
#   Вход local-профиля описан в docs\ru\getting-started.md
#
# Этот скрипт не поднимает React-консоль. Отладочная консоль —
# отдельное окно: .\scripts\run-web-console.ps1
# Открыть http://localhost:5173 (Vite ходит в API на порту 8080).
# Gradle встраивает консоль в JAR только при -PembedWebConsole=true и уже
# собранном apps\web-console\dist. Обычный bootRun этого не делает.

$ErrorActionPreference = "Stop"

# Каталог репозитория — родитель папки scripts, независимо от того,
# откуда вызвали скрипт.
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$Gradle = Join-Path $Root "gradlew.bat"
$WrapperJar = Join-Path $Root "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $Gradle)) {
    Write-Error "Не найден gradlew.bat в $Root. Запускайте скрипт из клона репозитория ISPF."
}
if (-not (Test-Path $WrapperJar)) {
    Write-Error "Не найден gradle\wrapper\gradle-wrapper.jar. Используйте Gradle Wrapper из этого репозитория, а не отдельную установку Gradle."
}

# Wrapper качает дистрибутив в GRADLE_USER_HOME. В терминале Cursor часто
# подставляется sandbox-каталог — тогда Gradle «ставится заново» при каждом запуске.
# По умолчанию кэш — .gradle в корне репо (см. .gitignore). Переопределение:
#   $env:ISPF_GRADLE_USER_HOME = 'D:\cache\gradle'
if ($env:ISPF_GRADLE_USER_HOME) {
    $GradleUserHome = $env:ISPF_GRADLE_USER_HOME
} elseif ($env:GRADLE_USER_HOME -and $env:GRADLE_USER_HOME -notmatch 'cursor-sandbox|Cursor') {
    $GradleUserHome = $env:GRADLE_USER_HOME
} else {
    $GradleUserHome = Join-Path $Root ".gradle"
}
New-Item -ItemType Directory -Force -Path $GradleUserHome | Out-Null
$env:GRADLE_USER_HOME = $GradleUserHome

Write-Host "Gradle Wrapper: $Gradle"
Write-Host "Gradle user home: $GradleUserHome"
Write-Host ""

function Invoke-RepoGradle {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$GradleArgs)
    & $Gradle -g $GradleUserHome @GradleArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

# Останавливаем Gradle Daemon.
# На Windows демон держит jar-файлы открытыми. Если их не отпустить,
# следующая очистка build\ не сможет удалить эти файлы.
Invoke-RepoGradle --stop

# Удаляем только результат сборки сервера (packages\ispf-server\build).
# Следующий bootRun заново скомпилирует сервер и подтянет изменившиеся
# зависимости. Корень проекта не чистим: задача clean на корне пересобирает
# все драйверные пакеты, это часы и для локального запуска не нужно.
Invoke-RepoGradle :packages:ispf-server:clean --no-daemon

# Собираем сервер и оставляем его работать в этом окне.
#   :packages:ispf-server:bootRun
#       компилирует сервер и запускает Spring Boot, не собирая отдельный JAR.
#   --no-daemon
#       сервер живёт в этом процессе. Ctrl+C останавливает и Gradle, и платформу.
#   --args=--spring.profiles.active=local
#       профиль local: файловая H2, без PostgreSQL, Keycloak, MQTT и NATS.
# bootRun сам вызывает ensureDevDriverPacks: в build\driver-packs попадает
# короткий набор пакетов для разработки, не весь каталог.
# Полный каталог, если он нужен:
#   .\gradlew.bat :packages:ispf-server:bootRun --args="--spring.profiles.active=local" -Dispf.driver.packs=all
Write-Host ""
Write-Host "Сборка и запуск. API: http://localhost:8080"
Write-Host "Остановка: Ctrl+C в этом окне."
Write-Host ""
Invoke-RepoGradle :packages:ispf-server:bootRun --no-daemon --args="--spring.profiles.active=local"
exit $LASTEXITCODE
