> **Language:** Canonical English. Russian edition: [ru/lab-without-plc.md](../ru/lab-without-plc.md).

# Lab without a PLC

> **Status:** Lab. Tags: [doc-status.md](doc-status.md).

ISPF runs on the host in the `local` profile: the portable zip, or `bootRun`. A `docker run` of the image does not see `127.0.0.1:1883` and `127.0.0.1:502` on the host. The portable zip serves the console on port `8080`. `bootRun` serves the API on `8080` and the console on `5173` (`cd apps/web-console && npm run dev`).

`lab-training` has no `objects` of its own. `ISPF_BOOTSTRAP_FIXTURES_ENABLED` defaults to `false` in `application.yml`. Set it `true` before start. That creates `lab-user-a` / `lab-user-b` and `root.platform.devices.lab-userA-01`, `lab-userB-01`, which the exercises use.

```bash
docker compose up -d mosquitto
docker compose -f deploy/driver-interop/docker-compose.yml up -d modbus-tcp
```

Mosquitto is the root compose service: port `1883`, anonymous, no TLS. Not a plant broker. The Modbus command starts only `modbus-tcp` from that file. It listens on `127.0.0.1:502`. Holding registers 0, 1, and 2 start as `0x1111`, `0x2222`, `0x3333`.

Token for the CLI (`ISPF_API_TOKEN`). `admin` / `admin` is the local profile.

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

From `tools/ispf-cli` after `npm install`, repo root:

```bash
node tools/ispf-cli/bin/ispf.mjs deploy examples/lab-training/bundle.json --app lab-training --strict
node tools/ispf-cli/bin/ispf.mjs deploy examples/lab-mqtt-temperature/bundle.json --app mqtt-temperature-lab --strict
```

`deploy` accepts a `bundle.json` file or a solution directory. Both bundles pass local `--strict`.

Operator screens on the portable zip: `http://localhost:8080?mode=operator&app=lab-training` and `?mode=operator&app=mqtt-temperature-lab`. With `bootRun`, use port `5173`. The 18 exercises: [Lab training](lab-training.md).

## MQTT

`examples/lab-mqtt-temperature/bundle.json` stores `metadata.broker` = `mqtt-broker.example.invalid:1883`. That field is not copied onto the driver. The sensor devices start with mixin temperature `22.5` and no MQTT driver.

`sensor-02` is `root.platform.devices.mqtt-lab.sensors.sensor-02`. Its registry topic is `esp2/1wire_test2/temperature`.

```bash
curl -X PUT "http://localhost:8080/api/v1/drivers/runtime/configure?devicePath=root.platform.devices.mqtt-lab.sensors.sensor-02" \
  -H "Authorization: Bearer $ISPF_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"driverId":"mqtt","pollIntervalMs":1000,"configuration":{"brokerUrl":"tcp://127.0.0.1:1883","topicPrefix":"esp2/","eventToVariable":"false"},"pointMappings":{"temperature":"1wire_test2/temperature"},"autoStart":true}'
```

Publish from the broker container:

```bash
docker compose exec mosquitto mosquitto_pub -h localhost -t esp2/1wire_test2/temperature -m 21.5
```

`eventToVariable` stays `false`. The driver writes the last value. It does not journal every message. On this call `topicPrefix` `esp2/` plus the mapping is the topic `esp2/1wire_test2/temperature`. `ispf/devices/` is filled in only when a new object is created with `driverId` `mqtt`, not by this configure call.

## Modbus

No bundle ships this slave. The object is a `DEVICE`. Calculations stay on a `SINGLETON` or `INSTANCE`.

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

`holding0` is a raw holding. Register 0 is `0x1111` (4369). Point format: [modbus-tcp](drivers.md#modbus-tcp-ispf-driver-modbus).
