# MES plan execution acceptance

Lab contour inside `mes-platform`. The planning engine stays outside. Live plant connectors (CUTE, CIP, historian) are not part of this bundle.

| Check | What the bundle does |
|---|---|
| AM-01 | A new candidate does not rewrite an existing official snapshot |
| AM-02 | Asset freshness `stale` is not treated as a zero result (`mes_plan_getDataReadiness` = `partial` on seed `LINE-A01`) |
| AM-03 | Quality status `blocked` rejects validation and publication |
| AM-04 | A blocked cooling resource cannot be reserved |
| AM-05 | `mes_plan_publish` creates one official id `PUB-<candidate>` and snapshot `SNAP-<candidate>` |
| AM-06 | A planner comment cannot publish a blocked SKU |
| AM-07 | Validation must be `ready` before publish |
| AM-08 | Repeating publish returns the same official id |
| AM-09 | `mes_plan_rejectPub` requires a reason |
| AM-10 | Republish marks the previous plan `replaced` and leaves recorded fact on the old id |
| AM-11 | Delay above 30 minutes opens a Warning deviation |
| AM-12 | Period close creates one tail; a second close returns the same tail id |
| AM-13 | Plan versus fact reads the selected official id, not a later demand row |
| AM-14 | Recipient delivery status is separate from the official plan (`mes_plan_setDelivery`) |
| AM-15 | Quality block is a function result, not a role bypass |
| AM-16 | Snapshots are insert-only rows in `mes_exec_snapshot` |

Lab mapping for AM-01…16. Happy-path coverage is in `MesPlanExecutionIntegrationTest` (deploy with `?withSeeds=true`). Full AM matrix and the production mirror are not asserted there. Marketplace / plain deploy without seeds leaves `mes_exec_*` empty — readiness then reports `ready` with zero assets until plant data is loaded.
