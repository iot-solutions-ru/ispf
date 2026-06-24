#!/bin/bash
set -euo pipefail
# Re-apply known-good CBR rates overview dashboard layout (object-table columnsJson as string).
PATH_DASH=root.platform.dashboards.cbr-rates-dashboard
LAYOUT='{"columns":12,"rowHeight":72,"widgets":[{"id":"rates-table","type":"object-table","title":"Currency Rates","x":0,"y":0,"w":12,"h":4,"parentPath":"root.platform.devices.cbr-digital-twins","selectionKey":"currency","columnsJson":"[{\"variable\":\"currencyName\",\"label\":\"Currency\"},{\"variable\":\"currentRate\",\"label\":\"Current Rate\",\"decimals\":2},{\"variable\":\"previousRate\",\"label\":\"Previous Rate\"},{\"variable\":\"lastChangeDate\",\"label\":\"Last Change\"}]","rowTargetDashboard":"root.platform.dashboards.cbr-rates-detail","rowOpenMode":"navigate"}]}'
curl -sf -X PUT -H 'X-ISPF-Role: admin' -H 'Content-Type: application/json' \
  --data-binary "$LAYOUT" \
  "http://127.0.0.1:8080/api/v1/dashboards/by-path/layout?path=${PATH_DASH}"
echo
echo "Fixed layout for ${PATH_DASH}"
