# Local deploy / lab tooling (not in git)

Copy this file to `deploy/local/README.md` on your workstation. The entire `deploy/local/` tree is **gitignored** — safe for SSH keys helpers, lab nginx, BL-210 gates, soak scripts.

## First-time lab SSH

```bash
# One-time password (never commit):
# export ISPF_LAB_PASSWORD=...
python deploy/local/tools/lab-ssh-install-key.py
cp deploy/local/lab_ssh.example.py deploy/lab_ssh.py   # deploy/lab_ssh.py is gitignored
ssh ispf-lab
```

Env vars: see root [`.env.example`](../../.env.example) (`ISPF_LAB_*`, `ISPF_BENCH_*`).

## Enterprise L gates (BL-210)

```bash
export ISPF_BENCH_BASE_URL=http://127.0.0.1:8000
bash deploy/local/tools/run-enterprise-l-gates.sh
```

See [`examples/analytics-platform/enterprise-l/README.md`](../../examples/analytics-platform/enterprise-l/README.md).

## Directory layout

| Path | Contents |
|------|----------|
| `tools/` | Scale/historian gates, catalog seed, CH 1B seed, agent soak, historian demo setup |
| `tools/debug/` | Ad-hoc `_check_*` API probes |
| `nginx/` | `cluster-lab.conf`, `lab.conf` |
| `scripts/` | Lab deploy PS1, dashboard scale helpers |
| `lib/` | `mqtt_loadtest_lib.py`, `ispf_api_cli.py` |
| `lab_ssh.example.py` | Template for `deploy/lab_ssh.py` |

## Lab compose (also gitignored)

`deploy/lab-cluster-compose.yml`, `deploy/lab-cluster-bootstrap.sh`, `deploy/run_lab_*.py` — referenced from [docs/en/cluster.md](../../docs/en/cluster.md).
