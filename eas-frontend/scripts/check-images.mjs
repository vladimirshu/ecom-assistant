import { readFileSync, existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const manifest = JSON.parse(readFileSync(join(root, 'src/data/catalog-images.json'), 'utf8'))
const categories = JSON.parse(readFileSync(join(root, 'src/data/categories.json'), 'utf8'))
const seed = readFileSync(join(root, '../eas-backend/src/main/resources/sampledata/apparel_catalog.csv'), 'utf8')
const entries = new Map(manifest.entries.map((entry) => [entry.imageKey, entry]))
const problems = new Set()

if (entries.size !== manifest.entries.length) problems.add('Duplicate manifest keys')
if (!entries.has('default')) problems.add('Missing default manifest entry')

for (const entry of entries.values()) {
  const path = `public/images/categories/${entry.imageKey}.webp`
  if (!existsSync(join(root, path))) {
    problems.add(`Missing ${path}`)
    continue
  }
  const bytes = readFileSync(join(root, path))
  // Extended WebP header stores the canvas dimensions and an alpha feature flag.
  const hasAlphaCanvas = bytes.length >= 30 && bytes.toString('ascii', 0, 4) === 'RIFF'
    && bytes.toString('ascii', 8, 16) === 'WEBPVP8X' && (bytes[20] & 0x10) !== 0
  if (!hasAlphaCanvas || bytes.readUIntLE(24, 3) + 1 !== 1200 || bytes.readUIntLE(27, 3) + 1 !== 1500
      || bytes.length > 200_000) {
    problems.add(`${path} must be a 1200×1500 alpha WebP of at most 200 KB`)
  }
  if (entry.status !== 'approved' || entry.export?.path !== path || entry.export.bytes !== bytes.length
      || !entry.source || !existsSync(join(root, entry.source))) {
    problems.add(`${path} is missing approval, source or matching export metadata`)
  }
}

function checkCategoryImages(nodes) {
  for (const node of nodes) {
    if (node.imageKey && !entries.has(node.imageKey)) {
      problems.add(`Category ${node.id} references unknown image ${node.imageKey}`)
    }
    checkCategoryImages(node.children ?? [])
  }
}
checkCategoryImages(categories.roots)

for (const row of seed.trimEnd().split(/\r?\n/).slice(1)) {
  // Read the leading columns only. Descriptions can contain delimiters.
  const columns = row.match(/^((?:"(?:[^"]|"")*"|[^";]*);){8}/)?.[0]
  if (!columns) continue
  const [sku, , slug, categoryIds, , , , image] = columns.match(/(?:"(?:[^"]|"")*"|[^";]*);/g)
    .map((field) => field.slice(0, -1).replace(/^"|"$/g, '').replaceAll('""', '"'))
  const path = image || `categories/${slug}.webp`
  const key = /^categories\/([a-z0-9-]+)\.webp$/.exec(path)?.[1]
  const entry = entries.get(key)
  if (!entry || categoryIds.split(',').some((id) => !entry.categoryIds.includes(id.trim()))) {
    problems.add(`Product ${sku} has no matching manifest mapping for ${path}`)
  }
}

if (problems.size > 0) {
  for (const problem of problems) console.error(problem)
  process.exit(1)
}
console.log(`Catalog imagery checks passed (${entries.size} entries).`)
