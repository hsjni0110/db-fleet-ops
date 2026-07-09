#!/usr/bin/env bash

set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
WORKER_URL="${WORKER_URL:-http://localhost:8081}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"

GRAFANA_ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}"
GRAFANA_ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-admin}"

MAX_RETRIES="${MAX_RETRIES:-30}"
SLEEP_SECONDS="${SLEEP_SECONDS:-2}"

log() {
  echo "[smoke-test] $*"
}

fail() {
  echo "[smoke-test][FAIL] $*" >&2
  exit 1
}

wait_for_http_2xx() {
  local name="$1"
  local url="$2"

  log "waiting for ${name}: ${url}"

  for attempt in $(seq 1 "${MAX_RETRIES}"); do
    local status_code

    status_code="$(curl -s -o /dev/null -w "%{http_code}" "${url}" || true)"

    if [[ "${status_code}" =~ ^2[0-9][0-9]$ ]]; then
      log "${name} is ready. status=${status_code}"
      return 0
    fi

    log "${name} is not ready yet. attempt=${attempt}/${MAX_RETRIES}, status=${status_code}"
    sleep "${SLEEP_SECONDS}"
  done

  fail "${name} did not become ready. url=${url}"
}

assert_body_contains() {
  local name="$1"
  local url="$2"
  local expected="$3"

  log "checking ${name}: ${url}"

  local body
  body="$(curl -s "${url}" || true)"

  if echo "${body}" | grep -q "${expected}"; then
    log "${name} contains expected text: ${expected}"
    return 0
  fi

  echo "${body}" >&2
  fail "${name} does not contain expected text: ${expected}"
}

assert_prometheus_query_success() {
  local query="$1"

  log "checking prometheus query: ${query}"

  local encoded_query
  encoded_query="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))' "${query}")"

  local body
  body="$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=${encoded_query}" || true)"

  if echo "${body}" | grep -q '"status":"success"'; then
    log "prometheus query succeeded: ${query}"
    return 0
  fi

  echo "${body}" >&2
  fail "prometheus query failed: ${query}"
}

main() {
  log "starting docker compose smoke test"

  wait_for_http_2xx "api health" "${API_URL}/actuator/health"
  wait_for_http_2xx "worker health" "${WORKER_URL}/actuator/health"

  wait_for_http_2xx "api prometheus endpoint" "${API_URL}/actuator/prometheus"
  wait_for_http_2xx "worker prometheus endpoint" "${WORKER_URL}/actuator/prometheus"

  wait_for_http_2xx "prometheus healthy" "${PROMETHEUS_URL}/-/healthy"
  wait_for_http_2xx "prometheus ready" "${PROMETHEUS_URL}/-/ready"

  wait_for_http_2xx "grafana health" "${GRAFANA_URL}/api/health"

  assert_body_contains "api actuator prometheus" "${API_URL}/actuator/prometheus" "jvm_memory_used_bytes"
  assert_body_contains "worker actuator prometheus" "${WORKER_URL}/actuator/prometheus" "jvm_memory_used_bytes"

  assert_prometheus_query_success "up"
  assert_prometheus_query_success 'up{job="db-fleetops-api"}'
  assert_prometheus_query_success 'up{job="db-fleetops-worker"}'

  log "checking grafana datasource"

  local grafana_datasources
  grafana_datasources="$(
    curl -s \
      -u "${GRAFANA_ADMIN_USER}:${GRAFANA_ADMIN_PASSWORD}" \
      "${GRAFANA_URL}/api/datasources" || true
  )"

  if echo "${grafana_datasources}" | grep -q "Prometheus"; then
    log "grafana datasource exists: Prometheus"
  else
    echo "${grafana_datasources}" >&2
    fail "grafana datasource not found: Prometheus"
  fi

  log "checking grafana dashboard"

  local grafana_dashboards
  grafana_dashboards="$(
    curl -s \
      -u "${GRAFANA_ADMIN_USER}:${GRAFANA_ADMIN_PASSWORD}" \
      "${GRAFANA_URL}/api/search?query=DB%20FleetOps" || true
  )"

  if echo "${grafana_dashboards}" | grep -q "DB FleetOps Overview"; then
    log "grafana dashboard exists: DB FleetOps Overview"
  else
    echo "${grafana_dashboards}" >&2
    fail "grafana dashboard not found: DB FleetOps Overview"
  fi

  log "docker compose smoke test completed successfully"
}

main "$@"