# BL-143 OPC UA server — lab GA gate (2026-10-15)

**Status:** Lab Done (CI). Field / plant UA Expert soak remains optional under BL-140.

## Acceptance mapping

| Requirement | Evidence |
|-------------|----------|
| External client read / write | `OpcUaServerDeviceDriverTest`, `OpcUaServerSubscriptionWriteBackIntegrationTest` |
| External client subscribe + ISPF write on wire | `OpcUaServerExternalSubscribeNotificationTest` (monitored items + read-after-write); scale: `OpcUaServerSubscriptionScaleTest` |
| Browse address space | Same test + client `browseChildrenFindsTemperatureNode` |
| UA Expert / Prosys | Lab proxy = Eclipse Milo in CI; manual steps in [opcua-server-interop](../../en/opcua-server-interop.md) |

## Commands

```bash
./gradlew :packages:ispf-driver-opcua-server:test
```

## Non-blocking residuals

- String-only Variants
- Username token accepts any credentials (channel security is application identity / SignAndEncrypt)
