#!/usr/bin/env bash
set -euo pipefail

INFRA_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INFRA_COMPOSE_FILE="${INFRA_ROOT_DIR}/infra/docker-compose.infra.yml"
INFRA_ENV_FILE="${ENV_FILE:-${INFRA_ROOT_DIR}/.env}"

infra_die() {
  echo "ERROR: $*" >&2
  exit 1
}

infra_require_command() {
  command -v "$1" >/dev/null 2>&1 || infra_die "Required command '$1' was not found."
}

infra_preflight() {
  infra_require_command docker
  [[ -f "${INFRA_COMPOSE_FILE}" ]] || infra_die "Compose file not found: ${INFRA_COMPOSE_FILE}"
  [[ -r "${INFRA_ENV_FILE}" ]] || infra_die "Environment file not found: ${INFRA_ENV_FILE}. Copy .env.example to .env and replace all placeholders."
  docker compose version >/dev/null 2>&1 || infra_die "Docker Compose v2 is unavailable. Install the docker compose plugin."
  docker info >/dev/null 2>&1 || infra_die "Docker daemon is unavailable. Start Docker and retry."
}

infra_compose() {
  docker compose \
    --env-file "${INFRA_ENV_FILE}" \
    --file "${INFRA_COMPOSE_FILE}" \
    "$@"
}

infra_env_value() {
  local key="$1"
  local line
  local value

  line="$(awk -v key="${key}" 'index($0, key "=") == 1 { value = substr($0, length(key) + 2) } END { print value }' "${INFRA_ENV_FILE}")"
  value="${line%$'\r'}"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  [[ -n "${value}" ]] || infra_die "${key} is missing or empty in ${INFRA_ENV_FILE}."
  printf '%s' "${value}"
}

infra_validate_no_placeholders() {
  local key
  local value
  local required_secrets=(
    POSTGRES_ADMIN_PASSWORD
    POSTGRES_PASSWORD
    REDIS_PASSWORD
    RABBITMQ_PASSWORD
    NACOS_AUTH_TOKEN
    NACOS_AUTH_IDENTITY_KEY
    NACOS_AUTH_IDENTITY_VALUE
    NACOS_ADMIN_PASSWORD
    SEAWEEDFS_ACCESS_KEY
    SEAWEEDFS_SECRET_KEY
  )

  for key in "${required_secrets[@]}"; do
    value="$(infra_env_value "${key}")"
    if [[ "${value}" == replace_me* ]]; then
      infra_die "${key} still contains the public placeholder from .env.example. Replace it in ${INFRA_ENV_FILE}."
    fi
  done

  [[ "$(infra_env_value POSTGRES_DB)" == liteworkflow_backend ]] \
    || infra_die "POSTGRES_DB must be exactly 'liteworkflow_backend'."
  [[ "$(infra_env_value POSTGRES_USER)" != postgres ]] \
    || infra_die "POSTGRES_USER must be a non-administrator application role, not 'postgres'."
}

infra_nacos_login() {
  local port
  local password

  port="$(infra_env_value NACOS_CLIENT_PORT)"
  password="$(infra_env_value NACOS_ADMIN_PASSWORD)"
  curl --silent --show-error --fail --output /dev/null \
    --request POST \
    "http://127.0.0.1:${port}/nacos/v3/auth/user/login" \
    --data-urlencode username=nacos \
    --data-urlencode "password=${password}"
}

infra_initialize_nacos_admin() {
  local port
  local password

  infra_require_command curl
  if infra_nacos_login 2>/dev/null; then
    echo "Nacos administrator is already initialized and authenticated."
    return
  fi

  port="$(infra_env_value NACOS_CLIENT_PORT)"
  password="$(infra_env_value NACOS_ADMIN_PASSWORD)"
  if ! curl --silent --show-error --fail --output /dev/null \
    --request POST \
    "http://127.0.0.1:${port}/nacos/v3/auth/user/admin" \
    --data-urlencode "password=${password}"; then
    infra_die "Nacos administrator initialization failed. If this is an existing volume, set NACOS_ADMIN_PASSWORD to its current password or run an intentional reset."
  fi

  infra_nacos_login \
    || infra_die "Nacos administrator was initialized but the authentication check failed."
  echo "Nacos administrator initialized and authenticated."
}
