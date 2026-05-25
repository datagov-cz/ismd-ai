# Semantic Modeling Assistant Backend

A FastAPI-based REST API that provides AI-powered semantic modeling suggestions for legal documents. The backend analyzes legal acts and generates intelligent suggestions for classes, attributes, and relationships to assist in building conceptual models.

## Overview

The Semantic Modeling Assistant Backend leverages Large Language Models (LLMs) to extract semantic information from legal documents and suggest conceptual model elements. The system is designed for legal domain experts and frontend developers who need to build conceptual models from legal texts.

### Key Features

- **Class Suggestion Extraction**: Identifies and suggests classes (entities) from legal document paragraphs
- **Property Suggestion Extraction**: Generates attribute suggestions for specific classes
- **Asynchronous Processing**: Long-running jobs with persisted results
- **Multilingual Support**: Handles multilingual names, definitions, and explanations
- **Feedback System**: Tracks accepted, liked, and disliked suggestions for model improvement
- **Context-Aware Processing**: Considers existing conceptual models when generating new suggestions

## Technical Architecture

### LLM Integration
The backend integrates with LLM providers through Mozilla.ai's `any-llm` package.
Set the provider and model with environment variables:

```bash
LLM_PROVIDER=openai
LLM_MODEL=gpt-4.1
```

Provider-specific credentials are read from the environment by `any-llm`, such as
`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, or `MISTRAL_API_KEY`. You can also set
`LLM_API_KEY` and `LLM_API_BASE` to pass an explicit key or compatible API base
URL to the configured provider.

The integration:
- Processes legal text and extracts suggestions asynchronously
- Uses typed structured outputs for class and property extraction

### Processing Constraints
- **Context Window Limits**: Large legal documents may exceed LLM context windows
- **Rate Limits**: Subject to the configured LLM provider's limits and the per-user daily token limit configured for this API
- **Processing Time**: Response times vary based on document complexity and LLM availability
- **Job Rejection**: Requests may be rejected due to API provider limitations

### Per-User Token Limit

Set `USER_DAILY_TOKEN_LIMIT` to cap each authenticated `user-id` to that many LLM tokens per calendar day. The limiter is disabled when the value is unset or `0`.

```bash
USER_DAILY_TOKEN_LIMIT=100000
```

Token usage is stored in `DATA_DIRECTORY/logs/daily_token_usage.json` by default. Override the path with `TOKEN_USAGE_FILE` if needed.

Get the authenticated user's current daily token usage:

```http
GET /token-usage
Authorization: Bearer <oidc-access-token>
```

## Authentication

All API endpoints use the same authentication dependency. The API supports the existing static credential headers:

```http
user-id: your-user-id
password: your-password-or-api-key
```

For OAuth2/OIDC deployments such as Keycloak, the API can also resolve the user ID from a bearer token:

```http
Authorization: Bearer <oidc-access-token>
```

The token is expected to be validated by deployment infrastructure. This repository does not contain a Keycloak-specific implementation. The API reads the authenticated user ID from OIDC claims, using `sub` first and `preferred_username` second by default. Override the claim list with:

```bash
OIDC_USER_ID_CLAIMS=sub,preferred_username,email
```

## API Endpoints

### Class Suggestions

#### Start Class Suggestions Job
```http
POST /legal-acts/{year}/{number}/{date}/class-suggestions-top-k-extraction-jobs
```

Starts an asynchronous job to extract class suggestions from specified paragraphs of a legal act.

**Path Parameters:**
- `year` (integer): Year of the legal act
- `number` (integer): Official number of the legal act
- `date` (string): Version date in YYYY-MM-DD format

**Request Body:**
```json
{
  "k": 10,
  "structural_element_ids": ["§1", "§2", "§3"],
  "context_text": "Focus on entities related to property rights",
  "known_conceptual_model": {
    "classes": [...],
    "relationships": [...]
  }
}
```

**Request Parameters:**
- `k` (optional, default: 10): Maximum number of class suggestions to extract
- `structural_element_ids` (optional): List of paragraph IDs to process. If not provided, the entire legal act is processed (may exceed API limits)
- `context_text` (optional): Additional context to focus extraction. Use with caution as malicious content can break the process
- `known_conceptual_model` (optional): Existing conceptual model to consider during extraction

**Response (202 Accepted):**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "in_progress"
}
```

#### Get Class Suggestions Job Status
```http
GET /legal-acts/{year}/{number}/{date}/class-suggestions-jobs/{job_id}
```

Retrieves the status and results of a class suggestions job.

**Path Parameters:**
- `year`, `number`, `date`: Same as job creation
- `job_id` (UUID): Job identifier from the start response

**Response:**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "completed",
  "new_suggestions": [
    {
      "id": "class_001",
      "type": "http://example.org/Class",
      "name": {
        "@value": "Property Owner",
        "@language": "en"
      },
      "definition": {
        "@value": "A person or entity that owns property",
        "@language": "en"
      },
      "explanation": {
        "@value": "Represents individuals or organizations with ownership rights",
        "@language": "en"
      },
      "is_subject_of_law": true,
      "is_object_of_law": false,
      "is_event": false,
      "is_document": false,
      "specializes": [
        {
          "id": "parent_class_001"
        }
      ],
      "legal_act": {
        "id": "act_123",
        "type": "http://example.org/LegalAct",
        "official_number": "123/2023"
      },
      "occurrences": [
        {
          "id": "occurrence_001",
          "type": "http://example.org/LocalClassSuggestion",
          "legal_act_part": {
            "id": "paragraph_1",
            "type": "http://example.org/Paragraph",
            "official_identifier": "§ 1"
          }
        }
      ],
      "references": [
        {
          "id": "paragraph_2",
          "type": "http://example.org/Paragraph",
          "official_identifier": "§ 2"
        }
      ]
    }
  ]
}
```

**Job Status Values:**
- `in_progress`: Job is running, may have partial results
- `completed`: Job finished successfully, all results available
- `failed`: Job failed due to LLM limits, malicious input, or other errors

### Property Suggestions

#### Start Property Suggestions Job
```http
POST /legal-acts/{year}/{number}/{date}/property-suggestions-top-k-extraction-jobs
```

Starts a job to extract attribute suggestions for a specific class.

**Request Body:**
```json
{
  "k": 10,
  "selected_class_id": "class_001",
  "structural_element_ids": ["§1", "§2"],
  "context_text": "Focus on ownership-related properties",
  "known_conceptual_model": {
    "classes": [...],
    "relationships": [...]
  }
}
```

**Key Parameters:**
- `selected_class_id` (required): ID of the class for which to generate attribute suggestions
- Other parameters same as class suggestions

**Response (202 Accepted):**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440001",
  "selected_class_id": "class_001",
  "status": "in_progress"
}
```

#### Get Property Suggestions Job Status
```http
GET /legal-acts/{year}/{number}/{date}/property-suggestions-jobs/{job_id}
```

**Response:**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440001",
  "selected_class_id": "class_001",
  "status": "completed",
  "new_attribute_suggestions": [
    {
      "id": "attr_001",
      "type": "http://example.org/Attribute",
      "name": {
        "@value": "owner_name",
        "@language": "en"
      },
      "definition": {
        "@value": "The legal name of the property owner",
        "@language": "en"
      },
      "explanation": {
        "@value": "Stores the full legal name as registered",
        "@language": "en"
      },
      "legal_act": {
        "id": "act_123",
        "type": "http://example.org/LegalAct",
        "official_number": "123/2023"
      },
      "occurences": [...],
      "references": [...]
    }
  ]
}
```

### Non-Legal Text Suggestions

#### Start Non-Legal Class Suggestions Job
```http
POST /nonlegal-text/class-suggestions-top-k-extraction-jobs
```

Starts an asynchronous job to extract class suggestions from a provided non-legal text.

**Header Parameters:**
- `text` (string): Non-legal text to process

**Request Body:** Same as Start Class Suggestions Job.

**Response (202 Accepted):** Same as Start Class Suggestions Job.

#### Get Non-Legal Class Suggestions Job Status
```http
GET /nonlegal-text/class-suggestions-jobs/{job_id}
```

Retrieves the status and results of a non-legal class suggestions job.

**Header Parameters:**
- `text` (string): Non-legal text to process

**Path Parameters:**
- `job_id` (UUID): Job identifier from the start response

**Response:** Same as Get Class Suggestions Job Status.

#### Start Non-Legal Property Suggestions Job
```http
POST /nonlegal-text/property-suggestions-top-k-extraction-jobs
```

Starts a job to extract attribute suggestions from a provided non-legal text for a specific class.

**Header Parameters:**
- `text` (string): Non-legal text to process

**Request Body:** Same as Start Property Suggestions Job.

**Response (202 Accepted):** Same as Start Property Suggestions Job.

#### Get Non-Legal Property Suggestions Job Status
```http
GET /nonlegal-text/property-suggestions-jobs/{job_id}
```

Retrieves the status and results of a non-legal attribute suggestions job.

**Header Parameters:**
- `text` (string): Non-legal text to process

**Path Parameters:**
- `job_id` (UUID): Job identifier from the start response

**Response:** Same as Get Property Suggestions Job Status.

#### Start Non-Legal Relationship Suggestions Job
```http
POST /nonlegal-text/relationship-suggestions-top-k-extraction-jobs
```

Starts a job to extract relationship suggestions from a provided non-legal text for a specific class.

**Header Parameters:**
- `text` (string): Non-legal text to process

**Request Body:**
```json
{
  "k": 10,
  "selected_class_id": "class_001",
  "structural_element_ids": ["§1", "§2"],
  "context_text": "Focus on ownership-related relationships",
  "known_conceptual_model": {
    "classes": [...],
    "relationships": [...]
  }
}
```

**Key Parameters:**
- `selected_class_id` (required): ID of the class for which to generate relationship suggestions
- Other parameters same as class suggestions

**Response (202 Accepted):**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440002",
  "selected_class_id": "class_001",
  "status": "in_progress"
}
```

#### Get Non-Legal Relationship Suggestions Job Status
```http
GET /nonlegal-text/relationship-suggestions-jobs/{job_id}
```

Retrieves the status and results of a non-legal relationship suggestions job.

**Header Parameters:**
- `text` (string): Non-legal text to process

**Path Parameters:**
- `job_id` (UUID): Job identifier from the start response

**Response:** Same as the relationship suggestions job status response.

### Feedback System

#### Record Accepted Suggestion
```http
POST /accepted-suggestions
```

Records that a user has accepted a specific suggestion.

**Request Body:**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "suggestion_id": "class_001"
}
```

**Response:** 204 No Content

#### Record Liked Suggestion
```http
POST /liked-suggestions
```

Records that a user likes a specific suggestion (positive feedback).

**Request Body:**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "suggestion_id": "class_001"
}
```

**Response:** 204 No Content

#### Record Disliked Suggestion
```http
POST /disliked-suggestions
```

Records that a user dislikes a specific suggestion (negative feedback).

**Request Body:**
```json
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "suggestion_id": "class_001"
}
```

**Response:** 204 No Content

## Data Models

### Core Concepts

#### LangString
Represents multilingual text content:
```json
{
  "@value": "Property Owner",
  "@language": "en"
}
```

#### LegalStructuralElement
Represents a paragraph or section in a legal document:
```json
{
  "id": "paragraph_1",
  "type": "http://example.org/Paragraph",
  "official_identifier": "§ 1"
}
```

#### LegalAct
Represents a legal document:
```json
{
  "id": "act_123",
  "type": "http://example.org/LegalAct",
  "official_number": "123/2023"
}
```

### Conceptual Model Structure

#### Class
```json
{
  "id": "class_001",
  "name": {"@value": "Person", "@language": "en"},
  "definition": {"@value": "A human being", "@language": "en"},
  "explanation": {"@value": "Represents individual persons", "@language": "en"},
  "isClass": true,
  "isSubjectOfLaw": true,
  "isObjectOfLaw": false,
  "isEvent": false,
  "isDocument": false,
  "ownsAttribute": [...],
  "specializes": [{"id": "parent_class"}]
}
```

#### Attribute
```json
{
  "id": "attr_001",
  "name": {"@value": "name", "@language": "en"},
  "definition": {"@value": "The name of the person", "@language": "en"},
  "explanation": {"@value": "Full legal name", "@language": "en"},
  "isAttribute": true
}
```

#### Relationship
```json
{
  "id": "rel_001",
  "name": {"@value": "owns", "@language": "en"},
  "definition": {"@value": "Ownership relationship", "@language": "en"},
  "explanation": {"@value": "Indicates ownership", "@language": "en"},
  "isRelationship": true,
  "mediatesClass": [
    {"id": "owner_class"},
    {"id": "property_class"}
  ]
}
```

## Error Handling

### Common HTTP Status Codes

- **200 OK**: Successful GET request
- **202 Accepted**: Job started successfully
- **204 No Content**: Successful POST with no response body
- **401 Unauthorized**: Invalid credentials
- **404 Not Found**: Job or resource not found
- **422 Unprocessable Entity**: Invalid request data
- **500 Internal Server Error**: Server or LLM provider error

### Error Response Format
```json
{
  "detail": "Error description"
}
```

### LLM-Related Errors

Jobs may fail due to:
- **Context Window Exceeded**: Requested parts (paragraphs) to be processed by an extraction job too large for LLM processing; Provided existing conceptual model too large for LLM processing
- **Rate Limits**: Too many requests to LLM provider
- **Content Policy**: Malicious or harmful context text (not implemented yet, but will be implemented in the future)
- **API Quota**: LLM provider quota exceeded

## Usage Recommendations

### For Frontend Developers

1. **Polling Strategy**: Poll job status endpoints every 1-2 seconds while `status` is `in_progress`

2. **Error Handling**: Implement retry logic for failed jobs, especially for rate limit errors

3. **Incremental Results**: Process `new_suggestions` as they arrive during polling

4. **Input Validation**: Validate `structural_element_ids` (the backend expects paragraph numbers of the given legal act to be processed) and `context_text` before sending requests

5. **Authentication**: Store credentials securely and include in all requests

### Performance Considerations

1. **Batch Processing**: Prefer processing specific paragraphs over entire legal acts
2. **Context Size**: Keep `context_text` concise to avoid token limits. In case of large conceptual model on the client's side, sending the whole model may also exceed token limits.
3. **Concurrent Jobs**: Limit concurrent jobs to avoid rate limiting
4. **Caching**: Cache job results to avoid redundant processing

### Best Practices

1. **Conceptual Model Evolution**: Provide existing conceptual models to get semantically consistent suggestions
2. **Feedback Loop**: Use acceptance/like/dislike endpoints to improve future suggestions
3. **Error Recovery**: Implement graceful degradation for LLM service interruptions

## Development Setup

### Prerequisites
- Python 3.11+
- API key for the configured LLM provider, unless using a local provider
- Environment variables configured

### Dependencies
Key dependencies include:
- `fastapi`: Web framework
- `any-llm-sdk`: LLM provider integration
- `pydantic`: Data validation
- `uvicorn`: ASGI server

### Environment Variables
```bash
LLM_PROVIDER=openai
LLM_MODEL=gpt-4.1
OPENAI_API_KEY=your-openai-api-key
USER_KEYS=[{"user_id": "user1", "password": "pass1"}]
DATA_DIRECTORY=optional-path-where-to-save-data
STATIC_DIRECTORY=optional-if-set-serve-static-content-from-given-directory
```

### Running the Server
```bash
pip install -r requirements.txt
uvicorn src.main:app --reload
```

The API will be available at `http://localhost:8000` with automatic OpenAPI documentation at `http://localhost:8000/docs`.

## Limitations

1. **Language Support**: Currently optimized for Czech legal documents
2. **LLM Dependency**: Requires an active configured LLM provider connection
3. **Binary Relationships**: Only supports binary relationships between classes
4. **Processing Time**: Variable response times based on document complexity
5. **Context Windows**: Limited by LLM provider context window sizes

## Support

For technical issues or questions about the API, refer to the OpenAPI documentation available at `/docs` when the server is running.
