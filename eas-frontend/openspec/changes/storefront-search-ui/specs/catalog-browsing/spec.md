## ADDED Requirements

### Requirement: Apparel assortment and category hierarchy
The catalog SHALL contain real men's and women's clothing. Its two root categories SHALL be `Men` (`men`) and `Women` (`women`). Each root SHALL initially contain `Jackets & Coats`, `Sweaters`, `Jeans`, and `Trousers`. Jackets & Coats SHALL include child categories for jackets and coats, with jacket styles such as puffers, parkas and rain jackets available as deeper categories. Corresponding branches under Men and Women SHALL have distinct stable category ids even when their display labels match. T-shirts MAY be added later as another subcategory under each root. Underwear, briefs, lingerie, undershirts, tank tops and bras SHALL NOT be included in the assortment or category navigation.

#### Scenario: Initial assortment
- **WHEN** a visitor opens the catalog navigation
- **THEN** Men and Women are available as roots, each with Jackets & Coats, Sweaters, Jeans and Trousers, and no excluded underwear categories

#### Scenario: Nested jacket categories
- **WHEN** a visitor explores Men → Jackets & Coats → Jackets
- **THEN** deeper jacket categories such as Puffers, Parkas and Rain Jackets are available for navigation

### Requirement: Category navigation and descendant products
The catalog page SHALL provide keyboard-operable category links, a visible selected-category heading, a breadcrumb trail and an `All clothing` link. Selecting any category, including a root or intermediate category, SHALL display one combined product list containing all products assigned directly to that category or to any descendant, without duplicate SKUs. A parent SHALL NOT be a navigation-only landing page. Selecting All clothing SHALL display the entire assortment across both roots. The navigation SHALL expose the selected branch's child categories and allow returning to its ancestors. Category browsing SHALL NOT invoke semantic search, change the search conversation, or consume LLM search quota.

#### Scenario: Select a root
- **WHEN** a visitor selects Women
- **THEN** the heading and breadcrumb identify Women and the list includes all women's products across its descendant categories, excluding products assigned only to Men

#### Scenario: Select an intermediate category
- **WHEN** a visitor selects Men → Jackets & Coats
- **THEN** one list contains men's jackets and coats from all nested styles, and the visitor can navigate further into Jackets or Coats

#### Scenario: Select a leaf
- **WHEN** a visitor selects Women → Jackets & Coats → Jackets → Rain Jackets
- **THEN** the list contains products belonging to that leaf and the breadcrumb provides links to Women, Jackets & Coats and Jackets

#### Scenario: Return to the entire assortment
- **WHEN** a visitor selects All clothing
- **THEN** the list includes all men's and women's products, each SKU once

### Requirement: Frontend category-tree configuration
The category hierarchy SHALL be stored in the dedicated, editable frontend file `src/data/categories.json`, imported at build time. Each node SHALL contain a stable `id`, an English `name`, a `children` array and an optional `imageKey` referencing a representative image in `public/images/categories/<imageKey>.webp`. The file SHALL be the source of truth for navigation structure, display labels, ordering, breadcrumbs and validation of category URL ids. Navigation SHALL NOT be duplicated in components or inferred from image directories. The application SHALL NOT fetch a category tree from a backend category endpoint or wait for backend readiness to render category navigation; product requests SHALL still use the availability gate.

Editing labels or order SHALL require only changing this file and rebuilding the frontend. Adding or reparenting ids SHALL require alignment with the backend product-listing contract so descendant-inclusive listing remains correct; the frontend tree SHALL NOT redefine backend product membership. Validation SHALL reject duplicate ids, missing required node fields and image keys referencing missing local assets before deployment.

#### Scenario: Edit category navigation
- **WHEN** a category label or sibling order is changed in `src/data/categories.json` and the frontend is rebuilt
- **THEN** navigation and breadcrumbs reflect the file without editing React components or calling a category-tree API

#### Scenario: Backend unavailable
- **WHEN** the catalog is opened while the backend is unavailable
- **THEN** the local category tree can render without a category-tree request, while product loading waits for availability readiness

#### Scenario: Invalid configuration
- **WHEN** the category file contains a duplicate id, a missing required field or an imageKey without a corresponding asset
- **THEN** configuration validation fails before deployment with the offending node identified

### Requirement: Category selection lifecycle
The selected category SHALL be encoded as the URL query parameter `category` on `/catalog`, using its stable id; omission SHALL mean All clothing. Selection SHALL preserve unrelated query parameters, including campaign tags. Direct entry, reload and browser Back/Forward SHALL restore the category from the URL. Switching category SHALL abort the previous request, invalidate late responses, discard the previous products and cursor, close any open product dialog, and load the new category's first page after availability is confirmed. Every subsequent page and retry SHALL retain the same category. A cursor SHALL never be reused for another category. An unknown category SHALL show `Category not found` with an All clothing link rather than silently displaying a different assortment. An empty valid category SHALL show `No products in this category` while keeping category navigation available.

#### Scenario: Switch while loading
- **WHEN** the Men list is loading and a visitor selects Women
- **THEN** the Men request is aborted, the Women first page is loaded without a cursor, and late Men responses cannot enter the Women list

#### Scenario: Share a category link
- **WHEN** a visitor opens `/catalog?category=women-jeans&ref=acme`
- **THEN** Women's Jeans is selected and loaded, and subsequent category links preserve `ref=acme`

#### Scenario: Return using browser history
- **WHEN** a visitor navigates from Men to Women's Jeans and then uses browser Back
- **THEN** Men is selected again and loaded from its first page

#### Scenario: Empty category
- **WHEN** a valid selected category returns no products and no next cursor
- **THEN** the page shows No products in this category with the category navigation still usable

#### Scenario: Unknown category
- **WHEN** a category URL references an id absent from the category tree
- **THEN** Category not found and an All clothing link are shown without requesting a product page for that id

### Requirement: No catalog filter controls
Category navigation SHALL be the only catalog narrowing control. The application SHALL NOT provide catalog filter forms, facets or controls for gender, price, size, color, material or other attributes, nor a catalog sort control. Men and Women SHALL be category links rather than gender filters. Natural-language requests and refinements SHALL remain available on the separate search homepage.

#### Scenario: Browse without filters
- **WHEN** a visitor opens any catalog category
- **THEN** category links and product cards are available without attribute filters, a filter drawer or sort controls

### Requirement: Initial catalog
After the availability gate confirms readiness, the application SHALL load the first catalog page for the URL-selected category (or All clothing) automatically and display it exclusively on `/catalog` using the same `ProductGrid` and `ProductCard` React components as search. Browsing SHALL be independent of the search conversation and SHALL NOT call the search endpoint or consume LLM search quota.

#### Scenario: Arrival
- **WHEN** a visitor opens `/catalog` and availability is confirmed
- **THEN** the first catalog page is requested and its products appear on the catalog page, without a search submission

#### Scenario: Service starting
- **WHEN** the availability gate has not confirmed readiness
- **THEN** no catalog request is sent; the initial page load proceeds once readiness is confirmed

### Requirement: Cursor-based infinite scrolling
The application SHALL request the catalog through same-origin `GET /api/v1/products?limit=24`, adding a URL-encoded `category` id when selected and an opaque URL-encoded `cursor` for subsequent pages. It SHALL append pages in backend order, preserve existing cards, and never derive a cursor from an index, page number or SKU. Only one catalog page request SHALL be in flight at a time. A `nextCursor` of null SHALL mark the end and prevent further page requests. These are requirements for a new backend contract; the existing search endpoint SHALL NOT be used to emulate browsing.

#### Scenario: Next page
- **WHEN** the visitor approaches the end of the loaded catalog and `nextCursor` is non-null
- **THEN** one request uses that cursor, and the returned cards are appended without replacing earlier cards

#### Scenario: Repeated trigger
- **WHEN** the scroll sentinel is triggered repeatedly during a page request
- **THEN** only one request for that page is sent

#### Scenario: End of catalog
- **WHEN** a response has `nextCursor: null`
- **THEN** the page shows "You've seen the whole collection" and further scrolling sends no catalog requests

### Requirement: Accessible loading and recovery
The catalog SHALL provide a keyboard-operable "Load more" action when another page exists, in addition to automatic loading. Initial loading SHALL show skeleton cards; appending SHALL show progress after the existing cards and announce newly loaded products in a polite live region without moving focus. A page failure SHALL preserve loaded cards and offer "Try again" for the same cursor, with no automatic retry loop. Network, timeout and 5xx failures SHALL be reported to the availability gate; a confirmed healthy service SHALL leave the inline retry available. Catalog requests SHALL use a 30 second timeout. HTTP 400 for an invalid or expired cursor SHALL offer "Reload collection", which discards loaded pages and restarts without a cursor.

#### Scenario: Keyboard pagination
- **WHEN** a visitor activates "Load more" with the keyboard
- **THEN** the next page is appended, loading is announced, and focus remains usable without jumping to the top

#### Scenario: Failed append
- **WHEN** a later page fails and the visitor selects "Try again"
- **THEN** earlier cards remain visible and the same cursor is retried without duplicate cards

#### Scenario: Invalid cursor
- **WHEN** a later catalog request returns HTTP 400 and the visitor selects "Reload collection"
- **THEN** loaded pages are discarded and the first page for the same selected category is requested without a cursor

#### Scenario: Empty catalog
- **WHEN** All clothing is selected and the first page contains no products and `nextCursor: null`
- **THEN** the application shows "The collection is empty" rather than the search no-matches message

### Requirement: Catalog and search navigation
The header Men and Women links SHALL navigate to `/catalog?category=men` and `/catalog?category=women`; "Back to search" SHALL navigate to `/` without clearing the in-memory search conversation or submitting a search. Leaving `/catalog` SHALL stop pagination, abort in-flight catalog requests and ignore late responses; re-entering it SHALL start from the first page of the category identified by the URL. Leaving `/` SHALL abort any in-flight search and ignore its late response while preserving completed dialogue turns and draft input. "New search" SHALL reset the dialogue on `/` and generate a new conversation id without navigating to or fetching the catalog. Catalog pages SHALL NOT be merged with search results.

#### Scenario: Leave during pagination
- **WHEN** a visitor selects "Back to search" while a catalog page is loading
- **THEN** `/` shows the preserved search state, and a late catalog response cannot add cards to the homepage

#### Scenario: Inspect and return
- **WHEN** a visitor selects a header root category, opens a product dialog, and selects "Back to search"
- **THEN** the homepage dialogue and draft input are preserved and the visitor can submit a query there

#### Scenario: Search empty or rejected
- **WHEN** a search returns no matches or is rejected
- **THEN** the appropriate assistant state appears in the homepage dialogue without displaying catalog products

### Requirement: Availability during catalog browsing
Before each catalog request the application SHALL re-evaluate the opening window and, outside it, confirm control UP before requesting products. STARTING or control failure SHALL reopen the availability gate without sending the catalog request. The first catalog page may load automatically after readiness is restored; interrupted pagination SHALL require an explicit "Load more" or "Try again" action and SHALL NOT replay automatically through the scroll observer.

#### Scenario: Service stops during off-hours browsing
- **WHEN** a next-page action outside opening hours receives STARTING from the control endpoint
- **THEN** no catalog request is sent, the gate opens, existing cards are preserved, and pagination waits for an explicit action after readiness returns

### Requirement: Catalog layout and product inspection
On desktop the catalog SHALL show its category tree in a quiet left navigation area, with the selected-category heading, breadcrumb and compact product grid to its right. On mobile the category navigation SHALL reflow above the grid without horizontal overflow; all category levels SHALL remain accessible. Product inspection SHALL use the shared product dialog specified by `product-details`, preserving the selected category, loaded pages and scroll position on close. All clothing SHALL remain available within catalog navigation without adding a Catalog link to the global header.

#### Scenario: Inspect during pagination
- **WHEN** a visitor has loaded multiple category pages, opens a product dialog and closes it
- **THEN** the category, loaded cards, cursor and scroll position are preserved without another catalog request
