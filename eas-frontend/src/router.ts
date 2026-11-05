import { useSyncExternalStore } from 'react'

function subscribe(callback: () => void): () => void {
  window.addEventListener('popstate', callback)
  return () => window.removeEventListener('popstate', callback)
}

function getLocationSnapshot(): string {
  return window.location.pathname + window.location.search
}

/** Reactive browser location (pathname + search), updated on popstate and internal navigation. */
export function useLocation(): string {
  return useSyncExternalStore(subscribe, getLocationSnapshot)
}

/** Navigate using the history API without a full reload, then notify subscribers. */
export function navigate(to: string): void {
  if (getLocationSnapshot() === to) return
  window.history.pushState({}, '', to)
  window.dispatchEvent(new PopStateEvent('popstate'))
}

export function currentSearchParams(): URLSearchParams {
  return new URLSearchParams(window.location.search)
}

/** Build a /catalog URL for a category, preserving unrelated query parameters. */
export function catalogUrl(categoryId: string): string {
  const params = new URLSearchParams(window.location.search)
  params.set('category', categoryId)
  return `/catalog?${params.toString()}`
}

/** Build the search homepage URL, preserving unrelated query parameters and dropping `category`. */
export function searchUrl(): string {
  const params = new URLSearchParams(window.location.search)
  params.delete('category')
  const qs = params.toString()
  return qs ? `/?${qs}` : '/'
}

/** Build the All-clothing catalog URL, preserving unrelated query parameters and dropping `category`. */
export function catalogAllUrl(): string {
  const params = new URLSearchParams(window.location.search)
  params.delete('category')
  const qs = params.toString()
  return qs ? `/catalog?${qs}` : '/catalog'
}

/** Parse the current location into a route and its query parameters. */
export function parseLocation(location: string): {
  path: string
  searchParams: URLSearchParams
} {
  const [path, query = ''] = location.split('?')
  return { path, searchParams: new URLSearchParams(query) }
}
