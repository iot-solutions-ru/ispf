#!/usr/bin/env python3
"""Emergency pilot tuning: stop SNMP drivers + disable elastic ingress threads."""
import sys

import paramiko

HOST = "185.246.66.158"
USER = "root"
BASE = "http://127.0.0.1:8080"


def main() -> None:
    password = sys.argv[1] if len(sys.argv) > 1 else sys.environ.get("ISPF_SSH_PASS")
    if not password:
        print("Usage: tune-pilot-load.py <ssh-password>", file=sys.stderr)
        sys.exit(2)

    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    try:
        client.connect(HOST, username=USER, password=password, timeout=25)
    except paramiko.SSHException as exc:
        print(f"Host key for {HOST} not trusted: {exc}", file=sys.stderr)
        print("SSH to the host once manually to add it to known_hosts.", file=sys.stderr)
        sys.exit(2)

    script = r"""set -e
ENV=/opt/ispf/ispf-server.env
touch "$ENV"
grep -q '^ISPF_DRIVER_INGRESS_BUFFER_ELASTIC=' "$ENV" \
  && sed -i 's/^ISPF_DRIVER_INGRESS_BUFFER_ELASTIC=.*/ISPF_DRIVER_INGRESS_BUFFER_ELASTIC=false/' "$ENV" \
  || echo 'ISPF_DRIVER_INGRESS_BUFFER_ELASTIC=false' >> "$ENV"
grep -q '^ISPF_DRIVER_INGRESS_BUFFER_THREADS=' "$ENV" \
  && sed -i 's/^ISPF_DRIVER_INGRESS_BUFFER_THREADS=.*/ISPF_DRIVER_INGRESS_BUFFER_THREADS=1/' "$ENV" \
  || echo 'ISPF_DRIVER_INGRESS_BUFFER_THREADS=1' >> "$ENV"
grep -q '^ISPF_DRIVER_INGRESS_BUFFER_THREADS_MAX=' "$ENV" \
  && sed -i 's/^ISPF_DRIVER_INGRESS_BUFFER_THREADS_MAX=.*/ISPF_DRIVER_INGRESS_BUFFER_THREADS_MAX=2/' "$ENV" \
  || echo 'ISPF_DRIVER_INGRESS_BUFFER_THREADS_MAX=2' >> "$ENV"
echo "=== ispf-server.env ingress settings ==="
grep ISPF_DRIVER_INGRESS_BUFFER "$ENV" || true
systemctl restart ispf-server
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1:8080/api/v1/info >/dev/null; then echo "ISPF up"; break; fi
  sleep 2
done
TUNED_OK
"""

    stdin, stdout, stderr = client.exec_command("bash -s", timeout=180)
    stdin.write(script.encode())
    stdin.flush()
    stdin.channel.shutdown_write()
    out = (stdout.read() + stderr.read()).decode("utf-8", errors="replace")
    sys.stdout.buffer.write(out.encode("utf-8", errors="replace"))
    client.close()
    if "TUNED_OK" not in out:
        sys.exit(1)


if __name__ == "__main__":
    main()
