#!/usr/bin/env bash
set -euo pipefail

APP_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
APP_ENV_FILE="${ENV_FILE:-${APP_ROOT_DIR}/.env}"
APP_RUN_DIR="${APP_ROOT_DIR}/.run"

app_die() {
  echo "ERROR: $*" >&2
  exit 1
}

app_require_command() {
  command -v "$1" >/dev/null 2>&1 || app_die "Required command '$1' was not found."
}

app_env_value() {
  local key="$1"
  local value
  value="$(awk -v key="${key}" 'index($0, key "=") == 1 { value = substr($0, length(key) + 2) } END { print value }' "${APP_ENV_FILE}")"
  value="${value%$'\r'}"
  [[ -n "${value}" ]] || app_die "${key} is missing or empty in ${APP_ENV_FILE}."
  printf '%s' "${value}"
}

app_preflight() {
  app_require_command curl
  app_require_command java
  [[ -r "${APP_ENV_FILE}" ]] || app_die "Environment file not found: ${APP_ENV_FILE}. Copy .env.example to .env first."
}

app_validate_environment() {
  local key value
  local required=(
    POSTGRES_PASSWORD REDIS_PASSWORD RABBITMQ_PASSWORD JWT_SECRET INTERNAL_SERVICE_TOKEN
    SEAWEEDFS_ACCESS_KEY SEAWEEDFS_SECRET_KEY LITEWORKFLOW_AI_API_KEY
    LITEWORKFLOW_AI_CHAT_MODEL LITEWORKFLOW_AI_EMBEDDING_MODEL
    LITEWORKFLOW_AI_EMBEDDING_DIMENSIONS
  )
  for key in "${required[@]}"; do
    value="$(app_env_value "${key}")"
    [[ "${value}" != replace_me* ]] || app_die "${key} still contains a public placeholder."
  done
  [[ "$(app_env_value LITEWORKFLOW_AI_EMBEDDING_DIMENSIONS)" =~ ^[1-9][0-9]*$ ]] \
    || app_die "LITEWORKFLOW_AI_EMBEDDING_DIMENSIONS must be a positive integer."
}

app_export_environment() {
  set -a
  # .env is a trusted, local operator file and must use shell-compatible KEY=VALUE syntax.
  # shellcheck disable=SC1090
  source "${APP_ENV_FILE}"
  set +a
}

app_wait_for_health() {
  local service="$1"
  local port="$2"
  local attempts="${APP_HEALTH_ATTEMPTS:-90}"
  local response
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    response="$(curl --silent --show-error --max-time 2 "http://127.0.0.1:${port}/actuator/health" 2>/dev/null || true)"
    if [[ "${response}" == *'"status":"UP"'* ]]; then
      echo "PASS service/${service}: health=UP port=${port}"
      return 0
    fi
    sleep 1
  done
  return 1
}
