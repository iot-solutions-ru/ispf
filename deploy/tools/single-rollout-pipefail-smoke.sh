#!/usr/bin/env bash
# Smoke-test: empty grep under pipefail must not abort (vps-single-rollout regression).
set -euo pipefail

remove_matching_containers() {
  local pattern="$1"
  shift
  local names
  # Simulate `docker ps -a --format '{{.Names}}' | grep -E …`
  names="$(printf '%s\n' "$@" | grep -E "$pattern" || true)"
  if [[ -z "$names" ]]; then
    echo "empty-ok"
    return 0
  fi
  local count=0
  while IFS= read -r c; do
    [[ -n "$c" ]] || continue
    count=$((count + 1))
  done <<< "$names"
  echo "matched=$count"
}

out="$(remove_matching_containers 'never-match-this' 'alpha' 'beta')"
[[ "$out" == "empty-ok" ]] || { echo "FAIL empty: $out"; exit 1; }

out="$(remove_matching_containers 'alp' 'alpha' 'beta' 'alpine')"
[[ "$out" == "matched=2" ]] || { echo "FAIL match: $out"; exit 1; }

# Prove the old bug: bare pipefail+grep with no match exits non-zero.
if ( set -euo pipefail; printf 'alpha\n' | grep -E 'never-match-this' >/dev/null ); then
  echo "FAIL: expected bare grep miss to be non-zero"
  exit 1
fi

echo "vps-single-rollout pipefail smoke OK"
