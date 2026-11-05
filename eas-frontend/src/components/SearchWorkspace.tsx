import { useEffect, useState } from 'react'
import type { Product } from '../lib/api/types'
import type { SearchSession } from '../session/useSearchSession'
import { useMediaQuery } from '../lib/useMediaQuery'
import { ConversationHistory } from './ConversationHistory'
import { FollowUpComposer } from './FollowUpComposer'
import { CurrentMatches } from './CurrentMatches'

export function SearchWorkspace({
  session,
  onOpenProduct,
}: {
  session: SearchSession
  onOpenProduct: (product: Product) => void
}) {
  const isDesktop = useMediaQuery('(min-width: 768px)')
  const [tab, setTab] = useState<'matches' | 'conversation'>('matches')

  const pendingId = session.turns.find((turn) => turn.status === 'pending')?.id

  useEffect(() => {
    if (session.draftError) setTab('conversation')
    else if (pendingId) setTab('matches')
  }, [session.draftError, pendingId])

  const selectTurn = (turnId: string) => {
    session.selectTurn(turnId)
    setTab('matches')
  }
  const conversation = (
    <div className="flex h-full flex-col">
      <div className="min-h-0 flex-1 overflow-y-auto pr-1">
        <ConversationHistory
          turns={session.turns}
          activeTurnId={session.activeTurnId}
          onSelectTurn={selectTurn}
          onRetry={session.retry}
        />
      </div>
      <FollowUpComposer
        value={session.draft}
        onChange={session.setDraft}
        onSubmit={session.submit}
        error={session.draftError}
        onNewSearch={session.newSearch}
      />
    </div>
  )

  const matches = (
    <div className="overflow-y-auto pr-1">
      <CurrentMatches
        turns={session.turns}
        activeTurnId={session.activeTurnId}
        newMatchesAvailable={session.newMatchesAvailable}
        onReturnToLatest={session.returnToLatest}
        onAcceptSuggestion={session.acceptSuggestion}
        onRetry={session.retry}
        onOpenProduct={onOpenProduct}
      />
    </div>
  )

  if (isDesktop) {
    return (
      <div className="grid grid-cols-[300px_minmax(0,1fr)] gap-8">
        <aside className="h-[calc(100vh-160px)] overflow-hidden">{conversation}</aside>
        <section aria-label="Results">{matches}</section>
      </div>
    )
  }

  return (
    <div>
      <div className="flex gap-1 border-b border-border" role="tablist" aria-label="Search views">
        {(['matches', 'conversation'] as const).map((view) => (
          <button
            key={view}
            id={`search-tab-${view}`}
            type="button"
            role="tab"
            tabIndex={tab === view ? 0 : -1}
            aria-selected={tab === view}
            aria-controls={`search-panel-${view}`}
            onClick={() => setTab(view)}
            onKeyDown={(event) => {
              const other = view === 'matches' ? 'conversation' : 'matches'
              const next = event.key === 'Home' ? 'matches' :
                event.key === 'End' ? 'conversation' :
                ['ArrowLeft', 'ArrowRight'].includes(event.key) ? other : null
              if (!next) return
              event.preventDefault()
              setTab(next)
              document.getElementById(`search-tab-${next}`)?.focus()
            }}
            className={`border-b-2 px-4 py-2 text-sm focus:outline focus:outline-2 focus:outline-offset-[-2px] focus:outline-action ${
              tab === view ? 'border-action text-ink' : 'border-transparent text-muted'
            }`}
          >
            {view === 'matches' ? 'Matches' : 'Conversation'}
          </button>
        ))}
      </div>
      <div id="search-panel-matches" role="tabpanel" aria-labelledby="search-tab-matches" hidden={tab !== 'matches'} className="py-4">
        {matches}
      </div>
      <div id="search-panel-conversation" role="tabpanel" aria-labelledby="search-tab-conversation" hidden={tab !== 'conversation'} className="py-4">
        {conversation}
      </div>
    </div>
  )
}
