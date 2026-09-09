# Доска отложенного бэклога (Post-S33)

> **Политика:** [roadmap — качество вместо фич](roadmap.md#политика-исполнения--качество-вместо-фич).  
> Отложенное остаётся отложенным, пока нет **именованной** полевой / интеграционной задачи.

| ID | Тема | Статус | Критерий снятия | Заметка |
|----|------|--------|-----------------|---------|
| P-ENT-L | Enterprise L (50k history + 1B CH + multi-tag SLO) | **Учтено** (scorecard 2026-09-09) | — | Сложено в Historian **9.5** — [competitive-scorecard](competitive-scorecard.md). Evidence: [`historian-scale/2026-09-05-…`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md) |
| P-HMI-8H | HMI offline **8 h** (lab CDP) | **Учтено** (scorecard 2026-09-09) | — | Сложено в HMI **8.0**. Только CDP — **P-HMI-FIELD** для 9+. Evidence: [`hmi-offline/2026-09-05-…`](../evidence/hmi-offline/2026-09-05-ispf-vps-0.9.207-offline-8h.json) |
| P-HMI-FIELD | Полевой планшет / airplane mode | **Отложено** | Площадка + journal | [hmi-offline-field-soak.md](../en/hmi-offline-field-soak.md) |
| P-OT | Фаза 25 OT Trust (BL-140…) | **В работе** (P1 days 1–4; P2 MQTT day 2; P3 OPC UA day 2; timers) | 7-day soak + OT sign-off | [P1 day4](../evidence/ot-trust/2026-09-09-bl140-pilot1-day4.md) · [P2 day2](../evidence/ot-trust/2026-09-09-bl140-pilot2-mqtt-day2.md) · [P3 day2](../evidence/ot-trust/2026-09-09-bl140-pilot3-opcua-day2.md). Не OT 10/10. |
| P-ERP | Живой ERP (BL-169) | **Отложено** | Именованная интеграция 1C/SAP | Sandbox ≠ live |
| P-BPMN | Глубина BPMN/DMN сверх ADR-0047 | **Отложено** | Blocker заказчика + ADR | Freeze |
| P-WEBAUTHN | WebAuthn (BL-194) | **Отложено (Planned)** | Требование тендера / MFA | TOTP GA уже есть |
| P-PENTEST | Сторонний pen-test (G-01) | **Отложено** (vendor-pack готов) | Контракт + отчёт + retest | **Отправить:** [pen-test-vendor-pack.md](pen-test-vendor-pack.md). Scope/RoE/RFP EN; [pen-test-prep.md](pen-test-prep.md); lab pin [2026-09-09](../evidence/security-pentest/2026-09-09-lab-192.168.100.10-preflight.md). Docs ≠ cert. |

## Lab (операторы)

| Роль | Адрес | Заметка |
|------|-------|---------|
| Jump | `84.42.21.226:5031` | Учётка вне git |
| ISPF/DB | `192.168.100.10` | SSH без пароля **с jump** |

Пароли/токены **не** коммитить.

EN: [parked-backlog.md](../en/parked-backlog.md)
