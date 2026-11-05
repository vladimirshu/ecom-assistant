// @vitest-environment node
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { fetchCatalogPage } from './catalog'
import type { Product } from './types'

const product: Product = {
  sku: 'JKT0001',
  title: 'Jacket',
  category: 'jackets',
  colors: [],
  sizes: [],
  price: 100,
  description: '',
  imageUrl: '/images/categories/puffer.webp',
}

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

// In the Node test environment undici needs an absolute URL; provide an ambient origin.
;(globalThis as { location?: { origin: string } }).location = { origin: 'http://localhost' }

describe('fetchCatalogPage', () => {
  it('parses a success page with a cursor', async () => {
    server.use(
      http.get('*/api/v1/products', () =>
        HttpResponse.json({ products: [product], nextCursor: 'opaque-token' }),
      ),
    )
    const result = await fetchCatalogPage({ category: 'men', cursor: 'prev' })
    expect(result).toEqual({ kind: 'success', products: [product], nextCursor: 'opaque-token' })
  })

  it('treats a null cursor as the end', async () => {
    server.use(
      http.get('*/api/v1/products', () => HttpResponse.json({ products: [product], nextCursor: null })),
    )
    expect(await fetchCatalogPage({})).toEqual({ kind: 'success', products: [product], nextCursor: null })
  })

  it('maps a 400 to invalidCursor', async () => {
    server.use(
      http.get('*/api/v1/products', () => HttpResponse.json({ error: 'expired cursor' }, { status: 400 })),
    )
    expect(await fetchCatalogPage({ cursor: 'expired' })).toEqual({
      kind: 'invalidCursor',
      message: 'expired cursor',
    })
  })

  it('maps a 500 to serverError', async () => {
    server.use(
      http.get('*/api/v1/products', () => HttpResponse.json({ error: 'boom' }, { status: 500 })),
    )
    expect(await fetchCatalogPage({})).toEqual({ kind: 'serverError' })
  })
})
