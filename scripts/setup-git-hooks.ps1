# Install repo git hooks (blocks push of cursor/* branches).
git config core.hooksPath .githooks
Write-Host "Installed .githooks (pre-push blocks cursor/*)."
