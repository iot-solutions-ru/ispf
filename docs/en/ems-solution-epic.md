# Epic: EMS solution pack (separate from storage)

> **Status:** Draft — not a storage deliverable.  
> **Storage:** [mssql-single-engine-ems-epic](mssql-single-engine-ems-epic.md). This file does not change historian claims.

## Scope

An operator solution shipped as an ISPF bundle plus an external React ui-pack:

- Logic on **SINGLETON** or **INSTANCE** hubs. Never `ObjectType.DEVICE` for orchestration.
- Data through `POST /api/v1/bff/invoke`. No industry pages inside `apps/web-console`.
- No PostgreSQL-only SQL (`ON CONFLICT`, `search_path`) against `ISPF_DB` when the site runs MSSQL.
- Channels, limits, bypass, acknowledgement, and reports are **solution** work. They are not Gate A–E of the single-engine epic.

## Done when

`ispf pack` / `ispf validate --strict` passes on the bundle, and the storage epic’s claim text is unchanged.
