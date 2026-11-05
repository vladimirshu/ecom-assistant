import type { Interpretation, Product } from './api/types'

interface DemoStep {
  query: string
  products: Product[]
  interpretation: Interpretation
}

/** Two cards per turn from a local seed-catalog-quality/v1 run, in observed order. */
export const DEMO_TIMELINE: DemoStep[] = [
  {
    "query": "navy jeans",
    "products": [
      {
        "sku": "M-JNS-10",
        "title": "Stretch Straight Jeans",
        "category": "Jeans",
        "colors": [
          "navy",
          "black"
        ],
        "sizes": [
          "30",
          "32",
          "34",
          "36"
        ],
        "price": 100,
        "description": "A straight jean with a whisper of stretch and a clean regular fit for easy office-casual wear. The cotton denim keeps a classic look while a little elastane adds give. Five pockets and a zip fly round it out. It is a dependable year-round pair that leans smart rather than rugged.",
        "imageUrl": "/images/categories/jeans.webp"
      },
      {
        "sku": "W-JNS-11",
        "title": "Womens Stretch Straight Jeans",
        "category": "Jeans",
        "colors": [
          "navy",
          "black"
        ],
        "sizes": [
          "26",
          "28",
          "30",
          "32"
        ],
        "price": 99,
        "description": "A women's straight jean with a whisper of stretch and a clean regular fit for easy office-casual wear. The cotton denim keeps a classic look while a little elastane adds give. Five pockets and a zip fly round it out. It is a dependable year-round pair that leans smart rather than rugged.",
        "imageUrl": "/images/categories/jeans.webp"
      }
    ],
    "interpretation": {
      "searchQuery": "navy jeans",
      "negativeExamples": [],
      "priceConstraint": null,
      "sortByLowestPrice": false
    }
  },
  {
    "query": "actually make it black",
    "products": [
      {
        "sku": "W-JNS-09",
        "title": "Womens Black Skinny Jeans",
        "category": "Jeans",
        "colors": [
          "black"
        ],
        "sizes": [
          "26",
          "28",
          "30",
          "32"
        ],
        "price": 93,
        "description": "A women's black skinny jean with medium stretch and a sleek, dressy look for evenings. The cotton and elastane denim holds a dark wash and hugs the leg without pinching. Five pockets and a slim ankle keep it sharp. It pairs with boots or heels and suits a smart night out.",
        "imageUrl": "/images/categories/jeans.webp"
      },
      {
        "sku": "M-JNS-11",
        "title": "Black Skinny Jeans",
        "category": "Jeans",
        "colors": [
          "black"
        ],
        "sizes": [
          "28",
          "30",
          "32",
          "34"
        ],
        "price": 94,
        "description": "A black skinny jean with medium stretch and a sleek, dressy look for evenings. The cotton and elastane denim holds a dark wash and hugs the leg without pinching. Five pockets and a slim ankle keep it sharp. It pairs with boots or trainers and suits a smart night out.",
        "imageUrl": "/images/categories/jeans.webp"
      }
    ],
    "interpretation": {
      "searchQuery": "black jeans",
      "negativeExamples": [],
      "priceConstraint": null,
      "sortByLowestPrice": false
    }
  }
]
