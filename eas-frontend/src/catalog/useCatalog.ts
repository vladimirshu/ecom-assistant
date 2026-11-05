import { useCallback, useEffect, useRef, useState } from 'react'
import type { Product } from '../lib/api/types'
import { fetchCatalogPage } from '../lib/api/catalog'
import { useGate } from '../gate/GateContext'

export type CatalogStatus = 'idle' | 'loading' | 'ready' | 'appending' | 'error' | 'invalidCursor'

export interface CatalogState {
  products: Product[]
  nextCursor: string | null
  status: CatalogStatus
  errorMessage: string
  loadFirst: () => Promise<void>
  loadMore: () => Promise<void>
  retry: () => Promise<void>
  reload: () => Promise<void>
}

export function useCatalog(category: string | undefined): CatalogState {
  const gate = useGate()

  const [products, setProducts] = useState<Product[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [status, setStatus] = useState<CatalogStatus>('idle')
  const [errorMessage, setErrorMessage] = useState('')

  const generation = useRef(0)
  const abortRef = useRef<AbortController | null>(null)
  const inFlight = useRef(false)

  const requestPage = useCallback(
    async (cursor: string | undefined, append: boolean): Promise<void> => {
      if (inFlight.current) return
      inFlight.current = true
      generation.current += 1
      const gen = generation.current
      const ready = await gate.ensureReady()
      if (gen !== generation.current) return
      if (!ready) {
        inFlight.current = false
        return
      }
      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller

      setStatus(append ? 'appending' : 'loading')
      setErrorMessage('')

      const outcome = await fetchCatalogPage(
        { category, cursor },
        controller.signal,
      )
      if (gen !== generation.current) {
        return
      }
      inFlight.current = false

      switch (outcome.kind) {
        case 'success':
          setProducts((prev) => {
            if (!append) return outcome.products
            const seen = new Set(prev.map((p) => p.sku))
            const additions = outcome.products.filter((p) => {
              if (seen.has(p.sku)) return false
              seen.add(p.sku)
              return true
            })
            return [...prev, ...additions]
          })
          setNextCursor(outcome.nextCursor)
          setStatus('ready')
          return
        case 'invalidCursor':
          setStatus('invalidCursor')
          setErrorMessage(outcome.message)
          return
        case 'serverError':
        case 'network':
          setStatus('error')
          void gate.reportFailure()
          return
        case 'aborted':
          return
      }
    },
    [category, gate],
  )

  const loadFirst = useCallback(async () => {
    generation.current += 1
    abortRef.current?.abort()
    inFlight.current = false
    setProducts([])
    setNextCursor(null)
    setStatus('idle')
    await requestPage(undefined, false)
  }, [requestPage])

  const loadMore = useCallback(async () => {
    if (inFlight.current || nextCursor === null) return
    await requestPage(nextCursor, true)
  }, [nextCursor, requestPage])

  const retry = useCallback(async () => {
    await requestPage(nextCursor ?? undefined, nextCursor !== null)
  }, [nextCursor, requestPage])

  const reload = useCallback(async () => {
    await loadFirst()
  }, [loadFirst])

  useEffect(() => {
    return () => {
      generation.current += 1
      abortRef.current?.abort()
    }
  }, [])

  return { products, nextCursor, status, errorMessage, loadFirst, loadMore, retry, reload }
}
