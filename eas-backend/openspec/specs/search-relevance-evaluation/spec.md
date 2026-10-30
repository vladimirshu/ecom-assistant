# search-relevance-evaluation Specification

## Purpose
TBD - created by syncing change seed-catalog-quality. Update Purpose after archive.

## Requirements
### Requirement: Judged scenario suite
A versioned suite SHALL contain at least 44 distinct scenarios covering the allocation in relevance-plan.md: 8 root/group searches, 12 positive attribute combinations, 8 exclusion cases, 4 paraphrased needs, 4 non-English cases, 4 multi-turn conversations and 4 intentional empty results. Each case SHALL record its text/turns, intended constraints, expected status, relevant/acceptable SKU judgments, prohibited near-miss SKUs, rationale and source catalog revision. Positive cases SHALL have at least one fact-verified eligible product; empty cases SHALL be valid apparel requests with no eligible product in the full catalog. Expectations SHALL NOT be fabricated from the search response being evaluated.

#### Scenario: Validate a positive case
- **WHEN** a new waterproof cycling query is added to the suite
- **THEN** its expected eligible products are checked against authored material/protection/use facts and its near misses include plausible products failing one of those requirements

#### Scenario: Validate an empty case
- **WHEN** a valid query has no eligible SKU under the complete authored facts
- **THEN** its fixture expects a successful empty response, not an off-topic rejection or irrelevant substitute

### Requirement: Held-out wording and traceability
At least one quarter of scenarios SHALL use held-out wording not used to generate or tune the descriptions/prompts. Authoring profiles, judgments and query-set revisions SHALL be fixed before the acceptance run. Any subsequent correction SHALL state its factual reason and invalidate the previous acceptance report for the changed revision. Reports SHALL include cases individually and SHALL NOT hide mandatory failures inside an aggregate score.

#### Scenario: Evaluation reveals a failure
- **WHEN** a held-out request retrieves a wrong product
- **THEN** the case is retained and diagnosed rather than silently removing it or copying its wording into the product description to claim a pass

### Requirement: Separate offline, candidate and end-to-end validation
Offline data-quality checks and deterministic fixture/fake-embedding tests SHALL be reported separately from real-model relevance evaluation. Candidate-stage evaluation SHALL use the actual embedding configuration and Qdrant retrieval path. End-to-end evaluation SHALL additionally use the actual query-analysis, conversation and filtering path with production-equivalent topK, thresholds and maximum-results settings. A fake hashed embedding or mocked selected-SKU response SHALL NOT establish semantic, multilingual or final-response relevance.

#### Scenario: Current unit/integration tests pass
- **WHEN** fake-embedding retrieval tests are green but the real-model suite has not run
- **THEN** the report marks pipeline correctness as tested and semantic relevance as not yet evaluated

### Requirement: Hard constraints and unknown facts
Every returned product in a judged constrained case SHALL satisfy the explicit price, audience, requested color/size, material exclusion and required functional constraints according to the authored facts and runtime-visible product fields. Unknown composition/feature facts SHALL NOT be counted as a confirmed match for an explicit exclusion or required property. A relevant-looking near miss SHALL remain incorrect when it violates a hard constraint. Literal word occurrence SHALL NOT alone decide whether an item contains wool, leather or animal down.

#### Scenario: Synthetic alternative
- **WHEN** a no-down request is evaluated and a synthetic-filled positive and an animal-down near miss are candidates
- **THEN** any returned animal-down item is a hard-constraint failure, and a correct synthetic item is judged by composition rather than the presence or absence of a marketing keyword

### Requirement: Acceptance measurements
For the fixed accepted catalog/query revisions, the real end-to-end acceptance run SHALL have zero hard-constraint violations, zero false-empty responses on mandatory positive cases and correct empty results for all intentional-empty cases. Across positive cases, mean precision at the first min(5, returned-count) results SHALL be at least 0.90 and Hit@3 SHALL be at least 0.90. For cases marked broad with at least three known eligible products, at least three valid products SHALL be returned so one-result precision cannot conceal missing coverage. Candidate-stage Hit@30, evaluated with the actual configured topK=30 baseline, SHALL be at least 0.95 across positive cases; changed topK SHALL be recorded and the metric named accordingly. All returned products, including ranks beyond five, SHALL be checked for hard violations.

Per-case output SHALL include ordered candidates/final SKUs, expected constraints and judgments, metrics, HTTP status, errors, stage failures, latency and available model/token usage. A report SHALL distinguish a measured pass from not-run, unavailable-provider, quota-limited or failed cases. Repeats SHALL follow the declared run policy in relevance-plan.md rather than silently choosing the best run.

#### Scenario: High precision but a material violation
- **WHEN** aggregate precision exceeds the target but one returned product contains excluded material
- **THEN** the acceptance run fails and identifies the violating SKU and case

#### Scenario: Rank exceeds result cap
- **WHEN** an expected product is absent from the retrieved candidates
- **THEN** the failure is diagnosed at retrieval rather than blamed only on final product filtering

### Requirement: Conversation and multilingual evaluation
Each conversation scenario SHALL retain one conversation id through its turns and verify accumulated and replaced constraints against expected interpretation and results. Equivalent multilingual cases SHALL preserve intended constraints and find acceptable products without requiring byte-identical English translations or an identical ranking among equally relevant items. Looking at historical UI snapshots SHALL remain outside backend evaluation and SHALL NOT be simulated as conversation rewinds.

#### Scenario: Refinement chain
- **WHEN** a conversation requests warm layers, excludes wool, lowers the budget and then requires a hood
- **THEN** later responses honor the applicable accumulated constraints and do not reintroduce excluded wool products

### Requirement: Opt-in reproducible real-model runs
Paid/external-provider evaluation SHALL be an explicit opt-in action against an isolated test catalog/revision, with an estimated request/model/token budget and declared repetition policy before running. It SHALL NOT run in ordinary offline unit tests or every build, modify the user's deployed catalog, weaken production quotas or leave unbounded retries. Fixed configuration, model/provider identifiers when available, input revisions and timestamps SHALL be recorded. Normalized interpretation/candidate/final-result traces SHALL be stored as bounded evaluation artifacts; this requirement SHALL NOT enable unrestricted production prompt/response debug logging.

#### Scenario: Provider unavailable
- **WHEN** the embedding or model provider cannot be called during a real evaluation
- **THEN** affected cases are marked unavailable/not-run and semantic acceptance is not reported as passed
