# OT Trust Wave 4 — edge I/O codec promotion

Date: 2026-09-05

## Scope

Promoted clean-room lab codecs (JDK sockets, Apache-2.0):

| driverId | pack | capability |
|----------|------|------------|
| barcode-scanner | ispf-driver-barcode-scanner | read+write |
| weighbridge | ispf-driver-weighbridge | read+write |
| weather-station | ispf-driver-weather-station | read-only |

## Honesty

- Lab TCP dialects with in-process fake devices — not vendor SDK wrappers.
- Matrix PRODUCTION / READY_LAB ≠ field certification.

## Tests

```
./gradlew :packages:ispf-driver-barcode-scanner:test \
  :packages:ispf-driver-weighbridge:test \
  :packages:ispf-driver-weather-station:test
```
