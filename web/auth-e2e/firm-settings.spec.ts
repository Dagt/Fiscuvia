import { expect, test, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync, mkdirSync } from 'node:fs'
import { createHash, createHmac, randomUUID } from 'node:crypto'
import { resolve } from 'node:path'
import { validator } from '../../tools/ci/contracts.mjs'

const settings = JSON.parse(
  readFileSync(process.env.FISCUVIA_AUTH_HARNESS_FILE!, 'utf8'),
) as { password: string; otpSecret: string }
const evidence = resolve('../.ci-work/firm-browser-evidence')
mkdirSync(evidence, { recursive: true })
function otp() {
  const counter = Buffer.alloc(8)
  counter.writeBigInt64BE(BigInt(Math.floor(Date.now() / 30000)))
  const hash = createHmac('sha1', Buffer.from(settings.otpSecret, 'hex'))
    .update(counter)
    .digest()
  return String(
    (hash.readUInt32BE(hash[19]! & 15) & 0x7fffffff) % 1000000,
  ).padStart(6, '0')
}
async function login(
  page: Page,
  user = 'alice-2',
  count = user.startsWith('alice-') ? 2 : 1,
) {
  await page.goto('/login')
  await page
    .getByRole('button', { name: 'Continuar con acceso seguro' })
    .click()
  await page.locator('#username').fill(user)
  await page.locator('#password').fill(settings.password)
  await page.locator('#kc-login').click()
  await page.locator('#otp').fill(otp())
  await page.locator('#kc-login').click()
  await page.waitForURL(
    (url) => url.origin === 'https://127.0.0.1:4174' && url.pathname === '/app',
  )
  await expect
    .poll(
      async () =>
        (await (await page.request.get('/api/v1/me')).json()).memberships
          ?.length ?? -1,
    )
    .toBe(count)
  await page.reload()
}
async function choose(page: Page) {
  await page.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
  await expect(
    page.getByLabel('Nombre comercial', { exact: false }),
  ).toHaveValue('Despacho Horizonte DEMO')
}
async function own(page: Page) {
  const me = await (await page.request.get('/api/v1/me')).json()
  return me.memberships.find(
    (m: { commercialName: string }) =>
      m.commercialName === 'Despacho Horizonte DEMO',
  ).tenantId as string
}
async function put(page: Page, tenant: string, name: string, version: number) {
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
  return page.request.post(`/api/v1/tenants/${tenant}/settings`, {
    headers: { 'X-CSRF-TOKEN': csrf.token, 'Idempotency-Key': randomUUID() },
    data: {
      expectedVersion: version,
      commercialName: name,
      timeZone: 'America/Mexico_City',
      locale: 'es-MX',
    },
  })
}
function subject(username: string) {
  const bytes = createHash('md5').update(`fiscuvia-test:${username}`).digest()
  bytes[6] = (bytes[6]! & 15) | 48
  bytes[8] = (bytes[8]! & 63) | 128
  const hex = bytes.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
async function reset(page: Page, tenant: string) {
  const profile = await (
    await page.request.get(`/api/v1/tenants/${tenant}/settings`)
  ).json()
  expect(
    (
      await put(page, tenant, 'Despacho Horizonte DEMO', profile.version)
    ).status(),
  ).toBe(200)
}

test('configuración real, nombres autorizados, fallback y reflow accesible', async ({
  page,
}) => {
  await login(page, 'alice-2')
  expect(
    await page.getByRole('button', { name: /Crear despacho/ }).count(),
  ).toBe(0)
  for (const [width, height] of [
    [1440, 900],
    [1280, 800],
    [768, 1024],
    [390, 844],
    [320, 900],
  ] as const) {
    await page.setViewportSize({ width, height })
    await choose(page)
    await expect(
      page.getByRole('button', {
        name: 'Contexto actual: Despacho Horizonte DEMO',
      }),
    ).toBeVisible()
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
    await page.getByLabel('Nombre comercial', { exact: false }).focus()
    expect(
      await page.evaluate(
        () => getComputedStyle(document.activeElement!).outlineStyle,
      ),
    ).not.toBe('none')
    if (width !== 320)
      await page.screenshot({
        path: resolve(evidence, `firm-${width}.png`),
        animations: 'disabled',
      })
    await page.getByRole('button', { name: 'Cambiar de despacho' }).click()
  }
  await page
    .getByRole('button', { name: /Despacho de Servicios Profesionales/ })
    .click()
  await expect(page.getByText(/Sin logo/)).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true)
})
test('guardar y cancelar conservan el contrato real y la confirmación', async ({
  page,
}) => {
  await login(page, 'alice-3')
  const tenant = await own(page)
  await choose(page)
  try {
    const input = page.getByLabel('Nombre comercial', { exact: false })
    await input.fill('Borrador no guardado')
    await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
    await expect(input).toHaveValue('Despacho Horizonte DEMO')
    await input.fill('Despacho Horizonte actualizado DEMO')
    await page.getByRole('button', { name: 'Guardar cambios' }).click()
    await expect(page.getByRole('alertdialog')).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Cancelar', exact: true }).last(),
    ).toBeFocused()
    await page.getByRole('button', { name: 'Confirmar cambios' }).click()
    await expect(page.getByText('Datos del despacho guardados')).toBeVisible()
    const response = await page.request.get(
      `/api/v1/tenants/${tenant}/settings`,
    )
    expect(response.status()).toBe(200)
    const data = await response.json()
    expect(validator('FirmSettings')(data)).toBe(true)
    expect(data.commercialName).toBe('Despacho Horizonte actualizado DEMO')
    expect(data.version).toBeGreaterThan(0)
    await expect(
      page.getByRole('button', {
        name: 'Contexto actual: Despacho Horizonte actualizado DEMO',
      }),
    ).toBeVisible()
  } finally {
    await reset(page, tenant)
  }
})
test('CSRF y UUID de otro despacho no permiten leer ni editar configuración', async ({
  page,
  browser,
}) => {
  await login(page, 'alice-4')
  const tenant = await own(page)
  const other = await browser.newContext({
    baseURL: 'https://127.0.0.1:4174',
    ignoreHTTPSErrors: true,
  })
  try {
    const otherPage = await other.newPage()
    await login(otherPage, 'bob-2')
    expect(
      (await other.request.get(`/api/v1/tenants/${tenant}/settings`)).status(),
    ).toBe(403)
    const denied = await put(otherPage, tenant, 'Ataque DEMO', 0)
    expect(denied.status()).toBe(403)
    expect(validator('ProblemDetails')(await denied.json())).toBe(true)
    expect(
      (
        await page.request.post(`/api/v1/tenants/${tenant}/settings`, {
          headers: { 'Idempotency-Key': randomUUID() },
          data: { expectedVersion: 0, commercialName: 'Ataque' },
        })
      ).status(),
    ).toBe(403)
    const profile = await (
      await page.request.get(`/api/v1/tenants/${tenant}/settings`)
    ).json()
    expect(profile.commercialName).toBe('Despacho Horizonte DEMO')
  } finally {
    await other.close()
  }
})
test('validación visible y conflicto de versión no producen éxito falso', async ({
  page,
}) => {
  await login(page, 'alice-5')
  const tenant = await own(page)
  await choose(page)
  try {
    const input = page.getByLabel('Nombre comercial', { exact: false })
    await input.fill('')
    await page.getByRole('button', { name: 'Guardar cambios' }).click()
    await expect(page.getByRole('alertdialog')).toHaveCount(0)
    expect(
      await input.evaluate(
        (el) => (el as HTMLInputElement).validity.valueMissing,
      ),
    ).toBe(true)
    await input.fill('Borrador obsoleto DEMO')
    const original = await (
      await page.request.get(`/api/v1/tenants/${tenant}/settings`)
    ).json()
    expect(
      (
        await put(page, tenant, 'Actualización paralela DEMO', original.version)
      ).status(),
    ).toBe(200)
    await page.getByRole('button', { name: 'Guardar cambios' }).click()
    await page.getByRole('button', { name: 'Confirmar cambios' }).click()
    await expect(page.getByText(/La información cambió/)).toBeVisible()
    expect(await page.getByText('Datos del despacho guardados').count()).toBe(0)
    expect(
      (
        await (
          await page.request.get(`/api/v1/tenants/${tenant}/settings`)
        ).json()
      ).commercialName,
    ).toBe('Actualización paralela DEMO')
  } finally {
    await reset(page, tenant)
  }
})
test('carga y error recuperable retiran datos y reintentan contra el backend', async ({
  page,
}) => {
  await login(page, 'alice-6')
  let release!: () => void
  const waiting = new Promise<void>((resolve) => {
    release = resolve
  })
  let reached!: () => void
  const entered = new Promise<void>((resolve) => {
    reached = resolve
  })
  await page.route('**/api/v1/tenants/*/settings', async (route) => {
    reached()
    await waiting
    await route.abort('failed')
  })
  await page.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
  await entered
  await expect(page.getByText('Consultando tu despacho')).toBeVisible()
  await page.screenshot({ path: resolve(evidence, 'loading-1440.png') })
  release()
  await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible()
  await page.screenshot({ path: resolve(evidence, 'error-1440.png') })
  await page.unroute('**/api/v1/tenants/*/settings')
  await page.getByRole('button', { name: 'Reintentar' }).click()
  await expect(
    page.getByLabel('Nombre comercial', { exact: false }),
  ).toHaveValue('Despacho Horizonte DEMO')
})

test('revocación confirmada durante carga retira nombre, configuración y contexto', async ({
  page,
  browser,
}) => {
  await login(page, 'alice-7')
  const tenant = await own(page)
  const recipient = await browser.newContext({
    baseURL: 'https://127.0.0.1:4174',
    ignoreHTTPSErrors: true,
  })
  try {
    const target = await recipient.newPage()
    await login(target, 'carol-5', 0)
    const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
    const issued = await page.request.post('/api/v1/auth/invitations', {
      headers: { 'X-CSRF-TOKEN': csrf.token },
      data: {
        tenantId: tenant,
        subject: subject('carol-5'),
        role: 'ACCOUNTANT',
      },
    })
    expect(issued.status()).toBe(201)
    const invitation = await issued.json()
    const recipientCsrf = await (
      await target.request.get('/api/v1/auth/csrf')
    ).json()
    expect(
      (
        await target.request.post('/api/v1/auth/invitations/accept', {
          headers: { 'X-CSRF-TOKEN': recipientCsrf.token },
          data: { token: invitation.token },
        })
      ).status(),
    ).toBe(200)
    const member = (await (await target.request.get('/api/v1/me')).json())
      .memberships[0]
    await target.reload()
    let release!: () => void
    const waiting = new Promise<void>((resolve) => {
      release = resolve
    })
    let reached!: () => void
    const entered = new Promise<void>((resolve) => {
      reached = resolve
    })
    await target.route('**/api/v1/tenants/*/settings', async (route) => {
      reached()
      await waiting
      await route.continue()
    })
    await target
      .getByRole('button', { name: /Despacho Horizonte DEMO/ })
      .click()
    await entered
    expect(
      (
        await page.request.post('/api/v1/auth/memberships/status', {
          headers: { 'X-CSRF-TOKEN': csrf.token },
          data: {
            tenantId: tenant,
            membershipId: member.membershipId,
            status: 'REVOKED',
          },
        })
      ).status(),
    ).toBe(204)
    release()
    await expect(
      target.getByText('Necesitas permisos para continuar'),
    ).toBeVisible()
    await expect(target.getByText('Sin despacho seleccionado')).toBeVisible()
    expect(
      await target.getByLabel('Nombre comercial', { exact: false }).count(),
    ).toBe(0)
    expect(
      (await target.request.get(`/api/v1/tenants/${tenant}/settings`)).status(),
    ).toBe(403)
  } finally {
    await recipient.close()
  }
})
test('suspensión administrativa real no ofrece acceso operativo ni reactivación', async ({
  page,
}) => {
  await login(page, 'bob-3')
  await expect
    .poll(
      async () =>
        (await (await page.request.get('/api/v1/me')).json()).memberships[0]
          ?.tenantStatus,
    )
    .toBe('SUSPENDED')
  await page.reload()
  await expect(
    page.getByRole('button', { name: /Despacho suspendido DEMO/ }),
  ).toBeDisabled()
  expect(
    await page
      .getByRole('button', { name: /Reactivar|Crear despacho/ })
      .count(),
  ).toBe(0)
  const tenant = (await (await page.request.get('/api/v1/me')).json())
    .memberships[0].tenantId
  expect(
    (await page.request.get(`/api/v1/tenants/${tenant}/settings`)).status(),
  ).toBe(403)
})
