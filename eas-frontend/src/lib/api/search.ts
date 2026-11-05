import type {
  FieldErrors,
  Interpretation,
  Product,
  RateLimitCode,
  SearchRejectionBody,
  SearchSuccessBody,
} from './types'
import { apiUrl } from './client'

export type SearchOutcome =
  | { kind: 'success'; products: Product[]; interpretation: Interpretation }
  | { kind: 'rejected'; suggestedPrompts: string[] }
  | { kind: 'fieldError'; errors: FieldErrors }
  | { kind: 'rateLimited'; code: RateLimitCode; message: string; retryAfterSeconds: number | null }
  | { kind: 'serverError' }
  | { kind: 'network' }
  | { kind: 'aborted' }

export interface SearchRequest {
  message: string
  conversationId: string
  clearMemory: boolean
}

const TIMEOUT_MS = 30_000

function isTimeout(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'TimeoutError'
}

function isAbort(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'AbortError'
}

/** Resolve a search response body into an outcome. */
export async function searchProducts(
  req: SearchRequest,
  signal?: AbortSignal,
): Promise<SearchOutcome> {
  if (signal?.aborted) return { kind: 'aborted' }
  const timeout = new AbortController()
  const timer = setTimeout(() => timeout.abort(new DOMException('timeout', 'TimeoutError')), TIMEOUT_MS)

  const onExternalAbort = () => timeout.abort(signal?.reason)
  signal?.addEventListener('abort', onExternalAbort, { once: true })

  try {
    const params = new URLSearchParams({
      message: req.message,
      conversationId: req.conversationId,
      clearMemory: String(req.clearMemory),
    })
    const res = await fetch(apiUrl(`/api/v1/search?${params.toString()}`), {
      method: 'GET',
      signal: timeout.signal,
    })
    return await toSearchOutcome(res)
  } catch (err) {
    if (isTimeout(err)) return { kind: 'network' }
    if (isAbort(err)) return { kind: 'aborted' }
    return { kind: 'network' }
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', onExternalAbort)
  }
}

async function toSearchOutcome(res: Response): Promise<SearchOutcome> {
  if (res.status === 200) {
    const body = (await res.json()) as SearchSuccessBody
    return { kind: 'success', products: body.products, interpretation: body.interpretation }
  }
  if (res.status === 422) {
    const body = (await res.json()) as SearchRejectionBody
    return { kind: 'rejected', suggestedPrompts: body.suggestedPrompts ?? [] }
  }
  if (res.status === 400) {
    const body = (await res.json().catch(() => ({}))) as FieldErrors
    return { kind: 'fieldError', errors: body }
  }
  if (res.status === 429) {
    const body = (await res.json().catch(() => ({ code: 'RATE_LIMITED', message: '' }))) as {
      code: RateLimitCode
      message: string
    }
    const retryAfter = parseRetryAfter(res.headers.get('Retry-After'))
    return {
      kind: 'rateLimited',
      code: body.code,
      message: body.message ?? '',
      retryAfterSeconds: retryAfter,
    }
  }
  if (res.status >= 500) {
    return { kind: 'serverError' }
  }
  // Unexpected status: treat as a server-side problem for recovery purposes.
  return { kind: 'serverError' }
}

function parseRetryAfter(value: string | null): number | null {
  if (!value) return null
  const seconds = Number(value)
  return Number.isFinite(seconds) && seconds >= 0 ? Math.ceil(seconds) : null
}
