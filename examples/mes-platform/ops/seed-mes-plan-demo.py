#!/usr/bin/env python3
"""Publish a demo official plan on mes-demo so Official/Monitor screens are not empty."""
from __future__ import annotations

import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
HUB = "root.platform.singleton-blueprints.mes-platform-hub-v1"


def login() -> str:
    req = urllib.request.Request(
        f"{BASE}/api/v1/auth/login",
        data=json.dumps({"username": "admin", "password": "admin"}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    return json.load(urllib.request.urlopen(req))["token"]


def invoke(token: str, function_name: str, row: dict) -> dict:
    fields = [{"name": k, "type": "STRING"} for k in row]
    body = {
        "objectPath": HUB,
        "functionName": function_name,
        "input": {"schema": {"name": "in", "fields": fields}, "rows": [row]},
        "wireProfile": "ispf-operator-v1",
    }
    req = urllib.request.Request(
        f"{BASE}/api/v1/bff/invoke",
        data=json.dumps(body).encode(),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return json.load(resp)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise SystemExit(f"{function_name} HTTP {exc.code}: {detail[:500]}") from exc


def main() -> None:
    token = login()
    pubs = invoke(token, "mes_plan_listPubs", {"siteCode": "PLANT-A"})
    existing = pubs.get("result") if isinstance(pubs.get("result"), list) else (pubs.get("result") or {}).get("rows")
    if isinstance(existing, list) and existing:
        print("already_has_pubs", len(existing), existing[0])
    else:
        steps = [
            (
                "mes_plan_ingestCandidate",
                {
                    "candidateId": "CAND-DEMO-1",
                    "siteCode": "PLANT-A",
                    "skuCode": "MAT-WIDGET-A01",
                    "plannedQty": "100",
                    "uom": "EA",
                    "qualityStatus": "released",
                    "idempotencyKey": "IF-04:CAND-DEMO-1",
                },
            ),
            ("mes_plan_validate", {"candidateId": "CAND-DEMO-1"}),
            ("mes_plan_publish", {"candidateId": "CAND-DEMO-1", "actor": "admin"}),
            ("mes_plan_acceptPub", {"pubId": "PUB-CAND-DEMO-1", "actor": "shop"}),
            (
                "mes_plan_recordFact",
                {
                    "factId": "FACT-DEMO-1",
                    "pubId": "PUB-CAND-DEMO-1",
                    "actualQty": "40",
                    "delayMinutes": "45",
                    "reasonCode": "DELAY",
                },
            ),
            (
                "mes_plan_setDelivery",
                {
                    "pubId": "PUB-CAND-DEMO-1",
                    "recipientCode": "SPP",
                    "status": "delivered",
                    "errorText": "",
                },
            ),
            (
                "mes_plan_setDelivery",
                {
                    "pubId": "PUB-CAND-DEMO-1",
                    "recipientCode": "SHOP",
                    "status": "delivered",
                    "errorText": "",
                },
            ),
            (
                "mes_plan_openAlert",
                {
                    "alertId": "AL-DEMO-1",
                    "pubId": "PUB-CAND-DEMO-1",
                    "siteCode": "PLANT-A",
                    "severity": "Warning",
                    "alertType": "delay",
                    "message": "Demo delay on PLANT-A",
                },
            ),
        ]
        for name, row in steps:
            out = invoke(token, name, row)
            print(name, out.get("error_code"), json.dumps(out.get("result"), ensure_ascii=False)[:180])

    print("pubs", invoke(token, "mes_plan_listPubs", {"siteCode": "PLANT-A"}))
    print("dels", invoke(token, "mes_plan_listDeliveriesBySite", {"siteCode": "PLANT-A"}))
    print("fact", invoke(token, "mes_plan_planFactBySite", {"siteCode": "PLANT-A"}))
    print("devs", invoke(token, "mes_plan_listDeviationsBySite", {"siteCode": "PLANT-A"}))


if __name__ == "__main__":
    main()
