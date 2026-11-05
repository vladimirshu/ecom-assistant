# AI-Powered E-Commerce Assistant

## Project Overview
An apparel discovery demo that lets customers browse a catalog and search in natural language, for example, “Show me a red waterproof jacket under €100.” Customers can refine a search conversationally, such as “the same jacket in blue.”

The assistant interprets the request, retrieves candidates from Qdrant vector DB using semantic search and price constraints, and uses an LLM to select matching products. The storefront shows the interpreted search and product details. The catalog is seeded from CSV.

## User workflow

1. The customer submits a natural-language search. The backend normalizes and validates the input.
2. An LLM checks the request against the security guardrail and interprets the search, using conversation history for follow-ups. Rejected requests receive suggested prompts.
3. Qdrant retrieves relevant catalog candidates using the interpreted query, price constraints, and negative examples.
4. An LLM selects matching products from those candidates. The backend validates its output and returns only retrieved products, along with the search interpretation.
5. The customer reviews the results and product details, then can refine the search or start a new one.

## Security guardrails

- Search input and conversation IDs are validated before processing.
- An LLM-based guardrail checks whether requests are relevant to apparel search and rejects requests it identifies as irrelevant, contradictory, or seeking internal information. Its structured output is also validated.
- Product selection is limited to retrieved catalog items; unknown or duplicate product IDs are discarded.
- Per-client rate limits, a daily search budget, and LLM output-token limits help control abuse and costs. The rate limits and budget are in memory and reset on restart; see the [backend README](eas-backend/README.md) for limitations and the external spending cap.

The demo does not include customer accounts, authentication, cart, checkout, or order management. LLM-based guardrails can make mistakes and do not guarantee security.

## Repository Structure

| Directory | Project | Specifications |
|---|---|---|
| `eas-backend/` | Spring Boot API (Gradle, Java): natural language search, guardrails, vector search | `eas-backend/openspec/` |
| `eas-frontend/` | Storefront web app (Vite, React, TypeScript) | `eas-frontend/openspec/` |

`docker-compose.yml` in the repository root starts the full stack: the Spring Boot
backend, the frontend (served by nginx), and Qdrant.

## Running Locally

### Full stack with Docker Compose

```bash
cp .env.example .env   # then set OPEN_AI_TOKEN
docker compose up --build
```

| Service  | URL                          |
|----------|------------------------------|
| frontend | http://localhost:3000        |
| backend  | http://localhost:8080        |
| Qdrant   | http://localhost:6333 (REST) / :6334 (gRPC) |

### Individual services (development)

```bash
docker compose up -d qdrant                     # vector DB only
cd eas-backend && OPEN_AI_TOKEN=... ./gradlew bootRun   # API on http://localhost:8080
cd eas-frontend && npm install && npm run dev   # storefront on http://localhost:3000
```
