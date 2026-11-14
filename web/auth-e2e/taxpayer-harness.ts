import { expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync, writeFileSync, mkdirSync, renameSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
export { login, owner, api } from './team-harness'
const settings = JSON.parse(
  readFileSync(process.env.FISCUVIA_AUTH_HARNESS_FILE!, 'utf8'),
) as { fiscalControl: string; password: string; otpSecret: string }
const root = resolve('..'),
  capture = process.env.FISCUVIA_TAXPAYER_CAPTURE === '1'
const folder = resolve(
  root,
  capture ? 'docs/ux/evidence/issue-35' : '.ci-work/taxpayer-browser-evidence',
)
mkdirSync(folder, { recursive: true })
const shots: object[] = []
let chromium = ''
const git = (args: string[]) =>
  execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
export async function fixture(
  tenant: string,
  kind: 'grant' | 'quota' | 'expireMfa',
  fields: Record<string, string | number> = {},
) {
  const id = crypto.randomUUID(),
    path = settings.fiscalControl
  writeFileSync(path + '.tmp', JSON.stringify({ id, tenant, kind, ...fields }))
  renameSync(path + '.tmp', path)
  await expect
    .poll(
      () => {
        try {
          return JSON.parse(
            readFileSync(resolve(dirname(path), '35-result.json'), 'utf8'),
          ).id
        } catch {
          return null
        }
      },
      { timeout: 15000 },
    )
    .toBe(id)
}
export async function currentTenant(page: Page) {
  return (await (await page.request.get('/api/v1/me')).json()).memberships.find(
    (m: { commercialName: string }) =>
      m.commercialName === 'Despacho Horizonte DEMO',
  ).tenantId as string
}
export async function directory(page: Page) {
  const link = page.getByRole('link', { name: 'Contribuyentes', exact: true })
  if (!(await link.isVisible()))
    await page.getByRole('button', { name: 'Abrir menú de navegación' }).click()
  await link.click()
  await expect(
    page.getByRole('heading', { name: 'Contribuyentes', exact: true }),
  ).toBeVisible()
}
export async function close(page: Page) {
  await page.getByRole('button', { name: 'Cerrar panel', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeHidden()
}
export async function create(
  page: Page,
  rfc = 'DEM000101001',
  name = 'Servicios Profesionales de Demostración y Gestión Administrativa del Centro DEMO',
) {
  await page
    .getByRole('button', { name: 'Nuevo contribuyente', exact: true })
    .click()
  await page.getByLabel(/^RFC/).fill(rfc)
  await page.getByLabel(/^Nombre o razón social/).fill(name)
  await page
    .getByRole('button', { name: 'Agregar referencia', exact: true })
    .click()
  await page.getByLabel('Código de régimen 1').fill('000')
  const response = page.waitForResponse(
    (r) => r.request().method() === 'POST' && /\/taxpayers$/.test(r.url()),
  )
  await page
    .getByRole('button', { name: 'Registrar contribuyente', exact: true })
    .click()
  const result = await response
  expect(result.status()).toBe(201)
  await expect(
    page.getByRole('button', { name: 'Editar información', exact: true }),
  ).toBeVisible()
  return (await result.json()).targetId as string
}
export async function open(page: Page, name: string) {
  await page
    .getByRole('button', { name: `Ver información de ${name}`, exact: true })
    .click()
  await expect(
    page.getByRole('dialog', { name: 'Información administrativa' }),
  ).toBeVisible()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
}
export async function lifecycle(page: Page, archive: boolean, success = true) {
  await page
    .getByRole('button', {
      name: archive ? 'Archivar contribuyente' : 'Restaurar contribuyente',
      exact: true,
    })
    .click()
  const response = page.waitForResponse(
    (r) =>
      r.request().method() === 'POST' &&
      r.url().endsWith(archive ? '/archivals' : '/restorations'),
  )
  await page
    .getByRole('alertdialog')
    .getByRole('button', {
      name: archive ? 'Confirmar archivado' : 'Confirmar restauración',
      exact: true,
    })
    .click()
  expect((await response).status()).toBe(success ? 200 : 409)
  if (success) await expect(page.getByRole('alertdialog')).toBeHidden()
}
export async function patch(
  page: Page,
  tenant: string,
  id: string,
  version: number,
  name: string,
) {
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
  const response = await page.request.patch(
    `/api/v1/tenants/${tenant}/taxpayers/${id}`,
    {
      headers: {
        'X-CSRF-TOKEN': csrf.token,
        'Idempotency-Key': crypto.randomUUID(),
      },
      data: {
        expectedVersion: version,
        legalName: name,
        tradeName: null,
        regimes: [{ code: '000', validFrom: null, validTo: null }],
      },
    },
  )
  expect(response.status()).toBe(200)
}
export async function take(page: Page, scenario: string) {
  if (
    !['04-registro-confirmado', '14-restauracion-confirmada'].includes(
      scenario,
    ) &&
    !(await page.getByRole('dialog').count()) &&
    !(await page.getByRole('alertdialog').count())
  ) {
    const dismiss = page.getByRole('button', {
      name: 'Cerrar notificación',
      exact: true,
    })
    if (await dismiss.count()) await dismiss.click()
  }
  chromium = page.context().browser()!.version()
  for (const [width, height] of [
    [1440, 900],
    [390, 844],
  ]) {
    await page.setViewportSize({ width: width!, height: height! })
    await page.evaluate(() => document.fonts.ready)
    // Audit the settled interface; a disabled-to-enabled button may still be
    // inside its finite CSS transition after the command response arrives.
    await page.evaluate(async () => {
      await Promise.all(
        document
          .getAnimations()
          .filter((a) => a.effect?.getTiming().iterations !== Infinity)
          .map((a) => a.finished.catch(() => undefined)),
      )
    })
    const text = await page.locator('body').innerText()
    expect(
      text.includes(settings.password) || text.includes(settings.otpSecret),
    ).toBe(false)
    expect(
      (
        await new AxeBuilder({ page })
          .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
          .analyze()
      ).violations,
    ).toEqual([])
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    const file = `${scenario}-${width}.png`
    await page.screenshot({
      path: resolve(folder, file),
      animations: 'disabled',
    })
    shots.push({
      file,
      scenario,
      width,
      height,
      sha256: createHash('sha256')
        .update(readFileSync(resolve(folder, file)))
        .digest('hex'),
      axeViolations: 0,
      horizontalOverflow: false,
    })
  }
  for (const [width, height] of [
    [1280, 800],
    [768, 1024],
    [320, 844],
  ]) {
    await page.setViewportSize({ width: width!, height: height! })
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    const body = page.locator('.taxpayer-drawer .drawer-body')
    if (await body.count())
      expect(
        await body.evaluate((el) => el.scrollWidth <= el.clientWidth),
      ).toBe(true)
  }
  await page.setViewportSize({ width: 1440, height: 900 })
}
export function finish() {
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
    throw Error('Commit application and fixtures before publishing screenshots')
  writeFileSync(
    resolve(folder, 'capture-manifest.json'),
    JSON.stringify(
      {
        sourceSha: git(['rev-parse', 'HEAD']),
        applicationTree: git(['rev-parse', 'HEAD:web']),
        backendTree: git(['rev-parse', 'HEAD:backend/src/main']),
        fixtureTree: git(['rev-parse', 'HEAD:backend/src/test']),
        chromium,
        capturedAt: new Date().toISOString(),
        additionalReflowViewports: [
          { width: 1280, height: 800 },
          { width: 768, height: 1024 },
          { width: 320, height: 844 },
        ],
        source:
          'Actual React application, PostgreSQL 18, V1–V13, real Keycloak OTP; synthetic test identities and administrative commands; no mocked success/data/permissions',
        screenshots: shots,
      },
      null,
      2,
    ) + '\n',
  )
}
