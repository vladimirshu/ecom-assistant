import { useCallback, useRef, useState } from 'react'
import type { Interpretation, Product } from '../lib/api/types'
import { searchProducts, type SearchOutcome } from '../lib/api/search'
import { newConversationId } from '../lib/uuid'
import { validateMessage } from '../lib/validation'
import { useGate } from '../gate/GateContext'

export type TurnStatus =
  | 'pending'
  | 'success'
  | 'empty'
  | 'rejected'
  | 'rateLimited'
  | 'budgetExhausted'
  | 'error'

export interface Turn {
  id: string
  customerText: string
  clearMemory: boolean
  status: TurnStatus
  products: Product[]
  interpretation: Interpretation | null
  suggestedPrompts: string[]
  errorMessage?: string
  retryAfterSeconds?: number | null
  retryAt?: number
}

export interface SearchSession {
  conversationId: string
  turns: Turn[]
  draft: string
  draftError: string | null
  activeTurnId: string | null
  newMatchesAvailable: boolean
  hasSubmitted: boolean
  submit: (raw: string) => Promise<void>
  retry: (turnId: string) => Promise<void>
  selectTurn: (turnId: string) => void
  returnToLatest: () => void
  acceptSuggestion: (prompt: string) => Promise<void>
  newSearch: () => void
  cancelPending: () => void
  setDraft: (value: string) => void
}

let turnCounter = 0
function nextTurnId(): string {
  turnCounter += 1
  return `turn-${turnCounter}`
}

export function useSearchSession(): SearchSession {
  const gate = useGate()

  const [conversationId, setConversationId] = useState(() => newConversationId())
  const [turns, setTurns] = useState<Turn[]>([])
  const [draft, setDraftState] = useState('')
  const [draftError, setDraftError] = useState<string | null>(null)
  const [activeTurnId, setActiveTurnId] = useState<string | null>(null)
  const [newMatchesAvailable, setNewMatchesAvailable] = useState(false)
  const [hasSubmitted, setHasSubmitted] = useState(false)

  const requestGeneration = useRef(0)
  const abortRef = useRef<AbortController | null>(null)
  const sentAnyRequest = useRef(false)
  const selectedExplicitlyRef = useRef(false)
  const latestSuccessfulTurnIdRef = useRef<string | null>(null)
  const pendingRef = useRef<{ id: string; raw: string; original?: Turn } | null>(null)

  const applyOutcome = useCallback(
    (outcome: SearchOutcome, turnId: string, raw: string) => {
      switch (outcome.kind) {
        case 'aborted':
          return
        case 'success': {
          const status: TurnStatus = outcome.products.length === 0 ? 'empty' : 'success'
          latestSuccessfulTurnIdRef.current = turnId
          setTurns((prev) =>
            prev.map((t) =>
              t.id === turnId
                ? { ...t, status, products: outcome.products, interpretation: outcome.interpretation }
                : t,
            ),
          )
          setDraftError(null)
          if (selectedExplicitlyRef.current) {
            setNewMatchesAvailable(true)
          } else {
            setActiveTurnId(turnId)
          }
          return
        }
        case 'rejected':
          setDraftState((current) => current || raw)
          setTurns((prev) =>
            prev.map((t) =>
              t.id === turnId ? { ...t, status: 'rejected', suggestedPrompts: outcome.suggestedPrompts } : t,
            ),
          )
          setDraftError(null)
          setActiveTurnId(turnId)
          return
        case 'fieldError': {
          setTurns((prev) => prev.filter((t) => t.id !== turnId))
          setDraftError(outcome.errors.message ?? 'That could not be processed. Please adjust your request.')
          setDraftState((current) => current || raw)
          setActiveTurnId((current) => (current === turnId ? latestSuccessfulTurnIdRef.current : current))
          return
        }
        case 'rateLimited': {
          setDraftState((current) => current || raw)
          const status: TurnStatus =
            outcome.code === 'DAILY_BUDGET_EXHAUSTED' ? 'budgetExhausted' : 'rateLimited'
          setTurns((prev) =>
            prev.map((t) =>
              t.id === turnId
                ? { ...t, status, errorMessage: outcome.message, retryAfterSeconds: outcome.retryAfterSeconds,
                  retryAt: Date.now() + (outcome.retryAfterSeconds ?? 0) * 1000 }
                : t,
            ),
          )
          setDraftError(null)
          setActiveTurnId(turnId)
          return
        }
        case 'serverError':
        case 'network':
          setDraftState((current) => current || raw)
          setTurns((prev) => prev.map((t) => (t.id === turnId ? { ...t, status: 'error' } : t)))
          setDraftError(null)
          setActiveTurnId(turnId)
          void gate.reportFailure()
          return
      }
    },
    [gate],
  )

  const dispatch = useCallback(
    async (raw: string, clearMemory: boolean) => {
      requestGeneration.current += 1
      const generation = requestGeneration.current
      abortRef.current?.abort()
      const previousPending = pendingRef.current
      if (previousPending) setTurns((prev) => prev.filter((t) => t.id !== previousPending.id))
      const controller = new AbortController()
      abortRef.current = controller

      const turnId = nextTurnId()
      pendingRef.current = { id: turnId, raw }
      const pendingTurn: Turn = {
        id: turnId,
        customerText: raw,
        clearMemory,
        status: 'pending',
        products: [],
        interpretation: null,
        suggestedPrompts: [],
      }

      setTurns((prev) => [...prev, pendingTurn])
      setActiveTurnId(turnId)
      selectedExplicitlyRef.current = false
      setNewMatchesAvailable(false)
      setDraftState((current) => current === raw ? '' : current)
      setDraftError(null)
      setHasSubmitted(true)

      const outcome = await searchProducts(
        { message: raw.trim(), conversationId, clearMemory },
        controller.signal,
      )
      if (generation !== requestGeneration.current) return
      pendingRef.current = null
      applyOutcome(outcome, turnId, raw)
    },
    [applyOutcome, conversationId],
  )

  const cancelPending = useCallback(() => {
    requestGeneration.current += 1
    abortRef.current?.abort()
    const pending = pendingRef.current
    pendingRef.current = null
    if (!pending) return
    setTurns((prev) => pending.original
      ? prev.map((turn) => turn.id === pending.id ? pending.original! : turn)
      : prev.filter((turn) => turn.id !== pending.id))
    setDraftState((current) => current || pending.raw)
    if (!pending.original) {
      setActiveTurnId((current) => current === pending.id ? latestSuccessfulTurnIdRef.current : current)
    }
  }, [])

  const submit = useCallback(
    async (raw: string) => {
      const validation = validateMessage(raw)
      if (!validation.ok) {
        setDraftError(validation.error)
        setDraftState(raw)
        return
      }
      setDraftState(raw)
      setDraftError(null)
      cancelPending()
      const generation = ++requestGeneration.current
      const ready = await gate.ensureReady()
      if (!ready || generation !== requestGeneration.current) return
      const clearMemory = !sentAnyRequest.current
      sentAnyRequest.current = true
      await dispatch(raw, clearMemory)
    },
    [cancelPending, dispatch, gate],
  )

  const retry = useCallback(
    async (turnId: string) => {
      const turn = turns.find((t) => t.id === turnId)
      if (!turn || turn !== turns.at(-1) || (turn.status !== 'error' && turn.status !== 'rateLimited')) return
      if (turn.status === 'rateLimited' && Date.now() < (turn.retryAt ?? 0)) return
      cancelPending()
      const intent = ++requestGeneration.current
      const ready = await gate.ensureReady()
      if (!ready || intent !== requestGeneration.current) return

      requestGeneration.current += 1
      const generation = requestGeneration.current
      abortRef.current?.abort()
      const previousPending = pendingRef.current
      if (previousPending) setTurns((prev) => prev.filter((t) => t.id !== previousPending.id))
      pendingRef.current = { id: turnId, raw: turn.customerText, original: turn }
      const controller = new AbortController()
      abortRef.current = controller

      setTurns((prev) =>
        prev.map((t) => (t.id === turnId ? { ...t, status: 'pending' as TurnStatus } : t)),
      )
      setActiveTurnId(turnId)
      selectedExplicitlyRef.current = false
      setNewMatchesAvailable(false)
      setDraftState((current) => current === turn.customerText ? '' : current)
      setDraftError(null)

      const outcome = await searchProducts(
        { message: turn.customerText.trim(), conversationId, clearMemory: turn.clearMemory },
        controller.signal,
      )
      if (generation !== requestGeneration.current) return
      pendingRef.current = null
      applyOutcome(outcome, turnId, turn.customerText)
    },
    [applyOutcome, cancelPending, conversationId, gate, turns],
  )

  const selectTurn = useCallback((turnId: string) => {
    selectedExplicitlyRef.current = true
    setActiveTurnId(turnId)
    setNewMatchesAvailable(false)
  }, [])

  const returnToLatest = useCallback(() => {
    selectedExplicitlyRef.current = false
    setNewMatchesAvailable(false)
    if (latestSuccessfulTurnIdRef.current) setActiveTurnId(latestSuccessfulTurnIdRef.current)
  }, [])

  const acceptSuggestion = useCallback(
    async (prompt: string) => { await submit(prompt) },
    [submit],
  )

  const newSearch = useCallback(() => {
    requestGeneration.current += 1
    abortRef.current?.abort()
    pendingRef.current = null
    setConversationId(newConversationId())
    setTurns([])
    setDraftState('')
    setDraftError(null)
    setActiveTurnId(null)
    setNewMatchesAvailable(false)
    setHasSubmitted(false)
    sentAnyRequest.current = false
    selectedExplicitlyRef.current = false
    latestSuccessfulTurnIdRef.current = null
  }, [])

  const setDraft = useCallback((value: string) => {
    setDraftState(value)
    setDraftError(null)
  }, [])

  return {
    conversationId,
    turns,
    draft,
    draftError,
    activeTurnId,
    newMatchesAvailable,
    hasSubmitted,
    submit,
    retry,
    selectTurn,
    returnToLatest,
    acceptSuggestion,
    newSearch,
    cancelPending,
    setDraft,
  }
}
