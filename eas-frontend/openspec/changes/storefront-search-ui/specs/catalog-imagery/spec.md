## ADDED Requirements

### Requirement: Approved illustration style
All generated catalog imagery SHALL follow the light graphite style frozen in `reference-assets/graphite-garment-style.png` and the production brief in `image-generation.md`: fine pencil contours, restrained crosshatching, soft midtones, legible material texture and generous unshaded areas. Imagery SHALL remain monochrome, recognizable at card size and visually consistent when different garments appear next to each other. A photograph, photorealistic render, solid black silhouette, heavy charcoal treatment or colored illustration SHALL NOT substitute for the approved style.

#### Scenario: Consistent collection
- **WHEN** jacket, sweater, hoodie, jeans and trousers assets are reviewed in one product grid
- **THEN** their line weight, tonal density, garment scale and viewpoint read as one illustration set, without one asset dominating as a much darker block

### Requirement: Genuine transparent background
Every final illustration, including the fallback, SHALL use genuine alpha transparency outside its garment silhouette. Background areas and natural gaps between sleeves and body or trouser legs SHALL be transparent. Opaque graphite/fabric shading inside the garment SHALL be preserved where needed. There SHALL be no paper rectangle, textured background, drawn checkerboard, white halo, ground plane or cast shadow around the garment. Merely adding an alpha channel to an otherwise opaque image SHALL NOT satisfy this requirement. CSS grayscale, multiply blending or a white background matching one page surface SHALL NOT be used as background removal.

#### Scenario: Background-independent appearance
- **WHEN** an illustration is composited over paper #F6F8F0, Sage wash #E5ECD7 and a contrasting inspection surface
- **THEN** each surface remains visible around the garment and through genuine cutouts, with no white rectangular plate or fringe

#### Scenario: Alpha survives export
- **WHEN** a generated transparent source is optimized into WebP
- **THEN** the exported image retains meaningful transparent pixels, opaque garment pixels and clean anti-aliased edges

### Requirement: Full initial category coverage
The initial image set SHALL contain one separately reviewable asset for each of the 17 agreed garment-style keys: puffer, parka, coat, rain, softshell, fleece, track, technical, field, bomber, denim, leather, windbreaker, sweater, hoodie, jeans and trousers. It SHALL additionally include default, for a baseline of 18 final files. Every product image key used by the released catalog and every optional category-tree imageKey SHALL resolve to an approved file. Any later product/category style SHALL extend the coverage manifest before release.

Men/Women roots and intermediate browsing nodes are category navigation, not additional garment styles. They SHALL either reference an appropriate existing representative illustration or omit their optional imageKey; they SHALL NOT require duplicate drawings solely because their browsing ids differ. T-shirts and accessories, including the style-reference beanie, SHALL remain outside this initial image set unless separately added to the catalog. Underwear, lingerie, undershirts, tank tops and bras SHALL NOT be generated for this assortment.

#### Scenario: Initial completeness
- **WHEN** the initial asset set is prepared for release
- **THEN** all 17 garment-style keys and default have approved alpha WebP files, and there are no unresolved catalog or category-tree image references

#### Scenario: Reference accessory is not a new category
- **WHEN** the reference sheet includes a beanie
- **THEN** it is used only to judge illustration style and does not silently add an accessory category or required beanie asset

### Requirement: Product and category image mapping
A maintained asset manifest SHALL record each image key, filename, represented garment/style, relevant category-tree ids, intended cut/audience where applicable, source/reference, generation prompt, revision and review/export status. Image keys SHALL remain independent of category ids. A representative image MAY be shared across Men/Women, color and size variants when its silhouette remains suitable; color and size availability SHALL come from product data, not inferred from grayscale artwork. If a materially different cut requires another drawing, a stable distinct key SHALL be added and explicitly referenced by the backend product image field. A missing required image SHALL fail coverage validation rather than being silently accepted because runtime fallback exists.

#### Scenario: Color variants
- **WHEN** one hoodie is available in sage, navy and cream
- **THEN** one approved neutral drawing may represent those variants without generating three differently colored images

#### Scenario: Distinct cut
- **WHEN** a product uses a substantially different fitted, cropped or long silhouette from the representative asset
- **THEN** its image mapping is reviewed and a suitable distinct asset/key is supplied when the shared illustration would misrepresent the garment

### Requirement: One garment per final asset
Each released image SHALL contain one complete isolated garment, front view, centered without visible mannequin, person, hands, hanger, props, text, logos or another garment. Seams, hood, lapels, pockets, cuffs, waistband and leg shapes SHALL be structurally plausible for the named type. Each image SHALL fit the reserved 4:5 area with consistent visual scale and clear safety margins, keeping the full silhouette intact. Generation SHALL use a separate request for each distinct asset or explicitly identified revision; a multi-garment sheet may be retained as a reference but SHALL NOT become a CSS sprite or final cropped screenshot asset.

#### Scenario: Complete jacket
- **WHEN** a leather illustration is reviewed
- **THEN** both sleeves, cuffs, lapels and hem are fully visible with no cropped edge or neighboring garment fragment

#### Scenario: Jeans versus denim jacket
- **WHEN** denim.webp and jeans.webp are reviewed
- **THEN** denim.webp represents a denim jacket and jeans.webp represents a complete pair of jeans, with distinct manifest entries and filenames

### Requirement: Static deliverables and provenance
Final assets SHALL be individual alpha WebP files at `public/images/categories/<image-key>.webp`, with flat stable filenames and a transparent default.webp. Export target SHALL be 1200×1500 pixels (4:5), with the full garment contained without stretching, targeting at most 200 KB per file. Source images SHALL retain their original transparency; processing SHALL NOT flatten the alpha onto a background. Prompt and generation provenance SHALL be preserved alongside the asset manifest, with sources kept in a designated non-public source location. Production delivery SHALL copy the approved final files into the frontend workspace and static build output, not leave them only in a tool's generated-images folder or depend on expiring URLs.

#### Scenario: Stable static delivery
- **WHEN** the frontend is built
- **THEN** each manifest filename exists under dist/images/categories/ and the production URLs remain independent of generation-tool storage

#### Scenario: Oversized export
- **WHEN** an exported image exceeds 200 KB
- **THEN** it is re-optimized or regenerated and visually rechecked without removing alpha or blurring the pencil detail merely to meet the size target

### Requirement: Generation and collection acceptance
Generation SHALL use the approved reference and the common prompt template plus the garment-specific brief from `image-generation.md`. The workflow SHALL preserve an approved style sample, generate/revise assets individually, validate coverage/format/alpha, and visually inspect both individual images and a mixed-category grid before release. The matrix SHALL include paper, wash and product-dialog surfaces, mobile cards and a full-size dialog. A successful generation call alone SHALL NOT mark an image approved. Review status SHALL distinguish planned, generated, needs-revision, approved and exported. Known-good assets SHALL NOT be overwritten without retaining their source/revision until replacement is accepted.

#### Scenario: Artifact found in review
- **WHEN** a generated hoodie has malformed drawstrings or a visible background halo
- **THEN** that asset remains needs-revision and is excluded from the release set until corrected and rechecked

#### Scenario: Whole-set acceptance
- **WHEN** all required images have been generated
- **THEN** both technical checks and a mixed-category visual review pass before the image-generation tasks are marked complete
