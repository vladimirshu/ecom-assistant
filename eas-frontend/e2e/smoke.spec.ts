import { test, expect } from '@playwright/test'
import axe from 'axe-core'

const puffer = {
  sku: 'JKT0003',
  title: 'Sage Puffer Jacket',
  category: 'jackets',
  colors: ['Sage'],
  sizes: ['M'],
  price: 189,
  description: 'A quilted puffer jacket.',
  imageUrl: '/images/categories/puffer.webp',
}
const interpretation = { searchQuery: 'puffer jacket', negativeExamples: [], priceConstraint: null, sortByLowestPrice: false }

// Stub the backend so the availability gate opens and search/catalog resolve
// deterministically regardless of the local schedule or time.
async function stubBackend(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/health', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"UP"}' }),
  )
  await page.route('**/api/v1/control/up', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: '{"state":"UP"}' }),
  )
  await page.route('**/api/v1/search*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        products: [puffer],
        interpretation,
        suggestedPrompts: null,
      }),
    }),
  )
  await page.route('**/api/v1/products*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ products: [], nextCursor: null }),
    }),
  )
}

test('landing page shows the Sage search-first layout', async ({ page }) => {
  await stubBackend(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: "Describe it. We'll find it." })).toBeVisible()
  await expect(page.getByPlaceholder('What are you looking for?')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Find my match' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Men', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Women' })).toBeVisible()
})

for (const width of [320, 390, 1440, 1920]) {
  test(`homepage has no horizontal overflow at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await stubBackend(page)
    await page.goto('/')
    await expect(page.getByRole('link', { name: 'Men', exact: true })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Women', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  })
}

test('mobile search retains the draft and opens a product dialog without API requests', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await stubBackend(page)
  await page.route('**/api/v1/search*', (route) => route.fulfill({
    json: {
      products: [puffer, { ...puffer, sku: 'B', title: 'Second Puffer' }, { ...puffer, sku: 'C', title: 'Third Puffer' }],
      interpretation,
    },
  }))
  let apiRequests = 0
  page.on('request', (request) => { if (new URL(request.url()).pathname.startsWith('/api/')) apiRequests++ })
  await page.goto('/')
  await page.getByPlaceholder('What are you looking for?').fill('puffer jacket')
  await page.getByRole('button', { name: 'Find my match' }).click()
  await expect(page.getByRole('heading', { name: '3 matches' })).toBeVisible()
  const pane = page.getByRole('region', { name: 'Selected matches' })
  await expect(pane.locator('h3')).toHaveText(['Sage Puffer Jacket', 'Second Puffer', 'Third Puffer'])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  const open = page.getByRole('button', { name: 'View details' }).first()
  const before = apiRequests
  await open.click()
  const dialog = page.getByRole('dialog', { name: 'Details for Sage Puffer Jacket' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('JKT0003')).toBeVisible()
  expect(apiRequests).toBe(before)
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(open).toBeFocused()
  await page.getByRole('tab', { name: 'Matches' }).focus()
  await page.keyboard.press('ArrowRight')
  await expect(page.getByRole('tab', { name: 'Conversation' })).toBeFocused()
  await page.getByLabel('Refine your search').fill('with a hood')
  await page.getByRole('tab', { name: 'Matches' }).click()
  await page.getByRole('tab', { name: 'Conversation' }).click()
  await expect(page.getByLabel('Refine your search')).toHaveValue('with a hood')
})

test('the example is usable while the backend is unavailable, including reduced motion', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  const searchOrCatalog: string[] = []
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname
    if (path === '/api/v1/search' || path === '/api/v1/products') searchOrCatalog.push(path)
  })
  await page.route('**/api/v1/health', (route) => route.fulfill({ status: 503 }))
  await page.route('**/api/v1/control/up', (route) => route.fulfill({ status: 200, json: { state: 'STARTING' } }))
  await page.goto('/')
  await expect(page.getByRole('region', { name: 'Service availability' })).toBeVisible()
  await checkAccessibility(page)
  const example = page.getByLabel('Example search demonstration')
  await expect(example.getByText('“navy jeans”', { exact: true })).toBeVisible()
  await expect(example.getByText('“actually make it black”', { exact: true })).toBeVisible()
  expect(searchOrCatalog).toEqual([])
})

async function checkAccessibility(page: import('@playwright/test').Page) {
  await page.addScriptTag({ content: axe.source })
  const violations = await page.evaluate(async () => {
    const result = await (window as unknown as { axe: typeof axe }).axe.run()
    return result.violations.filter((v) => v.impact === 'critical' || v.impact === 'serious').map((v) => ({ id: v.id, nodes: v.nodes.map((n) => n.html) }))
  })
  expect(violations).toEqual([])
}

test('homepage, results, dialog and catalog have no serious axe violations', async ({ page }) => {
  await stubBackend(page)
  await page.goto('/')
  await checkAccessibility(page)
  await page.getByPlaceholder('What are you looking for?').fill('puffer jacket')
  await page.getByRole('button', { name: 'Find my match' }).click()
  await expect(page.getByRole('heading', { name: '1 match' })).toBeVisible()
  await checkAccessibility(page)
  await page.getByRole('button', { name: 'View details' }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await checkAccessibility(page)
  await page.keyboard.press('Escape')
  await page.getByRole('link', { name: 'Women', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Women' })).toBeVisible()
  await checkAccessibility(page)
})

test('a real search shows the conversation workspace with results', async ({ page }) => {
  await stubBackend(page)
  await page.goto('/')
  await page.getByPlaceholder('What are you looking for?').fill('puffer jacket')
  await page.getByRole('button', { name: 'Find my match' }).click()
  await expect(page.getByText('Sage Puffer Jacket')).toBeVisible()
  await expect(page.getByRole('heading', { name: '1 match' })).toBeVisible()
})

test('catalog renders local category navigation', async ({ page }) => {
  await stubBackend(page)
  await page.goto('/catalog?category=women')
  await expect(page.getByRole('heading', { name: 'Women' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Back to search' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Catalog categories' })).toBeVisible()
})

test('desktop keeps the composer and first row visible while refining or inspecting history', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  await stubBackend(page)
  const requests: URLSearchParams[] = []
  let releaseRefinement!: () => void
  const refinement = new Promise<void>((resolve) => { releaseRefinement = resolve })
  await page.route('**/api/v1/search*', async (route) => {
    const params = new URL(route.request().url()).searchParams
    requests.push(params)
    if (requests.length === 2) await refinement
    const products = requests.length === 1 ? [puffer] : [
      { ...puffer, sku: 'B', title: 'Navy Puffer' },
      { ...puffer, sku: 'A', title: 'Blue Puffer' },
      { ...puffer, sku: 'C', title: 'Long Puffer' },
    ]
    await route.fulfill({ json: { products, interpretation, suggestedPrompts: null } })
  })
  await page.goto('/?ref=atelier')
  await page.getByPlaceholder('What are you looking for?').fill('warm winter jacket')
  await page.getByPlaceholder('What are you looking for?').press('Enter')
  const pane = page.getByRole('region', { name: 'Selected matches' })
  const composer = page.getByLabel('Refine your search')
  await expect(composer).toBeInViewport()
  await expect(pane.getByRole('button', { name: /View details/ }).first()).toBeInViewport()
  await composer.fill('same but in navy')
  await composer.press('Enter')
  await expect(pane.getByText('Reading your request')).toBeVisible()
  await page.getByRole('button', { name: 'Show these matches' }).click()
  releaseRefinement()
  await expect(pane.getByText('Sage Puffer Jacket')).toBeVisible()
  await pane.getByRole('button', { name: 'New matches available' }).click()
  await expect(pane.locator('h3')).toHaveText(['Navy Puffer', 'Blue Puffer', 'Long Puffer'])
  await composer.fill('with a hood')
  await page.getByRole('link', { name: 'Women', exact: true }).click()
  await expect(page).toHaveURL(/category=women.*ref=atelier|ref=atelier.*category=women/)
  await page.getByRole('button', { name: 'Back to search' }).click()
  await expect(composer).toHaveValue('with a hood')
  await expect(pane.getByRole('heading', { name: '3 matches' })).toBeVisible()
  expect(requests.map((params) => params.get('clearMemory'))).toEqual(['true', 'false'])
  expect(requests[1].get('conversationId')).toBe(requests[0].get('conversationId'))
  await page.getByRole('button', { name: 'New search' }).click()
  await expect(page.getByRole('heading', { name: "Describe it. We'll find it." })).toBeVisible()
})

test('search feedback distinguishes rejection, validation, quota, failure and an empty result', async ({ page }) => {
  await stubBackend(page)
  const cases = [
    { status: 200, json: { products: [puffer], interpretation } },
    { status: 422, json: { suggestedPrompts: ['une veste de pluie'] } },
    { status: 400, json: { message: 'Unsupported characters' } },
    { status: 429, headers: { 'Retry-After': '1' }, json: { code: 'RATE_LIMITED' } },
    { status: 429, json: { code: 'DAILY_BUDGET_EXHAUSTED' } },
    { status: 500, json: { error: 'backend failure' } },
    { status: 200, json: { products: [], interpretation } },
  ]
  let attempt = 0
  await page.route('**/api/v1/search*', (route) => route.fulfill(cases[attempt++]))
  await page.goto('/')
  await page.getByPlaceholder('What are you looking for?').fill('warm winter jacket')
  await page.getByPlaceholder('What are you looking for?').press('Enter')
  const pane = page.getByRole('region', { name: 'Selected matches' })
  await expect(pane.getByRole('heading', { name: '1 match' })).toBeVisible()
  const refine = async (text: string) => {
    const input = page.getByLabel('Refine your search')
    await input.fill(text)
    await input.press('Enter')
  }
  await refine('the first refinement')
  await expect(pane.getByRole('button', { name: 'une veste de pluie' })).toBeVisible()
  await expect(pane.getByText('Last successful results')).toBeVisible()
  await refine('the second refinement')
  await expect(page.getByText('Unsupported characters')).toBeVisible()
  await expect(page.getByLabel('Refine your search')).toHaveValue('the second refinement')
  await refine('the third refinement')
  const retry = pane.getByRole('button', { name: 'Try again' })
  await expect(retry).toBeDisabled()
  await expect(retry).toBeEnabled()
  await retry.click()
  await expect(pane.getByText("Today's search demo quota is used up.")).toBeVisible()
  await expect(retry).toHaveCount(0)
  await refine('the fourth refinement')
  await expect(retry).toBeVisible()
  await retry.click()
  await expect(pane.getByText('Nothing matched that description').last()).toBeVisible()
  await expect(pane.getByText('Sage Puffer Jacket')).toHaveCount(0)
})

test('catalog retries an opaque cursor, recovers from expiry, and restores URL category on Back', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'IntersectionObserver', { value: undefined })
  })
  await stubBackend(page)
  const calls: URLSearchParams[] = []
  await page.route('**/api/v1/products*', (route) => {
    const params = new URL(route.request().url()).searchParams
    calls.push(params)
    if (calls.length === 2) return route.fulfill({ status: 500, json: { error: 'unavailable' } })
    if (calls.length === 3) return route.fulfill({ status: 400, json: { error: 'expired cursor' } })
    return route.fulfill({ json: { products: [puffer], nextCursor: 'opaque+/=' } })
  })
  await page.goto('/catalog?category=women-jackets&ref=atelier')
  const loadMore = page.getByRole('button', { name: 'Load more' })
  await loadMore.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByText('Sage Puffer Jacket')).toBeVisible()
  await page.getByRole('button', { name: 'Try again' }).click()
  await page.getByRole('button', { name: 'Reload collection' }).click()
  await expect(loadMore).toBeVisible()
  expect(calls.map((params) => params.get('cursor'))).toEqual([null, 'opaque+/=', 'opaque+/=', null])
  await page.getByRole('button', { name: 'Puffer Jackets', exact: true }).click()
  await expect(page).toHaveURL(/category=women-puffer/)
  await expect(loadMore).toBeVisible()
  await page.goBack()
  await expect(loadMore).toBeVisible()
  expect(calls.slice(-2).map((params) => params.get('category'))).toEqual(['women-puffer', 'women-jackets'])
})

test('field focus pauses the offline example without editing the real input', async ({ page }) => {
  await page.route('**/api/v1/health', (route) => route.fulfill({ status: 503 }))
  await page.route('**/api/v1/control/up', (route) => route.fulfill({ json: { state: 'STARTING' } }))
  await page.goto('/')
  const example = page.getByLabel('Example search demonstration')
  await expect(example.getByRole('button', { name: 'Pause' })).toBeVisible()
  const field = page.getByPlaceholder('What are you looking for?')
  await field.focus()
  await expect(example.getByRole('button', { name: 'Play' })).toBeVisible()
  await expect(field).toHaveValue('')
  await field.fill('my own query')
  await example.getByRole('button', { name: 'Play' }).click()
  await expect(example.getByRole('button', { name: 'Pause' })).toBeVisible()
  await expect(field).toHaveValue('my own query')
})

test('a timed-out search offers recovery without losing the request', async ({ page }) => {
  await page.clock.install()
  await stubBackend(page)
  let release!: () => void
  const pending = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/search*', async (route) => {
    await pending
    await route.fulfill({ json: { products: [puffer], interpretation } })
  })
  await page.goto('/')
  await page.getByPlaceholder('What are you looking for?').fill('warm winter jacket')
  await page.getByRole('button', { name: 'Find my match' }).click()
  const pane = page.getByRole('region', { name: 'Selected matches' })
  await expect(pane.getByText('Reading your request')).toBeVisible()
  await page.clock.fastForward(30_000)
  await expect(pane.getByText('Something went wrong on our side.')).toBeVisible()
  await expect(page.getByLabel('Refine your search')).toHaveValue('warm winter jacket')
  release()
})

test('a newer search supersedes the pending response', async ({ page }) => {
  await stubBackend(page)
  let release!: () => void
  const old = new Promise<void>((resolve) => { release = resolve })
  await page.route('**/api/v1/search*', async (route) => {
    const message = new URL(route.request().url()).searchParams.get('message')
    const isOld = message === 'old request'
    if (isOld) await old
    await route.fulfill({
      json: { products: [{ ...puffer, title: isOld ? 'Outdated puffer' : 'Latest puffer' }], interpretation },
    })
  })
  await page.goto('/')
  await page.getByPlaceholder('What are you looking for?').fill('old request')
  await page.getByRole('button', { name: 'Find my match' }).click()
  const pane = page.getByRole('region', { name: 'Selected matches' })
  await expect(pane.getByText('Reading your request')).toBeVisible()
  await page.getByLabel('Refine your search').fill('new request')
  await page.getByLabel('Refine your search').press('Enter')
  await expect(pane.getByText('Latest puffer')).toBeVisible()
  release()
  await expect(pane.getByText('Outdated puffer')).toHaveCount(0)
  await expect(page.getByRole('list', { name: 'Conversation history' }).getByText('old request')).toHaveCount(0)
})

test('catalog navigation stays usable before readiness and fetches only the selected category', async ({ page }) => {
  await page.clock.install()
  let ready = false
  const requested: (string | null)[] = []
  await page.route('**/api/v1/health', (route) => route.fulfill({ status: 503 }))
  await page.route('**/api/v1/control/up', (route) => route.fulfill({
    json: { state: ready ? 'UP' : 'STARTING' },
  }))
  await page.route('**/api/v1/products*', (route) => {
    requested.push(new URL(route.request().url()).searchParams.get('category'))
    return route.fulfill({ json: { products: [puffer], nextCursor: null } })
  })
  await page.goto('/catalog?category=women')
  await expect(page.getByRole('heading', { name: 'Getting the shop ready.' })).toBeVisible()
  await page.getByRole('link', { name: 'Men', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Men', exact: true })).toBeVisible()
  expect(requested).toEqual([])
  ready = true
  await page.clock.fastForward(3_000)
  await expect(page.getByText(puffer.title)).toBeVisible()
  expect(requested).toEqual(['men'])
})
