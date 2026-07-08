#!/usr/bin/env python3
"""Migrate docs/ to docs/en/ + docs/ru/ with kebab-case names and fix links."""

from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
EN = DOCS / "en"
RU = DOCS / "ru"

# Old basename (any case) -> new kebab filename under en/ and ru/
RENAME: dict[str, str] = {
    "README.md": "readme.md",
    "ACCELERATION_PROGRAM.md": "acceleration-program.md",
    "AGENT_KNOWLEDGE.md": "agent-knowledge.md",
    "AGENT_RECIPES.md": "agent-recipes.md",
    "AGENT_REGRESSION.md": "agent-regression.md",
    "AI_AGENT.md": "ai-agent.md",
    "AI_DEVELOPMENT.md": "ai-development.md",
    "AIR_GAP_DEPLOYMENT.md": "air-gap-deployment.md",
    "API.md": "api.md",
    "APPLICATION_PRINCIPLES.md": "application-principles.md",
    "APPLICATIONS.md": "applications.md",
    "ARCHITECTURE.md": "architecture.md",
    "AUTOMATION.md": "automation.md",
    "BINDINGS.md": "bindings.md",
    "BLUEPRINTS.md": "blueprints.md",
    "CERTIFICATION.md": "certification.md",
    "CI_DASHBOARD.md": "ci-dashboard.md",
    "CI_FLAKY_TRIAGE.md": "ci-flaky-triage.md",
    "CLICKHOUSE_PROD_PLAYBOOK.md": "clickhouse-prod-playbook.md",
    "CLUSTER.md": "cluster.md",
    "COLLABORATION.md": "collaboration.md",
    "COMMERCIAL_LICENSING.md": "commercial-licensing.md",
    "COMPETITIVE_SCORECARD.md": "competitive-scorecard.md",
    "DASHBOARDS.md": "dashboards.md",
    "DEMOSTANDS.md": "demostands.md",
    "DEPLOYMENT.md": "deployment.md",
    "DRIVER_DDK.md": "driver-ddk.md",
    "DRIVER_INTEROP_LAB.md": "driver-interop-lab.md",
    "DRIVER_PROMOTION.md": "driver-promotion.md",
    "DRIVERS.md": "drivers.md",
    "FEDERATION.md": "federation.md",
    "FIELD_PILOT_PLAYBOOK.md": "field-pilot-playbook.md",
    "GETTING_STARTED.md": "getting-started.md",
    "GLOSSARY.md": "glossary.md",
    "HISTORIAN_TIERS.md": "historian-tiers.md",
    "HMI_QUALITY_GATES.md": "hmi-quality-gates.md",
    "ISA95_CATALOG.md": "isa95-catalog.md",
    "LAB_EVENT_JOURNAL_STRESS.md": "lab-event-journal-stress.md",
    "LAB_TRAINING.md": "lab-training.md",
    "LICENSE.md": "license.md",
    "LICENSE_COMPLIANCE.md": "license-compliance.md",
    "LICENSED_DRIVER_PACKS.md": "licensed-driver-packs.md",
    "LOAD_TESTING.md": "load-testing.md",
    "MARKETPLACE.md": "marketplace.md",
    "MESSAGING.md": "messaging.md",
    "MULTI_TENANT.md": "multi-tenant.md",
    "OBJECT_FUNCTIONS.md": "object-functions.md",
    "OBJECT_MODEL.md": "object-model.md",
    "OBSERVABILITY.md": "observability.md",
    "OPCUA_SERVER_INTEROP.md": "opcua-server-interop.md",
    "OPERATOR_GUIDE.md": "operator-guide.md",
    "OPERATOR_PWA_ANDROID_SMOKE.md": "operator-pwa-android-smoke.md",
    "PARTNER_PROGRAM.md": "partner-program.md",
    "PID_SYMBOLS_LEGAL.md": "pid-symbols-legal.md",
    "PLATFORM_EVOLUTION.md": "platform-evolution.md",
    "PLATFORM_LOGIC.md": "platform-logic.md",
    "PLUGINS.md": "plugins.md",
    "PRODUCT.md": "product.md",
    "REFERENCE_BUILDING_HVAC_WALKTHROUGH.md": "reference-building-hvac-walkthrough.md",
    "REFERENCE_ESCALATION_TEMPLATES.md": "reference-escalation-templates.md",
    "REFERENCE_MES_DEFECT_WALKTHROUGH.md": "reference-mes-defect-walkthrough.md",
    "REFERENCE_MES_OEE_WALKTHROUGH.md": "reference-mes-oee-walkthrough.md",
    "REFERENCE_MES_OGP_EVENTS_WALKTHROUGH.md": "reference-mes-ogp-events-walkthrough.md",
    "REFERENCE_MES_PLATFORM.md": "reference-mes-platform.md",
    "REFERENCE_MES_WALKTHROUGH.md": "reference-mes-walkthrough.md",
    "REFERENCE_MINI_TEC_WALKTHROUGH.md": "reference-mini-tec-walkthrough.md",
    "REPORTS.md": "reports.md",
    "ROADMAP.md": "roadmap.md",
    "ROADMAP_PHASE25.md": "roadmap-phase-25.md",
    "SCADA.md": "scada.md",
    "SCADA_MIMIC.md": "scada-mimic.md",
    "SCADA_SYMBOL_LIBRARY.md": "scada-symbol-library.md",
    "SECURITY.md": "security.md",
    "SEMANTIC_DEMO.md": "semantic-demo.md",
    "SOLUTION_DEVELOPER_GUIDE.md": "solution-developer-guide.md",
    "SOLUTION_DEVELOPER_PUBLIC_API.md": "solution-developer-public-api.md",
    "SPREADSHEET_WIDGET.md": "spreadsheet-widget.md",
    "STORAGE_PORTABILITY_INVENTORY.md": "storage-portability-inventory.md",
    "SYMBOL_MARKETPLACE.md": "symbol-marketplace.md",
    "TESTING.md": "testing.md",
    "THIRD_PARTY_NOTICES.md": "third-party-notices.md",
    "VARIABLE_HISTORY.md": "variable-history.md",
    "VPS_DEMOSTAND.md": "vps-demostand.md",
    "WEB_CONSOLE.md": "web-console.md",
    "WIDGETS.md": "widgets.md",
    "WORKFLOWS.md": "workflows.md",
}

# Prefer Russian canonical copy in docs/ru/ (English mirror in docs/en/)
RU_PRIMARY = {
    "readme.md",
    "product.md",
    "demostands.md",
    "vps-demostand.md",
    "roadmap-phase-25.md",
    "architecture.md",
    "application-principles.md",
    "getting-started.md",
    "glossary.md",
}

CYRILLIC_RE = re.compile(r"[\u0400-\u04FF]")
LINK_RE = re.compile(
    r"(\[[^\]]*\]\()((?:\./)?(?:\.\./)?(?:docs/)?(?:en/|ru/)?)?([^)#]+\.md)(#[^)]*)?(\))"
)


def cyrillic_ratio(text: str) -> float:
    if not text:
        return 0.0
    c = len(CYRILLIC_RE.findall(text))
    return c / max(len(text), 1)


def old_to_new_name(old: str) -> str:
    base = Path(old).name
    upper = base.upper()
    for k, v in RENAME.items():
        if k.upper() == upper or k == base:
            return v
    # decisions keep numbered names
    if re.match(r"^\d{4}-.+\.md$", base, re.I):
        return base.lower()
    return base.lower().replace("_", "-")


def build_link_map() -> dict[str, str]:
    """Map various old link forms -> docs/en/<kebab>.md"""
    m: dict[str, str] = {}
    for old, new in RENAME.items():
        stem = Path(old).stem
        targets = [
            old,
            old.lower(),
            f"docs/{old}",
            f"docs/{old.lower()}",
            stem,
            stem.upper(),
            stem.lower(),
            f"docs/{stem}.md",
            f"docs/{stem.upper()}.md",
            f"../docs/{old}",
            f"../../docs/{old}",
        ]
        for t in targets:
            m[t] = f"docs/en/{new}"
            m[t.replace("docs/", "")] = f"docs/en/{new}"
    # decisions
    for p in (DOCS / "decisions").glob("*.md"):
        name = p.name
        low = name.lower()
        m[name] = f"docs/en/decisions/{low}"
        m[f"decisions/{name}"] = f"docs/en/decisions/{low}"
        m[f"docs/decisions/{name}"] = f"docs/en/decisions/{low}"
    return m


def resolve_link(target: str, from_dir: Path, lang: str) -> str:
    """Resolve relative .md link to docs/<lang>/..."""
    anchor = ""
    if "#" in target:
        target, anchor = target.split("#", 1)
        anchor = "#" + anchor
    target = target.strip()
    if not target.endswith(".md"):
        return target + anchor
    base = Path(target).name
    new_name = old_to_new_name(base)
    # relative within same lang folder
    if target.startswith("decisions/") or "decisions/" in target:
        return f"decisions/{new_name}{anchor}"
    if target.startswith("../en/") or target.startswith("../ru/"):
        return target + anchor
    if target.startswith("en/") or target.startswith("ru/"):
        parts = target.split("/", 1)
        return f"{parts[0]}/{new_name}{anchor}" if len(parts) == 2 else target
    return f"{new_name}{anchor}"


def fix_links_in_text(text: str, lang: str) -> str:
    def repl(m: re.Match) -> str:
        prefix, path, anchor, suffix = m.group(1), m.group(3), m.group(4) or "", m.group(5)
        if path.startswith("http"):
            return m.group(0)
        fixed = resolve_link(path, EN, lang)
        return f"{prefix}{fixed}{suffix}"

    # Fix markdown links
    text = LINK_RE.sub(repl, text)
    # Fix ADR references like [0001](decisions/0001-...)
    text = re.sub(
        r"\((decisions/)(\d{4}-[^)]+)\.md\)",
        lambda m: f"({m.group(1)}{m.group(2).lower()}.md)",
        text,
        flags=re.I,
    )
    # Uppercase doc refs in backticks
    for old, new in RENAME.items():
        text = text.replace(f"`docs/{old}`", f"`docs/en/{new}`")
        text = text.replace(f"docs/{old}", f"docs/en/{new}")
        text = text.replace(f"({old})", f"({new})")
        stem = Path(old).stem
        for pat in [stem, stem.upper(), f"{stem}.md", f"{stem.upper()}.md"]:
            text = re.sub(
                rf"\(({re.escape(pat)})\)",
                f"({new})",
                text,
                flags=re.I,
            )
    return text


def migrate() -> None:
    if EN.exists():
        shutil.rmtree(EN)
    if RU.exists():
        shutil.rmtree(RU)
    EN.mkdir(parents=True)
    RU.mkdir(parents=True)
    (EN / "decisions").mkdir()
    (RU / "decisions").mkdir()
    if (DOCS / "assets").exists():
        shutil.copytree(DOCS / "assets", EN / "assets")
        shutil.copytree(DOCS / "assets", RU / "assets")
    if (DOCS / "agent-scenarios").exists():
        shutil.copytree(DOCS / "agent-scenarios", EN / "agent-scenarios")
        shutil.copytree(DOCS / "agent-scenarios", RU / "agent-scenarios")

    # Copy decisions (English canonical — ADRs)
    for p in sorted((DOCS / "decisions").glob("*.md")):
        name = p.name.lower()
        shutil.copy2(p, EN / "decisions" / name)
        shutil.copy2(p, RU / "decisions" / name)

    # Copy top-level md files
    for p in sorted(DOCS.glob("*.md")):
        if p.name.lower() == "readme.md" and p.parent == DOCS:
            continue  # handle separately
        new = old_to_new_name(p.name)
        content = p.read_text(encoding="utf-8")
        en_content = fix_links_in_text(content, "en")
        ru_content = fix_links_in_text(content, "ru")

        if new in RU_PRIMARY or cyrillic_ratio(content) > 0.12:
            (RU / new).write_text(ru_content, encoding="utf-8")
            en_header = (
                f"> **Language:** English overview. Full Russian document: "
                f"[ru/{new}](../ru/{new}).\n\n"
            )
            if not en_content.startswith("> **Language:**"):
                (EN / new).write_text(en_header + en_content, encoding="utf-8")
            else:
                (EN / new).write_text(en_content, encoding="utf-8")
        else:
            (EN / new).write_text(en_content, encoding="utf-8")
            ru_header = (
                f"> **Язык:** русская версия. Canonical English: "
                f"[en/{new}](../en/{new}).\n\n"
            )
            if not ru_content.startswith("> **Язык:**"):
                (RU / new).write_text(ru_header + ru_content, encoding="utf-8")
            else:
                (RU / new).write_text(ru_content, encoding="utf-8")

    # Remove old top-level md (after copy)
    for p in list(DOCS.glob("*.md")):
        if p.name.lower() != "readme.md":
            p.unlink()

    write_indexes()
    write_redirect_stubs()

    # Old decisions folder — remove after copy
    if (DOCS / "decisions").exists():
        shutil.rmtree(DOCS / "decisions")
    if (DOCS / "assets").exists():
        shutil.rmtree(DOCS / "assets")
    if (DOCS / "agent-scenarios").exists():
        shutil.rmtree(DOCS / "agent-scenarios")


def write_redirect_stubs() -> None:
    """Backward-compatible redirects from docs/en/old-name.md -> docs/en/kebab.md"""
    for old, new in RENAME.items():
        if old.upper() == "README.MD":
            continue
        stub = (
            f"# Moved\n\n"
            f"This document moved to **[en/{new}](en/{new})** "
            f"(English) and **[ru/{new}](ru/{new})** (Russian).\n"
        )
        (DOCS / old).write_text(stub, encoding="utf-8")


def write_indexes() -> None:
    en_readme = (EN / "readme.md")
    ru_readme = (RU / "readme.md")

    en_index = """# ISPF Documentation (English)

**IoT Solutions Platform Framework** — middleware for IoT, SCADA, and industrial automation.

> Canonical language: **English**. Russian mirror: [../ru/readme.md](../ru/readme.md).

## Product

| Document | Audience | Description |
|----------|----------|-------------|
| [Product overview](product.md) | All | Capabilities, scenarios, doc map |
| [Operator guide](operator-guide.md) | Operator | HMI, work queue, events |
| [Solution developer guide](solution-developer-guide.md) | App developer | Deploy, operator UI, bundles |
| [Application principles](application-principles.md) | Developer, Agent | P1–P10 north star |
| [Public API](solution-developer-public-api.md) | App developer | Stable platform ↔ bundle boundary |
| [Glossary](glossary.md) | All | Terms and definitions |

## Platform

| Document | Description |
|----------|-------------|
| [Getting started](getting-started.md) | Install, profiles, first run |
| [Architecture](architecture.md) | Vision, layers, extensibility |
| [Object model](object-model.md) | Tree, variables, events, functions |
| [Bindings](bindings.md) | CEL and platform bindings |
| [Platform logic](platform-logic.md) | Rules, dashboard context |
| [Variable history](variable-history.md) | Time-series, retention |
| [REST API](api.md) | Endpoints reference |
| [Applications](applications.md) | Bundles, BFF, scheduler |
| [Reports](reports.md) | SQL reports, CSV export |
| [Roadmap](roadmap.md) | Phase 0–24, BL-01…139 |
| [Roadmap Phase 25+](roadmap-phase-25.md) | Excellence Program → 10/10 |
| [Competitive scorecard](competitive-scorecard.md) | Code-verified readiness matrix |
| [ADR index](decisions/readme.md) | Architecture decision records |

## SCADA / HMI

| Document | Description |
|----------|-------------|
| [SCADA overview](scada.md) | Mimics, symbols, bindings |
| [SCADA mimic reference](scada-mimic.md) | `diagramJson`, REST API |
| [Symbol library](scada-symbol-library.md) | P&ID pack (218 symbols) |
| [Widgets catalog](widgets.md) | All widget types |
| [Dashboards](dashboards.md) | Layout, `selectionKey` |
| [HMI quality gates](hmi-quality-gates.md) | Lighthouse, axe, FPS |
| [Spreadsheet widget](spreadsheet-widget.md) | Formulas and bindings |

## OT / drivers / historian

| Document | Description |
|----------|-------------|
| [Drivers catalog](drivers.md) | Built-in drivers |
| [Driver DDK](driver-ddk.md) | Custom driver SDK |
| [Driver promotion](driver-promotion.md) | PRODUCTION matrix |
| [Field pilot playbook](field-pilot-playbook.md) | OT validation runbooks |
| [Historian tiers](historian-tiers.md) | JDBC, ClickHouse, dual-write |
| [ClickHouse prod playbook](clickhouse-prod-playbook.md) | Production rollout |

## AI / automation / MES

| Document | Description |
|----------|-------------|
| [AI development](ai-development.md) | ContextPack, tools, Studio |
| [AI agent](ai-agent.md) | Agent API and metrics |
| [Agent knowledge](agent-knowledge.md) | Internal agent routing map |
| [Agent regression](agent-regression.md) | Scenario CI gates |
| [Automation](automation.md) | Alerts, correlators |
| [Workflows](workflows.md) | BPMN engine |
| [MES platform reference](reference-mes-platform.md) | ISA-95 bundles |

## Operations

| Document | Description |
|----------|-------------|
| [Deployment](deployment.md) | Docker, env vars |
| [Demostand profiles](demostands.md) | Prod, lab, edge topologies |
| [Cluster](cluster.md) | Multi-replica |
| [Federation](federation.md) | Hub / edge peers |
| [Security](security.md) | RBAC, MFA |
| [Observability](observability.md) | Metrics, diagnostics |
| [Testing](testing.md) | Unit, integration |
| [Load testing](load-testing.md) | Throughput baselines |

## Ecosystem

| Document | Description |
|----------|-------------|
| [Marketplace](marketplace.md) | Catalog and install |
| [Symbol marketplace](symbol-marketplace.md) | Symbol pack distribution |
| [Partner program](partner-program.md) | Integrator tiers |
| [Certification](certification.md) | Training paths |
| [License](license.md) | Apache 2.0 core |

## Quick links

- API: `http://localhost:8080/api/v1`
- Web Console: `http://localhost:5173` (dev)
- Operator HMI: `http://localhost:5173?mode=operator`
"""
    ru_index = """# Документация ISPF (русский)

**IoT Solutions Platform Framework** — middleware-платформа для IoT, SCADA и промышленной автоматизации.

> Канонический технический язык: **английский** ([../en/readme.md](../en/readme.md)). Здесь — русские оглавления и документы с русским основным текстом.

## Продукт

| Документ | Аудитория | Описание |
|----------|-----------|----------|
| [Обзор продукта](product.md) | Все | Возможности и карта документации |
| [Руководство оператора](operator-guide.md) | Оператор | HMI, work queue |
| [Руководство разработчика решений](solution-developer-guide.md) | Разработчик | Bundle, deploy |
| [Принципы приложений](application-principles.md) | Разработчик, агент | P1–P10 |
| [Глоссарий](glossary.md) | Все | Термины |

## Платформа

| Документ | Описание |
|----------|-------------|
| [Быстрый старт](getting-started.md) | Установка и первый запуск |
| [Архитектура](architecture.md) | Видение и слои |
| [Модель объектов](object-model.md) | Дерево, переменные |
| [REST API](api.md) | Справочник endpoints |
| [Roadmap](roadmap.md) | Phase 0–24 |
| [Roadmap Phase 25+](roadmap-phase-25.md) | Excellence Program |
| [Конкурентный scorecard](competitive-scorecard.md) | Оценка по коду (0.9.102) |
| [ADR](decisions/readme.md) | Архитектурные решения (English) |

## SCADA / HMI

| Документ | Описание |
|----------|-------------|
| [SCADA](scada.md) | Мнемосхемы |
| [Справочник виджетов](widgets.md) | Каталог виджетов |
| [Дашборды](dashboards.md) | Конструктор HMI |
| [HMI quality gates](hmi-quality-gates.md) | Качество operator UI |

## OT / драйверы

| Документ | Описание |
|----------|-------------|
| [Драйверы](drivers.md) | Каталог |
| [Field pilot playbook](field-pilot-playbook.md) | Пилоты на объекте |
| [Профили развёртывания](demostands.md) | Prod, lab, edge |

## Экосистема

| Документ | Описание |
|----------|-------------|
| [Marketplace](marketplace.md) | Каталог решений |
| [Партнёрская программа](partner-program.md) | Уровни интеграторов |

## Быстрые ссылки

- API: `http://localhost:8080/api/v1`
- Web Console: `http://localhost:5173`
- Operator HMI: `http://localhost:5173?mode=operator`
"""
    en_readme.write_text(en_index, encoding="utf-8")
    ru_readme.write_text(ru_index, encoding="utf-8")

    root_hub = """# ISPF Documentation / Документация ISPF

Documentation is split by language:

| Language | Index |
|----------|-------|
| **English** (canonical) | [docs/en/readme.md](en/readme.md) |
| **Русский** | [docs/ru/readme.md](ru/readme.md) |

## Layout

```
docs/
  en/           # English (canonical technical docs)
  ru/           # Russian (mirrors + RU-primary pages)
  decisions/    # → moved to en/decisions/ and ru/decisions/
  *.md          # Legacy UPPER_CASE names → redirect stubs to en/
```

## Maintenance

- Migrate: `python tools/docs-audit/migrate_docs.py`
- Fix repo links: `python tools/docs-audit/fix_repo_links.py`
- Audit links: `python tools/docs-audit/audit_links.py`
"""
    (DOCS / "README.md").write_text(root_hub, encoding="utf-8")


if __name__ == "__main__":
    migrate()
    print("Migration complete: docs/en + docs/ru")
