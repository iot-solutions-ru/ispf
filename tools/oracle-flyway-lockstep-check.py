#!/usr/bin/env python3
"""ADR-0080: PG Flyway Vnn must have an oracle twin or an exception.

Usage (repo root):
  python tools/oracle-flyway-lockstep-check.py
  python tools/oracle-flyway-lockstep-check.py --exceptions docs/en/oracle-flyway-exceptions.txt

Exit 0 when every postgresql/V*.sql either:
  - has packages/ispf-server/src/main/resources/db/migration/oracle/V{n}__*.sql, or
  - is listed in the exceptions file (one version number or Vnn per line; # comments OK).

Baseline: oracle ships a single generated V1__baseline.sql covering the PG pack
(tools/oracle-baseline-generate.py). Applied oracle/V1 is frozen — a new PG Vnn needs an
oracle/Vnn twin (or an exception), not a regenerated V1 with a new checksum.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PG_DIR = ROOT / "packages/ispf-server/src/main/resources/db/migration/postgresql"
ORACLE_DIR = ROOT / "packages/ispf-server/src/main/resources/db/migration/oracle"
DEFAULT_EXCEPTIONS = ROOT / "docs/en/oracle-flyway-exceptions.txt"

VERSION_RE = re.compile(r"^V(\d+)__", re.IGNORECASE)


def versions_in(directory: Path) -> set[int]:
    found: set[int] = set()
    if not directory.is_dir():
        return found
    for path in directory.iterdir():
        if not path.is_file() or not path.name.endswith(".sql"):
            continue
        match = VERSION_RE.match(path.name)
        if match:
            found.add(int(match.group(1)))
    return found


def load_exceptions(path: Path) -> set[int]:
    if not path.is_file():
        return set()
    out: set[int] = set()
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line.upper().startswith("V"):
            line = line[1:]
        if line.isdigit():
            out.add(int(line))
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--exceptions",
        type=Path,
        default=DEFAULT_EXCEPTIONS,
        help="baseline-subsumed / deferred twin exception list",
    )
    args = parser.parse_args()

    pg = versions_in(PG_DIR)
    oracle = versions_in(ORACLE_DIR)
    exceptions = load_exceptions(args.exceptions)

    if not pg:
        print(f"ERROR: no postgresql migrations under {PG_DIR}", file=sys.stderr)
        return 2
    if 1 not in oracle:
        print(f"ERROR: oracle pack missing V1 under {ORACLE_DIR}", file=sys.stderr)
        return 2

    # V1 on oracle is the greenfield baseline (may subsume many PG versions).
    missing = sorted(v for v in pg if v != 1 and v not in oracle and v not in exceptions)
    if missing:
        print("FAIL: postgresql versions without oracle twin or exception:")
        for version in missing:
            print(f"  V{version}")
        print(f"Add oracle/V{{n}}__*.sql or list the version in {args.exceptions}")
        return 1

    covered = sorted(v for v in pg if v == 1 or v in oracle or v in exceptions)
    print(
        f"OK: lockstep check passed "
        f"(postgresql={len(pg)} oracle={len(oracle)} exceptions={len(exceptions)} covered={len(covered)})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
