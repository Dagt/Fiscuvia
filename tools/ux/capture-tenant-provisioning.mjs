// Real Chromium evidence for #31. No pixel-diff baseline (#56) and no provider screenshots.
import { createRequire } from 'node:module'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync, spawn } from 'node:child_process'
import { get } from 'node:https'
import { setTimeout as pause } from 'node:timers/promises'
import { createHash, createHmac, randomUUID } from 'node:crypto'
import assert from 'node:assert/strict'
const root = fileURLToPath(new URL('../../', import.meta.url))
const require = createRequire(resolve(root, 'web/package.json'))
const { chromium } = require('@playwright/test')
const AxeBuilder = require('@axe-core/playwright').default
const settings = JSON.parse(readFileSync(resolve(root, '.ci-work/27-browser.json'), 'utf8'))
const dir = resolve(root, 'docs/ux/evidence/issue-31')
mkdirSync(dir, { recursive: true })
assert.equal(execFileSync('git',['diff','--name-only','HEAD','--','web','backend/src','tools/ci/auth-browser.mjs'],{cwd:root,encoding:'utf8'}).trim(),'','Commit application/harness sources before capture')
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
  settingsPolicy: { migration: '7', approvalReference: 'https://github.com/Dagt/Fiscuvia/pull/89#issuecomment-6649647209', harnessGrant: false },
  applicationTree: git(['rev-parse', 'HEAD:web']), backendTree: git(['rev-parse','HEAD:backend/src/main']), fixtureTree: git(['rev-parse','HEAD:backend/src/test']),
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

async function loginAs(username, count) {
  await readyLogin(); await page.getByRole('button',{name:'Continuar con acceso seguro'}).click(); await provider(username)
  await page.waitForFunction(async n => (await (await fetch('/api/v1/me')).json()).memberships?.length === n,count)
  await page.reload(); await page.getByRole('heading',{name:'Elige tu despacho'}).waitFor()
}
async function csrfRequest(target,path,data) {
  const csrf=await(await target.request.get('/api/v1/auth/csrf')).json()
  return target.request.post(path,{headers:{'X-CSRF-TOKEN':csrf.token,'Idempotency-Key':randomUUID()},data})
}
function subject(username){const bytes=createHash('md5').update(`fiscuvia-test:${username}`).digest();bytes[6]=(bytes[6]&15)|48;bytes[8]=(bytes[8]&63)|128;const h=bytes.toString('hex');return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`}
async function chooseHorizonte(){await page.getByRole('button',{name:/Despacho Horizonte DEMO/}).click();await page.getByLabel('Nombre comercial',{exact:false}).waitFor()}
try {
  await loginAs('alice-2',2)
  await both('selector','Dos despachos aprovisionados por procedimiento real tras OIDC; metadata propia persistida')
  const memberships=(await(await page.request.get('/api/v1/me')).json()).memberships
  const tenant=memberships.find(m=>m.commercialName==='Despacho Horizonte DEMO').tenantId
  await chooseHorizonte()
  for(const [w,h]of[[1440,900],[1280,800],[768,1024],[390,844]])await shot('formulario',w,h,'GET settings real; política permanente owner+MFA aprobada e instalada por Flyway V7')
  await both('formulario-acciones','Formulario real completo; guardar deshabilitado hasta modificar datos, sin acciones fuera del viewport al desplazar','.firm-form-actions')
  await both('sin-logo','Contrato NULL y fallback Lucide; no existe upload ni almacenamiento público')
  await page.setViewportSize({width:320,height:900})
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));report.checks.push({id:'reflow-320',passed:true})
  await page.setViewportSize({width:1440,height:900});await page.evaluate(()=>{document.body.style.zoom='2'})
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));report.checks.push({id:'zoom-200-percent',passed:true})
  await page.evaluate(()=>{document.body.style.zoom=''})
  const input=page.getByLabel('Nombre comercial',{exact:false})
  await input.fill('');await page.getByRole('button',{name:'Guardar cambios'}).click()
  await page.getByText('Este campo es obligatorio.').waitFor();await both('validacion','Constraint nativo y mensaje Field asociado; sin mutación')
  await page.getByRole('button',{name:'Cancelar',exact:true}).click()
  await input.fill('Despacho Horizonte actualizado DEMO');await page.getByRole('button',{name:'Guardar cambios'}).click()
  await page.getByRole('alertdialog').waitFor();assert(await page.getByRole('button',{name:'Cancelar',exact:true}).last().evaluate(el=>el===document.activeElement))
  await both('confirmacion','Radix compartido #52; foco inicial en cancelar, sin guardar todavía')
  await page.keyboard.press('Escape');assert(await page.getByRole('button',{name:'Guardar cambios'}).evaluate(el=>el===document.activeElement))
  report.checks.push({id:'confirmation-escape-focus-restore',passed:true})
  await page.getByRole('button',{name:'Guardar cambios'}).click();await page.getByRole('button',{name:'Confirmar cambios'}).click()
  await page.getByText('Datos del despacho guardados').waitFor()
  assert.equal((await(await page.request.get(`/api/v1/tenants/${tenant}/settings`)).json()).commercialName,'Despacho Horizonte actualizado DEMO')
  await page.getByRole('button',{name:'Contexto actual: Despacho Horizonte actualizado DEMO'}).waitFor()
  await both('guardado','POST real confirmado, GET verifica persistencia y /me actualiza nombre del shell')
  await page.getByRole('button',{name:'Cerrar notificación'}).click()
  const profile=await(await page.request.get(`/api/v1/tenants/${tenant}/settings`)).json()
  assert.equal((await csrfRequest(page,`/api/v1/tenants/${tenant}/settings`,{expectedVersion:profile.version,commercialName:'Despacho Horizonte DEMO',timeZone:profile.timeZone,locale:'es-MX'})).status(),200)
  await page.reload();await chooseHorizonte();await page.getByRole('button',{name:'Cambiar de despacho'}).click()
  await page.getByRole('button',{name:/Despacho de Servicios Profesionales/}).click();await page.getByLabel('Nombre comercial',{exact:false}).waitFor()
  await both('nombre-largo','Nombre persistido extenso; wrapping en contenido y nombre accesible completo del contexto')
  await page.getByRole('button',{name:'Cambiar de despacho'}).click()
  let release;const waiting=new Promise(resolve=>{release=resolve});let reached;const entered=new Promise(resolve=>{reached=resolve})
  await page.route('**/api/v1/tenants/*/settings',async route=>{reached();await waiting;await route.abort('failed')})
  await page.getByRole('button',{name:/Despacho Horizonte DEMO/}).click();await entered
  await page.getByText('Consultando tu despacho').waitFor();await both('loading','Solicitud real retenida en Chromium; no hay datos anteriores visibles')
  release();await page.getByRole('button',{name:'Reintentar'}).waitFor();await both('error','Fallo de transporte inyectado en navegador; no un éxito simulado')
  await page.unroute('**/api/v1/tenants/*/settings');await page.getByRole('button',{name:'Reintentar'}).click();await page.getByLabel('Nombre comercial',{exact:false}).waitFor()
  report.checks.push({id:'recoverable-error-real-retry',passed:true})
  const ownerPage=page, ownerContext=context
  context=await browser.newContext(contextOptions);page=await context.newPage();await loginAs('carol-5',0)
  const issued=await csrfRequest(ownerPage,'/api/v1/auth/invitations',{tenantId:tenant,subject:subject('carol-5'),role:'ACCOUNTANT'})
  assert.equal(issued.status(),201);const invitation=await issued.json()
  assert.equal((await csrfRequest(page,'/api/v1/auth/invitations/accept',{token:invitation.token})).status(),200)
  const member=(await(await page.request.get('/api/v1/me')).json()).memberships[0]
  await page.reload();await chooseHorizonte();await both('solo-lectura','Membresía ACCOUNTANT real; sin capacidad de edición, inputs readonly')
  assert.equal(await page.getByRole('button',{name:'Guardar cambios'}).count(),0)
  await page.getByRole('button',{name:'Cambiar de despacho'}).click()
  let continueRead;const held=new Promise(resolve=>{continueRead=resolve});let reachedRead;const atRead=new Promise(resolve=>{reachedRead=resolve})
  await page.route('**/api/v1/tenants/*/settings',async route=>{reachedRead();await held;await route.continue()})
  await page.getByRole('button',{name:/Despacho Horizonte DEMO/}).click();await atRead
  assert.equal((await csrfRequest(ownerPage,'/api/v1/auth/memberships/status',{tenantId:tenant,membershipId:member.membershipId,status:'REVOKED'})).status(),204)
  continueRead();await page.getByText('Necesitas permisos para continuar').waitFor();await page.getByText('Sin despacho seleccionado').waitFor()
  assert.equal(await page.getByLabel('Nombre comercial',{exact:false}).count(),0)
  await both('denegado','403 real después de revocación confirmada; metadata y contexto retirados, identidad global conservada')
  report.checks.push({id:'revocation-real-403-context-cleared',passed:true})
  await context.close();context=ownerContext;page=ownerPage;await exitSession();await freshSession();await loginAs('bob-3',1)
  await page.waitForFunction(async()=> (await(await fetch('/api/v1/me')).json()).memberships[0]?.tenantStatus==='SUSPENDED')
  await page.reload();await page.getByRole('button',{name:/Despacho suspendido DEMO/}).waitFor()
  assert(await page.getByRole('button',{name:/Despacho suspendido DEMO/}).isDisabled())
  await both('suspendido','Estado persistido por procedimiento administrativo real; selector propio visible, acceso operativo denegado')
  report.checks.push({id:'suspended-firm-disabled',passed:true})
  writeFileSync(resolve(dir,'capture-manifest.json'),JSON.stringify(report,null,2)+'\n')
  console.log(JSON.stringify({captureSourceCommit:report.captureSourceCommit,chromium:report.chromium,screenshots:report.screenshots.length,checks:report.checks.length}))
} finally { await context.close();await browser.close();server.kill() }
