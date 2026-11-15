> **Язык:** русская версия. Английский оригинал: [en/mes-field-pilot-playbook.md](../en/mes-field-pilot-playbook.md).

# Регламент опытно-промышленной эксплуатации пакета MES в цеху

> **Статус:** Lab — порядок проведения 7-дневных цеховых испытаний прикладного пакета `mes-platform-production`. Метки зрелости: [doc-status.md](doc-status.md).

Этот документ описывает порядок проверки эталонного решения **[MES (`mes-platform-production`)](reference-mes-platform.md)** на производственном участке в течение 7 сменных циклов.

---

## 1. Установка пакета на сервере участка

```bash
ISPF_BASE_URL=https://ispf.iot-solutions.ru \
ISPF_DEPLOY_PASSWORD=<пароль-администратора> \
bash deploy/tools/mes-platform-production-deploy.sh
```

Рабочее место оператора открывается по адресу `?mode=operator&app=mes-platform-production`.

---

## 2. Ежедневный проверочный цикл смены (7 дней)

| Модуль MES | Проверяемая операция на смене |
|------------|-------------------------------|
| **Производственные заказы (`Work Orders`)** | Создание заказа по шаблону `work-order-v1`, выдача в рабочую очередь смены и подтверждение выполнения оператором |
| **Эффективность линии (`OEE`)** | Расчёт показателей смены через `mes_oee_getKpi` и фиксация причин простоев |
| **Контроль качества (`Quality / SPC`)** | Ввод контрольных замеров и проверка отображения на графике допусков |
| **Рецептурные партии (`ISA-88 Batch`)** | Проведение партии по фазам технологического процесса (`mes_batch_runPhase`) |
| **Генеалогия партии (`Genealogy`)** | Построение дерева прослеживаемости партии через `mes_genealogy_queryByLot` и выгрузка отчёта |

Шаблон журнала опытной эксплуатации: [`docs/evidence/mes-field-soak-journal.template.md`](../evidence/mes-field-soak-journal.template.md).  
Пример конфигурации цеховой линии: [`examples/field-sites/mes-line-a01/README.md`](../../examples/field-sites/mes-line-a01/README.md).
