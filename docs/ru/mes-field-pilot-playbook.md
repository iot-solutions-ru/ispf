> **Язык:** русская версия (вычитка). Канонический английский: [en/mes-field-pilot-playbook.md](../en/mes-field-pilot-playbook.md).

# Playbook полевого MES-пилота (BL-164…170 / Post-S33)

> **Статус:** Lab — runbook цеха. Хаб: [doc-status.md](doc-status.md).

Повторяемый цикл **MES на реальной линии** через `mes-platform-production`. Lab/marketplace **Готово**; хвост — **7 дней на площадке**, не новые модули MES.

**Вне scope:** живой ERP (BL-169 отложен) — outbox честно stub.

---

## Деплой

```bash
ISPF_BASE_URL=https://ispf.iot-solutions.ru \
ISPF_DEPLOY_PASSWORD=… \
bash deploy/tools/mes-platform-production-deploy.sh
```

Operator: `?mode=operator&app=mes-platform-production`

---

## Ежедневный цикл (7 дней)

| Шаг | Действие |
| --- | -------- |
| WO | instantiate + dispatch |
| OEE | `mes_oee_getKpi` |
| Quality | dashboard SPC |
| Batch | `mes_batch_runPhase` |
| Genealogy | `mes_genealogy_queryByLot` |
| ERP | outbox stub only |

Журнал: [mes-field-soak-journal.template.md](../evidence/mes-field-soak-journal.template.md)  
Стартовый пакет: [examples/field-sites/mes-line-a01/](../../examples/field-sites/mes-line-a01/README.md)

См. [reference-mes-platform.md](reference-mes-platform.md)
