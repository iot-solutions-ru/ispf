> **Language:** Canonical English. Russian edition: [ru/troubleshooting.md](../ru/troubleshooting.md).

# Troubleshooting the first session

> **Status:** Stable — first-hour failures. Tags: [doc-status.md](doc-status.md).

Use this when a laptop install does not reach the demo sensor. Each section is a symptom, the check, and what the result means. Design rules for a project you are building are in [Common design mistakes](anti-patterns.md). Things the platform does not claim are in [Known limitations](known-limitations.md).

The checks below assume the server is on port `8080`. If you set `ISPF_SERVER_PORT`, use that port instead.

---

## The browser cannot open the console

**Which URL.** Portable zip / Docker → **http://localhost:8080**. From source (Option C) → **http://localhost:5173** after both the API and `npm run dev` in `apps/web-console`. A healthy API alone is not a complete first install — [Getting started](getting-started.md#try-ispf-15-minutes). The split (API `8080` + Vite `5173`) is intentional for hot-reload — [Why two processes](getting-started.md#why-two-processes-and-two-ports).

**Check.** From the same machine:

```bash
curl -sS http://localhost:8080/api/v1/info
curl -sS http://localhost:8080/actuator/health
```

`/api/v1/info` answers without a token and includes `version`. `/actuator/health` answers without a token. `{"status":"UP"}` means the process is serving.

**Connection refused or a timeout.** No ISPF process is listening. The portable zip is `start.bat` / `start.sh`. The image is the `docker run` line in [Getting started](getting-started.md). From source, the local profile is:

```bash
./gradlew :packages:ispf-server:bootRun --args="--spring.profiles.active=local"
```

**`/api/v1/info` answers, but `http://localhost:8080` returns 404.** Expected for source `bootRun`: that port is API-only. Start the Web Console (`cd apps/web-console && npm install && npm run dev`) and open **http://localhost:5173**. Portable zip and container serve the console on 8080. API-only without the console is the [advanced path](getting-started.md#advanced-api-only).

**Something else answers, and it is not ISPF.** Another program owns port 8080, or an older ISPF is still running. Stop that process, or start this one with a different `ISPF_SERVER_PORT`.

---

## The process exits before the port opens

**Default profile is not the laptop profile.** Without `local`, the server expects PostgreSQL at `localhost:5432/ispf` (user `ispf`, password `ispf`, unless `ISPF_DB_URL` / `ISPF_DB_USER` / `ISPF_DB_PASSWORD` say otherwise) and an OIDC issuer at `http://localhost:8180/realms/ispf`. The portable zip and the container already start the local profile: embedded H2 in `data/ispf-local` (`/opt/ispf/data/ispf-local` in the container), no PostgreSQL. A source checkout does not. Use the `bootRun` line above, or start PostgreSQL and Keycloak with `docker compose up -d` and the `dev` profile. Profile table: [Getting started](getting-started.md#summary-of-spring-profiles).

**The log says `Startup security guard refused boot`.** `ispf.security.strict-startup=true` (the `prod` profile) stopped the start. The same line names the cause: WebSocket origins `*`, the debug role header left on, or RBAC turned off. Fix that setting. Do not turn the guard off to get a demo going. Details: [Security](security.md).

---

## Sign-in rejects `admin` / `admin`

**Check.**

```bash
curl -sS http://localhost:8080/api/v1/auth/config
```

| `mode` | What to do |
|--------|------------|
| `local` | A new local database has `admin` / `admin`, `developer` / `developer`, and `operator` / `operator`. `operator` opens the operator screen and does not edit the object tree. That is the role, not a failed install. If this laptop already changed the password, stop ISPF and delete `data/` (leave `drivers/` in place) to recreate those accounts. |
| `oidc` | The built-in passwords are not this server's login. `localLoginEnabled` is false. Sign in with the issuer and client id in the same response. |

A wrong password is HTTP 401, `Invalid username or password`. After 5 failures for the same account and client address, the next attempt is HTTP 429, `Too many failed login attempts`, for 15 minutes.

`admin` / `admin` is for a local evaluation. Change it before the machine is reachable on a network. [Security](security.md).

The header `X-ISPF-Role` does not sign you in. It is off unless `ISPF_LOCAL_ROLE_HEADER_ENABLED=true`.

---

## `demo-sensor-01` never moves

The demo device needs the `virtual` driver and a started poll.

**Check** (token from `POST /api/v1/auth/login` on a `local` server):

```bash
curl -sS -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/drivers
```

HTTP 401 means the token is missing or was issued for another server. A JSON list with no `"id":"virtual"` means the evaluation packs are not loaded.

| How you started | Where the packs are |
|-----------------|---------------------|
| Portable zip | `drivers/` next to the JAR. `start.bat` / `start.sh` set `ISPF_DRIVER_PACKS_DIR` when you have not set it. |
| Container | `/opt/ispf/drivers` in the image. A volume on `/opt/ispf/data` does not replace them. |
| `bootRun` | `build/driver-packs`, filled by `syncDevDriverPacks`. |

Deleting `data/` resets the H2 database and does not remove `drivers/`. Deleting `drivers/`, or pointing `ISPF_DRIVER_PACKS_DIR` at an empty folder, removes the protocols.

The evaluation set is the small one (`virtual`, `modbus`, `mqtt`, `http`, `jdbc`, and the rest of that set). OPC UA, SNMP, and the rest of the catalog are not in the zip or the image. Build them with `./gradlew syncAllDriverPacks` or bring an air-gap bundle. [Getting started](getting-started.md#how-driver-packs-are-loaded).

When `virtual` is in the list, start the demo poll:

```bash
curl -X POST "http://localhost:8080/api/v1/drivers/runtime/start?devicePath=root.platform.devices.demo-sensor-01" \
  -H "Authorization: Bearer $TOKEN"
```

`temperature` then follows a sine wave. It crosses `threshold`, `alarmActive` becomes true, and the sample alert writes a journal event.

---

## An MQTT device stays down on the local profile

The `local` profile does not start a broker, and the platform's own MQTT client is off (`ispf.mqtt.enabled=false`). A `DEVICE` that uses the `mqtt` driver still needs a broker you start yourself. The `dev` profile expects Mosquitto from `docker compose` on port `1883`. A bundle whose broker host is a placeholder such as `mqtt-broker.example.invalid` will not connect until that host is a broker you run.

---

## `ispf validate --strict` fails

From a checkout, after `npm install` in `tools/ispf-cli`:

```bash
node tools/ispf-cli/bin/ispf.mjs validate <solution-dir> --local --strict
```

`LOGIC_HOST_DEVICE` means application logic sits on an object of type `DEVICE`. A `DEVICE` is the driver and its tags. The orchestrator is a `SINGLETON` (one hub) or an `INSTANCE` (one twin per unit). Without `--strict`, authoring reports that as a warning. `--strict` and the marketplace catalog make it an error. [Application principles](application-principles.md), [Common design mistakes](anti-patterns.md).

`HEAVY_WIDGET_EXPRESSION` means a widget `expression`, `condition`, or `script` is 400 characters or longer. Move that calculation to a hub binding or function. The widget only displays the variable.

---

## The trend is empty

History is written only when `historyEnabled` is on for that variable. On the local profile the store is the H2 file, not ClickHouse.

The disk spool in front of sole-store ClickHouse (`ispf.variable-history.spool`) is shipped and **off**. It does not apply to JDBC or to dual-write. It is not a second buffer on another ISPF node, and ISPF does not create a ClickHouse cluster. The metric `oldest_age_ms` on dual-write catch-up is the age of an open catch-up range, not how far ClickHouse lags PostgreSQL. [Variable history](variable-history.md), [Known limitations](known-limitations.md).

---

## Deploy or other API calls return 401

Obtain a token from the server you are calling:

```bash
curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

Send it as `Authorization: Bearer <token>`. `ISPF_BASE_URL` must be that same origin (default `http://localhost:8080`). A token from another host is not accepted here. On an `oidc` server, use the identity provider's access token, not `admin` / `admin`.

---

## Related

- [Getting started](getting-started.md)
- [Security](security.md)
- [Device drivers](drivers.md)
- [Known limitations](known-limitations.md)
