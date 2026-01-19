# Spring AI Recursive Language Models (RLM)

A Spring Boot microservice that implements Recursive Language Models (RLMs) with Spring AI. It decomposes complex problems into sub-problems, recursively solves them, and aggregates results into a final answer.

Status: experimental. API and internal interfaces may change.
**It does not have a sandbox for running Python code ot shell script. It is recommended to run it on a separate virtual machine for security reasons. Running generated code by the LLM model is risky on your personal machine.**

See documentation.md for a deeper architectural overview.

## Features

- Recursive problem decomposition and solving
- Multiple recursion strategies (depth-first, breadth-first)
- Configurable recursion depth and branching
- Thought process tracking and metrics
- Caffeine caching support
- RESTful API with JSON and multipart/form-data
- Pluggable model providers (OpenAI, Anthropic, Google Gemini) via Spring AI
- Docker support

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6+
- At least one LLM API key:
  - OpenAI: OPENAI_API_KEY
  - Anthropic: ANTHROPIC_API_KEY
  - Google Gemini: GEMINI_API_KEY
- Docker (optional)

### 1) Clone and build

```bash
git clone <this-repo>
cd spring-ai-rlm
git checkout rlm
mvn -q -DskipTests package
```

### 2) Configure a model provider

This project wires a ChatClient based on the first available ChatModel bean, in this preference order:
OpenAI > Anthropic > Google Gemini.
If no provider is available, the app will fail with:
"No ChatModel bean available..."

Enable exactly one provider and set its API key. Examples:

- OpenAI:
  - Environment:
    - OPENAI_API_KEY=your-api-key
  - application.properties:
    - spring.ai.openai.enabled=true
    - spring.ai.anthropic.enabled=false
    - spring.ai.google.genai.enabled=false

- Anthropic (default in repo):
  - Environment:
    - ANTHROPIC_API_KEY=your-api-key
  - application.properties:
    - spring.ai.anthropic.enabled=true
    - spring.ai.openai.enabled=false
    - spring.ai.google.genai.enabled=false

- Google Gemini:
  - Environment:
    - GEMINI_API_KEY=your-api-key
  - application.properties:
    - spring.ai.google.genai.enabled=true
    - spring.ai.openai.enabled=false
    - spring.ai.anthropic.enabled=false

You may also set model names and temperatures:
- OpenAI: spring.ai.openai.chat.options.model, spring.ai.openai.chat.options.temperature
- Anthropic: spring.ai.anthropic.chat.options.model, spring.ai.anthropic.chat.options.temperature
- Google: spring.ai.google.genai.chat.options.model, spring.ai.google.genai.chat.options.temperature

Note: If you enable multiple providers, the app will pick OpenAI first if configured, otherwise Anthropic, otherwise Google.

### 3) Run

```bash
mvn spring-boot:run
# or
java -jar target/spring-ai-rlm-*.jar
```

Health check:
```bash
curl -s http://localhost:8080/api/v1/rlm/health
```

## API

Base path: /api/v1/rlm

- POST /solve (application/json)
- POST /solve (multipart/form-data) — supports uploading a context file
- GET /health

### Request model (JSON)

RlmRequest fields:
- problem (string, required, non-blank)
- maxDepth (int, default 3, 1..5)
- maxBranching (int, default 3, 1..5)
- strategy (string, default "depth-first", allowed: "depth-first"|"breadth-first")
- verbose (boolean, default false)
- context (string, optional) — additional inline context

Validation errors are returned as HTTP 400 for multipart route when problem is blank. Other errors are returned as HTTP 500 with an error message body.

### Examples (JSON)

```bash
curl -X POST http://localhost:8080/api/v1/rlm/solve \
  -H "Content-Type: application/json" \
  -d '{
    "problem": "Calculate the sum of all prime numbers between 1 and 100",
    "maxDepth": 3,
    "maxBranching": 3,
    "strategy": "depth-first",
    "verbose": true
  }'
```

```bash
curl -X POST http://localhost:8080/api/v1/rlm/solve \
  -H "Content-Type: application/json" \
  -d '{
    "problem": "Design a scalable microservices architecture for an e-commerce platform that handles 1 million users",
    "maxDepth": 4,
    "maxBranching": 4,
    "strategy": "depth-first",
    "verbose": true,
    "context": "The platform needs to support product catalog, user management, orders, and payments"
  }'
```

```bash
curl -X POST http://localhost:8080/api/v1/rlm/solve \
  -H "Content-Type: application/json" \
  -d '{
    "problem": "Plan a 7-day trip to Japan covering Tokyo, Kyoto, and Osaka with a budget of $3000",
    "maxDepth": 3,
    "maxBranching": 3,
    "strategy": "breadth-first",
    "verbose": false
  }'
```

### Example (multipart/form-data with context file)

```bash
curl -X POST http://localhost:8080/api/v1/rlm/solve \
  -H "Accept: application/json" \
  -F "problem=Summarize this document and propose next steps" \
  -F "maxDepth=3" \
  -F "maxBranching=3" \
  -F "strategy=depth-first" \
  -F "verbose=true" \
  -F "context=@./my-context.txt;type=text/plain"
```

## Configuration

application.properties contains sensible defaults. You can override any property via environment variables or Spring profiles.

Relevant keys:

- Server:
  - server.port=8080
- Caching:
  - spring.cache.type=caffeine
  - spring.cache.caffeine.spec=maximumSize=100,expireAfterWrite=3600s
- Logging:
  - logging.level.org.springframework.ai=INFO
  - logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} - %msg%n
- RLM:
  - rlm.max-depth (default 3 or 5 depending on file section; last definition wins)
  - rlm.max-branching (default 3)
  - rlm.timeout-seconds
  - rlm.execution-timeout-seconds
  - rlm.enable-caching
  - Temperatures:
    - rlm.decomposition-temperature
    - rlm.solving-temperature
    - rlm.aggregation-temperature
- Security (RlmSecurityConfig):
  - rlm.security.allow-network=false
  - rlm.security.allow-file-system=true
  - rlm.security.max-file-size-mb=10
  - rlm.security.allowed-commands[0]=python3
  - rlm.security.allowed-commands[1]=bash

Model provider toggles:

- spring.ai.openai.enabled=[true|false]
- spring.ai.anthropic.enabled=[true|false]
- spring.ai.google.genai.enabled=[true|false]

API keys can be supplied as:
- spring.ai.openai.api-key or OPENAI_API_KEY
- spring.ai.anthropic.api-key or ANTHROPIC_API_KEY
- spring.ai.google.genai.api-key or GEMINI_API_KEY

## Architecture

- RlmController: REST endpoints and request handling
- RlmService: maps API request to core request and orchestrates response
- DefaultRlmClient: selects recursion strategy, builds context, invokes execution
- RecursionStrategy: traversal policy (DepthFirstRecursion, BreadthFirstRecursion)
- RecursiveThinkingService: base/decompose/solve/aggregate logic interacting with the LLM
- See documentation.md for detailed sequence diagrams and component breakdown

## Docker

Example commands (adjust paths as needed):

```bash
# Build image
docker build -f src/Dockerfile -t spring-ai-rlm:latest .

# Run container (provide API key(s))
docker run --rm -p 8080:8080 \
  -e OPENAI_API_KEY=your-api-key \
  -e SPRING_AI_OPENAI_ENABLED=true \
  -e SPRING_AI_ANTHROPIC_ENABLED=false \
  -e SPRING_AI_GOOGLE_GENAI_ENABLED=false \
  spring-ai-rlm:latest
```

If using the provided compose file:

```bash
docker compose -f src/docker-compose.yml up --build
```

## Testing

```bash
mvn test
```

## Troubleshooting

- No ChatModel bean available:
  - Enable exactly one provider and set the corresponding API key.
  - The app picks OpenAI > Anthropic > Google if multiple are enabled.
- 500 Error "Error: ...":
  - Review logs; check API keys, provider enable flags, and network access.
- Multipart uploads:
  - Use the "context" form part for content files. Ensure text/plain if unsure.
- Port already in use:
  - Set server.port to a free port.
