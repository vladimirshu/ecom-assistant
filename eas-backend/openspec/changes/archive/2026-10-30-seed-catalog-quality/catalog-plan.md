# Authored catalog plan

## Initial size and allocation

Build 200 distinct functional designs: a balanced starting point for this demo, not a claim to cover 80% of all shoppers. A larger 300-product dataset is optional only when an identified coverage gap warrants additional functional designs. Do not pad the count with random metadata or interchangeable descriptions.

| Family | Men | Women | Total |
|---|---:|---:|---:|
| Jackets & Coats | 50 | 50 | 100 |
| Sweaters (12 sweater + 8 hoodie designs per root) | 20 | 20 | 40 |
| Jeans | 15 | 15 | 30 |
| Trousers | 15 | 15 | 30 |
| Total distinct designs | 100 | 100 | 200 |

Initial jacket allocation per root: puffer 5, parka 3, coat 7, rain 5, softshell 4, fleece 4, track 3, technical 4, field 3, bomber 3, denim jacket 3, leather 3, windbreaker 3 (50 total). This covers the 13 existing jacket styles without letting puffers dominate the whole store. Each root also covers sweater, hoodie, jeans and trousers, for all 17 agreed styles.

Shared/unisex membership is permitted by the backend contract but cannot count one SKU twice toward 200. If used, keep the above per-root coverage and add enough distinct designs to retain the unique-product target. Do not imply that audience alone provides a functional difference: differences within each style must come from actual garment facts.

## Authoring facts as source, CSV as export

Proposed source: versioned authoring-facts part files under `src/main/resources/sampledata/facts/` (Men/Women × jackets-coats/knitwear/bottoms), each a complete `CatalogFacts` document sharing one schema version and catalog id; the definitive layout, field names and controlled vocabularies are in `facts-schema.md`. Each product carries the ordinary import fields (SKU, title, category, categoryIds, colors, sizes, price, imageKey, description) plus authoring-only functional facts. Export the CSV format owned by apparel-catalog-browsing, proposed `sampledata/apparel_catalog.csv`. Validate export equality so the facts source and CSV cannot drift independently. Update ecom.import.sample-file explicitly during implementation; do not leave the old jacket-only source configured.

Example fact structure (illustrative schema, not an accepted product or invented real SKU):

```json
{
  "materials": {
    "shell":  { "state": "known", "fibers": [ { "fiber": "cotton", "percent": 80 }, { "fiber": "polyester", "percent": 20 } ] },
    "lining": { "state": "not-applicable" },
    "fill":   { "state": "not-applicable" }
  },
  "warmth": "medium",
  "seasons": ["spring", "autumn"],
  "weatherProtection": "none",
  "windProtection": "low",
  "hood": "fixed",
  "fit": "relaxed",
  "stretch": "low",
  "pockets": ["kangaroo"],
  "packable": false,
  "occasions": ["casual", "travel"],
  "limitations": ["not a rain shell"]
}
```

Define enums/unknown states before authoring, keep each applicable composition complete and consistent, and validate percentages per component rather than mixing shell and fill. `none`, `unknown` and `not-applicable` have different meanings. Define exactly how an exclusion treats wool-family/animal fibers in each judged query; cashmere must not be ignored because its name lacks the literal word wool. Leather-free means all relevant components known to be free of leather; down-free requires explicit known fill. A fictional demo can author known facts, but it cannot infer them from vague legacy marketing terms.

Do not change the public Product DTO to carry this evaluation structure. Every fact needed by a user query must appear naturally in runtime-visible title/description/colors/sizes/category data (and metadata filtering where already supported). The evaluator must not give the model a secret, richer product representation than the real application uses. Image mapping stays compatible with frontend catalog-imagery: reuse neutral drawings when appropriate and assign explicit additional image keys for genuinely different cuts only when needed.

## Diversity dimensions

Within a style, require at least two substantive dimensions to differ between distinct designs, ignoring title, SKU, price, colors, sizes and audience-only labels. Examples:

- Puffers: synthetic vs animal-down fill; light layering vs deep-winter insulation; fixed/removable/no hood; short vs long cut; packable vs substantial construction.
- Rain/technical/softshell: actual waterproof shell vs water-resistant layer; wind protection; ventilation; stretch; hood; cycling/high-output activity vs city commuting vs hiking. Water-resistant is not an interchangeable synonym for waterproof.
- Coats: wool/cashmere blends vs known non-wool construction; lining/insulation; tailored vs relaxed cut; office/evening vs casual layering; protection limitations.
- Sweaters/hoodies: complete cotton/synthetic/wool blends; knit weight; neck/hood; fit; layering; pocket construction. A 100% cotton sweater and a wool-rich near miss should both exist.
- Jeans: straight/slim/relaxed/wide cuts; rigid vs stretch denim; rise; fabric weight; pocket construction and season suitability.
- Trousers: office/casual/travel functions; cotton/twill/linen/synthetic blends; stretch; fit; crease/pocket details and temperature/occasion suitability.

Do not give every item every desirable feature. Include trade-offs: warm but bulky, packable but lightly insulated, breathable but not waterproof, tailored but limited stretch. Do not invent medical/safety/temperature guarantees, certifications or external sourcing credentials. Technical measurements can be synthetic authored specifications only when explicitly chosen and consistent; never copy repeated ratings merely to appear technical.

## Description contract

Use about 45–80 ordinary English words. In roughly 3–5 short sentences, state: garment/audience/cut; material and relevant lining/fill; two or more distinguishing functions; intended use and a useful limitation. This is guidance for useful evidence, not a demand to pad every sentence or repeat a uniform template.

Each description must be unique after normalization. More importantly, each distinct design must have a distinct functional profile. Different adjectives or renamed place/collection prefixes do not qualify. Titles must agree with the evidence: avoid Waterproof when only Water-resistant is known, no-down labels when the fill is ambiguous, or deep-winter titles without stronger insulation facts. Do not stuff negated keywords into every description to manipulate embeddings; contextual negation must be tested rather than hidden.

Keep price out of the embedded prose: it already exists as numeric metadata. Colors and sizes remain arrays/exported lists, with ordinary availability rather than random combinations chosen solely to create apparent variety.

## Price and constraint intersections

Minimum useful price coverage: 20 products below €50; 60 from €50 to below €100; 60 from €100 to below €200; 20 at least €200. Allocate the remaining 40 or more products by meaningful scenarios rather than a uniform random price generator. Both roots should have credible affordable alternatives; do not put the entire inexpensive segment into one root or one garment family.

Choose several useful exact comparison prices (for example €50, €70, €100, €150, €200) and include boundary and adjacent values where needed. Test strict under vs up-to operators using the actual facts. Ensure color, size, price and functional constraints intersect: a long list of independent marginal values does not prove that a combined query has an eligible product.

For each mandatory positive query, author at least one eligible item and at least two plausible one-constraint near misses. Broad scenarios with at least three eligible designs should expose several useful alternatives. Intentional empty queries must be valid requests whose complete combination has zero eligible products, not guardrail-invalid contradictions.

## Offline audit and publication

Validate 200 unique SKU rows, stable identities, all root/style counts, category/image-key parity, known profiles, composition, strings and price coverage. Compare functional signatures pairwise within style; flag near-duplicate wording for review. Produce a SKU-addressable quality report and require all blocking issues to be resolved.

Keep the tiny integration fixture separate. Its fabricated SHIRT1/windbreaker combination must not enter production facts or judged relevance labels; correct misleading fixture labels deliberately if tests begin to depend on their semantics.

Publish only through the versioned import defined in apparel-catalog-browsing, verify the complete stored revision and public sample fields, then run relevance evaluation against that exact revision. A CSV edit alone is not a deployed update. The baseline audit remains a dated snapshot rather than being rewritten to look like a post-implementation pass.
