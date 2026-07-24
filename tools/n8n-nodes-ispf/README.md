# n8n Nodes — ISPF

Community node package for [n8n](https://n8n.io/) that calls the **IoT Solutions Platform (ISPF)** REST API.

Pattern matches connectors like [n8n-nodes-ric](https://github.com/Rightech/n8n-nodes-ric): n8n stays the external orchestrator; ISPF remains the OT/platform system of record.

## Install (local / self-hosted n8n)

```bash
cd tools/n8n-nodes-ispf
npm install
npm run build

# Install into n8n custom nodes folder
mkdir -p ~/.n8n/nodes   # Windows: %USERPROFILE%\.n8n\nodes
cd ~/.n8n/nodes
npm init -y
npm install /absolute/path/to/IoT-Solutions-Platform-1/tools/n8n-nodes-ispf
```

Restart n8n. The **ISPF** node appears in the node picker.

### Dev with custom extensions (recommended)

```powershell
# Windows — builds package and starts n8n with this folder loaded
.\scripts\start-n8n.ps1
```

Or manually:

```powershell
$env:N8N_CUSTOM_EXTENSIONS = (Resolve-Path .).Path
$env:N8N_SECURE_COOKIE = "false"   # needed for http://localhost
n8n start
```

**Node.js note:** n8n `2.31+` wants Node `>=22.22`. On Node `22.14`, use `n8n@2.5.x` (`npm i -g n8n@2.5.0`).

## Credentials

| Field | Example |
|-------|---------|
| Base URL | `http://localhost:8080` |
| Access Token | Bearer token from login |

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin\"}"
```

Use the `token` field from the JSON response. Credential test calls `GET /api/v1/auth/me`.

## Operations (MVP)

| Resource | Operations |
|----------|------------|
| Object | Get, List Children, Get Editor |
| Variable | Get, List, Set, Get History |
| Event | Fire, List Journal |
| Function | Invoke, BFF Invoke |
| Workflow | Get, Run |
| Platform | Get Info, Get Me |

API reference: `docs/en/api.md` in the ISPF repo.

## Build

```bash
npm install
npm run build
```

Output is under `dist/` (what n8n loads).
