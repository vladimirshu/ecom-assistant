# rag-product-filtering Specification

## Purpose
TBD - created by syncing change rag-product-filtering. Update Purpose after archive.
## Requirements
### Requirement: Two LLM calls per successful search
For an allowed search request with at least one retrieved candidate, the system SHALL make exactly two LLM calls: the query analysis, then the product filtering over the retrieved candidates. Schema-validation retries are excluded from this count.

#### Scenario: Happy path call count
- **WHEN** a client sends `blue cotton jacket under 200 euro, no wool` and retrieval returns candidates
- **THEN** exactly one query-analysis call and one product-filtering call are sent to the LLM provider, in that order

#### Scenario: Rejected request
- **WHEN** the query analysis rejects the request
- **THEN** no product-filtering call is made and the response is HTTP 422

#### Scenario: Empty retrieval
- **WHEN** retrieval returns no candidates
- **THEN** no product-filtering call is made and the response is HTTP 200 with an empty `products` list

### Requirement: Product filtering input
The product-filtering call SHALL receive the English search query, the customer's original message, the negative examples, the price constraint if any, and, for every retrieved candidate, its SKU, title, category, colors, sizes, price and description — and no product that was not retrieved.

#### Scenario: Negatives and candidates in prompt
- **WHEN** the analysis extracted negatives `["wool"]` and retrieval returned SKUs `JKT0001` and `JKT0007`
- **THEN** the product-filtering prompt contains `wool`, `JKT0001` and `JKT0007` and no other SKU

#### Scenario: Title and category in prompt
- **WHEN** retrieval returned `JKT0004` with title `Classic Bomber Jacket` and category `bomber`
- **THEN** the product-filtering prompt line for `JKT0004` contains `Classic Bomber Jacket` and `bomber`

### Requirement: Only retrieved SKUs are returned
The system SHALL return only products whose SKU was both retrieved and selected by the product-filtering call; selected SKUs that were not retrieved SHALL be discarded and logged, and duplicate SKUs SHALL be returned once.

#### Scenario: Hallucinated SKU
- **WHEN** retrieval returned `JKT0001` and `JKT0007` and the product-filtering call selects `JKT0007` and `JKT9999`
- **THEN** the response contains only `JKT0007`

#### Scenario: Duplicate SKU
- **WHEN** the product-filtering call selects `JKT0001` twice
- **THEN** the response contains `JKT0001` once

### Requirement: Response order follows retrieval order
The system SHALL return the selected products in the order in which they were retrieved, regardless of the order of SKUs in the product-filtering output.

#### Scenario: Lowest price preserved
- **WHEN** the customer asks for the cheapest rain jacket, retrieval returns `JKT0003 (90)`, `JKT0001 (120)`, `JKT0005 (150)` and the filtering call selects `JKT0005`, `JKT0003`
- **THEN** the response lists `JKT0003` then `JKT0005`

### Requirement: Result size cap
The system SHALL return at most `ecom.search.max-results` products.

#### Scenario: More selected than allowed
- **WHEN** `max-results` is 10 and 14 retrieved products are selected
- **THEN** the response contains the first 10 of them in retrieval order

### Requirement: Product filtering failure
The system SHALL respond with HTTP 500 and error `Illegal assistant response` when the product-filtering output does not match the `ProductSelection` schema after the configured retries.

#### Scenario: Persistent invalid output
- **WHEN** every product-filtering response is not valid `ProductSelection` JSON
- **THEN** the response is HTTP 500 with error `Illegal assistant response` and no products

### Requirement: End-to-end search response
For a successful search the system SHALL respond with HTTP 200 and a JSON body whose `products` list contains each returned product in the product representation defined by `product-catalog`, whose `interpretation` is the interpretation defined by `query-analysis-guardrail`, and whose `suggestedPrompts` is null.

#### Scenario: Full customer happy path
- **WHEN** a client calls `GET /api/v1/search?message=blue cotton jacket under 200 euro, no wool&conversationId=c1`
- **THEN** the query is analyzed, candidates under 200 are retrieved with `wool` as a negative example, the filtering call selects a subset, and the client receives HTTP 200 with exactly that subset in retrieval order, each product having `title`, `category`, `colors` and `sizes` arrays and `imageUrl`, and an `interpretation` with `negativeExamples = ["wool"]` and price `LESS_THAN 200`

#### Scenario: Old path is gone
- **WHEN** a client calls `GET /search?message=blue cotton jacket&conversationId=c1`
- **THEN** the response is HTTP 404 and no LLM or vector store call is made
