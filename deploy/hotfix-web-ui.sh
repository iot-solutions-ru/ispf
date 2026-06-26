#!/bin/bash
set -euo pipefail
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
unzip -oq /tmp/web-console-hotfix.zip -d "$TMP"
rm -rf /opt/ispf/web-console/*
cp -a "$TMP"/. /opt/ispf/web-console/
chmod -R a+rX /opt/ispf/web-console
ls /opt/ispf/web-console/assets/ObjectTableWidgetView*.js /opt/ispf/web-console/assets/ChartWidgetView*.js
