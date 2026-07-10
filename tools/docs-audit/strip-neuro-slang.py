#!/usr/bin/env python3
"""Remove marketing / LLM boilerplate phrasing from docs (especially ADRs)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"

# Order matters — longer / specific first.
REPLACEMENTS: list[tuple[str, str]] = [
    ("Product goal (user request):", "Goal:"),
    ("PI-like discoverability and extensibility", "catalog browse and extensibility comparable to PI AF"),
    ("PI-like catalog", "PI AF-style catalog"),
    ("PI-like ", "Comparable to PI: "),
    ("One mental model for operators:", "For operators:"),
    ("without a second mental model.", "without a parallel alarm object model."),
    ("overlapping mental models", "duplicate tabs and overlapping concepts"),
    ("aligned with integrator mental model.", "aligned with integrator expectations."),
    ("Same mental model as", "Same pattern as"),
    ("one mental model:", "one workflow:"),
    ("competitive target moves from", "Phase 33 target shifts from"),
    ("competitive target", "target"),
    ("**Authoritative current column:**", "**Current column:**"),
    ("authoritative definitions remain", "canonical definitions remain"),
    ("remain authoritative for", "remain canonical for"),
    ("remains authoritative for", "remains canonical for"),
    ("tree remains authoritative", "tree remains canonical"),
    ("Verdict:", "Assessment:"),
    ("Strategic audit vs best-in-class", "Domain audit vs leading platforms"),
    ("best-in-class platforms", "leading commercial platforms"),
    ("best-in-class (Kepware", "leading platforms (Kepware"),
    ("best-in-class:", "leading platforms:"),
    ("The only moat incumbents cannot copy within a year", "Live LLM agent deploy without manual edits"),
    ("Own AI moat", "Differentiated AI agent path"),
    ("All phases + AI moat", "All phases + live AI agent"),
    (" and surpass incumbents in solution delivery speed and AI-native development.", "."),
    ("AI-native development", "AI-assisted solution development"),
    ("zero-touch deploy", "automated deploy"),
    ("north star", "target approach"),
    ("North star", "Target approach"),
    ("Excellence Program (Partial)", "Phases 25–33 (partial)"),
    ("Excellence Program:", "Phases 25–33:"),
    ("Excellence Program ", "Phases 25–33 "),
    ("Excellence Program", "Phases 25–33"),
    ("checkbox trap", "premature Done marking"),
    ("HMI moat", "HMI depth"),
    ("semantic moat", "semantic overlay"),
    ("Sprint S21 — HMI Moat", "Sprint S21 — HMI depth"),
    ("Acceleration: HMI moat", "Acceleration: HMI depth"),
    ("Acceleration: semantic moat", "Acceleration: semantic overlay"),
    ("Application team needs easily lead to", "Application team needs often lead to"),
    ("**Near-term quick win:**", "**First step:**"),
    ("Запрос: **такой же опыт discovery", "Цель: **каталог и расширение как в PI"),
    ("для Excellence Program", "для фаз 25–33"),
    ("Excellence Program (фазы", "Фазы 25–33 (фазы"),
    ("роя HMI", "глубины HMI"),
    ("north star", "целевой подход"),
    ("| [S21](#sprint-s21--hmi-moat) | 23 | Ускорение: ров HMI |", "| [S21](#sprint-s21--hmi-moat) | 23 | Ускорение: глубина HMI |"),
    ("aligned с integrator mental model.", "aligned с ожиданиями интегратора."),
    ("## Phase 25–33 — Phases 25–33", "## Phases 25–33"),
    ("Phase 25–33 Phases 25–33", "Phases 25–33"),
    ("### Excellence summary", "### Phases 25–33 summary"),
    ("### Сводка Excellence", "### Сводка фаз 25–33"),
    ("Excellence scorecard", "competitive scorecard"),
    ("**Checkbox trap:**", "**Premature Done marking:**"),
    ("**Ловушка галочек:**", "**Преждевременная отметка Done:**"),
    (" и превзойти действующих игроков по скорости создания решений и AI-разработке.", "."),
    ("## Этап 25–33 — Программа совершенствования", "## Фазы 25–33"),
    ("| **Competitive target** |", "| **Target** |"),
    ("| Verdict |", "| Assessment |"),
    ("analytics,.", "analytics."),
    ("Стратегический аудит относительно лучших в классе", "Аудит домена относительно ведущих платформ"),
    ("Вердикт:", "Оценка:"),
    ("лучших платформ класса", "ведущих коммерческих платформ"),
    ("Программа совершенствования", "Фазы 25–33"),
    ("Фаза 25–33 Программа совершенствования", "Фазы 25–33"),
    ("| Excellence: OT→", "| Phases 25–33: OT→"),
    ("| Excellence target |", "| Phase 26 target |"),
    ("## Phase 26 — HMI Excellence", "## Phase 26 — HMI"),
    ("HMI Excellence", "HMI phase"),
    ("(Excellence split)", "(Phases 25–33 split)"),
    ("(выделение Excellence)", "(выделение фаз 25–33)"),
    ("вкл. Excellence", "вкл. фазы 25–33"),
    ("- Positive: ", "- "),
    ("Excellence REQ-EX", "REQ-EX"),
    ("## Phase 23 — Platform Excellence (REQ-EX)", "## Phase 23 — REQ-EX"),
    ("## Этап 23 — Совершенствование платформы (REQ-EX)", "## Этап 23 — REQ-EX"),
]

INLINE_POSITIVE = re.compile(r"\n\*\*Positive:\*\* (.+)\n\n", re.MULTILINE)
INLINE_NEGATIVE = re.compile(r"\n\*\*Negative(?: / risks)?:\*\* (.+)\n\n", re.MULTILINE)
INLINE_RU_PLUS = re.compile(r"\n\*\*Плюсы:\*\* (.+)\n\n", re.MULTILINE)
INLINE_RU_MINUS = re.compile(r"\n\*\*Минусы(?: / риски)?:\*\* (.+)\n\n", re.MULTILINE)
STANDALONE_RU_MINUS = re.compile(r"\n\*\*Минусы\*\*\n\n", re.MULTILINE)

CONSEQUENCES_POSITIVE = re.compile(
    r"(\n## Consequences\n\n)\*\*Positive\*\*\n\n",
    re.MULTILINE,
)
CONSEQUENCES_POSITIVE_BARE = re.compile(
    r"\n\*\*Positive\*\*\n\n",
    re.MULTILINE,
)
CONSEQUENCES_NEGATIVE = re.compile(
    r"\n\*\*Negative(?: / risks| / follow-ups)?\*\*\n\n",
    re.MULTILINE,
)
CONSEQUENCES_NEGATIVE_BARE = re.compile(
    r"\n\*\*Negative\*\*\n\n",
    re.MULTILINE,
)
CONSEQUENCES_RISK_GAP = re.compile(
    r"(\n- [^\n]+)\n\n\n(- )",
    re.MULTILINE,
)
RU_CONSEQUENCES = [
    (re.compile(r"(## Последствия\n\n)\*\*Плюсы\*\*\n\n", re.MULTILINE), r"\1"),
    (re.compile(r"\n\*\*Минусы / риски\*\*\n\n", re.MULTILINE), r"\n\nRisks:\n\n"),
    (re.compile(r"\n\*\*Negative(?: / follow-ups)?\*\*\n\n", re.MULTILINE), r"\n\nRisks:\n\n"),
]


def scrub(text: str) -> str:
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    text = CONSEQUENCES_POSITIVE.sub(r"\1", text)
    text = CONSEQUENCES_POSITIVE_BARE.sub("\n\n", text)
    text = CONSEQUENCES_NEGATIVE.sub("\n\nRisks:\n\n", text)
    text = CONSEQUENCES_NEGATIVE_BARE.sub("\n\nRisks:\n\n", text)
    text = CONSEQUENCES_RISK_GAP.sub(r"\1\n\nRisks:\n\n\2", text)
    for pat, repl in RU_CONSEQUENCES:
        text = pat.sub(repl, text)
    text = INLINE_POSITIVE.sub(r"\n- \1\n\n", text)
    text = INLINE_NEGATIVE.sub(r"\n\nRisks:\n\n- \1\n\n", text)
    text = INLINE_RU_PLUS.sub(r"\n- \1\n\n", text)
    text = INLINE_RU_MINUS.sub(r"\n\nRisks:\n\n- \1\n\n", text)
    text = STANDALONE_RU_MINUS.sub("\n\nRisks:\n\n", text)
    return text


def main() -> int:
    paths = sorted(DOCS.rglob("*.md"))
    changed = 0
    for path in paths:
        original = path.read_text(encoding="utf-8")
        updated = scrub(original)
        if updated != original:
            path.write_text(updated, encoding="utf-8", newline="\n")
            print(path.relative_to(ROOT))
            changed += 1
    print(f"Updated {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
