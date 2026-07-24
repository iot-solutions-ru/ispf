#!/usr/bin/env python3
"""Flatten dashboard widget settingsJson into top-level fields for erp-mes marketplace bundles."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "examples" / "marketplace-catalog"

DEFAULT_APPS = ("erp-mes-core", "erp-mes-printing", "erp-mes-pharma")
NEW_VERSION = "1.0.1"
CHANGELOG = (
    "1.0.1 - Flatten dashboard widget settingsJson to top-level fields "
    "(reportPath, objectPath, functionName) so operator UI works without "
    "web-console settingsJson expansion."
)

PREFER_FROM_SETTINGS = {
    "reportPath",
    "objectPath",
    "functionName",
    "buttonLabel",
    "fieldsJson",
    "pageSize",
    "htmlJson",
    "html",
    "emptyMessage",
    "refreshIntervalMs",
    "selectionVariablePath",
    "bindSelectionTo",
}


def flatten_widget(w: dict) -> dict:
    out = dict(w)
    if not out.get("id") and out.get("key"):
        out["id"] = out["key"]
    sj = out.pop("settingsJson", None)
    settings: dict = {}
    if isinstance(sj, str) and sj.strip():
        try:
            parsed = json.loads(sj)
            if isinstance(parsed, dict):
                settings = parsed
        except json.JSONDecodeError:
            settings = {}
    elif isinstance(sj, dict):
        settings = sj
    for k, v in settings.items():
        if k not in out or out[k] in (None, "", {}) or k in PREFER_FROM_SETTINGS:
            out[k] = v
    if isinstance(out.get("htmlJson"), str):
        try:
            hj = json.loads(out["htmlJson"])
            if isinstance(hj, dict) and "html" in hj and not out.get("html"):
                out["html"] = hj["html"]
        except json.JSONDecodeError:
            pass
    out.pop("settingsJson", None)
    return out


def flatten_layout(layout_raw):
    if isinstance(layout_raw, str):
        layout = json.loads(layout_raw)
        as_string = True
    else:
        layout = layout_raw
        as_string = False
    if not isinstance(layout, dict):
        return layout_raw, 0
    widgets = layout.get("widgets")
    if not isinstance(widgets, list):
        return layout_raw, 0
    n = sum(1 for w in widgets if isinstance(w, dict) and "settingsJson" in w)
    layout["widgets"] = [flatten_widget(w) if isinstance(w, dict) else w for w in widgets]
    if as_string:
        return json.dumps(layout, ensure_ascii=False), n
    return layout, n


def process_bundle(path: Path) -> tuple[int, int]:
    data = json.loads(path.read_text(encoding="utf-8"))
    flattened = 0
    dashboards = 0
    for dash in data.get("dashboards") or []:
        if not isinstance(dash, dict) or "layoutJson" not in dash:
            continue
        dashboards += 1
        new_layout, n = flatten_layout(dash["layoutJson"])
        dash["layoutJson"] = new_layout
        flattened += n
    data["version"] = NEW_VERSION
    meta = data.get("metadata")
    if not isinstance(meta, dict):
        meta = {}
        data["metadata"] = meta
    meta["changelog"] = CHANGELOG
    meta["bundleFix"] = "flatten-settingsJson-1.0.1"
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return dashboards, flattened


def process_manifest(path: Path) -> None:
    listing = json.loads(path.read_text(encoding="utf-8"))
    listing["latestVersion"] = NEW_VERSION
    listing["changelog"] = CHANGELOG
    meta = listing.get("metadata")
    if not isinstance(meta, dict):
        meta = {}
        listing["metadata"] = meta
    entries = meta.get("changelog")
    if not isinstance(entries, list):
        entries = []
    entries = [e for e in entries if not (isinstance(e, dict) and e.get("version") == NEW_VERSION)]
    entries.insert(0, {"version": NEW_VERSION, "notes": CHANGELOG})
    meta["changelog"] = entries
    path.write_text(json.dumps(listing, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def verify(path: Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    s = json.dumps(data)
    flat = 0
    nested = s.count("settingsJson")
    for dash in data.get("dashboards") or []:
        layout = dash.get("layoutJson")
        if isinstance(layout, str):
            layout = json.loads(layout)
        for w in (layout or {}).get("widgets") or []:
            if isinstance(w, dict) and w.get("reportPath"):
                flat += 1
    print(f"  verify {path.parent.name}: version={data.get('version')} settingsJson={nested} flat_reportPath={flat}")


def main(argv: list[str] | None = None) -> None:
    import sys

    apps = tuple(argv if argv is not None else sys.argv[1:]) or DEFAULT_APPS
    for app in apps:
        bundle = CATALOG / app / "bundle.json"
        manifest = CATALOG / app / "listing.manifest.json"
        if not bundle.is_file() or not manifest.is_file():
            raise SystemExit(f"missing catalog files for {app}")
        d, n = process_bundle(bundle)
        process_manifest(manifest)
        print(f"{app}: dashboards={d} widgets_flattened={n}")
        verify(bundle)


if __name__ == "__main__":
    main()
