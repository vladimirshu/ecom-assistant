## Context

The `/search` endpoint binds `ProductSearchRequestDto` via `@ModelAttribute` and validates it with Bean Validation (`@Pattern`, `@Size`). The controller then calls `ValidationUtils.validateAndSanitize` on both fields, which re-runs a series of regex blacklists and HTML-sanitizes the text. The same `validate` method is reused for LLM output (`LlmOutputValidationAdvisor`, tool arguments in `AssistantTools`).

Findings from review:

| Check in `ValidationUtils` | Status |
|---|---|
| `SQL_INJECTION_PATTERN` | False positives (no word boundaries, blocks `'` and `;`); JPQL queries are parameterized, so it protects nothing |
| `XSS_PATTERN`, `javascript:`, `data:`, URL regex, `${…}` | Unreachable for user input: the whitelist rejects `:`, `/`, `{` |
| HTML tag / `<>` check | Only reachable because the DTO whitelist allows `<>` |
| Control characters | Already covered by the whitelist |
| Length ≤ 1000 | Duplicates `@Size(max = 500)` with a different limit |
| OWASP `sanitize` | Corrupts text (`&` → `&amp;`); no HTML is rendered by this service |
| `ALPHANUMERIC_VALIDATOR` | Unused |

Bugs: `validateAssistantMessage` catches `IllegalArgumentException` but the XSS branch throws `SecurityException`; `validate(null)` throws NPE because regexes run before the empty check; `String.matches(".*…")` without DOTALL lets multi-line input bypass checks; `io.micrometer.common.util.StringUtils` is imported instead of commons-lang3.

## Goals / Non-Goals

**Goals:**
- One authoritative, declarative validation rule for the search message, defined on the DTO.
- Accept natural-language queries in any script, including apostrophes.
- Normalize Unicode (NFKC) before validation so full-width and compatibility characters cannot sneak past the whitelist.
- Keep the existing tool/advisor code working with a minimal plain-text check until `query-analysis-guardrail` deletes it.
- Get the build and test classpath into a working state.

**Non-Goals:**
- LLM-based guardrails (covered by `query-analysis-guardrail`).
- Output encoding for HTML clients; the API returns JSON and clients are responsible for escaping.
- Rate limiting, authentication, conversation-id ownership.

## Decisions

**1. Custom constraint `@SafeSearchText` instead of a utility class.**
A `ConstraintValidator` keeps the rule next to the field and produces the standard `MethodArgumentNotValidException` → 400 field-error response. Rejected alternative: keeping a static utility called from the controller — that is what produced the duplicated, diverging rules.

**2. Whitelist:** `^[\p{L}\p{M}\p{N}\s'’"\-_.,!?()€$£¥%/&+]+$` after normalization, length 3–500.
`\p{L}\p{M}` covers all scripts including combining marks; `:` stays excluded (no URLs/schemes needed in a product search); `<>{}[];` are excluded. Rejected alternative: a blacklist — every previous blacklist entry was either unreachable or a false positive.

**3. Normalization happens in the DTO setter.**
`setMessage(String)` stores `TextNormalizer.normalize(value)` (NFKC → collapse whitespace → trim), so Bean Validation runs on exactly the text that is later sent to the LLM. Spring's `@ModelAttribute` binding uses setters, so no binder customization is needed. Rejected alternative: `@InitBinder` with a custom `PropertyEditor` — more indirection for a single field.

**4. No third-party library needed.**
`java.text.Normalizer` covers normalization, Jakarta Bean Validation covers the rule. OWASP HTML sanitizer and commons-validator become unused and are removed. commons-lang3 remains (used elsewhere).

**5. Interim LLM output check.**
`PlainTextPolicy.requirePlainText(String, int maxLength)` (non-blank, no control characters except whitespace, length cap) replaces `validateAssistantMessage` and the `validateAndSanitize` calls in `AssistantTools`. It throws `IllegalAssistantResponseException`, mapped to 500. It is intentionally small because `query-analysis-guardrail` replaces this code path with structured output.

**6. Exception handler cleanup.**
The `SecurityException` handler is removed (nothing throws it any more). `IllegalArgumentException` → 400 is kept for binding/argument errors.

**7. Build prerequisites in this change.**
They block any test from running, and this is the first change that adds tests. `spring-ai-starter-model-openai` brings `spring-ai-openai` (the `ChatModel`/`EmbeddingModel` implementation) that the bare autoconfigure module only declares optionally. Testcontainers 2.x renamed modules (`testcontainers-mysql`, `testcontainers-junit-jupiter`); the exact names are verified against the Boot 4 BOM during implementation.

## Risks / Trade-offs

- [Wider character set reaches the LLM] → Prompt-injection defence moves to the LLM guardrail in `query-analysis-guardrail`; the whitelist was never an effective injection defence (injection is plain English).
- [Clients relying on `<`/`>` being accepted] → Low risk; these were accepted by the DTO but then rejected by `ValidationUtils`, so no request containing them ever succeeded.
- [NFKC changes some characters (e.g. `ﬁ` → `fi`, full-width digits → ASCII)] → Desired for search; embeddings and LLM handle the normalized form better.
- [Hibernate 7 dialect removal] → If `MySQL8Dialect` is gone, remove the property and let Hibernate auto-detect; verified by application context startup in tests.
