# Air-gap deployment (BL-128)

Runbook for installing and updating ISPF on hosts **without outbound internet**. Complements [DEPLOYMENT.md](DEPLOYMENT.md) (online quick start) and [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) (RSA bundle / driver pack licenses).

## When to use

| Scenario | Approach |
|----------|----------|
| Lab / factory DMZ, no egress | Air-gap bundle + `air-gap-apply.sh` |
| Connected Linux + Docker | [Production quick start](DEPLOYMENT.md#production-quick-start-bl-127) (`prod-quickstart.sh`) |
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
| `images/prod-stack.tar` | `docker save` of postgres, redis, temurin JRE, nginx |
| `deploy/docker-compose.air-gap.yml` | Self-contained stack |
| `MANIFEST.json` / `CHECKSUMS.sha256` | Version + integrity |

Optional `--with-clickhouse` adds `clickhouse/clickhouse-server:24.8` to the image tar (enable CH env vars separately — see [DEPLOYMENT.md § ClickHouse](DEPLOYMENT.md#clickhouse-variable-history-prod-playbook-bl-114)).

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
- [ ] Record SHA256 of archive for transfer integrity
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

## Commercial licensing flow

Air-gap sites typically run with strict bundle trust. Align with [BL-100](EXCELLENCE_BACKLOG.md#bl-100--bundle-trust-signing-optional):

| Step | Action |
|------|--------|
| 1 | On target (once): `GET /api/v1/platform/installation-id` → copy hex ID |
| 2 | On vendor build machine (connected): `python tools/license-builder/sign-bundle.py` with `--installation-id` ([tools/license-builder/README.md](../tools/license-builder/README.md)) |
| 3 | Deploy signed app manifest via admin API or import UI **after** platform is up |
| 4 | Set on target before/at apply:

```bash
export ISPF_LICENSE_PUBLIC_KEY_PEM="$(cat license-public.pem)"
export ISPF_LICENSE_ENFORCE=true
export ISPF_LICENSE_REQUIRE_SIGNED_BUNDLES=true
```

| 5 | Licensed **driver packs** use the same public key; include packs in bundle (`air-gap-pack.sh` default) or add via staging tar |

Unsigned reference apps (Apache) deploy when `require-signed-bundles=false`. Production marketplace should keep `require-signed-bundles=true`.

Key rotation without internet: ship updated `license-public.pem` (multiple PEM blocks supported) and re-signed bundles in the **next** air-gap archive — see [COMMERCIAL_LICENSING.md § Production key rotation](COMMERCIAL_LICENSING.md#production-key-rotation-ops).

---

## systemd install (no Docker on app host)

Some sites run JAR + nginx on bare metal and only use Docker for PostgreSQL on a adjacent host.

1. Extract bundle; copy `artifacts/ispf-server.jar` → `/opt/ispf/`
2. Unzip `artifacts/web-console.zip` → `/opt/ispf/web-console/`
3. Extract `artifacts/driver-packs.tar.gz` → `/opt/ispf/data/drivers/`
4. Configure `/opt/ispf/ispf-server.env` (DB URL, license vars)
5. Use `deploy/remote-setup-ispf.sh` or existing unit files from [DEPLOYMENT.md § Remote host](DEPLOYMENT.md#remote-host-systemd--nginx)
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
| `docker load` fails | Archive truncated; re-verify SHA256 |
| Health check timeout | `docker compose logs ispf-server`; DB credentials |
| 403 on app deploy | License / `require-signed-bundles`; installation ID mismatch |
| Empty driver list | `artifacts/drivers/` not populated; rebuild with driver packs |

---

## Related documents

- [DEPLOYMENT.md](DEPLOYMENT.md) — online deploy, bundle signing, ClickHouse
- [COMMERCIAL_LICENSING.md](COMMERCIAL_LICENSING.md) — license claims, enforce flags
- [LICENSED_DRIVER_PACKS.md](LICENSED_DRIVER_PACKS.md) — driver pack layout
- [tools/license-builder/README.md](../tools/license-builder/README.md) — sign bundles offline on vendor side
