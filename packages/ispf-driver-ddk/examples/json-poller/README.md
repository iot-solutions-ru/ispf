# JSON Poller (DDK reference)

Poll-only HTTP JSON poller (DDK reference). Point mapping: `jsonPath:$.field.name`. GETs `baseUrl` and extracts the path.

Copy to `packages/ispf-driver-json-poller/` and wire real HTTP fetch in `readPoints`.
