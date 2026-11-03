// Real Chromium evidence for #52. No pixel-diff baseline (#56) and no provider screenshots.
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
const dir = resolve(root, 'docs/ux/evidence/issue-52')
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
async function shot(name, width, height, source, scrollTarget) {
  await page.setViewportSize({ width, height })
  await page.evaluate(() => document.fonts.ready)
  await page.waitForFunction(() => !document.querySelector('.catalog-page') ||
    (matchMedia('(max-width: 699px)').matches ? !document.querySelector('#tabla table') : !document.querySelector('.data-mobile-list')))
  if (scrollTarget) await page.locator(scrollTarget).evaluate(el => el.scrollIntoView({ block: 'start' }))
  else await page.evaluate(() => window.scrollTo(0, 0))
  assert.equal(new URL(page.url()).origin, 'https://127.0.0.1:4174', 'Capture only Fiscuvia, never provider credentials')
  const text = await page.locator('body').innerText()
  assert(!text.includes(settings.password) && !text.includes(settings.otpSecret), 'Private material must not be visible')
  assert(!/\b[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\b/i.test(text), 'No internal identifiers in public evidence')
  const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
  assert.equal(axe.violations.length, 0, 'Accessibility violation; inspect privately before publishing')
  assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Unexpected horizontal overflow')
  // Responsive React rendering and axe may change scroll anchoring. Position again immediately before capture.
  if (scrollTarget) {
    await page.locator(scrollTarget).evaluate(el => el.scrollIntoView({ block: 'start' }))
    assert(await page.locator(scrollTarget).evaluate(el => { const r = el.getBoundingClientRect(); return r.top < innerHeight && r.bottom > 0 }), 'Capture target must be in the viewport')
  } else await page.evaluate(() => window.scrollTo(0, 0))
  const file = `${name}-${width}.png`
  await page.screenshot({ path: resolve(dir, file), animations: 'disabled', fullPage: false })
  const bytes = readFileSync(resolve(dir, file))
  assert.equal(bytes.readUInt32BE(16), width); assert.equal(bytes.readUInt32BE(20), height)
  const geometry = await page.evaluate(() => Object.fromEntries(['.desktop-sidebar', '.topbar', '.page-container', '.auth-brand-panel', '.auth-content'].map(selector => {
    const el = document.querySelector(selector); if (!el) return [selector, null]
    const s = getComputedStyle(el), r = el.getBoundingClientRect()
    return [selector, { width: r.width, height: r.height, padding: s.padding, display: s.display }]
  })))
  report.screenshots.push({ file, width, height, source, target: scrollTarget ?? 'page-start', sha256: createHash('sha256').update(bytes).digest('hex'), axeViolations: 0, horizontalOverflow: false, geometry })
}
async function both(name, source, scrollTarget) { for (const [w, h] of [[1440, 900], [390, 844]]) await shot(name, w, h, source, scrollTarget) }
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
  await readyLogin(); await both('login', 'OIDC local disponible; sin credenciales visibles')
  await page.getByRole('button', { name: 'Continuar con acceso seguro' }).click(); await provider('alice-1')
  await page.waitForFunction(async () => (await (await fetch('/api/v1/me')).json()).memberships.length === 2)
  await page.reload(); await page.getByRole('button', { name: /Despacho 1/ }).waitFor()
  await both('seleccion-autorizada', 'Identidad Keycloak real, membresías reales de fixture interno; sin nombres comerciales inventados')
  await page.getByRole('button', { name: /Despacho 1/ }).click()
  await page.getByRole('heading', { name: 'Despacho autorizado' }).waitFor()
  await both('shell-autenticado', 'Contexto comprobado por POST CSRF y revalidación de membresía')
  await page.getByRole('button', { name: 'Abrir menú de navegación' }).click()
  await shot('menu-autenticado', 390, 844, 'Menú Radix, foco confinado y secciones futuras deshabilitadas')
  await page.keyboard.press('Escape')
  assert(await page.getByRole('button', { name: 'Abrir menú de navegación' }).evaluate(el => el === document.activeElement))
  report.checks.push({ id: 'authenticated-menu-escape-restore', passed: true })
  await exitSession(); await freshSession(); await readyLogin()
  await page.getByRole('button', { name: 'Continuar con acceso seguro' }).click(); await provider('carol-1')
  await page.getByRole('heading', { name: 'Aún no tienes despachos asignados' }).waitFor()
  await both('sin-membresias', 'Identidad real sin despachos; acceso fiscal no disponible')
  await exitSession(); await page.goto('/app'); await page.getByText('Tu sesión terminó.', { exact: false }).waitFor()
  await both('sesion-expirada', 'Logout real seguido de 401')
  await freshSession(); await readyLogin()
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
  const start = await page.request.post('/api/v1/auth/login', { headers: { 'X-CSRF-TOKEN': csrf.token }, maxRedirects: 0 })
  assert.equal(start.status(), 303)
  const authorization = await page.request.get(start.headers().location, { maxRedirects: 0 })
  assert.equal(authorization.status(), 302)
  const manipulated = new URL(authorization.headers().location); manipulated.searchParams.set('state', 'untrusted-state')
  await page.goto(manipulated.toString()); await provider('carol-2')
  await page.getByRole('heading', { name: 'No pudimos verificar tu acceso' }).waitFor()
  await both('error-autenticacion', 'State OIDC alterado, error real seguro')
  await page.goto('/desarrollo'); await both('shell-desarrollo', 'Bienvenida existente, sin datos fiscales')
  await page.goto('/desarrollo/componentes')
  await page.getByRole('heading', { name: 'Una sola identidad visual' }).waitFor()
  await both('catalogo', 'Catálogo público de desarrollo; DEMO explícita')
  for (const [w, h] of [[1280, 800], [768, 1024]]) await shot('catalogo', w, h, 'Geometría compacta y tableta')
  for (const [name, target] of [['controles-p0', '#controles'], ['tabla-p1', '.data-table'], ['estados-p1', '#estados']])
    await both(name, 'Componentes compartidos con datos sintéticos, no funcionalidades fiscales', target)
  await page.getByRole('button', { name: 'Validar ejemplo', exact: true }).click()
  await both('formulario-invalido', 'Error asociado, texto seguro, foco en campo requerido', '#demo-name')
  await page.getByRole('button', { name: 'Reiniciar DEMO', exact: true }).click()
  await both('confirmacion', 'Diálogo destructivo DEMO, cancelar inicialmente enfocado')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Abrir panel lateral', exact: true }).click()
  await both('drawer', 'Panel modal contextual400/full móvil; no contiene un recurso de negocio')
  await page.keyboard.press('Escape')
  for (const [state, name] of [['loading', 'carga'], ['error', 'error']]) {
    await page.getByLabel('Estado del panel lateral DEMO').selectOption(state)
    await page.getByRole('button', { name: 'Abrir panel lateral', exact: true }).click()
    await both(`drawer-${name}`, 'Estado transversal sin contenido o acciones anteriores')
    await page.keyboard.press('Escape')
  }
  await page.getByLabel('Estado del panel lateral DEMO').selectOption('ready')
  await page.getByRole('button', { name: 'Ver detalle de DEMO-001', exact: true }).click()
  await both('detalle-inline', 'Aside inline, no modal; texto100 caracteres y cifra grande sintética', '.details-panel')
  await page.getByRole('button', { name: 'Cerrar detalle', exact: true }).click()
  await page.getByRole('button', { name: 'Mostrar estado pendiente' }).click()
  await both('pendiente', 'Botón pendiente bloqueado y alternativa para restablecer DEMO', '#controles')
  for (const [state, name] of [['loading','carga'], ['empty','vacio'], ['error','error'], ['unauthorized','sin-permisos']]) {
    await page.getByLabel('Estado de tabla DEMO').selectOption(state)
    await both(`tabla-${name}`, 'No se muestran filas ni conteos del estado anterior', '#tabla')
  }
  await page.getByLabel('Estado de tabla DEMO').selectOption('ready')
  await page.setViewportSize({ width: 1440, height: 900 })
  const focusButton = page.getByRole('button', { name: 'Acción primaria', exact: true })
  await focusButton.focus(); await page.keyboard.press('Tab'); await page.keyboard.press('Shift+Tab')
  assert(await focusButton.evaluate(el => getComputedStyle(el).outlineStyle !== 'none'))
  await shot('foco', 1440, 900, 'Foco visible por teclado en acción; disabled y pending diferenciados', '#controles')
  await page.getByRole('button', { name: 'Secundaria', exact: true }).click()
  await both('notificacion', 'Aviso DEMO local con cierre44; sin envío a servidor', '#controles')
  report.checks.push({ id: 'keyboard-focus-visible', passed: true }, { id: 'no-credential-or-provider-screenshots', passed: true })
  writeFileSync(resolve(dir, 'capture-manifest.json'), JSON.stringify(report, null, 2) + '\n')
  console.log(JSON.stringify({ screenshots: report.screenshots.length, source: report.captureSourceCommit, applicationTree: report.applicationTree, chromium: report.chromium, checks: report.checks }))
} finally { await context.close(); await browser.close(); server.kill() }
