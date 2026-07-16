> **Language:** Canonical English. Russian edition: [ru/release-dogfood.md](../ru/release-dogfood.md).

# Release dogfood checklist

> **Status:** Internal — Release checklist. Hub: [doc-status.md](doc-status.md).

Short gate before tagging a release. Prefer failing a step over skipping it.

Related: [demostands](demostands.md), [observability](observability.md), [golden-path-alarm-smoke](../../deploy/tools/golden-path-alarm-smoke.py).

## Gate (human or CI)

| # | Check | How |
|---|--------|-----|
| 1 | **Golden alarm path** | `python deploy/tools/golden-path-alarm-smoke.py` (fixtures / demostand with `demo-sensor-01`) |
| 2 | **Self-diagnostics** | Admin → System → Metrics: hot-path card shows numbers; open dashboard `root.platform.dashboards.platform-metrics` |
| 3 | **Operator starters** | `?mode=operator` → Alarm Console / Work Queue / HMI Wall open (or Install starters) |
| 4 | **Dashboard bind** | Open `demo-sensor` (or any HMI): live value updates without full page reload |
| 5 | **Mimic** | Open a mimic object (if present): canvas loads, no console error |
| 6 | **Workflow** | Run or view `demo-alarm-handler` (or site workflow): instance appears / completes |
| 7 | **Federation (if used)** | Peer health / connect wizard smoke — skip on single-node |

## Exit criteria

- Steps 1–3 green on the target stand (local fixtures or staging).
- Steps 4–6 green for the product surfaces you ship in this tag.
- No known P0 in journal / WS silence / auth.

## Not this checklist

- Full load-test suite ([load-testing](load-testing.md))
- External Grafana import (optional export only)
- Clean-install without fixtures (starters installable via API/launcher)
