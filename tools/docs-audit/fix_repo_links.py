#!/usr/bin/env python3
"""Fix docs/*.md links across the repository after migration."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Match docs/en/foo-bar.md or FOO_BAR.md in markdown links (not http)
PATTERNS = [
    (re.compile(r"\bdocs/([A-Z][A-Z0-9_]+)\.md\b"), lambda m: f"docs/en/{_kebab(m.group(1))}.md"),
    (re.compile(r"\bdocs/([a-z][a-z0-9-]+)\.md\b"), None),  # already kebab under docs/ - fix below
    (re.compile(r"\(\.\./\.\./docs/([A-Z][A-Z0-9_]+)\.md\)"), lambda m: f"(../../docs/en/{_kebab(m.group(1))}.md)"),
    (re.compile(r"\(\.\./docs/([A-Z][A-Z0-9_]+)\.md\)"), lambda m: f"(../docs/en/{_kebab(m.group(1))}.md)"),
    (re.compile(r"\bdocs/decisions/([0-9]{4}-[^)\s]+\.md)\b", re.I), lambda m: f"docs/en/decisions/{m.group(1).lower()}"),
    (re.compile(r"\(\.\./\.\./docs/decisions/"), "(../../docs/en/decisions/"),
    (re.compile(r"\(\.\./docs/decisions/"), "(../docs/en/decisions/"),
    (re.compile(r"\(decisions/([0-9]{4}-[^)]+\.md)\)", re.I), lambda m: f"(decisions/{m.group(1).lower()})"),
]

SKIP_DIRS = {".git", "node_modules", "build", "dist", ".gradle", "__pycache__", "tools/docs-audit"}

RENAME = {
    "README": "readme",
    "ROADMAP_PHASE25": "roadmap-phase-25",
    "HMI_QUALITY_GATES": "hmi-quality-gates",
    "SCADA_MIMIC": "scada-mimic",
    "SCADA_SYMBOL_LIBRARY": "scada-symbol-library",
    "AGENT_KNOWLEDGE": "agent-knowledge",
    "AGENT_REGRESSION": "agent-regression",
    "AI_AGENT": "ai-agent",
    "AI_DEVELOPMENT": "ai-development",
    "APPLICATION_PRINCIPLES": "application-principles",
    "COMPETITIVE_SCORECARD": "competitive-scorecard",
    "SYMBOL_MARKETPLACE": "symbol-marketplace",
    "PARTNER_PROGRAM": "partner-program",
    "FIELD_PILOT_PLAYBOOK": "field-pilot-playbook",
    "OPERATOR_GUIDE": "operator-guide",
    "GETTING_STARTED": "getting-started",
    "OBJECT_MODEL": "object-model",
    "VARIABLE_HISTORY": "variable-history",
    "WEB_CONSOLE": "web-console",
    "SOLUTION_DEVELOPER_GUIDE": "solution-developer-guide",
    "SOLUTION_DEVELOPER_PUBLIC_API": "solution-developer-public-api",
    "DRIVER_DDK": "driver-ddk",
    "DRIVER_PROMOTION": "driver-promotion",
    "LICENSED_DRIVER_PACKS": "licensed-driver-packs",
    "THIRD_PARTY_NOTICES": "third-party-notices",
    "HISTORIAN_TIERS": "historian-tiers",
    "ACCELERATION_PROGRAM": "acceleration-program",
    "PLATFORM_EVOLUTION": "platform-evolution",
    "PLATFORM_LOGIC": "platform-logic",
    "OBJECT_FUNCTIONS": "object-functions",
    "SPREADSHEET_WIDGET": "spreadsheet-widget",
    "LAB_TRAINING": "lab-training",
    "LAB_EVENT_JOURNAL_STRESS": "lab-event-journal-stress",
    "LOAD_TESTING": "load-testing",
    "CI_DASHBOARD": "ci-dashboard",
    "CI_FLAKY_TRIAGE": "ci-flaky-triage",
    "CLICKHOUSE_PROD_PLAYBOOK": "clickhouse-prod-playbook",
    "AIR_GAP_DEPLOYMENT": "air-gap-deployment",
    "COMMERCIAL_LICENSING": "commercial-licensing",
    "LICENSE_COMPLIANCE": "license-compliance",
    "REFERENCE_MES_WALKTHROUGH": "reference-mes-walkthrough",
    "REFERENCE_MES_PLATFORM": "reference-mes-platform",
    "REFERENCE_MINI_TEC_WALKTHROUGH": "reference-mini-tec-walkthrough",
    "REFERENCE_BUILDING_HVAC_WALKTHROUGH": "reference-building-hvac-walkthrough",
    "REFERENCE_MES_DEFECT_WALKTHROUGH": "reference-mes-defect-walkthrough",
    "REFERENCE_MES_OEE_WALKTHROUGH": "reference-mes-oee-walkthrough",
    "REFERENCE_MES_OGP_EVENTS_WALKTHROUGH": "reference-mes-ogp-events-walkthrough",
    "REFERENCE_ESCALATION_TEMPLATES": "reference-escalation-templates",
    "PID_SYMBOLS_LEGAL": "pid-symbols-legal",
    "OPCUA_SERVER_INTEROP": "opcua-server-interop",
    "OPERATOR_PWA_ANDROID_SMOKE": "operator-pwa-android-smoke",
    "DRIVER_INTEROP_LAB": "driver-interop-lab",
    "STORAGE_PORTABILITY_INVENTORY": "storage-portability-inventory",
    "ISA95_CATALOG": "isa95-catalog",
    "SEMANTIC_DEMO": "semantic-demo",
    "VPS_DEMOSTAND": "vps-demostand",
    "AGENT_RECIPES": "agent-recipes",
    "MULTI_TENANT": "multi-tenant",
}


def _kebab(upper_stem: str) -> str:
    if upper_stem in RENAME:
        return RENAME[upper_stem]
    return upper_stem.lower().replace("_", "-")


def fix_file(path: Path) -> bool:
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return False
    orig = text
    for pat, repl in PATTERNS:
        if repl is None:
            continue
        if callable(repl):
            text = pat.sub(repl, text)
        else:
            text = pat.sub(repl, text)
    # docs/foo.md without en/ -> docs/en/foo.md (if file exists)
    def doc_sub(m: re.Match) -> str:
        p = m.group(1)
        if p.startswith("en/") or p.startswith("ru/"):
            return m.group(0)
        candidate = ROOT / "docs" / "en" / p
        if candidate.exists():
            return f"docs/en/{p}"
        return m.group(0)

    text = re.sub(r"\bdocs/([a-z0-9][a-z0-9-]*\.md)\b", doc_sub, text)
    if text != orig:
        path.write_text(text, encoding="utf-8")
        return True
    return False


def main() -> int:
    changed = 0
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if any(p in path.parts for p in SKIP_DIRS):
            continue
        if path.suffix not in {".md", ".mdc", ".java", ".ts", ".tsx", ".json", ".yml", ".yaml", ".py", ".kts", ".gradle", ".sh", ".ps1"}:
            continue
        if fix_file(path):
            changed += 1
            print(f"fixed: {path.relative_to(ROOT)}")
    print(f"Done. {changed} files updated.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
