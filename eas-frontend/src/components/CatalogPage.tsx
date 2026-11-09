import { useEffect, useRef } from 'react'
import type { Product } from '../lib/api/types'
import { findCategory, isKnownCategory } from '../lib/categories'
import { useCatalog } from '../catalog/useCatalog'
import { useGate } from '../gate/GateContext'
import { CategoryBreadcrumbs } from './CategoryBreadcrumbs'
import { CatalogNavigation } from './CatalogNavigation'
import { CatalogList } from './CatalogList'

export function CatalogPage({
  selectedId,
  onSelectCategory,
  onBackToSearch,
  onOpenProduct,
}: {
  selectedId: string | null
  onSelectCategory: (id: string | null) => void
  onBackToSearch: () => void
  onOpenProduct: (product: Product) => void
}) {
  const unknown = selectedId !== null && !isKnownCategory(selectedId)
  const effectiveCategory = unknown ? undefined : (selectedId ?? undefined)
  const catalog = useCatalog(effectiveCategory)
  const { isHealthy } = useGate()

  const startedRef = useRef(false)

  useEffect(() => {
    startedRef.current = false
  }, [effectiveCategory])

  useEffect(() => {
    if (!isHealthy && catalog.status === 'idle') startedRef.current = false
    if (!unknown && isHealthy && !startedRef.current) {
      startedRef.current = true
      void catalog.loadFirst()
    }
  }, [unknown, isHealthy, effectiveCategory, catalog])

  const heading = selectedId === null ? 'All clothing' : (findCategory(selectedId)?.name ?? selectedId)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <h1 className="font-serif text-2xl text-ink">{heading}</h1>
          <div className="mt-1">
            <CategoryBreadcrumbs selectedId={selectedId} onSelect={onSelectCategory} />
          </div>
        </div>
        <button
          type="button"
          onClick={onBackToSearch}
          className="text-sm text-muted underline-offset-2 hover:text-action hover:underline focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
        >
          Back to search
        </button>
      </div>

      {unknown ? (
        <div className="rounded-sm border border-border bg-paper px-4 py-6 text-center">
          <p className="text-ink">Category not found</p>
          <button
            type="button"
            onClick={() => onSelectCategory(null)}
            className="mt-2 text-sm text-action underline-offset-2 hover:underline focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
          >
            All clothing
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-[240px_minmax(0,1fr)]">
          <div>
            <CatalogNavigation selectedId={selectedId} onSelect={onSelectCategory} />
          </div>
          <div>
            <CatalogList
              products={catalog.products}
              nextCursor={catalog.nextCursor}
              status={catalog.status}
              selectedId={selectedId}
              onLoadMore={catalog.loadMore}
              onRetry={catalog.retry}
              onReload={catalog.reload}
              onOpenProduct={onOpenProduct}
            />
          </div>
        </div>
      )}
    </div>
  )
}
