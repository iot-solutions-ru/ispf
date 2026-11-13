#!/usr/bin/env python3
"""Seed 1000 INSTANCE twins and the faceplate bound to them.

The twins are CUSTOM objects from an INSTANCE blueprint. They are not DEVICE.
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

SPEC = json.loads((Path(__file__).resolve().parent / "faceplate" / "tck-unit-faceplate.json").read_text(encoding="utf-8"))
PORT = int(os.environ.get("SOLUTION_TCK_PORT", "8081"))
PARENT = SPEC["parentPath"]
COUNT = int(SPEC["instanceCount"])


def http(method, path, body=None, token=None, query=None, timeout=60):
    url = "http://127.0.0.1:%s%s" % (PORT, path)
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = None if body is None else json.dumps(body).encode()
    headers = {}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode()
            parsed = json.loads(raw) if raw else {}
            return response.status, parsed, response.headers.get("X-Total-Count")
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode()
        try:
            parsed = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw[:400]}
        return ex.code, parsed, None


def login(username, password):
    status, body, _total = http("POST", "/api/v1/auth/login", {"username": username, "password": password})
    if status != 200 or not isinstance(body, dict) or not body.get("token"):
        raise RuntimeError("login %s failed: %s %s" % (username, status, body))
    return body["token"]


def ensure_object(token, parent, name, object_type, template_id=None):
    payload = {"parentPath": parent, "name": name, "type": object_type, "displayName": name}
    if template_id:
        payload["templateId"] = template_id
    status, body, _total = http("POST", "/api/v1/objects", payload, token)
    if status in (200, 409) or "already exists" in json.dumps(body):
        return True, status
    return False, {"status": status, "body": body}


def child_total(token):
    status, _body, total = http(
        "GET", "/api/v1/objects", token=token,
        query={"parent": PARENT, "namePattern": "unit-*", "offset": "0", "limit": "1", "lite": "true"},
    )
    if status != 200 or total is None:
        raise RuntimeError("child page %s %s" % (status, total))
    return int(total)


def ensure_instance_type(token):
    status, body, _total = http("GET", "/api/v1/instance-types/by-name/tck-unit-faceplate", token=token)
    if status == 200 and isinstance(body, dict) and body.get("id"):
        return body["id"]
    status, body, _total = http("POST", "/api/v1/instance-types", SPEC["instanceType"], token)
    if status != 200 or not isinstance(body, dict) or not body.get("id"):
        raise RuntimeError("instance type %s %s" % (status, body))
    return body["id"]


def instantiate_one(token, type_id, index):
    name = "unit-%04d" % index
    status, body, _total = http(
        "POST", "/api/v1/instance-types/%s/instantiate" % type_id,
        {"parentPath": PARENT, "instanceName": name, "parameters": {}},
        token,
    )
    return status == 200


def save_dashboard(token, path, name, layout):
    parent, leaf = path.rsplit(".", 1)
    ok, detail = ensure_object(token, parent, leaf, "DASHBOARD", "dashboard-v1")
    if not ok and name:
        return False, detail
    status, body, _total = http(
        "PUT", "/api/v1/dashboards/by-path/layout",
        {"layoutJson": json.dumps(layout)},
        token,
        {"path": path},
    )
    return status == 200, {"status": status, "body": body if status != 200 else {"ok": True}}


def main():
    token = login("admin", "admin")
    folder_parent, folder_name = PARENT.rsplit(".", 1)
    ok, detail = ensure_object(token, folder_parent, folder_name, "CUSTOM")
    if not ok:
        raise SystemExit(json.dumps({"pass": False, "stage": "folder", "detail": detail}))
    type_id = ensure_instance_type(token)
    existing = child_total(token)
    created = 0
    if existing < COUNT:
        missing = list(range(COUNT))
        workers = int(os.environ.get("SOLUTION_TCK_FACEPLATE_WORKERS", "4"))
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = [pool.submit(instantiate_one, token, type_id, index) for index in missing]
            for future in as_completed(futures):
                if future.result():
                    created += 1
    started = time.perf_counter()
    total = child_total(token)
    list_ms = round((time.perf_counter() - started) * 1000, 1)
    sample = PARENT + ".unit-0007"
    started = time.perf_counter()
    status, variables, _total = http("GET", "/api/v1/objects/by-path/variables", token=token, query={"path": sample})
    read_ms = round((time.perf_counter() - started) * 1000, 1)
    names = [item.get("name") for item in variables] if isinstance(variables, list) else []
    faceplate_ok, faceplate_detail = save_dashboard(token, SPEC["faceplatePath"], "faceplate", SPEC["faceplateLayout"])
    index_ok, index_detail = save_dashboard(token, SPEC["indexPath"], "index", SPEC["indexLayout"])
    patch_status, patch_body, _total = http(
        "PATCH", "/api/v1/objects/by-path/variables",
        {"readRoles": ["admin"]},
        token,
        {"path": sample, "name": "alarm"},
    )
    viewer_ok = False
    viewer_detail = None
    try:
        http(
            "POST", "/api/v1/security/users",
            {"username": "tck-faceplate-viewer", "displayName": "Faceplate viewer", "password": "Tck-viewer-1000", "roles": ["operator"]},
            token,
        )
        viewer = login("tck-faceplate-viewer", "Tck-viewer-1000")
        view_status, view_vars, _total = http(
            "GET", "/api/v1/objects/by-path/variables", token=viewer, query={"path": sample},
        )
        view_names = [item.get("name") for item in view_vars] if isinstance(view_vars, list) else []
        viewer_ok = view_status == 200 and "level" in view_names and "alarm" not in view_names
        if not viewer_ok:
            viewer_detail = {"status": view_status, "names": view_names}
    except Exception as ex:
        viewer_ok = False
        viewer_detail = str(ex)[:300]
    result = {
        "pass": total >= COUNT and status == 200 and "level" in names and faceplate_ok and index_ok and patch_status == 200 and viewer_ok,
        "existing": existing,
        "created": created,
        "total": total,
        "listMs": list_ms,
        "sampleReadMs": read_ms,
        "sampleVariables": names,
        "faceplate": faceplate_ok,
        "index": index_ok,
        "alarmRolesUpdated": patch_status == 200,
        "viewerHidesAlarm": viewer_ok,
        "viewerDetail": viewer_detail,
        "patchDetail": patch_body if patch_status != 200 else None,
        "faceplateDetail": faceplate_detail if not faceplate_ok else None,
        "indexDetail": index_detail if not index_ok else None,
    }
    print(json.dumps(result), flush=True)
    if not result["pass"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
