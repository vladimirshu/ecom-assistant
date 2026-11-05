import { useEffect, useState } from 'react'
import { CATEGORY_ROOTS, breadcrumbsFor, type CategoryNode } from '../lib/categories'

export function CatalogNavigation({
  selectedId,
  onSelect,
}: {
  selectedId: string | null
  onSelect: (id: string | null) => void
}) {
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  // Expand the selected branch (and its ancestors) whenever selection changes.
  useEffect(() => {
    if (!selectedId) return
    setExpanded((prev) => {
      const next = new Set(prev)
      for (const node of breadcrumbsFor(selectedId)) next.add(node.id)
      return next
    })
  }, [selectedId])

  const toggle = (id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  return (
    <nav aria-label="Catalog categories" className="text-sm">
      <ul className="m-0 list-none space-y-1 p-0">
        <li>
          <button
            type="button"
            onClick={() => onSelect(null)}
            aria-current={selectedId === null ? 'page' : undefined}
            className={`w-full rounded-sm px-2 py-1 text-left focus:outline focus:outline-2 focus:outline-offset-1 focus:outline-action ${
              selectedId === null ? 'bg-wash/60 font-semibold text-ink' : 'text-muted hover:text-action'
            }`}
          >
            All clothing
          </button>
        </li>
        {CATEGORY_ROOTS.map((root) => (
          <TreeItem
            key={root.id}
            node={root}
            selectedId={selectedId}
            expanded={expanded}
            onToggle={toggle}
            onSelect={onSelect}
            depth={0}
          />
        ))}
      </ul>
    </nav>
  )
}

function TreeItem({
  node,
  selectedId,
  expanded,
  onToggle,
  onSelect,
  depth,
}: {
  node: CategoryNode
  selectedId: string | null
  expanded: Set<string>
  onToggle: (id: string) => void
  onSelect: (id: string) => void
  depth: number
}) {
  const hasChildren = Boolean(node.children && node.children.length > 0)
  const isExpanded = expanded.has(node.id)
  const isSelected = selectedId === node.id

  return (
    <li>
      <div className="flex items-center">
        {hasChildren ? (
          <button
            type="button"
            onClick={() => onToggle(node.id)}
            aria-label={isExpanded ? `Collapse ${node.name}` : `Expand ${node.name}`}
            aria-expanded={isExpanded}
            className="w-5 shrink-0 text-muted focus:outline focus:outline-2 focus:outline-offset-1 focus:outline-action"
          >
            {isExpanded ? '−' : '+'}
          </button>
        ) : (
          <span className="w-5 shrink-0" />
        )}
        <button
          type="button"
          onClick={() => onSelect(node.id)}
          aria-current={isSelected ? 'page' : undefined}
          className={`flex-1 rounded-sm px-2 py-1 text-left focus:outline focus:outline-2 focus:outline-offset-1 focus:outline-action ${
            isSelected ? 'bg-wash/60 font-semibold text-ink' : 'text-muted hover:text-action'
          }`}
          style={{ paddingLeft: `${depth * 12 + 8}px` }}
        >
          {node.name}
        </button>
      </div>
      {hasChildren && isExpanded && (
        <ul className="m-0 list-none p-0">
          {node.children!.map((child) => (
            <TreeItem
              key={child.id}
              node={child}
              selectedId={selectedId}
              expanded={expanded}
              onToggle={onToggle}
              onSelect={onSelect}
              depth={depth + 1}
            />
          ))}
        </ul>
      )}
    </li>
  )
}
