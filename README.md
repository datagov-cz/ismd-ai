# Semantic Modeling Assistant Backend

A Spring Boot REST API for asynchronous semantic modeling suggestions.

## Important TODOs

* Implement mock responses for development environment

## Run

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

All REST endpoints require OIDC authentication with a bearer JWT:

```http
Authorization: Bearer <access-token>
```

Configure the OIDC issuer and JWK set URL with environment variables or properties:

```properties
OIDC_ISSUER_URI=https://issuer.example.com/realms/ismd
OIDC_JWK_SET_URI=https://issuer.example.com/realms/ismd/protocol/openid-connect/certs
APP_TOKENS_MAX_ALLOWED_PER_DAY=100000
APP_SPARQL_ENDPOINT_URL=https://opendata.eselpoint.gov.cz/sparql
APP_SPARQL_TIMEOUT=10s
APP_SPARQL_MAX_ATTEMPTS=3
APP_SPARQL_RETRY_BACKOFF=250ms
APP_DB_HOST=localhost
APP_DB_PORT=5432
APP_DB_NAME=ismd
APP_DB_USER=ismd
APP_DB_PASSWORD=ismd
```

### Environment variables

Copy `.env.example` to `.env` for a documented local configuration. The
defaults below are the application defaults from `application.properties`;
Docker Compose or `.env.example` may intentionally provide development-specific
values instead.

| Variable | Default | Description |
| --- | --- | --- |
| `APP_ENVIRONMENT` | `production` | Runtime mode. `dev` and `development` enable development-only mock endpoints. |
| `OIDC_ISSUER_URI` | `http://localhost:8081/realms/ismd` | Expected OpenID Connect issuer used to validate bearer JWTs. |
| `OIDC_JWK_SET_URI` | `http://localhost:8081/realms/ismd/protocol/openid-connect/certs` | Endpoint providing public keys used to verify bearer JWT signatures. |
| `APP_SPARQL_ENDPOINT_URL` | `https://opendata.eselpoint.gov.cz/sparql` | SPARQL endpoint queried for legal-act data. |
| `APP_SPARQL_TIMEOUT` | `10s` | Maximum duration of one SPARQL request. |
| `APP_SPARQL_MAX_ATTEMPTS` | `3` | Total number of attempts for a retryable SPARQL request. |
| `APP_SPARQL_RETRY_BACKOFF` | `250ms` | Delay between retryable SPARQL request attempts. |
| `APP_TOKENS_MAX_ALLOWED_PER_DAY` | `1000` | Maximum number of LLM tokens a user may consume per day; `0` denies all usage. |
| `APP_LLM_ENABLED` | `false` | Enables calls to the configured external LLM provider. |
| `APP_LLM_PROVIDER` | `OPENAI` | Provider API format. Supported values are listed under LLM Configuration. |
| `APP_LLM_ENDPOINT_URL` | Provider-specific | Overrides the provider's default endpoint when set. |
| `APP_LLM_MODEL` | `gpt-4o-mini` | Model identifier sent to the provider and substituted for `{model}` in supported endpoint URLs. |
| `APP_LLM_API_KEY` | Empty | Credential sent to the provider. Ollama does not require one. |
| `APP_LLM_MAX_TOKENS` | `1024` | Default maximum number of output tokens requested from the model. |
| `APP_LLM_TEMPERATURE` | Empty | Optional sampling temperature; when unset, the provider's default is used. |
| `APP_LLM_REASONING_EFFORT` | Empty | Optional reasoning depth. Supported values depend on the provider and model; see the mapping below. |
| `APP_LLM_TEXT_VERBOSITY` | Empty | Optional response detail level. OpenAI-style APIs support `low`, `medium`, and `high`. |
| `APP_LLM_TIMEOUT` | `60s` | Maximum duration of one LLM HTTP request. |
| `APP_LLM_LOG_INTERACTIONS` | `false` | Requests provider-side interaction retention where supported. Keep disabled for sensitive text. |
| `APP_DB_HOST` | `localhost` | PostgreSQL server hostname. Docker Compose uses its `postgres` service instead. |
| `APP_DB_PORT` | `5432` | PostgreSQL server TCP port. |
| `APP_DB_NAME` | `ismd` | PostgreSQL database name. |
| `APP_DB_USER` | `ismd` | PostgreSQL login username. |
| `APP_DB_PASSWORD` | `ismd` | PostgreSQL login password; change it outside local development. |
| `APP_DB_TIME_ZONE` | `Europe/Prague` | Time zone applied when each database connection is initialized. |

## SPARQL Access

Use `SparqlQueryExecutor` for services that call the configured SPARQL endpoint.
It centralizes retry behavior, query timeouts, endpoint error messages, and
`SparqlAccessException` reporting.

For RDF4J tuple queries, inject the executor and keep result mapping inside the
lambda:

```java
return sparqlQueryExecutor.query("sample resource query", connection -> {
    try (TupleQueryResult result = sparqlQueryExecutor.evaluateTupleQuery(connection, QUERY)) {
        // map bindings here
    }
});
```

For services that need the configured `RestClient`, wrap the HTTP call:

```java
return sparqlQueryExecutor.execute("legal act repository query", () ->
        sparqlRestClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(JsonNode.class)
);
```

Use a short operation name that identifies the query in logs and error
responses. Do not add service-local retry loops around SPARQL calls; configure
`APP_SPARQL_TIMEOUT`, `APP_SPARQL_MAX_ATTEMPTS`, and
`APP_SPARQL_RETRY_BACKOFF` instead.

## LLM Configuration

External LLM calls are disabled by default. Enable and configure them with:

```properties
APP_LLM_ENABLED=true
APP_LLM_PROVIDER=OPENAI
APP_LLM_MODEL=gpt-4o-mini
APP_LLM_API_KEY=<provider-api-key>
APP_LLM_ENDPOINT_URL=https://api.openai.com/v1/responses
APP_LLM_MAX_TOKENS=1024
APP_LLM_TEMPERATURE=0.2
APP_LLM_REASONING_EFFORT=
APP_LLM_TEXT_VERBOSITY=
APP_LLM_TIMEOUT=60s
APP_LLM_LOG_INTERACTIONS=false
```

Supported providers are `OPENAI`, `OPENAI_COMPATIBLE`, `AZURE_OPENAI`,
`ANTHROPIC`, `GOOGLE`, `MISTRAL`, `COHERE`, and `OLLAMA`. Use
`APP_LLM_ENDPOINT_URL` to override any default provider endpoint. Google Gemini
endpoints may include `{model}`, which is replaced with `APP_LLM_MODEL`.

Reasoning effort and text verbosity are translated to each provider's native
request format when supported:

| Provider | Reasoning effort mapping | Text verbosity mapping |
| --- | --- | --- |
| `OPENAI` | `reasoning.effort`: `none`, `minimal`, `low`, `medium`, `high`, `xhigh`, `max` | `text.verbosity`: `low`, `medium`, `high` |
| `OPENAI_COMPATIBLE` | OpenAI-compatible `reasoning_effort` | OpenAI-compatible `verbosity` |
| `AZURE_OPENAI` | `reasoning.effort`: `none`, `minimal`, `low`, `medium`, `high`, `xhigh` | `text.verbosity`: `low`, `medium`, `high` |
| `ANTHROPIC` | `output_config.effort`: `low`, `medium`, `high`, `xhigh`, `max` | Not supported separately |
| `GOOGLE` | `generationConfig.thinkingConfig.thinkingLevel`: `minimal`, `low`, `medium`, `high` | Not supported separately |
| `MISTRAL` | `reasoning_effort`: `none`, `minimal`, `low`, `medium`, `high`, `xhigh` | Not supported separately |
| `COHERE` | No categorical effort control; Cohere exposes a separate thinking budget | Not supported separately |
| `OLLAMA` | `think`: `none`, `low`, `medium`, `high`, `max` | Not supported separately |

The application rejects reasoning values unsupported by the selected provider
before sending the request. Individual models may support only a subset of the
provider-level values. Both settings are omitted when their environment
variables are empty.

Set `APP_LLM_LOG_INTERACTIONS=true` to ask supported providers to retain API
requests and responses in their provider-side logs. OpenAI and Azure OpenAI
always receive an explicit `store` value so their Responses APIs remain opt-in;
Google Gemini receives `store: true` only when logging is enabled.
Other providers receive no extra request field. Keep this disabled unless the
sent legal text is permitted to be retained by the configured provider. Azure
OpenAI custom endpoints must target `/openai/v1/responses` (or a compatible
Responses API preview endpoint).

## API Docs

When the server is running:

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Implemented

- Legal class suggestion jobs
- Legal property suggestion jobs
- Accepted, liked, and disliked suggestion feedback
- OIDC bearer-token authentication
- In-memory asynchronous jobs
- Configurable LLM client

## Test

Docker must be installed and running, and its daemon must be accessible to the
Maven process. The integration tests use Testcontainers to start an isolated
PostgreSQL container automatically; no separately configured test database is
required. Database-backed integration tests are skipped when Docker is not
available.

```bash
mvn test
```
