#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ispf-auth.sh
source "${SCRIPT_DIR}/ispf-auth.sh"
API="${API:-http://127.0.0.1:8080}"
AUTH="$(ispf_auth_header)"
for i in 1 2 3; do
  curl -s -H "$AUTH" \
    "${API}/api/v1/objects/by-path/editor?path=root.platform.devices.snmp-localhost" \
    | python3 -c '
import sys,json
d=json.load(sys.stdin)
vars={v["name"]:v for v in d["variables"]}
for n in ["ifInOctets","ifInOctetsRate","ifOutOctets","ifOutOctetsRate"]:
 v=vars[n]
 print(n, "val=", v["value"]["rows"][0].get("value"), "upd=", v["updatedAt"])
'
  echo "--- poll $i ---"
  sleep 6
done
