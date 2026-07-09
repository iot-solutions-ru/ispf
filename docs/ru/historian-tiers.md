> **Язык:** русская версия (вычитка). Канонический английский: [en/historian-tiers.md](../en/historian-tiers.md).

# Уровни историка (BL-159)

Готовый архивный профиль **горячий → теплый → холодный** для развертываний ISPF. Конфигурация находится в `application.yml` под `ispf.historian.tiers`; Обеспечение многоуровневой маршрутизации является последующей работой (BL-160+).

**См. также:** [VARIABLE_HISTORY.md](variable-history.md), [decisions/0035-historian-dual-write.md](decisions/0035-historian-dual-write.md), [DEPLOYMENT.md](deployment.md).

---

## Модель уровня

| Уровень | Магазин по умолчанию | Удержание | Роль |
|------|---------------|-----------|------|
| **горячий** | PostgreSQL/Шкала времени (`jdbc`) | 7 дней | Живые записи, тенденции операторов, последние агрегаты |
| **теплый** | КликХаус | 90 дней | Аналитика, дашборды дальнего действия, цель двойной записи |
| **холодно** | S3 + Паркет (`cold`) | 10 лет | Архив соответствия, источник массового экспорта |

Горячий уровень может включать **двойную запись** для разогрева ClickHouse (`dual-write-enabled: true`) без изменения пути чтения (все еще JDBC до переключения).

---

## Конфигурация по умолчанию

```yaml
ispf:
  historian:
    deploy-profile: three-tier
    tiers:
      hot:
        store: jdbc
        retention-days: 7
        min-interval-ms: 5000
        dual-write-enabled: true
      warm:
        store: clickhouse
        retention-days: 90
        clickhouse:
          url: http://localhost:8123
          database: ispf
          table: variable_samples
      cold:
        store: cold
        retention-days: 3650
        cold:
          provider: s3
          bucket: ispf-historian-archive
          prefix: variable-samples/
          format: parquet
```

Java binding: `HistorianTierProperties` + `HistorianTierProfile` (`com.ispf.server.config`).

---

## Развертывание фрагмента профиля (prod/VPS)

**трехуровневый** профиль производства в один клик для создания архива данных в любом масштабе. Подайте заявку после проверки ClickHouse (`vps-clickhouse-verify.sh`).

```bash
# /opt/ispf/config/runtime-settings.properties (or env on systemd unit)
ISPF_HISTORIAN_DEPLOY_PROFILE=three-tier
ISPF_VARIABLE_HISTORY_STORE=jdbc
ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=true
ISPF_VARIABLE_HISTORY_RETENTION_DAYS=7
ISPF_VARIABLE_HISTORY_CLICKHOUSE_URL=http://127.0.0.1:8123
ISPF_VARIABLE_HISTORY_CLICKHOUSE_DATABASE=ispf
ISPF_HISTORIAN_TIER_HOT_RETENTION_DAYS=7
ISPF_HISTORIAN_TIER_WARM_RETENTION_DAYS=90
ISPF_HISTORIAN_TIER_COLD_BUCKET=ispf-historian-archive
ISPF_HISTORIAN_TIER_COLD_PREFIX=variable-samples/
```

**Lab-only (single tier):** set `ISPF_HISTORIAN_DEPLOY_PROFILE=hot-only`, `ISPF_VARIABLE_HISTORY_DUAL_WRITE_ENABLED=false`, omit cold bucket.

**Переключение горячего запроса:** установите `ISPF_VARIABLE_HISTORY_STORE=clickhouse` только после выдержки двойной записи и лабораторного шлюза SLO — см. [VARIABLE_HISTORY.md § SLO](variable-history.md).

---

## Контрольный список операций

1. Включите гипертаблицу шкалы времени на `variable_samples` (автоматически при наличии расширения).
2. Разверните ClickHouse; запустить `vps-clickhouse-verify.sh`.
3. Установите вышеперечисленные переменные среды горячей двойной записи + уровня хранения.
4. Отслеживайте ошибки добавления CH в журналы сервера (рекомендуется вторичная запись).
5. Запланируйте задание экспорта холодного уровня (BL-163), когда требуется архив Parquet.

---

## Соответствующая дорожная карта

| БЛ | Тема |
|----|-------|
| БЛ-159 | Этот документ + конфигурация уровня |
| БЛ-161 | SLO запроса — [VARIABLE_HISTORY.md](variable-history.md) |
| БЛ-160+ | AF-lite → AF-capable — [analytics-platform-roadmap.md](analytics-platform-roadmap.md) (БЛ-200…210) |
| БЛ-162 | Журнал событий CH переключение |
| БЛ-163 | Тенденция экспорта CSV/Паркет оптом |
