// Records provenance after the real Playwright capture; never fabricates screenshots.
import { readFileSync, writeFileSync, readdirSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'
import { createRequire } from 'node:module'
const root = resolve(import.meta.dirname, '../..')
const dir = resolve(root, 'docs/ux/evidence/issue-32')
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const files = execFileSync('git', ['ls-files', '-z', '--cached', '--others', '--exclude-standard', '--', 'web/src', 'backend/src/main', 'web/e2e/plan-usage.spec.ts'], { cwd: root, encoding: 'utf8' }).split('\0').filter(Boolean).sort()
const sources = files.map(path => ({ sha256: hash(readFileSync(resolve(root, path))), path }))
const screenshots = readdirSync(dir).filter(path => path.endsWith('.png')).sort().map(file => {
  const bytes = readFileSync(resolve(dir, file)); const width = bytes.readUInt32BE(16); const height = bytes.readUInt32BE(20)
  if (width !== (file.includes('desktop') ? 1440 : 390) || height !== (file.includes('desktop') ? 900 : 844)) throw Error('Capture dimensions do not match requested viewport')
  return { file, width, height, sha256: hash(bytes), data: 'SYNTHETIC_PRESENTATION_FIXTURE_ONLY' }
})
if (screenshots.length !== 14) throw Error('Missing required capture')
const require = createRequire(resolve(root, 'web/package.json'))
const { chromium } = require('@playwright/test')
const browser = await chromium.launch()
const chromiumVersion = browser.version(); await browser.close()
writeFileSync(resolve(dir, 'capture-manifest.json'), JSON.stringify({ schemaVersion: 1, recordedAt: new Date().toISOString(), chromium: chromiumVersion, captureCommand: 'FISCUVIA_CAPTURE=1 npx playwright test e2e/plan-usage.spec.ts --project=desktop --project=mobile', fixture: 'web/e2e/plan-usage.spec.ts', securityEvidence: 'Separate real OIDC/PostgreSQL suites; these screenshots do not prove authorization', applicationSources: sources, screenshots, checks: ['axe WCAG A/AA passed in capture scenarios', 'document horizontal overflow absent', 'tenant switch removes previous usage', 'expired session removes usage'], limitations: ['TENANT_PLAN_VIEW remains DENY without approved policy', 'no WCAG certification or pixel-diff baseline'] }, null, 2) + '\n')
