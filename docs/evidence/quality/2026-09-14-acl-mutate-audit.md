# ACL mutate-API audit (2026-09-14)

> Quick pass after hardening leftovers (#213–#217). **Not** a full security assessment (that is G-01).

## Method

- HTTP role matrix: `IspfAuthorizationRules` (RBAC on).
- Controller-level `ObjectAccessService` / `VariableMemberAccessService` / `requireConfigurator` for paths operators can reach at HTTP.

## Findings

| Surface | HTTP gate | Object / member ACL | Verdict |
|---------|-----------|---------------------|---------|
| Driver runtime mutate / write | CONFIG (catch-all) + object WRITE / variable writeRoles | Yes (#213, #217) | OK |
| Alert / filter / correlator / data-source / SQL binding | CONFIG + object WRITE | Yes (#215–#216) | OK |
| Dashboard / mimic save | READ HTTP for some variable writes; member writeRoles | Yes (#202) | OK |
| Local marketplace install/uninstall | CONFIG catch-all POST; controller `requireConfigurator` | Yes (#217) | OK (defense-in-depth for rbac-off / tests) |
| Operator-apps create / save UI / starters install | POST/PUT → CONFIG explicitly | Role gate only | OK for operator block; no per-app object ACL (acceptable — not object-tree resources) |
| Remote solution marketplace install/uninstall | CONFIG catch-all | Role gate only | OK vs operator; optional later: mirror `requireConfigurator` like local marketplace |
| Application deploy / bundle mutate | CONFIG catch-all | Mostly role + service checks | Out of this pass — CONFIG-only by design |
| Platform runtime-settings / backup / federation | ADMIN | ADMIN services | OK |
| BFF invoke / function invoke | READ + invoke ACL | Member invoke | OK |

## Residual (optional, low priority)

1. Add explicit `requireConfigurator` on `SolutionCatalogController` install/uninstall for symmetry with local marketplace (defense-in-depth when HTTP rules change).
2. Full G-01 grey-box — not replaceable by this note.

## Conclusion

No open **operator→mutate** hole of the R3 class remaining under current HTTP rules. Next quality gate is **hired pen-test (G-01)** + **dogfood** ([web-console-dogfood-checklist.md](../../en/web-console-dogfood-checklist.md)), not another ACL wave.
