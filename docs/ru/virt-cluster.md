> **Язык:** перевод. Каноническая английская версия: [en/virt-cluster.md](../en/virt-cluster.md).

# Эталонный виртуальный кластер

> **Статус:** Stable — один вызов ставит встроенный виртуальный кластер. Метки: [doc-status.md](doc-status.md).

`POST /api/v1/platform/presets/virt-cluster` ставит один эталонный проект: три виртуальных устройства, хаб с копиями их синусов, флаг ошибки, два дашборда, тревогу и операторское приложение. Это единственный путь под `/api/v1/platform/presets/`. Остальные решения — карточки в **Система**, вкладка **Решения**. Проект с другими именами — это деплой `bundle.json`: [Руководство разработчика решений](solution-developer-guide.md#шаг-4-деплой-единого-пакета-bundlejson-основной-способ).

Вызов доступен `admin` и `developer`. `operator` получает 403. Пустое тело — те же значения, что в таблице. Повторный вызов обновляет те же пути.

```bash
curl -sS -X POST http://localhost:8080/api/v1/platform/presets/virt-cluster \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"wireOperatorApp\":true,\"operatorAppId\":\"platform\"}"
```

| Поле | По умолчанию | Смысл |
|------|----------------|-------|
| `wireOperatorApp` | `true` | Создать или обновить операторское приложение |
| `operatorAppId` | `platform` | Идентификатор в `?mode=operator&app=` |

В ответе `status` равен `OK`, пути из таблицы ниже и `operatorUrlHint`, если приложение подключено.

## Что появляется

| Путь | Что это |
|------|---------|
| `root.platform.devices.virt-cluster` | Папка, тип `CUSTOM` |
| `.dev-01`, `.dev-02`, `.dev-03` в этой папке | Устройства, тип `DEVICE`, модель `virtual-lab-v1`, драйвер `virtual`, опрос 2000 мс, автозапуск включён |
| `.hub` в этой папке | Хаб, тип `CUSTOM`. Переменные `member1Sine`, `member2Sine`, `member3Sine`, `clusterError` |
| `root.platform.dashboards.virt-cluster-overview` | Индикатор **Cluster ERROR** и таблица устройств |
| `root.platform.dashboards.virt-cluster-detail` | Три значения и три графика устройства, выбранного в таблице |
| `root.platform.alert-rules.virt-cluster-error` | Тревога на хабе |
| операторское приложение `platform` | Заголовок «Platform HMI», экран по умолчанию — обзор, полоса тревог включена |

Установка запускает драйвер каждого устройства и делает один опрос.

## Экран оператора

При запуске из клона консоль — `http://localhost:5173/?mode=operator&app=platform`. Готовый архив и контейнер используют порт 8080 с тем же запросом. Другой `operatorAppId` меняет значение `app`.

Таблица на обзоре использует `selectionKey` `device`. Строка открывает детальный дашборд (`rowTargetDashboard`, `rowOpenMode` `navigate`). Виджеты детального экрана читают выбранное устройство. Поля виджетов: [Виджеты](widgets.md) и [Дашборды](dashboards.md).

Полоса тревог принадлежит этому операторскому приложению: сверху, минимальный уровень `WARNING`, имя события `virtClusterError`. На дашборде администратора её нет. Учётная запись `operator` открывает это приложение сразу после входа, если `autoStartEnabled` истинно и `autoStartApp` равен `platform`. `admin` и `developer` по-прежнему попадают в консоль администратора. Они открывают кластер по адресу оператора выше. [Безопасность](security.md).

## Волны, копия и тревога

Каждый опрос драйвера `virtual` пишет сразу `sineWave`, `sawtoothWave` и `triangleWave`. Переключателя одной волны нет. Пресет задаёт амплитуды 10, 5 и 5 и `periodSec` 30. Страница драйвера: [virtual](drivers.md#virtual-ispf-driver-virtual).

Пресет включает `historyEnabled` на этих трёх переменных. График переменной пуст, пока флаг выключен. На профиле `local` точки лежат в файле H2 `data/ispf-local`, не в ClickHouse. [История переменных](variable-history.md).

Хаб копирует каждое устройство привязкой. `read("root.platform.devices.virt-cluster.dev-01/sineWave")` пишет в `member1Sine`. Так же устроены `dev-02` → `member2Sine` и `dev-03` → `member3Sine`. Флаг ошибки:

```text
self.member1Sine["value"] > 0 && self.member2Sine["value"] > 0 && self.member3Sine["value"] > 0
```

Результат пишется в `clusterError`. [Привязки](bindings.md).

Тревога смотрит переменную хаба `clusterError`. Условие `self.clusterError["value"] == true`, `edgeTrigger` включён, приоритет `HIGH`, имя события `virtClusterError`.
