#!/usr/bin/env python3
"""Inject hub Status one-liners into docs/{en,ru} pages missing them.

Idempotent: skips files that already contain a Status / Статус / Partial / Draft banner.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# filename -> (tag, short reason EN)
STATUS: dict[str, tuple[str, str]] = {
    "product.md": ("Stable", "public product entry"),
    "operator-guide.md": ("Stable", "operator HMI path"),
    "solution-developer-guide.md": ("Stable", "bundle / deploy contracts"),
    "application-principles.md": ("Stable", "P1–P10 target approach"),
    "solution-developer-public-api.md": ("Stable", "platform ↔ bundle boundary"),
    "glossary.md": ("Stable", "terms"),
    "web-console.md": ("Stable", "admin + operator shells"),
    "getting-started.md": ("Stable", "Try + contributor QA"),
    "architecture.md": ("Stable", "layers and extensibility"),
    "object-model.md": ("Stable", "tree model"),
    "bindings.md": ("Stable", "CEL / bindings"),
    "platform-logic.md": ("Beta", "rules; @dashboardContext readiness varies"),
    "blueprints.md": ("Stable", "models / templates"),
    "variable-history.md": ("Stable", "time-series / retention"),
    "api.md": ("Stable", "REST reference"),
    "applications.md": ("Stable", "bundles, BFF, scheduler"),
    "reports.md": ("Stable", "SQL reports"),
    "roadmap.md": ("Charter", "phases and backlog — not a how-to"),
    "competitive-scorecard.md": ("Stable", "code-verified readiness matrix"),
    "scada.md": ("Stable", "mimics and symbols"),
    "scada-mimic.md": ("Stable", "diagramJson / REST"),
    "scada-symbol-library.md": ("Stable", "P&ID pack"),
    "widgets.md": ("Stable", "widget catalog"),
    "dashboards.md": ("Stable", "84×8 grid canonical"),
    "hmi-quality-gates.md": ("Lab", "Lighthouse / axe / FPS runbooks"),
    "spreadsheet-widget.md": ("Stable", "formulas and bindings"),
    "operator-apps.md": ("Stable", "operator shell config"),
    "drivers.md": ("Beta", "packs; maturity varies — see promotion matrix"),
    "driver-ddk.md": ("Stable", "custom driver SDK"),
    "driver-promotion.md": ("Stable", "PRODUCTION + ready-for-field"),
    "field-pilot-playbook.md": ("Lab", "OT validation runbooks"),
    "historian-tiers.md": ("Beta", "JDBC / ClickHouse / dual-write"),
    "clickhouse-prod-playbook.md": ("Lab", "production rollout"),
    "cluster.md": ("Beta", "HA capability vs demostand maturity"),
    "messaging.md": ("Stable", "NATS / MQTT notes"),
    "analytics-historian-cookbook.md": ("Stable", "recipes and rollups"),
    "analytics-formulas-and-packs.md": ("Stable", "expression packs"),
    "analytics-platform-roadmap.md": ("Charter", "BL-200…210 planning"),
    "analytics-tag-catalog.md": ("Stable", "deployed analytics tags"),
    "ai-development.md": ("Beta", "ContextPack / Studio; BL-178 open"),
    "ai-agent.md": ("Beta", "agent API; ≥95% gate not met"),
    "agent-knowledge.md": ("Internal", "agent routing map"),
    "agent-regression.md": ("Lab", "scenario CI gates"),
    "automation.md": ("Stable", "alerts and correlators"),
    "workflows.md": ("Beta", "BPMN subset — not full 2.0"),
    "reference-mes-platform.md": ("Beta", "marketplace MES; smoke ≠ plant"),
    "reference-mes-walkthrough.md": ("Lab", "end-to-end MES path"),
    "deployment.md": ("Stable", "Docker / env"),
    "demostands.md": ("Lab", "prod / lab / edge topologies"),
    "air-gap-deployment.md": ("Stable", "offline installs"),
    "federation.md": ("Beta", "hub / edge maturity caveats"),
    "security.md": ("Stable", "RBAC / MFA"),
    "observability.md": ("Stable", "metrics / diagnostics"),
    "testing.md": ("Stable", "unit / integration"),
    "load-testing.md": ("Lab", "throughput baselines"),
    "release-dogfood.md": ("Internal", "release checklist"),
    "lab-training.md": ("Lab", "training sample packs"),
    "marketplace.md": ("Draft", "Partial BL-183 — not full GA"),
    "symbol-marketplace.md": ("Draft", "listing API stub"),
    "partner-program.md": ("Draft", "design; in-server API stub"),
    "certification.md": ("Draft", "training paths / exams"),
    "license.md": ("Stable", "AGPL v3 + dual-license"),
    "commercial-licensing.md": ("Stable", "Enterprise terms"),
    "license-compliance.md": ("Stable", "obligations checklist"),
    "plugins.md": ("Stable", "core vs packs vs bundles"),
    "documentation-audit.md": ("Internal", "structure / link audit"),
    "documentation-full-audit-2026-07-16.md": ("Internal", "content honesty pass"),
    "russian-software-registry.md": ("Internal", "rights-holder / RU market"),
    "doc-status.md": ("Stable", "status vocabulary"),
}

REASON_RU = {
    "Stable": "актуально для текущего main",
    "Beta": "работает, есть оговорки зрелости",
    "Draft": "дизайн / stub — не GA",
    "Charter": "планирование, не how-to",
    "Lab": "lab / stress runbook",
    "Internal": "для мейнтейнеров",
}

HAS_STATUS = re.compile(
    r"(?i)\*\*Status\b|\*\*Статус\b|Status:\s*(Partial|Draft)|Статус:\s*(Partial|Draft)"
)
H1 = re.compile(r"^(# .+)$", re.MULTILINE)


def inject(path: Path, lang: str) -> bool:
    name = path.name
    if name not in STATUS and not str(path).replace("\\", "/").endswith(
        "decisions/readme.md"
    ):
        # decisions ADR special-cases
        rel = path.relative_to(ROOT / "docs" / lang).as_posix()
        if rel == "decisions/readme.md":
            tag, reason = "Stable", "ADR index"
        elif rel in (
            "decisions/0038-analytics-platform-architecture.md",
            "decisions/0042-analytics-function-catalog.md",
        ):
            tag, reason = "Stable", "architecture ADR"
        else:
            return False
    else:
        if name not in STATUS:
            return False
        tag, reason = STATUS[name]

    text = path.read_text(encoding="utf-8")
    if HAS_STATUS.search(text):
        return False
    m = H1.search(text)
    if not m:
        return False
    hub = (
        "[doc-status.md](../doc-status.md)"
        if "decisions" in path.parts
        else (
            "[doc-status.md](doc-status.md)"
            if lang == "en"
            else "[doc-status](../en/doc-status.md)"
        )
    )
    if lang == "en":
        block = f"\n> **Status:** {tag} — {reason}. Hub: {hub}.\n"
    else:
        block = (
            f"\n> **Статус:** {tag} — {REASON_RU.get(tag, reason)}. "
            f"Теги: {hub}.\n"
        )
    insert_at = m.end()
    new = text[:insert_at] + block + text[insert_at:]
    path.write_text(new, encoding="utf-8", newline="\n")
    return True


def main() -> int:
    n = 0
    for lang in ("en", "ru"):
        base = ROOT / "docs" / lang
        for path in sorted(base.rglob("*.md")):
            if inject(path, lang):
                print(f"injected {path.relative_to(ROOT)}")
                n += 1
    print(f"total injected: {n}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
