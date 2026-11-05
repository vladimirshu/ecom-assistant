import type { Product } from '../lib/api/types'
import { formatPrice } from '../lib/format'
import { SafeImage } from './SafeImage'

export function ProductCard({ product, onOpen }: { product: Product; onOpen: (product: Product) => void }) {
  return (
    <button
      type="button"
      onClick={() => onOpen(product)}
      className="group flex w-full flex-col text-left focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
    >
      <div className="aspect-[4/5] w-full overflow-hidden rounded-sm bg-wash/30">
        <SafeImage
          src={product.imageUrl}
          alt={product.title}
          className="h-full w-full object-contain"
        />
      </div>
      <h3 className="mt-2 font-serif text-base leading-snug text-ink">{product.title}</h3>
      <p className="mt-0.5 text-sm text-muted">{formatPrice(product.price)}</p>
      <span className="mt-1 text-sm text-action underline-offset-2 group-hover:underline">
        View details
      </span>
    </button>
  )
}
