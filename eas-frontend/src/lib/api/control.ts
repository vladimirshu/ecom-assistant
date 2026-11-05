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

export async function checkControl(signal?: AbortSignal): Promise<ControlResult> {
  try {
    const res = await fetch(apiUrl('/api/v1/control/up'), { method: 'GET', signal })
    if (!res.ok) return { state: 'error' }
    const body = (await res.json()) as Partial<ControlResponse>
    if (body.state === 'UP') return { state: 'UP' }
    if (body.state === 'STARTING') return { state: 'STARTING' }
    return { state: 'error' }
  } catch {
    return { state: 'error' }
  }
}
