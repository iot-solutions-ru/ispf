#!/usr/bin/env python3
"""OT Trust — readiness audit for every pack in gradle/driver-packs.json (162).

Honesty-first inventory. Does **not** claim field certification.

Usage:
  python3 tools/driver-readiness-audit.py
  python3 tools/driver-readiness-audit.py \\
    --md docs/evidence/ot-trust/driver-readiness.md \\
    --json docs/evidence/ot-trust/driver-readiness.json \\
    --fail-on-findings
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKS_JSON = ROOT / "gradle" / "driver-packs.json"
STUB_JSON = (
    ROOT
    / "packages"
    / "ispf-server"
    / "src"
    / "main"
    / "resources"
    / "driver-pack"
    / "protocol-stub-ids.json"
)
MATRIX_JAVA = (
    ROOT
    / "packages"
    / "ispf-server"
    / "src"
    / "main"
    / "java"
    / "com"
    / "ispf"
    / "server"
    / "driver"
    / "DriverProductionMatrix.java"
)
INTEROP_YML = ROOT / ".github" / "workflows" / "driver-interop.yml"
DRIVERS_MD = ROOT / "docs" / "en" / "drivers.md"

WRITE_IMPL_RE = re.compile(
    r"\.(writeProperty|writePoint|writeTag|writeRegister|writeCoil|submit|publish|setOid|setParameter|"
    r"writeValue|send\()\s*\(",
    re.IGNORECASE,
)
WRITE_STUB_MSG_RE = re.compile(
    r"not implemented|unsupported operation|write not supported|"
    r"is read-only in|read-only driver|driver is read-only|read-only in v\d",
    re.IGNORECASE,
)
STUB_JAVADOC_RE = re.compile(r"\b(stub|placeholder)\b", re.IGNORECASE)
CLASS_JAVADOC_RE = re.compile(
    r"(?s)/\*\*(.*?)\*/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*public\s+(?:abstract\s+)?class\s+\w+"
)
EXPECTED_CATALOG = 162


@dataclass
class Finding:
    severity: str  # FAIL | WARN | INFO
    code: str
    message: str


@dataclass
class DriverRow:
    driver_id: str
    pack_id: str
    driver_class: str
    maturity: str
    source: str
    capabilities: list[str] = field(default_factory=list)
    top10: bool = False
    top20: bool = False
    pack_dir_ok: bool = False
    source_ok: bool = False
    source_path: str = ""
    extends_protocol_stub: bool = False
    loopback_path: str = ""
    loopback_ok: bool | None = None
    interop_module: str = ""
    interop_in_workflow: bool | None = None
    write_claimed: bool = False
    write_looks_stub: bool | None = None
    production_javadoc_stub: bool | None = None
    readiness: str = "UNKNOWN"
    findings: list[Finding] = field(default_factory=list)


def load_packs() -> dict[str, dict]:
    return json.loads(PACKS_JSON.read_text(encoding="utf-8"))


def load_stub_ids() -> set[str]:
    return set(json.loads(STUB_JSON.read_text(encoding="utf-8"))["driverIds"])


def parse_matrix(java_text: str) -> tuple[list[str], list[str], dict[str, dict]]:
    def list_const(name: str) -> list[str]:
        m = re.search(
            rf"static final List<String> {name} = List\.of\((.*?)\);",
            java_text,
            re.S,
        )
        return re.findall(r'"([^"]+)"', m.group(1)) if m else []

    top10 = list_const("TOP_10_INDUSTRIAL")
    top20 = list_const("TOP_20_INDUSTRIAL")
    entries: dict[str, dict] = {}
    for m in re.finditer(
        r'entry\(\s*"([^"]+)"\s*,\s*DriverMaturity\.(PRODUCTION|BETA|STUB)\s*,\s*'
        r"([A-Z0-9_]+|EnumSet\.of\([^)]+\))\s*,\s*"
        r'testPath\("([^"]+)"\s*,\s*"([^"]+)"\)\s*(?:,\s*"([^"]+)")?\s*\)',
        java_text,
    ):
        driver_id, maturity, caps_token, module, test_class, interop = m.groups()
        caps: list[str] = []
        for name in ("POLL", "SUBSCRIBE", "WRITE", "DISCOVERY", "QUALITY", "OBSERVED_AT"):
            if name in caps_token:
                caps.append(name)
        if not caps:
            caps = ["POLL"]
        test_path = f"packages/{module}/src/test/java/{test_class.replace('.', '/')}.java"
        entries[driver_id] = {
            "maturity": maturity,
            "capabilities": caps,
            "loopback": test_path,
            "interop": interop or "",
            "module": module,
        }
    return top10, top20, entries


def parse_interop_modules(yml: str) -> set[str]:
    mods: set[str] = set()
    in_module = False
    for line in yml.splitlines():
        if re.search(r"^\s+module:\s*$", line):
            in_module = True
            continue
        if in_module:
            m = re.match(r"^\s+-\s+(ispf-driver-[\w-]+)\s*$", line)
            if m:
                mods.add(m.group(1))
                continue
            if re.match(r"^\s+\w+:", line) or (line.strip() and not line.startswith(" ")):
                in_module = False
    return mods


def class_path(pack_id: str, driver_class: str) -> Path:
    return ROOT / "packages" / pack_id / "src" / "main" / "java" / Path(
        *driver_class.split(".")
    ).with_suffix(".java")


def write_window(source: str) -> str:
    idx = source.find("void writePoint")
    return "" if idx < 0 else source[idx : idx + 1200]


def detect_write_stub(window: str) -> bool:
    """Heuristic: real write call ⇒ not stub; unconditional read-only/not-implemented throw ⇒ stub.

    Conditional guards like \"object type is read-only\" must not alone mark WRITE as false.
    """
    if not window:
        return False
    if WRITE_IMPL_RE.search(window):
        return False
    for msg in re.findall(r'DriverException\(\s*"([^"]*)"', window):
        if WRITE_STUB_MSG_RE.search(msg):
            return True
    if re.search(r"throw new UnsupportedOperationException", window):
        return True
    return False


def docs_mentions(driver_id: str, docs: str) -> set[str]:
    found: set[str] = set()
    needle = f"`{driver_id}`"
    for line in docs.splitlines():
        if needle not in line:
            continue
        for label in ("PRODUCTION", "BETA", "STUB"):
            if label in line:
                found.add(label)
    return found


def classify(row: DriverRow) -> str:
    if any(f.severity == "FAIL" for f in row.findings):
        return "BLOCKED"
    if row.maturity == "STUB":
        return "STUB"
    if row.maturity == "BETA" and row.top20:
        return "SHELL_BETA"
    if row.maturity == "PRODUCTION" and row.loopback_ok and row.source_ok:
        return "READY_LAB"
    return "PARTIAL"


def audit() -> tuple[list[DriverRow], dict]:
    packs = load_packs()
    stub_ids = load_stub_ids()
    top10, top20, matrix = parse_matrix(MATRIX_JAVA.read_text(encoding="utf-8"))
    interop_mods = (
        parse_interop_modules(INTEROP_YML.read_text(encoding="utf-8"))
        if INTEROP_YML.exists()
        else set()
    )
    docs = DRIVERS_MD.read_text(encoding="utf-8") if DRIVERS_MD.exists() else ""

    rows: list[DriverRow] = []
    for pack_id, meta in sorted(packs.items(), key=lambda kv: kv[1]["driverId"]):
        driver_id = meta["driverId"]
        driver_class = meta["driverClass"]
        src = class_path(pack_id, driver_class)
        row = DriverRow(
            driver_id=driver_id,
            pack_id=pack_id,
            driver_class=driver_class,
            maturity="",
            source="",
            pack_dir_ok=(ROOT / "packages" / pack_id).is_dir(),
            source_ok=src.is_file(),
            source_path=str(src.relative_to(ROOT)) if src.is_file() else str(src),
            top10=driver_id in top10,
            top20=driver_id in top20,
        )

        if driver_id in stub_ids:
            row.maturity = "STUB"
            row.source = "stub-list"
            row.capabilities = ["POLL"]
        elif driver_id in matrix:
            info = matrix[driver_id]
            row.maturity = info["maturity"]
            row.source = "matrix"
            row.capabilities = list(info["capabilities"])
            row.loopback_path = info["loopback"]
            row.loopback_ok = (ROOT / row.loopback_path).is_file()
            row.interop_module = info["interop"]
            if row.interop_module:
                row.interop_in_workflow = row.interop_module in interop_mods
            row.write_claimed = "WRITE" in row.capabilities
        else:
            row.maturity = "BETA"
            row.source = "default-beta"
            row.capabilities = ["POLL"]

        text = src.read_text(encoding="utf-8", errors="replace") if src.is_file() else ""
        if text:
            row.extends_protocol_stub = (
                "ProtocolStubDeviceDriver" in text or "extends ProtocolStub" in text
            )
            window = write_window(text)
            if window:
                row.write_looks_stub = detect_write_stub(window)
            javadoc = CLASS_JAVADOC_RE.search(text)
            if javadoc and row.maturity == "PRODUCTION":
                row.production_javadoc_stub = bool(STUB_JAVADOC_RE.search(javadoc.group(1)))

        if not row.pack_dir_ok:
            row.findings.append(Finding("FAIL", "PACK_DIR_MISSING", f"packages/{pack_id} missing"))
        if not row.source_ok:
            row.findings.append(
                Finding("FAIL", "SOURCE_MISSING", f"DeviceDriver source missing: {row.source_path}")
            )
        if row.maturity == "STUB" and row.source_ok and not row.extends_protocol_stub:
            row.findings.append(
                Finding(
                    "FAIL",
                    "STUB_BASE_MISSING",
                    "listed STUB but does not extend ProtocolStubDeviceDriver",
                )
            )
        if row.maturity != "STUB" and row.extends_protocol_stub:
            row.findings.append(
                Finding(
                    "FAIL",
                    "STUB_BASE_UNEXPECTED",
                    f"extends ProtocolStubDeviceDriver but maturity={row.maturity}",
                )
            )
        if driver_id in stub_ids and driver_id in matrix:
            row.findings.append(
                Finding("FAIL", "STUB_AND_MATRIX", "id in both stub-list and matrix ENTRIES")
            )

        if row.source == "matrix":
            if row.maturity == "PRODUCTION" and row.loopback_ok is False:
                row.findings.append(
                    Finding("FAIL", "LOOPBACK_MISSING", f"missing {row.loopback_path}")
                )
            if row.maturity == "PRODUCTION" and row.production_javadoc_stub:
                row.findings.append(
                    Finding(
                        "FAIL",
                        "PRODUCTION_STUB_JAVADOC",
                        "PRODUCTION class javadoc contains stub/placeholder",
                    )
                )
            if row.write_claimed and row.write_looks_stub:
                row.findings.append(
                    Finding(
                        "FAIL",
                        "FALSE_WRITE",
                        "matrix claims WRITE but writePoint looks stub/not-implemented",
                    )
                )
            if (
                not row.write_claimed
                and row.write_looks_stub is False
                and row.maturity == "PRODUCTION"
            ):
                row.findings.append(
                    Finding(
                        "WARN",
                        "WRITE_UNDERCLAIM",
                        "writePoint looks real but matrix has no WRITE",
                    )
                )
            if row.top20 and row.interop_module and row.interop_in_workflow is False:
                row.findings.append(
                    Finding(
                        "FAIL",
                        "INTEROP_WORKFLOW_GAP",
                        f"{row.interop_module} not in driver-interop.yml",
                    )
                )
            if row.top20 and not row.interop_module and row.maturity == "PRODUCTION":
                row.findings.append(
                    Finding(
                        "WARN",
                        "INTEROP_MODULE_EMPTY",
                        "top-20 PRODUCTION without interopGradleModule",
                    )
                )

        if row.source == "default-beta":
            labels = docs_mentions(driver_id, docs)
            if "PRODUCTION" in labels:
                row.findings.append(
                    Finding(
                        "FAIL",
                        "DOCS_PRODUCTION_CODE_BETA",
                        "drivers.md implies PRODUCTION but code resolveMaturity → BETA",
                    )
                )
            else:
                row.findings.append(
                    Finding(
                        "WARN",
                        "UNMATRIXED_BETA",
                        "pack not in matrix ENTRIES nor stub-list → default BETA",
                    )
                )

        if row.maturity == "STUB":
            row.findings.append(
                Finding(
                    "INFO",
                    "STUB_CATALOG",
                    "protocol catalog stub — TCP reachability until promoted",
                )
            )

        row.readiness = classify(row)
        rows.append(row)

    summary = {
        "generatedAt": datetime.now(timezone.utc)
        .replace(microsecond=0)
        .isoformat()
        .replace("+00:00", "Z"),
        "catalogSize": len(rows),
        "expectedCatalogSize": EXPECTED_CATALOG,
        "byMaturity": {},
        "byReadiness": {},
        "bySource": {},
        "failCount": sum(1 for r in rows for f in r.findings if f.severity == "FAIL"),
        "warnCount": sum(1 for r in rows for f in r.findings if f.severity == "WARN"),
        "top20": top20,
        "top10": top10,
        "matrixEntries": len(matrix),
        "stubIds": len(stub_ids),
        "interopWorkflowModules": sorted(interop_mods),
        "honesty": "Lab/matrix readiness only — not field certification for 162 drivers",
    }
    if summary["catalogSize"] != EXPECTED_CATALOG:
        summary["failCount"] += 1
    for r in rows:
        summary["byMaturity"][r.maturity] = summary["byMaturity"].get(r.maturity, 0) + 1
        summary["byReadiness"][r.readiness] = summary["byReadiness"].get(r.readiness, 0) + 1
        summary["bySource"][r.source] = summary["bySource"].get(r.source, 0) + 1
    return rows, summary


def render_md(rows: list[DriverRow], summary: dict) -> str:
    out: list[str] = [
        "# Driver readiness audit (all packs)",
        "",
        f"> Generated: `{summary['generatedAt']}`  ",
        f"> Catalog: **{summary['catalogSize']}** / expected **{summary['expectedCatalogSize']}**  ",
        f"> Matrix ENTRIES: **{summary['matrixEntries']}** · Stub list: **{summary['stubIds']}**  ",
        f"> Findings: FAIL **{summary['failCount']}** · WARN **{summary['warnCount']}**  ",
        f"> Honesty: {summary['honesty']}",
        "",
        "## Summary",
        "",
        "| Dimension | Counts |",
        "|-----------|--------|",
        "| Maturity | "
        + ", ".join(f"`{k}`={v}" for k, v in sorted(summary["byMaturity"].items()))
        + " |",
        "| Readiness | "
        + ", ".join(f"`{k}`={v}" for k, v in sorted(summary["byReadiness"].items()))
        + " |",
        "| Source | "
        + ", ".join(f"`{k}`={v}" for k, v in sorted(summary["bySource"].items()))
        + " |",
        "",
        "### Readiness legend",
        "",
        "| Label | Meaning |",
        "|-------|---------|",
        "| `READY_LAB` | PRODUCTION + source + loopback; no FAIL (lab ≠ field) |",
        "| `SHELL_BETA` | Top-20 BETA shell |",
        "| `PARTIAL` | BETA / incomplete |",
        "| `STUB` | Protocol catalog stub |",
        "| `BLOCKED` | Honesty FAIL |",
        "",
        "## FAIL findings",
        "",
    ]
    fails = [(r, f) for r in rows for f in r.findings if f.severity == "FAIL"]
    if not fails:
        out.append("_None._")
    else:
        out += ["| driverId | code | detail |", "|----------|------|--------|"]
        for r, f in fails:
            out.append(f"| `{r.driver_id}` | `{f.code}` | {f.message} |")
    out += ["", "## WARN findings", ""]
    warns = [(r, f) for r in rows for f in r.findings if f.severity == "WARN"]
    if not warns:
        out.append("_None._")
    else:
        out += ["| driverId | code | detail |", "|----------|------|--------|"]
        for r, f in warns:
            out.append(f"| `{r.driver_id}` | `{f.code}` | {f.message} |")

    out += [
        "",
        "## Top-20 industrial",
        "",
        "| driverId | maturity | readiness | WRITE | loopback | interop |",
        "|----------|----------|-----------|-------|----------|---------|",
    ]
    for r in rows:
        if not r.top20:
            continue
        lb = "✓" if r.loopback_ok else ("✗" if r.loopback_ok is False else "—")
        iw = "✓" if r.interop_in_workflow else ("✗" if r.interop_in_workflow is False else "—")
        out.append(
            f"| `{r.driver_id}` | {r.maturity} | `{r.readiness}` | "
            f"{'Y' if r.write_claimed else 'n'} | {lb} | {iw} |"
        )

    out += [
        "",
        "## Full catalog (162)",
        "",
        "| driverId | pack | maturity | source | readiness | FAIL/WARN |",
        "|----------|------|----------|--------|-----------|----------|",
    ]
    for r in rows:
        codes = ",".join(f.code for f in r.findings if f.severity in ("FAIL", "WARN")) or "—"
        out.append(
            f"| `{r.driver_id}` | `{r.pack_id}` | {r.maturity} | {r.source} | `{r.readiness}` | {codes} |"
        )
    out += [
        "",
        "## How to re-run",
        "",
        "```bash",
        "python3 tools/driver-readiness-audit.py \\",
        "  --md docs/evidence/ot-trust/driver-readiness.md \\",
        "  --json docs/evidence/ot-trust/driver-readiness.json \\",
        "  --fail-on-findings",
        "```",
        "",
    ]
    return "\n".join(out)


def main(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--md", type=Path)
    ap.add_argument("--json", type=Path)
    ap.add_argument("--fail-on-findings", action="store_true")
    ap.add_argument("--fail-on-warn", action="store_true")
    args = ap.parse_args(argv)

    rows, summary = audit()
    payload = {
        "summary": summary,
        "drivers": [
            {
                **{k: v for k, v in asdict(r).items() if k != "findings"},
                "findings": [asdict(f) for f in r.findings],
            }
            for r in rows
        ],
    }
    md = render_md(rows, summary)
    if args.md:
        args.md.parent.mkdir(parents=True, exist_ok=True)
        args.md.write_text(md, encoding="utf-8")
        print(f"Wrote {args.md}", file=sys.stderr)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"Wrote {args.json}", file=sys.stderr)

    print(
        f"catalog={summary['catalogSize']} matrix={summary['matrixEntries']} "
        f"stubs={summary['stubIds']} FAIL={summary['failCount']} WARN={summary['warnCount']} "
        f"readiness={summary['byReadiness']}"
    )
    if args.fail_on_findings and summary["failCount"] > 0:
        return 1
    if args.fail_on_warn and summary["warnCount"] > 0:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
