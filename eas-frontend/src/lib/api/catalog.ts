import type { Product } from './types'
import { apiUrl } from './client'

export type CatalogOutcome =
  | { kind: 'success'; products: Product[]; nextCursor: string | null }
  | { kind: 'invalidCursor'; message: string }
  | { kind: 'serverError' }
  | { kind: 'network' }
  | { kind: 'aborted' }

export interface CatalogRequest {
  limit?: number
  category?: string
  cursor?: string
}

export const CATALOG_PAGE_SIZE = 24

const TIMEOUT_MS = 30_000

function isTimeout(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'TimeoutError'
}

function isAbort(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'AbortError'
}

export async function fetchCatalogPage(
  req: CatalogRequest,
  signal?: AbortSignal,
): Promise<CatalogOutcome> {
  if (signal?.aborted) return { kind: 'aborted' }
  const timeout = new AbortController()
  const timer = setTimeout(() => timeout.abort(new DOMException('timeout', 'TimeoutError')), TIMEOUT_MS)

  const onExternalAbort = () => timeout.abort(signal?.reason)
  signal?.addEventListener('abort', onExternalAbort, { once: true })

  try {
    const params = new URLSearchParams({ limit: String(req.limit ?? CATALOG_PAGE_SIZE) })
    if (req.category) params.set('category', req.category)
    if (req.cursor) params.set('cursor', req.cursor)
    const res = await fetch(apiUrl(`/api/v1/products?${params.toString()}`), {
      method: 'GET',
      signal: timeout.signal,
    })
    return await toCatalogOutcome(res)
  } catch (err) {
    if (isTimeout(err)) return { kind: 'network' }
    if (isAbort(err)) return { kind: 'aborted' }
    return { kind: 'network' }
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', onExternalAbort)
  }
}

async function toCatalogOutcome(res: Response): Promise<CatalogOutcome> {
  if (res.status === 200) {
    const body = (await res.json()) as { products: Product[]; nextCursor: string | null }
    return { kind: 'success', products: body.products ?? [], nextCursor: body.nextCursor ?? null }
  }
  if (res.status === 400) {
    const body = (await res.json().catch(() => ({ error: '' }))) as { error: string }
    return { kind: 'invalidCursor', message: body.error ?? '' }
  }
  if (res.status >= 500) return { kind: 'serverError' }
  return { kind: 'serverError' }
}
