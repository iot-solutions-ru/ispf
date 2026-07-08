# Agent reference scenarios (BL-108)

Ten spec→validate→deploy paths used for Gradle regression tests and the AI Studio help panel.

| ID | Title | Assignment type |
|----|-------|-----------------|
| `snmp-monitoring-lab` | SNMP monitoring lab | MONITORING_LAB |
| `mes-bundle-deploy` | MES reference bundle | APPLICATION_BUNDLE |
| `pump-station-scada` | Pump station SCADA | INDUSTRIAL_FACILITY |
| `workflow-hydro-impact` | Hydro-impact workflow | AUTOMATION_RULES |
| `virtual-device-lab` | Virtual device lab | MONITORING_LAB |
| `alert-automation` | Alert automation | AUTOMATION_RULES |
| `dashboard-monitoring` | Dashboard monitoring | SCADA_HMI |
| `bundle-validate-dry-run` | Bundle validate + dry-run | APPLICATION_BUNDLE |
| `operator-report-readonly` | Operator report (read-only) | REPORTING |
| `tree-function-deploy` | Tree function deploy | INTEGRATION_SKELETON |

Canonical machine-readable catalog: `packages/ispf-server/src/main/resources/agent-scenarios/catalog.json`.

API: `GET /api/v1/ai/agent/scenarios`.

Tests: `ReferenceScenarioTest` (10 parameterized cases).
