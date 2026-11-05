import { breadcrumbsFor, type CategoryNode } from '../lib/categories'

export function CategoryBreadcrumbs({
  selectedId,
  onSelect,
}: {
  selectedId: string | null
  onSelect: (id: string | null) => void
}) {
  const trail: CategoryNode[] = selectedId ? breadcrumbsFor(selectedId) : []

  return (
    <nav aria-label="Breadcrumb">
      <ol className="m-0 flex list-none flex-wrap items-center gap-1 p-0 text-sm">
        <li>
          <button
            type="button"
            onClick={() => onSelect(null)}
            aria-current={selectedId === null ? 'page' : undefined}
            className="text-muted hover:text-action focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
          >
            All clothing
          </button>
        </li>
        {trail.map((node, index) => {
          const isLast = index === trail.length - 1
          return (
            <li key={node.id} className="flex items-center gap-1">
              <span aria-hidden="true" className="text-muted">
                /
              </span>
              {isLast ? (
                <span aria-current="page" className="text-ink">
                  {node.name}
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => onSelect(node.id)}
                  className="text-muted hover:text-action focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
                >
                  {node.name}
                </button>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
