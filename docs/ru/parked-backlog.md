# Доска отложенного бэклога (Post-S33)

> **Политика:** [roadmap — качество вместо фич](roadmap.md#политика-исполнения--качество-вместо-фич).  
> Отложенное остаётся отложенным, пока нет **именованной** полевой / интеграционной задачи.

| ID | Тема | Статус | Критерий снятия | Заметка |
|----|------|--------|-----------------|---------|
| P-ENT-L | Enterprise L (50k history + 1B CH + multi-tag SLO) | **Lab PASS** (2026-09-05) | Rescore Historian на полном audit | Evidence: [`docs/evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md`](../evidence/historian-scale/2026-09-05-lab-192.168.100.10-enterprise-l.md) |
| P-HMI-8H | HMI offline **8 h** (lab CDP) | **В работе** | JSON + journal в `docs/evidence/hmi-offline/` | CDP soak 480 мин на demostand с 2026-09-05. Это **не** планшет / airplane mode. |
| P-HMI-FIELD | Полевой планшет / airplane mode | **Отложено** | Площадка + journal | [hmi-offline-field-soak.md](../en/hmi-offline-field-soak.md) |
| P-OT | Фаза 25 OT Trust (BL-140…) | **В работе** (lab-каталог закрыт; поле открыто) | Именованная площадка + soak journals для field Done | Lab-каталог **162/162**, stubs **0** (Waves 1–11 / #144). ADR-0057 DNP3 poll-only. Docker-фикстуры пока **mqtt/modbus/opcua** — [post-merge inventory](../evidence/ot-trust/2026-09-06-post-merge-lab-vs-field.md). `opc-da`/`opc-bridge` = `SHELL_BETA`. Lab ≠ OT 10/10. |
| P-ERP | Живой ERP (BL-169) | **Отложено** | Именованная интеграция 1C/SAP | Sandbox ≠ live |
| P-BPMN | Глубина BPMN/DMN сверх ADR-0047 | **Отложено** | Blocker заказчика + ADR | Freeze |
| P-WEBAUTHN | WebAuthn (BL-194) | **Отложено (Planned)** | Требование тендера / MFA | TOTP GA уже есть |
| P-PENTEST | Сторонний pen-test (G-01) | **Отложено** (prep углублён+) | Контракт + отчёт + retest | [pen-test-scope.md](pen-test-scope.md), RoE/RFP EN, [pen-test-prep.md](pen-test-prep.md), кейсы EN [pen-test-cases.md](../en/pen-test-cases.md), `tools/security/pen-test-preflight.sh` |

## Lab (операторы)

| Роль | Адрес | Заметка |
|------|-------|---------|
| Jump | `84.42.21.226:5031` | Учётка вне git |
| ISPF/DB | `192.168.100.10` | SSH без пароля **с jump** |

Пароли/токены **не** коммитить.

EN: [parked-backlog.md](../en/parked-backlog.md)
