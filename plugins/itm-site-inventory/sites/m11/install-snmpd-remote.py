#!/usr/bin/env python3
"""Install and start snmpd on ISPF pilot host for M11 demo SNMP polling."""
import sys
from pathlib import Path

import paramiko

HOST = "185.246.66.158"
USER = "root"
REPO = Path(__file__).resolve().parents[4]
CONF = REPO / "deploy" / "snmpd-ispf.conf"


def main() -> None:
    password = sys.argv[1] if len(sys.argv) > 1 else sys.environ.get("ISPF_SSH_PASS")
    if not password:
        print("Usage: install-snmpd-remote.py <ssh-password>", file=sys.stderr)
        sys.exit(2)
    if not CONF.is_file():
        print(f"Missing {CONF}", file=sys.stderr)
        sys.exit(2)

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=password, timeout=25)

    sftp = client.open_sftp()
    sftp.put(str(CONF), "/tmp/snmpd-ispf.conf")
    sftp.close()

    script = r"""set -e
export DEBIAN_FRONTEND=noninteractive
echo "=== Install snmpd ==="
apt-get update -qq
apt-get install -y -qq snmp snmpd

echo "=== Configure ISPF snmpd ==="
mkdir -p /etc/snmp/snmpd.conf.d
cp /tmp/snmpd-ispf.conf /etc/snmp/snmpd.conf.d/ispf.conf
if ! grep -q 'includeDir /etc/snmp/snmpd.conf.d' /etc/snmp/snmpd.conf 2>/dev/null; then
  echo 'includeDir /etc/snmp/snmpd.conf.d' >> /etc/snmp/snmpd.conf
fi
sed -i '/^rocommunity/d; /^rocommunity6/d; /^agentaddress/d' /etc/snmp/snmpd.conf

systemctl enable snmpd
systemctl restart snmpd
sleep 2

echo "=== Verify snmpd ==="
systemctl is-active snmpd
ss -ulnp | grep ':161 ' || true
snmpget -v2c -c public -t 2 127.0.0.1 1.3.6.1.2.1.1.5.0
snmpget -v2c -c public -t 2 127.0.0.1 1.3.6.1.2.1.2.2.1.8.2
echo "SNMP_OK"
"""

    stdin, stdout, stderr = client.exec_command("bash -s", timeout=300)
    stdin.write(script.encode())
    stdin.flush()
    stdin.channel.shutdown_write()
    out = (stdout.read() + stderr.read()).decode()
    sys.stdout.buffer.write(out.encode("utf-8", errors="replace"))
    sys.stdout.buffer.write(b"\n")
    client.close()

    if "SNMP_OK" not in out:
        sys.exit(1)
    print("snmpd ready on 127.0.0.1:161")


if __name__ == "__main__":
    main()
