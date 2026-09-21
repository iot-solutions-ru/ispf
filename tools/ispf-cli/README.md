# ISPF CLI (`ispf`)

Solution-as-repo tooling (ADR-0060 W4): pack folder layouts to `bundle.json`, validate, diff against server export, deploy.

```bash
cd tools/ispf-cli && npm install
node bin/ispf.mjs pack ../../examples/demo-app -o ../../examples/demo-app/bundle.json
node bin/ispf.mjs validate ../../examples/demo-app --local
```

Requires Node 20+. Remote commands use `ISPF_BASE_URL` (default `http://localhost:8080`) and optional `ISPF_API_TOKEN`.
