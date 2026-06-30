#!/usr/bin/env python3
"""Generate ISPF agent recipe catalog from matrix files."""

from __future__ import annotations

import argparse
import itertools
import json
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
TOOLS_DIR = Path(__file__).resolve().parent

RECIPE_MATRIX_PATH = TOOLS_DIR / "recipe-matrix.yaml"
PROJECT_MATRIX_PATH = TOOLS_DIR / "project-matrix.yaml"
DRIVER_MATRIX_PATH = TOOLS_DIR / "driver-matrix.yaml"

CATALOG_PATH = (
    ROOT
    / "packages"
    / "ispf-server"
    / "src"
    / "main"
    / "resources"
    / "agent-recipes"
    / "catalog.json"
)
DOC_PATH = ROOT / "docs" / "AGENT_RECIPES.md"


def load_yaml_like(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    try:
        import yaml  # type: ignore

        data = yaml.safe_load(text)
        if data is None:
            return {}
        if not isinstance(data, dict):
            raise RuntimeError(f"{path} must define a top-level mapping")
        return data
    except ModuleNotFoundError:
        data = json.loads(text)
        if not isinstance(data, dict):
            raise RuntimeError(f"{path} must define a top-level mapping")
        return data


def slugify(value: str) -> str:
    lowered = value.strip().lower()
    dashed = re.sub(r"[^a-z0-9]+", "-", lowered)
    return dashed.strip("-")


def humanize(value: str) -> str:
    spaced = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", value)
    spaced = spaced.replace("-", " ").replace("_", " ")
    return " ".join(part.capitalize() for part in spaced.split())


def ordered_unique(values: list[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for raw in values:
        value = str(raw).strip()
        if not value or value in seen:
            continue
        seen.add(value)
        out.append(value)
    return out


def reserve_id(base_id: str, used: set[str]) -> str:
    candidate = slugify(base_id)
    if not candidate:
        candidate = "recipe"
    if candidate not in used:
        used.add(candidate)
        return candidate
    suffix = 2
    while True:
        retry = f"{candidate}-{suffix}"
        if retry not in used:
            used.add(retry)
            return retry
        suffix += 1


def pick_rotating(pool: list[str], start: int, count: int) -> list[str]:
    if not pool or count <= 0:
        return []
    if count >= len(pool):
        return pool[:]
    return [pool[(start + i) % len(pool)] for i in range(count)]


def format_map(tokens: dict[str, str]) -> dict[str, str]:
    fmt: dict[str, str] = {}
    for key, value in tokens.items():
        fmt[key] = value
        fmt[f"{key}Label"] = humanize(value)
    return fmt


def build_entry(
    *,
    rule: dict[str, Any],
    recipe_id: str,
    category: str,
    title: str,
    one_liner: str,
    index: int,
    layers: list[str] | None = None,
    tool_chain: list[str] | None = None,
    verify: list[str] | None = None,
    tier: str | None = None,
) -> dict[str, Any]:
    layers_count = int(rule.get("layersCount", 3))
    tool_count = int(rule.get("toolChainCount", 3))
    verify_count = int(rule.get("verifyCount", 3))
    tier_cycle = [str(x) for x in rule.get("tierCycle", ["core"])] or ["core"]

    resolved_layers = layers if layers is not None else pick_rotating([str(x) for x in rule.get("layersPool", [])], index, layers_count)
    resolved_tools = tool_chain if tool_chain is not None else pick_rotating([str(x) for x in rule.get("toolChainPool", [])], index + 1, tool_count)
    resolved_verify = verify if verify is not None else pick_rotating([str(x) for x in rule.get("verifyPool", [])], index + 2, verify_count)
    resolved_tier = tier if tier is not None else tier_cycle[index % len(tier_cycle)]

    return {
        "id": recipe_id,
        "category": category,
        "title": title,
        "oneLiner": one_liner,
        "layers": resolved_layers,
        "toolChain": resolved_tools,
        "verify": resolved_verify,
        "tier": resolved_tier,
    }


def generate_generic_rule(rule: dict[str, Any], used_ids: set[str]) -> list[dict[str, Any]]:
    dims = rule.get("dimensions", [])
    if not dims:
        raise RuntimeError(f"Rule {rule.get('slug')} is missing dimensions")
    dim_names = [str(item["name"]) for item in dims]
    dim_values = [[str(v) for v in item.get("values", [])] for item in dims]
    if any(not values for values in dim_values):
        raise RuntimeError(f"Rule {rule.get('slug')} has empty dimension values")

    combos = list(itertools.product(*dim_values))
    target = int(rule["targetCount"])
    slug = str(rule["slug"])
    out: list[dict[str, Any]] = []
    for index in range(target):
        combo = combos[index % len(combos)]
        tokens = {dim_names[i]: combo[i] for i in range(len(dim_names))}
        if index >= len(combos):
            tokens["variant"] = f"v{index // len(combos) + 1}"
        fmt = format_map(tokens)
        id_parts = [tokens[name] for name in dim_names]
        if "variant" in tokens:
            id_parts.append(tokens["variant"])
        recipe_id = reserve_id(f"{slug}-{'-'.join(id_parts)}", used_ids)
        out.append(
            build_entry(
                rule=rule,
                recipe_id=recipe_id,
                category=slug,
                title=str(rule["titleTemplate"]).format(**fmt),
                one_liner=str(rule["oneLinerTemplate"]).format(**fmt),
                index=index,
            )
        )
    return out


def parse_widget_types(widget_cfg: dict[str, Any]) -> list[str]:
    source_path = ROOT / str(widget_cfg["path"])
    text = source_path.read_text(encoding="utf-8")
    match = re.search(
        r"export const WIDGET_TYPES:[\s\S]*?=\s*\(\s*\[(?P<items>[\s\S]*?)\]\s*as WidgetType\[\]\s*\)\.map",
        text,
    )
    if not match:
        raise RuntimeError(f"Could not parse WIDGET_TYPES from {source_path}")
    block = match.group("items")
    widget_types = ordered_unique(re.findall(r'"([^"]+)"', block))
    exclude = {str(x) for x in widget_cfg.get("excludeTypes", [])}
    filtered = [item for item in widget_types if item not in exclude]

    expected = widget_cfg.get("expectedCount")
    if expected is not None and len(filtered) != int(expected):
        raise RuntimeError(
            f"Widget type count mismatch: expected {expected}, got {len(filtered)} "
            f"(source={source_path})"
        )
    return filtered


def generate_widget_rule(
    rule: dict[str, Any],
    widget_cfg: dict[str, Any],
    widget_types: list[str],
    used_ids: set[str],
) -> list[dict[str, Any]]:
    binding_modes = [str(x) for x in widget_cfg.get("bindingModes", [])]
    if not binding_modes:
        raise RuntimeError("widgetSource.bindingModes is empty")
    combos = list(itertools.product(widget_types, binding_modes))
    target = int(rule["targetCount"])
    if len(combos) < target:
        raise RuntimeError(
            f"Widget combinations ({len(combos)}) are less than targetCount ({target})"
        )

    out: list[dict[str, Any]] = []
    for index in range(target):
        widget_type, binding_mode = combos[index]
        tokens = {"widgetType": widget_type, "bindingMode": binding_mode}
        fmt = format_map(tokens)
        recipe_id = reserve_id(f"widget-{widget_type}-{binding_mode}", used_ids)
        out.append(
            build_entry(
                rule=rule,
                recipe_id=recipe_id,
                category="widget",
                title=str(rule["titleTemplate"]).format(**fmt),
                one_liner=str(rule["oneLinerTemplate"]).format(**fmt),
                index=index,
            )
        )
    return out


def collect_driver_ids(payload: Any, id_fields: set[str]) -> list[str]:
    found: list[str] = []
    if isinstance(payload, dict):
        for key in id_fields:
            value = payload.get(key)
            if isinstance(value, str) and value.strip():
                found.append(slugify(value))
        for value in payload.values():
            found.extend(collect_driver_ids(value, id_fields))
    elif isinstance(payload, list):
        for item in payload:
            found.extend(collect_driver_ids(item, id_fields))
    return found


def scan_driver_ids(driver_cfg: dict[str, Any]) -> list[str]:
    id_fields = {str(x) for x in driver_cfg.get("driverIdFields", [])}
    if not id_fields:
        id_fields = {"driverId", "id"}
    file_pattern = str(driver_cfg.get("filePattern", "**/driver-pack.json"))
    scan_roots = [ROOT / str(x) for x in driver_cfg.get("scanRoots", [])]
    driver_ids: list[str] = []

    for scan_root in scan_roots:
        if not scan_root.exists():
            continue
        for pack_json in sorted(scan_root.glob(file_pattern)):
            try:
                payload = json.loads(pack_json.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                continue
            driver_ids.extend(collect_driver_ids(payload, id_fields))

    unique = ordered_unique(driver_ids)
    if unique:
        return unique
    fallback = [slugify(str(x)) for x in driver_cfg.get("fallbackDriverIds", [])]
    return ordered_unique(fallback)


def generate_driver_rule(
    rule: dict[str, Any],
    driver_ids: list[str],
    used_ids: set[str],
) -> list[dict[str, Any]]:
    if not driver_ids:
        raise RuntimeError("No driver IDs were found and fallbackDriverIds is empty")
    cadences = [str(x) for x in rule.get("cadences", [])]
    operations = [str(x) for x in rule.get("operations", [])]
    scenarios = [str(x) for x in rule.get("scenarios", [])]
    if not cadences or not operations or not scenarios:
        raise RuntimeError("Driver rule must define cadences, operations, and scenarios")

    combos = list(itertools.product(cadences, operations, scenarios))
    target = int(rule["targetCount"])
    if len(combos) < target:
        raise RuntimeError(
            f"Driver combinations ({len(combos)}) are less than targetCount ({target})"
        )

    out: list[dict[str, Any]] = []
    for index in range(target):
        cadence, operation, scenario = combos[index]
        driver_id = driver_ids[index % len(driver_ids)]
        tokens = {
            "driverId": driver_id,
            "operation": operation,
            "scenario": scenario,
            "cadence": cadence,
        }
        fmt = format_map(tokens)
        recipe_id = reserve_id(
            f"driver-{driver_id}-{operation}-{scenario}-{cadence}",
            used_ids,
        )
        out.append(
            build_entry(
                rule=rule,
                recipe_id=recipe_id,
                category="driver",
                title=str(rule["titleTemplate"]).format(**fmt),
                one_liner=str(rule["oneLinerTemplate"]).format(**fmt),
                index=index,
            )
        )
    return out


def generate_atomic_recipes(
    recipe_matrix: dict[str, Any], driver_matrix: dict[str, Any]
) -> tuple[list[dict[str, Any]], list[str], list[str]]:
    widget_cfg = dict(recipe_matrix["widgetSource"])
    widget_types = parse_widget_types(widget_cfg)
    driver_ids = scan_driver_ids(driver_matrix)

    rules = recipe_matrix.get("atomicRules", [])
    used_ids: set[str] = set()
    atomic: list[dict[str, Any]] = []
    for rule in rules:
        mode = str(rule.get("mode", "generic"))
        if mode == "generic":
            atomic.extend(generate_generic_rule(rule, used_ids))
        elif mode == "widget-binding":
            atomic.extend(generate_widget_rule(rule, widget_cfg, widget_types, used_ids))
        elif mode == "driver-scan":
            atomic.extend(generate_driver_rule(rule, driver_ids, used_ids))
        else:
            raise RuntimeError(f"Unknown atomic rule mode: {mode}")
    return atomic, widget_types, driver_ids


def generate_project_recipes(
    project_matrix: dict[str, Any], used_ids: set[str]
) -> list[dict[str, Any]]:
    industries = [str(x) for x in project_matrix.get("industries", [])]
    archetypes = [dict(x) for x in project_matrix.get("archetypes", [])]
    defaults = dict(project_matrix.get("defaults", {}))

    if len(industries) != 50:
        raise RuntimeError(f"Expected 50 industries, got {len(industries)}")
    if len(archetypes) != 10:
        raise RuntimeError(f"Expected 10 archetypes, got {len(archetypes)}")

    out: list[dict[str, Any]] = []
    index = 0
    for industry, archetype in itertools.product(industries, archetypes):
        archetype_slug = str(archetype["slug"])
        recipe_id = reserve_id(f"project-{industry}-{archetype_slug}", used_ids)
        layers = ordered_unique(
            [str(x) for x in defaults.get("layers", [])]
            + [str(x) for x in archetype.get("layers", [])]
        )
        tool_chain = ordered_unique(
            [str(x) for x in defaults.get("toolChain", [])]
            + [str(x) for x in archetype.get("toolChain", [])]
        )
        verify = ordered_unique(
            [str(x) for x in defaults.get("verify", [])]
            + [str(x) for x in archetype.get("verify", [])]
        )
        out.append(
            {
                "id": recipe_id,
                "category": "project",
                "title": f"Project recipe: {humanize(industry)} / {humanize(archetype_slug)}",
                "oneLiner": (
                    f"Blueprint for {humanize(industry)} using {humanize(archetype_slug)} "
                    "archetype."
                ),
                "layers": layers,
                "toolChain": tool_chain,
                "verify": verify,
                "tier": "project",
            }
        )
        index += 1
    return out


def validate_catalog(catalog: list[dict[str, Any]], targets: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    target_catalog = int(targets.get("catalog", 1410))
    target_atomic = int(targets.get("atomic", 910))
    target_project = int(targets.get("project", 500))

    if len(catalog) != target_catalog:
        errors.append(f"Catalog size mismatch: expected {target_catalog}, got {len(catalog)}")

    ids = [str(item.get("id", "")) for item in catalog]
    duplicate_ids = [recipe_id for recipe_id, count in Counter(ids).items() if count > 1]
    if duplicate_ids:
        errors.append(f"Duplicate IDs found: {', '.join(sorted(duplicate_ids)[:10])}")

    project_count = sum(1 for item in catalog if item.get("category") == "project")
    atomic_count = len(catalog) - project_count
    if project_count != target_project:
        errors.append(f"Project recipe count mismatch: expected {target_project}, got {project_count}")
    if atomic_count != target_atomic:
        errors.append(f"Atomic recipe count mismatch: expected {target_atomic}, got {atomic_count}")

    required = {"id", "category", "title", "oneLiner", "layers", "toolChain", "verify", "tier"}
    for idx, item in enumerate(catalog):
        missing = required - set(item.keys())
        if missing:
            errors.append(f"Entry #{idx} is missing fields: {', '.join(sorted(missing))}")
            break

    return errors


def write_catalog(catalog: list[dict[str, Any]]) -> None:
    CATALOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    CATALOG_PATH.write_text(
        json.dumps(catalog, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def render_docs(catalog: list[dict[str, Any]], widget_types: list[str], driver_ids: list[str]) -> str:
    counts = Counter(str(item["category"]) for item in catalog)
    order = [
        "model",
        "logic",
        "auto",
        "widget",
        "scada",
        "dash",
        "report",
        "fix",
        "life",
        "otype",
        "driver",
        "int",
        "project",
    ]
    generated_at = datetime.now(timezone.utc).isoformat()
    lines: list[str] = [
        "# AGENT_RECIPES",
        "",
        "Generated index for platform agent recipe catalog.",
        "",
        f"- Generated at: `{generated_at}`",
        f"- Total recipes: `{len(catalog)}`",
        f"- Atomic recipes: `{len(catalog) - counts.get('project', 0)}`",
        f"- Project recipes: `{counts.get('project', 0)}`",
        f"- Widget types used: `{len(widget_types)}`",
        f"- Driver ids discovered: `{len(driver_ids)}`",
        "",
        "## Category counts",
        "",
    ]
    for category in order:
        lines.append(f"- `{category}`: `{counts.get(category, 0)}`")
    lines.extend(
        [
            "",
            "## Project matrix",
            "",
            "- Industries: `50`",
            "- Archetypes: `10`",
            "- ID format: `project-<industry>-<archetype>`",
            "",
            "## Validation",
            "",
            "- Enforced unique `id` values",
            "- Enforced total count `1410`",
            "- Enforced split `910` atomic + `500` project",
        ]
    )
    return "\n".join(lines) + "\n"


def write_docs(content: str) -> None:
    DOC_PATH.parent.mkdir(parents=True, exist_ok=True)
    DOC_PATH.write_text(content, encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate ISPF agent recipe catalog")
    parser.add_argument(
        "--check",
        action="store_true",
        help="Exit non-zero when count/duplication checks fail",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    recipe_matrix = load_yaml_like(RECIPE_MATRIX_PATH)
    project_matrix = load_yaml_like(PROJECT_MATRIX_PATH)
    driver_matrix = load_yaml_like(DRIVER_MATRIX_PATH)
    targets = dict(recipe_matrix.get("targets", {}))

    atomic, widget_types, driver_ids = generate_atomic_recipes(recipe_matrix, driver_matrix)
    used_ids = {str(item["id"]) for item in atomic}
    projects = generate_project_recipes(project_matrix, used_ids)
    catalog = atomic + projects

    errors = validate_catalog(catalog, targets)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    write_catalog(catalog)
    write_docs(render_docs(catalog, widget_types, driver_ids))

    print(f"Wrote {CATALOG_PATH}")
    print(f"Wrote {DOC_PATH}")
    print(
        f"recipes={len(catalog)} atomic={len(atomic)} project={len(projects)} "
        f"widgets={len(widget_types)} drivers={len(driver_ids)}"
    )
    if args.check:
        print("CHECK OK: count=1410, duplicates=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
