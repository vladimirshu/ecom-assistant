// @vitest-environment node
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import { searchProducts } from './search'
import type { Product, Interpretation } from './types'

const product: Product = {
  sku: 'JKT0003',
  title: 'Sage Puffer Jacket',
  category: 'jackets',
  colors: ['Sage'],
  sizes: ['M'],
  price: 189,
  description: 'A quilted puffer.',
  imageUrl: '/images/categories/puffer.webp',
}

const interpretation: Interpretation = {
  searchQuery: 'rain jacket',
  negativeExamples: [],
  priceConstraint: null,
  sortByLowestPrice: false,
}

const server = setupServer()
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

// In the Node test environment undici needs an absolute URL; provide an ambient origin.
;(globalThis as { location?: { origin: string } }).location = { origin: 'http://localhost' }

const req = { message: 'rain jacket', conversationId: 'abc', clearMemory: true }

describe('searchProducts', () => {
  it('parses a 200 success body', async () => {
    server.use(
      http.get('*/api/v1/search', () =>
        HttpResponse.json({ products: [product], interpretation, suggestedPrompts: null }),
      ),
    )
    const result = await searchProducts(req)
    expect(result).toEqual({ kind: 'success', products: [product], interpretation })
  })

  it('parses a 422 rejection with suggestions', async () => {
    server.use(
      http.get('*/api/v1/search', () =>
        HttpResponse.json(
          { products: null, interpretation: null, suggestedPrompts: ['A warm winter jacket'] },
          { status: 422 },
        ),
      ),
    )
    const result = await searchProducts(req)
    expect(result).toEqual({ kind: 'rejected', suggestedPrompts: ['A warm winter jacket'] })
  })

  it('parses a 400 field error', async () => {
    server.use(
      http.get('*/api/v1/search', () =>
        HttpResponse.json({ message: 'Message contains unsupported characters' }, { status: 400 }),
      ),
    )
    const result = await searchProducts(req)
    expect(result).toEqual({
      kind: 'fieldError',
      errors: { message: 'Message contains unsupported characters' },
    })
  })

  it('parses a 429 rate-limit with Retry-After', async () => {
    server.use(
      http.get('*/api/v1/search', () =>
        HttpResponse.json({ code: 'RATE_LIMITED', message: 'slow down' }, {
          status: 429,
          headers: { 'Retry-After': '30' },
        }),
      ),
    )
    const result = await searchProducts(req)
    expect(result).toEqual({
      kind: 'rateLimited',
      code: 'RATE_LIMITED',
      message: 'slow down',
      retryAfterSeconds: 30,
    })
  })

  it('parses a 429 daily-budget-exhausted without retry', async () => {
    server.use(
      http.get('*/api/v1/search', () =>
        HttpResponse.json({ code: 'DAILY_BUDGET_EXHAUSTED', message: 'quota' }, { status: 429 }),
      ),
    )
    const result = await searchProducts(req)
    expect(result).toEqual({
      kind: 'rateLimited',
      code: 'DAILY_BUDGET_EXHAUSTED',
      message: 'quota',
      retryAfterSeconds: null,
    })
  })

  it('maps 5xx to serverError', async () => {
    server.use(
      http.get('*/api/v1/search', () => HttpResponse.json({ error: 'boom' }, { status: 500 })),
    )
    expect(await searchProducts(req)).toEqual({ kind: 'serverError' })
  })

  it('maps a network failure to network', async () => {
    server.use(http.get('*/api/v1/search', () => HttpResponse.error()))
    expect(await searchProducts(req)).toEqual({ kind: 'network' })
  })

  it('maps an aborted request to aborted', async () => {
    server.use(
      http.get('*/api/v1/search', () => HttpResponse.json({ products: [product], interpretation, suggestedPrompts: null })),
    )
    const controller = new AbortController()
    controller.abort()
    expect(await searchProducts(req, controller.signal)).toEqual({ kind: 'aborted' })
  })
})
