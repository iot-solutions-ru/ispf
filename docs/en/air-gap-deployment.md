# Air-gap deployment (BL-128)

> **Status:** Stable — Offline installs. Hub: [doc-status.md](doc-status.md).

Runbook for installing and updating ISPF on hosts **without outbound internet**. Complements [deployment](deployment.md) (online quick start) and [commercial-licensing](commercial-licensing.md) (RSA bundle / driver pack licenses).

## When to use

| Scenario | Approach |
|----------|----------|
| Lab / factory DMZ, no egress | Air-gap bundle + `air-gap-apply.sh` |
| Connected Linux + Docker | [Production quick start](deployment.md) (`prod-quickstart.sh`) |
| Managed VPS with SSH | `deploy/vps-deploy-direct.ps1` (direct SCP) |

## Architecture

```text
[Build host — has internet]
  air-gap-pack.sh → ispf-airgap-<version>.tar.gz
        │  (USB / sneakernet / internal file share)
        ▼
[Target host — no internet]
  air-gap-apply.sh → docker load + compose up
```

Bundle contents:

| Path | Description |
|------|-------------|
| `artifacts/ispf-server.jar` | Platform server |
| `artifacts/web-console/` | Built SPA (also `web-console.zip`) |
| `artifacts/driver-packs.tar.gz` | Optional; extracted to `artifacts/drivers/` |
| `images/prod-stack.tar` | `docker save` of pinned postgres, redis, temurin JRE, nginx (+ optional ClickHouse) |
| `deploy/docker-compose.air-gap.yml` | Self-contained stack |
| `deploy/air-gap-images.env` | Pinned image tags used at pack time |
| `MANIFEST.json` / `CHECKSUMS.sha256` | Version + integrity (corruption check) |

Optional `--with-clickhouse` adds `clickhouse/clickhouse-server:24.8` to the image tar (enable CH env vars separately — see [DEPLOYMENT.md § ClickHouse](deployment.md)).

---

## Checklist — build side (connected)

Prerequisites on **build machine**: git checkout, JDK 25, Node.js 20+, Docker, Gradle wrapper.

- [ ] Choose release version (e.g. `0.9.32`)
- [ ] Build bundle:

```bash
bash deploy/air-gap-pack.sh --version 0.9.32
```

- [ ] Optional flags: `--skip-build`, `--skip-driver-packs`, `--with-clickhouse`
- [ ] Verify output: `build/air-gap/ispf-airgap-0.9.32.tar.gz`
- [ ] Record **out-of-band** SHA256 of archive for transfer integrity (CHECKSUMS inside bundle ≠ anti-tamper)
- [ ] Optional: `--with-clickhouse` adds ClickHouse image + compose profile
- [ ] **Commercial apps:** sign bundle manifests on build side before packaging apps (see § Licensing below)
- [ ] Copy archive to removable media or internal artifact repository

---

## Checklist — target side (air-gapped)

Prerequisites on **target host**: Docker Engine + Compose v2 only (no JDK/Node required).

- [ ] Transfer `ispf-airgap-<version>.tar.gz`
- [ ] Verify archive SHA256 matches build-side record
- [ ] Set commercial license env (if applicable) — see § Licensing
- [ ] Apply:

```bash
bash deploy/air-gap-apply.sh /media/ispf-airgap-0.9.32.tar.gz
```

Or extract first, then apply directory:

```bash
tar -xzf ispf-airgap-0.9.32.tar.gz
bash ispf-airgap-0.9.32/deploy/air-gap-apply.sh ispf-airgap-0.9.32/
```

- [ ] Confirm health: `curl http://localhost:8080/api/v1/info`
- [ ] Open UI: http://localhost:8088/
- [ ] Note `installationId` for future license issuance: `GET /api/v1/platform/installation-id`

---

## Update without internet

1. On build host: create newer `ispf-airgap-<new-version>.tar.gz`.
2. Transfer to target.
3. On target, stop running stack (keeps DB volume):

```bash
cd /path/to/current/ispf-airgap-<old>/
docker compose -f deploy/docker-compose.air-gap.yml down
```

4. Apply new bundle (loads images if changed, replaces JAR/UI/drivers):

```bash
bash deploy/air-gap-apply.sh /media/ispf-airgap-<new-version>.tar.gz
```

5. Flyway migrations run automatically on server start. For systemd/VPS-style installs use `deploy/apply-platform-update.sh` with staging dir containing `ispf-server.jar`, `web-console.zip`, optional `driver-packs.tar.gz`.

**Rollback:** keep previous `.tar.gz`; re-apply after `docker compose down`.

---

## Commercial licensing flow (RSA — Done)

Air-gap compose defaults to **prod license posture**: `ISPF_LICENSE_ENFORCE=true` and `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true` (`deploy/docker-compose.air-gap.yml`). Export the public key **before** `air-gap-apply.sh` so the server can verify app manifests.

| Step | Action |
|------|--------|
| 1 | On target (once): `GET /api/v1/platform/installation-id` → copy hex ID |
| 2 | On vendor build machine (connected): `python tools/license-builder/sign-bundle.py` with `--installation-id` ([tools/license-builder/README.md](../../tools/license-builder/README.md)) |
| 3 | Deploy signed app manifest via admin API or import UI **after** platform is up |
| 4 | Set on target before apply (public key required when gate is on):

```bash
export ISPF_LICENSE_PUBLIC_KEY_PEM="$(cat license-public.pem)"
# enforce + require-signed default true in docker-compose.air-gap.yml
bash deploy/air-gap-apply.sh /media/ispf-airgap-<version>.tar.gz
```

| 5 | Licensed **driver packs** use the same public key; include packs in bundle (`air-gap-pack.sh` default) or add via staging tar |
| 6 | Optional smoke: `bash deploy/tools/air-gap-signed-gate-smoke.sh` (unsigned → 403, signed → 2xx) |

**Lab override** (not production posture): `ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=false` / `ISPF_LICENSE_ENFORCE=false` before apply.

Key rotation without internet: ship updated `license-public.pem` (multiple PEM blocks supported) and re-signed bundles in the **next** air-gap archive — see [commercial-licensing § Production key rotation](commercial-licensing.md).

Evidence / claim table: [docs/evidence/air-gap-signing/](../evidence/air-gap-signing/README.md).

---

## Publisher signatures (Cosign) — Gap

| Claim | Status |
|-------|--------|
| RSA app-bundle `license` + require-signed on air-gap stack | **Done** (this section) |
| `CHECKSUMS.sha256` in the air-gap tar | **Done** as corruption check — not a publisher signature |
| Cosign / sigstore image or archive signatures | **Gap** — not implemented in this repository |
| Dated Cosign verify on a customer host | **Gap** — record under [air-gap-signing evidence](../evidence/air-gap-signing/README.md) when a vendor run exists |

Optional operator/vendor steps (outside ISPF): sign Docker digests with Cosign on the connected build machine; on the air-gap host run `cosign verify` against the loaded images and archive the transcript. ISPF does not ship a Cosign binary or CI verify job.

---

## systemd install (no Docker on app host)

Some sites run JAR + nginx on bare metal and only use Docker for PostgreSQL on a adjacent host.

1. Extract bundle; copy `artifacts/ispf-server.jar` → `/opt/ispf/`
2. Unzip `artifacts/web-console.zip` → `/opt/ispf/web-console/`
3. Extract `artifacts/driver-packs.tar.gz` → `/opt/ispf/data/drivers/`
4. Configure `/opt/ispf/ispf-server.env` (DB URL, license vars)
5. Use `deploy/remote-setup-ispf.sh` or existing unit files from [DEPLOYMENT.md § Remote host](deployment.md)
6. Updates: stage dir + `deploy/apply-platform-update.sh /opt/ispf/staging/<version>`

---

## Manual artifact list (without pack script)

If you cannot run `air-gap-pack.sh` on Linux, assemble the same layout manually:

| Artifact | Source |
|----------|--------|
| `ispf-server.jar` | `./gradlew :packages:ispf-server:bootJar -Pversion=<ver>` |
| `web-console.zip` | `apps/web-console/dist` → zip |
| `driver-packs.tar.gz` | `./gradlew syncAllDriverPacks` → tar `build/driver-packs/` |
| Docker images | `docker pull` + `docker save` for images listed in `MANIFEST.json` |

---

## Troubleshooting

| Symptom | Check |
|---------|-------|
| `docker load` fails | Archive truncated; re-verify out-of-band SHA256 |
| `Invalid bundle kind` | Archive is not an ISPF air-gap bundle |
| Health check timeout | `docker compose logs ispf-server`; DB credentials |
| 403 on app deploy | License / `require-signed-bundles`; installation ID mismatch |
| Empty driver list | `artifacts/drivers/` not populated; rebuild with driver packs |

---

## Local LLM demostand (air-gapped site)

Playbook for pointing the agent at an **OpenAI-compatible model that stays on the site**. Status: **Partial**. The server already reads `ISPF_AI_BASE_URL` (`ispf.ai.base-url` in `application.yml`). This section does not add image signing or a dated site soak.

### Claim boundary — "data never leaves the site"

- **In scope:** agent LLM calls go to the configured base URL (`OpenAiCompatibleLlmProvider` → `{base}/chat/completions`, or `OllamaLlmProvider` → `{base}/api/chat`). If that URL is a host on the site LAN, prompts and completions for the agent do not need a path to the public internet.
- **Not a claim:** ISPF does not prove that model weights, the OS, or the Docker images were built with no network. `CHECKSUMS.sha256` inside the air-gap bundle detects corruption. It is not a publisher signature. **Cosign / sigstore image signing remains Gap** — see § Publisher signatures (Cosign). RSA app-bundle require-signed on the air-gap stack is separate and **Done**.
- **Not a claim:** marketplace sync, license issuance, and any other client still follow their own URLs. An air-gapped host should not set `ISPF_AI_BASE_URL` (or `ISPF_MARKETPLACE_DEFAULT_URL`) to a public endpoint. A wrong base URL sends prompts wherever that URL points.
- **Not a claim:** a dated air-gapped demostand journal is not archived yet. Record one under [docs/evidence/air-gap-llm/](../evidence/air-gap-llm/README.md). Until that folder has a real run, the scorecard air-gap axis stays **Partial**.

### Offline install

1. On a connected build machine, create the platform bundle with `deploy/air-gap-pack.sh` (checklist above) and move `ispf-airgap-<version>.tar.gz` by sneakernet.
2. Move the local model runtime and weights the same way. The air-gap tar does **not** contain model weights.
3. On the target: `deploy/air-gap-apply.sh`, then start the local server so it listens only on the site network.

### Point the agent at the local model

`ISPF_AI_BASE_URL` maps to `ispf.ai.base-url`. A host-only OpenAI-compatible URL (no path) receives `/v1` from `LlmHttpSupport.normalizeOpenAiCompatibleBaseUrl`. Ollama's native API does not use `/v1`.

OpenAI-compatible server on the site (vLLM, llama.cpp, Ollama's OpenAI shim, …):

```bash
export ISPF_AI_ENABLED=true
export ISPF_AI_PROVIDER=openai-compatible
export ISPF_AI_BASE_URL=http://127.0.0.1:8000/v1
export ISPF_AI_MODEL=<model-id-served-locally>
export ISPF_AI_API_KEY=local-not-used
```

Native Ollama on the site:

```bash
export ISPF_AI_ENABLED=true
export ISPF_AI_PROVIDER=ollama
export ISPF_AI_BASE_URL=http://127.0.0.1:11434
export ISPF_AI_MODEL=<model-tag>
```

Provider details: [ai-development](ai-development.md) § LlmProvider SPI. Do not point `ISPF_AI_BASE_URL` at a public vendor API when the site rule is that prompts stay on premises.

### What to record

After a real run, add a dated folder next to the stub [docs/evidence/air-gap-llm/README.md](../evidence/air-gap-llm/README.md): base URL host (redacted), provider, model id, and whether the NIC had a default route. Do not commit API keys.

---

## Related documents

- [deployment](deployment.md) — online deploy, bundle signing, ClickHouse
- [commercial-licensing](commercial-licensing.md) — license claims, enforce flags
- [licensed-driver-packs](licensed-driver-packs.md) — driver pack layout
- [tools/license-builder/README.md](readme.md) — sign bundles offline on vendor side
- [ai-development](ai-development.md) — `ISPF_AI_BASE_URL` / provider SPI
- [evidence/air-gap-llm](../evidence/air-gap-llm/README.md) — local-LLM demostand evidence stub (**Partial**)
