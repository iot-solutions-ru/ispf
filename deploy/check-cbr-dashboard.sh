#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ispf-auth.sh
source "${SCRIPT_DIR}/ispf-auth.sh"
API="${API:-http://127.0.0.1:8080}"
AUTH="$(ispf_auth_header)"
curl -s -H "$AUTH" \
  "${API}/api/v1/dashboards/by-path?path=root.platform.dashboards.cbr-rates-dashboard" \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
w=d['layout']['widgets'][0]
cj=w.get('columnsJson')
print('columnsJson type:', type(cj).__name__)
print('columnsJson value:', repr(cj)[:500])
if isinstance(cj, str):
    try:
        cols=json.loads(cj)
        print('parsed columns:', len(cols), cols)
    except Exception as e:
        print('PARSE ERROR:', e)
elif isinstance(cj, list):
    print('columnsJson is ARRAY (bug):', cj)
else:
    print('columnsJson missing or wrong type')
"
