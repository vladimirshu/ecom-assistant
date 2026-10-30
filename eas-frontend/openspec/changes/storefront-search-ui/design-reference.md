# Approved ATELIER Sage reference

Approved in this conversation on 2026-10-30. MagicPath project `466876169267060736`, named My First Project. These are reference revisions; subsequent canvas edits must not silently change implementation scope. Written OpenSpec requirements govern real runtime behavior.

| Screen | Component id | Reference revision id |
|---|---|---|
| Homepage | 466877444310315008 | 466877444310315009 |
| Conversation | 466877480846905344 | 466877480846905345 |
| Catalog | 466877487725559808 | 466879528745185280 |
| Product details | 466878657810558976 | 466880030438481920 |
| Search loading | 466878665028931584 | 466878665028931585 |
| Empty results | 466878677129519104 | 466878677129519105 |
| Errors and limits | 466879003328925696 | 466879003328925697 |
| Service availability | 466879010748665856 | 466879010748665857 |
| Design foundations | 466879017224654848 | 466879017224654849 |
| Mobile homepage | 466879421043863552 | 466880013329924096 |
| Mobile conversation | 466879427654090752 | 466880022788079616 |

## Decisions to preserve

- Sage surfaces and green actions; monochrome light graphite clothing drawings with transparent backgrounds.
- Small header portfolio note, Men/Women entry points, no extra Catalog link.
- Compact full-width search, normal-sized input and small Describe it. We'll find it. heading.
- Clothes for what you have in mind. / Tell us what you need, in any language.
- Cards show image/title/price and open complete details in a dialog. Descriptions belong in that dialog.
- Desktop history beside current matches; mobile tabs; stored history remains inspectable without replaying requests.
- Local category tree from the existing frontend configuration decision; no attribute filters.
- Final homepage example must reflect the completed real experience, rather than prototype logic.

## Artwork and source reuse

`reference-assets/graphite-garment-style.png` freezes the approved light graphite style with alpha. It is a reference sheet, not a production sprite. The complete baseline asset plan, prompt template, generation workflow and transparency review are specified in `image-generation.md` and capability `catalog-imagery`. Final deliverables are separately reviewed one-garment alpha assets with no adjacent fragments.

MagicPath components contain usable React visual source, but each frame has its own duplicated common module. Export/inspect the pinned component revisions and consolidate shared components. Keep production API clients, router/session state, cursor logic and availability rules as specified here. The prototypes use four sample garments, simple local matching, simulated readiness, review toolbars and simplified pagination; these are not backend requirements or evidence of integration. Do not copy standalone fixed-width mobile wrappers into responsive production layouts.
