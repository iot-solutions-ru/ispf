> **Язык:** русская версия (вычитка). Канонический английский: [en/decisions/0003-commercial-bundle-licensing.md](../../en/decisions/0003-commercial-bundle-licensing.md).

# ADR-0003: Лицензирование commercial bundle

Статус: **Принято**  
Дата: 2026-06-22

> **Уточнение по лицензии ядра:** платформа — **AGPL v3** (+ опционально Enterprise) — [0016-agpl-dual-licensing](0016-agpl-dual-licensing.md). Этот ADR по-прежнему про **RSA-привязку bundle / driver pack**.

## Контекст

Платформа ISPF поставляется под AGPL (community) без DRM на ядровых движках. Коммерческие решения (bundle, driver pack) поставляются отдельно; нужна проверяемая привязка к установке без лицензирования каждого deploy как «лицензия ядра».

## Решение

1. **Ядро** не лицензируется; optional verify при `POST .../deploy`, если в manifest есть секция `license`.
2. **Installation ID** — файл `{data-dir}/.ispf-installation-id` (hex); генерируется при первом старте; передаётся поставщику для выпуска лицензии.
3. **Формат `license` в bundle:** `bundleId`, `minPlatformVersion`, `installationId`, `contentSha256`, `expiresAt`, `signature` (RSA-SHA256 over canonical claims JSON).
4. **`contentSha256`** — SHA-256 canonical JSON manifest **без** поля `license`.
5. **Публичный ключ** — в конфиге platform (`ispf.license.public-key-pem`); закрытый — у поставщика (`tools/license-builder/`).
6. **`ispf.license.enforce`:** `true` — invalid license → HTTP 403; `false` — WARN (local/dev).
7. Bundle **без** `license` — deploy без изменений (открытые reference apps).

## Последствия

- Реализация: `com.ispf.server.license.*`, [commercial-licensing](../commercial-licensing.md).
- [plugins](../plugins.md) — требования к поставке commercial bundle.

## Связанные материалы

- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- REQ-FW-10, FW-11 в [roadmap](../roadmap.md)
