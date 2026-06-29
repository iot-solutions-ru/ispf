# Build standalone landing (embed screenshots)
Set-Location $PSScriptRoot
python build.py
if ($LASTEXITCODE -eq 0) {
  Write-Host "Open: dist/landing.html"
}
