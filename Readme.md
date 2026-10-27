# AI-Powered E-Commerce Assistant

## Project Overview
The goal of this project is to build an AI assistant that allows customers to search and explore a product catalog using natural language questions (e.g., “Show me a red waterproof jacket under €100”).
The assistant combines semantic search with AI-based interpretation, clarification (“Can you show me the same jacket but in blue?”), and explanations.
This way customers can find products faster and more intuitively.

## Scope

### In Scope
* Uploading product catalog (CSV format)
* Natural language query processing
* AI-generated explanations of search results.
* Clarification loop when queries are incomplete or ambiguous.
* Web API endpoints (no large customer-facing UI, small demo UI is enough).
* Logging and metrics collection for evaluation.
* Compliance with EU AI Act and GDPR.

### Out of Scope
* Customer authentication and user accounts.
* Payment, checkout, cart, or order management.

## Repository Structure

| Directory | Project | Specifications |
|---|---|---|
| `backend/` | Spring Boot API (Gradle, Java): natural language search, LLM guardrails, vector search | `backend/openspec/` |
| `frontend/` | Storefront web app (Next.js, React, TypeScript) | `frontend/openspec/` |

`docker-compose.yml` in the repository root starts the shared infrastructure (MySQL, Qdrant).

## Running Locally

```bash
docker compose up -d                       # MySQL + Qdrant
cd backend && OPEN_AI_TOKEN=... ./gradlew bootRun   # API on http://localhost:8080
cd frontend && npm install && npm run dev  # storefront on http://localhost:3000
```
