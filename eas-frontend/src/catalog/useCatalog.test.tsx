// @vitest-environment jsdom
import { act, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'
import { AvailabilityGateContext } from '../gate/GateContext'
import type { AvailabilityGate } from '../gate/useAvailabilityGate'
import { fetchCatalogPage, type CatalogOutcome } from '../lib/api/catalog'
import { useCatalog } from './useCatalog'

vi.mock('../lib/api/catalog', () => ({ fetchCatalogPage: vi.fn() }))

const gate: AvailabilityGate = {
  phase: 'healthy', isHealthy: true, ensureReady: async () => true,
  reportFailure: async () => {}, startDemo: () => {}, retry: () => {}, busy: false,
}
const fetchPage = vi.mocked(fetchCatalogPage)

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}

function Catalog({ category }: { category: string }) {
  const catalog = useCatalog(category)
  return <div><button onClick={catalog.loadFirst}>Load {category}</button><span>{catalog.products.map((p) => p.sku).join(',')}</span></div>
}

function app(category: string) {
  return <AvailabilityGateContext.Provider value={gate}><Catalog key={category} category={category} /></AvailabilityGateContext.Provider>
}

beforeEach(() => fetchPage.mockReset())

it('aborts the old category and ignores its late response when a new category loads', async () => {
  const old = deferred<CatalogOutcome>()
  const next = deferred<CatalogOutcome>()
  fetchPage.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise)
  const view = render(app('men'))
  act(() => screen.getByText('Load men').click())
  await waitFor(() => expect(fetchPage).toHaveBeenCalledTimes(1))
  const oldSignal = fetchPage.mock.calls[0][1]!
  view.rerender(app('women'))
  expect(oldSignal.aborted).toBe(true)
  act(() => screen.getByText('Load women').click())
  await waitFor(() => expect(fetchPage).toHaveBeenCalledTimes(2))
  expect(fetchPage.mock.calls[1][0]).toEqual({ category: 'women', cursor: undefined })
  const product = (sku: string) => ({ sku, title: sku, category: '', colors: [], sizes: [], price: 1, description: '', imageUrl: '' })
  await act(async () => {
    next.resolve({ kind: 'success', products: [product('WOMEN')], nextCursor: null })
    old.resolve({ kind: 'success', products: [product('MEN')], nextCursor: null })
  })
  expect(screen.getByText('WOMEN')).toBeTruthy()
  expect(screen.queryByText('MEN')).toBeNull()
})
