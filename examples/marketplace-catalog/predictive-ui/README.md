# predictive-ui (ISPF Marketplace ui-pack)

Hosted React SPA for `appId=predictive` (ADR-0054).

| File | Role |
|------|------|
| `ui-pack.json` | Pack manifest |
| `predictive-ui-1.2.0.zip` | Static SPA (index.html + assets) |
| `listing.manifest.json` | Marketplace listing |

## Install

```bash
# after application predictive is registered
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "$ISPF/api/v1/marketplace/ui-packs/predictive-ui/install"
```

Open `https://<ispf-host>/apps/predictive/`.
