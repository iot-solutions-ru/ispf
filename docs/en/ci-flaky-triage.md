# CI flaky test triage (S20-04)

Policy for Playwright, Gradle integration tests, and load gates when CI success rate drops.

## Severity

| Level | Definition | SLA |
| ----- | ---------- | --- |
| **P0 flake** | Blocks `main` merge or nightly >2 consecutive runs | Fix or quarantine within **24h** |
| **P1 flake** | Fails >1× per week, workaround exists | Fix within **1 sprint** |
| **P2 flake** | Rare (<1× month) | Backlog |

## Triage flow

1. **Detect** — failed job in GitHub Actions; label issue `ci-flake`.
2. **Assign owner** — module owner (backend / web-console / deploy).
3. **Reproduce** — local command from [testing](testing.md) or workflow log.
4. **Fix or quarantine:**
   - **Fix preferred:** deterministic wait, isolation, test data cleanup.
   - **Quarantine:** `@Disabled` / `test.skip` with issue link + expiry date (max 14 days).
5. **Verify** — green on PR + one nightly full run.
6. **Log** — note in [ACCELERATION_PROGRAM.md § scorecard log](acceleration-program.md).

## Quarantine rules

- Quarantined tests **must not** run in `pr-fast`; may run in nightly with `continue-on-error` only if explicitly marked experimental.
- Every quarantine entry in code:

```java
@Disabled("ci-flake #1234 — quarantine until 2026-07-20")
```

```typescript
test.skip(true, "ci-flake #1234 — quarantine until 2026-07-20");
```

- Expired quarantine without fix → escalate to P0.

## CI profiles

| Workflow | Trigger | Scope |
| -------- | ------- | ----- |
| [ci.yml](../.github/workflows/ci.yml) | PR | pr-fast: unit + build, path filters, no load/e2e |
| [ci-nightly.yml](../.github/workflows/ci-nightly.yml) | push `main`, daily cron | Full: load gates, Playwright, cluster ownership |
| [load-test.yml](../.github/workflows/load-test.yml) | nightly cron | JVM load gates only |
| [e2e-live.yml](../.github/workflows/e2e-live.yml) | weekly + dispatch | Prod smoke (secrets) |

## Known hotspots (baseline Jul 2026)

| Area | Symptom | Mitigation |
| ---- | ------- | ---------- |
| Load tests in PR | Slow + H2/Flyway flakes on Windows | Moved to nightly (S20-01) |
| Playwright preview | Mobile viewport timing | Nightly only |
| Federation tunnel IT | WS connect / buffer drain timeout on slow runners | `@Isolated`, budgets in `FederationIntegrationTestSupport` (S27); nightly if &gt;2×/week |

## Metrics

Track in weekly scorecard:

- CI success rate (14d) — target ≥95%
- Open `ci-flake` issues count
- Quarantined tests count (should trend to 0)

Collect baseline: `python tools/acceleration/collect-baseline.py`
