> **Язык:** русская версия. Английский оригинал: [en/reference-escalation-templates.md](../en/reference-escalation-templates.md).

# Шаблоны процессов эскалации тревог в BPMN

> **Статус:** Stable — готовые схемы автоматической эскалации: от повторяющегося превышения уставки и коррелятора событий до задачи дежурному оператору с контролем времени реакции (SLA). Метки зрелости: [doc-status.md](doc-status.md).

В каталоге [`examples/escalation-templates/`](../../examples/escalation-templates/) собраны типовые шаблоны связки **правило тревоги (`ALERT`) → коррелятор событий (`CORRELATOR`) → процесс BPMN (`WORKFLOW`) → очередь задач оператора (`Work Queue`)**.

---

## 1. Схема А — Запуск процесса при серии повторных тревог

Когда единичный выброс датчика не требует вмешательства, но 3 срабатывания подряд за короткое время означают реальную неисправность:

| Шаг | Компонент ISPF | Как настроен |
|-----|----------------|--------------|
| 1 | Правило тревоги (`ALERT`) | Проверяет условие CEL на переменной и публикует событие `thresholdExceeded` |
| 2 | Коррелятор событий (`CORRELATOR`) | Режим `COUNT`: если за заданное окно пришло `N` событий `thresholdExceeded`, выполняет действие `RUN_WORKFLOW` |
| 3 | Процесс BPMN (`WORKFLOW`) | Ставит задачу «Подтвердите тревогу» в рабочую очередь оператора и параллельно отправляет уведомление |

Файл примера коррелятора: [`examples/escalation-templates/recurring-threshold-correlator.json`](../../examples/escalation-templates/recurring-threshold-correlator.json).

---

## 2. Схема Б — Автоматическая эскалация, если оператор не принял тревогу вовремя (`boundaryEvent`)

Если дежурный оператор обязан отреагировать на задачу в течение заданного времени (например, за 5 минут), к шагу `userTask` в диаграмме BPMN прикрепляется **граничный таймер (`boundaryEvent`)**:

```xml
<userTask id="operatorAck" ispf:title="Подтвердите тревогу" />
<boundaryEvent id="ackTimeout" attachedToRef="operatorAck"
               cancelActivity="true" ispf:durationSeconds="300"/>
<sequenceFlow sourceRef="ackTimeout" targetRef="escalate"/>
```

Полный файл диаграммы: [`examples/escalation-templates/ack-timeout-escalation.bpmn.xml`](../../examples/escalation-templates/ack-timeout-escalation.bpmn.xml).

| Сценарий | Что происходит с экземпляром процесса |
|----------|---------------------------------------|
| Процесс запущен | Экземпляр переходит в состояние `WAITING` на шаге `operatorAck`, задача появляется в очереди оператора |
| Оператор закрыл задачу до истечения 300 с | Процесс завершается по штатной ветке (`COMPLETED`) |
| Оператор не успел за 300 с | Срабатывает таймер `ackTimeout`, задача снимается и выполняется ветка эскалации `escalate` (отправка уведомления начальнику смены в NATS / вызов функции оповещения) |

---

## 3. Схема В — Ожидание внешнего сигнала о закрытии заявки в системе ТОиР (`signal`)

Если процесс должен дождаться закрытия ремонтной заявки во внешней системе (например, 1С:ТОиР или ServiceDesk), используйте промежуточное событие ожидания сигнала (`intermediateCatchEvent`):

```xml
<intermediateCatchEvent id="waitTicket" ispf:signal="ticketClosed"/>
```

Когда внешняя система закрывает заявку, она отправляет сигнал экземпляру процесса по REST API:

```http
POST /api/v1/workflows/instances/{instanceId}/signal
Content-Type: application/json

{"signal":"ticketClosed","operatorId":"operator-1"}
```

**Связанные разделы:** [Процессы BPMN (`workflows.md`)](workflows.md) · [Правила тревог и корреляторы (`automation.md`)](automation.md).
