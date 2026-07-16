> **Язык:** русская версия (вычитка). Канонический английский: [en/license-compliance.md](../en/license-compliance.md).

# Соответствие лицензии (инженерный контрольный список)

Инженерные процедуры для выпусков ISPF. **Не юридическая консультация** — требуется проверка адвоката
для коммерческих контрактов и перераспределения активов третьих сторон.

## Режимы лицензии платформы

| Режим | Когда | Обязательства |
|------|------|-------------|
| **Сообщество (AGPL)** | По умолчанию; нет `platform-license.json` | Использование сети → Исходное предложение AGPL для модификаций **платформы** |
| **Предприятие** | Действительно `platform-license.json` | Согласно [LICENSE-COMMERCIAL](../../LICENSE-COMMERCIAL.md) |

Check runtime: `GET /api/v1/platform/license`

## Бинарный дистрибутив

Поставляется с каждым выпуском:

1. [LICENSE](../../LICENSE) (AGPL)
2. [NOTICE](../../NOTICE)
3. [third-party-notices](third-party-notices.md)
4. Java + npm SBOM (рекомендуется CycloneDX)
5. Для каждого пакета драйверов: `LICENSE`, `THIRD_PARTY-NOTICE.txt`, `NOTICE-EXTERNAL-DEPS.txt` (если есть)

Web console static files include `legal/*` (copied at build via `scripts/copy-legal-assets.mjs`).

## Профили развертывания пакетов драйверов

| Профиль | Вариант использования |
|---------|----------|
| `permissive` (**default** for VPS deploy) | Apache-2.0 + public-domain packs only |
| `all` | Full monorepo build including GPL/LGPL/MPL/StepFunc-restricted |

Развертывание VPS:

```powershell
.\deploy\vps-deploy-direct.ps1 -Version 0.9.32 -SkipTests -DriverPackProfile permissive
```

Пакеты с авторским левом или с ограничением StepFunc требуют отдельной юридической проверки до `-DriverPackProfile all`.

## Ограниченные пакеты (не в разрешительном профиле)

| Пакет | тип лицензии | Заметки |
|------|-------------|-------|
| `ispf-driver-bacnet` | GPL-3.0-only | bacnet4j |
| `ispf-driver-dlms` | GPL-2.0-only | Gurux |
| `ispf-driver-iec104*` | GPL-3.0-or-later | j60870 |
| `ispf-driver-mbus` | MPL-2.0 | jMBus |
| `ispf-driver-radius` | LGPL-3.0-or-later | TinyRadius |
| `ispf-driver-ipmi` | GPL-3.0-or-later | vxIPMI (Verax) |
| `ispf-driver-dnp3` | LicenseRef-StepFunc-Некоммерческая | **io.stepfunc:dnp3 не входит в комплект** — см. пакет `NOTICE-EXTERNAL-DEPS.txt` |

## Пакет символов P&ID

Исходные функциональные символы ISA/ISO — **Apache-2.0**, созданные [`tools/symbol-pack-isa`](../../tools/symbol-pack-isa).

- [license](license.md)
- [pid-symbols-legal](pid-symbols-legal.md)

## Предварительный аудит (автоматический)

```bash
node tools/license-audit/check-all.mjs
cd apps/web-console && npm ci && npm run build
./gradlew syncAllDriverPacks
```

CI runs `check-all.mjs` on every push/PR.

## водяной знак bpmn-js

Редактор/просмотрщик рабочего процесса BPMN должен сохранять водяной знак bpmn.io видимым. CSS в
`apps/web-console/src/styles.css` обеспечивает видимость; проверьте вручную в пользовательском интерфейсе перед выпуском.

## Связанный

- [license](license.md)
- [third-party-notices](third-party-notices.md)
- [licensed-driver-packs](licensed-driver-packs.md)
- [commercial-licensing](commercial-licensing.md)
