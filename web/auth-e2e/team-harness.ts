import { expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync, mkdirSync, writeFileSync, existsSync } from 'node:fs'
import { createHash, createHmac } from 'node:crypto'
import { resolve } from 'node:path'
import { execFileSync } from 'node:child_process'

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
export async function login(page: Page, name: string) {
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
export async function owner(page: Page, name: string) {
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
export async function api(
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
export async function take(page: Page, state: string, secret?: string) {
  if (state === 'equipo-vacio' || state === 'equipo-error-recuperable') {
    const close = page.getByRole('button', {
      name: 'Cerrar notificación',
      exact: true,
    })
    if (await close.count()) await close.click()
  }
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
    else {
      const team = page.getByRole('region', {
        name: 'Administraci\u00f3n del equipo',
      })
      if (await team.count()) await team.scrollIntoViewIfNeeded()
    }
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
export function finishCaptures() {
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
  const source = git(['rev-parse', 'HEAD'])
  const manifest = resolve(folder, 'capture-manifest.json')
  const prior = existsSync(manifest)
    ? JSON.parse(readFileSync(manifest, 'utf8'))
    : null
  const combined = new Map<string, object>()
  if (prior?.sourceSha === source)
    for (const row of prior.screenshots) combined.set(row.file, row)
  for (const row of shots as { file: string }[]) combined.set(row.file, row)
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
          'Approved owner team administration; local/test verified email only; no fiscal portfolio or external email delivery',
        screenshots: [...combined.values()],
      },
      null,
      2,
    ) + '\n',
  )
}
