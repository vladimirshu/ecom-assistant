import { useEffect, useRef, useState } from 'react'
import type { Product } from '../lib/api/types'
import { DEMO_TIMELINE } from '../lib/fixtures'
import { useReducedMotion } from '../lib/useReducedMotion'
import { ProductGrid } from './ProductGrid'
import { ProductDialog } from './ProductDialog'
import { InterpretationBar } from './InterpretationBar'
import { LoadingStory } from './LoadingStory'

type DemoStatus = 'idle' | 'playing' | 'paused' | 'done'

const LOADING_FRAMES = 10
const RESULT_FRAMES = 22

export function SearchDemonstration({
  pausedByField,
}: {
  pausedByField: boolean
}) {
  const reducedMotion = useReducedMotion()
  const [status, setStatus] = useState<DemoStatus>('idle')
  const [step, setStep] = useState(0)
  const [tick, setTick] = useState(0)
  const [autoPlayed, setAutoPlayed] = useState(false)
  const [selected, setSelected] = useState<Product | null>(null)
  const [visible, setVisible] = useState(false)
  const containerRef = useRef<HTMLElement>(null)
  const automaticRef = useRef(false)

  const stepData = DEMO_TIMELINE[step]
  const typeFrames = stepData.query.length + 1
  const phase = tick <= stepData.query.length ? 'typing' : tick <= typeFrames + LOADING_FRAMES ? 'loading' : 'result'

  // Advance the tick while playing (not under reduced motion, not externally paused).
  useEffect(() => {
    if (status !== 'playing' || reducedMotion) return
    const id = window.setInterval(() => setTick((t) => t + 1), 70)
    return () => window.clearInterval(id)
  }, [status, reducedMotion])

  // Pause when the real search field receives focus.
  useEffect(() => {
    if (pausedByField && status === 'playing') setStatus('paused')
  }, [pausedByField, status])

  // Move to the next step, or finish, once a step completes.
  useEffect(() => {
    if (status !== 'playing' || reducedMotion) return
    const total = typeFrames + LOADING_FRAMES + RESULT_FRAMES
    if (tick >= total) {
      if (step < DEMO_TIMELINE.length - 1) {
        setStep(step + 1)
        setTick(0)
      } else {
        setStatus('done')
      }
    }
  }, [tick, status, reducedMotion, step, typeFrames])

  useEffect(() => {
    const element = containerRef.current
    if (!element) return
    if (typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver(([entry]) => setVisible(entry.isIntersecting))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  // Auto-play one cycle only while the demonstration is visible and the input is unfocused.
  useEffect(() => {
    if (!reducedMotion && !autoPlayed && visible && !pausedByField) {
      setAutoPlayed(true)
      automaticRef.current = true
      setStatus('playing')
    }
    if (!visible && automaticRef.current && status === 'playing') setStatus('paused')
  }, [autoPlayed, pausedByField, reducedMotion, status, visible])

  const play = () => {
    automaticRef.current = false
    setStatus('playing')
    setStep(0)
    setTick(0)
    setAutoPlayed(true)
  }
  const replay = () => {
    if (status === 'paused') {
      automaticRef.current = false
      setStatus('playing')
    } else play()
  }
  const pause = () => setStatus('paused')
  const openExample = (product: Product) => {
    if (status === 'playing') pause()
    setSelected(product)
  }

  const typedQuery = stepData.query.slice(0, tick)

  return (
    <aside
      ref={containerRef}
      aria-label="Example search demonstration"
      className="rounded-sm border border-border bg-paper px-4 py-4"
    >
      <div className="flex items-center justify-between gap-3">
        <h3 className="font-serif text-sm text-muted">Example</h3>
        <div className="flex gap-2">
          {!reducedMotion && <button
            type="button"
            onClick={status === 'playing' ? pause : replay}
            className="rounded-sm border border-action px-3 py-1 text-sm text-action hover:bg-wash/50 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
          >
            {status === 'playing' ? 'Pause' : status === 'done' ? 'Replay' : 'Play'}
          </button>}
        </div>
      </div>

      {reducedMotion ? (
        <div className="mt-3 space-y-4">
          {DEMO_TIMELINE.map((s) => (
            <div key={s.query}>
              <p className="text-sm text-ink">“{s.query}”</p>
              <InterpretationBar interpretation={s.interpretation} />
              <div className="mt-2">
                <ProductGrid products={s.products} onOpen={openExample} />
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="mt-3">
          <p className="min-h-[1.5rem] text-sm text-ink" aria-hidden="true">
            “{typedQuery}
            {phase === 'typing' && <span className="animate-pulse">|</span>}”
          </p>
          {phase === 'loading' && <LoadingStory resetKey={`${step}-loading`} />}
          {phase === 'result' && (
            <div className="mt-2">
              <InterpretationBar interpretation={stepData.interpretation} />
              <div className="mt-3">
                <ProductGrid products={stepData.products} onOpen={openExample} />
              </div>
            </div>
          )}
          {status === 'idle' && (
            <p className="text-sm text-muted">Press Play to see an example search and refinement.</p>
          )}
        </div>
      )}

      <ProductDialog product={selected} onClose={() => setSelected(null)} example />
    </aside>
  )
}
