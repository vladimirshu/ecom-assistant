import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { checkHealth } from '../lib/api/health'
import { checkControl } from '../lib/api/control'
import {
  isWithinOpeningHours,
  parseSchedule,
  type ScheduleConfig,
} from './schedule'

export type GatePhase = 'checking' | 'closed' | 'waking' | 'healthy' | 'stuck'

const CONTROL_POLL_MS = 3000
const STUCK_AFTER_MS = 180_000
const START_DEBOUNCE_MS = 1000

function loadSchedule(): ScheduleConfig | null {
  const result = parseSchedule({
    tz: import.meta.env.VITE_OPEN_TZ,
    days: import.meta.env.VITE_OPEN_DAYS,
    from: import.meta.env.VITE_OPEN_FROM,
    to: import.meta.env.VITE_OPEN_TO,
  })
  return result.ok ? result.config : null
}

export interface AvailabilityGate {
  phase: GatePhase
  isHealthy: boolean
  /** Re-evaluate readiness before a backend operation. Returns true when the request may proceed. */
  ensureReady: () => Promise<boolean>
  /** Report a network/5xx request failure so the gate can re-check and possibly re-open. */
  reportFailure: () => Promise<void>
  /** Manual immediate control check (debounced). */
  startDemo: () => void
  /** Restart the wake cycle from a stuck state. */
  retry: () => void
  /** True when a control call or startup polling is currently active. */
  busy: boolean
}

export function useAvailabilityGate(): AvailabilityGate {
  const [phase, setPhase] = useState<GatePhase>('checking')
  const [busy, setBusy] = useState(false)

  const phaseRef = useRef<GatePhase>('checking')
  const busyRef = useRef(false)
  const inFlightRef = useRef(false)
  const wakeStartRef = useRef<number | null>(null)
  const pollTimerRef = useRef<number | null>(null)
  const generationRef = useRef(0)
  const lastStartRef = useRef(0)

  const setGatePhase = useCallback((next: GatePhase) => {
    phaseRef.current = next
    setPhase(next)
  }, [])

  const stopPolling = useCallback(() => {
    if (pollTimerRef.current !== null) {
      window.clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }, [])

  const schedule = useMemo(() => loadSchedule(), [])

  const pollControl = useCallback(
    async (generation: number) => {
      if (inFlightRef.current) return
      inFlightRef.current = true
      setBusy(true)
      busyRef.current = true
      try {
        const result = await checkControl()
        if (generation !== generationRef.current) return
        if (result.state === 'UP') {
          stopPolling()
          setGatePhase('healthy')
        } else if (wakeStartRef.current !== null && Date.now() - wakeStartRef.current >= STUCK_AFTER_MS) {
          stopPolling()
          setGatePhase('stuck')
        } else {
          setGatePhase('waking')
        }
      } finally {
        inFlightRef.current = false
        if (generation === generationRef.current) {
          setBusy(false)
          busyRef.current = false
        }
      }
    },
    [setGatePhase, stopPolling],
  )

  const beginWake = useCallback(
    (generation: number) => {
      if (generation !== generationRef.current) return
      stopPolling()
      wakeStartRef.current = Date.now()
      setGatePhase('waking')
      void pollControl(generation)
      pollTimerRef.current = window.setInterval(() => {
        void pollControl(generation)
      }, CONTROL_POLL_MS)
    },
    [pollControl, setGatePhase, stopPolling],
  )

  const evaluateInitial = useCallback(async () => {
    const generation = generationRef.current
    if (!schedule) {
      // Without valid schedule configuration there is nothing to gate on; allow requests.
      setGatePhase('healthy')
      return
    }
    setGatePhase('checking')
    if (isWithinOpeningHours(new Date(), schedule)) {
      const health = await checkHealth()
      if (generation !== generationRef.current) return
      if (health === 'healthy') {
        setGatePhase('healthy')
      } else {
        beginWake(generation)
      }
    } else {
      beginWake(generation)
    }
  }, [beginWake, schedule, setGatePhase])

  useEffect(() => {
    generationRef.current += 1
    void evaluateInitial()
    return () => {
      stopPolling()
      generationRef.current += 1
    }
  }, [evaluateInitial, stopPolling])

  const ensureReady = useCallback(async (): Promise<boolean> => {
    if (phaseRef.current === 'healthy') {
      // Re-evaluate the window; if we have slipped outside it, confirm control UP.
      if (schedule && !isWithinOpeningHours(new Date(), schedule)) {
        const result = await checkControl()
        if (result.state === 'UP') return true
        beginWake(generationRef.current)
        return false
      }
      return true
    }
    // Not healthy: ensure a wake cycle is active and block.
    if (phaseRef.current !== 'waking') beginWake(generationRef.current)
    return false
  }, [beginWake, schedule])

  const reportFailure = useCallback(async (): Promise<void> => {
    const generation = generationRef.current
    if (!schedule) return
    if (isWithinOpeningHours(new Date(), schedule)) {
      const health = await checkHealth()
      if (generation !== generationRef.current) return
      if (health === 'healthy') {
        setGatePhase('healthy')
      } else {
        beginWake(generation)
      }
    } else {
      const result = await checkControl()
      if (generation !== generationRef.current) return
      if (result.state === 'UP') {
        setGatePhase('healthy')
      } else {
        beginWake(generation)
      }
    }
  }, [beginWake, schedule, setGatePhase])

  const startDemo = useCallback(() => {
    const now = Date.now()
    if (now - lastStartRef.current < START_DEBOUNCE_MS) return
    if (inFlightRef.current) return
    lastStartRef.current = now
    wakeStartRef.current = wakeStartRef.current ?? Date.now()
    void pollControl(generationRef.current)
    if (pollTimerRef.current === null) {
      pollTimerRef.current = window.setInterval(() => {
        void pollControl(generationRef.current)
      }, CONTROL_POLL_MS)
    }
  }, [pollControl])

  const retry = useCallback(() => {
    generationRef.current += 1
    const generation = generationRef.current
    stopPolling()
    wakeStartRef.current = Date.now()
    setGatePhase('waking')
    void pollControl(generation)
    pollTimerRef.current = window.setInterval(() => {
      void pollControl(generation)
    }, CONTROL_POLL_MS)
  }, [pollControl, setGatePhase, stopPolling])

  return useMemo<AvailabilityGate>(
    () => ({
      phase,
      isHealthy: phase === 'healthy',
      ensureReady,
      reportFailure,
      startDemo,
      retry,
      busy,
    }),
    [phase, busy, ensureReady, reportFailure, startDemo, retry],
  )
}
