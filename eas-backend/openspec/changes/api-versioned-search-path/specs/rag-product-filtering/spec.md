## MODIFIED Requirements

### Requirement: End-to-end search response
For a successful search the system SHALL respond with HTTP 200 and a JSON body whose `products` list contains each returned product in the product representation defined by `product-catalog`, whose `interpretation` is the interpretation defined by `query-analysis-guardrail`, and whose `suggestedPrompts` is null.

#### Scenario: Full customer happy path
- **WHEN** a client calls `GET /api/v1/search?message=blue cotton jacket under 200 euro, no wool&conversationId=c1`
- **THEN** the query is analyzed, candidates under 200 are retrieved with `wool` as a negative example, the filtering call selects a subset, and the client receives HTTP 200 with exactly that subset in retrieval order, each product having `title`, `category`, `colors` and `sizes` arrays and `imageUrl`, and an `interpretation` with `negativeExamples = ["wool"]` and price `LESS_THAN 200`

#### Scenario: Old path is gone
- **WHEN** a client calls `GET /search?message=blue cotton jacket&conversationId=c1`
- **THEN** the response is HTTP 404 and no LLM or vector store call is made
