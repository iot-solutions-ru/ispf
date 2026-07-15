# Local overrides (gitignored)

Everything under this directory is **gitignored** except this README.

Copy files from `../env/` and `../compose/` to `~/ispf` on lab hosts, then create **untracked** overrides here or on the server:

```bash
cp ../env/lab-loadgen.env ./lab-loadgen.local.env
# edit: real LAN IPs, SSH user@host, passwords
```

Keep personal suite notes here (real hosts), e.g. `ordered-suite-operator-notes.md`. Public anonymized baseline: [`../reports/ordered-suite-i01-i08.md`](../reports/ordered-suite-i01-i08.md).

Never commit `*.local.env`, SSH keys, `lab_ssh.py`, or operator notes with public IPs.
