> **Язык:** русская версия (вычитка). Канонический английский: [en/commercial-licensing.md](../en/commercial-licensing.md).

# Лицензирование коммерческого пакета

Коммерческий пакет RSA-лицензирование при развертывании. Архитектурное решение: [0003-commercial-bundle-licensing](decisions/0003-commercial-bundle-licensing.md).

## Принцип

| Слой | Лицензия |
|------|----------|
| Платформа (`ispf-server`, веб-консоль) | **GNU AGPL v3** (+ необязательный [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md)) |
| Пакет драйверов устройств | `licenseType` в упаковке — см. [licensed-driver-packs](licensed-driver-packs.md) |
| Commercial bundle | Optional секция `license` в manifest; verify при deploy |

## Конфигурация сервера

```yaml
ispf:
  license:
    data-dir: ${ISPF_DATA_DIR:./data}
    public-key-pem: ${ISPF_LICENSE_PUBLIC_KEY_PEM:}
    enforce: ${ISPF_LICENSE_ENFORCE:false}
```

| Переменная | Назначение |
|------------|------------|
| `ISPF_DATA_DIR` | Каталог для `.ispf-installation-id` |
| `ISPF_LICENSE_PUBLIC_KEY_PEM` | Поставщик публичного переключателя PEM RSA (возможно несколько подряд блоков PEM для ротации) |
| `ISPF_LICENSE_ENFORCE` | `true` — недействительная лицензия на пакет/драйвер/платформу блокирует развертывание/загрузку пакета/**старт сервера** |

## Идентификатор установки

Файл `{data-dir}/.ispf-installation-id` создаётся при первом старте.

```http
GET /api/v1/platform/installation-id
```

Admin передаёт `installationId` поставщику для выпуска лицензии.

Статус лицензии (admin): `GET /api/v1/platform/license` — режим, уровень, действительность, принудительное исполнение, идентификатор установки. Карточка в веб-консоли: **Система → Метрики**.

## Platform license file (`platform-license.json`)

Файл `{data-dir}/platform-license.json` — Освобождение предприятия от AGPL (см. [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md)).

| Условие | Результат |
|---------|-----------|
| Файл отсутствует | Сообщество (AGPL), начало решения |
| Файл + действительный | Коммерческий уровень активен |
| Файл + invalid + `enforce=false` | WARN в логе, старт разрешён |
| Файл + invalid + `enforce=true` | **Сервер не стартует** (`IllegalStateException`) |

## Формат `license` в bundle

```json
"license": {
  "bundleId": "mes-reference",
  "minPlatformVersion": "0.7.0",
  "installationId": "<hex>",
  "contentSha256": "<sha256 canonical manifest without license>",
  "expiresAt": "2027-12-31T23:59:59Z",
  "signature": "<base64 RSA-SHA256 over canonical claims JSON>"
}
```

`contentSha256` — SHA-256 от manifest **без** поля `license` (canonical JSON, sorted keys).

## Поставщик

CLI: [tools/license-builder/README.md](../../tools/license-builder/README.md).

## Поведение развертывания

| Условие | Результат |
|---------|-----------|
| Нет `license` | Deploy как раньше (если `require-signed-bundles=false`) |
| Нет `license` + `require-signed-bundles=true` | HTTP 403 ([roadmap](roadmap.md)) |
| `license` + `enforce=false` + недействительно | ПРЕДУПРЕЖДАЕМ: если нужны, развертывание продолжается (кроме `require-signed-bundles=true` → 403) |
| `license` + (`enforce=true` **или** `require-signed-bundles=true`) + invalid | HTTP 403 |

Property: `ispf.license.require-signed-bundles` / env `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES`. См. [DEPLOYMENT.md § Bundle signing](deployment.md).

## Защита IP после развертывания (сбалансированная политика)

Лицензия RSA на **артефакт доставки** (манифест), не соблюдайте требования дерева после установки. Админ установки может видеть и дорабатывать пакет объектов; идея может изменить конфигурацию по частям.

**Принятая политика (ADR [0036-bundle-ip-balanced-protection](decisions/0036-bundle-ip-balanced-protection.md)):**

| Делаем | Не делать |
|--------|-----------|
| Привязка deploy к `installationId` | Блокировка export / pull-from-tree |
| EULA, маркетплейс, активация | Шифрование JSON в дереве |
| Ценность в обновлениях и поддержке | Запрет доработки под объект |
| Пользовательский интерфейс: ID установки, подсказки при Нужны деньги | Жёсткий DRM для оператора/администратора |

Копирование декларативной конфигурации on-prem **полностью не предотвращает** без нагрузки для кастомизации; контроль — договор + лицензия на доставку + периодическая стоимость.

## Ротация производственного ключа (ops)

Ротация RSA-ключей поставщика **без** смены ID установки:

| Шаг | Действие |
|-----|----------|
| 1 | Сгенерировать новую пару ключей (`tools/license-builder/`); сохранить старый закрытый ключ до конца льготного периода |
| 2 | На платформе: задеплоить **оба** открытый ключ в `ISPF_LICENSE_PUBLIC_KEY_PEM` (несколько `-----BEGIN PUBLIC KEY-----` блоков в одной переменной); подпись принимается, если совпала с любым ключом |
| 3 | Перевыпустить коммерческий пакет / подписи пакетов драйверов для активных клиентов |
| 4 | Льготный период (рекомендуется ≥30 дней): повторное подключение ещё допускается только в том случае, если открытый ключ не меняли; после замены ключей старых лицензий **невалидны** — планировать обслуживание окон |
| 5 | `enforce=true` на staging до prod; мониторить WARN/403 в deploy logs |
| 6 | Уничтожить старый закрытый ключ после подтверждения, что все установки на новых лицензиях |

Идентификатор установки (`GET /api/v1/platform/installation-id`) при ротации **не меняется**. Пакеты лицензионных драйверов используют тот же `ispf.license.public-key-pem` — см. [licensed-driver-packs](licensed-driver-packs.md).

## Связанные документы

- [plugins](plugins.md)
- [solution-developer-public-api](solution-developer-public-api.md)
- [air-gap-deployment](air-gap-deployment.md) — автономная установка/обновление (BL-128)
