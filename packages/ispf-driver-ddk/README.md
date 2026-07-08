# ISPF Driver DDK (BL-144)

Driver Development Kit — шаблоны и документация для внешних и in-repo custom driver packs.

## Быстрый старт

1. Скопируйте `template/` в новый Gradle-модуль, например `packages/ispf-driver-acme-widget/`.
2. Замените `acme-widget`, пакет `com.ispf.driver.acmewidget`, конфиг и point mapping.
3. Реализуйте `readPoints` / `writePoint` и loopback-тест со stub-сервером.
4. Соберите pack: `./gradlew :packages:ispf-driver-acme-widget:assembleDriverPack`.
5. Пройдите promotion gate — [docs/DRIVER_PROMOTION.md](../../docs/DRIVER_PROMOTION.md).

Полная документация: [docs/DRIVER_DDK.md](../../docs/DRIVER_DDK.md).

## Содержимое

| Путь | Назначение |
| ---- | ---------- |
| `template/` | Минимальный driver stub + test + `driver-pack.json` + `build.gradle.kts` |
| `examples/simple-counter/` | Poll-only counter reference driver (BL-144) |
| `examples/json-poller/` | HTTP JSON poller stub (BL-144) |
| `src/main/java/.../DriverDdk.java` | Версия DDK (для smoke-теста модуля) |

## Тесты

```bash
./gradlew :packages:ispf-driver-ddk:test
```

Проверяет наличие template-файлов и компилируемость reference stub в unit-тесте.
