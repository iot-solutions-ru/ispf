# ADR-0066: Platform security model (auth, RBAC, tenancy, ACL, secrets)

## Status

**Accepted** (2026-10-12) — consolidates the shipped security contract; does not change runtime behaviour.  
**Citation:** until merge to `main`, treat this record as the security claim boundary from PR #476.

## Context

Security behaviour is documented across [security](../security.md), [multi-tenant](../multi-tenant.md), federation on-behalf-of notes, and ADRs [0056](0056-webauthn-idp-mfa.md) / [0065](0065-secrets-key-rotation.md). Tenders and agents need **one decision record** that states what ISPF claims today, what is optional, and what remains Proposed — without inventing a new stack.

Related field evidence: BL-153 (TOTP), BL-154 (per-variable ACL + trusted channel), BL-155 (tenant-admin + RLS), ADR-0065 (secrets-key rotation).

**Industry bar:** mature IIoT platforms publish a layered auth → RBAC → tenancy → ACL → secrets model with fail-closed defaults. ISPF’s differentiator is **tree-scoped MEMBER ACL + federation ceiling + envelope secrets** in one claim table — not a parallel “security product.”

## Decision

### 1. Layered model (outer → inner)

| Layer | Contract | Primary code / docs |
|-------|----------|---------------------|
| **1. Authentication** | Caller identity via local Bearer session **or** OIDC JWT. Dev-only `X-ISPF-Role` is **off by default** (`ispf.security.local-role-header-enabled=false`). | `LocalBearerTokenFilter`, resource-server JWT, [security](../security.md) |
| **2. HTTP RBAC** | Built-in roles `admin`, `developer`, `operator`, `tenant-admin` (+ custom roles). Endpoint matrix in `IspfAuthorizationRules`. | [security § Access matrix](../security.md) |
| **3. Tenancy scope** | Path namespace `root.tenant.{id}.*`; sole-tenant virtual root; global `admin` always `tenant_id` NULL. | [multi-tenant](../multi-tenant.md), `TenantScopeService` |
| **4. Object ACL** | `object_acl_entries` (OWNER / EDITOR / …) on the tree. | Web Console Access tab, `ObjectAccessService` |
| **5. Member ACL** | Interactive paths run `VariableAclRequestContext.MEMBER`: variable `readRoles` / `writeRoles`, event/function `invokeRoles`. Background jobs stay `SYSTEM`. | BL-154, `VariableMemberAccessService` |
| **6. Federation ceiling** | HTTP peer: channel Bearer ∩ `X-ISPF-On-Behalf-Of-*` roles; channel is a **ceiling**, not an escalation path. Tunnel uses trusted-channel principal. | `FederationOnBehalfOfFilter`, [security § Per-variable ACL](../security.md) |
| **7. Secrets** | Ciphertext in vault / federation columns under AES-GCM envelope ([0065](0065-secrets-key-rotation.md)). Secrets are **not** object-tree variables. | `IspfSecretCipher`, `PlatformCredentialService` |

Denial order: unauthenticated → RBAC → tenant path visibility → object ACL → member variable ACL. Fail closed where documented (e.g. hub omits remote-only proxy variables without local ACL metadata).

### 2. Hard rules (must not regress)

1. **`admin` + non-null `tenant_id` is forbidden.** Global admin always bypasses tenant scope; `tenant-admin` never does.
2. **`tenant-admin` cannot** create tenants, see other tenants, grant global `admin`, or use the platform/local JDBC catalog (`TenantLocalDataAccessGuard`).
3. **Interactive user and agent data paths use MEMBER** for variable/history/export/analytics/WebSocket/Haystack/Brick/federated proxy-on-behalf-of. Schedulers and binding engines remain SYSTEM.
4. **Federation on-behalf-of roles = intersection** with the authenticated channel roles (no privilege escalation via headers).
5. **Secrets-key** may be YAML/env or DATABASE UI; rotation follows [0065](0065-secrets-key-rotation.md). Do not store master keys or plaintext credentials in the object tree.
6. **Production defaults:** `local-role-header-enabled=false`; prefer OIDC/`prod` profile; MFA optional until enabled (`ispf.security.mfa.*`).

### 3. Tenancy honesty

| Mode / feature | Status |
|----------------|--------|
| Logical SaaS A≠B (path + API + `tenant-admin`) | **Done** (default `ispf.tenant.isolation-mode=logical`) |
| Sole-tenant virtual root | **Done** |
| OIDC `tenant_id` claim (`ispf.tenant.oidc-tenant-claim`) | **Done** |
| PostgreSQL RLS (`ispf.tenant.db-row-isolation`, default true) | **Done** on PostgreSQL; **no-op on H2** |
| Hard mode schema provision/drop (`isolation-mode=hard`) | **Done** (hooks); platform tables still shared + RLS |
| Physical per-tenant table routing for all platform tables | **Optional / not claimed** |

### 4. MFA and identity follow-ups (explicit non-goals of this ADR)

- **TOTP GA** (BL-153) is the local MFA path — shipped.
- **WebAuthn / IdP OTP** remain [0056](0056-webauthn-idp-mfa.md) (**Proposed**, BL-194) until a named tender unparks them.
- Hired pen-test (G-01) is evidence work, not a model change — see [pen-test-scope](../pen-test-scope.md).

### 5. Operator / agent profiles

Admin MCP / AI sessions use the full tool catalog under the caller's RBAC + MEMBER ACL. Operator copilot / `AgentProfile.OPERATOR` is further restricted by `OperatorAgentToolAllowlist` ([0006](0006-mcp-agent-tool-adapter.md)) — mutating platform tools stay out of operator mode.

### 6. Adjacent honesty (not owned here)

| Topic | Owner | Claim from 0066 |
|-------|-------|-----------------|
| Secrets envelope + rotation | [0065](0065-secrets-key-rotation.md) | In-process AES-GCM Done |
| External KMS / HSM / GOST | Follow-up (0076 when merged) | **Non-claim** |
| Ui-pack same-origin / CSP | Follow-up (0073 when merged) | **Non-claim** |
| WebAuthn / IdP OTP | [0056](0056-webauthn-idp-mfa.md) | Proposed |

## Consequences

- Runbooks stay authoritative for how-to; this ADR is the **product claim boundary** for tenders, scorecard Security row, and agent teaching.
- New security features must amend this ADR (or add a child ADR and link it here) when they change a hard rule or honesty cell.
- Docs: short pointers from [security](../security.md) and [multi-tenant](../multi-tenant.md).
- Scorecard Security may cite this layered model; do not jump to 10/10 without pen-test G-01 evidence and without clearing adjacent Gaps above.

### Risks

- Readers may treat the ADR as an implementation checklist — mitigated by linking runbooks and “Does not change runtime” status.
- Hard-mode wording can be oversold as full physical isolation — honesty table forbids that claim.

## Related

- [security](../security.md), [multi-tenant](../multi-tenant.md), [federation](../federation.md)
- [0056-webauthn-idp-mfa](0056-webauthn-idp-mfa.md), [0065-secrets-key-rotation](0065-secrets-key-rotation.md)
- [0006-mcp-agent-tool-adapter](0006-mcp-agent-tool-adapter.md)
- [compliance-tender-pack](../compliance-tender-pack.md) (G-03 / G-05)
