// Public evidence verification, not a pixel-diff baseline or WCAG certification.
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import assert from 'node:assert/strict'
const root = fileURLToPath(new URL('../../', import.meta.url))
const dir = resolve(root, 'docs/ux/evidence/issue-52')
const manifest = JSON.parse(readFileSync(resolve(dir, 'capture-manifest.json'), 'utf8'))
const git = args => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex')
assert.equal(manifest.applicationTree, git(['rev-parse', 'HEAD:web']), 'Capture UI differs from final HEAD')
assert.equal(manifest.applicationTree, git(['rev-parse', `${manifest.captureSourceCommit}:web`]))
assert.equal(manifest.captureScriptSha256, hash(resolve(root, 'tools/ux/capture-design-system.mjs')))
assert.equal(manifest.harnessRunnerSha256, hash(resolve(root, 'tools/ci/auth-browser.mjs')))
assert(manifest.checks.length >= 3 && manifest.checks.every(check => check.passed))
assert(manifest.screenshots.length >= 35)
assert.equal(new Set(manifest.screenshots.map(shot => shot.file)).size, manifest.screenshots.length)
for (const shot of manifest.screenshots) {
  assert(/^[a-z0-9-]+-(1440|1280|768|390)\.png$/.test(shot.file))
  const file = resolve(dir, shot.file), bytes = readFileSync(file)
  assert.equal(hash(file), shot.sha256)
  assert.equal(bytes.readUInt32BE(16), shot.width); assert.equal(bytes.readUInt32BE(20), shot.height)
  assert([[1440,900],[1280,800],[768,1024],[390,844]].some(([w,h]) => shot.width === w && shot.height === h))
  assert.equal(shot.axeViolations, 0); assert.equal(shot.horizontalOverflow, false)
  const content = shot.geometry['.page-container'], sidebar = shot.geometry['.desktop-sidebar'], header = shot.geometry['.topbar']
  if (content) {
    const padding = shot.width >= 1024 ? 32 : shot.width >= 700 ? 24 : 16
    assert(Math.abs(parseFloat(content.padding) - padding) <= 1, `${shot.file}: content padding`)
    assert(header.height >= (shot.width >= 1024 ? 66 : 64), `${shot.file}: header height`)
    if (shot.width >= 1024) {
      assert.equal(sidebar.width, 240)
      assert.equal(content.width - padding * 2, shot.width - 240 - 64)
    } else assert.equal(sidebar.display, 'none')
  }
}
assert.equal(readdirSync(dir).filter(name => name.endsWith('.png')).length, manifest.screenshots.length)
let links = 0
for (const relative of ['docs/ux/DESIGN-SYSTEM-52.md', 'docs/ux/evidence/issue-52/README.md', 'web/README.md']) {
  const file = resolve(root, relative)
  for (const match of readFileSync(file, 'utf8').matchAll(/!?\[[^\]]*\]\(([^)]+)\)/g)) {
    const target = match[1].split('#')[0]
    if (!target || /^https?:|mailto:/.test(target)) continue
    assert(existsSync(resolve(dirname(file), decodeURIComponent(target))), `${relative}: broken local link ${target}`); links++
  }
}
const oq = readFileSync(resolve(root, 'docs/product/open-questions.md'), 'utf8').split('\n').find(line => line.startsWith('- **OQ-021'))
assert(oq.includes('Resolved') && oq.includes('6598264416') && oq.includes('2026-11-03'))
console.log(JSON.stringify({ testedHead: git(['rev-parse','HEAD']), captureSource: manifest.captureSourceCommit, sameApplicationTree: true,
  screenshots: manifest.screenshots.length, validLocalLinks: links, dimensionsHashesAxeOverflow: 'PASS', chromium: manifest.chromium,
  limitation: 'Real UI evidence; no pixel-diff or complete WCAG certification; production identity/permissions/fiscal gates unchanged' }))
