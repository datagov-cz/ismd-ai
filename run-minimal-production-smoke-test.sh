#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.local-production.yml"
readonly START_ENDPOINT="http://localhost:8080/legal-acts/2026/60/2026-05-27/class-suggestions-top-k-extraction-jobs"
readonly STATUS_ENDPOINT="http://localhost:8080/legal-acts/class-suggestions-jobs"

READINESS_TIMEOUT_SECONDS="${READINESS_TIMEOUT_SECONDS:-300}"
JOB_TIMEOUT_SECONDS="${JOB_TIMEOUT_SECONDS:-180}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"
CLEANUP=false
CLEANUP_VOLUMES=false

usage() {
  cat <<'EOF'
Usage: ./run-minimal-production-smoke-test.sh [OPTION]

Build and start the local production-mode stack, wait for it to become ready,
obtain a Keycloak token, start and incrementally poll a streaming suggestion
job, verify that suggestions are returned exactly once, and verify that an
unauthenticated request is rejected.

Options:
  --cleanup          Stop containers after the test (keeps database data)
  --cleanup-volumes  Stop containers and remove the database volume
  -h, --help         Show this help

Optional environment variables:
  READINESS_TIMEOUT_SECONDS  Service startup timeout (default: 300)
  JOB_TIMEOUT_SECONDS        Suggestion job timeout (default: 180)
  POLL_INTERVAL_SECONDS      Polling interval (default: 2)
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for argument in "$@"; do
  case "${argument}" in
    --cleanup)
      CLEANUP=true
      ;;
    --cleanup-volumes)
      CLEANUP=true
      CLEANUP_VOLUMES=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage >&2
      fail "Unknown option: ${argument}"
      ;;
  esac
done

for command_name in docker curl jq base64; do
  command -v "${command_name}" >/dev/null 2>&1 || fail "Required command not found: ${command_name}"
done

docker compose version >/dev/null 2>&1 || fail "Docker Compose is not available"
[[ -f "${COMPOSE_FILE}" ]] || fail "Compose file not found: ${COMPOSE_FILE}"

compose=(docker compose --file "${COMPOSE_FILE}")

cleanup() {
  if [[ "${CLEANUP}" == true ]]; then
    echo "Stopping the local production-mode stack..."
    if [[ "${CLEANUP_VOLUMES}" == true ]]; then
      "${compose[@]}" down --volumes
    else
      "${compose[@]}" down
    fi
  fi
}
trap cleanup EXIT

wait_until() {
  local description="$1"
  local check_function="$2"
  local deadline=$((SECONDS + READINESS_TIMEOUT_SECONDS))

  until "${check_function}"; do
    if ((SECONDS >= deadline)); then
      fail "Timed out after ${READINESS_TIMEOUT_SECONDS}s waiting for ${description}"
    fi
    echo "${description} is not reachable yet; retrying..."
    sleep "${POLL_INTERVAL_SECONDS}"
  done

  echo "${description} is ready."
}

keycloak_is_ready() {
  curl --proxy '' \
    --resolve localhost:8081:127.0.0.1 \
    --fail --silent --show-error --max-time 5 \
    http://localhost:8081/realms/ismd/.well-known/openid-configuration \
    >/dev/null 2>&1
}

api_is_ready() {
  local status_code
  status_code="$(
    curl --proxy '' \
      --resolve localhost:8080:127.0.0.1 \
      --silent --output /dev/null --max-time 5 \
      --write-out '%{http_code}' \
      http://localhost:8080/v3/api-docs 2>/dev/null || true
  )"
  [[ "${status_code}" == 200 ]]
}

cd "${SCRIPT_DIR}"

echo "Building and starting the local production-mode stack..."
"${compose[@]}" up --build --detach

wait_until "Keycloak" keycloak_is_ready
wait_until "API" api_is_ready

echo "Requesting an access token..."
token_response="$(
  curl --proxy '' \
    --resolve localhost:8081:127.0.0.1 \
    --fail --silent --show-error \
    --request POST \
    http://localhost:8081/realms/ismd/protocol/openid-connect/token \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=ismd-local' \
    --data-urlencode 'username=local-user' \
    --data-urlencode 'password=local-password' \
    --data-urlencode 'grant_type=password'
)"

ACCESS_TOKEN="$(jq --exit-status --raw-output \
  '.access_token | select(type == "string" and length > 0)' <<<"${token_response}")" \
  || fail "Keycloak did not return an access token"

jwt_payload="${ACCESS_TOKEN#*.}"
jwt_payload="${jwt_payload%%.*}"
jwt_payload="$(tr '_-' '/+' <<<"${jwt_payload}" | tr -d '\n')"
while ((${#jwt_payload} % 4 != 0)); do
  jwt_payload+='='
done

decoded_payload="$(printf '%s' "${jwt_payload}" | base64 --decode 2>/dev/null)" \
  || fail "The access token payload is not valid base64url"
subject="$(jq --exit-status --raw-output \
  '.sub | select(type == "string" and length > 0)' <<<"${decoded_payload}")" \
  || fail "The access token does not contain a non-empty subject"
echo "Token obtained for subject ${subject}."

echo "Starting a class-suggestion job..."
START_RESPONSE="$(
  curl --proxy '' \
    --resolve localhost:8080:127.0.0.1 \
    --fail-with-body --silent --show-error \
    --request POST \
    "${START_ENDPOINT}" \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --header 'Content-Type: application/json' \
    --data '{
      "k": 3,
      "structural_element_ids": [
        "https://e-sbirka.gov.cz/eli/cz/sb/2026/60/2026-05-27/dokument/norma/cast_1/hlava_1/par_3"
      ],
      "context_text": "Generate the main conceptual-model class.",
      "known_conceptual_model": {
        "classes": [],
        "attributes": [],
        "relationships": []
      }
    }'
)"
echo "${START_RESPONSE}" | jq

JOB_ID="$(jq --exit-status --raw-output \
  '.job_id | select(type == "string" and length > 0)' <<<"${START_RESPONSE}")" \
  || fail "The API did not return a job_id"

echo "Polling suggestion job ${JOB_ID}..."
job_deadline=$((SECONDS + JOB_TIMEOUT_SECONDS))
ALL_SUGGESTIONS='[]'
poll_count=0
while true; do
  poll_count=$((poll_count + 1))
  STATUS_RESPONSE="$(
    curl --proxy '' \
      --resolve localhost:8080:127.0.0.1 \
      --fail --silent --show-error \
      --get \
      "${STATUS_ENDPOINT}" \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      --data-urlencode "jobIds=${JOB_ID}"
  )"

  JOB_RESPONSE="$(jq --exit-status --compact-output --arg job_id "${JOB_ID}" '
    if type != "array" then
      error("status response is not an array")
    else
      [.[] | select(.job_id == $job_id)] |
      if length == 1 then .[0]
      else error("expected exactly one response for the requested job")
      end
    end
  ' <<<"${STATUS_RESPONSE}")" || fail "The API returned an invalid status response for job ${JOB_ID}"

  job_status="$(jq --exit-status --raw-output \
    '.status | select(type == "string" and length > 0)' <<<"${JOB_RESPONSE}")" \
    || fail "The status response does not contain a valid job status"

  NEW_SUGGESTIONS="$(jq --exit-status --compact-output '
    .new_suggestions |
    select(type == "array") |
    if all(.[]; .suggestion_id | type == "string" and length > 0) then .
    else error("a streamed suggestion has no valid suggestion_id")
    end
  ' <<<"${JOB_RESPONSE}")" || fail "The status response does not contain a valid new_suggestions array"

  duplicate_ids="$(jq --null-input --raw-output \
    --argjson seen "${ALL_SUGGESTIONS}" \
    --argjson received "${NEW_SUGGESTIONS}" '
      [$seen[], $received[]]
      | group_by(.suggestion_id)
      | map(select(length > 1) | .[0].suggestion_id)
      | join(", ")
    ')"
  [[ -z "${duplicate_ids}" ]] \
    || fail "Polling returned suggestion IDs more than once: ${duplicate_ids}"

  new_suggestion_count="$(jq 'length' <<<"${NEW_SUGGESTIONS}")"
  if ((new_suggestion_count > 0)); then
    echo "Poll ${poll_count} returned ${new_suggestion_count} new completed suggestion(s):"
    jq <<<"${NEW_SUGGESTIONS}"
  fi
  ALL_SUGGESTIONS="$(jq --compact-output --null-input \
    --argjson seen "${ALL_SUGGESTIONS}" \
    --argjson received "${NEW_SUGGESTIONS}" '$seen + $received')"

  case "${job_status}" in
    completed)
      total_suggestion_count="$(jq 'length' <<<"${ALL_SUGGESTIONS}")"
      ((total_suggestion_count > 0)) \
        || fail "Suggestion job completed without returning any suggestions"
      echo "Suggestion job completed after ${poll_count} poll(s) with ${total_suggestion_count} suggestion(s)."
      break
      ;;
    failed)
      echo "${STATUS_RESPONSE}" | jq >&2
      fail "Suggestion job ${JOB_ID} failed; inspect the server logs for the underlying error"
      ;;
    in_progress)
      ;;
    *)
      fail "Suggestion job returned unknown status: ${job_status}"
      ;;
  esac

  if ((SECONDS >= job_deadline)); then
    echo "${STATUS_RESPONSE}" | jq >&2
    fail "Timed out after ${JOB_TIMEOUT_SECONDS}s waiting for suggestion job ${JOB_ID}"
  fi

  echo "Suggestion job status: ${job_status:-not returned yet}; retrying..."
  sleep "${POLL_INTERVAL_SECONDS}"
done

echo "Confirming that completed suggestions are not returned by another poll..."
FINAL_STATUS_RESPONSE="$(
  curl --proxy '' \
    --resolve localhost:8080:127.0.0.1 \
    --fail --silent --show-error \
    --get \
    "${STATUS_ENDPOINT}" \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --data-urlencode "jobIds=${JOB_ID}"
)"

jq --exit-status --arg job_id "${JOB_ID}" '
  [.[] | select(.job_id == $job_id)] |
  length == 1 and
  .[0].status == "completed" and
  .[0].new_suggestions == []
' <<<"${FINAL_STATUS_RESPONSE}" >/dev/null \
  || fail "A poll after completion repeated suggestions or returned an invalid terminal status"
echo "One-time suggestion delivery check passed."

echo "Confirming that authentication is enforced..."
unauthenticated_status="$(
  curl --proxy '' \
    --resolve localhost:8080:127.0.0.1 \
    --output /dev/null --silent --show-error --write-out '%{http_code}' \
    --request POST \
    "${START_ENDPOINT}" \
    --header 'Content-Type: application/json' \
    --data '{"k":1}'
)"

[[ "${unauthenticated_status}" == 401 ]] \
  || fail "Expected unauthenticated request to return HTTP 401, got ${unauthenticated_status}"

echo "Authentication check passed (HTTP 401)."
echo "Minimal production-mode smoke test finished successfully."
