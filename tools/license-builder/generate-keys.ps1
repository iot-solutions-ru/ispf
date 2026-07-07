# Generate RSA key pair for ISPF commercial bundle licensing.
param(
    [string]$OutDir = "."
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
python (Join-Path $PSScriptRoot "generate-keys.py") --out-dir $OutDir
