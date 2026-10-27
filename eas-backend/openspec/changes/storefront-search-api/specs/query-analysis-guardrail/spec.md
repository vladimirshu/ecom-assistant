## ADDED Requirements

### Requirement: Interpretation returned to the client
For a successful search the system SHALL include in the response an `interpretation` object derived from the validated query analysis, containing `searchQuery` (English), `negativeExamples` (array, possibly empty), `priceConstraint` (`{operator, value}` or null, operator one of `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`) and `sortByLowestPrice` (boolean), and SHALL NOT include the rejection reasons.

#### Scenario: Full interpretation
- **WHEN** the analysis of `blue cotton jacket under 200 euro, no wool` returns `searchQuery = "blue cotton jacket"`, negatives `["wool"]`, price `LESS_THAN 200` and `sortByLowestPrice = false`
- **THEN** the response contains `interpretation = {"searchQuery": "blue cotton jacket", "negativeExamples": ["wool"], "priceConstraint": {"operator": "LESS_THAN", "value": 200}, "sortByLowestPrice": false}`

#### Scenario: No constraints
- **WHEN** the analysis returns no price constraint and no negatives
- **THEN** `interpretation.priceConstraint` is null and `interpretation.negativeExamples` is `[]`

#### Scenario: Empty result still interpreted
- **WHEN** retrieval returns no candidates
- **THEN** the response is HTTP 200 with `products: []` and a non-null `interpretation`

#### Scenario: Conversation refinement
- **WHEN** a client sends `warm winter jacket` and then `same but in navy` with the same conversation id, and the second analysis returns `searchQuery = "warm navy winter jacket"`
- **THEN** the second response's `interpretation.searchQuery` is `warm navy winter jacket`

### Requirement: Suggested prompts in the customer's language
When a request is rejected, the suggested prompts SHALL be written in the language of the customer's latest message; the generic fallback prompt SHALL be in English.

#### Scenario: Non-English rejection
- **WHEN** a client sends `зимняя куртка для лета` and the analysis rejects it as contradictory
- **THEN** the query-analysis prompt instructs the model to write suggested prompts in the language of the latest customer message, and the HTTP 422 response contains the model's suggested prompts unchanged

#### Scenario: Fallback prompt
- **WHEN** the analysis returns `allowed = false` with an empty `suggestedPrompts` list for a non-English message
- **THEN** the response is HTTP 422 with the English generic suggested prompt
