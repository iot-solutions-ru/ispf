#!/usr/bin/env python3
"""Normalize IoT Solutions vendor fields in marketplace-catalog listings."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "examples" / "marketplace-catalog"

VENDOR = {
    "vendorName": "IoT Solutions",
    "vendorLegalName": 'ООО "ИОТ РЕШЕНИЯ"',
    "vendorInn": "6950228075",
    "vendorSellerKind": "company",
    "vendorContactEmail": "info@vendor.example.invalid",
    "vendorContactPhone": "+7 980 630-93-33",
}

PRICING = {
    "mini-tec": ("paid", 19900),
    "mes-reference": ("paid", 9900),
}

DESCRIPTIONS = {
    "mini-tec": "Платный эталон — цифровой двойник мини-ТЭЦ: ГПУ, ГРПБ, РУМБ, ДГУ, мнемосхемы и operator UI",
    "mes-reference": "Платный эталон MES — наряды на отгрузку, резервуар, эстакада, журнал оператора",
    "building-hvac": "Бесплатный эталон — зоны комфорта, setpoint и Haystack-теги",
    "warehouse-reference": "Бесплатный эталон — список ячеек склада через BFF",
    "mes-platform": "MES platform: OEE, dispatch BPMN, quality SPC, ISA-88 batch lite",
    "mes-platform-production": "Сертифицированный MES walkthrough: OEE, dispatch, SPC, batch, ERP outbox (BL-170)",
    "mes-oee-reference": "Минимальный OEE reference: смены, KPI, downtime",
    "lab-training": "Учебный пакет — 18 упражнений: virtual device, workflow, отчёты",
    "demo-app": "Демо-приложение для знакомства с operator UI",
    "marketplace-demo": "Демо marketplace bundle для теста install/activate",
    "mqtt-temperature": "MQTT temperature lab — auto-provisioning и alert",
    "simulator-profiles": "Профили симуляторов для lab и interop",
    "spreadsheet-demo": "Spreadsheet widget demo на дашборде",
}


def main() -> None:
    for folder in sorted(CATALOG.iterdir()):
        if not folder.is_dir():
            continue
        manifest_path = folder / "listing.manifest.json"
        if not manifest_path.is_file():
            continue
        m = json.loads(manifest_path.read_text(encoding="utf-8"))
        slug = m.get("slug", folder.name)
        m.update(VENDOR)
        if slug in DESCRIPTIONS:
            m["description"] = DESCRIPTIONS[slug]
        if slug in PRICING:
            m["pricing"], m["priceCents"] = PRICING[slug]
        elif m.get("pricing") != "paid":
            m["pricing"] = "free"
            m["priceCents"] = 0
        m["minIspfVersion"] = m.get("minIspfVersion") or "0.9.30"
        manifest_path.write_text(json.dumps(m, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("updated", slug)

    # mes-platform-production listing
    prod_dir = CATALOG / "mes-platform-production"
    prod_dir.mkdir(exist_ok=True)
    prod_bundle_src = ROOT / "examples" / "mes-platform-production" / "bundle.json"
    if prod_bundle_src.is_file():
        (prod_dir / "bundle.json").write_text(prod_bundle_src.read_text(encoding="utf-8"), encoding="utf-8")
    prod_manifest = {
        "slug": "mes-platform-production",
        "title": "MES Platform Production",
        "description": DESCRIPTIONS["mes-platform-production"],
        "pricing": "free",
        "appId": "mes-platform-production",
        **VENDOR,
        "vendorContactPerson": "Marketplace Team",
        "priceCents": 0,
        "latestVersion": "1.0.0",
        "minIspfVersion": "0.9.30",
        "bundleArtifact": "bundle.json",
        "tags": ["mes", "oee", "dispatch", "spc", "batch"],
        "changelog": "BL-170 certified production MES walkthrough bundle.",
    }
    (prod_dir / "listing.manifest.json").write_text(
        json.dumps(prod_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print("updated mes-platform-production")


if __name__ == "__main__":
    main()
