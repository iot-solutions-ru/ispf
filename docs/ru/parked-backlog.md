# Доска отложенного бэклога (Post-S33)

> **Политика:** [roadmap — качество вместо фич](roadmap.md#политика-исполнения--качество-вместо-фич).  
> Отложенное остаётся отложенным, пока нет **именованной** полевой / интеграционной задачи.

| ID | Тема | Статус | Критерий снятия | Заметка |
|----|------|--------|-----------------|---------|
| P-ENT-L | Enterprise L (50k history + 1B CH + multi-tag SLO) | **Lab PASS** (2026-09-05) | Rescore Historian на полном audit | Evidence: [`docs/evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md) |
| P-HMI-8H | HMI offline **8 h** (lab CDP) | **В работе** | JSON + journal в `docs/evidence/hmi-offline/` | CDP soak 480 мин на demostand с 2026-09-05. Это **не** планшет / airplane mode. |
| P-HMI-FIELD | Полевой планшет / airplane mode | **Отложено** | Площадка + journal | [hmi-offline-field-soak.md](../en/hmi-offline-field-soak.md) |
| P-OT | Фаза 25 OT Trust (BL-140…) | **Отложено** | Именованная задача на драйвер | BL-191 honesty Done |
| P-ERP | Живой ERP (BL-169) | **Отложено** | Именованная интеграция 1C/SAP | Sandbox ≠ live |
| P-BPMN | Глубина BPMN/DMN сверх ADR-0047 | **Отложено** | Blocker заказчика + ADR | Freeze |
| P-WEBAUTHN | WebAuthn (BL-194) | **Отложено (Planned)** | Требование тендера / MFA | TOTP GA уже есть |
| P-PENTEST | Сторонний pen-test (G-01) | **Отложено** | Контракт на оценку | [compliance-tender-pack.md](compliance-tender-pack.md) |

## Lab (операторы)

| Роль | Адрес | Заметка |
|------|-------|---------|
| Jump | `84.42.21.226:5031` | Учётка вне git |
| ISPF/DB | `192.168.100.10` | SSH без пароля **с jump** |

Пароли/токены **не** коммитить.

EN: [parked-backlog.md](../en/parked-backlog.md)
