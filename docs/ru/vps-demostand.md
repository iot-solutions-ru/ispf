> **Язык:** русская версия (вычитка). Канонический английский: [en/vps-demostand.md](../en/vps-demostand.md).

# Демостанд VPS (пример хоста)

> **Обобщённое руководство:** [DEMOSTANDS.md](demostands.md) — производство, пропускная способность, демо-простой, край.

Эта страница — **операционные заметки** для одного из prod-примеров: одноузлового Docker для nginx. Используйте как шаблон; имена контейнеров и хостов замените на свои.

## Профиль

**Демо/холостой** из [DEMOSTANDS.md § Демо/холостой](demostands.md):

- `ISPF_CLUSTER_ENABLED=false`, unified role `all`
- Overlay [`deploy/ispf-server.prod-idle.env`](../deploy/ispf-server.prod-idle.env)
- 2 РАБОТАЮЩИХ драйвера (остальные ОСТАНОВЛЕНЫ): SNMP `TELEMETRY_ONLY`, один виртуальный `FULL` для демонстрационной автоматизации.

## Типовая топология (одноузловой Docker)

```text
Internet → nginx :8080
              └→ ISPF JVM :8081 (host network)
              PostgreSQL, Redis
```

Compose: [`deploy/docker-compose.vps-single.yml`](../deploy/docker-compose.vps-single.yml).

## Операции

| Действие | Скрипт |
|----------|--------|
| Применить idle env + recreate | [`deploy/vps-apply-prod-idle-env.sh`](../deploy/vps-apply-prod-idle-env.sh) |
| Установить драйверы через API | [`deploy/vps-demostand-tune-drivers.sh`](../deploy/vps-demostand-tune-drivers.sh) |
| Полный rollout jar+UI | [`deploy/vps-single-rollout.sh`](../deploy/vps-single-rollout.sh) |
| Диагностика потоков | [`deploy/vps-idle-thread-sample.py`](../deploy/vps-idle-thread-sample.py) |
| Сборка + SCP staging | [`deploy/vps-deploy-direct.ps1`](../deploy/vps-deploy-direct.ps1) |

**Важно:** на Docker-стенде не используйте [`apply-platform-update.sh`](../deploy/apply-platform-update.sh) — он для systemd. После смены env — воссоздать контейнер, а не `docker restart`.

Миграция с multi-replica: `vps-single-rollout.sh`. Обратно на кластер: [CLUSTER.md](cluster.md).

## Версии с исправлениями hot path

| Версия | Исправление |
|--------|-------------|
| ≥ 0.9.100 | `AlarmShelfService` — read-only hot path |
| ≥ 0.9.101 | `ScheduleObjectService.listEnabled()` — без write в read-only tx |

См. [ADR-0033](decisions/0033-prod-idle-demostand-tuning.md).
