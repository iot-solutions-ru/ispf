> **Язык:** русская версия. Канон: [en/release-upgrade.md](../en/release-upgrade.md).

# Совместимость релизов / upgrade bundle

> **Статус:** Lab gate (ADR-0064 волна 2). Hub: [doc-status.md](doc-status.md).

Что ISPF **доказывает** сегодня про upgrade решений и платформы — и чего **не** заявляет.

Политика: [0064-release-compatibility-lts](decisions/0064-release-compatibility-lts.md). Поле wire: `manifest.formatVersion` (нет поля ⇒ `1`). Semver продукта: `manifest.version` — [applications](applications.md).

## Доказано в CI / тестах (lab)

| Гейт | Proof | Честность |
|------|-------|-----------|
| Accept current format | `BundleFormatVersionTest` | Только lab |
| Reject unsupported format | `formatVersion: 99` → ERROR | Только lab |
| Catalog pilot validate + redeploy | `BundleUpgradeCompatGateTest` (`marketplace-demo`) | Lab H2 / MockMvc — **не** production rolling upgrade |

Evidence: [release-compat/2026-10-12-bundle-upgrade-lab-gate.md](../evidence/release-compat/2026-10-12-bundle-upgrade-lab-gate.md).

## Ещё не доказано

| Разрыв | Почему |
|--------|--------|
| **Cluster image N → N+1** | Нужны два образа + shared DB + soak — follow-up ADR-0064 |
| Plant upgrade journals | Field evidence |
| `formatVersion: 2` dual-read | Бампа формата ещё нет |

Не заявлять Deploy/scale 10/10 или «zero-downtime upgrade» из этого lab gate.

## Связанное

- [0064](decisions/0064-release-compatibility-lts.md), [applications](applications.md), [deployment](deployment.md), [marketplace](marketplace.md)
