> **Язык:** краткое зеркало. Канонические процедуры: [en/cluster-chaos-soak-runbook.md](../en/cluster-chaos-soak-runbook.md).

# Runbook chaos / soak кластера (Wave 6)

> **Статус:** Lab — доказательство кластера воспроизводимыми шагами. Хаб: [doc-status.md](doc-status.md).

Wave 6 risk burn-down: **доказательство кластера** (chaos/soak + усиленный smoke), без изменений промышленных драйверов и без рефакторинга ObjectManager / AI / BPMN.

ADRs: [0028](decisions/0028-horizontal-active-active-cluster.md), [0029](decisions/0029-cluster-live-variable-replica-sync.md), [0030](decisions/0030-cluster-config-structure-replica-sync.md). Гайды: [cluster](cluster.md), [deployment](deployment.md), [testing](testing.md).

## Классы доказательств

| Класс | Смысл |
| ----- | ----- |
| **REAL** | Runtime + автотест + воспроизводимый runbook |
| **PARTIAL** | Основа есть; нет длительности / нагрузки / полевых условий |
| **STUB** | Только доки или stub runtime |

## Что доказывает CI и что — нет

| Утверждение | CI | Лаб / soak |
| ----------- | -- | ---------- |
| JDBC ownership / failover (in-process) | **REAL** (weekly + dispatch) | Повтор под нагрузкой |
| Round-robin REST ≥2, REST 200 при stop реплики | **REAL** (`cluster-smoke-test.sh`; API upstream round-robin в `nginx-cluster.conf`) | То же |
| Kill owner → reclaim ≤ SLO | **REAL** на compose (SLO по умолчанию 45s) | **PARTIAL→REAL** под ingress |
| Config/structure sync (ADR-0030) | **REAL** (`--config-sync`) | То же |
| Согласованность значения переменной через LB (ADR-0029 API-path) | **REAL** (`--live-var-lag`) | **PARTIAL** для telemetry под MQTT |
| Scale-out ≥1.8× | **REAL** при compose gate | Перезамер на железе |
| Kill owner под sustained load / 30–60m soak / multi-day | **Не доказывает** | § в EN runbook |
| Протоколы драйверов / BL-191 | Вне Wave 6 | Field / interop |

## Быстрый прогон

```bash
export ISPF_CLUSTER_REQUIRE_DRIVER_LOCKS=1
bash deploy/cluster-smoke-test.sh --config-sync --live-var-lag
python deploy/cluster-scale-load-test.py --scale-factor-floor 1.8

# Lab soak (не CI): нагрузка + mid-window chaos + journal в deploy/journals/
bash deploy/cluster-soak-lab.sh                  # 30 мин
bash deploy/cluster-soak-lab.sh --duration-min 2 # короткий прогон
```

Полные сценарии chaos, soak-журнал и SLO — только в [английской версии](../en/cluster-chaos-soak-runbook.md).
