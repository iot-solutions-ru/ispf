#!/usr/bin/env python3
"""Generate db/migration/mysql/V1__baseline.sql from postgresql pack (ADR-0079 B1 lab).

MySQL has no ADD COLUMN / CREATE INDEX IF NOT EXISTS and ignores inline REFERENCES on 8.4,
so the generator tracks the greenfield schema while it walks V1 + V71+ and emits plain DDL.
Untranslatable constructs fail the run instead of landing in the pack.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PG = ROOT / "packages/ispf-server/src/main/resources/db/migration/postgresql"
OUT = ROOT / "packages/ispf-server/src/main/resources/db/migration/mysql/V1__baseline.sql"

SKIP = {
    "V86__tenant_row_level_security.sql",
    "V89__random_uuid_compat.sql",
}

# utf8mb4_0900_bin: case-sensitive, NO PAD — PostgreSQL equality semantics for object paths.
TABLE_OPTIONS = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin"
UTF8MB4_BYTES = 4
# InnoDB DYNAMIC row format on 16 KiB pages.
MAX_KEY_BYTES = 3072
# 65535-byte row limit / 4 bytes per utf8mb4 character.
MAX_VARCHAR_CHARS = 16383

FIXED_KEY_BYTES = {
    "BIGINT": 8,
    "INT": 4,
    "SMALLINT": 2,
    "BOOLEAN": 1,
    "DATETIME(6)": 8,
    "DOUBLE": 8,
    "FLOAT": 4,
}

PG_TYPE_RE = re.compile(
    r"^(BIGINT\s+GENERATED\s+BY\s+DEFAULT\s+AS\s+IDENTITY"
    r"|DOUBLE\s+PRECISION"
    r"|TIMESTAMPTZ"
    r"|TIMESTAMP(?:\s+WITHOUT\s+TIME\s+ZONE)?"
    r"|VARCHAR\s*\(\s*\d+\s*\)"
    r"|CHAR\s*\(\s*\d+\s*\)"
    r"|BIGINT|INTEGER|INT|SMALLINT|BOOLEAN|TEXT|BYTEA|UUID|DOUBLE|REAL)(?![\w(])",
    re.I,
)
INLINE_FK_RE = re.compile(
    r"\s*\bREFERENCES\s+(\w+)\s*\(\s*(\w+)\s*\)"
    r"(\s+ON\s+DELETE\s+(?:CASCADE|SET\s+NULL|RESTRICT|NO\s+ACTION))?",
    re.I,
)


class GenerateError(RuntimeError):
    pass


@dataclass
class Column:
    name: str
    type: str
    # NOT NULL / NULL / DEFAULT / AUTO_INCREMENT only — what MODIFY COLUMN must repeat.
    attrs: str


@dataclass
class Table:
    name: str
    columns: dict[str, Column] = field(default_factory=dict)


@dataclass
class Schema:
    tables: dict[str, Table] = field(default_factory=dict)
    # PostgreSQL index names are schema-wide; MySQL needs the table for DROP INDEX.
    indexes: dict[str, str] = field(default_factory=dict)

    def table(self, name: str) -> Table:
        table = self.tables.get(name.lower())
        if table is None:
            raise GenerateError(f"unknown table {name}")
        return table

    def column(self, table: str, column: str) -> Column:
        col = self.table(table).columns.get(column.lower())
        if col is None:
            raise GenerateError(f"unknown column {table}.{column}")
        return col


def split_statements(sql: str) -> list[str]:
    """Split on top-level semicolons (not inside quotes or -- comments)."""
    chunks: list[str] = []
    buf: list[str] = []
    i = 0
    in_single = False
    while i < len(sql):
        if not in_single and sql.startswith("--", i):
            end = sql.find("\n", i)
            end = len(sql) if end < 0 else end
            buf.append(sql[i:end])
            i = end
            continue
        ch = sql[i]
        if ch == "'":
            if in_single and sql.startswith("''", i):
                buf.append("''")
                i += 2
                continue
            in_single = not in_single
        if ch == ";" and not in_single:
            buf.append(";")
            chunks.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(ch)
        i += 1
    tail = "".join(buf)
    if tail.strip():
        chunks.append(tail)
    return chunks


def split_leading_comments(chunk: str) -> tuple[list[str], str]:
    lines = chunk.splitlines()
    comments: list[str] = []
    index = 0
    while index < len(lines):
        stripped = lines[index].strip()
        if stripped and not stripped.startswith("--"):
            break
        if stripped:
            comments.append(stripped)
        index += 1
    return comments, "\n".join(lines[index:]).strip()


def strip_inline_comments(sql: str) -> str:
    out: list[str] = []
    in_single = False
    i = 0
    while i < len(sql):
        if not in_single and sql.startswith("--", i):
            end = sql.find("\n", i)
            i = len(sql) if end < 0 else end
            continue
        if sql[i] == "'":
            in_single = not in_single
        out.append(sql[i])
        i += 1
    return "".join(out)


def split_top_level(text: str) -> list[str]:
    parts: list[str] = []
    buf: list[str] = []
    depth = 0
    in_single = False
    for ch in text:
        if ch == "'":
            in_single = not in_single
        elif not in_single:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
            elif ch == "," and depth == 0:
                parts.append("".join(buf).strip())
                buf = []
                continue
        buf.append(ch)
    if "".join(buf).strip():
        parts.append("".join(buf).strip())
    return parts


def squash(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def map_outside_quotes(sql: str, fn) -> str:
    """Apply fn to unquoted segments only."""
    out: list[str] = []
    for index, segment in enumerate(re.split(r"('(?:[^']|'')*')", sql)):
        out.append(segment if index % 2 else fn(segment))
    return "".join(out)


def mysql_literals(sql: str) -> str:
    """PG standard_conforming_strings literals → MySQL (backslash is an escape there)."""
    def quoted(match: re.Match[str]) -> str:
        return match.group(0).replace("\\", "\\\\")

    sql = re.sub(r"'(?:[^']|'')*'", quoted, sql)
    return map_outside_quotes(
        sql,
        lambda seg: re.sub(r"\bFALSE\b", "0", re.sub(r"\bTRUE\b", "1", seg, flags=re.I), flags=re.I),
    )


def mysql_type(pg_type: str) -> tuple[str, bool]:
    """Return (MySQL type, auto_increment)."""
    t = squash(pg_type).upper()
    if t.startswith("BIGINT GENERATED"):
        return "BIGINT", True
    if t.startswith("TIMESTAMP"):
        return "DATETIME(6)", False
    match = re.fullmatch(r"VARCHAR ?\( ?(\d+) ?\)", t)
    if match:
        size = int(match.group(1))
        return ("LONGTEXT" if size > MAX_VARCHAR_CHARS else f"VARCHAR({size})"), False
    match = re.fullmatch(r"CHAR ?\( ?(\d+) ?\)", t)
    if match:
        return f"CHAR({match.group(1)})", False
    mapped = {
        "BIGINT": "BIGINT",
        "INTEGER": "INT",
        "INT": "INT",
        "SMALLINT": "SMALLINT",
        # Stored as TINYINT(1); spelling it TINYINT(1) raises deprecation warning 1681 on 8.4.
        "BOOLEAN": "BOOLEAN",
        "TEXT": "LONGTEXT",
        "BYTEA": "LONGBLOB",
        "UUID": "CHAR(36)",
        "DOUBLE PRECISION": "DOUBLE",
        "DOUBLE": "DOUBLE",
        "REAL": "FLOAT",
    }.get(t)
    if mapped is None:
        raise GenerateError(f"unmapped PostgreSQL type {pg_type}")
    return mapped, False


def mysql_default(column_type: str, attrs: str) -> str:
    if column_type == "DATETIME(6)":
        attrs = re.sub(
            r"\bDEFAULT\s+(?:CURRENT_TIMESTAMP|NOW\s*\(\s*\))",
            "DEFAULT CURRENT_TIMESTAMP(6)",
            attrs,
            flags=re.I,
        )
    if column_type in ("LONGTEXT", "LONGBLOB"):
        # Only expression defaults are allowed on TEXT/BLOB (MySQL 8.0.13+).
        attrs = re.sub(r"\bDEFAULT\s+('(?:[^']|'')*')", r"DEFAULT (\1)", attrs, flags=re.I)
    if re.search(r"\bNOW\s*\(|\bgen_random_uuid\b|\brandom_uuid\b", attrs, flags=re.I):
        raise GenerateError(f"untranslated default in '{attrs}'")
    return mysql_literals(attrs)


@dataclass
class ColumnDef:
    column: Column
    ddl: str
    foreign_key: str | None
    inline_key: bool


def parse_column(table: str, text: str) -> ColumnDef:
    text = squash(text)
    name, _, rest = text.partition(" ")
    match = PG_TYPE_RE.match(rest)
    if not match:
        raise GenerateError(f"cannot parse column type in {table}: {text}")
    column_type, auto_increment = mysql_type(match.group(1))
    attrs = rest[match.end():].strip()

    foreign_key = None
    fk = INLINE_FK_RE.search(attrs)
    if fk:
        on_delete = squash(fk.group(3) or "")
        foreign_key = (
            f"CONSTRAINT fk_{table}_{name} FOREIGN KEY ({name}) "
            f"REFERENCES {fk.group(1)}({fk.group(2)})" + (f" {on_delete.upper()}" if on_delete else "")
        )
        attrs = (attrs[:fk.start()] + attrs[fk.end():]).strip()

    attrs = mysql_default(column_type, attrs)
    inline_key = bool(re.search(r"\b(PRIMARY\s+KEY|UNIQUE)\b", attrs, flags=re.I))
    stored = squash(re.sub(r"\b(PRIMARY\s+KEY|UNIQUE)\b", "", attrs, flags=re.I))
    if auto_increment:
        stored = f"{stored} AUTO_INCREMENT".strip()
    ddl = f"{name} {column_type}" + (" AUTO_INCREMENT" if auto_increment else "") + (f" {attrs}" if attrs else "")
    return ColumnDef(Column(name, column_type, stored), ddl, foreign_key, inline_key)


def column_key_bytes(schema: Schema, table: str, column: str) -> int:
    column_type = schema.column(table, column).type
    match = re.fullmatch(r"(?:VAR)?CHAR\((\d+)\)", column_type)
    if match:
        return int(match.group(1)) * UTF8MB4_BYTES
    if column_type in FIXED_KEY_BYTES:
        return FIXED_KEY_BYTES[column_type]
    raise GenerateError(f"{table}.{column} ({column_type}) cannot be a key part without a prefix")


def key_parts(columns_text: str) -> list[tuple[str, str]]:
    parts = []
    for part in split_top_level(columns_text):
        match = re.fullmatch(r"(\w+)(?:\s+(ASC|DESC))?", squash(part), flags=re.I)
        if not match:
            raise GenerateError(f"unsupported key part '{part}'")
        parts.append((match.group(1), (match.group(2) or "").upper()))
    return parts


def assert_key_fits(schema: Schema, table: str, name: str, columns: list[str]) -> None:
    total = sum(column_key_bytes(schema, table, col) for col in columns)
    if total > MAX_KEY_BYTES:
        raise GenerateError(
            f"unique key {name} on {table} is {total} bytes (> {MAX_KEY_BYTES}); "
            "a prefix would change uniqueness — decide by hand"
        )


def translate_create_table(stmt: str, schema: Schema) -> list[str]:
    match = re.match(r"CREATE\s+TABLE\s+(IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\((.*)\)\s*;$", stmt, flags=re.I | re.S)
    if not match:
        raise GenerateError(f"cannot parse CREATE TABLE: {stmt[:80]}")
    if_not_exists, name, body = match.group(1), match.group(2), match.group(3)
    if name.lower() in schema.tables:
        if if_not_exists:
            return [f"-- skipped CREATE TABLE {name} (already in greenfield baseline)"]
        raise GenerateError(f"duplicate CREATE TABLE {name}")

    table = Table(name)
    schema.tables[name.lower()] = table
    items: list[str] = []
    foreign_keys: list[str] = []
    constraints: list[str] = []
    auto_columns: list[str] = []
    keyed_columns: set[str] = set()

    for raw in split_top_level(body):
        item = squash(raw)
        if re.match(r"(CONSTRAINT|PRIMARY\s+KEY|UNIQUE|FOREIGN\s+KEY|CHECK)\b", item, flags=re.I):
            constraints.append(mysql_literals(item))
            continue
        parsed = parse_column(name, item)
        table.columns[parsed.column.name.lower()] = parsed.column
        items.append(parsed.ddl)
        if parsed.foreign_key:
            foreign_keys.append(parsed.foreign_key)
        if parsed.inline_key:
            keyed_columns.add(parsed.column.name.lower())
            assert_key_fits(schema, name, f"{name}.{parsed.column.name}", [parsed.column.name])
        if "AUTO_INCREMENT" in parsed.column.attrs:
            auto_columns.append(parsed.column.name)

    for constraint in constraints:
        key = re.match(
            r"(?:CONSTRAINT\s+(\w+)\s+)?(PRIMARY\s+KEY|UNIQUE)\s*\((.*)\)$", constraint, flags=re.I
        )
        if key:
            columns = [col for col, _ in key_parts(key.group(3))]
            assert_key_fits(schema, name, key.group(1) or f"{name} {key.group(2)}", columns)
            keyed_columns.add(columns[0].lower())
            if key.group(1):
                schema.indexes[key.group(1).lower()] = name

    # InnoDB requires an AUTO_INCREMENT column to lead some index.
    for column in auto_columns:
        if column.lower() not in keyed_columns:
            index_name = f"idx_{name}_{column}"
            constraints.append(f"KEY {index_name} ({column})")
            schema.indexes[index_name.lower()] = name

    lines = items + constraints + foreign_keys
    return [f"CREATE TABLE {name} (\n    " + ",\n    ".join(lines) + f"\n) {TABLE_OPTIONS};"]


def translate_create_index(stmt: str, schema: Schema) -> list[str]:
    match = re.match(
        r"CREATE\s+(UNIQUE\s+)?INDEX\s+(IF\s+NOT\s+EXISTS\s+)?(\w+)\s+ON\s+(\w+)\s*(USING\s+\w+\s*)?\((.*)\)\s*;$",
        stmt,
        flags=re.I | re.S,
    )
    if not match:
        raise GenerateError(f"cannot parse CREATE INDEX: {stmt[:80]}")
    unique, if_not_exists, name, table, using, columns_text = match.groups()
    if using:
        raise GenerateError(f"index {name} uses {squash(using)} — no MySQL equivalent")
    if name.lower() in schema.indexes:
        if if_not_exists:
            return [f"-- skipped CREATE INDEX {name} (already in greenfield baseline)"]
        raise GenerateError(f"duplicate index {name}")
    schema.table(table)
    parts = key_parts(columns_text)
    widths = [column_key_bytes(schema, table, col) for col, _ in parts]
    rendered = [col + (f" {order}" if order else "") for col, order in parts]
    total = sum(widths)
    if total > MAX_KEY_BYTES:
        if unique:
            raise GenerateError(
                f"unique index {name} is {total} bytes (> {MAX_KEY_BYTES}); "
                "a prefix would change uniqueness — decide by hand"
            )
        widest = max(range(len(parts)), key=lambda idx: widths[idx])
        widest_type = schema.column(table, parts[widest][0]).type
        if not widest_type.startswith("VARCHAR"):
            raise GenerateError(f"index {name} is {total} bytes and has no VARCHAR to prefix")
        prefix_chars = (MAX_KEY_BYTES - (total - widths[widest])) // UTF8MB4_BYTES
        col, order = parts[widest]
        # Prefix keeps lookups selective; it cannot serve ORDER BY on that column.
        rendered[widest] = f"{col}({prefix_chars})" + (f" {order}" if order else "")
    schema.indexes[name.lower()] = table
    kind = "UNIQUE INDEX" if unique else "INDEX"
    return [f"CREATE {kind} {name} ON {table} ({', '.join(rendered)});"]


def modify_column(table: str, column: Column) -> str:
    return f"ALTER TABLE {table} MODIFY COLUMN {column.name} {column.type}" + (
        f" {column.attrs}" if column.attrs else ""
    ) + ";"


def translate_alter_table(stmt: str, schema: Schema) -> list[str]:
    match = re.match(r"ALTER\s+TABLE\s+(\w+)\s+(.*);$", stmt, flags=re.I | re.S)
    if not match:
        raise GenerateError(f"cannot parse ALTER TABLE: {stmt[:80]}")
    name, action = match.group(1), squash(match.group(2))
    table = schema.table(name)

    add = re.match(
        r"ADD\s+(?:COLUMN\s+)?(?!(?:CONSTRAINT|PRIMARY|UNIQUE|FOREIGN|CHECK|INDEX|KEY)\b)"
        r"(IF\s+NOT\s+EXISTS\s+)?(\w+\s+.+)$",
        action,
        flags=re.I,
    )
    if add:
        parsed = parse_column(name, add.group(2))
        column_name = parsed.column.name
        if column_name.lower() in table.columns:
            if add.group(1):
                return [f"-- skipped ADD COLUMN {name}.{column_name} (already in greenfield baseline)"]
            raise GenerateError(f"duplicate column {name}.{column_name}")
        if parsed.inline_key or "AUTO_INCREMENT" in parsed.column.attrs:
            raise GenerateError(f"keyed ADD COLUMN {name}.{column_name} is not supported by the generator")
        table.columns[column_name.lower()] = parsed.column
        out = f"ALTER TABLE {name} ADD COLUMN {parsed.ddl}"
        if parsed.foreign_key:
            out += f", ADD {parsed.foreign_key}"
        return [out + ";"]

    drop = re.match(r"DROP\s+COLUMN\s+(IF\s+EXISTS\s+)?(\w+)$", action, flags=re.I)
    if drop:
        column_name = drop.group(2)
        if column_name.lower() not in table.columns:
            if drop.group(1):
                return [f"-- skipped DROP COLUMN {name}.{column_name} (not in greenfield baseline)"]
            raise GenerateError(f"unknown column {name}.{column_name}")
        del table.columns[column_name.lower()]
        return [f"ALTER TABLE {name} DROP COLUMN {column_name};"]

    rename = re.match(r"RENAME\s+TO\s+(\w+)$", action, flags=re.I)
    if rename:
        new_name = rename.group(1)
        del schema.tables[name.lower()]
        table.name = new_name
        schema.tables[new_name.lower()] = table
        for index, owner in list(schema.indexes.items()):
            if owner.lower() == name.lower():
                schema.indexes[index] = new_name
        return [f"ALTER TABLE {name} RENAME TO {new_name};"]

    rename_column = re.match(r"RENAME\s+COLUMN\s+(\w+)\s+TO\s+(\w+)$", action, flags=re.I)
    if rename_column:
        column = schema.column(name, rename_column.group(1))
        del table.columns[column.name.lower()]
        column.name = rename_column.group(2)
        table.columns[column.name.lower()] = column
        return [f"ALTER TABLE {name} RENAME COLUMN {rename_column.group(1)} TO {column.name};"]

    retype = re.match(r"ALTER\s+COLUMN\s+(\w+)\s+TYPE\s+(.+)$", action, flags=re.I)
    if retype:
        column = schema.column(name, retype.group(1))
        column.type, auto_increment = mysql_type(retype.group(2))
        if auto_increment:
            raise GenerateError(f"identity retype on {name}.{column.name}")
        column.attrs = mysql_default(column.type, column.attrs)
        return [modify_column(name, column)]

    nullability = re.match(r"ALTER\s+COLUMN\s+(\w+)\s+(SET|DROP)\s+NOT\s+NULL$", action, flags=re.I)
    if nullability:
        column = schema.column(name, nullability.group(1))
        attrs = squash(re.sub(r"\b(NOT\s+NULL|NULL)\b", "", column.attrs, flags=re.I))
        nullness = "NOT NULL" if nullability.group(2).upper() == "SET" else "NULL"
        column.attrs = f"{nullness} {attrs}".strip()
        return [modify_column(name, column)]

    set_default = re.match(r"ALTER\s+COLUMN\s+(\w+)\s+SET\s+DEFAULT\s+(.+)$", action, flags=re.I)
    if set_default:
        column = schema.column(name, set_default.group(1))
        default = mysql_default(column.type, f"DEFAULT {set_default.group(2)}")
        column.attrs = squash(re.sub(r"\bDEFAULT\s+(\([^)]*\)|'(?:[^']|'')*'|\S+)", "", column.attrs, flags=re.I))
        column.attrs = f"{column.attrs} {default}".strip()
        return [f"ALTER TABLE {name} ALTER COLUMN {column.name} SET {default};"]

    raise GenerateError(f"unsupported ALTER TABLE {name}: {action}")


def translate_drop_index(stmt: str, schema: Schema) -> list[str]:
    match = re.match(r"DROP\s+INDEX\s+(IF\s+EXISTS\s+)?(\w+)\s*;$", stmt, flags=re.I)
    if not match:
        raise GenerateError(f"cannot parse DROP INDEX: {stmt[:80]}")
    name = match.group(2)
    table = schema.indexes.pop(name.lower(), None)
    if table is None:
        if match.group(1):
            return [f"-- skipped DROP INDEX {name} (not in greenfield baseline)"]
        raise GenerateError(f"unknown index {name}")
    return [f"DROP INDEX {name} ON {table};"]


def translate_dml(stmt: str) -> list[str]:
    if re.search(r"\bregexp_replace\s*\(", stmt, flags=re.I):
        return ["-- skipped PG regexp_replace data rewrite (greenfield)"]
    # PG string concat → CONCAT (|| is logical OR on MySQL).
    stmt = re.sub(
        r"'([^']*)'\s*\|\|\s*REPLACE\(([^,]+),\s*'((?:\\'|[^'])*)',\s*'((?:\\'|[^'])*)'\)\s*\|\|\s*'([^']*)'",
        r"CONCAT('\1', REPLACE(\2, '\3', '\4'), '\5')",
        stmt,
        flags=re.I,
    )
    unquoted = "".join(re.split(r"'(?:[^']|'')*'", stmt))
    if "||" in unquoted:
        raise GenerateError(f"untranslated PG || concat: {squash(stmt)[:120]}")
    if re.search(r"::|\bILIKE\b|\bRETURNING\b|\bON\s+CONFLICT\b", unquoted, flags=re.I):
        raise GenerateError(f"PostgreSQL-only DML: {squash(stmt)[:120]}")
    lines = [line.rstrip() for line in mysql_literals(stmt).splitlines()]
    return ["\n".join(lines)]


def translate_statement(stmt: str, schema: Schema) -> list[str]:
    stmt = strip_inline_comments(stmt).strip()
    if not stmt.endswith(";"):
        stmt += ";"
    head = squash(stmt[:60]).upper()
    if head.startswith("CREATE TABLE"):
        return translate_create_table(stmt, schema)
    if re.match(r"CREATE (UNIQUE )?INDEX", head):
        return translate_create_index(stmt, schema)
    if head.startswith("ALTER TABLE"):
        return translate_alter_table(stmt, schema)
    if head.startswith("DROP INDEX"):
        return translate_drop_index(stmt, schema)
    if re.match(r"(INSERT|UPDATE|DELETE)\b", head):
        return translate_dml(stmt)
    raise GenerateError(f"unsupported statement: {squash(stmt)[:120]}")


def translate_file(sql: str, schema: Schema) -> str:
    sql = re.sub(r"\$\{rls_block_start\}.*?\$\{rls_block_end\}", "", sql, flags=re.S)
    out: list[str] = []
    for chunk in split_statements(sql):
        comments, stmt = split_leading_comments(chunk)
        if comments:
            if out:
                out.append("")
            out.extend(comments)
        if stmt:
            out.extend(translate_statement(stmt, schema))
    return "\n".join(out)


def ver_key(path: Path) -> int:
    match = re.match(r"V(\d+)", path.name)
    return int(match.group(1)) if match else 0


def main() -> int:
    parts = [
        "-- ISPF MySQL 8.4 greenfield baseline (ADR-0079 B1 lab)",
        "-- Generated from postgresql V1 + V71+ (skipped V86 RLS, V89 random_uuid).",
        "-- Empty database only. MySQL DDL is not transactional: if V1 fails, drop the database and retry.",
        "-- Every table: InnoDB, utf8mb4, utf8mb4_0900_bin (case-sensitive, NO PAD).",
        "-- TIMESTAMP → DATETIME(6), UUID → CHAR(36), BOOLEAN (stored TINYINT(1)), TEXT → LONGTEXT, BYTEA → LONGBLOB.",
        "-- Regenerate: python tools/mysql-baseline-generate.py",
    ]
    schema = Schema()
    for path in sorted(PG.glob("V*.sql"), key=ver_key):
        if path.name in SKIP:
            parts.extend(["", f"-- skipped {path.name} (PG-only)"])
            continue
        try:
            body = translate_file(path.read_text(encoding="utf-8"), schema)
        except GenerateError as ex:
            print(f"ERROR in {path.name}: {ex}", file=sys.stderr)
            return 1
        parts.extend(["", f"-- ===== {path.name} =====", body.strip()])
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(parts)) + "\n"
    OUT.write_text(text, encoding="utf-8", newline="\n")
    print(f"wrote {OUT} ({len(text.splitlines())} lines, {len(schema.tables)} tables)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
