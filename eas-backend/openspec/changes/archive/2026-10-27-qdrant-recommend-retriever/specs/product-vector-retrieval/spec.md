## ADDED Requirements

### Requirement: Similarity retrieval
The retriever SHALL return at most `topK` product documents whose similarity to the query text is at least the configured similarity threshold, ordered by descending relevance when no ordering is requested.

#### Scenario: Relevant products returned
- **WHEN** the retriever is called with query text `waterproof nylon rain jacket` and no context keys
- **THEN** it returns documents whose descriptions match that text, most relevant first, at most `topK` of them

#### Scenario: Nothing above threshold
- **WHEN** no product reaches the similarity threshold
- **THEN** the retriever returns an empty list

### Requirement: Negative examples
The retriever SHALL use the strings in the `ecom_negative_examples` context key as negative examples, so that products similar to them rank lower than or are excluded relative to the same search without negatives.

#### Scenario: Wool products pushed down
- **WHEN** the retriever is called with query text `warm winter jacket` and negative examples `["wool"]`
- **THEN** products whose description is about wool rank lower than in the same search without negative examples

#### Scenario: Empty negatives
- **WHEN** the `ecom_negative_examples` key holds an empty list
- **THEN** the result equals the result of the search without that key

### Requirement: Metadata price filter
The retriever SHALL apply the filter expression in the `vector_store_filter_expression` context key, given either as a Spring AI `Filter.Expression` or as its string form, and SHALL return only documents satisfying it.

#### Scenario: Price upper bound
- **WHEN** the filter expression is `price <= 200`
- **THEN** every returned document has price less than or equal to 200

#### Scenario: Filter as string
- **WHEN** the filter expression is the string `price > 250`
- **THEN** every returned document has price greater than 250

#### Scenario: Invalid filter value type
- **WHEN** the filter expression context value is neither a string nor a `Filter.Expression`
- **THEN** the retriever fails with `IllegalArgumentException`

### Requirement: Lowest-price ordering
When the `ecom_sort_by_lowest_price` context key is `true`, the retriever SHALL select the relevant candidates as in the unsorted case and SHALL return them ordered by ascending price, with the ordering executed by the vector database.

#### Scenario: Cheapest first among relevant products
- **WHEN** the retriever is called with query text `rain jacket` and `ecom_sort_by_lowest_price = true`
- **THEN** the returned documents are the same set as without sorting and are ordered by ascending price

#### Scenario: Ordering combined with filter and negatives
- **WHEN** the retriever is called with a price filter, negative examples and `ecom_sort_by_lowest_price = true`
- **THEN** every returned document satisfies the filter and the documents are ordered by ascending price

### Requirement: Price payload index
The system SHALL ensure a float payload index on `price` exists in the products collection at application startup, and SHALL treat an already existing index as success.

#### Scenario: Restart with existing index
- **WHEN** the application starts and the index already exists
- **THEN** startup succeeds without error

### Requirement: Configurable retrieval and connection
The system SHALL read the Qdrant host, port and collection name, the retrieval `topK`, the similarity threshold and the sample-data import file from configuration properties.

#### Scenario: Container-provided Qdrant
- **WHEN** the application is started with Qdrant host and port pointing at a test container
- **THEN** products are imported into and retrieved from that container
