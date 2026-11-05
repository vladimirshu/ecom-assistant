import raw from '../data/categories.json'

export interface CategoryNode {
  id: string
  name: string
  imageKey?: string
  children?: CategoryNode[]
}

export const CATEGORY_ROOTS: CategoryNode[] = (raw as { roots: CategoryNode[] }).roots

/** Flatten all category ids for validation and lookup. */
export function allCategoryIds(): string[] {
  const ids: string[] = []
  const walk = (nodes: CategoryNode[]) => {
    for (const node of nodes) {
      ids.push(node.id)
      if (node.children) walk(node.children)
    }
  }
  walk(CATEGORY_ROOTS)
  return ids
}

export function isKnownCategory(id: string): boolean {
  return allCategoryIds().includes(id)
}

export function findCategory(id: string): CategoryNode | null {
  let found: CategoryNode | null = null
  const walk = (nodes: CategoryNode[]) => {
    for (const node of nodes) {
      if (node.id === id) found = node
      if (node.children) walk(node.children)
    }
  }
  walk(CATEGORY_ROOTS)
  return found
}

/** Breadcrumb trail from the root down to (and including) the given id. */
export function breadcrumbsFor(id: string): CategoryNode[] {
  const trail: CategoryNode[] = []
  const walk = (nodes: CategoryNode[], path: CategoryNode[]): boolean => {
    for (const node of nodes) {
      const next = [...path, node]
      if (node.id === id) {
        trail.push(...next)
        return true
      }
      if (node.children && walk(node.children, next)) return true
    }
    return false
  }
  walk(CATEGORY_ROOTS, [])
  return trail
}

export interface CategoryValidationIssue {
  id: string
  problem: string
}

/** Validate the category tree: unique ids, required fields, optional image keys. */
export function validateCategories(
  nodes: CategoryNode[] = CATEGORY_ROOTS,
  imageKeys: string[] = [],
): CategoryValidationIssue[] {
  const issues: CategoryValidationIssue[] = []
  const seen = new Set<string>()
  const walk = (list: CategoryNode[]) => {
    for (const node of list) {
      if (!node.id) {
        issues.push({ id: node.name ?? '<unnamed>', problem: 'missing id' })
        continue
      }
      if (seen.has(node.id)) {
        issues.push({ id: node.id, problem: 'duplicate id' })
      } else {
        seen.add(node.id)
      }
      if (!node.name) issues.push({ id: node.id, problem: 'missing name' })
      if (node.imageKey && imageKeys.length > 0 && !imageKeys.includes(node.imageKey)) {
        issues.push({ id: node.id, problem: `imageKey "${node.imageKey}" has no asset` })
      }
      if (node.children) walk(node.children)
    }
  }
  walk(nodes)
  return issues
}
