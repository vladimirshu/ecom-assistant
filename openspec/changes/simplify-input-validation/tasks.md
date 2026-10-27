## 1. Build prerequisites

- [x] 1.1 Replace `spring-ai-autoconfigure-model-openai` with `spring-ai-starter-model-openai` and confirm `spring-ai-openai` is on `runtimeClasspath`
- [x] 1.2 Migrate Testcontainers dependencies to the 2.x artifact names managed by the Spring Boot 4 BOM and confirm `testRuntimeClasspath` resolves
- [x] 1.3 Remove `owasp-java-html-sanitizer` and `commons-validator` dependencies
- [x] 1.4 Remove the explicit `hibernate.dialect: MySQL8Dialect` property if Hibernate 7 no longer ships it

## 2. Normalization and constraint

- [x] 2.1 Add `validation/TextNormalizer` (NFKC, whitespace collapse, trim; null-safe)
- [x] 2.2 Add `validation/SafeSearchText` annotation and `SafeSearchTextValidator` implementing the whitelist and 3–500 length rule
- [x] 2.3 Update `ProductSearchRequestDto`: `@SafeSearchText` on `message`, normalizing `setMessage`, remove the duplicated `conversationId` annotations

## 3. Remove legacy validation

- [x] 3.1 Add `validation/PlainTextPolicy.requirePlainText(text, maxLength)` throwing `IllegalAssistantResponseException`
- [x] 3.2 Switch `LlmOutputValidationAdvisor` and `AssistantTools` from `ValidationUtils` to `PlainTextPolicy`
- [x] 3.3 Remove the `ValidationUtils` calls from `ProductsController`
- [x] 3.4 Delete `ValidationUtils` and the empty `InputSanitizationService` (and its controller injection)
- [x] 3.5 Remove the `SecurityException` handler from `GlobalExceptionHandler`

## 4. Tests

- [x] 4.1 Unit tests for `TextNormalizer` (full-width, whitespace, null/blank)
- [x] 4.2 Unit tests for `SafeSearchTextValidator` covering every scenario in `search-input-validation` (apostrophe, former SQL keywords, Cyrillic, `<>`, length bounds, `&` preserved)
- [x] 4.3 Unit tests for `PlainTextPolicy`
- [x] 4.4 `@WebMvcTest` for `/search` asserting 400 field-error responses and that the `ChatClient` is never invoked on invalid input
- [x] 4.5 Run `./gradlew test` green
