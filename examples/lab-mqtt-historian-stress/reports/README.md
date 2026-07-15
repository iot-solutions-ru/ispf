# Anonymized load-test report examples

Committed artifacts for [ordered-suite-i01-i08.md](ordered-suite-i01-i08.md). Numbers are from a split lab run on documentation addresses (`198.51.100.x`); hostnames and SSH users are placeholders.

| File | Scenario |
|------|----------|
| `ordered-suite-i01-i08.md` | Full I-01…I-08 narrative + tables |
| `example-i07-http-events-report.json` | Sanitized `events-load-test.py` phase summary |
| `example-i08-mqtt-ingress-history-report.json` | Sanitized `mqtt-ingress-load-test.py` phase summary |

**Do not commit** operator logs with real public IPs or SSH users. Keep those under gitignored [`../local/`](../local/) or `deploy/` on the workstation.
