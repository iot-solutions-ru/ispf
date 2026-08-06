# ADR-0055: Пробелы платформы для NMS / мониторинга ИТ-инфраструктуры

## Статус

**Proposed** (2026-08-06) — По приёмке `it-infra-monitoring` (Трасса М11) и ui-pack `m11-monitor-ui` ([PR #69](https://github.com/iot-solutions-ru/ispf/pull/69)). Канонический текст: [en/decisions/0055-itm-nms-platform-gaps.md](../../en/decisions/0055-itm-nms-platform-gaps.md).

## Контекст

Большая часть ТЗ NMS закрывается бандлом и существующими драйверами (SNMP, ICMP, trap, syslog, sFlow, LDAP, schedules, reports). Блокеры приёмки:

| Область | Сейчас | Пробел |
|---------|--------|--------|
| SSH | `ispf-driver-ssh` v0.1 **read-only** | Запись / rollback конфигураций |
| OPC DA → CMS | `ispf-driver-opc-da` **stub** | Production bridge (DCOM) или OPC UA |
| SMS | Только webhook / email-relay | Канал SMS gateway |
| E-mail отчётов | `emailTo` в schedules | Связка с `ispf.notifications.email-relay-url` |
| Cron в bundle | Только `intervalMs` | Опциональный cron или явная документация |
| `/apps/<appId>/` на prod | ADR-0054 | Проверить, что не SPA-fallback консоли |

## Решение

Эпики платформы (не отраслевой Java в `ispf-server`):

### P0
1. SSH write с allow-list / audit (opt-in).  
2. OPC DA bridge **или** путь OPC UA.  
3. Hosted ui-pack на прод-ingress.

### P1
4. E-mail для `run_report`.  
5. SMS relay (`SEND_SMS` / `sms-relay-url`).  
6. Опциональный `cron` в `BundleSchedule` (без ломки Jackson `long`).

### P2
7. WMI/WinRM или SSH+PowerShell для софт-инвентаря.  
8. Пример HTTP blueprint OceanStor.  
9. Шаблон discovery.  
10. Runbook подписи бандлов для партнёров.

## Последствия

Полное покрытие ТЗ М11 после P0–P1. Риски SSH write и Windows-зависимость OPC — см. EN ADR.

## Связанное

- [0001](0001-app-platform-boundary.md), [0054](0054-hosted-ui-packs.md), [0039](0039-unified-alarm-architecture.md)  
- PR UI pack: https://github.com/iot-solutions-ru/ispf/pull/69
