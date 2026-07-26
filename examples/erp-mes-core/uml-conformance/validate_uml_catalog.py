#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Validate uml-catalog.json against generated erp-mes-core bundle.json.

Fails if any class/table/function marked covered is missing from the bundle,
or if any catalog status is still 'missing' at milestone M3.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = Path(__file__).resolve().parent / "uml-catalog.json"
BUNDLE = ROOT / "bundle.json"


def main() -> int:
    cat = json.loads(CATALOG.read_text(encoding="utf-8"))
    bundle = json.loads(BUNDLE.read_text(encoding="utf-8"))
    sql = "\n".join(m.get("sql") or "" for m in bundle.get("migrations") or [])
    fns = {f.get("functionName") for f in bundle.get("functions") or []}
    errors: list[str] = []

    if cat.get("milestone") == "M3":
        for c in cat.get("classes") or []:
            if c.get("status") == "missing":
                errors.append(f"class {c.get('classId')} still missing at M3")
            for t in c.get("emcTables") or []:
                if f"CREATE TABLE IF NOT EXISTS {t}" not in sql and t not in sql:
                    errors.append(f"table {t} (class {c.get('classId')}) not in migrations")
            for f in c.get("bff") or []:
                if f not in fns:
                    errors.append(f"bff {f} (class {c.get('classId')}) not in bundle functions")
            for a in c.get("attributes") or []:
                if a.get("status") == "missing":
                    errors.append(f"attr {c.get('classId')}.{a.get('name')} missing at M3")
        for row in cat.get("part3Activities") or []:
            if row.get("status") == "missing":
                errors.append(f"part3 {row.get('domain')}/{row.get('activity')} missing")
            fn = row.get("functionName")
            if fn and fn not in fns:
                errors.append(f"part3 bff {fn} not in bundle")
        for t in cat.get("requiredTables") or []:
            if f"CREATE TABLE IF NOT EXISTS {t}" not in sql:
                errors.append(f"requiredTables missing CREATE: {t}")
        for f in cat.get("requiredFunctions") or []:
            if f not in fns:
                errors.append(f"requiredFunctions missing: {f}")

    if errors:
        print("UML catalog validation FAILED:")
        for e in errors:
            print(" -", e)
        return 1
    print(
        "UML catalog OK:",
        len(cat.get("classes") or []),
        "classes,",
        len(cat.get("part3Activities") or []),
        "part3 cells; bundle",
        bundle.get("version"),
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
