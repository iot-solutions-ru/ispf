# Local overrides (gitignored)

Copy files from `../env/` and `../compose/` to `~/ispf` on lab hosts, then create **untracked** overrides here or on the server:

```bash
cp ../env/lab-loadgen.env ./lab-loadgen.local.env
# edit: real LAN IPs, SSH user@host, passwords
```

Never commit `*.local.env`, SSH keys, or `lab_ssh.py`.
