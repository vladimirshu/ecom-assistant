## ADDED Requirements

### Requirement: Search homepage and separate catalog page
The client-only SPA SHALL serve the search homepage and conversation workspace at `/` and the catalog at `/catalog`. On first arrival, `/` SHALL show the Sage search-first introduction and an isolated demonstration. Submitting a real search SHALL replace the introduction with the conversation workspace on the same route. The homepage SHALL NOT request catalog pages; demonstration products SHALL be labelled examples and kept separate from live results. The catalog SHALL provide Men/Women category navigation without attribute filters or sort controls.

#### Scenario: Landing page
- **WHEN** a visitor opens `/` before any search
- **THEN** the header, headline, real search field, example prompts, introductory copy and labelled demonstration appear without a catalog-listing request

#### Scenario: First real search
- **WHEN** the visitor submits a valid search
- **THEN** `/` displays the conversation workspace specified by `search-results`, and the demonstration stops

### Requirement: Quiet header and category entry points
Every page SHALL show the ATELIER wordmark, the exact small text `AI-powered clothing discovery. A portfolio demo.`, and unobtrusive `Men` and `Women` navigation links. The note SHALL use approximately 11 px text, wrapping on narrow screens without overlapping navigation. Men and Women SHALL open `/catalog?category=men` and `/catalog?category=women` respectively, preserving unrelated query parameters. Both links SHALL be visible without scrolling at 390×844 and 1440×900. There SHALL be no separate Catalog or Browse catalog header link and no cart, account or wishlist controls. The conversation workspace SHALL provide New search; the catalog SHALL provide Back to search near its page heading.

#### Scenario: Choose a root from the first screen
- **WHEN** a visitor selects Women on `/?ref=acme`
- **THEN** `/catalog?category=women&ref=acme` opens with Women's entire branch selected

#### Scenario: Compact header
- **WHEN** the viewport is 390 px wide
- **THEN** the project note can wrap under the wordmark while Men and Women remain visible and operable

### Requirement: Search-first hero
The homepage SHALL show `Describe it. We'll find it.` above a compact search component spanning the available content width. The heading SHALL use approximately 23–25 px serif type, subordinate to the field's visual emphasis. The input SHALL use ordinary body sizing (approximately 14 px on desktop and at least 16 px on mobile), with a component height around 48–52 px, a light Sage fill, a visible border and a green Find my match action. It SHALL NOT use a large textarea or oversized input typography. The accompanying copy SHALL read `Clothes for what you have in mind.` and `Tell us what you need, in any language.`. On desktop this copy SHALL sit to the left of the demonstration below the full-width search row; on narrow screens it SHALL precede the demonstration.

#### Scenario: Hierarchy
- **WHEN** the initial desktop homepage renders
- **THEN** the full-width Sage search field is the primary action, the small headline precedes it, and the copy and demonstration form a second row

### Requirement: Search examples and stable input
The field SHALL have the stable placeholder `What are you looking for?`; its real input SHALL NOT be animated or filled by the demonstration. At least two concise example prompts SHALL be available beneath it, initially covering warm layers and a leather jacket. Clicking a prompt SHALL submit its associated complete request as a real search through normal validation and availability checks. The example set SHALL also include a non-English request for multilingual demonstration and testing without requiring every example to be visible simultaneously.

#### Scenario: Example request
- **WHEN** the visitor selects A leather jacket
- **THEN** the associated request is submitted through the real search flow, independently of the demonstration

#### Scenario: Input ownership
- **WHEN** the visitor focuses or types into the real field
- **THEN** its text is not replaced by animation and any automatically playing demonstration pauses

### Requirement: Sage visual language
The storefront SHALL use the approved soft Sage palette: paper `#F6F8F0`, Sage wash `#E5ECD7`, action green `#4E704E`, graphite text `#303B30`, secondary text `#68745F` and hairline borders `#D6DFCC`. Display headings, wordmark and product titles SHALL use Georgia or a visually equivalent serif; inputs and body text SHALL use Arial or a visually equivalent sans serif. The baseline implementation SHALL use system Georgia/Arial stacks to match the approved sketch. Garment drawings SHALL remain monochrome against the colored interface. Interface labels SHALL be English; visitor messages and backend suggestions SHALL retain their original language. Decoration SHALL remain restrained, with generous spacing and no heavy card frames or shadows.

#### Scenario: Colored interface with grayscale drawings
- **WHEN** products appear on the Sage surface
- **THEN** green actions and soft Sage areas provide color while the garment illustrations remain neutral graphite without rectangular white image backgrounds

### Requirement: Responsive composition
The application SHALL work from 320 px to 1920 px without horizontal page overflow. Desktop conversation history and current results SHALL appear alongside one another; mobile behavior is specified by `search-results`. Product grids SHALL adapt to their available container width, rather than using viewport width alone: normally three columns in the desktop results pane, four in a wide desktop catalog and two on a 390 px mobile screen. Fewer returned products SHALL NOT be duplicated or hidden to fill a row. At 1440×900 the conversation composer, current interpretation and first row of cards SHALL be visible without scrolling through the history.

#### Scenario: Different desktop containers
- **WHEN** search results and the catalog are viewed at 1440 px
- **THEN** the split workspace accommodates three result columns while the wider catalog accommodates four, using the same card component

#### Scenario: Mobile product completeness
- **WHEN** three search products are returned at 390 px
- **THEN** all three are rendered in backend order across a two-column grid, including the third product on a later row

### Requirement: Accessibility baseline
The application SHALL provide labelled inputs, native keyboard-operable controls, visible focus, accessible image text and polite status announcements. It SHALL respect reduced motion and maintain readable contrast on the Sage surfaces. Automated axe-core checks SHALL report no critical or serious violations on the homepage, conversation, catalog, service states and open product dialog.

#### Scenario: Keyboard search
- **WHEN** a visitor types into the labelled search input and presses Enter
- **THEN** the same search action as Find my match is performed

### Requirement: Route navigation and direct entry
Both routes SHALL support direct entry, reload and browser Back/Forward. Internal navigation SHALL preserve unrelated query parameters such as `ref`, updating only the category parameter when selecting a category. Static hosting SHALL serve the SPA entry for `/catalog` without rewriting `/api/v1/*` responses. Internal catalog navigation SHALL preserve completed conversation turns, selected result snapshot and draft input in memory; a full reload SHALL create a fresh conversation.

#### Scenario: Direct category entry
- **WHEN** `/catalog?category=women-jeans&ref=acme` is opened directly or reloaded
- **THEN** the SPA restores that category from the local tree and loads its first product page after readiness, preserving `ref=acme`
