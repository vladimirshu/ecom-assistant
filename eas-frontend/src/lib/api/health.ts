/**
 * In-hours readiness probe: 2.5s timeout; 2xx and 429 are healthy, everything else
 * (network failure, timeout, 5xx, other 4xx) is unhealthy.
 */
import { apiUrl } from './client'

export type HealthResult = 'healthy' | 'unhealthy'

const TIMEOUT_MS = 2500

export async function checkHealth(signal?: AbortSignal): Promise<HealthResult> {
  const timeout = new AbortController()
  const timer = setTimeout(() => timeout.abort(new DOMException('timeout', 'TimeoutError')), TIMEOUT_MS)
  const onExternalAbort = () => timeout.abort(signal?.reason)
  signal?.addEventListener('abort', onExternalAbort, { once: true })

  try {
    const res = await fetch(apiUrl('/api/v1/health'), { method: 'GET', signal: timeout.signal })
    if (res.status >= 200 && res.status < 300) return 'healthy'
    if (res.status === 429) return 'healthy'
    return 'unhealthy'
  } catch {
    return 'unhealthy'
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', onExternalAbort)
  }
}
