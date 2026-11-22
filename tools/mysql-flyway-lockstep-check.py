#!/usr/bin/env python3
"""ADR-0079: PG Flyway Vnn must have a mysql twin or an exception.

Usage (repo root):
  python tools/mysql-flyway-lockstep-check.py
  python tools/mysql-flyway-lockstep-check.py --exceptions docs/en/mysql-flyway-exceptions.txt

Exit 0 when every postgresql/V*.sql either:
  - has packages/ispf-server/src/main/resources/db/migration/mysql/V{n}__*.sql, or
  - is listed in the exceptions file (one version number or Vnn per line; # comments OK).

Baseline: mysql ships a single generated V1__baseline.sql covering the PG pack
(tools/mysql-baseline-generate.py). Applied mysql/V1 is frozen — a new PG Vnn needs a
mysql/Vnn twin (or an exception), not a regenerated V1 with a new checksum.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PG_DIR = ROOT / "packages/ispf-server/src/main/resources/db/migration/postgresql"
MYSQL_DIR = ROOT / "packages/ispf-server/src/main/resources/db/migration/mysql"
DEFAULT_EXCEPTIONS = ROOT / "docs/en/mysql-flyway-exceptions.txt"

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
    mysql = versions_in(MYSQL_DIR)
    exceptions = load_exceptions(args.exceptions)

    if not pg:
        print(f"ERROR: no postgresql migrations under {PG_DIR}", file=sys.stderr)
        return 2
    if 1 not in mysql:
        print(f"ERROR: mysql pack missing V1 under {MYSQL_DIR}", file=sys.stderr)
        return 2

    # V1 on mysql is the greenfield baseline (may subsume many PG versions).
    missing = sorted(v for v in pg if v != 1 and v not in mysql and v not in exceptions)
    if missing:
        print("FAIL: postgresql versions without mysql twin or exception:")
        for version in missing:
            print(f"  V{version}")
        print(f"Add mysql/V{{n}}__*.sql or list the version in {args.exceptions}")
        return 1

    covered = sorted(v for v in pg if v == 1 or v in mysql or v in exceptions)
    print(
        f"OK: lockstep check passed "
        f"(postgresql={len(pg)} mysql={len(mysql)} exceptions={len(exceptions)} covered={len(covered)})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
