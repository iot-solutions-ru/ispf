# ADR-0063: Application Git lifecycle (full bundle sync)

> **Status:** Accepted — 2026-10-11. Hub: [doc-status.md](../doc-status.md).

## Context

Platform applications already ship as a **single bundle** (objects, SQL migrations, functions, dashboards, workflows, bindings, alerts, correlators, schedules, events, blueprints, operatorUi, …) via deploy / validate / pull-from-tree / deploy history ([applications](../applications.md), [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)).

Teams need:

1. Visibility of changes when humans edit the tree or AI mutates via MCP.
2. Review / partial accept / reject (PR).
3. Version tracking beyond in-DB snapshots.
4. A first-class **Git** surface on the APPLICATION node — not a second app format.

Git must stay at the **team host** (GitHub/GitLab). ISPF is a **client**: credentials, pull release → deploy, push tree → PR.

## Decision

### Three layers

| Layer | Location | Role |
|-------|----------|------|
| **Workspace** | Live object tree on ISPF | Fast edits (Console / MCP) |
| **Catalog** | Team git remote | Source of truth for releases and review |
| **Runtime** | `application_bundle_deployments` snapshots | What is deployed; `deploy/rollback` |

### Unit of sync

The **full application bundle** — the same manifest `ApplicationBundleDeployService` already deploys. Git does not invent a partial format.

Version identity is a triple:

1. `bundle.version` (semver in the manifest)
2. Git ref / commit SHA
3. Deploy history row (version + sha + time)

### Git tab on APPLICATION

| Action | Status | Behaviour |
|--------|--------|-----------|
| **Connect / Disconnect** | Shipped | Remote URL (`https` only; no embedded userinfo), provider (`github` \| `gitlab`), default branch, path to `bundle.json`, PAT via `PlatformCredentialService` at a fixed app-scoped path |
| **Status** | Shipped (partial) | Binding + active deploy + credential present; remote reachability and drift are reserved (`unknown` until providers land) |
| **Pull release** | Planned | Fetch full manifest (or pack from solution layout) → validate → deploy; history records `source=git`, ref, sha |
| **Push tree** | Planned | Full-section pull-from-tree (plus root fields / blueprints refresh) → commit on a feature branch → **open PR** (never force-push default branch) |

### Round-trip completeness

Pull-from-tree must preserve shipable root fields (`displayName`, `tablePrefix`, `schemaName`, `operatorUi`, `metadata`, `license`, `requires`) and refresh `blueprints[]` from the registry when names are known. `models[]` reverse-engineering and ui-pack packaging may lag; they stay from the last deployed base until a follow-up ADR.

### External CI

Repo-side CI (validate / dry-run) remains a complementary gate. It is not required for the platform Git tab to work.

## Consequences

- New table / API under `/api/v1/applications/{appId}/git`.
- Console Deploy panel gains a **Git** tab.
- Agent tools later: status / pull / push_pr.
- Security: least-privilege PAT; secrets never returned by GET; credential path fixed per app; disconnect and app uninstall delete the vault entry; changing provider or remote host requires a fresh token; outbound SSRF guards and branch-name validation land with Pull/Push.

## References

- [0007-bundle-tree-packaging](0007-bundle-tree-packaging.md)
- [0001-app-platform-boundary](0001-app-platform-boundary.md)
- [0049](0049-ot-automation-excellence.md) (credentials / vault — ADR-0049 family)
- [applications](../applications.md)
- [solution-developer-guide](../solution-developer-guide.md)
