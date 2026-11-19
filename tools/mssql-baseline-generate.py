#!/usr/bin/env python3
"""Generate db/migration/mssql/V1__baseline.sql from postgresql pack (ADR-0078)."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PG = ROOT / "packages/ispf-server/src/main/resources/db/migration/postgresql"
OUT = ROOT / "packages/ispf-server/src/main/resources/db/migration/mssql/V1__baseline.sql"

SKIP = {
    "V86__tenant_row_level_security.sql",
    "V89__random_uuid_compat.sql",
}


def translate(sql: str) -> str:
    sql = re.sub(r"\$\{rls_block_start\}.*?\$\{rls_block_end\}", "", sql, flags=re.S)
    sql = re.sub(
        r"CREATE\s+INDEX\s+IF\s+NOT\s+EXISTS\s+\S+\s+ON\s+\S+\s+USING\s+gin\s*\([^;]+;",
        "",
        sql,
        flags=re.I,
    )
    sql = re.sub(
        r"CREATE\s+INDEX\s+\S+\s+ON\s+\S+\s+USING\s+gin\s*\([^;]+;",
        "",
        sql,
        flags=re.I,
    )
    sql = re.sub(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS", "CREATE TABLE", sql, flags=re.I)
    sql = re.sub(r"CREATE\s+INDEX\s+IF\s+NOT\s+EXISTS", "CREATE INDEX", sql, flags=re.I)
    sql = re.sub(
        r"CREATE\s+UNIQUE\s+INDEX\s+IF\s+NOT\s+EXISTS",
        "CREATE UNIQUE INDEX",
        sql,
        flags=re.I,
    )
    sql = re.sub(r"\bBYTEA\b", "VARBINARY(MAX)", sql, flags=re.I)
    sql = re.sub(r"\bUUID\b", "UNIQUEIDENTIFIER", sql, flags=re.I)
    sql = re.sub(r"\bBOOLEAN\b", "BIT", sql, flags=re.I)
    sql = re.sub(r"\bTEXT\b", "NVARCHAR(MAX)", sql, flags=re.I)
    sql = re.sub(r"\bTIMESTAMPTZ\b", "DATETIMEOFFSET", sql, flags=re.I)
    sql = re.sub(r"\bTIMESTAMP\b", "DATETIME2", sql, flags=re.I)
    sql = re.sub(
        r"BIGINT\s+GENERATED\s+BY\s+DEFAULT\s+AS\s+IDENTITY",
        "BIGINT IDENTITY(1,1)",
        sql,
        flags=re.I,
    )
    sql = re.sub(
        r"GENERATED\s+BY\s+DEFAULT\s+AS\s+IDENTITY",
        "IDENTITY(1,1)",
        sql,
        flags=re.I,
    )
    sql = re.sub(r"\bTRUE\b", "1", sql)
    sql = re.sub(r"\bFALSE\b", "0", sql)
    # T-SQL uses ADD, not ADD COLUMN / IF NOT EXISTS — guard with COL_LENGTH for greenfield
    # concatenations that re-add columns already present from earlier slices.
    def add_column(match: re.Match[str]) -> str:
        table = match.group(1)
        rest = match.group(2).rstrip().rstrip(";")
        # first token is column name
        col = rest.split(None, 1)[0]
        return (
            f"IF COL_LENGTH('{table}', '{col}') IS NULL "
            f"ALTER TABLE {table} ADD {rest};"
        )

    sql = re.sub(
        r"ALTER\s+TABLE\s+(\S+)\s+ADD\s+COLUMN\s+IF\s+NOT\s+EXISTS\s+([^;]+);",
        add_column,
        sql,
        flags=re.I,
    )
    sql = re.sub(
        r"ALTER\s+TABLE\s+(\S+)\s+ADD\s+COLUMN\s+([^;]+);",
        add_column,
        sql,
        flags=re.I,
    )
    sql = re.sub(
        r"ALTER\s+TABLE\s+(\S+)\s+ADD\s+(?!CONSTRAINT\b|PRIMARY\b|UNIQUE\b|FOREIGN\b|CHECK\b|DEFAULT\b)([^;]+);",
        add_column,
        sql,
        flags=re.I,
    )
    sql = re.sub(r"\bDEFAULT\s+NOW\s*\(\s*\)", "DEFAULT CURRENT_TIMESTAMP", sql, flags=re.I)
    sql = re.sub(r"\bNOW\s*\(\s*\)", "CURRENT_TIMESTAMP", sql, flags=re.I)
    sql = re.sub(r"gen_random_uuid\s*\(\s*\)", "NEWID()", sql, flags=re.I)
    sql = re.sub(r"random_uuid\s*\(\s*\)", "NEWID()", sql, flags=re.I)
    # PG string concat → CONCAT (|| is bitwise OR on MSSQL)
    sql = re.sub(
        r"'([^']*)'\s*\|\|\s*REPLACE\(([^,]+),\s*'((?:\\'|[^'])*)',\s*'((?:\\'|[^'])*)'\)\s*\|\|\s*'([^']*)'",
        r"CONCAT('\1', REPLACE(\2, '\3', '\4'), '\5')",
        sql,
        flags=re.I,
    )
    if "||" in sql:
        # Fail loud in generated file rather than emit invalid T-SQL.
        sql = (
            "-- WARNING: untranslated PG || concat remains — fix mssql-baseline-generate.py\n"
            + sql
        )

    # PG regexp_replace data rewrites — no-op on empty greenfield; not valid T-SQL.
    sql = re.sub(
        r"UPDATE\s+\S+\s+SET\s+\S+\s+=\s+regexp_replace\s*\([\s\S]*?;",
        "-- skipped PG regexp_replace data rewrite (greenfield)",
        sql,
        flags=re.I,
    )
    # ALTER TABLE x RENAME TO y → sp_rename
    sql = re.sub(
        r"ALTER\s+TABLE\s+(\S+)\s+RENAME\s+TO\s+(\S+)\s*;",
        r"EXEC sp_rename '\1', '\2';",
        sql,
        flags=re.I,
    )
    sql = re.sub(
        r"ALTER\s+TABLE\s+(\S+)\s+RENAME\s+COLUMN\s+(\S+)\s+TO\s+(\S+)\s*;",
        r"EXEC sp_rename '\1.\2', '\3', 'COLUMN';",
        sql,
        flags=re.I,
    )
    # ALTER COLUMN … TYPE t → T-SQL omits TYPE
    sql = re.sub(
        r"ALTER\s+COLUMN\s+(\S+)\s+TYPE\s+",
        r"ALTER COLUMN \1 ",
        sql,
        flags=re.I,
    )
    # Oversized VARCHAR(n) → VARCHAR(MAX) when n > 8000
    def varchar_max(match: re.Match[str]) -> str:
        size = int(match.group(1))
        return "VARCHAR(MAX)" if size > 8000 else match.group(0)

    sql = re.sub(r"\bVARCHAR\((\d+)\)", varchar_max, sql, flags=re.I)
    # Known SET NOT NULL (need full type on MSSQL)
    sql = re.sub(
        r"ALTER\s+TABLE\s+variable_samples\s+ALTER\s+COLUMN\s+observed_at\s+SET\s+NOT\s+NULL\s*;",
        "ALTER TABLE variable_samples ALTER COLUMN observed_at DATETIME2 NOT NULL;",
        sql,
        flags=re.I,
    )
    # SET DEFAULT via ALTER COLUMN is PG-only — skip on greenfield when column already has default
    sql = re.sub(
        r"ALTER\s+TABLE\s+(\S+)\s+ALTER\s+COLUMN\s+(\S+)\s+SET\s+DEFAULT\s+[^;]+;",
        r"-- skipped PG ALTER COLUMN \2 SET DEFAULT on \1 (greenfield)",
        sql,
        flags=re.I,
    )
    # Catch remaining SET NOT NULL / DROP NOT NULL
    if re.search(r"ALTER\s+COLUMN\s+\S+\s+(SET|DROP)\s+NOT\s+NULL", sql, flags=re.I):
        sql = (
            "-- WARNING: untranslated ALTER COLUMN SET/DROP NOT NULL — fix generator\n"
            + sql
        )

    def drop_column(match: re.Match[str]) -> str:
        table, column = match.group(1), match.group(2)
        return (
            f"IF COL_LENGTH('{table}', '{column}') IS NOT NULL "
            f"ALTER TABLE {table} DROP COLUMN {column};"
        )

    sql = re.sub(
        r"ALTER\s+TABLE\s+(\S+)\s+DROP\s+COLUMN\s+IF\s+EXISTS\s+(\S+)\s*;",
        drop_column,
        sql,
        flags=re.I,
    )

    def drop_index(match: re.Match[str]) -> str:
        name = match.group(1).strip().rstrip(";")
        # Table inferred from known greenfield indexes; extend as pack grows.
        table_by_index = {
            "uq_application_outbox_idempotency": "application_outbox",
        }
        table = table_by_index.get(name)
        if not table:
            return (
                f"-- TODO mssql: DROP INDEX {name} (unknown table)\n"
                f"-- DROP INDEX IF EXISTS {name};"
            )
        return (
            f"IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'{name}' "
            f"AND object_id = OBJECT_ID(N'{table}')) "
            f"DROP INDEX {name} ON {table};"
        )

    sql = re.sub(
        r"DROP\s+INDEX\s+IF\s+EXISTS\s+(\S+)\s*;",
        drop_index,
        sql,
        flags=re.I,
    )
    return sql


def ver_key(path: Path) -> int:
    match = re.match(r"V(\d+)", path.name)
    return int(match.group(1)) if match else 0


def main() -> None:
    parts = [
        "-- ISPF MS SQL Server greenfield baseline (ADR-0078 B1)",
        "-- Generated from postgresql V1 + V71+ (skipped V86 RLS, V89 random_uuid).",
        "-- Empty database only. TIMESTAMP mapped to DATETIME2 (not rowversion).",
        "-- Regenerate: python tools/mssql-baseline-generate.py",
        # Omit session SET … ON — Flyway/JDBC batches reject them without GO, and
        # SQL Server 2022 defaults are fine for greenfield DDL.
    ]
    for path in sorted(PG.glob("V*.sql"), key=ver_key):
        if path.name in SKIP:
            parts.extend(["", f"-- skipped {path.name} (PG-only)"])
            continue
        parts.extend(["", f"-- ===== {path.name} =====", translate(path.read_text(encoding="utf-8")).strip()])
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(parts)) + "\n"
    # Flyway SQL Server default delimiter is GO — semicolon alone keeps the whole
    # script in one batch and opaque "near SET" errors. Batch each statement.
    text = ensure_go_batches(text)
    OUT.write_text(text, encoding="utf-8")
    print(f"wrote {OUT} ({len(text.splitlines())} lines)")


def ensure_go_batches(sql: str) -> str:
    """Insert GO after top-level semicolons (not inside quotes or -- comments)."""
    out: list[str] = []
    i = 0
    in_single = False
    while i < len(sql):
        ch = sql[i]
        # Line comment: copy through end-of-line (do not treat ; as a delimiter).
        if not in_single and ch == "-" and i + 1 < len(sql) and sql[i + 1] == "-":
            while i < len(sql) and sql[i] != "\n":
                out.append(sql[i])
                i += 1
            continue
        if ch == "'" and not in_single:
            in_single = True
            out.append(ch)
            i += 1
            continue
        if in_single:
            out.append(ch)
            if ch == "'" and i + 1 < len(sql) and sql[i + 1] == "'":
                out.append("'")
                i += 2
                continue
            if ch == "'":
                in_single = False
            i += 1
            continue
        if ch == ";":
            # Always terminate the GO line; do not consume the following newline or
            # the next token glues on (GOCREATE).
            out.append(";\nGO\n")
            i += 1
            continue
        out.append(ch)
        i += 1
    text = "".join(out)
    text = re.sub(r"\nGO\nGO\n", "\nGO\n", text)
    if not text.rstrip().endswith("GO"):
        text = text.rstrip() + "\nGO\n"
    return text


if __name__ == "__main__":
    main()
