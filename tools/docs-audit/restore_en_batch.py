#!/usr/bin/env python3
"""Restore canonical English for selected docs/en/ files."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EN = ROOT / "docs" / "en"
RU = ROOT / "docs" / "ru"

LINKS = {
    "APPLICATION_PRINCIPLES.md": "application-principles.md",
    "AI_DEVELOPMENT.md": "ai-development.md",
    "AGENT_KNOWLEDGE.md": "agent-knowledge.md",
    "AGENT_RECIPES.md": "agent-recipes.md",
    "SOLUTION_DEVELOPER_PUBLIC_API.md": "solution-developer-public-api.md",
    "SOLUTION_DEVELOPER_GUIDE.md": "solution-developer-guide.md",
    "APPLICATIONS.md": "applications.md",
    "API.md": "api.md",
    "ARCHITECTURE.md": "architecture.md",
    "PRODUCT.md": "product.md",
    "SECURITY.md": "security.md",
    "DASHBOARDS.md": "dashboards.md",
    "DRIVERS.md": "drivers.md",
    "BINDINGS.md": "bindings.md",
    "PLATFORM_LOGIC.md": "platform-logic.md",
    "AUTOMATION.md": "automation.md",
    "WORKFLOWS.md": "workflows.md",
    "WEB_CONSOLE.md": "web-console.md",
    "OPERATOR_GUIDE.md": "operator-guide.md",
    "OBJECT_MODEL.md": "object-model.md",
    "BLUEPRINTS.md": "blueprints.md",
    "OBJECT_FUNCTIONS.md": "object-functions.md",
    "VARIABLE_HISTORY.md": "variable-history.md",
    "SCADA.md": "scada.md",
    "SCADA_MIMIC.md": "scada-mimic.md",
    "WIDGETS.md": "widgets.md",
    "SPREADSHEET_WIDGET.md": "spreadsheet-widget.md",
    "MESSAGING.md": "messaging.md",
    "FEDERATION.md": "federation.md",
    "REPORTS.md": "reports.md",
    "DEPLOYMENT.md": "deployment.md",
    "TESTING.md": "testing.md",
    "LOAD_TESTING.md": "load-testing.md",
    "OBSERVABILITY.md": "observability.md",
    "CLUSTER.md": "cluster.md",
    "GLOSSARY.md": "glossary.md",
    "LAB_TRAINING.md": "lab-training.md",
    "REFERENCE_MES_WALKTHROUGH.md": "reference-mes-walkthrough.md",
    "REFERENCE_MES_DEFECT_WALKTHROUGH.md": "reference-mes-defect-walkthrough.md",
    "REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md": "reference-mes-ogp-events-walkthrough.md",
    "REFERENCE_MINI_TEC_WALKTHROUGH.md": "reference-mini-tec-walkthrough.md",
    "PLATFORM_EVOLUTION.md": "platform-evolution.md",
    "ROADMAP.md": "roadmap.md",
    "COMMERCIAL_LICENSING.md": "commercial-licensing.md",
    "PLUGINS.md": "plugins.md",
    "README.md": "readme.md",
}


def banner(name: str) -> str:
    return (
        f"> **Language:** Canonical English. Russian edition: "
        f"[ru/{name}](../ru/{name}).\n\n"
    )


def fix_links(text: str) -> str:
    for old, new in LINKS.items():
        text = text.replace(f"]({old})", f"]({new})")
        text = text.replace(f"](../{old})", f"](../{new})")
    return text


def strip_banner(text: str) -> str:
    return re.sub(r"^> \*\*(?:Language|Язык):\*\*.*\n\n", "", text, count=1, flags=re.MULTILINE)


def has_cyrillic(text: str) -> bool:
    return bool(re.search(r"[\u0400-\u04FF]", text))


def write_ai_development() -> None:
    ai = subprocess.check_output(
        ["git", "show", "f9a0d4cd:docs/AI_DEVELOPMENT.md"], text=True, errors="replace"
    ).lstrip("\ufeff")
    ai = ai.replace(
        " — подходы к приложениям, карта docs",
        " — application approaches, docs map",
    )
    ai = ai.replace(
        "Создай SNMP localhost и дашборд с CPU",
        "Create SNMP localhost and a dashboard with CPU",
    )
    ai = ai.replace('suggestion «Продолжить»', 'suggestion "Continue"')
    ai = ai.replace(
        "лимит **ответа** для bundle generation",
        "**response** limit for bundle generation",
    )
    ai = ai.replace(
        "лимит **ответа** на один ход агента",
        "**response** limit per agent turn",
    )
    ai = ai.replace(
        "256k у Qwen/vLLM — **окно контекста** (prompt + completion). "
        "Defaults выше рассчитаны под `max-model-len=262144`: до ~512 KB ТЗ + "
        "system/tools/history в prompt, до 128k tokens на completion. "
        "Не ставьте `agent-max-tokens=262144` — prompt не оставляет места. "
        "vLLM на inference host должен разрешать `max_tokens` ≥ 131072.",
        "256k on Qwen/vLLM is the **context window** (prompt + completion). "
        "Defaults above assume `max-model-len=262144`: up to ~512 KB spec + "
        "system/tools/history in the prompt, up to 128k tokens for completion. "
        "Do not set `agent-max-tokens=262144` — the prompt leaves no room. "
        "vLLM on the inference host must allow `max_tokens` ≥ 131072.",
    )
    ai = fix_links(ai)
    path = EN / "ai-development.md"
    path.write_text(banner("ai-development.md") + ai, encoding="utf-8")
    print(f"ai-development: cyrillic={has_cyrillic(ai)}")


if __name__ == "__main__":
    write_ai_development()
