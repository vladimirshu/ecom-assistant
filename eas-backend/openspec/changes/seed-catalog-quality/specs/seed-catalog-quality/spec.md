## ADDED Requirements

### Requirement: Curated initial assortment
The released seed assortment SHALL contain at least 200 distinct, valid SKU products and cover Men and Women, Jackets & Coats, Sweaters, Jeans and Trousers in both roots, and every agreed initial garment style. The initial allocation SHALL follow catalog-plan.md: 50 jacket/coat designs, 20 sweaters/hoodies, 15 jeans and 15 trousers per root. Products shared across roots SHALL count once toward the distinct-product total; a shared membership SHALL NOT be used to disguise a shortfall in distinct designs. Color/size variants already represented by one product's arrays SHALL NOT be duplicated as fake functional variety. Underwear, lingerie, undershirts, tank tops and bras SHALL remain excluded.

#### Scenario: Release assortment
- **WHEN** the revised seed is accepted
- **THEN** it has at least 200 distinct valid SKUs with both-root/group/style coverage and no counts inflated by repeated memberships or color-only clones

### Requirement: Explicit authoring facts
Each product SHALL have a versioned authoring profile covering its import fields and explicit functional facts: intended audience/root membership, garment type, materials across relevant shell/lining/fill components, warmth/season, weather protection, hood, fit, stretch, pockets and intended occasions as applicable. Unknown and not-applicable values SHALL be distinguished from false/absent values. The profiles SHALL support exclusion judgments without inferring a missing feature from an omitted word. Description and import fields SHALL agree with the profile. Authoring/evaluation facts SHALL NOT add unsupported fields to the public Product DTO or be secretly injected into runtime search during evaluation.

#### Scenario: No-down judgment
- **WHEN** a jacket's fill is only called eco-down and its actual composition is unknown
- **THEN** it is not accepted as a known synthetic/down-free positive for an exclusion benchmark until its fill fact and public description are clarified

#### Scenario: Unknown hood
- **WHEN** a profile leaves hood availability unknown
- **THEN** the product is not labelled definitely hoodless merely because the description omits the word hood

### Requirement: Functional diversity within garment families
Same-family products SHALL have meaningfully distinct factual profiles, not merely different names, colors, sizes, prices or synonymous descriptions. Each distinct same-family design SHALL differ from every other design in that family in at least two relevant functional dimensions, such as fabric/fill, warmth, protection, hood construction, cut, stretch, packability or pocket design. An alternative that changes only metadata variants SHALL be consolidated or justified as a variant rather than counted as a new functional design. The collection SHALL include both useful matches and plausible near misses for the scenario matrix.

#### Scenario: Puffer variety
- **WHEN** the revised puffers are reviewed
- **THEN** they include distinct synthetic/down fills, warmth levels and hood/fit/protection combinations rather than 25 records sharing one functional description

### Requirement: Informative consistent descriptions
Each product SHALL have a unique English description with approximately 45–80 words covering its material/functional differences, use case and at least one relevant limitation or distinction. Normalized exact duplicates SHALL fail the quality gate. Cosmetic rewrites of the same factual profile SHALL also fail review even if their strings are unique. Titles, descriptions, facts, category, image mapping, colors, sizes and price SHALL be internally consistent. Unsupported certifications, sourcing claims, temperature guarantees and guessed material composition SHALL NOT be invented to fill a template. Known properties required by benchmark queries SHALL be present in runtime-visible product data.

#### Scenario: Waterproof title mismatch
- **WHEN** a title calls a jacket waterproof but its facts support only water resistance
- **THEN** quality review requires a title/description correction or explicit compatible protection facts before release

#### Scenario: Cosmetic uniqueness
- **WHEN** two descriptions use different adjectives but share the same functional profile and differ only in color/price
- **THEN** they do not count as two functionally distinct accepted designs

### Requirement: Scenario-driven attribute and price coverage
The assortment SHALL satisfy the positive, exclusion, multi-attribute, multilingual and refinement coverage plan in catalog-plan.md and relevance-plan.md. It SHALL include complete known-composition positive and negative alternatives for wool/animal-fiber, leather and down exclusions; weather/hood/fit/use-case distinctions; varied sizes/colors; and useful lower-price choices. Initial price coverage SHALL include at least 20 products below €50, 60 from €50 to below €100, 60 from €100 to below €200 and 20 at €200 or above, with remaining products allocated by useful scenarios. Fixtures SHALL include actual prices exactly at chosen comparison boundaries. Price SHALL remain a metadata constraint, not embedding text.

#### Scenario: Useful cheaper refinement
- **WHEN** the benchmark refines a broad garment request with a lower budget
- **THEN** the authored collection contains known eligible cheaper alternatives, while at least one otherwise plausible more expensive product is an excluded near miss

### Requirement: Offline seed quality gate
A reproducible offline audit SHALL validate schema, uniqueness, count/allocation, memberships, factual/description consistency, normalized duplicate strings, functional profile differences, price coverage and image-key parity. It SHALL output category/attribute coverage, duplicate groups, invalid rows and unresolved review items with SKU references. Near-duplicate wording detection SHALL flag candidates for review rather than count textual similarity as proof of functional equality. Required functional comparisons SHALL exclude identity, price, color and size. Release SHALL fail for missing required coverage, unresolved contradictions, duplicate profiles or invalid production rows. Model calls SHALL NOT be required for these checks.

#### Scenario: Cheap clone padding
- **WHEN** additional rows reuse the same functional profiles with different names and prices
- **THEN** the quality gate identifies the offending groups and does not treat the new row count as sufficient diversity

### Requirement: Controlled seed publication
A dataset release SHALL record source/facts/CSV hashes and a catalog revision, preserve stable SKUs for unchanged identities, and publish through the safe import/recovery behavior owned by apparel-catalog-browsing. Content changes SHALL not be considered deployed merely because a CSV was edited. A successful post-import check SHALL compare expected SKU counts, representative public product fields and the revision visible to the running service. Partial updates SHALL not be accepted as relevance-test input. This change SHALL NOT introduce a second competing importer or production product database.

#### Scenario: New descriptions on an existing store
- **WHEN** revised descriptions are published to an existing Qdrant collection
- **THEN** the desired revision is imported and verified through the owning migration path, rather than being skipped because category fields already exist
