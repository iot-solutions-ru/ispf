#!/usr/bin/env python3
import json
import urllib.request
import urllib.error

def call(method, path, token=None, body=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"} if data else {}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request("http://127.0.0.1:8081" + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return response.status, response.read().decode()[:500]
    except urllib.error.HTTPError as ex:
        return ex.code, ex.read().decode()[:500]

status, body = call("POST", "/api/v1/auth/login", body={"username": "admin", "password": "admin"})
print("login", status, body)
token = json.loads(body).get("token")
print("me", call("GET", "/api/v1/auth/me", token))
print("enroll", call("PUT", "/api/v1/auth/signature-key", token, {"publicKey": "AAAA"}))
