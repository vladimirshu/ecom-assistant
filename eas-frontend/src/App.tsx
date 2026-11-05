import { useEffect, useState } from 'react'
import type { Product } from './lib/api/types'
import { HEADLINE, HERO_COPY_LEAD, HERO_COPY_SUB } from './lib/brand'
import { EXAMPLE_PROMPTS } from './lib/examples'
import { parseLocation, useLocation, navigate, catalogUrl, catalogAllUrl, searchUrl } from './router'
import { useAvailabilityGate } from './gate/useAvailabilityGate'
import { AvailabilityGateContext, useGate } from './gate/GateContext'
import { AvailabilityPanel } from './gate/AvailabilityPanel'
import { useSearchSession } from './session/useSearchSession'
import { Header } from './components/Header'
import { SearchBar } from './components/SearchBar'
import { ExamplePrompts } from './components/ExamplePrompts'
import { SearchDemonstration } from './components/SearchDemonstration'
import { SearchWorkspace } from './components/SearchWorkspace'
import { CatalogPage } from './components/CatalogPage'
import { ProductDialog } from './components/ProductDialog'

export default function App() {
  const gate = useAvailabilityGate()
  return (
    <AvailabilityGateContext.Provider value={gate}>
      <AppShell />
    </AvailabilityGateContext.Provider>
  )
}

function AppShell() {
  const gate = useGate()
  const session = useSearchSession()
  const { cancelPending } = session
  const location = useLocation()
  const { path, searchParams } = parseLocation(location)

  const isCatalog = path === '/catalog'
  const categoryParam = searchParams.get('category')
  const selectedId = categoryParam ?? null
  const showWorkspace = path === '/' && session.hasSubmitted

  useEffect(() => {
    if (path !== '/') cancelPending()
  }, [path, cancelPending])

  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null)
  const [demoPausedByField, setDemoPausedByField] = useState(false)

  // Close stale dialogs when the route, category or selected snapshot changes.
  useEffect(() => {
    setSelectedProduct(null)
  }, [path, categoryParam, session.activeTurnId])

  const openProduct = (product: Product) => setSelectedProduct(product)

  const handleSelectCategory = (id: string | null) => {
    navigate(id ? catalogUrl(id) : catalogAllUrl())
  }

  const handleBackToSearch = () => navigate(searchUrl())

  return (
    <div className="flex min-h-screen flex-col bg-paper text-ink">
      <Header />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6">
        {!gate.isHealthy && <AvailabilityPanel />}
        <div className={gate.isHealthy ? '' : 'mt-6'}>
          {isCatalog ? (
            <CatalogPage
              key={selectedId ?? 'all'}
              selectedId={selectedId}
              onSelectCategory={handleSelectCategory}
              onBackToSearch={handleBackToSearch}
              onOpenProduct={openProduct}
            />
          ) : showWorkspace ? (
            <SearchWorkspace session={session} onOpenProduct={openProduct} />
          ) : (
            <div className="mx-auto max-w-3xl">
              <section>
                <h1 className="font-serif text-[23px] leading-snug text-ink sm:text-[25px]">
                  {HEADLINE}
                </h1>
                <div className="mt-4">
                  <SearchBar
                    value={session.draft}
                    onChange={session.setDraft}
                    onSubmit={session.submit}
                    error={session.draftError}
                    onFocus={() => setDemoPausedByField(true)}
                    onBlur={() => setDemoPausedByField(false)}
                  />
                </div>
                <div className="mt-3">
                  <ExamplePrompts examples={EXAMPLE_PROMPTS} onSelect={session.submit} />
                </div>
              </section>
              <section className="mt-10 grid grid-cols-1 items-start gap-8 md:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
                <div className="text-lg leading-relaxed text-muted">
                  <p className="font-serif text-ink">{HERO_COPY_LEAD}</p>
                  <p>{HERO_COPY_SUB}</p>
                </div>
                <SearchDemonstration pausedByField={demoPausedByField} />
              </section>
            </div>
          )}
        </div>
      </main>
      <ProductDialog product={selectedProduct} onClose={() => setSelectedProduct(null)} />
    </div>
  )
}
