# Minimal local production-mode setup

This guide starts the API locally with `APP_ENVIRONMENT=production`, including
real JWT validation. PostgreSQL, Keycloak, and the Spring application are all
started by a dedicated Docker Compose configuration.

> [!IMPORTANT]
> This is a **production-mode smoke test**, not a production-ready deployment.
> Keycloak's `start-dev`, direct password grant, example credentials, and plain
> HTTP are intended only for local testing.

## Prerequisites

- Docker with Docker Compose
- `curl`
- `jq`

The local setup uses:

- API: <http://localhost:8080>
- Keycloak: <http://localhost:8081>
- PostgreSQL: `localhost:5432`

## Keycloak configuration included with the setup

The Compose configuration imports
[`local/keycloak/ismd-realm.json`](local/keycloak/ismd-realm.json), which creates:

- Realm: `ismd`
- Client: `ismd-local`
- User: `local-user`
- Password: `local-password`

Keycloak 26 configures its built-in `admin-cli` client to issue lightweight
access tokens by default. Those tokens omit `sub`, while this API requires a
non-blank `sub` as its stable user identifier. The dedicated `ismd-local`
client explicitly uses regular access tokens and avoids modifying Keycloak's
administrative client. Tokens without `sub` are rejected with HTTP `401`.

The imported user includes a complete local profile and has no pending required
actions, allowing the direct password grant to run without an interactive
first-login step.

The Keycloak administrator account is `admin` / `admin` and is only provided in
case the local configuration needs to be inspected at
<http://localhost:8081/admin/>. It is not used to call the API.

## 1. Configure the optional LLM integration

Docker Compose automatically reads the repository's `.env` file. Authentication
can be tested with `APP_LLM_ENABLED=false`, but the asynchronous suggestion job
will subsequently have status `failed` because no LLM call can be made.

For completed class-suggestion jobs, configure at least:

```dotenv
APP_LLM_ENABLED=true
APP_LLM_PROVIDER=OPENAI
APP_LLM_MODEL=gpt-4o-mini
APP_LLM_API_KEY=replace-with-your-api-key
```

Do not commit a real API key.

## 2. Start the complete local environment

From the repository root, run:

```bash
docker compose --file docker-compose.local-production.yml up --build
```

This starts all three services.

## 3. Wait for Keycloak and the API

The explicit proxy bypass prevents localhost requests from being sent through a
configured HTTP proxy.

```bash
until curl --proxy '' \
  --resolve localhost:8081:127.0.0.1 \
  --fail --silent --show-error --max-time 5 \
  http://localhost:8081/realms/ismd/.well-known/openid-configuration \
  >/dev/null; do
  echo "Keycloak is not reachable yet; retrying..."
  sleep 2
done

echo "Keycloak is ready."

until curl --proxy '' \
  --resolve localhost:8080:127.0.0.1 \
  --silent --output /dev/null --max-time 5 \
  --write-out '%{http_code}' \
  http://localhost:8080/v3/api-docs |
  grep --quiet '^200$'; do
  echo "API is not reachable yet; retrying..."
  sleep 2
done

echo "API is ready."
```

If preferred, configure the proxy bypass once for the current shell:

```bash
export NO_PROXY="localhost,127.0.0.1,::1"
export no_proxy="$NO_PROXY"
```

## 4. Obtain a valid access token

Request a regular access token for the imported local user:

```bash
ACCESS_TOKEN=$(
  curl --proxy '' \
    --resolve localhost:8081:127.0.0.1 \
    --fail --silent --show-error \
    --request POST \
    http://localhost:8081/realms/ismd/protocol/openid-connect/token \
    --header "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "client_id=ismd-local" \
    --data-urlencode "username=local-user" \
    --data-urlencode "password=local-password" \
    --data-urlencode "grant_type=password" |
  jq --raw-output '.access_token'
)
```

Verify that a token was returned:

```bash
test -n "$ACCESS_TOKEN" && test "$ACCESS_TOKEN" != "null" && echo "Token obtained"
```

Verify that its payload contains a non-empty subject:

```bash
echo "$ACCESS_TOKEN" |
  cut --delimiter=. --fields=2 |
  tr '_-' '/+' |
  base64 --decode 2>/dev/null |
jq --exit-status '.sub | select(type == "string" and length > 0)'
```

The output should be a quoted UUID-like user identifier. The default token is
short-lived; repeat this step if the API later returns HTTP `401`.

If `local/keycloak/ismd-realm.json` is changed after Keycloak has already
started, recreate the Keycloak container before requesting another token.
Keycloak imports a realm only when that realm does not already exist:

```bash
docker compose --file docker-compose.local-production.yml \
  up --detach --force-recreate keycloak
```

Wait for Keycloak again using step 3 before retrying the token request.

## 5. Start a suggestion job

```bash
START_RESPONSE=$(
  curl --proxy '' \
    --resolve localhost:8080:127.0.0.1 \
    --fail-with-body --silent --show-error \
    --request POST \
    http://localhost:8080/legal-acts/2026/60/2026-05-27/class-suggestions-top-k-extraction-jobs \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --header "Content-Type: application/json" \
    --data '{
      "k": 1,
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
)

echo "$START_RESPONSE" | jq
JOB_ID=$(echo "$START_RESPONSE" | jq --raw-output '.job_id')
```

`--fail-with-body` keeps the API's validation detail visible if this request is
rejected. For example, an invalid `structural_element_ids` value is returned as
an HTTP `422` response with a JSON `detail` field.

An accepted request returns HTTP `202` with a response similar to:

```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "in_progress"
}
```

## 6. Retrieve the job status

```bash
curl --proxy '' \
  --resolve localhost:8080:127.0.0.1 \
  --fail --silent --show-error \
  --get \
  http://localhost:8080/legal-acts/class-suggestions-jobs \
  --header "Authorization: Bearer ${ACCESS_TOKEN}" \
  --data-urlencode "jobIds=${JOB_ID}" |
jq
```

Repeat the request until the returned status is `completed` or `failed`.
While the LLM response is streaming, each request may contain newly completed
objects in `new_suggestions` even though the status is still `in_progress`.
Accumulate those objects on the client: every suggestion is returned only once,
so later polling requests contain only suggestions completed since the previous
request. The status changes to `completed` only after the complete LLM response
has arrived. A further poll after completion therefore returns an empty
`new_suggestions` array.

The repository smoke-test script performs this accumulation and checks for
duplicate suggestion IDs automatically:

```bash
./run-minimal-production-smoke-test.sh --cleanup
```

## 7. Confirm authentication is enforced

A request without the bearer token should return HTTP `401`:

```bash
curl --proxy '' \
  --resolve localhost:8080:127.0.0.1 \
  --output /dev/null --silent --write-out '%{http_code}\n' \
  --request POST \
  http://localhost:8080/legal-acts/2026/60/2026-05-27/class-suggestions-top-k-extraction-jobs \
  --header "Content-Type: application/json" \
  --data '{"k":1}'
```

Expected output:

```text
401
```

## Stop and clean up

Stop and remove the local containers and network:

```bash
docker compose --file docker-compose.local-production.yml down
```

The PostgreSQL data remains in the named volume. To reset it as well:

```bash
docker compose --file docker-compose.local-production.yml down --volumes
```

## What changes for a real deployment

For an actual production deployment:

- Use an organization-managed OIDC provider and a dedicated production client.
- Use an authorization-code or service-account flow instead of the password
  grant.
- Serve the identity provider and API over HTTPS.
- Replace all example credentials and manage secrets outside source control.
- Configure audience validation and endpoint-specific roles/scopes if required.
- Use a durable, backed-up PostgreSQL deployment.
- Configure monitoring, health checks, resource limits, and key rotation.
