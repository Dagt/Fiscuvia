// #19 documentation evidence only; no visual-diff baseline or application feature (#52/#56).
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync, spawn } from 'node:child_process'
import { get } from 'node:https'
import { setTimeout as pause } from 'node:timers/promises'
import { createHash, createHmac } from 'node:crypto'
import assert from 'node:assert/strict'
const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(resolve(root, 'web/package.json'))
const { chromium } = require('@playwright/test')
const AxeBuilder = require('@axe-core/playwright').default
const settings = JSON.parse(readFileSync(resolve(root, '.ci-work/27-browser.json'), 'utf8'))
const dir = resolve(root, 'docs/ux/evidence/issue-19')
mkdirSync(dir, { recursive: true })
const git = args => execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
const server = spawn(process.execPath, [resolve(root, 'web/node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', '4174', '--strictPort'], { cwd: resolve(root, 'web'), stdio: 'ignore', windowsHide: true })
const deadline = Date.now() + 45000
try {
  while (true) {
    const ready = await new Promise(resolve => {
      const request = get('https://127.0.0.1:4174/login', { rejectUnauthorized: false }, response => { response.resume(); resolve(response.statusCode === 200) })
      request.on('error', () => resolve(false)); request.setTimeout(1000, () => request.destroy())
    })
    if (ready) break
    if (server.exitCode !== null || Date.now() > deadline) throw Error('Local reference UI unavailable')
    await pause(200)
  }
} catch (error) { server.kill(); throw error }
const report = { schemaVersion: 1, capturedAt: new Date().toISOString(), captureSourceCommit: git(['rev-parse', 'HEAD']),
  applicationTree: git(['rev-parse', 'HEAD:web']),
  captureScriptSha256: createHash('sha256').update(readFileSync(fileURLToPath(import.meta.url))).digest('hex'),
  harnessRunnerSha256: createHash('sha256').update(readFileSync(resolve(root, 'tools/ci/auth-browser.mjs'))).digest('hex'),
  chromium: '', screenshots: [], checks: [] }
const browser = await chromium.launch()
report.chromium = browser.version()
const contextOptions = { baseURL: 'https://127.0.0.1:4174', ignoreHTTPSErrors: true, reducedMotion: 'reduce', deviceScaleFactor: 1 }
let context = await browser.newContext(contextOptions)
let page = await context.newPage()
async function freshSession() { await context.close(); context = await browser.newContext(contextOptions); page = await context.newPage() }
async function shot(name, width, height, source) {
  await page.setViewportSize({ width, height })
  await page.evaluate(() => document.fonts.ready)
  await page.evaluate(() => window.scrollTo(0, 0))
  assert.equal(new URL(page.url()).origin, 'https://127.0.0.1:4174', 'Capture only Fiscuvia, never provider credentials')
  const text = await page.locator('body').innerText()
  assert(!text.includes(settings.password) && !text.includes(settings.otpSecret), 'Private material must not be visible')
  assert(!/\b[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\b/i.test(text), 'No internal identifiers in public evidence')
  const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
  assert.equal(axe.violations.length, 0, 'Accessibility violation; inspect privately before publishing')
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Unexpected horizontal overflow')
  const file = `${name}-${width}.png`
  await page.screenshot({ path: resolve(dir, file), animations: 'disabled', fullPage: false })
  const bytes = readFileSync(resolve(dir, file))
  assert.equal(bytes.readUInt32BE(16), width); assert.equal(bytes.readUInt32BE(20), height)
  const geometry = await page.evaluate(() => Object.fromEntries(['.desktop-sidebar', '.topbar', '.page-container', '.auth-brand-panel', '.auth-content'].map(selector => {
    const el = document.querySelector(selector); if (!el) return [selector, null]
    const s = getComputedStyle(el), r = el.getBoundingClientRect()
    return [selector, { width: r.width, height: r.height, padding: s.padding, display: s.display }]
  })))
  report.screenshots.push({ file, width, height, source, sha256: createHash('sha256').update(bytes).digest('hex'), axeViolations: 0, horizontalOverflow: false, geometry })
}
async function both(name, source) { for (const [w, h] of [[1440, 900], [390, 844]]) await shot(name, w, h, source) }
async function readyLogin() { await page.goto('/login'); await page.getByRole('button', { name: 'Continuar con acceso seguro' }).waitFor() }
function otp() {
  const counter = Buffer.alloc(8); counter.writeBigInt64BE(BigInt(Math.floor(Date.now() / 30000)))
  const hash = createHmac('sha1', Buffer.from(settings.otpSecret, 'hex')).update(counter).digest()
  return String((hash.readUInt32BE(hash[19] & 15) & 0x7fffffff) % 1000000).padStart(6, '0')
}
async function provider(username) {
  await page.locator('#username').fill(username); await page.locator('#password').fill(settings.password)
  await page.locator('#kc-login').click(); await page.locator('#otp').waitFor()
  await page.locator('#otp').fill(otp()); await page.locator('#kc-login').click()
  await page.waitForURL(url => url.origin === 'https://127.0.0.1:4174')
}
async function exitSession() {
  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  await page.waitForURL(url => url.pathname === '/login')
  assert.equal((await page.request.get('/api/v1/me')).status(), 401)
}
try {
  await readyLogin(); await both('login', 'OIDC local disponible, sin sesión')
  await page.getByRole('link', { name: 'Fiscuvia, acceso' }).focus(); await page.keyboard.press('Tab')
  assert(await page.evaluate(() => getComputedStyle(document.activeElement).outlineStyle !== 'none'), 'Visible keyboard focus required')
  report.checks.push({ id: 'login-keyboard-focus', passed: true })
  await page.getByRole('button', { name: 'Continuar con acceso seguro' }).click(); await provider('alice-1')
  await page.waitForFunction(async () => (await (await fetch('/api/v1/me')).json()).memberships.length === 2)
  await page.reload(); await page.getByRole('button', { name: /Despacho 1/ }).waitFor()
  await both('seleccion', 'Login Keycloak real y dos membresías aprobadas por fixture interno existente')
  await page.getByRole('button', { name: /Despacho 1/ }).click(); await page.getByRole('heading', { name: 'Despacho autorizado' }).waitFor()
  await both('autorizado', 'Selección verificada por backend, no dashboard fiscal')
  await exitSession(); await freshSession(); await readyLogin()
  await page.getByRole('button', { name: 'Continuar con acceso seguro' }).click(); await provider('carol-1')
  await page.getByRole('heading', { name: 'Aún no tienes despachos asignados' }).waitFor()
  await shot('sin-membresias', 1440, 900, 'Identidad OIDC real sin membresías, no fallo de autenticación')
  await exitSession(); await page.goto('/app')
  await page.getByText('Tu sesión terminó.', { exact: false }).waitFor()
  await shot('sesion-expirada', 390, 844, 'Acceso a /app tras invalidar sesión mediante logout real')
  await freshSession(); await readyLogin()
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
  const start = await page.request.post('/api/v1/auth/login', { headers: { 'X-CSRF-TOKEN': csrf.token }, maxRedirects: 0 })
  assert.equal(start.status(), 303)
  const authorization = await page.request.get(start.headers().location, { maxRedirects: 0 })
  assert.equal(authorization.status(), 302)
  const manipulated = new URL(authorization.headers().location); manipulated.searchParams.set('state', 'untrusted-state')
  await page.goto(manipulated.toString()); await provider('carol-2')
  await page.getByRole('heading', { name: 'No pudimos verificar tu acceso' }).waitFor()
  await both('error-autenticacion', 'Rechazo real de state OIDC alterado, no API simulada')
  await page.goto('/desarrollo'); await both('shell', 'Bienvenida pública explícitamente demostrativa')
  await page.getByRole('button', { name: 'Abrir menú de navegación' }).focus(); await page.keyboard.press('Enter')
  const dialog = page.getByRole('dialog'); await dialog.waitFor()
  for (let i = 0; i < 6; i++) { await page.keyboard.press('Tab'); assert(await dialog.evaluate(el => el.contains(document.activeElement))) }
  await shot('menu', 390, 844, 'Radix Dialog real, foco confinado')
  await page.keyboard.press('Escape'); assert(await page.getByRole('button', { name: 'Abrir menú de navegación' }).evaluate(el => el === document.activeElement))
  report.checks.push({ id: 'mobile-menu-focus-trap-escape-restore', passed: true })
  for (const [state, w, h] of [['vacio',1440,900],['carga',390,844],['error',1440,900],['permisos',390,844]]) {
    await page.goto(`/desarrollo/estados?estado=${state}`)
    await page.getByRole('heading', { name: 'Cada estado, una respuesta clara' }).waitFor()
    await shot(`demo-${state}`, w, h, 'Estado demostrativo rotulado; no representa datos ni autorización de cuenta')
  }
  report.checks.push({ id: 'no-credential-or-provider-screenshots', passed: true })
  writeFileSync(resolve(dir, 'capture-manifest.json'), JSON.stringify(report, null, 2) + '\n')
  console.log(JSON.stringify({ screenshots: report.screenshots.length, source: report.captureSourceCommit, applicationTree: report.applicationTree, chromium: report.chromium, checks: report.checks }))
} finally { await context.close(); await browser.close(); server.kill() }
