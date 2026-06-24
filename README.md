# Semantic Modeling Assistant Backend

A Spring Boot REST API for asynchronous semantic modeling suggestions.

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
```

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

Authenticated LLM endpoint:

```http
POST /llm
Content-Type: application/json

{
  "system_prompt": "You are concise.",
  "prompt": "Reply with one sentence.",
  "max_tokens": 128,
  "temperature": 0.2
}
```

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
- Configurable LLM client and test endpoint

## Test

```bash
mvn test
```
