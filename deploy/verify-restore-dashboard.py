import requests

base = "https://ispf.iot-solutions.ru"
s = requests.Session()
t = s.post(
    f"{base}/api/v1/auth/login",
    json={"username": "admin", "password": "admin"},
    timeout=30,
).json()["token"]
s.headers["Authorization"] = f"Bearer {t}"

sess = s.post(f"{base}/api/v1/ai/agent/sessions", json={"rootPath": "root"}, timeout=30).json()
sid = sess["sessionId"]

r = s.post(
    f"{base}/api/v1/ai/agent/sessions/{sid}/messages",
    json={"message": "Восстанови layout дашборда snmp-host-monitoring из шаблона"},
    timeout=180,
)
print("status", r.status_code)
if r.ok:
    body = r.json()
    print("agent status", body.get("status"))
    print("summary", (body.get("summary") or "")[:300])
    for step in body.get("steps", []):
        print("-", step.get("label"))
else:
    print(r.text[:500])
