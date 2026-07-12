#!/usr/bin/env python3
"""Backward-compatible wrapper — use deploy/tools/anonymize-repo.py for full repo pass."""
from __future__ import annotations

import runpy
import sys
from pathlib import Path

if __name__ == "__main__":
    repo_script = Path(__file__).with_name("anonymize-repo.py")
    sys.argv = [str(repo_script), "--markdown-only", *sys.argv[1:]]
    runpy.run_path(str(repo_script), run_name="__main__")
