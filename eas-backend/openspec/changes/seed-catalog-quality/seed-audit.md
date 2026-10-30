# Seed audit — 2026-10-30

## Scope and evidence

Audited the repository file currently configured by `ecom.import.sample-file`: `src/main/resources/sampledata/apparel_jackets_sample.csv`. Full counts, source SHA-256 and repeated-description SKU groups are frozen in `audit/seed-baseline.json`. No running database was queried and no real embedding/model calls were made. This is a source-data audit, not a measured search-relevance result.

The application imports seed products into Qdrant; it does not currently use a relational product database. Editing the CSV alone will not reliably update existing data: the current import skips a nonempty collection when all products have category. The separate `apparel-catalog-browsing` change already owns the revision-aware safe replacement needed for content updates.

## Findings

| Check | Measured result | Implication |
|---|---|---|
| Product rows / unique SKUs / unique titles | 100 / 100 / 100 | Names and identities are distinct |
| Unique description strings | 20 | Only one fifth of rows have a distinct description string |
| Rows in repeated-description groups | 95 | 95 products share a description with another product |
| Excess descriptions beyond distinct strings | 80 | 80 rows repeat a description already used elsewhere |
| Categories | 13 jacket/coat styles | No sweater, hoodie, jeans or trousers assortment |
| Browsing membership | No categoryIds column | No authored Men/Women root membership |
| Prices | €50.78–€296.96 | Any under-€50 search necessarily has no price-eligible item |
| Price bands | 27 below €100, 31 from €100 to below €200, 42 at least €200 | Little low-price coverage and a large expensive segment |
| Description length | 15–25 whitespace-separated words | Core functions are mentioned but many constraints remain unspecified |
| Image cells | All 100 blank | Backend category default images are used; this is supported fallback behavior, not an import error |

### Distribution

| Style | Products |
|---|---:|
| Puffer | 25 |
| Rain | 14 |
| Coat | 12 |
| Fleece | 11 |
| Track | 11 |
| Technical | 10 |
| Softshell | 5 |
| Field | 5 |
| Denim | 2 |
| Windbreaker | 2 |
| Bomber | 1 |
| Parka | 1 |
| Leather | 1 |

All 25 puffers use the exact same down-filled, water-repellent, cold-city-winter description. Twelve rain products share one commuting description; eleven fleeces share one insulated-core description; eleven track jackets share one retro description. Different prices/colors/sizes can support metadata constraints, but these groups provide little evidence for choosing among products by function.

## Concrete content issues

- `JKT0034` is titled Commuter Waterproof Jacket, but its repeated description only states water-resistant nylon. This is an unsupported strength mismatch to resolve, not evidence that the garment is fully waterproof.
- `JKT0053` (Deep Winter Puffer) and `JKT0021` (Nordic Quilted Puffer) have identical functional descriptions. The title alone cannot establish a distinct warmth level or construction.
- `JKT0019` and three other cashmere-blend products mention cashmere, while only three other descriptions literally mention wool. A lexical count of wool therefore underestimates animal-fiber exclusions; cashmere is relevant to a no-wool request.
- Synthetic insulation and complete lining/fill composition are generally not explicit. `eco-down` in `JKT0004` does not establish whether the fill is animal down or polyester. A no-down/no-animal-material request cannot be judged safely from that term alone.
- Hood absence, removable hoods, fit, stretch, breathable fabric, full weather protection and pockets are unevenly specified. Absence of a word is not evidence of absence of a feature/material.
- Repeated down sourcing, eco-friendly and technical-rating claims are synthetic catalog copy, not verified real-product claims. Replace unsupported certification/sourcing language with clearly authored demo facts rather than treating it as external evidence.

## Search pipeline limits relevant to the audit

- Actual searchable text includes title, readable category, colors, sizes and description; SKU/price are excluded from embeddings. Price filtering uses metadata. Current production settings are topK=30, similarity threshold=0.15 and maximum 10 final products.
- Query analysis translates/compresses the request and extracts exclusions/price. Retrieved candidates are then filtered by a model. Better descriptions help only if relevant candidates survive retrieval and the filtering step respects the constraints.
- Existing retrieval integration tests use a deterministic 256-dimensional hashed bag-of-words FakeEmbeddingModel and a separate eight-row fixture. They demonstrate plumbing and filter behavior, not multilingual or semantic quality of this 100-row production seed.
- That small fixture includes `SHIRT1`, described as a cotton summer shirt but categorized as windbreaker. Do not use it as production assortment truth or blindly copy its labels into a judged benchmark.

## Conclusion for implementation

The catalog is large enough to exercise basic list pagination but not sufficiently diverse for the expanded apparel demo. The problem is primarily content/fact coverage and repeated profiles, not just row count. Build a curated 200-product assortment with genuine within-style differences and a judged query suite; expand further only where specific scenario gaps justify it. Actual relevant ranking remains to be measured with the real embedding/search pipeline after the dataset is implemented.
