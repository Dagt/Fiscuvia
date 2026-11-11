import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { createHash, createHmac } from 'node:crypto'
import { resolve } from 'node:path'
import { execFileSync } from 'node:child_process'
import { validator } from '../../tools/ci/contracts.mjs'

const privateSettings = JSON.parse(
  readFileSync(process.env.FISCUVIA_AUTH_HARNESS_FILE!, 'utf8'),
) as { password: string; otpSecret: string }
const capture = process.env.FISCUVIA_TEAM_CAPTURE === '1'
const root = resolve('..')
const folder = resolve(
  root,
  capture ? 'docs/ux/evidence/issue-33' : '.ci-work/team-browser-evidence',
)
mkdirSync(folder, { recursive: true })
const shots: object[] = []
let chromiumVersion = ''
const git = (args: string[]) =>
  execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
function otp() {
  const counter = Buffer.alloc(8)
  counter.writeBigInt64BE(BigInt(Math.floor(Date.now() / 30000)))
  const hash = createHmac('sha1', Buffer.from(privateSettings.otpSecret, 'hex'))
    .update(counter)
    .digest()
  return String(
    (hash.readUInt32BE(hash[19]! & 15) & 0x7fffffff) % 1000000,
  ).padStart(6, '0')
}
async function login(page: Page, name: string) {
  await page.goto('/login')
  await page
    .getByRole('button', { name: 'Continuar con acceso seguro' })
    .click()
  await page.locator('#username').fill(name)
  await page.locator('#password').fill(privateSettings.password)
  await page.locator('#kc-login').click()
  await page.locator('#otp').fill(otp())
  await page.locator('#kc-login').click()
  await page.waitForURL(
    (u) => u.origin === 'https://127.0.0.1:4174' && u.pathname === '/app',
  )
  await expect
    .poll(
      async () => (await (await page.request.get('/api/v1/me')).json()).userId,
    )
    .toBeTruthy()
}
async function owner(page: Page, name: string) {
  await login(page, name)
  await expect
    .poll(
      async () =>
        (await (await page.request.get('/api/v1/me')).json()).memberships
          ?.length ?? 0,
    )
    .toBe(2)
  await page.reload()
  await page.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
  await page.getByLabel('Nombre comercial', { exact: false }).waitFor()
}
async function api(
  page: Page,
  path: string,
  data: object,
  headers: Record<string, string> = {},
) {
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
  return page.request.post('/api/v1' + path, {
    headers: { 'X-CSRF-TOKEN': csrf.token, ...headers },
    data,
  })
}
function conforms(name: string, value: unknown) {
  const check = validator(name)
  if (!check(value)) throw Error('HTTP contract mismatch: ' + name)
}
async function take(page: Page, state: string, secret?: string) {
  chromiumVersion = page.context().browser()!.version()
  for (const [width, height] of [
    [1440, 900],
    [390, 844],
  ]) {
    await page.setViewportSize({ width: width!, height: height! })
    await page.evaluate(() => document.fonts.ready)
    const text = await page.locator('body').innerText()
    if (
      text.includes(privateSettings.password) ||
      text.includes(privateSettings.otpSecret) ||
      (secret && text.includes(secret)) ||
      (await page.locator('[data-private]').count())
    )
      throw Error('Private material must not enter evidence')
    const axe = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
      .analyze()
    expect(axe.violations.length).toBe(0)
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    const panel = page.getByRole('region', { name: 'Invitaciones del equipo' })
    if (await panel.count()) await panel.scrollIntoViewIfNeeded()
    const file = `${state}-${width}.png`
    await page.screenshot({
      path: resolve(folder, file),
      animations: 'disabled',
      fullPage: false,
    })
    const bytes = readFileSync(resolve(folder, file))
    shots.push({
      file,
      width,
      height,
      sha256: createHash('sha256').update(bytes).digest('hex'),
      axeViolations: 0,
      horizontalOverflow: false,
    })
  }
}
test.afterAll(() => {
  if (!capture) return
  if (
    git([
      'diff',
      '--name-only',
      'HEAD',
      '--',
      'web',
      'backend/src',
      'tools/ci/auth-browser.mjs',
    ])
  )
    throw Error('Commit source before public capture')
  writeFileSync(
    resolve(folder, 'capture-manifest.json'),
    JSON.stringify(
      {
        sourceSha: git(['rev-parse', 'HEAD']),
        applicationTree: git(['rev-parse', 'HEAD:web']),
        backendTree: git(['rev-parse', 'HEAD:backend/src/main']),
        fixtureTree: git(['rev-parse', 'HEAD:backend/src/test']),
        chromium: chromiumVersion,
        capturedAt: new Date().toISOString(),
        scope:
          'Email invitations local/test only; no general team administration or external email delivery',
        screenshots: shots,
      },
      null,
      2,
    ) + '\n',
  )
})

test('correo local verificado, token de un uso, replay sin secreto y aceptación OIDC exacta', async ({
  page,
  browser,
}) => {
  // Two real OIDC browser sessions, acceptance/replay and ten axe-backed captures.
  // Existing suites retain their original timeout and zero retries.
  test.setTimeout(120000)
  const recipientContext = await browser.newContext({
    baseURL: 'https://127.0.0.1:4174',
    ignoreHTTPSErrors: true,
  })
  const recipient = await recipientContext.newPage()
  try {
    await login(recipient, 'bob-20')
    await owner(page, 'alice-11')
    // Hold an actually authorized response only to capture the real loading state.
    let release!: () => void
    const held = new Promise<void>((r) => {
      release = r
    })
    let received!: () => void
    const fetched = new Promise<void>((r) => {
      received = r
    })
    let delivered!: () => void
    const completed = new Promise<void>((resolve) => {
      delivered = resolve
    })
    await page.route(
      '**/team/invitation-options',
      async (route) => {
        const response = await route.fetch()
        received()
        await held
        await route.fulfill({ response })
        delivered()
      },
      { times: 1 },
    )
    await page.getByRole('button', { name: 'Equipo', exact: true }).click()
    await fetched
    await take(page, 'cargando')
    release()
    await completed
    await page.getByLabel('Correo del colaborador').waitFor()
    await take(page, 'formulario')
    await page
      .getByLabel('Correo del colaborador')
      .fill('bob-20@example.invalid')
    await page
      .getByRole('button', { name: 'Invitar colaborador', exact: true })
      .click()
    const response = page.waitForResponse(
      (r) =>
        r.url().endsWith('/team/invitations') &&
        r.request().method() === 'POST',
    )
    await page
      .getByRole('alertdialog')
      .getByRole('button', { name: 'Crear invitación', exact: true })
      .click()
    const http = await response
    expect(http.status()).toBe(201)
    const result = await http.json()
    conforms('LocalEmailInvitation', result)
    const secret = result.token as string
    expect(secret.length).toBe(43)
    const request = http.request()
    const headers = await request.allHeaders()
    const route = new URL(request.url()).pathname.replace('/api/v1', '')
    const replay = await api(
      page,
      route,
      { email: 'bob-20@example.invalid', role: 'ACCOUNTANT' },
      { 'Idempotency-Key': headers['idempotency-key']! },
    )
    expect(replay.status()).toBe(200)
    const old = await replay.json()
    conforms('LocalEmailInvitation', old)
    expect(old.token).toBeNull()
    await page.getByRole('button', { name: 'Ya guardé el token' }).click()
    await take(page, 'creada-entrega-manual', secret)
    expect(
      (await api(page, '/auth/invitations/accept', { token: secret })).status(),
    ).toBe(403)
    expect(
      (
        await api(recipient, '/auth/invitations/accept', { token: secret })
      ).status(),
    ).toBe(200)
    expect(
      (
        await api(recipient, '/auth/invitations/accept', { token: secret })
      ).status(),
    ).toBe(403)
    await recipient.reload()
    await recipient
      .getByRole('button', { name: /Despacho Horizonte DEMO/ })
      .click()
    await recipient.getByRole('button', { name: 'Equipo', exact: true }).click()
    await expect(recipient.getByText('Sin permiso para invitar')).toBeVisible()
    await take(recipient, 'sin-permiso', secret)
  } finally {
    await recipientContext.close()
  }
})

test('revocación real retira el token y el cambio de despacho limpia el formulario', async ({
  page,
  browser,
}) => {
  const context = await browser.newContext({
    baseURL: 'https://127.0.0.1:4174',
    ignoreHTTPSErrors: true,
  })
  const recipient = await context.newPage()
  try {
    await login(recipient, 'bob-21')
    await owner(page, 'alice-12')
    await page.getByRole('button', { name: 'Equipo', exact: true }).click()
    await page
      .getByLabel('Correo del colaborador')
      .fill('bob-21@example.invalid')
    await page
      .getByRole('button', { name: 'Invitar colaborador', exact: true })
      .click()
    const response = page.waitForResponse(
      (r) =>
        r.url().endsWith('/team/invitations') &&
        r.request().method() === 'POST',
    )
    await page
      .getByRole('alertdialog')
      .getByRole('button', { name: 'Crear invitación', exact: true })
      .click()
    const http = await response
    expect(http.status()).toBe(201)
    const secret = (await http.json()).token as string
    await page
      .getByRole('button', { name: 'Revocar invitación', exact: true })
      .click()
    await page
      .getByRole('alertdialog')
      .getByRole('button', { name: 'Revocar invitación', exact: true })
      .click()
    await expect(
      page.getByText('Solicitud de revocación procesada.'),
    ).toBeVisible()
    expect(
      (
        await api(recipient, '/auth/invitations/accept', { token: secret })
      ).status(),
    ).toBe(403)
    await page
      .getByLabel('Correo del colaborador')
      .fill('pendiente@example.invalid')
    await page.getByRole('button', { name: 'Cambiar de despacho' }).click()
    await page
      .getByRole('button', { name: /Despacho de Servicios Profesionales/ })
      .click()
    await page.getByRole('button', { name: 'Equipo', exact: true }).click()
    await expect(page.getByLabel('Correo del colaborador')).toHaveValue('')
    expect(await page.locator('[data-private]').count()).toBe(0)
  } finally {
    await context.close()
  }
})

test('destinatario no verificado produce error real sin enumeración ni éxito simulado', async ({
  page,
}) => {
  await owner(page, 'alice-13')
  await page.getByRole('button', { name: 'Equipo', exact: true }).click()
  await page
    .getByLabel('Correo del colaborador')
    .fill('carol-1@example.invalid')
  await page
    .getByRole('button', { name: 'Invitar colaborador', exact: true })
    .click()
  const response = page.waitForResponse(
    (r) =>
      r.url().endsWith('/team/invitations') && r.request().method() === 'POST',
  )
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Crear invitación', exact: true })
    .click()
  const r = await response
  expect(r.status()).toBe(422)
  conforms('ProblemDetails', await r.json())
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Cancelar', exact: true })
    .click()
  await expect(page.getByRole('alert')).toContainText(
    'No fue posible generar la invitación',
  )
  expect(await page.locator('[data-private]').count()).toBe(0)
  await take(page, 'error-destinatario')
})
