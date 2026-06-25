# Deploy Mosquitto + MQTT load test scripts to VPS.
param(
    [string]$RemoteHost = "root@ispf.iot-solutions.ru",
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

Write-Host "==> Installing Mosquitto on $RemoteHost"
ssh -o BatchMode=yes $RemoteHost "mkdir -p /opt/ispf/loadtest"
scp -o BatchMode=yes "$RepoRoot\deploy\vps-mqtt-broker.sh" "${RemoteHost}:/tmp/vps-mqtt-broker.sh"
scp -o BatchMode=yes `
    "$RepoRoot\deploy\mqtt-loadtest-publisher.py" `
    "$RepoRoot\deploy\mqtt-loadtest-tap.py" `
    "$RepoRoot\deploy\mqtt_loadtest_lib.py" `
    "$RepoRoot\deploy\mqtt-ingress-load-test.py" `
    "${RemoteHost}:/opt/ispf/loadtest/"
ssh -o BatchMode=yes $RemoteHost "bash /tmp/vps-mqtt-broker.sh"

Write-Host "==> Verify broker"
ssh -o BatchMode=yes $RemoteHost "docker ps --filter name=ispf-mqtt-loadtest"

Write-Host ""
Write-Host "Done. Run ingress test from workstation:"
Write-Host "  pip install paho-mqtt requests"
Write-Host "  python deploy/mqtt-ingress-load-test.py --devices 10 --messages-per-second 500 --publish-via-ssh $RemoteHost"
