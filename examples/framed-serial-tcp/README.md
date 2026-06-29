# ASCII serial-over-TCP (flexible driver)

Example for **computer-format ASCII** request/response over TCP (RS-232 serial port bridged to Ethernet).

Uses the `flexible` driver **exchange pipeline** — no vendor-specific code in the platform.

## Protocol notes

Per typical serial interface manuals (computer format):

- **Request:** SOH (`0x01`) + optional security digits + ASCII function code (`i20101`)
- **Response:** SOH + ASCII payload (timestamps, tank id, **ASCII-hex IEEE floats** `FFFFFFFF`) + optional `&&CCCC` on wire + ETX (`0x03`)
- **TCP bridge:** transport is reliable; **checksum verification is disabled** (`checksumAlgorithm: none`). The gateway may still pass through `&&CCCC` from the device — extractors read floats from the ASCII body only.

This is **not a binary protocol** — only SOH/ETX are control bytes; function codes and float fields are printable ASCII.

## Device configuration

See [`device-config.json`](device-config.json).

```json
{
  "configuration": {
    "encoding": "escapes",
    "readMode": "delimiter",
    "readUntilHex": "03",
    "checksumAlgorithm": "none"
  },
  "pointMappings": {
    "tank01_volume": "req:\\x01${securityCode}i20101|extract:asciiHexFloat:0:after:07"
  }
}
```

Points sharing the same resolved `req:` are grouped — **one TCP exchange** per poll.

## Field map (function 201, tank inventory)

| Variable suffix | Float index | Meaning |
|-----------------|-------------|---------|
| `volume` | 0 | Volume |
| `tcVolume` | 1 | TC volume |
| `ullage` | 2 | Ullage |
| `height` | 3 | Product height |
| `water` | 4 | Water |
| `temperature` | 5 | Temperature |
| `waterVolume` | 6 | Water volume |

`after:07` — scan floats after ASCII hex field count `NN=07`.

## VPS (prod)

Device: **`root.platform.devices.framed-gauge-01`** on https://ispf.iot-solutions.ru

```bash
ssh root@ispf.iot-solutions.ru
GAUGE_TCP_HOST=10.0.0.5 GAUGE_TCP_PORT=10001 GAUGE_SECURITY_CODE= GAUGE_TANK=01 GAUGE_AUTO_START=true \
  python3 /opt/ispf/bin/vps-framed-gauge-setup.py
```

Script: [`deploy/vps-framed-gauge-setup.py`](../../deploy/vps-framed-gauge-setup.py)
