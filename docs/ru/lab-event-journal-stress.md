> **Язык:** русская версия. Английский оригинал: [en/lab-event-journal-stress.md](../en/lab-event-journal-stress.md).

# Стендовые испытания очереди записи журнала событий под пиковой нагрузкой

> **Статус:** Lab — настройка и запуск стендовых испытаний асинхронного писателя журнала событий (`EventJournalAsyncWriter`) при многопоточном приёме из MQTT. Метки зрелости: [doc-status.md](doc-status.md).

Этот документ дополняет описание сценария **[`I-03` (`lab-mqtt-event-journal-ingress.md`)](lab-mqtt-event-journal-ingress.md)** и содержит справочник параметров скрипта `lab-mqtt-event-journal-multi-test.sh` для калибровки размера очереди и числа рабочих потоков записи журнала событий.

---

## 1. Быстрый запуск на лабораторном сервере

<a id="ssh-с-рабочей-станции-один-раз"></a>

### SSH с рабочей станции (один раз)

Для автоматического запуска скриптов с рабочей станции настройте вход по SSH-ключу:

```bash
python deploy/local/tools/lab-ssh-install-key.py
```

### Команды запуска на стенде

```bash
cd ~/ispf
set -a && . lab-stress.env && set +a

# Полный чистый прогон со сбросом томов БД
bash lab-test-host-clean-run.sh

# Пиковый замер на 16 устройствах (8 контейнеров генератора emqtt-bench)
DEVICES=16 RATE_PER_DEVICE=32000 EMQTT_SHARD_MAX=8 SKIP_DEVICE_SETUP=false \
  bash lab-mqtt-event-journal-multi-test.sh
```

---

## 2. Основные параметры скрипта `lab-mqtt-event-journal-multi-test.sh`

| Переменная окружения | По умолчанию | Назначение |
|----------------------|--------------|------------|
| `DEVICES` | `8` | Количество создаваемых тестовых устройств `mqtt` |
| `RATE_PER_DEVICE` | `2000` | Целевая частота публикации сообщений в секунду на одно устройство |
| `WARMUP` / `PHASE` | `15` / `60` | Длительность прогрева и зачетной фазы измерения в секундах |
| `INTERVAL_MS` | `1` | Интервал между публикациями одного клиента `emqtt-bench` в мс |
| `EMQTT_SHARD_MAX` | `4` | Количество параллельных контейнеров генератора `emqtt-bench` (на пике — `8`) |
| `EMQTT_CPU_LIMIT` | `1.5` | Ограничение ядер CPU на каждый контейнер генератора |
| `BENCH_NO_L0_COALESCE` | `true` | Отключение слияния пакетов на входе драйвера (`ingressCoalesceEnabled = false`) |

---

## 3. Как интерпретировать строки итогового отчёта

По завершении фазы измерения скрипт выводит три показателя:
1. **`Mosquitto PUBLISH in` / `delivered`** — сколько сообщений брокер Mosquitto фактически принял от генераторов и передал подписчикам (по системным счётчикам `$SYS/broker/messages/*`).
2. **`ISPF eventsFired`** — сколько сообщений принял быстрый путь журнала внутри `ispf-server`.
3. **`eventJournalFlushedTotal`** — сколько записей асинхронный писатель сбросил в БД. При отсутствии потерь внутри сервера отношение `eventJournalFlushedTotal / eventsFiredTotal` составляет **100 %**.

**Связанные разделы:** [Сценарий `I-03` (`lab-mqtt-event-journal-ingress.md`)](lab-mqtt-event-journal-ingress.md) · [Нагрузочное тестирование (`load-testing.md`)](load-testing.md).
