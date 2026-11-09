import type { ControlResponse } from './types'
import { apiUrl } from './client'

/**
 * Control-plane readiness: a valid `{ state: "UP" | "STARTING" }` body is required.
 * HTTP 200 alone is insufficient. Any failure or malformed body is `error`.
 */
export type ControlResult =
  | { state: 'UP' }
  | { state: 'STARTING' }
  | { state: 'error' }

const TIMEOUT_MS = 5000

export async function checkControl(signal?: AbortSignal): Promise<ControlResult> {
  if (signal?.aborted) return { state: 'error' }
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
  const onExternalAbort = () => controller.abort(signal?.reason)
  signal?.addEventListener('abort', onExternalAbort, { once: true })

  try {
    const res = await fetch(apiUrl('/api/v1/control/up'), { method: 'GET', signal: controller.signal })
    if (!res.ok) return { state: 'error' }
    const body = (await res.json()) as Partial<ControlResponse>
    if (body.state === 'UP') return { state: 'UP' }
    if (body.state === 'STARTING') return { state: 'STARTING' }
    return { state: 'error' }
  } catch {
    return { state: 'error' }
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', onExternalAbort)
  }
}
