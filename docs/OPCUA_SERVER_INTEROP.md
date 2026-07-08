# OPC UA Server Driver — Interop Guide (BL-143)

Embedded OPC UA **server** driver (`opcua-server`) exposes ISPF device variables to external OPC UA clients via Eclipse Milo. Loopback partner for the `opcua` **client** driver.

## Role in platform

| Driver | Direction | Module |
|--------|-----------|--------|
| `opcua-server` | ISPF → OPC UA wire | `packages/ispf-driver-opcua-server` |
| `opcua` | OPC UA wire → ISPF | `packages/ispf-driver-opcua` |

Production matrix: `DriverProductionMatrix` row `opcua-server` — maturity **PRODUCTION**, capabilities **read**, **write**, **quality**.

## Wire endpoint

| Setting | Default | Description |
|---------|---------|-------------|
| URL | `opc.tcp://localhost:4840/ispf` | Built from `bindPort` + fixed path `/ispf` |
| Transport | `TCP_UASC_UABINARY` | Milo binary protocol |
| Security | `SecurityPolicy.None` | Lab / interop only |
| Auth | Anonymous or username | Username validator accepts any credentials |
| Application URI | `urn:ispf:driver:opcua-server` | Milo application identity |
| Namespace URI | `urn:ispf:opcua:server` | Custom namespace index (default **2**) |

Constants and helpers: `OpcUaServerInterop.java` in the driver module.

## Address space

Browse tree:

```text
Objects
  └── IspfVariables
        └── {mapped tags from pointMappings}
```

- Variable type: `BaseDataVariableType`, datatype **String**, read/write.
- Nodes are created lazily on first `readPoints()`.
- Browse path pattern: `Objects/IspfVariables/{TagName}`.

## Device configuration

| Key | Default | Notes |
|-----|---------|-------|
| `bindPort` | `4840` | TCP listen port |
| `namespace` | `2` | Namespace index for bare identifiers |
| `timeoutMs` | `5000` | Server startup/shutdown timeout |
| `endpointPath` | `/ispf` | Documented in metadata; not user-overridable in v0.1 |

Point mapping: OPC UA `NodeId` string, e.g. `ns=2;s=Temperature`, or bare `Temperature` (uses configured namespace).

## Read / write schema

Driver variable schema `opcUaServerValue`:

| Field | Description |
|-------|-------------|
| `value` | String node value |
| `quality` | Always `StatusCode.GOOD` |
| `nodeId` | Mapping text |

Write expects `value` field in the incoming `DataRecord`.

## Interop testing

### Server module (CI gate)

```bash
./gradlew :packages:ispf-driver-opcua-server:test
```

Covers:

- `OpcUaServerPointTest` — NodeId parsing
- `OpcUaServerDeviceDriverTest` — connect, read, write round-trip on ephemeral port

### Client + server loopback (full stack)

```bash
./gradlew :packages:ispf-driver-opcua:test
```

`OpcUaDeviceDriverTest` starts in-process `OpcUaServerDeviceDriver`, then exercises client browse, write, and subscribe against `opc.tcp://localhost:{port}/ispf`.

### Top-20 interop report

```bash
bash deploy/tools/driver-interop-report.sh
```

CI workflow: `.github/workflows/driver-interop.yml` includes `ispf-driver-opcua-server`.

## Partner client driver

Configure `opcua` client device:

```json
{
  "endpointUrl": "opc.tcp://localhost:4840/ispf",
  "timeoutMs": "10000",
  "readMode": "poll"
}
```

Point mapping: same NodeId as server mapping, e.g. `ns=2;s=Temperature`.

See also [DRIVERS.md](DRIVERS.md) § opcua client and [DRIVER_INTEROP_LAB.md](DRIVER_INTEROP_LAB.md).

## Limitations (v0.1)

- String values only (no typed Variant mapping)
- SecurityPolicy None — not suitable for untrusted networks
- No REST browse for server devices (use OPC UA browse on wire)
- Server-side Milo monitoring callbacks are no-op; ISPF poll does not push to external subscribers automatically
- Ephemeral self-signed certificate regenerated on each connect

## Related files

| Path | Purpose |
|------|---------|
| `packages/ispf-driver-opcua-server/.../OpcUaServerDeviceDriver.java` | Driver SPI |
| `packages/ispf-driver-opcua-server/.../OpcUaServerInterop.java` | Documented endpoint contract |
| `packages/ispf-driver-opcua-server/.../IspfOpcUaNamespace.java` | Address space |
| `packages/ispf-driver-opcua/.../OpcUaDeviceDriverTest.java` | Cross-driver loopback |
| `packages/ispf-server/.../DriverProductionMatrix.java` | Production gate |
