# Подготовка к pen-test (G-01, углублённо)

> **Статус:** runbook подготовки — **не** отчёт о пройденном assessment.  
> SOW / RoE: [pen-test-scope.md](pen-test-scope.md) · Кейсы (EN): [../en/pen-test-cases.md](../en/pen-test-cases.md) · Prep (EN): [../en/pen-test-prep.md](../en/pen-test-prep.md)

## Зачем

Зафиксировать версию, роли, allow-list и шаблоны evidence **до** старта подрядчика, чтобы findings были воспроизводимы.

## Шаги оператора

1. Подписать SOW с [pen-test-scope.md](pen-test-scope.md).  
2. Снять preflight (без паролей):

```bash
bash tools/security/pen-test-preflight.sh https://TARGET --out /tmp/preflight.json
```

3. Заполнить [`inventory.template.md`](../evidence/security-pentest/inventory.template.md) (секреты только в vault).  
4. Выдать минимальный набор учёток: admin / operator / viewer / tenant-A / tenant-B.  
5. Отдать assessor каталог кейсов [pen-test-cases.md](../en/pen-test-cases.md) + OpenAPI.  
6. После отчёта: remediation → retest → редакция в `docs/evidence/security-pentest/`.

Пока нет датированного отчёта + retest — в тендерах только **Gap (G-01)**.

## Связанное

- [compliance-tender-pack.md](compliance-tender-pack.md) G-01  
- [parked-backlog.md](parked-backlog.md) P-PENTEST  
- [security.md](security.md)
