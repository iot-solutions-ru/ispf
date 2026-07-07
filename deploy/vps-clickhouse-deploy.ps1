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
    "$RepoRoot\deploy\vps-variable-history-clickhouse.sh" `
    "$RepoRoot\deploy\vps-variable-history-dual-write.sh" `
    "${RemoteHost}:/opt/ispf/"

Write-Host "==> Run vps-clickhouse-setup.sh on VPS"
ssh -o BatchMode=yes $RemoteHost @"
chmod +x /opt/ispf/vps-clickhouse-setup.sh /opt/ispf/vps-clickhouse-verify.sh /opt/ispf/vps-event-journal-jdbc.sh /opt/ispf/vps-variable-history-clickhouse.sh /opt/ispf/vps-variable-history-dual-write.sh
sed -i 's/\r$//' /opt/ispf/vps-clickhouse-setup.sh /opt/ispf/vps-clickhouse-verify.sh /opt/ispf/vps-event-journal-jdbc.sh /opt/ispf/vps-variable-history-clickhouse.sh /opt/ispf/vps-variable-history-dual-write.sh
bash /opt/ispf/vps-clickhouse-setup.sh
bash /opt/ispf/vps-clickhouse-verify.sh
"@

Write-Host "Done. Historian dual-write: ssh $RemoteHost 'bash /opt/ispf/vps-variable-history-dual-write.sh'"
Write-Host "Historian cutover: ssh $RemoteHost 'bash /opt/ispf/vps-variable-history-clickhouse.sh'"
Write-Host "Revert event journal JDBC: ssh $RemoteHost 'bash /opt/ispf/vps-event-journal-jdbc.sh'"
