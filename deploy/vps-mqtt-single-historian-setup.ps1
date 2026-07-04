$ErrorActionPreference = "Stop"
$Remote = "root@ispf.iot-solutions.ru"
$RemoteDir = "/opt/ispf/loadtest"
$Deploy = Join-Path $PSScriptRoot ""

ssh -o BatchMode=yes $Remote "mkdir -p $RemoteDir /opt/ispf/bin"
scp -o BatchMode=yes `
  "$Deploy\vps-factory-reset.sh" `
  "$Deploy\vps-mqtt-single-historian-setup.sh" `
  "$Deploy\setup-single-mqtt-historian.py" `
  "$Deploy\mqtt_loadtest_lib.py" `
  "$Deploy\loadtest_cleanup_lib.py" `
  "$Deploy\run-single-mqtt-setup-remote.sh" `
  "${Remote}:${RemoteDir}/"
ssh -o BatchMode=yes $Remote "cp $RemoteDir/vps-factory-reset.sh /opt/ispf/bin/vps-factory-reset.sh && sed -i 's/\r$//' $RemoteDir/*.sh /opt/ispf/bin/vps-factory-reset.sh && chmod +x $RemoteDir/*.sh /opt/ispf/bin/vps-factory-reset.sh && bash $RemoteDir/vps-mqtt-single-historian-setup.sh"
