> **Язык:** русская версия. Английский оригинал: [en/licensed-driver-packs.md](../en/licensed-driver-packs.md).

# Пакеты драйверов устройств (`driver-pack`) и проверка их лицензий

> **Статус:** Stable — структура внешних пакетов драйверов в каталоге `ISPF_DRIVER_PACKS_DIR`, паспорт `driver-pack.json` и проверка цифровой подписи. Метки зрелости: [doc-status.md](doc-status.md).

Все протокольные драйверы в ISPF оформлены как отдельные модульные пакеты (**1 драйвер = 1 пакет = 1 явно указанная лицензия `licenseType`**). Базовый JAR-файл сервера содержит только универсальный загрузчик `LicensedDriverPackLoader`, что позволяет подключать нужные протоколы из каталога драйверов и соблюдать лицензионную чистоту поставки.

---

## 1. Структура каталога пакетов драйверов

```text
${ISPF_DRIVER_PACKS_DIR}/
  acme-opc-premium/
    driver-pack.json
    acme-opc-premium.jar
    LICENSE
```

Собрать все пакеты драйверов из исходников репозитория:

```bash
./gradlew syncAllDriverPacks
```

Готовые папки пакетов формируются в `build/driver-packs/<packId>/` на основе реестра [`gradle/driver-packs.json`](../../gradle/driver-packs.json).

---

## 2. Настройка сервера (`application.yml`)

```yaml
ispf:
  driver:
    packs-dir: ${ISPF_DRIVER_PACKS_DIR:./data/drivers}
  license:
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

---

## 3. Паспорт пакета драйвера (`driver-pack.json`)

| Поле | Обязательно | Описание |
|------|:-----------:|----------|
| `packId` | да | Уникальный идентификатор пакета |
| `driverId` | да | Идентификатор драйвера в системе (например, `modbus-tcp`) |
| `licenseType` | да | Код лицензии SPDX (`Apache-2.0`, `MPL-2.0`, `LGPL-3.0-or-later` или коммерческая ссылка) |
| `minPlatformVersion` | да | Минимальная совместимая версия сервера ISPF |
| `jarFile` | да | Имя JAR-файла внутри папки пакета |
| `drivers[]` | да | Список классов, реализующих `com.ispf.driver.DeviceDriver` (`driverId` + `driverClass`) |
| `license` | для коммерческих пакетов | Блок RSA-подписи (обязателен при `ISPF_LICENSE_ENFORCE=true`) |

Пример `driver-pack.json`:

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
    "installationId": "<значение из GET /api/v1/platform/installation-id>",
    "jarSha256": "<sha256-хэш файла JAR>",
    "expiresAt": "2027-12-31T23:59:59Z",
    "signature": "<подпись RSA-SHA256 в base64>"
  }
}
```

---

## 4. Как сервер загружает пакеты при старте

1. При запуске `LicensedDriverPackLoader` обходит все подпапки в `ISPF_DRIVER_PACKS_DIR`, содержащие `driver-pack.json`.
2. Если в паспорте указан блок `license`, проверяется цифровая подпись, совпадение `installationId`, срок действия и контрольная сумма `jarSha256` самого JAR-файла.
3. Прошедшие проверку драйверы регистрируются в `LicensedDriverRegistry` и сразу становятся доступны в общем каталоге `GET /api/v1/drivers`.
4. Для промышленных поставок по умолчанию используется профиль `permissive`, включающий только пакеты со свободными разрешительными лицензиями (`Apache-2.0`, `MIT`, `BSD`, `EPL`, `MPL-2.0` — подробнее в разделе **[Лицензионная чистота](license-compliance.md)**).

**Связанные разделы:** [Справочник драйверов](drivers.md) · [SDK разработки драйверов (DDK)](driver-ddk.md) · [Коммерческое лицензирование](commercial-licensing.md).
