> **Язык:** русская версия. Английский оригинал: [en/lab-mqtt-gateway-ingress.md](../en/lab-mqtt-gateway-ingress.md).

# Нагрузочный сценарий `I-02`: приём потока MQTT через шлюз (`mqtt-gateway`) с распределением по дочерним датчикам

> **Статус:** Lab — методика проверки маршрутизации входящего потока MQTT с одного шлюза на дочерние объекты датчиков и записи в архив истории. Метки зрелости: [doc-status.md](doc-status.md).

Сценарий **`I-02-mqtt-gateway`** проверяет работу узла-шлюза (`mqtt-gateway-v1`): один MQTT-драйвер принимает общий поток телеметрии из брокера, вызывает функцию распределения `dispatchTelemetry`, автоматически создаёт недостающие дочерние объекты датчиков (`instantiateModelIfMissing`) и записывает показания `temperature` в архив истории в режиме `TELEMETRY_ONLY`.

---

## 1. Схема прохождения данных в тесте

```text
Генератор нагрузки (emqtt-bench, числовой сигнал)
    ──► Брокер Mosquitto (tcp://<хост-брокера>:1883)
    ──► 1 драйвер mqtt на узле loadtest-mqtt-gateway (режим TELEMETRY_ONLY)
    ──► Переменная lastIngress ──► функция dispatchTelemetry
    ──► Дочерние датчики (loadtest-mqtt-sensor-00001 … N)
    ──► Переменная temperature ──► асинхронная запись в variable_samples
```

Параметры базового прогона: **4 топика устройств**, суммарный поток **~2 000 сообщений/с**, окно замера **60 с** после 20 с прогрева, интервал укрупнения на шлюзе `telemetryCoalesceMs = 50` мс.

---

## 2. Запуск сценария

Через единый оркестратор нагрузочного набора:

```bash
python deploy/run_lab_ordered_suite.py --topology single --only I-02 --reset soft
```

Или напрямую скриптом `mqtt-ingress-load-test.py` с флагом `--gateway`:

```bash
cd ~/ispf/loadtest
source ../lab-loadgen.env

ISPF_EMQTT_DOCKER_NETWORK=ispf-lab_default ISPF_EMQTT_SHARD_MAX=2 \
python3 mqtt-ingress-load-test.py \
  --base-url http://127.0.0.1:${ISPF_LAB_HTTP_PORT:-8000} \
  --mode push \
  --broker-url "tcp://${ISPF_MQTT_BROKER_HOST}:${ISPF_MQTT_BROKER_PORT}" \
  --publisher emqtt-bench \
  --publish-via-ssh "${ISPF_LAB_LOADGEN_SSH}" \
  --remote-deploy-dir "${ISPF_LAB_LOADGEN_ROOT}/loadtest" \
  --emqtt-interval-ms 10 \
  --devices 4 --messages-per-second 2000 \
  --phase-seconds 60 --warmup-seconds 20 \
  --skip-monitor-setup \
  --gateway --telemetry-coalesce-ms 50
```

---

## 3. Критерии успешного прохождения (`PASS`)

| Проверка | Условие |
|----------|---------|
| Состояние драйвера шлюза | Статус `RUNNING` и признак `connected = true` |
| Автоматическое создание дочерних датчиков | По приходу первых пакетов создаются узлы `loadtest-mqtt-sensor-00001..00004` и на их переменной `temperature` включается режим записи `ALL_VALUES` |
| Запись в архив истории | Прирост счётчика сброшенных на диск точек `variableHistoryFlushedTotal` за 60-секундное окно соответствует скорости потока с учётом окна слияния (`~4 400` точек/с при 2 000 сообщений/с) |
| Отсутствие лишней нагрузки на журнал событий | Таблица `event_history` не растёт (режим `TELEMETRY_ONLY`) |

**Связанные разделы:** [Нагрузочное тестирование (`load-testing.md`)](load-testing.md) · [Стресс-тест истории MQTT (`lab-mqtt-historian-stress.md`)](lab-mqtt-historian-stress.md).
