#!/usr/bin/env bash
# nginx (www-data) must traverse asset dirs; cp -a from zip can leave mode 0700.

fix_web_console_permissions() {
  local root="${1:-/opt/ispf/web-console}"
  chmod -R a+rX "$root"
  find "$root" -type d -exec chmod 755 {} +
}

verify_web_console_permissions() {
  local root="${1:-/opt/ispf/web-console}"
  local assets="${root}/assets"
  if [[ ! -d "$assets" ]]; then
    echo "ERROR: missing web-console assets dir: $assets" >&2
    return 1
  fi
  if su -s /bin/sh www-data -c "test -x '$assets'" 2>/dev/null; then
    return 0
  fi
  echo "ERROR: www-data cannot traverse $assets (run fix_web_console_permissions)" >&2
  return 1
}
