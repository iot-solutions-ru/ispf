# Simulator profiles (PF-09)

Reference bundle for **virtual driver** profiles (`meter`, `weighbridge`, `rack-signals`).

Deploy:

```http
POST /api/v1/applications/simulator/deploy
Content-Type: application/json

<file: bundle.json>
```

After deploy, configure drivers on created devices (`sim-meter-01`, `sim-weighbridge-01`) via Web Console **Driver** tab or `PUT /api/v1/drivers/runtime/configure` with `driverId: virtual` and `profile` in configuration.

See [docs/DRIVERS.md](../../docs/DRIVERS.md#virtual-ispf-driver-virtual).
