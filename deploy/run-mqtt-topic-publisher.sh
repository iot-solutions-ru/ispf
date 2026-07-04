#!/usr/bin/env bash
set -euo pipefail
/opt/ispf/loadtest/venv/bin/python /opt/ispf/loadtest/mqtt-topic-publisher.py "$@"
