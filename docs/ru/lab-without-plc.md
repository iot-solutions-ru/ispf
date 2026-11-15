> **Язык:** русская версия. Канонический английский: [en/lab-without-plc.md](../en/lab-without-plc.md).

# Стенд без ПЛК

> **Статус:** Lab. Теги: [doc-status.md](doc-status.md).

ISPF работает на этом компьютере в профиле `local`: архив или `bootRun`. `docker run` образа не видит `127.0.0.1:1883` и `127.0.0.1:502` хоста. Архив отдаёт консоль на порту `8080`. `bootRun` отдаёт API на `8080`, консоль — на `5173` (`cd apps/web-console && npm run dev`).

У `lab-training` нет своих `objects`. `ISPF_BOOTSTRAP_FIXTURES_ENABLED` в `application.yml` по умолчанию `false`. Поставьте `true` до старта. Тогда появятся `lab-user-a` / `lab-user-b` и `root.platform.devices.lab-userA-01`, `lab-userB-01`, на которые опираются упражнения.

```bash
docker compose up -d mosquitto
docker compose -f deploy/driver-interop/docker-compose.yml up -d modbus-tcp
```

Mosquitto — сервис корневого compose: порт `1883`, анонимно, без TLS. Это не цеховой брокер. Команда Modbus поднимает из того файла только `modbus-tcp`. Он слушает `127.0.0.1:502`. Holding-регистры 0, 1 и 2 при старте равны `0x1111`, `0x2222`, `0x3333`.

Токен для CLI — `ISPF_API_TOKEN`. `admin` / `admin` — профиль `local`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)
export ISPF_API_TOKEN="$TOKEN"
```

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login -ContentType "application/json" -Body '{"username":"admin","password":"admin"}'
$env:ISPF_API_TOKEN = $login.token
```

Из корня репозитория, после `npm install` в `tools/ispf-cli`:

```bash
node tools/ispf-cli/bin/ispf.mjs deploy examples/lab-training/bundle.json --app lab-training --strict
node tools/ispf-cli/bin/ispf.mjs deploy examples/lab-mqtt-temperature/bundle.json --app mqtt-temperature-lab --strict
```

`deploy` принимает файл `bundle.json` или каталог решения. Оба бандла проходят локальный `--strict`.

Экраны архива: `http://localhost:8080?mode=operator&app=lab-training` и `?mode=operator&app=mqtt-temperature-lab`. При `bootRun` порт `5173`. 18 упражнений: [Лабораторное обучение](lab-training.md).

## MQTT

В `examples/lab-mqtt-temperature/bundle.json` поле `metadata.broker` равно `mqtt-broker.example.invalid:1883`. В драйвер оно не копируется. Датчики стартуют с температурой примеси `22.5` и без драйвера MQTT.

`sensor-02` — это `root.platform.devices.mqtt-lab.sensors.sensor-02`. Топик в реестре — `esp2/1wire_test2/temperature`.

```bash
curl -X PUT "http://localhost:8080/api/v1/drivers/runtime/configure?devicePath=root.platform.devices.mqtt-lab.sensors.sensor-02" \
  -H "Authorization: Bearer $ISPF_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"driverId":"mqtt","pollIntervalMs":1000,"configuration":{"brokerUrl":"tcp://127.0.0.1:1883","topicPrefix":"esp2/","eventToVariable":"false"},"pointMappings":{"temperature":"1wire_test2/temperature"},"autoStart":true}'
```

Публикация из контейнера брокера:

```bash
docker compose exec mosquitto mosquitto_pub -h localhost -t esp2/1wire_test2/temperature -m 21.5
```

`eventToVariable` остаётся `false`. Драйвер пишет последнее значение и не кладёт каждое сообщение в журнал. Здесь `topicPrefix` `esp2/` плюс точка дают топик `esp2/1wire_test2/temperature`. `ispf/devices/` подставляется только при создании объекта с `driverId` `mqtt`, не этим вызовом configure.

## Modbus

Готового бандла для этого подчинённого нет. Объект — `DEVICE`. Расчёты остаются на `SINGLETON` или `INSTANCE`.

```bash
curl -X POST http://localhost:8080/api/v1/objects \
  -H "Authorization: Bearer $ISPF_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"parentPath":"root.platform.devices","name":"lab-modbus-01","type":"DEVICE","displayName":"Lab Modbus"}'

curl -X PUT "http://localhost:8080/api/v1/drivers/runtime/configure?devicePath=root.platform.devices.lab-modbus-01" \
  -H "Authorization: Bearer $ISPF_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"driverId":"modbus-tcp","pollIntervalMs":1000,"configuration":{"host":"127.0.0.1","port":"502","timeoutMs":"3000"},"pointMappings":{"holding0":"1:HOLDING:0"},"autoStart":true}'
```

`holding0` — сырой holding. Регистр 0 равен `0x1111` (4369). Формат точки: [modbus-tcp](drivers.md#modbus-tcp-ispf-driver-modbus).
