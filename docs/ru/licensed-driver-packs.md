> **Язык:** русская версия (вычитка). Канонический английский: [en/licensed-driver-packs.md](../en/licensed-driver-packs.md).

# Пакеты лицензионных драйверов (FW-50)

Все драйверы устройств ISPF поставляются в виде **пакетов драйверов** (1 драйвер = 1 пакет = 1 `licenseType`).  
JAR платформы содержит только загрузчик пакета, а не реализации протокола.

## Макет

```text
${ISPF_DRIVER_PACKS_DIR}/
  acme-opc-premium/
    driver-pack.json
    acme-opc-premium.jar
```

## Сборка (монорепо)

```bash
./gradlew syncAllDriverPacks
```

Output: `build/driver-packs/<packId>/` with `driver-pack.json`, `LICENSE`, and JAR.

Источник каталога: [gradle/driver-packs.json](../gradle/driver-packs.json) (восстановить через `python tools/generate-driver-packs-json.py`).

## Настроить

```yaml
ispf:
  driver:
    packs-dir: ${ISPF_DRIVER_PACKS_DIR:./data/drivers}
  license:
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

## `driver-pack.json`

| Поле | Требуется | Описание |
|-------|----------|-------------|
| `packId` | yes | Unique pack identifier |
| `driverId` | yes | Runtime driver id (e.g. `modbus-tcp`) |
| `licenseType` | yes | SPDX id: `Apache-2.0`, `GPL-3.0-only`, `LGPL-3.0-or-later`, `MPL-2.0`, `LicenseRef-StepFunc-NonCommercial`, … |
| `externalDependencies` | when third-party JAR is not bundled | e.g. StepFunc DNP3 — see `NOTICE-EXTERNAL-DEPS.txt` |
| `minPlatformVersion` | yes | Semver gate (e.g. `0.9.32`) |
| `jarFile` | yes | JAR filename relative to pack directory |
| `drivers[]` | yes | `driverId` + `driverClass` |
| `license` | when `enforce=true` for commercial packs | RSA-signed claims (see below) |

Пример:

```json
{
  "packId": "acme-opc-premium",
  "minPlatformVersion": "0.7.5",
  "jarFile": "acme-opc-premium.jar",
  "drivers": [
    {
      "driverId": "acme-opc-premium",
      "driverClass": "com.acme.driver.OpcPremiumDeviceDriver"
    }
  ],
  "license": {
    "packId": "acme-opc-premium",
    "minPlatformVersion": "0.7.5",
    "installationId": "<from GET /api/v1/platform/installation-id>",
    "jarSha256": "<sha256 of JAR bytes>",
    "expiresAt": "2027-12-31T23:59:59Z",
    "signature": "<base64 RSA-SHA256 over canonical license payload>"
  }
}
```

License signing payload fields (sorted JSON): `packId`, `minPlatformVersion`, `installationId`, `jarSha256`, `expiresAt`.

## Поведение во время выполнения

1. При запуске `LicensedDriverPackLoader` сканирует каждый подкаталог на наличие `driver-pack.json`.
2. Когда присутствует `license`, `DriverPackLicenseVerifier` проверяет подпись, установку, срок действия и хеш JAR.
3. Действительные пакеты регистрируются в `LicensedDriverRegistry`; `DriverFactory` и `DriverCatalog` объединяют лицензированные драйверы.
4. Недействительная или отсутствующая лицензия: пакет пропущен + **WARN** (`enforce=false`) или пропущен + журнал **ERROR** (`enforce=true`).

## SPI-контракт

- JAR must implement `com.ispf.driver.DeviceDriver`.
- Prefer explicit `drivers[]` with `driverClass` for predictable `driverId`.
— Путь продвижения для заглушек в дереве: [driver-promotion](driver-promotion.md).

## Развертывание профилей

Для развертывания производственного VPS по умолчанию используется профиль **`permissive`** (исключая пакеты с авторским левом и пакетами с ограничением StepFunc). См. [license-compliance](license-compliance.md).

## Связанный

- [commercial-licensing](commercial-licensing.md) — RSA keys and `tools/license-builder/`
- [drivers](drivers.md) — древовидный каталог.
- [ROADMAP.md § Часть B (FW-50)](roadmap.md)
