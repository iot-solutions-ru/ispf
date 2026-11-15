> **Язык:** русская версия. Английский оригинал: [en/pen-test-prep.md](../en/pen-test-prep.md).

# Порядок подготовки стенда к независимому тестированию на проникновение

> **Статус:** Internal — пошаговая инструкция для администратора по подготовке изолированного стенда перед началом работы внешних аудиторов ИБ. Метки зрелости: [doc-status.md](doc-status.md).

Перед передачей тестового стенда внешним специалистам по тестированию на проникновение выполните следующие шаги:

1. Согласуйте техническое задание и правила проведения работ ([Область проверки `pen-test-scope.md`](pen-test-scope.md) и полные правила на английском языке [en/pen-test-roe.md](../en/pen-test-roe.md)).
2. Снимите контрольный паспорт конфигурации стенда (скрипт проверяет версию сервера, настройки аутентификации и HTTP-заголовки безопасности без сохранения секретов):
   ```bash
   bash tools/security/pen-test-preflight.sh https://TARGET_HOST --out /tmp/preflight.json
   ```
3. Заполните карточку инвентаризации стенда по шаблону [`docs/evidence/security-pentest/inventory.template.md`](../evidence/security-pentest/inventory.template.md).
4. Создайте на стенде изолированные тестовые учётные записи для каждого проверяемого уровня доступа: `admin`, `developer`, `operator`, а также двух тестовых арендаторов (`tenant-A` и `tenant-B`).
5. Передайте аудиторам каталог проверочных сценариев ([en/pen-test-cases.md](../en/pen-test-cases.md)) и шаблон протокола результатов ([`case-results.template.md`](../evidence/security-pentest/case-results.template.md)).

**Связанные разделы:** [Область проверки (`pen-test-scope.md`)](pen-test-scope.md) · [Комплект материалов для подрядчика (`pen-test-vendor-pack.md`)](pen-test-vendor-pack.md) · [Безопасность и RBAC (`security.md`)](security.md).
