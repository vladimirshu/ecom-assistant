## Context

The configured production seed has 100 unique SKUs/titles, 20 description strings and 13 jacket/coat categories. Ninety-five rows belong to repeated-description groups. Source and exact groups are frozen in seed-audit.md and audit/seed-baseline.json. Production search embeds title/category/colors/sizes/description, retrieves up to 30 candidates and model-filters them into at most 10 results. Existing fake-embedding tests use another tiny dataset and do not establish semantic quality.

The preceding apparel-catalog-browsing change owns expanded category membership, CSV/payload format and safe revision-aware import. This change owns the content and evidence for relevance, not another importer or a redesign of public APIs.

## Goals / Non-Goals

Goals: meaningful 200-design assortment, descriptions backed by explicit facts, useful combinations and near misses, reproducible offline checks, and measured real-model relevance before claiming the demo works for those cases.

Non-goals: claiming coverage of most shoppers; generating hundreds of paraphrased clones; adding every conceivable garment/accessory; changing search ranking or LLM prompts automatically; paid evaluation on ordinary builds; altering live databases during specification authoring.

## Decisions

### 1. Facts first, descriptions second

Create a versioned authoring-facts JSON source containing ordinary import fields and functional profiles, then export the backend CSV. catalog-plan.md defines the allocation and fields. Keep factual composition, construction and known/unknown status explicit. Descriptions naturally expose the facts needed for search; the runtime still consumes the normal Product data. This is preferable to free-generating CSV prose and trying to infer a reliable oracle afterwards.

Preserve stable SKUs when correcting the same product identity. Assign a new SKU only for a genuinely new design. Distinguish sweater/hoodie from the existing jacket families and use agreed category/image keys. The sidecar is authoring/test metadata, not a second production database or an extra secret model input.

### 2. Curated breadth with meaningful within-style differences

Start with 200 distinct products, 100 per root using the 50/20/15/15 family allocation, with every initial garment style represented. Require two substantive functional differences between distinct same-style designs, excluding names/audience-only labels, price/color/size. Choose combinations driven by realistic user needs; retain limitations so plausible near misses exist. Shared/unisex products do not count twice.

Descriptions target 45–80 words, enough for fabric/lining/fill, fit, features, occasion and limitation without long SEO prose. Measure prompt/token impact: topK=30 makes verbose descriptions multiply filtering input costs. Do not simply concatenate a large fact JSON object into each prompt.

### 3. Offline validation and review

Offline validators check profile schema, percentages, import export equality, identities, counts/coverage, category ids, image keys, exact normalized description duplicates and functional signatures. Near-duplicate wording is a review signal, not definitive semantic evidence. Flag title/fact/description contradictions such as waterproof vs water-resistant. A release report lists each blocking SKU issue; unresolved required gaps fail the dataset gate.

The authoring source makes property-aware checks deterministic without asking an LLM to judge its own generated output. Human/agent review still evaluates natural language coherence, useful differentiation and technical plausibility. Known facts are fictional demo data, not verified external-product certifications.

### 4. Independent relevance benchmark

relevance-plan.md defines at least 44 judged scenarios, 25% held-out wording, and development/acceptance revisions. Label eligible SKUs and hard near misses from facts before evaluation. Test positives, genuine empty results, constraints, translations and multi-turn changes. Use different layers: offline dataset checks; deterministic wiring tests; actual embedding candidate measurement; end-to-end actual model/API runs. Do not call a green fake-embedding test a semantic result.

Real acceptance records zero hard violations, zero false-empty mandatory positives, correct empty cases, mean top-five precision >=0.90, Hit@3 >=0.90 and candidate Hit@30 >=0.95 with the current baseline. Broad cases must return several known-valid options. Evaluate all returned ranks for hard constraints. Use two fixed independent passes and expose variance/failures; details and formulas are in relevance-plan.md.

### 5. Safe publication and scope ownership

Feed the accepted export into apparel-catalog-browsing's manifest/revision importer. That implementation must detect description/content changes, upsert and verify before cleanup, and not skip a partial upgrade. Verify the actual stored revision and public products before evaluating. The backend model/search configuration remain unchanged unless evidence justifies a separately scoped change. Dataset changes invalidate prior relevance scores; keep the frozen baseline audit instead of replacing historical findings.

### 6. Provider cost and runtime boundaries

Real evaluation is opt-in, against an isolated catalog, with an estimated request/token budget, bounded retries and fresh conversation ids. Keep default CI free of external model calls. Record provider/model and all configuration affecting results. Store bounded interpretation/candidate/result diagnostics rather than unrestricted production prompts. If quota/provider readiness blocks a run, label it incomplete. Preserve ordinary search rate/budget policies.

## Migration Plan

1. Complete the schema/membership and safe-import prerequisites in apparel-catalog-browsing.
2. Build authoring profiles and export 200 distinct products, preserving justified legacy identities; resolve the baseline content issues.
3. Run offline validators and review the full coverage/functional difference report.
4. Build and freeze judged query fixtures, including held-out cases, independently of observed search output.
5. Import into an isolated Qdrant catalog revision and verify completeness.
6. Explicitly run cost-bounded real candidate/end-to-end evaluations twice; diagnose failures and implement any separately required fixes.
7. Publish the accepted revision through the controlled importer, coordinate image/category parity and update the actual homepage example. Retain source, report and rollback revision.

## Risks / Trade-offs

- [Unique wording disguises the same functional products] → Compare profiles and meaningful dimensions, not string counts alone.
- [Sparse facts make no-wool/no-down judgments unreliable] → Distinguish unknown from absence; make composition explicit in both facts and searchable description.
- [All desirable features make retrieval trivial and unrealistic] → Author trade-offs and one-constraint near misses.
- [Dataset tuned to a few demos] → Freeze judged cases and keep at least 25% held-out phrasing.
- [Filtering loses relevant items even with better descriptions] → Measure candidate and final stages separately; don't blame only the dataset or promise guaranteed first-place ranking.
- [More prose increases latency/cost] → Keep descriptions concise, measure actual candidate-prompt usage and do not expand the whole collection without scenario value.
- [Content edits do not reach Qdrant] → Require revision-aware import and verify the loaded manifest before any relevance claim.
