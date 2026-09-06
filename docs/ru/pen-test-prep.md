# Подготовка к pen-test (G-01, углублённо)

> **Статус:** runbook подготовки — **не** отчёт о пройденном assessment.  
> SOW: [pen-test-scope.md](pen-test-scope.md) · RoE (EN): [../en/pen-test-roe.md](../en/pen-test-roe.md) · Опросник вендора (EN): [../en/pen-test-questionnaire.md](../en/pen-test-questionnaire.md) · Кейсы (EN): [../en/pen-test-cases.md](../en/pen-test-cases.md) · Prep (EN): [../en/pen-test-prep.md](../en/pen-test-prep.md)

## Зачем

Зафиксировать версию, роли, allow-list, severity SLA и шаблоны evidence **до** старта подрядчика, чтобы findings были воспроизводимы.

## Шаги оператора

1. Отобрать фирму по [опроснику](../en/pen-test-questionnaire.md); подписать SOW с [pen-test-scope.md](pen-test-scope.md) + [RoE](../en/pen-test-roe.md).  
2. Снять preflight (без паролей; читает info/health/auth/config + статусы OpenAPI + security headers):

```bash
bash tools/security/pen-test-preflight.sh https://TARGET --out /tmp/preflight.json
```

3. Заполнить [`inventory.template.md`](../evidence/security-pentest/inventory.template.md) (секреты только в vault).  
4. Выдать минимальный набор учёток: admin / operator / viewer / tenant-A / tenant-B.  
5. Отдать assessor каталог кейсов [pen-test-cases.md](../en/pen-test-cases.md), матрицу [`case-results.template.md`](../evidence/security-pentest/case-results.template.md) + OpenAPI (если опубликован).  
6. Провести kickoff по agenda в RoE §8.  
7. После отчёта: remediation → retest → редакция по [`evidence-index.template.md`](../evidence/security-pentest/evidence-index.template.md) в `docs/evidence/security-pentest/`.

Пока нет датированного отчёта + retest — в тендерах только **Gap (G-01)**.

## Связанное

- [compliance-tender-pack.md](compliance-tender-pack.md) G-01  
- [parked-backlog.md](parked-backlog.md) P-PENTEST  
- [security.md](security.md)
