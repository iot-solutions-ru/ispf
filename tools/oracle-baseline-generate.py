#!/usr/bin/env python3
"""Generate db/migration/oracle/V1__baseline.sql from postgresql pack (ADR-0080 B1 lab).

Oracle 19c has no ADD COLUMN / CREATE INDEX IF NOT EXISTS, no BOOLEAN, no multi-row VALUES,
refuses a second index on an indexed column list (ORA-01408) and stores '' as NULL, so the
generator tracks the greenfield schema while it walks V1 + V71+ and emits plain 19c DDL
(23ai accepts it too). Untranslatable constructs fail the run instead of landing in the pack.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PG = ROOT / "packages/ispf-server/src/main/resources/db/migration/postgresql"
OUT = ROOT / "packages/ispf-server/src/main/resources/db/migration/oracle/V1__baseline.sql"

SKIP = {
    "V86__tenant_row_level_security.sql",
    "V89__random_uuid_compat.sql",
}

# MAX_STRING_SIZE=STANDARD (the default): VARCHAR2 holds at most 4000 bytes.
MAX_VARCHAR2_BYTES = 4000
# AL32UTF8 with CHAR length semantics: key-length checks count 4 bytes per character.
CHAR_BYTES = 4
# ORA-01450 on 8 KiB blocks is 6398 bytes; keep room for per-column and rowid overhead.
MAX_KEY_BYTES = 6398 - 64
# Long identifiers need COMPATIBLE >= 12.2 (19c and 23ai default).
MAX_IDENTIFIER_BYTES = 128

FIXED_KEY_BYTES = {
    "NUMBER(19)": 22,
    "NUMBER(10)": 22,
    "NUMBER(5)": 22,
    "NUMBER(1)": 22,
    "TIMESTAMP(6)": 11,
    "BINARY_DOUBLE": 8,
    "BINARY_FLOAT": 4,
}
LOB_TYPES = {"CLOB", "BLOB"}

# Oracle SQL reserved words (V$RESERVED_WORDS.RESERVED = 'Y'): never valid unquoted.
ORACLE_RESERVED = frozenset("""
ACCESS ADD ALL ALTER AND ANY AS ASC AUDIT BETWEEN BY CHAR CHECK CLUSTER COLUMN COLUMN_VALUE
COMMENT COMPRESS CONNECT CREATE CURRENT DATE DECIMAL DEFAULT DELETE DESC DISTINCT DROP ELSE
EXCLUSIVE EXISTS FILE FLOAT FOR FROM GRANT GROUP HAVING IDENTIFIED IMMEDIATE IN INCREMENT
INDEX INITIAL INSERT INTEGER INTERSECT INTO IS LEVEL LIKE LOCK LONG MAXEXTENTS MINUS MLSLABEL
MODE MODIFY NESTED_TABLE_ID NOAUDIT NOCOMPRESS NOT NOWAIT NULL NUMBER OF OFFLINE ON ONLINE
OPTION OR ORDER PCTFREE PRIOR PUBLIC RAW RENAME RESOURCE REVOKE ROW ROWID ROWNUM ROWS SELECT
SESSION SET SHARE SIZE SMALLINT START SUCCESSFUL SYNONYM SYSDATE TABLE THEN TO TRIGGER UID
UNION UNIQUE UPDATE USER VALIDATE VALUES VARCHAR VARCHAR2 VIEW WHENEVER WHERE WITH
""".split())

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
ATTR_RE = re.compile(
    r"\s*(?:(?P<notnull>NOT\s+NULL)\b|(?P<null>NULL)\b|(?P<pk>PRIMARY\s+KEY)\b|(?P<unique>UNIQUE)\b"
    r"|DEFAULT\s+(?P<default>'(?:[^']|'')*'|\w+\s*\(\s*\)|-?\d+(?:\.\d+)?\b|\w+\b))",
    re.I,
)


class GenerateError(RuntimeError):
    pass


@dataclass
class Column:
    name: str
    type: str
    default: str | None = None
    not_null: bool = False
    boolean: bool = False
    identity: bool = False


@dataclass
class Table:
    name: str
    columns: dict[str, Column] = field(default_factory=dict)
    # index name (lower; "#pk" / "#uq:<cols>" for unnamed constraints) -> (key parts, unique)
    indexes: dict[str, tuple[tuple[tuple[str, str], ...], bool]] = field(default_factory=dict)


@dataclass
class Schema:
    tables: dict[str, Table] = field(default_factory=dict)
    # Oracle index namespace (CREATE INDEX + named PK/UNIQUE) -> owning table
    index_owner: dict[str, str] = field(default_factory=dict)
    constraint_names: set[str] = field(default_factory=set)
    skipped_indexes: set[str] = field(default_factory=set)
    seeded: set[str] = field(default_factory=set)
    empty_string_relaxed: list[str] = field(default_factory=list)
    reserved_quoted: list[str] = field(default_factory=list)
    duplicate_indexes: list[str] = field(default_factory=list)

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


def oracle_literals(sql: str) -> str:
    """TRUE / FALSE → 1 / 0 outside quotes (no BOOLEAN before 23ai)."""
    return map_outside_quotes(
        sql,
        lambda seg: re.sub(r"\bFALSE\b", "0", re.sub(r"\bTRUE\b", "1", seg, flags=re.I), flags=re.I),
    )


def check_identifier(kind: str, name: str) -> str:
    if len(name.encode("utf-8")) > MAX_IDENTIFIER_BYTES:
        raise GenerateError(f"{kind} name {name} exceeds {MAX_IDENTIFIER_BYTES} bytes")
    if name.upper() in ORACLE_RESERVED:
        raise GenerateError(f"{kind} name {name} is an Oracle reserved word — rename by hand")
    return name


def column_ref(schema: Schema, table: str, name: str) -> str:
    """Reserved words stay quoted lowercase: the PostgreSQL / H2 spelling Hibernate quotes with backticks."""
    if len(name.encode("utf-8")) > MAX_IDENTIFIER_BYTES:
        raise GenerateError(f"column name {table}.{name} exceeds {MAX_IDENTIFIER_BYTES} bytes")
    if name.upper() in ORACLE_RESERVED:
        label = f"{table}.{name.lower()}"
        if label not in schema.reserved_quoted:
            schema.reserved_quoted.append(label)
        return f'"{name.lower()}"'
    return name


def oracle_type(pg_type: str) -> tuple[str, bool, bool]:
    """Return (Oracle type, identity, boolean)."""
    t = squash(pg_type).upper()
    if t.startswith("BIGINT GENERATED"):
        return "NUMBER(19)", True, False
    if t == "TIMESTAMPTZ":
        raise GenerateError("TIMESTAMPTZ has no decided Oracle mapping")
    if t.startswith("TIMESTAMP"):
        return "TIMESTAMP(6)", False, False
    match = re.fullmatch(r"VARCHAR ?\( ?(\d+) ?\)", t)
    if match:
        size = int(match.group(1))
        return ("CLOB" if size > MAX_VARCHAR2_BYTES else f"VARCHAR2({size} CHAR)"), False, False
    match = re.fullmatch(r"CHAR ?\( ?(\d+) ?\)", t)
    if match:
        return f"CHAR({match.group(1)} CHAR)", False, False
    if t == "BOOLEAN":
        return "NUMBER(1)", False, True
    mapped = {
        "BIGINT": "NUMBER(19)",
        "INTEGER": "NUMBER(10)",
        "INT": "NUMBER(10)",
        "SMALLINT": "NUMBER(5)",
        "TEXT": "CLOB",
        "BYTEA": "BLOB",
        "UUID": "VARCHAR2(36 CHAR)",
        "DOUBLE PRECISION": "BINARY_DOUBLE",
        "DOUBLE": "BINARY_DOUBLE",
        "REAL": "BINARY_FLOAT",
    }.get(t)
    if mapped is None:
        raise GenerateError(f"unmapped PostgreSQL type {pg_type}")
    return mapped, False, False


EMPTY_STRING = object()


def oracle_default(label: str, column: Column, pg_default: str):
    """Oracle default expression, None, or EMPTY_STRING ('' is NULL on Oracle)."""
    value = squash(pg_default)
    upper = value.upper()
    if column.type == "TIMESTAMP(6)" and (upper == "CURRENT_TIMESTAMP" or re.fullmatch(r"NOW\s*\(\s*\)", upper)):
        # Session wall clock without zone: same clock as CURRENT_TIMESTAMP lease comparisons.
        return "LOCALTIMESTAMP"
    if column.boolean:
        if upper in ("TRUE", "FALSE"):
            return "1" if upper == "TRUE" else "0"
        raise GenerateError(f"untranslated boolean default {value} on {label}")
    if value == "''":
        return EMPTY_STRING
    if re.fullmatch(r"'(?:[^']|'')*'", value) or re.fullmatch(r"-?\d+(?:\.\d+)?", value):
        return value
    raise GenerateError(f"untranslated default '{value}' on {label}")


@dataclass
class ColumnDef:
    column: Column
    foreign_key: str | None
    inline_key: str | None  # "PRIMARY KEY" | "UNIQUE" | None
    explicit_null: bool


def parse_column(schema: Schema, table: str, text: str) -> ColumnDef:
    text = squash(text)
    name, _, rest = text.partition(" ")
    match = PG_TYPE_RE.match(rest)
    if not match:
        raise GenerateError(f"cannot parse column type in {table}: {text}")
    column_type, identity, boolean = oracle_type(match.group(1))
    column = Column(name, column_type, identity=identity, boolean=boolean)
    attrs = rest[match.end():].strip()
    label = f"{table}.{name}"

    foreign_key = None
    fk = INLINE_FK_RE.search(attrs)
    if fk:
        constraint = check_identifier("constraint", f"fk_{table}_{name}")
        foreign_key = (
            f"CONSTRAINT {constraint} FOREIGN KEY ({column_ref(schema, table, name)}) "
            f"REFERENCES {fk.group(1)}({fk.group(2)})" + on_delete(fk.group(3))
        )
        attrs = (attrs[:fk.start()] + attrs[fk.end():]).strip()

    inline_key = None
    explicit_null = False
    pg_default = None
    pos = 0
    while pos < len(attrs):
        token = ATTR_RE.match(attrs, pos)
        if not token or token.end() == pos:
            raise GenerateError(f"unparsed column attribute on {label}: '{attrs[pos:]}'")
        if token.group("notnull"):
            column.not_null = True
        elif token.group("null"):
            explicit_null = True
        elif token.group("pk"):
            inline_key = "PRIMARY KEY"
            column.not_null = True
        elif token.group("unique"):
            inline_key = "UNIQUE"
        else:
            pg_default = token.group("default")
        pos = token.end()
        while pos < len(attrs) and attrs[pos].isspace():
            pos += 1

    if pg_default is not None:
        default = oracle_default(label, column, pg_default)
        if default is EMPTY_STRING:
            if column.not_null and inline_key is None:
                schema.empty_string_relaxed.append(label)
            elif inline_key is not None:
                raise GenerateError(f"{label} is a key with DEFAULT '' — decide by hand")
            column.not_null = False
        else:
            column.default = default
    if identity and column.default is not None:
        raise GenerateError(f"identity column {label} has a DEFAULT")
    return ColumnDef(column, foreign_key, inline_key, explicit_null)


def on_delete(clause: str | None) -> str:
    """Oracle supports ON DELETE CASCADE / SET NULL; NO ACTION (and RESTRICT) is its default."""
    rule = squash(clause or "").upper()
    if rule.endswith("CASCADE") or rule.endswith("SET NULL"):
        return f" {rule}"
    return ""


def render_column(schema: Schema, table: str, parsed: ColumnDef) -> str:
    column = parsed.column
    ref = column_ref(schema, table, column.name)
    parts = [ref, column.type]
    if column.identity:
        parts.append("GENERATED BY DEFAULT AS IDENTITY")
    if column.default is not None:
        parts.append(f"DEFAULT {column.default}")
    if column.not_null and parsed.inline_key != "PRIMARY KEY" and not column.identity:
        parts.append("NOT NULL")
    elif parsed.explicit_null and not column.not_null:
        parts.append("NULL")
    if parsed.inline_key:
        parts.append(parsed.inline_key)
    if column.boolean:
        parts.append(f"CHECK ({ref} IN (0, 1))")
    return " ".join(parts)


def column_key_bytes(schema: Schema, table: str, column: str) -> int:
    column_type = schema.column(table, column).type
    match = re.fullmatch(r"(?:VARCHAR2|CHAR)\((\d+) CHAR\)", column_type)
    if match:
        return min(int(match.group(1)) * CHAR_BYTES, MAX_VARCHAR2_BYTES)
    if column_type in FIXED_KEY_BYTES:
        return FIXED_KEY_BYTES[column_type]
    raise GenerateError(f"{table}.{column} ({column_type}) cannot be an index key part on Oracle")


def key_parts(columns_text: str) -> tuple[tuple[str, str], ...]:
    parts = []
    for part in split_top_level(columns_text):
        match = re.fullmatch(r"(\w+)(?:\s+(ASC|DESC))?", squash(part), flags=re.I)
        if not match:
            raise GenerateError(f"unsupported key part '{part}'")
        order = (match.group(2) or "").upper()
        parts.append((match.group(1).lower(), "" if order == "ASC" else order))
    return tuple(parts)


def assert_key_fits(schema: Schema, table: str, name: str, parts: tuple[tuple[str, str], ...]) -> None:
    total = sum(column_key_bytes(schema, table, col) for col, _ in parts)
    if total > MAX_KEY_BYTES:
        raise GenerateError(f"key {name} on {table} is {total} bytes (> {MAX_KEY_BYTES}, ORA-01450)")


def assert_unique_not_null(schema: Schema, table: str, name: str, parts: tuple[tuple[str, str], ...]) -> None:
    """Oracle compares a partly-NULL composite unique key; PostgreSQL treats NULLs as distinct."""
    for col, _ in parts:
        if not schema.column(table, col).not_null:
            raise GenerateError(f"unique key {name} on {table} has nullable column {col} — decide by hand")


def register_index(schema: Schema, table: Table, name: str | None, parts, unique: bool) -> None:
    key = name.lower() if name else ("#pk" if unique == "pk" else "#uq:" + ",".join(c for c, _ in parts))
    if name:
        check_identifier("index", name)
        if name.lower() in schema.index_owner:
            raise GenerateError(f"duplicate index name {name}")
        schema.index_owner[name.lower()] = table.name
    table.indexes[key] = (parts, bool(unique))


def existing_index_for(table: Table, parts) -> tuple[str, bool] | None:
    for key, (existing, unique) in table.indexes.items():
        if existing == parts:
            return key, unique
    return None


def translate_constraint(schema: Schema, table: Table, item: str) -> str:
    item = oracle_literals(item)
    key = re.match(r"(?:CONSTRAINT\s+(\w+)\s+)?(PRIMARY\s+KEY|UNIQUE)\s*\((.*)\)$", item, flags=re.I)
    if key:
        name, kind, columns_text = key.group(1), squash(key.group(2)).upper(), key.group(3)
        parts = key_parts(columns_text)
        for col, order in parts:
            schema.column(table.name, col)
            if order:
                raise GenerateError(f"ordered key part in constraint on {table.name}")
        label = name or f"{table.name} {kind}"
        assert_key_fits(schema, table.name, label, parts)
        if kind == "PRIMARY KEY":
            for col, _ in parts:
                schema.column(table.name, col).not_null = True
        else:
            assert_unique_not_null(schema, table.name, label, parts)
        if name:
            check_identifier("constraint", name)
            if name.lower() in schema.constraint_names:
                raise GenerateError(f"duplicate constraint name {name}")
            schema.constraint_names.add(name.lower())
        register_index(schema, table, name, parts, "pk" if kind == "PRIMARY KEY" else True)
        rendered = ", ".join(column_ref(schema, table.name, col) for col, _ in parts)
        return (f"CONSTRAINT {name} " if name else "") + f"{kind} ({rendered})"
    fk = re.match(
        r"CONSTRAINT\s+(\w+)\s+FOREIGN\s+KEY\s*\(\s*(\w+)\s*\)\s*REFERENCES\s+(\w+)\s*\(\s*(\w+)\s*\)"
        r"(\s+ON\s+DELETE\s+(?:CASCADE|SET\s+NULL|RESTRICT|NO\s+ACTION))?$",
        squash(item),
        flags=re.I,
    )
    if fk:
        name = check_identifier("constraint", fk.group(1))
        if name.lower() in schema.constraint_names:
            raise GenerateError(f"duplicate constraint name {name}")
        schema.constraint_names.add(name.lower())
        schema.table(fk.group(3))
        return (
            f"CONSTRAINT {name} FOREIGN KEY ({column_ref(schema, table.name, fk.group(2))}) "
            f"REFERENCES {fk.group(3)}({fk.group(4)})" + on_delete(fk.group(5))
        )
    check = re.match(r"CONSTRAINT\s+(\w+)\s+CHECK\s*\((.*)\)$", squash(item), flags=re.I)
    if check:
        name = check_identifier("constraint", check.group(1))
        if name.lower() in schema.constraint_names:
            raise GenerateError(f"duplicate constraint name {name}")
        schema.constraint_names.add(name.lower())
        return f"CONSTRAINT {name} CHECK ({check.group(2)})"
    raise GenerateError(f"unsupported table constraint on {table.name}: {item}")


def translate_create_table(stmt: str, schema: Schema) -> list[str]:
    match = re.match(r"CREATE\s+TABLE\s+(IF\s+NOT\s+EXISTS\s+)?(\w+)\s*\((.*)\)\s*;$", stmt, flags=re.I | re.S)
    if not match:
        raise GenerateError(f"cannot parse CREATE TABLE: {stmt[:80]}")
    if_not_exists, name, body = match.group(1), match.group(2), match.group(3)
    if name.lower() in schema.tables:
        if if_not_exists:
            return [f"-- skipped CREATE TABLE {name} (already in greenfield baseline)"]
        raise GenerateError(f"duplicate CREATE TABLE {name}")

    table = Table(check_identifier("table", name))
    schema.tables[name.lower()] = table
    columns: list[str] = []
    constraints: list[str] = []
    foreign_keys: list[str] = []
    pending: list[str] = []

    for raw in split_top_level(body):
        item = squash(raw)
        if re.match(r"(CONSTRAINT|PRIMARY\s+KEY|UNIQUE|FOREIGN\s+KEY|CHECK)\b", item, flags=re.I):
            pending.append(item)
            continue
        parsed = parse_column(schema, name, item)
        if parsed.column.name.lower() in table.columns:
            raise GenerateError(f"duplicate column {name}.{parsed.column.name}")
        table.columns[parsed.column.name.lower()] = parsed.column
        columns.append(render_column(schema, name, parsed))
        if parsed.foreign_key:
            foreign_keys.append(parsed.foreign_key)
        if parsed.inline_key:
            parts = ((parsed.column.name.lower(), ""),)
            assert_key_fits(schema, name, f"{name}.{parsed.column.name}", parts)
            if parsed.inline_key == "UNIQUE":
                assert_unique_not_null(schema, name, f"{name}.{parsed.column.name}", parts)
            register_index(schema, table, None, parts, "pk" if parsed.inline_key == "PRIMARY KEY" else True)

    for item in pending:
        rendered = translate_constraint(schema, table, item)
        (foreign_keys if " FOREIGN KEY " in f" {rendered} " else constraints).append(rendered)

    lines = columns + constraints + foreign_keys
    return [f"CREATE TABLE {name} (\n    " + ",\n    ".join(lines) + "\n);"]


def translate_create_index(stmt: str, schema: Schema) -> list[str]:
    match = re.match(
        r"CREATE\s+(UNIQUE\s+)?INDEX\s+(IF\s+NOT\s+EXISTS\s+)?(\w+)\s+ON\s+(\w+)\s*(USING\s+\w+\s*)?\((.*)\)\s*;$",
        stmt,
        flags=re.I | re.S,
    )
    if not match:
        raise GenerateError(f"cannot parse CREATE INDEX: {stmt[:80]}")
    unique, if_not_exists, name, table_name, using, columns_text = match.groups()
    if using:
        raise GenerateError(f"index {name} uses {squash(using)} — no Oracle equivalent")
    if name.lower() in schema.index_owner:
        if if_not_exists:
            return [f"-- skipped CREATE INDEX {name} (already in greenfield baseline)"]
        raise GenerateError(f"duplicate index {name}")
    table = schema.table(table_name)
    parts = key_parts(columns_text)
    for col, _ in parts:
        schema.column(table_name, col)
    assert_key_fits(schema, table_name, name, parts)
    if unique:
        assert_unique_not_null(schema, table_name, name, parts)
    existing = existing_index_for(table, parts)
    if existing is not None:
        existing_key, existing_unique = existing
        if unique and not existing_unique:
            raise GenerateError(f"unique index {name} duplicates non-unique {existing_key} — decide by hand")
        schema.skipped_indexes.add(name.lower())
        schema.duplicate_indexes.append(f"{table.name}.{name}")
        owner = existing_key if not existing_key.startswith("#") else "its key constraint"
        return [f"-- skipped CREATE INDEX {name}: {table.name} column list already indexed by {owner} (ORA-01408)"]
    register_index(schema, table, name, parts, bool(unique))
    rendered = [column_ref(schema, table.name, col) + (f" {order}" if order else "") for col, order in parts]
    kind = "UNIQUE INDEX" if unique else "INDEX"
    return [f"CREATE {kind} {name} ON {table.name} ({', '.join(rendered)});"]


def modify(table: str, body: str) -> str:
    return f"ALTER TABLE {table} MODIFY ({body});"


def drop_indexes_with_column(schema: Schema, table: Table, column: str) -> None:
    for key, (parts, _) in list(table.indexes.items()):
        if any(col == column.lower() for col, _ in parts):
            del table.indexes[key]
            schema.index_owner.pop(key, None)


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
        parsed = parse_column(schema, name, add.group(2))
        column_name = parsed.column.name
        if column_name.lower() in table.columns:
            if add.group(1):
                return [f"-- skipped ADD COLUMN {name}.{column_name} (already in greenfield baseline)"]
            raise GenerateError(f"duplicate column {name}.{column_name}")
        if parsed.inline_key or parsed.column.identity:
            raise GenerateError(f"keyed ADD COLUMN {name}.{column_name} is not supported by the generator")
        table.columns[column_name.lower()] = parsed.column
        out = [f"ALTER TABLE {name} ADD ({render_column(schema, name, parsed)});"]
        if parsed.foreign_key:
            out.append(f"ALTER TABLE {name} ADD {parsed.foreign_key};")
        return out

    drop = re.match(r"DROP\s+COLUMN\s+(IF\s+EXISTS\s+)?(\w+)$", action, flags=re.I)
    if drop:
        column_name = drop.group(2)
        if column_name.lower() not in table.columns:
            if drop.group(1):
                return [f"-- skipped DROP COLUMN {name}.{column_name} (not in greenfield baseline)"]
            raise GenerateError(f"unknown column {name}.{column_name}")
        ref = column_ref(schema, name, column_name)
        del table.columns[column_name.lower()]
        drop_indexes_with_column(schema, table, column_name)
        return [f"ALTER TABLE {name} DROP COLUMN {ref};"]

    rename = re.match(r"RENAME\s+TO\s+(\w+)$", action, flags=re.I)
    if rename:
        new_name = check_identifier("table", rename.group(1))
        if new_name.lower() in schema.tables:
            raise GenerateError(f"rename target {new_name} exists")
        del schema.tables[name.lower()]
        table.name = new_name
        schema.tables[new_name.lower()] = table
        for index, owner in list(schema.index_owner.items()):
            if owner.lower() == name.lower():
                schema.index_owner[index] = new_name
        return [f"ALTER TABLE {name} RENAME TO {new_name};"]

    rename_column = re.match(r"RENAME\s+COLUMN\s+(\w+)\s+TO\s+(\w+)$", action, flags=re.I)
    if rename_column:
        column = schema.column(name, rename_column.group(1))
        old_ref = column_ref(schema, name, column.name)
        old = column.name.lower()
        del table.columns[old]
        column.name = rename_column.group(2)
        table.columns[column.name.lower()] = column
        for key, (parts, unique) in list(table.indexes.items()):
            table.indexes[key] = (tuple((column.name.lower() if c == old else c, o) for c, o in parts), unique)
        return [f"ALTER TABLE {name} RENAME COLUMN {old_ref} TO {column_ref(schema, name, column.name)};"]

    retype = re.match(r"ALTER\s+COLUMN\s+(\w+)\s+TYPE\s+(.+)$", action, flags=re.I)
    if retype:
        column = schema.column(name, retype.group(1))
        new_type, identity, boolean = oracle_type(retype.group(2))
        if identity or boolean != column.boolean:
            raise GenerateError(f"unsupported retype on {name}.{column.name}")
        if new_type == column.type:
            return [f"-- skipped retype {name}.{column.name} (already {new_type})"]
        ref = column_ref(schema, name, column.name)
        if (new_type in LOB_TYPES) != (column.type in LOB_TYPES):
            if any(col == column.name.lower() for parts, _ in table.indexes.values() for col, _ in parts):
                raise GenerateError(f"retype of indexed {name}.{column.name} to {new_type}")
            # ORA-22858: no in-place MODIFY between VARCHAR2 and LOB; the greenfield table is empty.
            temp = check_identifier("column", f"{column.name}__retype")
            column.type = new_type
            added = render_column(schema, name, ColumnDef(Column(
                temp, new_type, column.default, column.not_null, column.boolean
            ), None, None, False))
            return [
                f"-- {name}.{column.name} → {new_type}: Oracle cannot MODIFY across VARCHAR2 / LOB (ORA-22858)",
                f"ALTER TABLE {name} ADD ({added});",
                f"ALTER TABLE {name} DROP COLUMN {ref};",
                f"ALTER TABLE {name} RENAME COLUMN {temp} TO {ref};",
            ]
        for parts, _ in table.indexes.values():
            if any(col == column.name.lower() for col, _ in parts):
                assert_key_fits(schema, name, f"{name} index after retype", parts)
        column.type = new_type
        return [modify(name, f"{ref} {new_type}")]

    nullability = re.match(r"ALTER\s+COLUMN\s+(\w+)\s+(SET|DROP)\s+NOT\s+NULL$", action, flags=re.I)
    if nullability:
        column = schema.column(name, nullability.group(1))
        label = f"{name}.{column.name}"
        target = nullability.group(2).upper() == "SET"
        if target and label in schema.empty_string_relaxed:
            raise GenerateError(f"SET NOT NULL on {label}, which Oracle keeps nullable for ''")
        if column.not_null == target:
            return [f"-- skipped {label} {'SET' if target else 'DROP'} NOT NULL (already so; ORA-01442 / ORA-01451)"]
        column.not_null = target
        return [modify(name, f"{column_ref(schema, name, column.name)} {'NOT NULL' if target else 'NULL'}")]

    set_default = re.match(r"ALTER\s+COLUMN\s+(\w+)\s+SET\s+DEFAULT\s+(.+)$", action, flags=re.I)
    if set_default:
        column = schema.column(name, set_default.group(1))
        default = oracle_default(f"{name}.{column.name}", column, set_default.group(2))
        if default is EMPTY_STRING:
            raise GenerateError(f"SET DEFAULT '' on {name}.{column.name} — decide by hand")
        column.default = default
        return [modify(name, f"{column_ref(schema, name, column.name)} DEFAULT {default}")]

    raise GenerateError(f"unsupported ALTER TABLE {name}: {action}")


def translate_drop_index(stmt: str, schema: Schema) -> list[str]:
    match = re.match(r"DROP\s+INDEX\s+(IF\s+EXISTS\s+)?(\w+)\s*;$", stmt, flags=re.I)
    if not match:
        raise GenerateError(f"cannot parse DROP INDEX: {stmt[:80]}")
    name = match.group(2)
    if name.lower() in schema.skipped_indexes:
        schema.skipped_indexes.discard(name.lower())
        return [f"-- skipped DROP INDEX {name} (never created on Oracle: ORA-01408 duplicate)"]
    owner = schema.index_owner.pop(name.lower(), None)
    if owner is None:
        if match.group(1):
            return [f"-- skipped DROP INDEX {name} (not in greenfield baseline)"]
        raise GenerateError(f"unknown index {name}")
    schema.table(owner).indexes.pop(name.lower(), None)
    return [f"DROP INDEX {name};"]


def translate_insert(stmt: str, schema: Schema) -> list[str]:
    match = re.match(r"INSERT\s+INTO\s+(\w+)\s*\(([^)]*)\)\s*VALUES\s*(.*);$", stmt, flags=re.I | re.S)
    if not match:
        raise GenerateError(f"unsupported INSERT form: {squash(stmt)[:120]}")
    table = schema.table(match.group(1))
    columns = [col.strip() for col in match.group(2).split(",")]
    for col in columns:
        schema.column(table.name, col)
    rows = split_top_level(match.group(3).strip())
    out = []
    for row in rows:
        if not (row.startswith("(") and row.endswith(")")):
            raise GenerateError(f"unsupported VALUES row in INSERT INTO {table.name}: {row[:80]}")
        if re.search(r"[(,]\s*''\s*[,)]", row):
            raise GenerateError(f"INSERT INTO {table.name} writes '' (NULL on Oracle) — decide by hand")
        rendered_columns = ", ".join(column_ref(schema, table.name, col) for col in columns)
        values = oracle_literals(map_outside_quotes(row, lambda seg: re.sub(r"\s+", " ", seg)))
        # One row per statement: multi-row VALUES needs 23ai.
        out.append(f"INSERT INTO {table.name} ({rendered_columns}) VALUES {values};")
    schema.seeded.add(table.name.lower())
    return out


def translate_dml(stmt: str, schema: Schema) -> list[str]:
    unquoted = "".join(re.split(r"'(?:[^']|'')*'", stmt))
    if re.search(r"::|\bILIKE\b|\bRETURNING\b|\bON\s+CONFLICT\b", unquoted, flags=re.I):
        raise GenerateError(f"PostgreSQL-only DML: {squash(stmt)[:120]}")
    if re.match(r"INSERT\b", stmt, flags=re.I):
        return translate_insert(stmt, schema)
    target = re.match(r"(UPDATE|DELETE\s+FROM)\s+(\w+)\b", stmt, flags=re.I)
    if not target:
        raise GenerateError(f"unsupported DML: {squash(stmt)[:120]}")
    verb, table_name = squash(target.group(1)).upper(), target.group(2)
    table = schema.table(table_name)
    if table.name.lower() in schema.seeded:
        raise GenerateError(f"{verb} on seeded table {table.name} needs a hand translation")
    # Nothing has inserted into this table yet, so the statement cannot touch a row.
    return [f"-- skipped {verb} {table.name} (no rows on an empty schema)"]


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
        return translate_dml(stmt, schema)
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
    schema = Schema()
    sections: list[str] = []
    for path in sorted(PG.glob("V*.sql"), key=ver_key):
        if path.name in SKIP:
            sections.extend(["", f"-- skipped {path.name} (PG-only)"])
            continue
        try:
            body = translate_file(path.read_text(encoding="utf-8"), schema)
        except GenerateError as ex:
            print(f"ERROR in {path.name}: {ex}", file=sys.stderr)
            return 1
        sections.extend(["", f"-- ===== {path.name} =====", body.strip()])
    header = [
        "-- ISPF Oracle greenfield baseline (ADR-0080 B1 lab)",
        "-- Generated from postgresql V1 + V71+ (skipped V86 RLS, V89 random_uuid).",
        "-- Empty schema only. Oracle DDL auto-commits: if V1 fails, drop and recreate the ISPF schema user, then retry.",
        "-- 19c-compatible DDL (23ai accepts it): no BOOLEAN type, no IF NOT EXISTS, one row per INSERT.",
        "-- VARCHAR(n) → VARCHAR2(n CHAR) (CLOB above 4000), TEXT → CLOB, BYTEA → BLOB, UUID → VARCHAR2(36 CHAR),",
        "-- BOOLEAN → NUMBER(1) CHECK (0, 1), TIMESTAMP → TIMESTAMP(6) DEFAULT LOCALTIMESTAMP, BIGINT → NUMBER(19),",
        "-- INT → NUMBER(10), DOUBLE PRECISION → BINARY_DOUBLE, identity → GENERATED BY DEFAULT AS IDENTITY.",
        "-- Oracle stores '' as NULL: NOT NULL DEFAULT '' columns are nullable here ("
        + ", ".join(schema.empty_string_relaxed) + ").",
        "-- Reserved words stay quoted lowercase (" + ", ".join(schema.reserved_quoted) + ").",
        "-- Indexes on an already-indexed column list are skipped (ORA-01408): " + ", ".join(schema.duplicate_indexes) + ".",
        "-- UPDATE / DELETE on tables nothing has inserted into are skipped (no rows on an empty schema).",
        "-- Regenerate: python tools/oracle-baseline-generate.py",
    ]
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(header + sections)) + "\n"
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8", newline="\n")
    print(
        f"wrote {OUT} ({len(text.splitlines())} lines, {len(schema.tables)} tables; "
        f"'' relaxed={len(schema.empty_string_relaxed)}, reserved quoted={len(schema.reserved_quoted)}, "
        f"ORA-01408 skips={len(schema.duplicate_indexes)})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
