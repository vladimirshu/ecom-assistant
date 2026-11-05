import { useEffect, useState } from 'react'
import { SkeletonCard } from './SkeletonCard'

const STAGES = ['Reading your request', 'Searching the collection', 'Picking the best matches']

/** Loading skeletons plus a client-timed intent message advancing at 0 / 1.5 / 3 seconds. */
export function LoadingStory({ resetKey }: { resetKey: string }) {
  const [stage, setStage] = useState(0)

  useEffect(() => {
    setStage(0)
    const t1 = window.setTimeout(() => setStage(1), 1500)
    const t2 = window.setTimeout(() => setStage(2), 3000)
    return () => {
      window.clearTimeout(t1)
      window.clearTimeout(t2)
    }
  }, [resetKey])

  return (
    <div>
      <p className="text-sm text-muted" aria-live="off">
        {STAGES[stage]}
      </p>
      <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-6 sm:grid-cols-3">
        <SkeletonCard />
        <SkeletonCard />
        <SkeletonCard />
      </div>
    </div>
  )
}
