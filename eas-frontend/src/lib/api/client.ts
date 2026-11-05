/**
 * Build a same-origin API URL. In a browser this stays a relative URL; in a
 * Node/test context (where undici requires an absolute URL) it is derived from
 * the ambient location, so no backend host is ever configured.
 */
export function apiUrl(pathWithQuery: string): string {
  const origin = (globalThis as { location?: { origin?: string } }).location?.origin
  if (origin) return `${origin}${pathWithQuery}`
  return pathWithQuery
}
