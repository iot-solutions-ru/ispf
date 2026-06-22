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

r1 = s.post(
    f"{base}/api/v1/ai/agent/sessions/{sid}/messages",
    json={
        "message": "Создай SNMP-устройство localhost, выведи метрики и создай дашборд snmp-host-monitoring"
    },
    timeout=300,
)
print("turn1", r1.status_code, r1.json().get("status") if r1.ok else r1.text[:200])

r2 = s.post(
    f"{base}/api/v1/ai/agent/sessions/{sid}/messages",
    json={"message": "Добавь еще больше метрик из if-mib и выведи их на дашборд!"},
    timeout=300,
)
print("turn2", r2.status_code)
if r2.ok:
    body = r2.json()
    print("status", body.get("status"))
    print("steps", len(body.get("steps", [])))
    for step in body.get("steps", []):
        print("-", step.get("label", step.get("tool")))
else:
    print(body if "body" in dir() else r2.text[:400])
