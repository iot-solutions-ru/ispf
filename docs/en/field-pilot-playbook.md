# Field pilot playbook (BL-140)

Repeatable OT field-pilot runbooks for three reference scenarios. Each pilot validates **PRODUCTION** drivers, write round-trip, historian ingest, and operator HMI without middleware between ISPF and the plant edge.

Prerequisites: ISPF ≥ 0.9.32, [DRIVER_INTEROP_LAB.md](driver-interop-lab.md) green locally, operator account with device admin role.

---

## Pilot matrix

| Scenario | Primary driver | Typical site | Success criteria | Status |
| -------- | -------------- | ------------ | ---------------- | ------ |
| **Modbus plant** | `modbus-tcp` | PLC / RTU tank farm, pump skids | 50+ tags poll <2 s; FC16 write round-trip; mimic live | **Ready for field** |
| **MQTT fleet** | `mqtt` | Distributed gateways, telemetry burst | 10+ devices on shared broker; subscribe ingress; alarm on stale | **Ready for field** |
| **OPC UA line** | `opcua` + `opcua-server` | Packaging / assembly line cells | Browse + subscribe; external UA write-back; SCADA mimic | **Ready for field** |

Soak target: **7 days** minimum per pilot; log incidents in pilot journal (see § Close-out).

---

## 1 — Modbus plant pilot

### Scope

Single ISPF node on plant VLAN reading a Modbus TCP PLC (or `deploy/driver-interop` fixture for dry-run).

### Device setup

| Setting | Example |
| ------- | ------- |
| `driverId` | `modbus-tcp` |
| `host` | `192.168.10.50` |
| `port` | `502` |
| `pollIntervalMs` | `1000` |
| `unitId` | `1` |

Map holding registers to variables (`40001` → `tank.level`, etc.). See [DRIVERS.md](drivers.md) § modbus-tcp.

### Validation checklist

1. **Connect** — device status `RUNNING`; no `DriverException` in logs.
2. **Read** — 50 tags update within two poll cycles; quality `GOOD`.
3. **Write** — operator or API `PUT` on writable coil/register; PLC confirms change (or loopback fixture echoes).
4. **Historian** — `GET /api/v1/variables/{path}/history` returns samples for top 5 tags.
5. **HMI** — mimic or dashboard shows live values; stale badge after broker/PLC disconnect.

### Dry-run (lab)

```bash
docker compose -f deploy/driver-interop/docker-compose.yml up -d --wait
bash deploy/tools/driver-interop-smoke.sh
./gradlew :packages:ispf-driver-modbus:test
```

### Rollback

Stop driver runtime, revert device config snapshot, restore PLC program if write tests touched setpoints.

---

## 2 — MQTT fleet pilot

### Scope

Fleet of edge gateways publishing JSON telemetry to a shared broker (Mosquitto, HiveMQ, or cloud MQTT).

### Device setup

| Setting | Example |
| ------- | ------- |
| `driverId` | `mqtt` |
| `brokerUrl` | `tcp://mqtt.plant.local:1883` |
| `clientId` | `ispf-fleet-01` |
| `topicPrefix` | `plant/line1/{deviceId}/telemetry` |

One ISPF device per gateway or a multiplexed flexible topic map — see [DRIVERS.md](drivers.md) § mqtt.

### Validation checklist

1. **Ingress** — 10 devices online; variable count matches topic map.
2. **Burst** — simulate 1 k msg/min for 10 min; no ingress queue drops (`/api/v1/drivers/runtime` diagnostics).
3. **Write** — command topic publish (setpoint / ack) returns within SLA.
4. **Alarm** — stale rule fires when device stops publishing > 3× `pollIntervalMs`.
5. **Historian** — trend export CSV for one high-rate tag.

### Dry-run (lab)

```bash
docker compose -f deploy/driver-interop/docker-compose.yml up -d --wait
mosquitto_pub -h 127.0.0.1 -t ispf/lab/ping -m ok
./gradlew :packages:ispf-driver-mqtt:test
```

### Rollback

Disable alarm rules, stop MQTT devices, flush test topics on broker.

---

## 3 — OPC UA line pilot

### Scope

Assembly line with OPC UA servers on cells; ISPF as aggregator and optional `opcua-server` exporter for MES/SCADA partners.

### Device setup (client)

| Setting | Example |
| ------- | ------- |
| `driverId` | `opcua` |
| `endpointUrl` | `opc.tcp://192.168.20.10:4840` |
| `readMode` | `subscribe` |
| `timeoutMs` | `10000` |

Point mapping: `ns=2;s=LineSpeed` etc. SecurityPolicy None for lab; sign + encrypt for production VLAN.

### Device setup (server export)

| Setting | Example |
| ------- | ------- |
| `driverId` | `opcua-server` |
| `bindPort` | `4840` |
| `namespace` | `2` |

Expose selected ISPF variables to external UA clients (UA Expert, Prosys). See [OPCUA_SERVER_INTEROP.md](opcua-server-interop.md).

### Validation checklist

1. **Browse** — `IspfVariables` folder or cell namespace visible.
2. **Subscribe** — push updates without poll fallback; latency < 500 ms on lab LAN.
3. **Write-back** — external UA client write updates ISPF variable (`OpcUaServerSubscriptionWriteBackIntegrationTest` pattern).
4. **Partner read** — third-party client subscribes to `opcua-server` endpoint.
5. **Mimic** — line overview mimic bound to subscribed tags.

### Dry-run (lab)

```bash
./gradlew :packages:ispf-driver-opcua-server:test
./gradlew :packages:ispf-driver-opcua:test
```

### Rollback

Stop `opcua-server` device (frees port 4840), revert client endpoint URLs.

---

## Close-out

| Artifact | Location |
| -------- | -------- |
| Interop report | `build/driver-interop/interop-summary.md` |
| Pilot journal | Customer ticket / `docs/` appendix (duration, incidents, tag count) |
| Scorecard delta | [COMPETITIVE_SCORECARD.md](competitive-scorecard.md) dimension 3 |
| Promotion evidence | [DRIVER_PROMOTION.md](driver-promotion.md) field-pilot section |
| Sign-off record | § Pilot sign-off below (one per scenario) |

**Definition of done (BL-140):** three pilots **ready-for-field** with lab dry-run green, checklists complete, and sign-off template published; customer 7-day soak is the remaining gate before promotion evidence. Competitive scorecard OT connectivity ≥ 9.5 (post wave 8).

### Pilot checklist status (wave 8)

| Scenario | Lab dry-run | Field checklist | Sign-off template | Ready for field |
| -------- | :---------: | :-------------: | :---------------: | :-------------: |
| Modbus plant | ✅ `driver-interop-smoke.sh` | ✅ §1 | ✅ below | ✅ |
| MQTT fleet | ✅ Mosquitto + driver tests | ✅ §2 | ✅ below | ✅ |
| OPC UA line | ✅ opcua + opcua-server tests | ✅ §3 | ✅ below | ✅ |

Lab matrix, validation steps, and sign-off template are **complete** (BL-140 Done). Customer site pilots use the sign-off template to record production soak evidence.

---

## Pilot sign-off template (BL-140)

Complete one form per scenario after the **7-day soak**. Attach to the pilot journal and link from the customer ticket.

| Field | Value |
| ----- | ----- |
| **Scenario** | ☐ Modbus plant ☐ MQTT fleet ☐ OPC UA line |
| **Site / customer** | |
| **Pilot lead (ISPF)** | |
| **Customer OT lead** | |
| **ISPF version** | e.g. 0.9.32 |
| **Pilot start date** | |
| **Pilot end date** | (≥ 7 days after start) |
| **Tags / devices validated** | e.g. 52 Modbus tags, 10 MQTT gateways |
| **Historian samples verified** | ☐ Yes ☐ N/A |
| **HMI / mimic live** | ☐ Yes ☐ N/A |
| **Checklist items (§1–3)** | ☐ All passed |
| **P0 driver defects open** | ☐ None ☐ Listed in journal |

**Validation summary** (2–3 sentences):

```
<Describe connect/read/write/historian/HMI results; note any incidents>
```

**Signatures**

| Role | Name | Date |
| ---- | ---- | ---- |
| Customer OT lead | | |
| ISPF integrator | | |
| Platform QA | | |

**Approval:** ☐ Promote driver evidence to [DRIVER_PROMOTION.md](driver-promotion.md) ☐ Follow-up required

---

## Related

- [DRIVER_INTEROP_LAB.md](driver-interop-lab.md) — CI loopback matrix
- [DRIVERS.md](drivers.md) — driver configs
- [ROADMAP_PHASE25.md](roadmap-phase-25.md) — BL-140, BL-141, BL-143
- [DEPLOYMENT.md](deployment.md) — VPS / edge rollout
