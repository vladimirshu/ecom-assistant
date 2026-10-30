# Catalog image generation brief

This is the production brief for capability `catalog-imagery`. It records the approved light pencil style and the complete initial coverage plan. Creating this specification does not generate or approve the full asset set.

## Style anchor

Use `reference-assets/graphite-garment-style.png` as the visual reference for every generation/revision. Preserve its light pencil treatment and readable fabric detail. The earlier very dark leather-jacket sample is not the tonal target. The beanie in the reference is a style sample, not an expansion of the clothing assortment.

- Fine graphite contours, restrained crosshatching and soft gray shading. Leave substantial unshaded areas; avoid dense black fills and photographic gloss.
- One isolated full garment, frontal view, centered, natural construction, no body/mannequin/hanger.
- Genuine transparent alpha outside the silhouette and in structural gaps. Fabric interiors may retain opaque light-gray/white shading; do not erase light fabric with the background.
- No paper texture, colored background, white rectangular plate, checkerboard pattern, floor/shadow, annotations, logos or prices.
- Neutral illustration of a garment type; do not encode available colors into the drawing.

## Baseline asset plan

The existing 13 jacket styles plus four expanded apparel styles require 17 garment images; default brings the initial minimum to 18. Final keys match backend default image-key conventions. `denim` means a jacket; `jeans` means trousers.

| Image key | Final filename | Garment-specific prompt subject and distinguishing details |
|---|---|---|
| puffer | puffer.webp | Quilted puffer jacket, clear padded horizontal baffles, zip front, stand collar; lightly shaded rather than solid black |
| parka | parka.webp | Mid-thigh hooded parka, longer hem, simple flap pockets, weather-protective silhouette; no fur trim |
| coat | coat.webp | Mid-length single-breasted coat, notched lapels, straight hem and clear button closure |
| rain | rain.webp | Lightweight hooded rain jacket, simple zip front, adjustable cuffs, smooth fabric without heavy padding |
| softshell | softshell.webp | Close-fitting softshell jacket with stand collar, shaped seams and zip pockets, smooth flexible fabric |
| fleece | fleece.webp | Zip-front fleece jacket, stand collar and subtly suggested soft pile texture, restrained pencil marks |
| track | track.webp | Track jacket with ribbed hem/cuffs, zip front and simple panel seams; no logos, lettering or bright stripes |
| technical | technical.webp | Technical outdoor shell jacket with structured hood, high collar and articulated panels, no bulky insulation |
| field | field.webp | Field jacket with four flap pockets, structured collar and simple front fastening, subtle woven fabric |
| bomber | bomber.webp | Waist-length bomber jacket, ribbed collar/hem/cuffs, zip front and rounded sleeves |
| denim | denim.webp | Denim trucker jacket, pointed collar, button front, chest flap pockets and visible yoke seams |
| leather | leather.webp | Leather biker jacket matching the approved sample: notched lapels, asymmetric zip, two zipped pockets; convey leather with sparse folds and highlights |
| windbreaker | windbreaker.webp | Lightweight packable windbreaker, simple hood and zip, thin fabric folds and elastic cuffs |
| sweater | sweater.webp | Crew-neck cable-knit sweater matching the approved sample, full sleeves, ribbed collar/hem/cuffs, readable but delicate knit pattern |
| hoodie | hoodie.webp | Pullover hoodie matching the approved sample, hood, plausible drawstrings, kangaroo pocket and ribbed cuffs |
| jeans | jeans.webp | Complete straight-leg five-pocket jeans, waistband/belt loops/fly and two fully visible legs, lightly indicated denim texture |
| trousers | trousers.webp | Straight-leg tailored trousers or chinos, waistband, simple fly and slanted side pockets; distinguish from denim jeans with cleaner fabric and construction |
| default | default.webp | A simple folded crew-neck sweater, compact neutral silhouette and sparse knit marks; no text, question mark, box, hanger or background |

This is coverage by garment type, not by browsing node. The Men/Women roots, Jackets & Coats grouping and other intermediate nodes may reuse suitable illustrations when representative images are desired. Their optional imageKey is omitted when no illustration is needed. Every garment leaf and every actual backend image key must still be covered by the production manifest. Do not create gender/color/size duplicates automatically.

If the authored catalog includes a materially different cut, add a specific key such as `leather-fitted` or `coat-long` and put `categories/<key>.webp` explicitly in that product's image field. Validate its use against descriptions; keys are stable asset identifiers, not category navigation ids. A shared neutral garment is acceptable for both roots only where its silhouette is suitable. Add T-shirt or accessory assets only when those categories are explicitly brought into scope. Do not generate underwear/lingerie/undershirts/tank tops/bras.

## Common generation prompt

Supply the approved reference image as a style reference, not as a multi-garment edit target. Use the same template for every subject, replacing only the garment brief and any required cut. Request native transparency from the generation tool in addition to describing it in text.

```text
Use case: product-mockup.
Asset type: one standalone clothing illustration for an ATELIER apparel product card.
Primary request: {garment-specific subject from the asset plan}.
Reference: match the supplied approved light graphite garment reference in pencil line weight,
shading density, material detail and overall refinement. Draw only the requested garment.
Style: delicate graphite pencil contours, sparse controlled crosshatching, soft neutral gray
midtones, generous unshaded areas. Clearly hand-drawn. No dense black fills or photographic rendering.
Composition: one complete garment, front view, centered and fully visible with consistent clear
margins in a 4:5 portrait frame. Natural garment construction, no visible person or mannequin.
Background: genuinely transparent alpha outside the garment silhouette, including natural gaps
between sleeves and body or between trouser legs. Keep fabric interiors and graphite marks intact.
Materials: indicate the requested fabric with subtle marks; do not add product-specific features
or material claims absent from the garment brief.
Constraints: no paper, background texture, white panel, checkerboard, floor, cast shadow, hanger,
props, extra garment, text, price, brand, logo or watermark. Do not crop sleeves, hood, hem or legs.
The final drawing should feel light and consistent with the reference when placed next to the other
catalog garments on a soft Sage website.
```

For a revision, identify the existing asset as the edit target, preserve garment construction/framing/style and request the smallest correction: for example lighten shading, remove an alpha halo or correct one malformed cuff. Do not silently regenerate every approved item after a single failure.

## Composition and export

Use the central roughly 80% of the frame, retaining approximately 6–10% safety margins wherever possible. Fit naturally elongated items such as trousers by their height and broad items by their width; preserve proportions rather than forcing every silhouette to the same width. Compare optical scale in a mixed grid, including the compact default. Reserve a 4:5 image area in the UI and contain the full garment.

Generation-tool output size may differ from the export target. Resize proportionally and, if needed, pad with transparent pixels to 1200×1500. Never stretch, crop away garment parts or flatten alpha. Export individual alpha WebP files targeting <=200 KB. Reject noticeable detail loss or edge contamination; revise/optimize and recheck. Keep editable/original transparent source files in a non-public source location and only optimized final files in `public/images/categories/`. Final assets must be ordinary durable repository/workspace files, not expiring generation URLs.

## Manifest to implement

Use a dedicated editable manifest, proposed `src/data/catalog-images.json`, listing the required baseline keys plus any actual product-specific keys. It must remain separate from category navigation and product metadata. Consume it in generation/review/build tooling; do not expose provenance or review controls in the storefront UI.

For each entry record:
- imageKey and final filename;
- represented garment/style and intended cut/audience;
- category ids/actual product image references it is meant to cover;
- shared prompt version, garment brief and any revision prompt;
- reference/source image path, generation tool/model when available, date and revision;
- review status (planned/generated/needs-revision/approved/exported), final dimensions/bytes/alpha check;
- non-public source location and final static file path.

Do not invent a seed, model identifier or provenance that the generation tool does not provide. Generation implementation should follow the currently available image-generation skill/tool workflow, using one call for each distinct required asset and retaining the approved reference. The generation brief is tool-independent; no runtime image-generation service is added to the shop.

## Acceptance checklist

1. Coverage: all 18 baseline files plus every actually referenced extra key; no automatic category creation from reference imagery.
2. Content: one correct garment per image, complete silhouette, plausible pockets/zip/hood/legs, no text, logo, person or neighboring item.
3. Alpha: meaningful transparency, clean contours, preserved fabric interiors, natural gaps and no baked white plate or checkerboard. An alpha-channel flag alone is insufficient.
4. Style: compare in one mixed-category grid at approximately 150–220 px image widths; reject heavier shading, inconsistent linework or optical scale.
5. Surfaces: inspect over paper #F6F8F0, Sage wash #E5ECD7, the product-dialog surface and a contrasting QA background to reveal fringes. The contrasting QA background is not a production theme change.
6. Detail: open the full-size dialog and check seams, cuffs, drawstrings and material marks. Include 390 px mobile cards in the review.
7. Delivery: 1200×1500 alpha WebP, target <=200 KB, stable filenames, original source/provenance retained, static files copied into dist without flattening.
8. Failure: default.webp is approved in the same style and transparent; missing required assets fail release validation even though the runtime fallback remains available.

Record the review per asset. Only technically checked and visually accepted exports count as complete. Retain known-good revisions while replacements are reviewed.
