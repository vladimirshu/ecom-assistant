# Authoring-facts schema

This document defines the versioned authoring-facts source that is the single source of
truth for the released apparel catalog. The import CSV is an export of this source and is
parity-checked against it. The facts are authoring/evaluation metadata only: they are not
exposed on the public `ProductDto` and are never injected into runtime search.

## Source layout

The source is split into part files under `src/main/resources/sampledata/facts/`, each a
complete `CatalogFacts` document (same `schemaVersion` and `catalogId`):

- `men-jackets-coats.json`, `men-knitwear.json`, `men-bottoms.json`
- `women-jackets-coats.json`, `women-knitwear.json`, `women-bottoms.json`

`CatalogFactsLoader.loadAllClasspath(...)` merges the parts into one logical catalog; the
exported CSV (`sampledata/apparel_catalog.csv`) is the single runtime seed configured via
`ecom.import.sample-file`.

## Document shape

```json
{
  "schemaVersion": 1,
  "catalogId": "atelier-apparel",
  "provenance": { "note": "..." },
  "products": [ { ... } ]
}
```

Each product carries the ordinary import fields plus `facts`:

```json
{
  "sku": "M-PUF-01",
  "title": "...",
  "category": "puffer",
  "categoryIds": ["men-puffer"],
  "colors": ["navy", "black"],
  "sizes": ["S", "M", "L"],
  "price": 199.00,
  "imageKey": null,
  "description": "...",
  "facts": {
    "materials": {
      "shell":  { "state": "known", "fibers": [ { "fiber": "nylon", "percent": 100 } ] },
      "lining": { "state": "known", "fibers": [ { "fiber": "nylon", "percent": 100 } ] },
      "fill":   { "state": "known", "fibers": [ { "fiber": "down", "percent": 90 }, { "fiber": "feather", "percent": 10 } ] }
    },
    "warmth": "high",
    "seasons": ["autumn", "winter"],
    "weatherProtection": "water-resistant",
    "windProtection": "medium",
    "hood": "fixed",
    "fit": "regular",
    "stretch": "none",
    "pockets": ["zip-hand", "chest"],
    "packable": false,
    "occasions": ["casual", "city"],
    "limitations": ["bulky", "not fully waterproof"]
  }
}
```

- `imageKey` is optional; when null/blank the import falls back to
  `categories/<category>.webp`. Non-blank keys must be `categories/<key>.webp`.
- `category` is the `ProductCategory` slug (one of the 17 garment styles).
- `categoryIds` are direct browsing memberships (must be known to the backend registry).

## Material states

`state` is one of `known`, `unknown`, `not-applicable`. Only `known` components carry
`fibers`, whose `percent` values must sum to 100. `unknown` means the composition is not
established and must not be treated as confirmed free of an excluded material;
`not-applicable` means the garment has no such component.

## Controlled vocabulary

- `warmth`: none, light, medium, high, extreme, unknown
- `seasons`: spring, summer, autumn, winter (list)
- `weatherProtection`: none, water-resistant, waterproof, unknown
- `windProtection`: none, low, medium, high, unknown
- `hood`: none, fixed, removable, unknown
- `fit`: slim, regular, relaxed, tailored, athletic, loose
- `stretch`: none, low, medium, high

Fiber names (exclusion semantics):

- Plant/cellulosic: cotton, linen, viscose, rayon, tencel, lyocell, modal
- Synthetic: polyester, nylon, polyamide, elastane, spandex, acrylic
- Animal wool family: wool, merino, lambswool, cashmere, alpaca, mohair, angora, silk
- Animal fill: down, feather
- Animal hide: leather, suede

"no wool / no animal fiber" matches the wool family, down/feather and leather/suede.
"no down" matches down and feather. "no leather" matches leather and suede. Cashmere is an
animal fiber even though its name lacks the word "wool".
