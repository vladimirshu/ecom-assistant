// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import type { AvailabilityGate } from '../gate/useAvailabilityGate'
import { AvailabilityGateContext } from '../gate/GateContext'
import { useSearchSession } from './useSearchSession'
import { searchProducts, type SearchOutcome } from '../lib/api/search'
import type { Product, Interpretation } from '../lib/api/types'

vi.mock('../lib/api/search', () => ({
  searchProducts: vi.fn(),
}))

const mockedSearch = vi.mocked(searchProducts)

const product = (sku: string): Product => ({
  sku,
  title: sku,
  category: 'jackets',
  colors: [],
  sizes: [],
  price: 100,
  description: '',
  imageUrl: '/images/categories/default.webp',
})

const interpretation: Interpretation = {
  searchQuery: 'x',
  negativeExamples: [],
  priceConstraint: null,
  sortByLowestPrice: false,
}

const success = (skus: string[]): SearchOutcome => ({
  kind: 'success',
  products: skus.map(product),
  interpretation,
})

function deferred<T>() {
  let resolve!: (v: T) => void
  const promise = new Promise<T>((r) => (resolve = r))
  return { promise, resolve }
}

const gate: AvailabilityGate = {
  phase: 'healthy',
  isHealthy: true,
  ensureReady: async () => true,
  reportFailure: async () => {},
  startDemo: () => {},
  retry: () => {},
  busy: false,
}

function wrapper({ children }: { children: ReactNode }) {
  return <AvailabilityGateContext.Provider value={gate}>{children}</AvailabilityGateContext.Provider>
}

beforeEach(() => {
  mockedSearch.mockReset()
})

describe('useSearchSession', () => {
  it('does not retry a rate-limited turn before its original deadline', async () => {
    const now = vi.spyOn(Date, 'now').mockReturnValue(1000)
    try {
      mockedSearch.mockResolvedValueOnce({ kind: 'rateLimited', code: 'RATE_LIMITED', message: '', retryAfterSeconds: 30 })
        .mockResolvedValueOnce(success(['RETRY']))
      const { result } = renderHook(() => useSearchSession(), { wrapper })
      await act(async () => { await result.current.submit('navy jeans') })
      const id = result.current.activeTurnId!
      await act(async () => { await result.current.retry(id) })
      expect(mockedSearch).toHaveBeenCalledTimes(1)
      now.mockReturnValue(31_000)
      await act(async () => { await result.current.retry(id) })
      expect(mockedSearch).toHaveBeenCalledTimes(2)
    } finally {
      now.mockRestore()
    }
  })
  it('sends clearMemory=true only on the first request', async () => {
    mockedSearch.mockResolvedValue(success(['A']))
    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => {
      await result.current.submit('warm winter jacket')
    })
    await act(async () => {
      await result.current.submit('same but in navy')
    })
    expect(mockedSearch).toHaveBeenCalledTimes(2)
    expect(mockedSearch.mock.calls[0][0].clearMemory).toBe(true)
    expect(mockedSearch.mock.calls[1][0].clearMemory).toBe(false)
    expect(mockedSearch.mock.calls[0][0].conversationId).toBe(mockedSearch.mock.calls[1][0].conversationId)
  })

  it('keeps edits made while a submitted request waits for readiness', async () => {
    const ready = deferred<boolean>()
    const ensureReady = vi.spyOn(gate, 'ensureReady').mockReturnValueOnce(ready.promise)
    try {
      mockedSearch.mockResolvedValueOnce(success(['FIRST']))
      const { result } = renderHook(() => useSearchSession(), { wrapper })
      let submit!: Promise<void>
      act(() => { submit = result.current.submit('navy jeans') })
      act(() => result.current.setDraft('make them black'))
      await act(async () => {
        ready.resolve(true)
        await submit
      })
      expect(mockedSearch).toHaveBeenCalledWith(expect.objectContaining({ message: 'navy jeans' }), expect.anything())
      expect(result.current.draft).toBe('make them black')
    } finally {
      ensureReady.mockRestore()
    }
  })

  it('supersedes a pending request and renders only the latest response', async () => {
    const first = deferred<SearchOutcome>()
    const second = deferred<SearchOutcome>()
    mockedSearch.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

    const { result } = renderHook(() => useSearchSession(), { wrapper })

    let firstSubmit: Promise<void>
    let secondSubmit: Promise<void>
    act(() => {
      firstSubmit = result.current.submit('rain jacket')
    })
    await waitFor(() => expect(mockedSearch).toHaveBeenCalledTimes(1))
    act(() => { secondSubmit = result.current.submit('puffer jacket') })
    await waitFor(() => expect(mockedSearch).toHaveBeenCalledTimes(2))

    act(() => {
      first.resolve(success(['OLD']))
      second.resolve(success(['NEW']))
    })
    await act(async () => {
      await Promise.allSettled([firstSubmit!, secondSubmit!])
    })

    const last = result.current.turns[result.current.turns.length - 1]
    expect(last.products.map((p) => p.sku)).toEqual(['NEW'])
    // The superseded response must not create a second visible success turn.
    expect(result.current.turns.filter((t) => t.status === 'success')).toHaveLength(1)
    expect(result.current.turns.some((t) => t.status === 'pending')).toBe(false)
  })

  it('keeps a historical selection when a pending request completes', async () => {
    mockedSearch.mockResolvedValueOnce(success(['FIRST']))
    const second = deferred<SearchOutcome>()
    mockedSearch.mockReturnValueOnce(second.promise)

    const { result } = renderHook(() => useSearchSession(), { wrapper })

    await act(async () => {
      await result.current.submit('first query')
    })
    const firstTurnId = result.current.activeTurnId!

    let submitPromise: Promise<void>
    act(() => {
      submitPromise = result.current.submit('second query')
    })
    // Wait until the pending turn is actually created, then select the earlier snapshot.
    await waitFor(() => expect(result.current.turns).toHaveLength(2))
    act(() => {
      result.current.selectTurn(firstTurnId)
    })
    act(() => {
      second.resolve(success(['SECOND']))
    })
    await act(async () => {
      await submitPromise!
    })

    expect(result.current.activeTurnId).toBe(firstTurnId)
    expect(result.current.newMatchesAvailable).toBe(true)
  })

  it('retries without duplicating visible turns', async () => {
    mockedSearch.mockResolvedValueOnce({ kind: 'serverError' })
    mockedSearch.mockResolvedValueOnce(success(['RETRY-OK']))

    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => {
      await result.current.submit('something')
    })
    expect(result.current.turns).toHaveLength(1)

    const turnId = result.current.turns[0].id
    await act(async () => {
      await result.current.retry(turnId)
    })

    expect(result.current.turns).toHaveLength(1)
    expect(result.current.turns[0].status).toBe('success')
  })

  it('New search resets all snapshots and generates a new conversation id', async () => {
    mockedSearch.mockResolvedValue(success(['A']))
    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => {
      await result.current.submit('first')
    })
    const originalId = result.current.conversationId
    act(() => {
      result.current.newSearch()
    })
    expect(result.current.turns).toHaveLength(0)
    expect(result.current.activeTurnId).toBeNull()
    expect(result.current.hasSubmitted).toBe(false)
    expect(result.current.conversationId).not.toBe(originalId)
  })

  it('shows an inline hint without sending for an invalid message', async () => {
    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => {
      await result.current.submit('ab')
    })
    expect(mockedSearch).not.toHaveBeenCalled()
    expect(result.current.draftError).toBeTruthy()
  })

  it('retains failed turns for inspection', async () => {
    mockedSearch.mockResolvedValue({ kind: 'serverError' })
    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => {
      await result.current.submit('failing query')
    })
    await waitFor(() => expect(result.current.turns[0].status).toBe('error'))
    expect(result.current.turns[0].customerText).toBe('failing query')
    expect(result.current.draft).toBe('failing query')
  })

  it('cancels a request when leaving search, preserving completed results and the draft', async () => {
    const pending = deferred<SearchOutcome>()
    mockedSearch.mockResolvedValueOnce(success(['FIRST'])).mockReturnValueOnce(pending.promise)
    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => { await result.current.submit('first query') })
    const firstId = result.current.activeTurnId
    let request!: Promise<void>
    act(() => { request = result.current.submit('next query') })
    await waitFor(() => expect(result.current.turns).toHaveLength(2))
    const signal = mockedSearch.mock.calls[1][1]!
    act(() => result.current.cancelPending())
    expect(signal.aborted).toBe(true)
    expect(result.current.draft).toBe('next query')
    expect(result.current.activeTurnId).toBe(firstId)
    act(() => pending.resolve(success(['LATE'])))
    await act(async () => { await request })
    expect(result.current.turns).toHaveLength(1)
    expect(result.current.turns[0].products[0].sku).toBe('FIRST')
  })

  it('restores the latest snapshot and field text after backend validation fails', async () => {
    mockedSearch.mockResolvedValueOnce(success(['FIRST'])).mockResolvedValueOnce({ kind: 'fieldError', errors: { message: 'Unsupported characters' } })
    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => { await result.current.submit('first query') })
    const firstId = result.current.activeTurnId
    await act(async () => { await result.current.submit('second query') })
    expect(result.current.activeTurnId).toBe(firstId)
    expect(result.current.draft).toBe('second query')
    expect(result.current.draftError).toBe('Unsupported characters')
  })

  it('preserves a completed failed turn when a retry is cancelled', async () => {
    const pending = deferred<SearchOutcome>()
    mockedSearch.mockResolvedValueOnce({ kind: 'serverError' }).mockReturnValueOnce(pending.promise)
    const { result } = renderHook(() => useSearchSession(), { wrapper })
    await act(async () => { await result.current.submit('first query') })
    const turnId = result.current.turns[0].id
    let retry!: Promise<void>
    act(() => { retry = result.current.retry(turnId) })
    await waitFor(() => expect(result.current.turns[0].status).toBe('pending'))
    act(() => result.current.cancelPending())
    expect(result.current.turns[0].status).toBe('error')
    expect(result.current.activeTurnId).toBe(turnId)
    act(() => pending.resolve(success(['LATE'])))
    await act(async () => { await retry })
    expect(result.current.turns[0].status).toBe('error')
  })
})
