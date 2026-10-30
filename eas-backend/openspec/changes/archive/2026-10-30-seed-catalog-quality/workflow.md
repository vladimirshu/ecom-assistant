# Catalog authoring / export / evaluation workflow

This change turns the authored facts into the runtime seed and provides the judged
relevance benchmark. The workflow below is the repeatable path from source to evidence.

## 1. Authoring source

The single source of truth is the versioned authoring-facts source, split into six part
files under `src/main/resources/sampledata/facts/` (Men/Women × jackets-coats/knitwear/
bottoms). Each product carries the ordinary import fields plus authoring-only `facts`
(shell/lining/fill composition with known/unknown/not-applicable states, warmth, seasons,
protection, hood, fit, stretch, pockets, packable, occasions, limitations). The schema and
controlled vocabularies are documented in `facts-schema.md`.

## 2. Export to CSV

The import CSV (`src/main/resources/sampledata/apparel_catalog.csv`) is a deterministic
export of the facts source, produced by `CatalogFactsExporter.toCsv(...)`. Parity is
enforced by `CatalogFactsQualityTest.csvExportMatchesCommittedFile`, so the JSON and CSV
cannot drift independently. The runtime seed is wired via `ecom.import.sample-file`.

## 3. Offline quality gate

`CatalogQualityGate` validates the facts source with no model/vector-store calls: schema and
percentage checks, unique identities, 200-design coverage and per-root/style allocation,
browsing membership, image keys, price bands, normalized duplicate descriptions,
functional-signature diversity within each garment style (cross-root clones are blocked,
single-dimension cross-root differences are flagged for review), and title/description
versus fact consistency. `CatalogFactsQualityTest.qualityGateHasNoBlockingIssues` is the
release gate; run it after any data edit:

```bash
./gradlew test --tests "com.ecomassistant.catalog.facts.CatalogFactsQualityTest"
```

## 4. Judged scenarios

`src/test/resources/relevance/seed-search-cases.json` holds 44 versioned cases
(8 root/group, 12 positive, 8 exclusion, 4 paraphrase, 4 non-English, 4 conversation,
4 empty) with fact-derived hard constraints, eligible/acceptable/near-miss SKUs and a
development/held-out split (at least 25% held-out). `RelevanceCasesTest` verifies the
allocation, held-out split, SKU cross-references and that eligible SKUs satisfy the
fact-derivable hard constraints.

## 5. Real-model evaluation (opt-in)

`SearchEvaluationRunner` drives the real end-to-end pipeline once per turn and records
bounded per-case diagnostics plus the acceptance metrics. It is wired only when
`ecom.relevance.evaluation.enabled=true`; ordinary builds and unit tests never touch the
model or vector store. Before a run, record provider/model, topK, similarity threshold,
max-results and the source catalog revision, and run against an isolated catalog/revision.
Two independent complete passes are required for acceptance; quota/provider-blocked cases
are reported as incomplete rather than silently selecting the best run.

```bash
# Requires a running Qdrant and provider credentials; not part of the ordinary build.
./gradlew bootRun --args='--ecom.relevance.evaluation.enabled=true'
```

## 6. Publication and rollback

Publishing a dataset revision is `apparel-catalog-browsing`'s responsibility: on startup
`ProductImportService` computes the desired revision, upserts by deterministic SKU id,
verifies every expected SKU and payload, then removes obsolete points and marks the
catalog verified. A CSV edit alone is not a deployed update. Rollback is the previously
verified source + CSV revision, re-imported through the same path. The frozen baseline
audit (`seed-audit.md`, `audit/seed-baseline.json`) remains a dated snapshot and is not
rewritten after the content update.

## Backend checks

- Offline gate: `CatalogFactsQualityTest` (200 products, no blocking issues, CSV parity).
- Fixtures: `RelevanceCasesTest`, `SearchEvaluationRunnerTest`.
- Full Qdrant-backed integration suite (Testcontainers) remains the deployment check and
  is run where Docker is available; it is not part of the ordinary model-free build.
