# Modbus Simulator (DDK reference)

In-process Modbus register image for CI loopback — no external TCP slave. Point mapping matches production `modbus-tcp`: `slaveId:type:address` (e.g. `1:HOLDING:0`).

Copy to `packages/ispf-driver-modbus-simulator/` and replace the in-memory image with a real Modbus TCP client when promoting to production.
