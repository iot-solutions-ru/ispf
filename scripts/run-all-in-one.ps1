# Compatibility wrapper — prefer scripts/start.bat (double-click) or start.sh
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$bat = Join-Path $scriptDir "start.bat"
if (-not (Test-Path $bat)) {
    Write-Error "Missing start.bat next to this script"
    exit 1
}
$jar = if ($args.Count -ge 1) { $args[0] } else { "" }
if ($jar) {
    & cmd /c "`"$bat`" `"$jar`""
} else {
    & cmd /c "`"$bat`""
}
exit $LASTEXITCODE
