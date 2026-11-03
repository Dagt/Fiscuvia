// Read-only documentation/evidence validation; not a pixel-diff gate (#56).
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { resolve, dirname, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'
const root = fileURLToPath(new URL('../../', import.meta.url))
const library = resolve(root, 'docs/ux/reference-library')
const evidence = resolve(root, 'docs/ux/evidence/issue-19')
const files = [...readdirSync(library).filter(f => f.endsWith('.md')).map(f => resolve(library, f)),
  ...['README.md', 'APPROVED-CONCEPT.md', 'DESIGN-SYSTEM.md', 'SCREENS.md', 'VISUAL-ACCEPTANCE.md', 'screen-inventory.md', 'frontend-foundation-plan.md'].map(f => resolve(root, 'docs/ux', f)),
  resolve(evidence, 'README.md'), resolve(root, 'docs/product/open-questions.md')]
let links = 0
for (const path of files) {
  const text = readFileSync(path, 'utf8')
  for (const match of text.matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) {
    const href = match[1].split('#')[0]
    if (!href || /^https?:|mailto:/.test(href)) continue
    assert(existsSync(resolve(dirname(path), decodeURIComponent(href))), `Broken local link: ${relative(root, path)} → ${href}`); links++
  }
}
const oq = readFileSync(resolve(root, 'docs/product/open-questions.md'), 'utf8').split('\n').find(line => line.startsWith('- **OQ-021'))
assert(oq.includes('Resolved') && oq.includes('6598264416') && oq.includes('2026-11-03'), 'Explicit owner approval must remain traceable')
assert(!existsSync(resolve(root, 'docs/ux/assets/fiscuvia-six-screens-reference.png')), 'Original asset has appeared: review provenance and update missing-asset register')
const manifest = JSON.parse(readFileSync(resolve(evidence, 'capture-manifest.json'), 'utf8'))
const git = args => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
// Historical #19 evidence is verified against its source, never represented as current #52 UI.
assert.equal(manifest.applicationTree, git(['rev-parse', `${manifest.captureSourceCommit}:web`]))
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex')
assert.equal(manifest.captureScriptSha256, hash(resolve(root, 'tools/ux/capture-reference.mjs')))
// The #19 runner was introduced in PR #87, after the application's captureSourceCommit.
assert.equal(manifest.harnessRunnerSha256, createHash('sha256').update(git(['show', '9007bca9388a2051da0acbd2e6836e524883fc48:tools/ci/auth-browser.mjs']) + '\n').digest('hex'))
assert.equal(manifest.screenshots.length, 17)
assert.equal(new Set(manifest.screenshots.map(s => s.file)).size, 17)
assert(manifest.checks.length === 3 && manifest.checks.every(c => c.passed))
for (const shot of manifest.screenshots) {
  assert(/^[a-z-]+-(1440|390)\.png$/.test(shot.file))
  const path = resolve(evidence, shot.file), bytes = readFileSync(path)
  assert.equal(hash(path), shot.sha256); assert.equal(bytes.readUInt32BE(16), shot.width); assert.equal(bytes.readUInt32BE(20), shot.height)
  assert((shot.width === 1440 && shot.height === 900) || (shot.width === 390 && shot.height === 844))
  assert.equal(shot.axeViolations, 0); assert.equal(shot.horizontalOverflow, false)
}
assert.equal(readdirSync(evidence).filter(f => f.endsWith('.png')).length, 17)
console.log(JSON.stringify({ documentationFiles: files.length, validLocalLinks: links, screenshots: 17, dimensionsAndHashes: 'PASS',
  applicationTree: manifest.applicationTree, captureSourceCommit: manifest.captureSourceCommit, oq021: 'Resolved for design system', original: 'MISSING',
  limitation: 'No pixel comparison, no complete WCAG certification, no approval inferred' }))
