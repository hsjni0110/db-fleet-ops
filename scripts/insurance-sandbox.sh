#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIRECTORY="$(cd "${SCRIPT_DIRECTORY}/.." && pwd)"
SQL_DIRECTORY="${PROJECT_DIRECTORY}/infra/mysql/insurance"

log() {
  echo "[insurance-sandbox] $*"
}

fail() {
  echo "[insurance-sandbox][FAIL] $*" >&2
  exit 1
}

run_sql() {
  local sql_file="$1"

  docker compose \
    --file "${PROJECT_DIRECTORY}/docker-compose.yml" \
    --project-directory "${PROJECT_DIRECTORY}" \
    exec -T target-mysql \
    sh -c 'exec mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}"' \
    < "${sql_file}"
}

require_target_mysql() {
  if ! docker compose \
      --file "${PROJECT_DIRECTORY}/docker-compose.yml" \
      --project-directory "${PROJECT_DIRECTORY}" \
      ps --status running --services \
      | grep -qx 'target-mysql'; then
    fail "target-mysql is not running. Start it with: docker compose up -d target-mysql"
  fi
}

verify() {
  log "verifying normal Insurance Sandbox"
  run_sql "${SQL_DIRECTORY}/verify-normal.sql"
}

inject() {
  log "injecting five deterministic fault cases"
  run_sql "${SQL_DIRECTORY}/inject-faults.sql"
  run_sql "${SQL_DIRECTORY}/verify-faults.sql"
}

reset() {
  log "removing only reserved fault fixtures"
  run_sql "${SQL_DIRECTORY}/reset-faults.sql"
  verify
}

usage() {
  echo "Usage: $0 {verify|inject|reset}" >&2
  exit 2
}

main() {
  case "${1:-}" in
    verify|inject|reset)
      ;;
    *)
      usage
      ;;
  esac

  require_target_mysql

  case "$1" in
    verify)
      verify
      ;;
    inject)
      inject
      ;;
    reset)
      reset
      ;;
  esac
}

main "$@"
