import paramiko
import sys
import time

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect("185.246.66.158", username="root", password=sys.argv[1], timeout=20)


def run(cmd: str) -> str:
    _, stdout, stderr = client.exec_command(cmd)
    return (stdout.read() + stderr.read()).decode()


print("Force stopping ISPF...")
run("pkill -9 -f 'ispf-server.jar' || true")
time.sleep(3)
print("remaining:", run("pgrep -af ispf-server.jar || echo none").strip())

start_cmd = (
    "bash -lc 'cd /opt/ispf && "
    "nohup /usr/bin/java -jar /opt/ispf/ispf-server.jar --spring.profiles.active=local "
    ">> /opt/ispf/data/ispf-server.log 2>&1 & disown'"
)
transport = client.get_transport()
channel = transport.open_session()
channel.exec_command(start_cmd)
time.sleep(2)
channel.close()

for attempt in range(30):
    time.sleep(4)
    code = run("curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/info").strip()
    print(f"attempt {attempt + 1}: HTTP {code}")
    if code == "200":
        print(run("curl -s http://127.0.0.1:8080/api/v1/info | head -c 120"))
        break
else:
    print("startup failed, log tail:")
    print(run("tail -50 /opt/ispf/data/ispf-server.log")[-3000:])

print("processes:", run("pgrep -af ispf-server.jar || echo none").strip())
client.close()
