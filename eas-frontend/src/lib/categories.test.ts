import backendCategories from '../../../eas-backend/src/main/resources/catalog/browsing-categories.json'
import { describe, expect, it } from 'vitest'
import {
  CATEGORY_ROOTS,
  allCategoryIds,
  breadcrumbsFor,
  findCategory,
  isKnownCategory,
  validateCategories,
  type CategoryNode,
} from './categories'

describe('category tree', () => {
  it('has distinct Men and Women roots', () => {
    expect(CATEGORY_ROOTS.map((r) => r.id)).toEqual(['men', 'women'])
  })

  it('resolves nested jacket categories and breadcrumbs', () => {
    expect(isKnownCategory('women-puffer')).toBe(true)
    const trail = breadcrumbsFor('women-puffer').map((n) => n.id)
    expect(trail).toEqual(['women', 'women-jackets-coats', 'women-jackets', 'women-puffer'])
  })

  it('uses the same category ids and parents as the catalog API', () => {
    function edges(nodes: CategoryNode[], parent = ''): string[] {
      return nodes.flatMap((node) => [
        `${parent}/${node.id}`,
        ...edges(node.children ?? [], node.id),
      ])
    }
    expect(edges(CATEGORY_ROOTS).sort()).toEqual(edges(backendCategories).sort())
  })

  it('has no duplicate ids', () => {
    const ids = allCategoryIds()
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('rejects duplicate ids in a modified tree', () => {
    const tree: CategoryNode[] = [
      { id: 'men', name: 'Men', children: [{ id: 'men', name: 'Men again' }] },
    ]
    expect(validateCategories(tree)).toContainEqual({ id: 'men', problem: 'duplicate id' })
  })

  it('rejects image keys with no asset', () => {
    const tree: CategoryNode[] = [{ id: 'x', name: 'X', imageKey: 'nope' }]
    expect(validateCategories(tree, ['puffer'])).toContainEqual({
      id: 'x',
      problem: 'imageKey "nope" has no asset',
    })
  })

  it('finds an unknown category as null', () => {
    expect(findCategory('does-not-exist')).toBeNull()
  })
})
