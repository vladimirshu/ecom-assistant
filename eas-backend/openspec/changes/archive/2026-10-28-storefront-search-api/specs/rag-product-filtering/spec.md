## MODIFIED Requirements

### Requirement: Product filtering input
The product-filtering call SHALL receive the English search query, the customer's original message, the negative examples, the price constraint if any, and, for every retrieved candidate, its SKU, title, category, colors, sizes, price and description — and no product that was not retrieved.

#### Scenario: Negatives and candidates in prompt
- **WHEN** the analysis extracted negatives `["wool"]` and retrieval returned SKUs `JKT0001` and `JKT0007`
- **THEN** the product-filtering prompt contains `wool`, `JKT0001` and `JKT0007` and no other SKU

#### Scenario: Title and category in prompt
- **WHEN** retrieval returned `JKT0004` with title `Classic Bomber Jacket` and category `bomber`
- **THEN** the product-filtering prompt line for `JKT0004` contains `Classic Bomber Jacket` and `bomber`

### Requirement: End-to-end search response
For a successful search the system SHALL respond with HTTP 200 and a JSON body whose `products` list contains each returned product in the product representation defined by `product-catalog`, whose `interpretation` is the interpretation defined by `query-analysis-guardrail`, and whose `suggestedPrompts` is null.

#### Scenario: Full customer happy path
- **WHEN** a client calls `GET /search?message=blue cotton jacket under 200 euro, no wool&conversationId=c1`
- **THEN** the query is analyzed, candidates under 200 are retrieved with `wool` as a negative example, the filtering call selects a subset, and the client receives HTTP 200 with exactly that subset in retrieval order, each product having `title`, `category`, `colors` and `sizes` arrays and `imageUrl`, and an `interpretation` with `negativeExamples = ["wool"]` and price `LESS_THAN 200`
