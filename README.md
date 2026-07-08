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
APP_LLM_ENDPOINT_URL=https://api.openai.com/v1/chat/completions
APP_LLM_MAX_TOKENS=1024
APP_LLM_TEMPERATURE=0.2
APP_LLM_TIMEOUT=60s
```

Supported providers are `OPENAI`, `OPENAI_COMPATIBLE`, `AZURE_OPENAI`,
`ANTHROPIC`, `GOOGLE`, `MISTRAL`, `COHERE`, and `OLLAMA`. Use
`APP_LLM_ENDPOINT_URL` to override any default provider endpoint. Azure OpenAI
and Google Gemini endpoints may include `{model}`, which is replaced with
`APP_LLM_MODEL`.

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

```bash
mvn test
```
