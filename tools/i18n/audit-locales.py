#!/usr/bin/env python3
"""Flag likely machine-translation issues in ru/de/zh vs canonical en."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LOCALES = ROOT / "apps" / "web-console" / "src" / "locales"
TARGETS = ("ru", "de", "zh")

# Substrings that usually indicate wrong generic translation in ISPF context.
BAD_PATTERNS: dict[str, list[str]] = {
    "ru": [
        r"ведро", r"\bкуч[аи]\b", r"дворняга", r"стог сена", r"лаборатория\)",
        r"прокси-сервер", r"информационн", r"рабочие процессы",
        r"администратор/администратор", r"оборудовать",
    ],
    "de": [
        r"\bFahrer\b", r"\bHaufen\b", r"\bEimer\b", r"Heuhaufen", r"Historikerin",
        r"Schriftsteller", r"\bTisch\b", r"Veranstaltung", r"Schlüsselraum",
        r"Cassandra-Hafen", r"Cassandra-Tisch", r"Stapeländerung",
        r"\bWebsite\b", r"ausrüsten", r"\bsein\b",
    ],
    "zh": [
        r"干草堆", r"史家", r"历史桶", r"管理员/管理员", r"虚拟实验室",
        r"驱动程序", r"联合会", r"联合绑定", r"实验室\)", r"日记中的",
    ],
}

# English product terms that should often stay Latin in UI labels.
KEEP_LATIN = re.compile(
    r"\b(Heap|Historian|Haystack|BPMN|CEL|MQTT|SNMP|Modbus|JDBC|OPC|"
    r"ClickHouse|Cassandra|PostgreSQL|Redis|NATS|REST|WebSocket|"
    r"admin/admin|operator/operator|windowBucket|rollingAvg|"
    r"Federation bind|virtual lab|Blueprint|RELATIVE|ABSOLUTE|INSTANCE)\b",
    re.I,
)


def main() -> int:
    en_dir = LOCALES / "en"
    issues: list[str] = []
    for en_file in sorted(en_dir.glob("*.json")):
        en_data = json.loads(en_file.read_text(encoding="utf-8"))
        for lang in TARGETS:
            tgt_file = LOCALES / lang / en_file.name
            if not tgt_file.is_file():
                continue
            tgt_data = json.loads(tgt_file.read_text(encoding="utf-8"))
            for key, en_val in en_data.items():
                if key not in tgt_data:
                    continue
                val = tgt_data[key]
                if val == en_val:
                    continue
                for pat in BAD_PATTERNS.get(lang, []):
                    if re.search(pat, val, re.I):
                        issues.append(f"{lang}/{en_file.name}:{key}\n  en: {en_val[:120]}\n  {lang}: {val[:120]}")
                        break
    for line in sorted(set(issues)):
        print(line)
        print()
    print(f"Total flagged: {len(set(issues))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
