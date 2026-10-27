## ADDED Requirements

### Requirement: Conversation trail
Above the results, the application SHALL show the customer's messages of the current conversation in order, exactly as typed, with the latest emphasized.

#### Scenario: Two turns
- **WHEN** a visitor searched `warm winter jacket` and refined with `same but in navy`
- **THEN** the trail shows `warm winter jacket` then `same but in navy`, the latter emphasized

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
The application SHALL show the number of matches ("4 matches", "1 match") and SHALL display the products in the order returned by the backend.

#### Scenario: Backend order kept
- **WHEN** the backend returns `JKT0003`, `JKT0001`, `JKT0005`
- **THEN** the cards appear in that order and the count reads `3 matches`

### Requirement: Product card
Each product card SHALL show the product image in a 4:5 frame, the title in the serif typeface, the price formatted in EUR (`€262.73`), one swatch per color with the color name as accessible label and tooltip, and the sizes in the order received.

#### Scenario: Card content
- **WHEN** a product `{"title": "Urban Explorer Rain Jacket", "price": 262.73, "colors": ["navy", "red"], "sizes": ["XS", "S", "M"], "imageUrl": "/images/categories/rain.webp"}` is returned
- **THEN** its card shows that image, `Urban Explorer Rain Jacket`, `€262.73`, a navy and a red swatch labelled `navy` and `red`, and `XS S M`

#### Scenario: Unknown color name
- **WHEN** a product color is `ecru`, which has no swatch mapping
- **THEN** a neutral outlined swatch labelled `ecru` is shown

### Requirement: Product images
The application SHALL load product images from the `imageUrl` returned by the backend, show them in black and white, use the product title as alt text, and fall back to `/images/categories/default.webp` when the image fails to load; the static directory SHALL contain one black-and-white image per backend category slug (`puffer`, `parka`, `coat`, `rain`, `softshell`, `fleece`, `track`, `technical`, `field`, `bomber`, `denim`, `leather`, `windbreaker`) and `default.webp`.

#### Scenario: Category image
- **WHEN** a product has `imageUrl = "/images/categories/puffer.webp"`
- **THEN** the card shows the puffer category image with the product title as alt text

#### Scenario: Missing image
- **WHEN** the image at `imageUrl` returns HTTP 404
- **THEN** the card shows `/images/categories/default.webp`

#### Scenario: Absolute image URL
- **WHEN** a product has `imageUrl = "https://cdn.example.com/catalog/categories/puffer.webp"`
- **THEN** the card loads the image from that URL

### Requirement: Quick view
Activating a product card SHALL open a quick-view panel showing the image, title, price, category name, colors, sizes and full description, closable by a close button, the Escape key and a click outside, with focus moved into the panel on open and returned to the card on close.

#### Scenario: Open and close with keyboard
- **WHEN** a visitor focuses a card, presses Enter and then Escape
- **THEN** the panel opens with the full description and closes, and focus returns to the card

### Requirement: Empty results
When a successful response contains no products, the application SHALL show "Nothing matched that description" together with the interpretation bar and at least three example prompts, which submit as a refinement when clicked.

#### Scenario: No matches
- **WHEN** the backend returns `products: []` with an interpretation
- **THEN** the empty message, the interpretation bar and the example prompts are shown
