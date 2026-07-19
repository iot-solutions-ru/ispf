#!/usr/bin/env bash
# Render deploy/nginx-ispf.conf for a real public host (repo template uses ispf.example.invalid).
# AI console is on PUBLIC_HOST. Optional second arg AI_HOST adds legacy redirect vhosts only
# when it differs from PUBLIC_HOST.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEMPLATE="${ROOT}/deploy/nginx-ispf.conf"
PUBLIC_HOST="${1:-ispf.example.invalid}"
AI_HOST="${2:-}"

if [[ ! -f "$TEMPLATE" ]]; then
  echo "Missing template: $TEMPLATE" >&2
  exit 1
fi

python3 - "$TEMPLATE" "$PUBLIC_HOST" "$AI_HOST" <<'PY'
import re
import sys
from pathlib import Path

template_path, public_host, ai_host = sys.argv[1], sys.argv[2], sys.argv[3].strip()
text = Path(template_path).read_text(encoding="utf-8")
text = text.replace("ispf.example.invalid", public_host)

def drop_blocks_for_host(config: str, host: str) -> str:
    pattern = re.compile(
        r"(?ms)^server\s*\{(?:[^{}]|\{(?:[^{}]|\{[^{}]*\})*\})*\}"
    )
    out = []
    last = 0
    for m in pattern.finditer(config):
        block = m.group(0)
        if host in block:
            prefix = config[last:m.start()].rstrip("\n")
            if prefix:
                out.append(prefix)
            last = m.end()
            continue
        out.append(config[last:m.end()])
        last = m.end()
    out.append(config[last:])
    return "\n".join(part for part in out if part is not None).strip() + "\n"

use_ai_alias = bool(ai_host) and ai_host != public_host
if not use_ai_alias:
    text = drop_blocks_for_host(text, "ai.example.invalid")
else:
    text = text.replace("ai.example.invalid", ai_host)

sys.stdout.write(text)
PY
