> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0036-bundle-ip-balanced-protection.md](../../en/decisions/0036-bundle-ip-balanced-protection.md).

# ADR-0036: Защита IP commercial bundle — сбалансированная политика

Статус: **Принято**  
Дата: 2026-07-07

## Контекст

RSA-лицензия на manifest ([0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md)) привязывает **доставку** bundle к `installationId`, но после deploy конфигурация живёт в object tree ([0007-bundle-tree-packaging](0007-bundle-tree-packaging.md), [application-principles](../application-principles.md) P1). Администратор установки видит дашборды, мнемосхемы, functions, bindings и теоретически может воспроизвести решение по частям или через export / pull-from-tree.

Нужна явная продуктовая политика: сколько защищать runtime-конфигурацию vs насколько допускать доработку под объект.

## Решение

**Сбалансированная модель (soft IP protection).** Не вводить жёсткий DRM на дереве объектов.

### Что защищаем

| Слой | Механизм | Цель |
|------|----------|------|
| **Доставка** | RSA `license` в manifest, `installationId`, `contentSha256` | Нельзя перенести подписанный артефакт на чужой сервер без новой лицензии |
| **Коммерция** | EULA, маркетплейс, activation code, entitlement | Юридический и бизнес-контроль перепродажи |
| **Ценность** | Обновления bundle, поддержка, отраслевой контент | Копия v1.0 без vendor path устаревает |

### Что не делаем (осознанно)

- Не блокируем export / pull-from-tree / subtree export для licensed приложений.
- Не шифруем и не обфусцируем declarative JSON в дереве.
- Не запрещаем доработку объектов bundle на установке клиента (bindings, HMI, variables, workflows).
- Не вводим «operator-only» скрытие конфигурации от integrator/admin — on-prem admin владеет сервером.

Полная техническая защита от копирования declarative-конфигурации **недостижима и нежелательна** для tree-first платформы: ломает кастомизацию, аудит, AI-assisted editing и нормальную практику SCADA/MES-интеграции.

### Опционально (низкий приоритет, без enforce по умолчанию)

- **Provenance** — при deploy помечать объекты metadata (`bundleId`, `bundleVersion`) для аудита и support, не для блокировки.
- **UI hints** — installation ID и подсказки при ошибке лицензии в панели bundle (web-console).

Жёсткий **export gate** (`restrict-bundle-export`) — **не планируется** без отдельного ADR и запроса от legal/commercial.

## Последствия

- Поставщик commercial bundle опирается на: deploy-license + договор + обновления, а не на «чёрный ящик» в дереве.
- Клиент с admin-доступом может копировать и дорабатывать конфигурацию **в рамках своей установки**; перенос на другой site без новой лицензии остаётся нарушением EULA, а не технически невозможным действием.
- Платформа не раздувается DRM-слоем; фокус — доставка и marketplace.

## Связанные материалы

- [0003-commercial-bundle-licensing](0003-commercial-bundle-licensing.md) — RSA при deploy
- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md) — bundle = упаковка дерева
- [commercial-licensing](../commercial-licensing.md)
- [LICENSE-COMMERCIAL](../../../LICENSE-COMMERCIAL.md)
