## ADDED Requirements

### Requirement: Conversation workspace
After the first real submission on `/`, the introduction SHALL be replaced by a two-pane workspace: conversation history and follow-up input on the left, selected results and interpretation on the right. History SHALL contain customer messages in order, exactly as typed, and concise structured assistant states derived from backend responses. Product grids SHALL NOT be repeated inside the history. Completed turns, interpretations and returned product arrays SHALL remain in application memory. No invented assistant prose or additional model call SHALL be required.

The desktop history SHALL scroll independently when long, with the follow-up composer remaining available below it; the results pane SHALL remain beside it. Scrolling through history SHALL NOT push current results out of view. At 1440×900 the latest interpretation, first row of cards and composer SHALL be visible together. Longer result lists may continue vertically; the interface SHALL NOT hide returned products to force every result into the first screen.

#### Scenario: Two turns
- **WHEN** a visitor searches warm winter jacket and refines with same but in navy
- **THEN** both requests and their structured replies remain in the left history and a single grid on the right shows the selected turn's results

#### Scenario: Long history
- **WHEN** many refinements have accumulated
- **THEN** scrolling the desktop history leaves the composer and current product area in their own stable positions

### Requirement: Result snapshot selection
Each successful assistant turn SHALL offer Show these matches. Selecting it SHALL display that stored turn's product array and interpretation without an API call, changing the conversation id or modifying backend memory. The selected turn SHALL be identified in history and the result heading SHALL distinguish current matches from an earlier request. Return to latest matches SHALL restore the newest successful snapshot without a request. Historical selection SHALL NOT branch or rewind the conversation: a new refinement always uses the ongoing server conversation.

Submitting a new request SHALL select its pending state. A successful latest response SHALL select the new snapshot unless the visitor explicitly selected an earlier snapshot after that submission; in that case the UI SHALL retain the selected snapshot and offer a New matches available action. Late or superseded responses SHALL never replace the selected content.

#### Scenario: Inspect an earlier turn
- **WHEN** a visitor selects Show these matches for the first successful request
- **THEN** the first product array and interpretation appear in original backend order with an earlier-request label and no network request

#### Scenario: Refine while viewing history
- **WHEN** an earlier snapshot is selected and the visitor submits with a hood
- **THEN** the request refines the current server conversation, not the historical snapshot, and the new pending state becomes selected

#### Scenario: Preserve explicit selection during a request
- **WHEN** a visitor selects an earlier snapshot while the latest request is pending and that request succeeds
- **THEN** the earlier selection remains visible with an action to open the new matches

### Requirement: Mobile conversation and matches
On narrow screens the workspace SHALL provide Matches and Conversation tabs with explicit labels and selected state, sharing the same in-memory conversation and result selection. Matches SHALL be selected after a new submission or refinement and SHALL show the selected request summary, interpretation and product grid immediately. Conversation SHALL show history, follow-up input and controls to inspect prior matches. Switching tabs SHALL NOT make a request, reset the draft, generate a conversation id or change the selected snapshot. Choosing a historical result from Conversation SHALL open Matches. All returned products SHALL remain available, including additional grid rows.

#### Scenario: Mobile refinement
- **WHEN** a visitor submits a follow-up from Conversation at 390 px
- **THEN** Matches opens with the pending state and then the new results while prior history and draft lifecycle remain intact

#### Scenario: Tab switch
- **WHEN** the visitor switches from Matches to Conversation and back
- **THEN** history, selected results, draft and conversation id are preserved without API requests

### Requirement: Interpretation bar
For a successful response, the application SHALL show a "We understood" bar with one chip per element of `interpretation`: the `searchQuery`, one chip per negative example labelled "No <item>", a price chip, and "Lowest price first" when `sortByLowestPrice` is true; chips for absent elements SHALL NOT be shown.

#### Scenario: Full interpretation
- **WHEN** the interpretation is `{"searchQuery": "blue cotton jacket", "negativeExamples": ["wool"], "priceConstraint": {"operator": "LESS_THAN", "value": 200}, "sortByLowestPrice": true}`
- **THEN** the bar shows `blue cotton jacket`, `No wool`, `Under €200` and `Lowest price first`

#### Scenario: Query only
- **WHEN** the interpretation has no negatives, no price constraint and `sortByLowestPrice = false`
- **THEN** the bar shows only the search query chip

#### Scenario: Non-English request
- **WHEN** the visitor typed `veste chaude sans laine` and the interpretation is `{"searchQuery": "warm jacket", "negativeExamples": ["wool"]}`
- **THEN** the trail shows `veste chaude sans laine` and the bar shows `warm jacket` and `No wool`, using the backend's text unchanged

### Requirement: Price chip wording
The price chip SHALL read `Under €X` for `LESS_THAN`, `Up to €X` for `LESS_THAN_OR_EQUAL`, `Over €X` for `GREATER_THAN` and `From €X` for `GREATER_THAN_OR_EQUAL`, with X formatted as EUR without decimals when whole and with two decimals otherwise.

#### Scenario: Whole value
- **WHEN** the price constraint is `LESS_THAN_OR_EQUAL 150`
- **THEN** the chip reads `Up to €150`

#### Scenario: Fractional value
- **WHEN** the price constraint is `GREATER_THAN 99.5`
- **THEN** the chip reads `Over €99.50`

### Requirement: Result count and order
The application SHALL show the number of matches ("4 matches", "1 match") and SHALL display the products in the order returned by the backend, using the same `ProductGrid` and `ProductCard` React components as the catalog. Search SHALL render the entire returned list (currently at most 10 matches), without cursor pagination, client-side reordering or additional requests on scrolling. The first card SHALL represent the first backend match; the count describes returned matches, not an estimated total across the catalog.

#### Scenario: Backend order kept
- **WHEN** the backend returns `JKT0003`, `JKT0001`, `JKT0005`
- **THEN** the cards appear in that order and the count reads `3 matches`

### Requirement: Transparent graphite product illustrations
Generation coverage, prompts, source provenance and export acceptance SHALL follow capability `catalog-imagery` and `image-generation.md`.

Product images SHALL be light monochrome graphite drawings with fine contour lines, restrained crosshatching and sufficient unshaded material detail to remain legible on the Sage interface. Each final asset SHALL contain one complete garment, front view, centered with consistent margins, on genuine transparent alpha outside the garment silhouette. There SHALL be no paper rectangle, background texture, person, hanger, text, price, logo or neighboring garment fragment. Opaque shading inside the fabric silhouette MAY be retained.

The frontend SHALL load `imageUrl` from the Product object, use its title for accessible image text and contain the entire garment in a reserved 4:5 frame with a transparent background. Individual alpha WebP assets and `default.webp` SHALL live flat in `public/images/categories/`; every used image key SHALL have an asset. Image keys SHALL remain independent of browsing category ids and MAY be shared across Men/Women branches. Optional `imageKey` entries in `src/data/categories.json` refer to representative category drawings and SHALL NOT override product imageUrl. Asset production SHALL cover the existing jacket styles and the agreed sweaters, hoodies, jeans and trousers. Approximate export target: 1200×1500 pixels, at most 200 KB per image while preserving alpha and legible linework. CSS SHALL NOT simulate background removal or use screenshot crops as final assets.

#### Scenario: Sage background visible
- **WHEN** a jacket card is shown on the Sage paper surface
- **THEN** the page surface is visible around its silhouette with no white image rectangle, clipped sleeves or adjacent garment fragments

#### Scenario: Missing image
- **WHEN** a product image fails
- **THEN** the frontend substitutes the transparent default illustration once without an error loop or broken-image icon

#### Scenario: Absolute image URL
- **WHEN** imageUrl points to an absolute CDN URL
- **THEN** the same contained-image and fallback behavior applies without deriving the asset path from navigation categories

### Requirement: Empty results
A successful response with no products SHALL create an empty snapshot showing Nothing matched that description, its actual interpretation and at least three example prompts that submit as refinements. The selected empty snapshot SHALL NOT display older product cards as if they matched the latest request. Previous successful snapshots SHALL remain available through history.

#### Scenario: No matches after a successful turn
- **WHEN** the backend returns an empty products array for a refinement
- **THEN** the current pane shows the empty state and actual interpretation, and earlier results remain accessible only as labelled historical matches
