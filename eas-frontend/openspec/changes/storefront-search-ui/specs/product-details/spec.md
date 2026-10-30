## ADDED Requirements

### Requirement: Compact shared product cards
Catalog and search SHALL use the same ProductCard and ProductGrid components. Each card SHALL contain a transparent garment illustration in a reserved 4:5 area, serif title, EUR price and a quiet View details affordance. Descriptions, SKU, category, colors and sizes SHALL NOT be rendered as additional card-body content; they SHALL be available in the product dialog. Prices SHALL preserve fractional values when present. Cards SHALL retain the order provided by their parent grid.

#### Scenario: Compact card
- **WHEN** a product priced 262.73 is shown in search or catalog
- **THEN** its card shows its drawing, title, €262.73 and View details, without a description paragraph or attribute list

### Requirement: Full product dialog
Activating a card's image, title or View details action SHALL open one accessible product dialog containing the same product's image, title, EUR price, full description, category, colors, available sizes in API order and visible selectable SKU. These values SHALL come from the already loaded Product object. Opening the dialog SHALL NOT request a product-detail endpoint, invoke a model, consume search quota, navigate to a new route or change the selected result/category. The UI SHALL NOT invent a structured material field absent from the API; material information may be shown as part of the returned description. Color names SHALL remain visible even if decorative swatches are used.

#### Scenario: Inspect a search result
- **WHEN** a visitor activates Leather Biker Jacket in the current result grid
- **THEN** a dialog shows that loaded product's complete details without any API request or route change

#### Scenario: Unknown color
- **WHEN** the product has a color called ecru
- **THEN** ecru is shown by name regardless of whether a matching swatch color is available

### Requirement: Dialog accessibility and dismissal
The dialog SHALL have a product-specific accessible label, a visible Close action, Escape dismissal, contained keyboard focus and a non-operable background while open. It SHALL center within the viewport with an opaque Sage surface and subdued backdrop. Closing SHALL return focus to the invoking card control when it still exists, preserve page position and leave conversation/catalog state unchanged. If the underlying route, category or selected result snapshot changes, the stale dialog SHALL close safely. On mobile its image and details SHALL stack, with the dialog remaining within viewport bounds and permitting scrolling through long descriptions.

#### Scenario: Keyboard close
- **WHEN** a keyboard user opens a card and presses Escape
- **THEN** the dialog closes and focus returns to that card

#### Scenario: Long mobile description
- **WHEN** a full description exceeds the available height on a 390 px screen
- **THEN** all details and the Close action remain reachable without horizontal overflow

### Requirement: Manual relevance inspection
A visitor SHALL be able to open a catalog product, inspect and copy its SKU and full description, close the dialog, return to the preserved search conversation and compare its result position using the same dialog. The frontend SHALL preserve backend ranking and SHALL NOT guarantee that a chosen target product ranks first.

#### Scenario: Compare catalog with search
- **WHEN** a tester inspects a catalog SKU and later finds it in search results
- **THEN** the result's dialog exposes the same complete product fields for comparison
