# Marketplace — IT Infrastructure Monitoring (М11)

## Артефакты

| Поле | Значение |
|---|---|
| Package / appId | `it-infra-monitoring` |
| Display name | Мониторинг ИТ-инфраструктуры М11 |
| Bundle | `examples/it-infra-monitoring/bundle.json` **v1.1.2** |
| UI pack | `examples/m11-monitor-ui/m11-monitor-ui-0.1.0.zip` |
| Site plugins | inventory / topology / integrations (`sites/m11`) |
| Catalog | `examples/marketplace-catalog/it-infra-monitoring/` + `…/m11-monitor-ui/` |
| Bridge | `operatorUi.uiPack` → `/apps/it-infra-monitoring/` |

## Runtime (v1.1.2)

| Role | Path | Notes |
|------|------|--------|
| Logic hub | `root.platform.devices.itm.hub` | **CUSTOM** + blueprint `INSTANCE`/`CUSTOM` (not DEVICE) |
| Email / SMS / webhook | `…itm.notify-*` | DEVICE gateways; schedules use `write_point` |
| SSH config | `…itm.ssh-config-jump` | `writeEnabled` + `writeCommandAllowlist` |
| CMS | `…itm.cms-opcua` | `driverId=opcua` (Classic DA = BETA shell) |

Post-deploy driver bind:

```bash
ISPF_BASE_URL=https://ispf.iot-solutions.ru ISPF_TOKEN=… \
  node examples/it-infra-monitoring/scripts/configure-integrations.mjs
# or --dry-run
```

Hints live in `m11-ui-overlay.json` → `deviceDriverHints`. Rebuild: `node scripts/build-bundle.mjs`.

## Install path

1. Application listing `it-infra-monitoring` (`uiPackSlug: m11-monitor-ui`)
2. UI pack listing `m11-monitor-ui` — static under `/apps/it-infra-monitoring/`
3. Site plugins M11 (inventory → topology → integrations)
4. `configure-integrations.mjs` (relay URLs / OPC endpoint / SSH host)
5. Smoke: `https://<ispf-host>/apps/it-infra-monitoring/`

## Signed deploy note

Prod with `ispf.license.require-signed-bundles=true` needs a signed `license` block
(`installationId` from `GET /api/v1/platform/installation-id`) or stepwise skeleton deploy.
Correlator `SEND_EMAIL` / `SEND_SMS` also needs global `ISPF_NOTIFICATIONS_*_RELAY_URL` **or** DEVICE gateways above.
