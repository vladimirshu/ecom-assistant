import { useEffect, useRef, useState } from 'react'
import type { Product } from '../lib/api/types'
import type { CatalogStatus } from '../catalog/useCatalog'
import { ProductGrid } from './ProductGrid'
import { SkeletonCard } from './SkeletonCard'
import { LoadMore } from './LoadMore'

export function CatalogList({
  products,
  nextCursor,
  status,
  selectedId,
  onLoadMore,
  onRetry,
  onReload,
  onOpenProduct,
}: {
  products: Product[]
  nextCursor: string | null
  status: CatalogStatus
  selectedId: string | null
  onLoadMore: () => void
  onRetry: () => void
  onReload: () => void
  onOpenProduct: (product: Product) => void
}) {
  const sentinelRef = useRef<HTMLDivElement>(null)
  const previousCount = useRef(0)
  const [announcement, setAnnouncement] = useState('')

  useEffect(() => {
    if (previousCount.current > 0 && products.length > previousCount.current) {
      setAnnouncement(`${products.length - previousCount.current} more products loaded`)
    }
    previousCount.current = products.length
  }, [products.length])

  // Automatic infinite append via IntersectionObserver.
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel) return
    if (typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting) && status === 'ready' && nextCursor !== null) {
          onLoadMore()
        }
      },
      { rootMargin: '200px' },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [status, nextCursor, onLoadMore])

  const isEmpty = products.length === 0 && status === 'ready' && nextCursor === null

  return (
    <div>
      <p className="sr-only" role="status" aria-live="polite">{announcement}</p>
      {products.length === 0 && status === 'loading' && (
        <div className="grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <SkeletonCard key={i} />
          ))}
        </div>
      )}

      {isEmpty && (
        <p className="text-muted">
          {selectedId ? 'No products in this category' : 'The collection is empty'}
        </p>
      )}

      {products.length > 0 && (
        <ProductGrid products={products} variant="catalog" onOpen={onOpenProduct} />
      )}

      {status === 'appending' && (
        <p className="mt-3 text-sm text-muted" role="status" aria-live="polite">
          Loading more…
        </p>
      )}

      {status === 'error' && (
        <div className="mt-4 rounded-sm border border-border bg-paper px-4 py-3">
          <p className="text-ink">Something went wrong loading the collection.</p>
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 rounded-sm bg-action px-4 py-2 text-sm text-paper hover:bg-action/90 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
          >
            Try again
          </button>
        </div>
      )}

      {status === 'invalidCursor' && (
        <div className="mt-4 rounded-sm border border-border bg-paper px-4 py-3">
          <p className="text-ink">This page of results has expired.</p>
          <button
            type="button"
            onClick={onReload}
            className="mt-2 rounded-sm bg-action px-4 py-2 text-sm text-paper hover:bg-action/90 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
          >
            Reload collection
          </button>
        </div>
      )}

      {products.length > 0 && nextCursor === null && status === 'ready' && (
        <p className="mt-4 text-sm text-muted">You've seen the whole collection</p>
      )}

      {nextCursor !== null && (status === 'ready' || status === 'appending') && (
        <div className="mt-4">
          <LoadMore onClick={onLoadMore} loading={status === 'appending'} />
        </div>
      )}

      <div ref={sentinelRef} aria-hidden="true" className="h-px" />
    </div>
  )
}
