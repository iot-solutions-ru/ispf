#!/usr/bin/env bash
# Ramp event-journal load until saturation or failure (lab host).
set -euo pipefail

LAB_ROOT="${ISPF_LAB_ROOT:-$HOME/ispf}"
COMPOSE_FILE="$LAB_ROOT/lab-test-host-compose.yml"
ENV_FILE="$LAB_ROOT/lab-stress.env"
HTTP_PORT="${ISPF_LAB_HTTP_PORT:-8000}"
REPORT="$LAB_ROOT/loadtest/stress-sweep-report.txt"
SUMMARY="$LAB_ROOT/loadtest/stress-sweep-summary.tsv"

# Space-separated rates per device to try (aggregate = DEVICES * rate).
SWEEP_RATES="${SWEEP_RATES:-8000 12000 16000 20000 25000 30000 40000 50000}"
DEVICES="${SWEEP_DEVICES:-16}"
CALLBACK_THREADS="${SWEEP_CALLBACK_THREADS:-32}"
WARMUP="${SWEEP_WARMUP:-15}"
PHASE="${SWEEP_PHASE:-45}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

cd "$LAB_ROOT"

api_ok() {
  curl -sf "http://127.0.0.1:${HTTP_PORT}/api/v1/info" >/dev/null 2>&1
}

login_ok() {
  curl -sf -X POST "http://127.0.0.1:${HTTP_PORT}/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"admin","password":"admin"}' >/dev/null 2>&1
}

peak_cpu() {
  local name="$1"
  docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' \
    $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q 2>/dev/null) 2>/dev/null \
    | awk -v n="$name" '$1 ~ n {gsub(/%/,"",$2); print $2; exit}'
}

: > "$REPORT"
echo -e "rate_per_device\taggregate_target\tevents_per_sec\tefficiency_pct\tispf_cpu_pct\tscylla_cpu_pct\tstatus" > "$SUMMARY"

echo "=== STRESS SWEEP $(date -Is) ===" | tee -a "$REPORT"
echo "Devices: $DEVICES  rates: $SWEEP_RATES  phase: ${PHASE}s" | tee -a "$REPORT"
echo "Scylla smp=${SCYLLA_SMP:-?} memory=${SCYLLA_MEMORY:-?}" | tee -a "$REPORT"

prev_eps="0"
prev_target="0"
break_reason=""

for rate in $SWEEP_RATES; do
  aggregate=$((DEVICES * rate))
  step_log="$LAB_ROOT/loadtest/stress-sweep-${rate}.log"

  echo "" | tee -a "$REPORT"
  echo "=== STEP rate=${rate}/device aggregate=${aggregate} msg/s $(date -Is) ===" | tee -a "$REPORT"

  if ! api_ok || ! login_ok; then
    break_reason="API/login unavailable before step rate=${rate}"
    echo "BREAK: $break_reason" | tee -a "$REPORT"
    echo -e "${rate}\t${aggregate}\t0\t0\t0\t0\tAPI_DOWN" >> "$SUMMARY"
    break
  fi

  (
    while true; do
      echo "--- rate=${rate} $(date -Is) ---" >> "$LAB_ROOT/loadtest/stress-sweep-stats.log"
      docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' \
        $(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -q 2>/dev/null) \
        >> "$LAB_ROOT/loadtest/stress-sweep-stats.log" 2>/dev/null || true
      sleep 3
    done
  ) &
  stats_pid=$!

  set +e
  DEVICES="$DEVICES" RATE_PER_DEVICE="$rate" WARMUP="$WARMUP" PHASE="$PHASE" \
    CALLBACK_THREADS="$CALLBACK_THREADS" BENCH_NO_L0_COALESCE=true \
    ISPF_LAB_ROOT="$LAB_ROOT" ISPF_LAB_HTTP_PORT="$HTTP_PORT" \
    bash "$LAB_ROOT/lab-mqtt-event-journal-multi-test.sh" 2>&1 | tee "$step_log" | tee -a "$REPORT"
  bench_code=${PIPESTATUS[0]}
  set -e

  kill "$stats_pid" 2>/dev/null || true
  wait "$stats_pid" 2>/dev/null || true

  eps=$(grep 'Events/s total (scylla):' "$step_log" | tail -1 | awk '{print $NF}' || echo "0")
  eff=$(grep 'Efficiency vs MQTT target:' "$step_log" | tail -1 | awk '{print $NF}' | tr -d '%' || echo "0")
  ispf_cpu=$(peak_cpu "ispf-server" || echo "0")
  scylla_cpu=$(peak_cpu "scylla" || echo "0")

  status="OK"
  if [ "$bench_code" -ne 0 ]; then
    status="BENCH_FAIL"
    break_reason="benchmark exit ${bench_code} at rate=${rate}"
  elif ! api_ok || ! login_ok; then
    status="API_DOWN"
    break_reason="API/login failed after rate=${rate}"
  fi

  echo -e "${rate}\t${aggregate}\t${eps}\t${eff}\t${ispf_cpu}\t${scylla_cpu}\t${status}" >> "$SUMMARY"
  echo "STEP RESULT: ${eps} events/s  eff=${eff}%  ispf_cpu=${ispf_cpu}%  scylla_cpu=${scylla_cpu}%  status=${status}" | tee -a "$REPORT"

  if [ -n "$break_reason" ]; then
    echo "BREAK: $break_reason" | tee -a "$REPORT"
    break
  fi

  # Saturation: throughput barely grows despite much higher MQTT target.
  if awk -v prev="$prev_eps" -v cur="$eps" -v pt="$prev_target" -v ct="$aggregate" \
      'BEGIN {
        if (prev+0 > 1000 && ct > pt * 1.25 && cur+0 < prev+0 * 1.05) { exit 0 } else { exit 1 }
      }'; then
    break_reason="throughput plateau: ${eps} events/s (prev ${prev_eps}) while target ${aggregate} (prev ${prev_target})"
    echo "BREAK: $break_reason" | tee -a "$REPORT"
    break
  fi

  # Collapse: efficiency crashed vs prior step.
  if awk -v eff="$eff" 'BEGIN { exit (eff+0 < 8) ? 0 : 1 }'; then
    break_reason="efficiency collapsed to ${eff}% at rate=${rate}"
    echo "BREAK: $break_reason" | tee -a "$REPORT"
    break
  fi

  prev_eps="$eps"
  prev_target="$aggregate"

  echo "cooldown 20s..." | tee -a "$REPORT"
  sleep 20
done

echo "" | tee -a "$REPORT"
echo "=== SWEEP COMPLETE $(date -Is) ===" | tee -a "$REPORT"
if [ -n "$break_reason" ]; then
  echo "Breaking point: $break_reason" | tee -a "$REPORT"
else
  echo "Completed all steps without hard break (may still be below target efficiency)." | tee -a "$REPORT"
fi
echo "Summary: $SUMMARY" | tee -a "$REPORT"
column -t "$SUMMARY" 2>/dev/null | tee -a "$REPORT" || cat "$SUMMARY" | tee -a "$REPORT"
uptime | tee -a "$REPORT"
