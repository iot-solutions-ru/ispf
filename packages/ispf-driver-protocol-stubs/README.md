# ISPF protocol stub pack

Multi-driver **STUB** pack: TCP reachability shells for popular industrial/IoT protocols
that are not yet implemented as full codecs.

- Catalog source: [`tools/driver-stubs/protocol-stubs.yaml`](../../tools/driver-stubs/protocol-stubs.yaml)
- Generator: `python tools/driver-stubs/generate-protocol-stubs.py`
- Maturity: always `DriverMaturity.STUB` (see `DriverProductionMatrix.protocolStubIds()`)
- Promotion: [docs/en/driver-promotion.md](../../docs/en/driver-promotion.md)

Do not edit generated `*DeviceDriver.java` files by hand — change the YAML and regenerate.
