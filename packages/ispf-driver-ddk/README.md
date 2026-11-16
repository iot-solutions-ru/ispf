# ISPF Driver DDK (BL-144)

Driver Development Kit — шаблоны и документация для внешних и in-repo custom driver packs.

## Быстрый старт

1. Из корня репозитория: `./gradlew publishDdkApi` (кладёт `com.ispf:ispf-core` и `com.ispf:ispf-driver-api` в `~/.m2`).
2. Скопируйте `template/` в свой каталог. `ispfVersion` в его `gradle.properties` равен `version` опубликованной сборки.
3. В копии: `gradle test`, затем `gradle assembleDriverPack`. Каталог пакета — `build/driver-pack/ispf-driver-acme-widget/`.
4. Замените `acme-widget`, пакет и point mapping. `jarFile` в `driver-pack.json` совпадает с именем jar.
5. Promotion, если драйвер возвращается в этот репозиторий — [docs/en/driver-promotion.md](../../docs/en/driver-promotion.md). Модуль внутри дерева — отдельный `packages/ispf-driver-*` и строка в `gradle/driver-packs.json`, не регистрация `template/` в корневом `settings.gradle.kts`.

Полная документация: [docs/en/driver-ddk.md](../../docs/en/driver-ddk.md).

## Содержимое

| Путь | Назначение |
| ---- | ---------- |
| `template/` | Минимальный driver stub + test + `driver-pack.json` + `build.gradle.kts` |
| `examples/simple-counter/` | Poll-only counter reference driver (BL-144) |
| `examples/json-poller/` | HTTP JSON poller (BL-144) |
| `examples/modbus-simulator/` | In-memory Modbus register simulator (BL-144 wave 8) |
| `src/main/java/.../DriverDdk.java` | Версия DDK (для smoke-теста модуля) |

## Тесты

```bash
./gradlew :packages:ispf-driver-ddk:check
```

Проверяет template, reference drivers (в т.ч. HTTP loopback для json-poller) и собирает packs в `build/ddk-example-packs/`.
