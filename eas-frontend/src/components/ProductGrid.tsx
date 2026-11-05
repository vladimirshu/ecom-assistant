import type { Product } from '../lib/api/types'
import { ProductCard } from './ProductCard'

export type GridVariant = 'search' | 'catalog'

export function ProductGrid({
  products,
  variant = 'search',
  onOpen,
}: {
  products: Product[]
  variant?: GridVariant
  onOpen: (product: Product) => void
}) {
  const columns =
    variant === 'catalog'
      ? 'grid-cols-2 @[34rem]:grid-cols-3 @[48rem]:grid-cols-4'
      : 'grid-cols-2 @[34rem]:grid-cols-3'
  return (
    <div className="@container">
      <ul className={`grid list-none gap-x-4 gap-y-6 p-0 ${columns}`}>
        {products.map((product) => (
          <li key={product.sku}>
            <ProductCard product={product} onOpen={onOpen} />
          </li>
        ))}
      </ul>
    </div>
  )
}
