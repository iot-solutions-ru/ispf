#!/usr/bin/env python3
"""Remove paperclipai, openclaw-gateway; disable ollama on lab."""
from __future__ import annotations

import paramiko
from lab_ssh import HOST, PORT, USER, lab_password, connect_ssh, API_BASE



def run(c, cmd, timeout=300):
    print(">", cmd[:240], flush=True)
    _, o, e = c.exec_command(cmd, timeout=timeout)
    out = o.read().decode("utf-8", "replace")
    err = e.read().decode("utf-8", "replace")
    code = o.channel.recv_exit_status()
    if out.strip():
        safe = out[-12000:].encode("ascii", "replace").decode("ascii")
        print(safe, flush=True)
    if err.strip():
        safe_err = err[-3000:].encode("ascii", "replace").decode("ascii")
        print("STDERR:", safe_err, flush=True)
    print("exit", code, flush=True)
    return code, out, err


def main() -> int:
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, PORT, USER, lab_password(), timeout=60)

    print("=== INSPECT ===", flush=True)
    run(c, "ps aux | grep -iE 'paperclip|openclaw|ollama' | grep -v grep || true")
    run(c, "command -v pm2; pm2 list 2>/dev/null || true")
    run(c, "systemctl status ollama 2>&1 | head -15 || true")
    run(c, "ls -la ~/.paperclip 2>/dev/null; ls -la ~/.openclaw* 2>/dev/null; ls -la ~/openclaw* 2>/dev/null || true")

    print("\n=== STOP paperclipai ===", flush=True)
    steps = [
        "export PATH=\"$HOME/.npm-global/bin:$HOME/.local/bin:$PATH\"; "
        "command -v pm2 >/dev/null && pm2 stop all 2>/dev/null; "
        "command -v pm2 >/dev/null && pm2 delete all 2>/dev/null; "
        "command -v pm2 >/dev/null && pm2 kill 2>/dev/null; true",
        "pkill -u iot-solutions -f 'paperclipai run' 2>/dev/null || true",
        "pkill -u iot-solutions -f 'npm exec paperclipai' 2>/dev/null || true",
        "pkill -u iot-solutions -f paperclipai 2>/dev/null || true",
        "pkill -u iot-solutions -f embedded-postgres 2>/dev/null || true",
        "pkill -u iot-solutions -f '@embedded-postgres' 2>/dev/null || true",
        "pkill -u iot-solutions -f 'paperclip/instances' 2>/dev/null || true",
        "sleep 2",
        "rm -rf ~/.paperclip ~/.pm2 ~/.config/paperclip 2>/dev/null || true",
        "npm uninstall -g paperclipai 2>/dev/null || true",
    ]
    for s in steps:
        run(c, s)

    print("\n=== STOP openclaw-gateway ===", flush=True)
    steps2 = [
        "pkill -u iot-solutions -f openclaw-gateway 2>/dev/null || true",
        "pkill -u iot-solutions -f openclaw 2>/dev/null || true",
        "sleep 1",
        "rm -rf ~/.openclaw ~/.config/openclaw ~/openclaw-gateway ~/.local/share/openclaw 2>/dev/null || true",
        "npm uninstall -g openclaw 2>/dev/null || true",
        "npm uninstall -g @openclaw/gateway 2>/dev/null || true",
    ]
    for s in steps2:
        run(c, s)

    print("\n=== DISABLE ollama (sudo) ===", flush=True)
    pw = lab_password().replace("'", "'\"'\"'")
    ollama_steps = [
        f"echo '{pw}' | sudo -S systemctl stop ollama 2>&1",
        f"echo '{pw}' | sudo -S systemctl disable ollama 2>&1",
        "systemctl is-active ollama 2>&1 || true",
        "systemctl is-enabled ollama 2>&1 || true",
    ]
    for s in ollama_steps:
        code, _, err = run(c, s)
        if code != 0 and "sudo" in s and "password" in err.lower():
            print("WARN: sudo may require password — trying without passwordless sudo", flush=True)

    print("\n=== VERIFY ===", flush=True)
    run(c, "ps aux | grep -iE 'paperclip|openclaw|ollama' | grep -v grep || echo '(processes gone)'")
    run(c, "ss -tlnp 2>/dev/null | grep -E ':3100|:18789|:11434|:54329' || echo '(ports free)'")
    run(c, "systemctl is-enabled ollama 2>&1 || true")
    run(c, "ls -d ~/.paperclip ~/.openclaw ~/.pm2 2>/dev/null || echo '(dirs removed)'")

    c.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
