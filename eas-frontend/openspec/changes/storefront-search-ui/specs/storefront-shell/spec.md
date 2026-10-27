## ADDED Requirements

### Requirement: Single-page storefront
The application SHALL serve the storefront at `/` as one page consisting of a header, a search area and a results area, and SHALL NOT offer category navigation, a catalog listing or product filters.

#### Scenario: Landing page
- **WHEN** a visitor opens `/`
- **THEN** the page shows the header, the hero search area and no product results, category menu or filter controls

### Requirement: Header
The header SHALL show the brand wordmark and, once a conversation has started, a "New search" action; it SHALL NOT show non-functional shop controls such as a cart, account or wishlist.

#### Scenario: Before the first search
- **WHEN** no search has been submitted
- **THEN** the header shows the wordmark and no "New search" action

#### Scenario: After a search
- **WHEN** at least one search has been submitted
- **THEN** the header shows a "New search" action

### Requirement: Hero
Before the first search, the page SHALL show a hero with the headline "Describe it. We'll find it.", a sub-line stating that no browsing is needed and any language is welcome, a large search field, and at least four example prompts.

#### Scenario: Hero content
- **WHEN** a visitor opens `/`
- **THEN** the headline, sub-line, search field and at least four example prompts are visible

#### Scenario: Hero collapses after search
- **WHEN** the first search is submitted
- **THEN** the hero is replaced by a compact search bar at the top of the results area

### Requirement: Example prompts
The application SHALL show clickable example prompts, at least one of them not in English, and SHALL submit an example prompt as a search when it is clicked.

#### Scenario: Click example
- **WHEN** a visitor clicks the example prompt `A light rain jacket for cycling to work`
- **THEN** a search with exactly that message is submitted

### Requirement: Rotating placeholder
While the search field is empty and not focused, its placeholder SHALL cycle through example requests, including at least one non-English request; with reduced motion preferred by the user, the placeholder SHALL stay static.

#### Scenario: Reduced motion
- **WHEN** the visitor's system prefers reduced motion
- **THEN** the placeholder shows a single example and does not change

### Requirement: Visual language
The storefront SHALL use a monochrome palette (black, white and greys only, except product color swatches), a serif display typeface for the headline and product titles, a sans-serif typeface for body text, and English interface text only.

#### Scenario: Interface language
- **WHEN** a visitor searches in German
- **THEN** all interface labels, headings and messages remain in English

### Requirement: Responsive layout
The page SHALL be usable from 360 px to 1920 px viewport width, showing the product grid in 2 columns below 768 px, 3 columns from 768 px and 4 columns from 1280 px.

#### Scenario: Mobile
- **WHEN** the viewport is 390 px wide
- **THEN** products are shown in 2 columns and there is no horizontal scrolling

#### Scenario: Desktop
- **WHEN** the viewport is 1440 px wide
- **THEN** products are shown in 4 columns

### Requirement: Accessibility baseline
The page SHALL have a labelled search field, operable keyboard controls with visible focus, alt text on product images, status announcements through an ARIA live region, and no critical or serious axe-core violations.

#### Scenario: Keyboard search
- **WHEN** a visitor focuses the search field with the keyboard, types a request and presses Enter
- **THEN** the search is submitted

#### Scenario: Automated audit
- **WHEN** axe-core runs on the landing page and on a results page
- **THEN** it reports no critical or serious violations
