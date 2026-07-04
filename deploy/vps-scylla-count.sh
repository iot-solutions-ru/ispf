#!/usr/bin/env bash
docker exec ispf-scylla cqlsh -e 'SELECT COUNT(*) FROM ispf.event_history;' 2>&1 | tail -8
docker exec ispf-scylla cqlsh -e "SELECT COUNT(*) FROM ispf.event_history WHERE object_path='root.platform.devices.loadtest-mqtt-dev-00001';" 2>&1 | tail -8
