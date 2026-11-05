import { BRAND, TAGLINE } from '../lib/brand'
import { catalogUrl, navigate, searchUrl } from '../router'

export function Header() {
  return (
    <header className="flex flex-wrap items-baseline gap-x-6 gap-y-1 px-4 py-4 sm:px-6">
      <div className="flex min-w-0 flex-wrap items-baseline gap-x-3">
        <a
          href="/"
          onClick={(e) => {
            e.preventDefault()
            navigate(searchUrl())
          }}
          className="font-serif text-2xl tracking-wide text-ink no-underline hover:text-action"
        >
          {BRAND}
        </a>
        <span className="text-[11px] leading-tight text-muted">{TAGLINE}</span>
      </div>
      <nav aria-label="Category entry points" className="ml-auto flex items-baseline gap-4">
        <a
          href="/catalog?category=men"
          onClick={(e) => {
            e.preventDefault()
            navigate(catalogUrl('men'))
          }}
          className="text-sm text-muted no-underline hover:text-action"
        >
          Men
        </a>
        <a
          href="/catalog?category=women"
          onClick={(e) => {
            e.preventDefault()
            navigate(catalogUrl('women'))
          }}
          className="text-sm text-muted no-underline hover:text-action"
        >
          Women
        </a>
      </nav>
    </header>
  )
}
