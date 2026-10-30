## ADDED Requirements

### Requirement: Isolated animated search example
The initial homepage SHALL include a clearly labelled illustrative example of the real search experience, beside the explanatory copy on desktop and below it on mobile. It SHALL show a request being typed, the matching loading treatment, example product cards, a follow-up request and the resulting changed selection. Demonstration state and products SHALL remain separate from the real input, conversation id, history and result snapshots. They SHALL NOT be presented as the visitor's live results.

#### Scenario: Explain refinement
- **WHEN** the visitor plays the example
- **THEN** an initial clothing request leads to cards and a subsequent constraint visibly changes the example selection

### Requirement: Backend-independent demonstration
Playback SHALL use bundled fixtures and garment assets, make no search, catalog, health or control requests of its own, and consume no model or search quota. It SHALL be able to run while the backend is unavailable. Availability checks belonging to the real application's gate SHALL remain independent. Interacting with a demonstration product MAY open its local example dialog, which SHALL remain labelled example content.

#### Scenario: Service asleep
- **WHEN** the backend is unavailable and the example is played
- **THEN** playback remains available and makes no API requests

### Requirement: Playback controls and accessibility
The example SHALL provide visible Play/Replay and Pause controls, pause when the real search input receives focus, and stop when the real conversation starts or the homepage is left. Any automatic playback SHALL run at most one cycle per homepage entry and only while visible and the real field is unfocused. Reduced-motion preference SHALL disable automatic typing and movement, showing a readable static example with user-operated steps or replay instead. Typing characters SHALL NOT be announced individually to assistive technology.

#### Scenario: Visitor starts typing
- **WHEN** playback is running and the real search field receives focus
- **THEN** playback pauses without modifying the visitor's text

#### Scenario: Reduced motion
- **WHEN** reduced motion is enabled
- **THEN** no automatic typing, looping or card movement occurs and the example remains understandable

### Requirement: Final example derived from implemented experience
The implementation SHALL first provide the example using the same shared search, interpretation and product presentation components as the real flow. After the live flow is completed, the final demonstration SHALL be reviewed against an actual run and use a request/refinement pair supported by the agreed catalog. A recorded clip MAY replace the scripted demonstration only if it shows the finished interface and retains accessible playback controls and a static alternative. A rough MagicPath animation SHALL NOT be treated as the final recording or as evidence of actual backend responses.

#### Scenario: Release review
- **WHEN** the homepage demonstration is prepared for release
- **THEN** its query, refinement, layouts and product changes have been checked against the implemented search flow and actual catalog
