# ADR-0019: Visual groups

## Status

Accepted (2026-06-23)

## Context

Users need to organize objects in the explorer tree without creating structural containers or duplicating paths. The same object should appear in multiple user-defined collections while keeping a single canonical `path`.

## Decision

1. Introduce `ObjectType.VISUAL_GROUP` — a normal tree node created anywhere children are allowed.
2. Store membership in reserved variable `@groupMembers` (JSON array of `{ path, sortOrder }`).
3. `GET /api/v1/objects?parent=<group>` returns member objects with `groupRef: true` and `groupContextPath` set; `path` remains the member's canonical path.
4. `GET /api/v1/objects?parent=<structural-parent>` omits objects that belong to any visual group; full flat listing (`parent` omitted) is unchanged for pickers and search.
5. Structural children under `VISUAL_GROUP` are forbidden; membership changes use `PUT /api/v1/objects/by-path/group-members`.
6. On object delete, all groups are scanned and stale member paths removed.
7. Explorer multi-select uses composite row keys (`groupPath::memberPath`) for group references; bulk delete operates on canonical paths; remove-from-group is a separate contextual action.

## Consequences

- Groups are persisted server-side and shared across users/sessions.
- Tree lazy-loading treats visual groups like containers for expand/chevron purposes.
- Binding rules and federation semantics are unchanged — groups are UI-only organization.
