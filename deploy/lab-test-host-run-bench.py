#!/usr/bin/env python3
"""Run 8x2000 benchmark on lab (no wipe) for btop observation."""
from __future__ import annotations

import paramiko
from pathlib import Path

HOST = "84.42.21.226"
PORT = 5031
USER = "iot-solutions"
PASSWORD = "REDACTED_USE_ISPF_LAB_PASSWORD_ENV"
ROOT = "/home/iot-solutions/ispf"
DEPLOY = Path(__file__).resolve().parent

UPLOADS = [
    ("lab-mqtt-event-journal-multi-test.sh", f"{ROOT}/lab-mqtt-event-journal-multi-test.sh"),
    ("mqtt-emqtt-bench.sh", f"{ROOT}/loadtest/mqtt-emqtt-bench.sh"),
]


def main() -> int:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)

    sftp = client.open_sftp()
    for name, dest in UPLOADS:
        data = (DEPLOY / name).read_bytes().replace(b"\r\n", b"\n")
        with sftp.file(dest, "w") as f:
            f.write(data)
    sftp.close()

    cmd = (
        f"chmod +x {ROOT}/lab-mqtt-event-journal-multi-test.sh {ROOT}/loadtest/mqtt-emqtt-bench.sh && "
        f"echo '=== START $(date -Is) — watch btop now (~90s load) ===' && "
        f"ISPF_LAB_ROOT={ROOT} ISPF_LAB_HTTP_PORT=8000 DEVICES=8 RATE_PER_DEVICE=2000 "
        f"WARMUP=15 PHASE=60 bash {ROOT}/lab-mqtt-event-journal-multi-test.sh"
    )
    print(f"lab {HOST}: starting 8x2000 benchmark (~90s)...")
    _, stdout, stderr = client.exec_command(cmd, timeout=600)
    out = stdout.read().decode(errors="replace")
    err = stderr.read().decode(errors="replace")
    code = stdout.channel.recv_exit_status()
    client.close()

    result = out + ("\nERR:\n" + err if err.strip() else "")
    Path(__file__).resolve().parent.parent.joinpath("tmp_bench_btop.txt").write_text(result, encoding="utf-8")
    import sys
    sys.stdout.buffer.write(result.encode("utf-8", errors="replace"))
    sys.stdout.buffer.write(f"\nexit {code}\n".encode())
    return code


if __name__ == "__main__":
    raise SystemExit(main())
