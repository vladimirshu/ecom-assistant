## Why

`ValidationUtils` stacks regex blacklists (SQL keywords, XSS, URLs, HTML, `${}`) on top of a DTO whitelist. Most of these checks can never fire because the whitelist already rejects their trigger characters, and the ones that do fire produce false positives: the SQL pattern has no word boundaries and blocks `'` and `;`, so "I'd like", "alternative", "dropped shoulders" or "executive coat" are rejected. The whitelist also only allows Latin-1 letters, which blocks the non-English queries that the upcoming query-analysis step is meant to translate. Input HTML sanitization (OWASP) actively corrupts text (`M&S` → `M&amp;S`) before it reaches the LLM and embeddings, although this is a JSON API that renders no HTML.

The build is also currently broken for this work: `spring-ai-autoconfigure-model-openai` no longer brings the OpenAI model implementation onto the runtime classpath in Spring AI 2.0.2, and the Testcontainers 1.x artifact names no longer resolve under the Spring Boot 4 BOM.

## What Changes

- **BREAKING**: Search message validation becomes a single Unicode-aware whitelist constraint (`@SafeSearchText`) applied after NFKC normalization; letters from any script, apostrophes and common punctuation are accepted, `<` and `>` are no longer accepted.
- Remove the SQL-injection, XSS, URL, HTML-tag, `${}`/`javascript:`/`data:`, control-character and duplicate length checks from `ValidationUtils`.
- Remove OWASP HTML sanitization of user input; text is passed through unchanged apart from normalization.
- Remove the second, redundant validation of `conversationId` in the controller (the DTO constraint is authoritative) and the duplicated annotations on that field.
- Replace `ValidationUtils` with a small `TextNormalizer` (NFKC, whitespace collapse, trim) and a plain-text check for LLM-produced text used by the existing tool/advisor code until `query-analysis-guardrail` removes it.
- Fix exception mapping: LLM output failures always surface as `IllegalAssistantResponseException` (500), never as a 400 "invalid input".
- Delete the empty `InputSanitizationService`.
- Remove the `owasp-java-html-sanitizer` and `commons-validator` dependencies.
- Build prerequisites: replace `spring-ai-autoconfigure-model-openai` with `spring-ai-starter-model-openai`, migrate Testcontainers dependencies to the 2.x artifact names, and drop the explicit `MySQL8Dialect` if Hibernate 7 no longer provides it.

## Capabilities

### New Capabilities
- `search-input-validation`: rules for accepting, normalizing and rejecting the customer's search message and conversation id, and for the error response returned on rejection.

### Modified Capabilities
<!-- none: no baseline specs exist yet -->

## Impact

- Code: `dto/ProductSearchRequestDto`, `controller/ProductsController`, `utils/ValidationUtils` (replaced), new `validation/` package, `advisors/LlmOutputValidationAdvisor`, `tools/AssistantTools`, `exception/GlobalExceptionHandler`, `service/InputSanitizationService` (deleted).
- API: `GET /search` accepts a wider character set (any script, apostrophes) and rejects `<`/`>`; rejection still returns 400 with field errors.
- Dependencies: `-owasp-java-html-sanitizer`, `-commons-validator`, `spring-ai-autoconfigure-model-openai` → `spring-ai-starter-model-openai`, Testcontainers 2.x artifact names.
- Tests: first unit tests in the project (`src/test`).
