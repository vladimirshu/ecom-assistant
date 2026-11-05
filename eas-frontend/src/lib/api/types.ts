export interface Product {
  sku: string
  title: string
  category: string
  colors: string[]
  sizes: string[]
  price: number
  description: string
  imageUrl: string | null
}

export type PriceOperator =
  | 'LESS_THAN'
  | 'LESS_THAN_OR_EQUAL'
  | 'GREATER_THAN'
  | 'GREATER_THAN_OR_EQUAL'

export interface PriceConstraint {
  operator: PriceOperator
  value: number
}

export interface Interpretation {
  searchQuery: string
  negativeExamples: string[]
  priceConstraint: PriceConstraint | null
  sortByLowestPrice: boolean
}

export interface SearchSuccessBody {
  products: Product[]
  interpretation: Interpretation
  suggestedPrompts: null
}

export interface SearchRejectionBody {
  products: null
  interpretation: null
  suggestedPrompts: string[]
}

export type RateLimitCode = 'RATE_LIMITED' | 'DAILY_BUDGET_EXHAUSTED'

export interface RateLimitBody {
  code: RateLimitCode
  message: string
}

export interface ServerErrorBody {
  error: string
}

/** Backend 400 body maps a field name to an error message. */
export type FieldErrors = Record<string, string>

export interface CatalogPageBody {
  products: Product[]
  nextCursor: string | null
}

export interface ControlResponse {
  state: 'UP' | 'STARTING'
}
