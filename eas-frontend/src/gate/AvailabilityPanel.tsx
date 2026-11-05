import { useEffect, useMemo, useState } from 'react'
import { useGate } from './GateContext'
import { openingHoursLabel, parseSchedule } from './schedule'
import { useReducedMotion } from '../lib/useReducedMotion'
import {
  RESTING_MESSAGE,
  RESTING_TITLE,
  STUCK_MESSAGE,
  STUCK_TITLE,
  WAKING_MESSAGES,
  WAKING_TITLE,
} from './messages'

function formatElapsed(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

export function AvailabilityPanel() {
  const gate = useGate()
  const reducedMotion = useReducedMotion()

  const [elapsed, setElapsed] = useState(0)
  const [messageIndex, setMessageIndex] = useState(0)
  const [announcement, setAnnouncement] = useState('')

  const hours = useMemo(() => {
    const result = parseSchedule({
      tz: import.meta.env.VITE_OPEN_TZ,
      days: import.meta.env.VITE_OPEN_DAYS,
      from: import.meta.env.VITE_OPEN_FROM,
      to: import.meta.env.VITE_OPEN_TO,
    })
    return result.ok ? openingHoursLabel(result.config) : null
  }, [])

  const isStarting = gate.phase === 'waking'
  const isStuck = gate.phase === 'stuck'

  // Elapsed timer while waking/stuck.
  useEffect(() => {
    if (!isStarting && !isStuck) {
      setElapsed(0)
      return
    }
    const id = window.setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => window.clearInterval(id)
  }, [isStarting, isStuck])

  // Rotate intent messages every 4s (paused under reduced motion).
  useEffect(() => {
    if (!isStarting || reducedMotion) return
    const id = window.setInterval(() => setMessageIndex((i) => (i + 1) % WAKING_MESSAGES.length), 4000)
    return () => window.clearInterval(id)
  }, [isStarting, reducedMotion])

  // Throttle polite announcements to every 12s.
  useEffect(() => {
    const title = isStuck ? STUCK_TITLE : isStarting ? WAKING_TITLE : RESTING_TITLE
    setAnnouncement(title)
    const id = window.setInterval(() => {
      const next = isStuck ? STUCK_TITLE : isStarting ? WAKING_TITLE : RESTING_TITLE
      setAnnouncement(next)
    }, 12000)
    return () => window.clearInterval(id)
  }, [isStuck, isStarting])

  const title = isStuck ? STUCK_TITLE : isStarting ? WAKING_TITLE : RESTING_TITLE
  const body = isStuck ? STUCK_MESSAGE : isStarting ? WAKING_MESSAGES[messageIndex] : RESTING_MESSAGE

  const action = isStuck ? (
    <button
      type="button"
      onClick={gate.retry}
      className="rounded-sm bg-action px-4 py-2 text-sm text-paper hover:bg-action/90 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
    >
      Try again
    </button>
  ) : isStarting || gate.busy ? (
    <button
      type="button"
      disabled
      className="cursor-not-allowed rounded-sm border border-border px-4 py-2 text-sm text-ink"
    >
      Starting…
    </button>
  ) : (
    <button
      type="button"
      onClick={gate.startDemo}
      className="rounded-sm bg-action px-4 py-2 text-sm text-paper hover:bg-action/90 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
    >
      Start demo
    </button>
  )

  return (
    <section
      aria-label="Service availability"
      className="rounded-sm border border-border bg-wash/50 px-5 py-4"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="font-serif text-lg text-ink">{title}</h2>
          <p className="mt-1 text-sm text-ink">{body}</p>
          {hours && (
            <p className="mt-2 text-xs text-ink">
              Opening hours: {hours.days} {hours.from}–{hours.to} (your time)
            </p>
          )}
          {(isStarting || isStuck) && (
            <p className="mt-1 text-xs tabular-nums text-ink" aria-hidden="true">
              {formatElapsed(elapsed)}
            </p>
          )}
        </div>
        <div className="shrink-0">{action}</div>
      </div>
      <span className="sr-only" role="status" aria-live="polite">
        {announcement}
      </span>
    </section>
  )
}
