# Search relevance plan

This document specifies the benchmark to implement; it does not claim that current or proposed products have already passed it. Dataset authoring and real-model evaluation are separate tasks.

## Case allocation

| Scenario group | Minimum cases | Examples / required distinctions |
|---|---:|---|
| Root + main garment group | 8 | Men/Women × jackets/coats, sweaters/hoodies, jeans, trousers |
| Positive attribute combinations | 12 | Weather + activity; fit + stretch; season + material; color/size + budget; office vs casual/travel |
| Explicit exclusions | 8 | No wool/animal fibers, no down, no leather, no hood; combine a functional need with an exclusion and price |
| Paraphrased needs | 4 | Dress for a rainy bike commute; stay warm standing outdoors; comfortable trousers for a long trip; smart but flexible office clothing |
| Non-English | 4 | German, Russian and French equivalents retaining the actual material/function/budget intent |
| Conversations | 4 | Several turns preserving/replacing material, color, budget and hood/fit constraints |
| Intentional empty | 4 | Valid clothing requests with a fact-verified impossible catalog combination |
| Total | 44 | Conversation cases contain multiple API requests |

At least 11 cases (one quarter of 44; round up for larger suites) must have held-out wording not reused in description generation or prompt tuning. Keep cases representative of ordinary users, including need-based language rather than only product category names. Do not assert that the system covers most real-world shoppers based on this small suite.

## Example query families

These are authoring/coverage recipes, not final judged SKU fixtures. Populate concrete SKU judgments only after reviewing the actual revised catalog.

| Request or sequence | Facts that must support a positive | Useful near miss |
|---|---|---|
| A light jacket for cycling to work in heavy rain, under €150 | Waterproof protection, ventilation/cycling suitability, low weight, price <150 | Water-resistant-only jacket; waterproof but too expensive |
| Something warm for winter walks without wool | Explicit insulation/material composition, warm outdoor use, no excluded animal fiber | Wool/cashmere blend; unknown composition; thin uninsulated shell |
| A warm puffer without down | Known synthetic fill and appropriate warmth | Animal-down puffer; ambiguous eco-down fill |
| A leather-free jacket for a casual evening | Complete relevant material facts and casual use | Genuine leather biker jacket; otherwise similar garment with unknown trim |
| Women's relaxed jeans with stretch, up to €90 | Women membership, relaxed cut, stretch, price <=90 | Men's-only match; rigid denim; €90.01 item |
| Men's smart trousers for a long train journey | Men membership, office-appropriate cut, comfortable stretch/travel suitability | Rigid formal trousers; casual denim with similar color |
| A lightweight sweater for summer evenings, no wool | Lightweight known cotton/linen/synthetic profile, evening layering | Heavy winter knit; wool-rich sweater |
| warm layers → without wool → under €100 → only with a hood | Eligible subset after each turn; explicit hood and composition; accumulated price/exclusion | Earlier wool item reappearing; hoodless cheaper sweater |
| navy jeans → actually black → up to €80 | Latest color replaces earlier color; price bound accumulates | Keeping navy despite replacement; exceeding budget |
| Eine leichte Regenjacke zum Radfahren unter 150 Euro | Same intent as its English judgment family | Literal keyword overlap without actual rain/cycling suitability |
| Тёплый свитер без шерсти до 80 евро | Known wool-free sweater, warmth, correct price operator recorded in case | Cashmere/wool near miss; too expensive cotton sweater |
| Un pantalon confortable pour le bureau, avec de l'élasticité | Office trousers and stretch, not just any comfortable casual garment | Comfortable jeans that do not meet the office-style intent |

An actual empty-case example can be a valid request for a leather jacket in a specific color/size under a very low price, but only after the full dataset oracle verifies zero eligible products. Do not manufacture ambiguity or impossible garment contradictions that the guardrail would reasonably reject. Guardrail rejection is a distinct HTTP status outcome.

## Fixture fields

Proposed test input: `src/test/resources/relevance/seed-search-cases.json`. Each case has:

- id, family, split (development/held-out), language and source catalog revision;
- query or ordered turns, expected HTTP status and expected allowed/rejected outcome;
- explicit hard constraints and any required interpretation changes;
- fact-verified eligible/relevant SKUs, acceptable alternatives and prohibited near-miss SKUs with reasons;
- relevance judgments for expected top results (highly relevant / acceptable / irrelevant), judged before running the pipeline;
- whether it is broad (at least three eligible products), positive or intentional-empty;
- notes explaining unknown properties and how exclusion semantics were judged.

Descriptions, facts and expected SKU judgments must be consistent. Derive numeric/material/feature constraints from authoring facts; review soft occasion/style relevance explicitly. Do not label a product relevant merely because a model returned it. Store complete fact-derived eligibility for hard constraints; any unjudged returned product must be reviewed or conservatively marked unresolved, not automatically counted correct.

## Measurements and release gates

1. **Offline:** dataset schema/factual consistency/diversity/coverage checks. No model credentials needed. This does not measure semantic retrieval.
2. **Candidate stage:** actual configured embedding model and Qdrant retriever, including negative recommendations and price metadata filters. With baseline topK=30/threshold=0.15, Candidate Hit@30 is the proportion of positive cases having at least one judged relevant candidate. Target >=0.95. This is a hit metric, not recall over every possible relevant item, which can exceed the candidate limit.
3. **End-to-end:** actual GET /api/v1/search pipeline, query interpretation, filtering and conversation history, maxResults=10 baseline. Mean P@min(5,n) >=0.90; a false-empty positive contributes zero. Hit@3 >=0.90. Zero hard-constraint violations among all returned results, zero false-empty mandatory positive cases, and every intentional-empty case returns HTTP 200/products=[]. Broad cases need at least three correct results where three or more are known eligible.
4. **Conversation:** evaluate each turn's applicable constraints and replacement semantics, not only the final turn. Price ordering is checked only when requested; do not demand exact first-place ranking among equally relevant alternatives.
5. **Multilingual:** assess equivalent intent/eligible products; do not require identical English wording or byte-identical product order across languages.

These are proposed acceptance gates, not scores already achieved. If they fail, diagnose the cause and record unresolved issues; do not lower a threshold or rewrite judgments without an explicit documented rationale.

## Run policy, cost and diagnostics

Offline validators and deterministic pipeline tests run normally. Real provider evaluation is a separate explicit opt-in command/profile, with endpoint, model configuration and an isolated complete catalog revision recorded. Do not use the deployed user collection as a scratch database.

Before a real run, enumerate the total API requests including conversation turns, estimate query-analysis/filter/schema-retry and embedding calls, and set bounded retries/usage. A successful allowed request with candidates normally has two LLM calls; empty/rejected requests differ. Vector import/embedding cost must also be counted when preparing the test collection. Keep production rate/budget controls intact; use separately configured test limits if needed rather than silently weakening live limits.

Initial acceptance requires two independent complete passes with fresh conversation ids and fixed dataset/queries/configuration. Each pass must meet the gates; report differences instead of selecting only the best pass. Additional repeats are for diagnosis and are explicitly declared. If budget/provider availability prevents both passes, report incomplete evaluation rather than a measured pass.

For each case/turn record normalized interpretation, candidate SKUs/ranks/scores when available, final SKUs, expected facts, failure reasons, status, latency and available token/model usage. Classify failures:

- Dataset: no eligible product, missing/contradictory facts or poor descriptions.
- Analysis: rejected valid intent, mistranslated constraint, missing/replaced conversation state.
- Retrieval: eligible items exist but do not enter candidates or are wrongly demoted by a contextual negative.
- Filtering: eligible candidates dropped, or hard-negative candidates selected.
- Ordering/output: selected products reordered incorrectly, result cap/serialization/price comparisons mishandled.
- Infrastructure: quota, timeout, provider/store unavailable or wrong catalog revision.

Do not fix retrieval/filtering failures by making false product claims, raising every item's feature count, stuffing query words into descriptions, secretly adding oracle facts to the model prompt or silently widening candidate limits. Any required algorithm/prompt/runtime contract change belongs to its owning OpenSpec change, with this fixed suite retained as regression evidence. Evaluation traces are bounded test artifacts, not a request to enable unrestricted production prompt logging.
