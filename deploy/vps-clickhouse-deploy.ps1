# Deploy ClickHouse on VPS and switch ISPF event journal to clickhouse backend.
param(
    [string]$RemoteHost = "root@ispf.iot-solutions.ru",
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

Write-Host "==> Upload ClickHouse compose + setup script to $RemoteHost"
ssh -o BatchMode=yes $RemoteHost "mkdir -p /opt/ispf"
scp -o BatchMode=yes `
    "$RepoRoot\deploy\vps-clickhouse-setup.sh" `
    "$RepoRoot\deploy\vps-clickhouse-verify.sh" `
    "$RepoRoot\deploy\vps-event-journal-jdbc.sh" `
    "${RemoteHost}:/opt/ispf/"

Write-Host "==> Run vps-clickhouse-setup.sh on VPS"
ssh -o BatchMode=yes $RemoteHost @"
chmod +x /opt/ispf/vps-clickhouse-setup.sh /opt/ispf/vps-clickhouse-verify.sh /opt/ispf/vps-event-journal-jdbc.sh
sed -i 's/\r$//' /opt/ispf/vps-clickhouse-setup.sh /opt/ispf/vps-clickhouse-verify.sh /opt/ispf/vps-event-journal-jdbc.sh
bash /opt/ispf/vps-clickhouse-setup.sh
bash /opt/ispf/vps-clickhouse-verify.sh
"@

Write-Host ""
Write-Host "Done. Revert to JDBC: ssh $RemoteHost 'bash /opt/ispf/vps-event-journal-jdbc.sh'"
