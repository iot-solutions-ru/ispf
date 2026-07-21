#!/usr/bin/env python3
"""Anonymize committed repo content. RFC 5737 / example.invalid placeholders."""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

SKIP_DIR_NAMES = {
    ".git",
    ".gradle",
    "node_modules",
    "dist",
    "build",
    "target",
    ".cursor",
}

SKIP_FILE_NAMES = {
    "anonymize-repo.py",
    "anonymize-docs.py",
}

TEXT_SUFFIXES = {
    ".md",
    ".mdc",
    ".yml",
    ".yaml",
    ".json",
    ".sh",
    ".ps1",
    ".py",
    ".java",
    ".html",
    ".env",
    ".example",
    ".properties",
    ".xml",
    ".gradle",
    ".toml",
    ".ts",
    ".tsx",
    ".js",
    ".cjs",
    ".mjs",
    ".conf",
    ".txt",
}

# Longer / more specific patterns first
REPLACEMENTS: list[tuple[str, str]] = [
    ("https://ispf.iot-solutions.ru", "https://ispf.example.invalid"),
    ("http://ispf.iot-solutions.ru", "http://ispf.example.invalid"),
    ("https://ispf-marketplace.iot-solutions.ru", "https://marketplace.example.invalid"),
    ("ispf-marketplace.iot-solutions.ru", "marketplace.example.invalid"),
    ("https://marketplace.iot-solutions.ru", "https://marketplace.example.invalid"),
    ("marketplace.iot-solutions.ru", "marketplace.example.invalid"),
    ("https://ai.iot-solutions.ru", "https://ai.example.invalid"),
    ("ai.iot-solutions.ru", "ai.example.invalid"),
    ("https://www.iot-solutions.ru", "https://www.vendor.example.invalid"),
    ("www.iot-solutions.ru", "www.vendor.example.invalid"),
    ("https://iot-solutions.ru", "https://vendor.example.invalid"),
    ("ispf.iot-solutions.ru", "ispf.example.invalid"),
    ("iot-solutions.ru", "vendor.example.invalid"),
    ("info@iot-solutions.ru", "info@vendor.example.invalid"),
    ("marketplace@iot-solutions.ru", "marketplace@vendor.example.invalid"),
    ("http://84.42.21.226:8000", "http://lab-edge.example.invalid:8000"),
    ("84.42.21.226", "lab-edge.example.invalid"),
    ("m5.wqtt.ru:11296", "mqtt-broker.example.invalid:1883"),
    ("m5.wqtt.ru", "mqtt-broker.example.invalid"),
    ("wqtt.ru", "mqtt-broker.example.invalid"),
    ("192.168.100.11", "198.51.100.11"),
    ("192.168.100.10", "198.51.100.10"),
    ("192.168.100.15", "198.51.100.15"),
    ("192.168.20.10", "198.51.100.20"),
    ("192.168.10.50", "198.51.100.50"),
    ("192.168.1.10", "10.0.0.10"),
    ("192.168.1.1", "10.0.0.1"),
    ("root@ispf-marketplace.example.invalid", "deploy-user@marketplace.example.invalid"),
    ("root@marketplace.example.invalid", "deploy-user@marketplace.example.invalid"),
    ("root@ispf.example.invalid", "deploy-user@ispf.example.invalid"),
    ("iot-solutions@", "lab-operator@"),
    ("ISPF_LAB_SSH_PORT=5031", "ISPF_LAB_SSH_PORT=22"),
    ("https://github.com/Michaael/", "https://github.com/iot-solutions-ru/"),
    ("github.com/Michaael/", "github.com/iot-solutions-ru/"),
    ("Michaael/IoT-Solutions-Platform", "iot-solutions-ru/ispf"),
    ("Michaael/Partner-portal", "iot-solutions-ru/partner-portal"),
    ('githubOwner = "Michaael"', 'githubOwner = "iot-solutions-ru"'),
    (':Michaael}', ':iot-solutions-ru}'),
    ("https://michaael.github.io/", "https://iot-solutions-ru.github.io/"),
    ("michaael.github.io", "iot-solutions-ru.github.io"),
    ("${ISPF_MARKETPLACE_DEFAULT_ID:iot-solutions}", "${ISPF_MARKETPLACE_DEFAULT_ID:default-publisher}"),
    ('defaultId = "iot-solutions"', 'defaultId = "default-publisher"'),
    ("- id: iot-solutions", "- id: default-publisher"),
    ("ispf_lab_ed25519", "lab_ed25519"),
    ("ISPF_LAB_USER=iot-solutions", "ISPF_LAB_USER=lab-operator"),
]

LAB_EVENT_JOURNAL_HEADER_EN = """**Where to run:** dedicated lab host (SSH from workstation; HTTP via nginx edge).
**Templates:** [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) (shared topology/env) and gitignored `deploy/lab-*` on the operator machine.

SSH host, port, user, and key path — `deploy/lab_ssh.py` / `examples/.../env/lab-loadgen.env` (replace on site; never commit real values).
"""

LAB_EVENT_JOURNAL_HEADER_RU = """**Где запускать:** выделенный lab-хост (SSH с рабочей станции; HTTP через nginx).
**Шаблоны:** [`examples/lab-mqtt-historian-stress/`](../../examples/lab-mqtt-historian-stress/) (общая топология/env) и gitignored `deploy/lab-*` на машине оператора.

SSH-хост, порт, пользователь и ключ — `deploy/lab_ssh.py` / `examples/.../env/lab-loadgen.env` (подставьте на стенде; не коммитьте реальные значения).
"""


def apply_base_replacements(text: str) -> str:
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    return text


def apply_markdown_extras(text: str) -> str:
    text = re.sub(
        r"\*\*Host:\*\* `lab-edge\.example\.invalid`, SSH port `5031`, user `lab-operator`\n"
        r"\*\*SSH alias:\*\* `ispf-lab` \(.*?\)\n"
        r"\*\*HTTP:\*\* `http://lab-edge\.example\.invalid:8000`.*?\n",
        "",
        text,
        flags=re.DOTALL,
    )
    text = re.sub(
        r"\*\*Хост:\*\* `lab-edge\.example\.invalid`, порт SSH `5031`, пользователь `lab-operator`\n"
        r"\*\*SSH alias:\*\* `ispf-lab` \(.*?\)\n"
        r"\*\*HTTP:\*\* `http://lab-edge\.example\.invalid:8000`.*?\n",
        "",
        text,
        flags=re.DOTALL,
    )
    text = text.replace("ssh ispf-lab", "ssh lab-host")
    text = text.replace("ssh lab-host  # configure in ~/.ssh/config   # thereafter", "ssh lab-host   # thereafter")
    text = text.replace("ssh lab-host  # configure in ~/.ssh/config   # далее", "ssh lab-host   # далее")
    text = re.sub(
        r"`ssh lab-host  # configure in ~/.ssh/config`",
        "SSH alias `lab-host` (see `~/.ssh/config`)",
        text,
    )
    text = text.replace("| | Lab (lab-edge.example.invalid) | VPS prod |", "| | Lab (dedicated hardware) | Production VPS |")
    text = text.replace("| | Лаборатория (lab-edge.example.invalid) | VPS прод |", "| | Лаборатория (выделенное железо) | VPS прод |")
    text = text.replace("## VPS prod comparison (ispf.example.invalid)", "## Lab vs production VPS")
    text = text.replace("## Сравнение продуктов VPS (ispf.example.invalid)", "## Lab vs production VPS")
    text = text.replace("--publish-via-ssh deploy-user@ispf.example.invalid", "--publish-via-ssh deploy-user@production-host")
    text = text.replace("ssh deploy-user@ispf.example.invalid", "ssh deploy-user@production-host")
    text = re.sub(
        r"scp ([^\n]+) deploy-user@ispf\.example\.invalid",
        r"scp \1 deploy-user@production-host",
        text,
    )
    text = text.replace("https://ispf.example.invalid", "${ISPF_BASE_URL:-https://ispf.example.invalid}")
    text = text.replace(
        "${ISPF_BASE_URL:-${ISPF_BASE_URL:-https://ispf.example.invalid}}",
        "${ISPF_BASE_URL:-https://ispf.example.invalid}",
    )
    return text


def anonymize_text(text: str, *, markdown: bool) -> str:
    text = apply_base_replacements(text)
    if markdown:
        text = apply_markdown_extras(text)
    return text


def should_skip(path: Path) -> bool:
    if path.name in SKIP_FILE_NAMES:
        return True
    if path.name.startswith("tmp_") or path.name.startswith("_tmp_"):
        return True
    if any(part in SKIP_DIR_NAMES for part in path.parts):
        return True
    return False


def iter_text_files(*, markdown_only: bool) -> list[Path]:
    out: list[Path] = []
    for path in ROOT.rglob("*"):
        if not path.is_file() or should_skip(path):
            continue
        if markdown_only:
            if path.suffix.lower() != ".md":
                continue
        elif path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        out.append(path)
    return out


def patch_lab_event_journal(path: Path, header: str) -> None:
    text = path.read_text(encoding="utf-8")
    if "Dedicated lab host" in text or "выделенный lab-хост" in text:
        return
    text = re.sub(
        r"(# Lab: event journal stress.*?\n\n.*?\n\n)",
        r"\1" + header + "\n",
        text,
        count=1,
    )
    path.write_text(text, encoding="utf-8", newline="\n")


def main() -> None:
    parser = argparse.ArgumentParser(description="Anonymize repo content for public git")
    parser.add_argument("--markdown-only", action="store_true", help="Only process *.md (legacy)")
    args = parser.parse_args()

    targets = iter_text_files(markdown_only=args.markdown_only)
    changed = 0
    for path in sorted(set(targets)):
        try:
            original = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        updated = anonymize_text(original, markdown=path.suffix.lower() == ".md")
        if updated != original:
            path.write_text(updated, encoding="utf-8", newline="\n")
            changed += 1
            print("updated", path.relative_to(ROOT))

    if not args.markdown_only:
        for name, header in (
            ("docs/en/lab-event-journal-stress.md", LAB_EVENT_JOURNAL_HEADER_EN),
            ("docs/ru/lab-event-journal-stress.md", LAB_EVENT_JOURNAL_HEADER_RU),
        ):
            p = ROOT / name
            if p.exists():
                patch_lab_event_journal(p, header)
                print("patched header", name)

    print(f"done: {changed} files")


if __name__ == "__main__":
    main()
