> **Язык:** русская версия. Канонический английский: [en/known-limitations.md](../en/known-limitations.md).

# Известные ограничения и non-claims

> **Статус:** Stable — хаб честности. Теги: [doc-status](../en/doc-status.md).

Краткая выжимка; полный перечень таблиц — в каноне [en/known-limitations.md](../en/known-limitations.md). Читать вместе с [competitive-scorecard](competitive-scorecard.md) и [parked-backlog](parked-backlog.md).

Политика: [roadmap — качество вместо фич](roadmap.md#политика-исполнения--качество-вместо-фич).

---

## Дефолты и гигиена оператора

| Тема | Реальность |
|------|------------|
| Учётные данные по умолчанию | Demo / getting-started могут использовать `admin` / `admin`. **Смените до любой сетевой экспозиции** — [security](security.md), [deployment](deployment.md). |
| Unsigned bundles / RBAC off / WS origins `*` | Вне `prod` — обычно только **warn**. В `application-prod.yml` — `strict-startup=true` (отказ старта при RBAC off / role header / WS `*`). Unsigned bundles warn, пока не `require-signed-bundles=true`. Helm/`dev` могут не грузить `prod` — [security](security.md). |
| Java-функции | Держите `ISPF_FUNCTION_JAVA_ENABLED=false`, пока не принимаете sandbox gaps ([ADR-0045](../en/decisions/0045-java-function-sandbox.md)). |

---

## Историк

| Заявление | Статус |
|-----------|--------|
| JDBC + dual-write catch-up + sole-store ClickHouse | **Отгружено** — [variable-history](variable-history.md), [ADR-0035](../en/decisions/0035-historian-dual-write.md) |
| Локальный disk spool перед sole-store CH (`ispf.variable-history.spool`) | **Отгружено**, **выкл. по умолчанию** — [variable-history](variable-history.md) § Disk spool |
| Dual-write catch-up gauges + lab Prometheus alert rules | **Отгружено** — `…catchup.ranges` / `.oldest_age_ms` (возраст open range по sample clock) / `.wal_bytes`; это не CH-vs-PG lag ([ADR-0035](../en/decisions/0035-historian-dual-write.md)) |
| Настоящий метрик lag ClickHouse vs primary | **Открытый** — не трактовать `oldest_age_ms` как CH lag |
| Вторая копия буфера историана на другом узле ISPF | **Отложено** — не делать без явной просьбы |
| ISPF создаёт / управляет кластером ClickHouse | **Отложено** — репликацией CH владеет оператор |
| Parquet / Cassandra-Scylla `quality` наравне с flagship | **Gap / experimental** — [ADR-0074](../en/decisions/0074-historian-quality-persistence.md) |

---

## Безопасность и trust

| Заявление | Статус |
|-----------|--------|
| TOTP MFA (BL-153) | **Done** |
| WebAuthn / passkeys / IdP OTP (BL-194) | **Parked** — [ADR-0056](../en/decisions/0056-webauthn-idp-mfa.md), [parked-backlog](parked-backlog.md) **P-WEBAUTHN** |
| Внешний KMS / HSM / Vault / GOST | **Не заявлено** до тендера — [ADR-0076](../en/decisions/0076-external-secrets-providers.md) |
| Сторонний pen-test (G-01) | **Parked** — [parked-backlog](parked-backlog.md) **P-PENTEST** |
| Cosign на хосте заказчика / отдельный origin ui-pack | Lab tooling ≠ customer-host Done |

---

## Field и интеграции (parked)

Снимать с паузы только с **именованной** площадкой / заказчиком / вендором — см. [parked-backlog](parked-backlog.md).

## OT / драйверы (кратко)

DNP3 — **PRODUCTION** `POLL`+`WRITE` (CROB latch + analog float; не полный control profile). `opc-da` / `opc-bridge` — **BETA**. Полная таблица: [en/known-limitations.md](../en/known-limitations.md) § OT / drivers.

---

## Связанное

- [competitive-scorecard](competitive-scorecard.md)
- [parked-backlog](parked-backlog.md)
- [adr-tension-resolution](../en/adr-tension-resolution.md)
- [anti-patterns](anti-patterns.md)
