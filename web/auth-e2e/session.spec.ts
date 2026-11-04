import { expect, test, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync, mkdirSync } from 'node:fs'
import { createHash, createHmac } from 'node:crypto'
import { validator } from '../../tools/ci/contracts.mjs'
import { resolve } from 'node:path'

const settings = JSON.parse(
  readFileSync(process.env.FISCUVIA_AUTH_HARNESS_FILE!, 'utf8'),
) as { password: string; otpSecret: string }
const evidence = resolve('../.ci-work/auth-browser-evidence')
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
function conforms(name: string, value: unknown) {
  const check = validator(name)
  expect(check(value), `Respuesta HTTP conforme a ${name}`).toBe(true)
}
function subject(username: string) {
  const value = createHash('md5').update(`fiscuvia-test:${username}`).digest()
  value[6] = (value[6]! & 15) | 48
  value[8] = (value[8]! & 63) | 128
  const hex = value.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
async function login(page: Page, username: string) {
  await page.goto('/login')
  await page
    .getByRole('button', { name: 'Continuar con acceso seguro' })
    .click()
  await completeProviderLogin(page, username)
}
async function completeProviderLogin(page: Page, username: string) {
  await page.locator('#username').fill(username)
  await page.locator('#password').fill(settings.password)
  await page.locator('#kc-login').click()
  await expect(page.locator('#otp')).toBeVisible()
  await page.locator('#otp').fill(otp())
  await page.locator('#kc-login').click()
}

test('AppShell autenticado, selector y navegación móvil con OIDC real', async ({
  page,
}) => {
  await login(page, 'alice-1')
  await expect
    .poll(
      async () =>
        (await (await page.request.get('/api/v1/me')).json()).memberships
          .length,
    )
    .toBe(2)
  await page.reload()
  for (const [width, height] of [
    [1440, 900],
    [390, 844],
  ] as const) {
    await page.setViewportSize({ width, height })
    await expect(page.locator('main')).toHaveCount(1)
    await expect(page.locator('h1')).toHaveCount(1)
    await page.getByRole('button', { name: /Despacho Norte DEMO/ }).click()
    await expect(
      page.getByRole('heading', { name: 'Datos del despacho' }),
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
    if (width === 390) {
      const trigger = page.getByRole('button', {
        name: 'Abrir menú de navegación',
      })
      await trigger.click()
      await expect(
        page.getByRole('dialog', { name: 'Navegación de Fiscuvia' }),
      ).toBeVisible()
      await expect(
        page.getByRole('button', { name: 'Contribuyentes', exact: true }),
      ).toBeDisabled()
      await page.keyboard.press('Escape')
      await expect(trigger).toBeFocused()
    }
    await page
      .getByRole('button', {
        name: 'Contexto actual: Despacho Norte DEMO',
        exact: true,
      })
      .click()
    await expect(
      page.getByRole('heading', { name: 'Elige tu despacho' }),
    ).toBeVisible()
    await page.getByRole('button', { name: /Despacho Sur DEMO/ }).click()
    await expect(
      page.getByRole('button', {
        name: 'Contexto actual: Despacho Sur DEMO',
        exact: true,
      }),
    ).toBeVisible()
    await page
      .getByRole('button', { name: 'Cambiar de despacho', exact: true })
      .click()
  }
  expect(
    await page.evaluate(() => ({
      local: localStorage.length,
      session: sessionStorage.length,
    })),
  ).toEqual({ local: 0, session: 0 })
})
test('acceso navy/teal responsive y accesible con emisor real disponible', async ({
  page,
}) => {
  for (const [width, height] of [
    [1440, 900],
    [390, 844],
  ]) {
    await page.setViewportSize({ width, height })
    await page.goto('/login')
    await expect(
      page.getByRole('button', { name: 'Continuar con acceso seguro' }),
    ).toBeEnabled()
    await expect(page.locator('body')).toHaveCSS(
      'font-family',
      /Inter Variable/,
    )
    await expect(page.locator('.auth-brand-title')).toBeVisible()
    await expect(page.locator('.auth-brand-title')).toHaveCSS(
      'font-size',
      width === 390 ? '29px' : '46px',
    )
    await expect(page.locator('.auth-brand-title')).toHaveCSS(
      'font-weight',
      '600',
    )
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true)
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
    await page.keyboard.press('Tab')
    await page.keyboard.press('Tab')
    expect(
      await page.evaluate(
        () => getComputedStyle(document.activeElement!).outlineStyle,
      ),
    ).not.toBe('none')
    await page.evaluate(() => document.fonts.ready)
    await page.screenshot({
      path: resolve(evidence, `login-${width}.png`),
      animations: 'disabled',
    })
  }
})
test('login OIDC del navegador, sin membresías, cookies seguras y logout real', async ({
  page,
  context,
}) => {
  await login(page, 'carol-1')
  await expect(
    page.getByText('Aún no tienes despachos asignados'),
  ).toBeVisible()
  const cookie = (await context.cookies()).find(
    (cookie) => cookie.name === '__Host-FiscuviaSession',
  )
  expect(cookie).toMatchObject({
    httpOnly: true,
    secure: true,
    sameSite: 'Lax',
    path: '/',
    domain: '127.0.0.1',
  })
  expect(
    await page.evaluate(() => ({
      local: localStorage.length,
      session: sessionStorage.length,
    })),
  ).toEqual({ local: 0, session: 0 })
  await page.screenshot({
    path: resolve(evidence, 'sin-membresias-1440.png'),
    animations: 'disabled',
  })
  await page.getByRole('button', { name: 'Cerrar sesión' }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect((await context.request.get('/api/v1/me')).status()).toBe(401)
  await page.goto('/app')
  await expect(
    page.getByText('Tu sesión terminó.', { exact: false }),
  ).toBeVisible()
})
test('selección autorizada, invitaciones y contratos HTTP reales sin datos fiscales', async ({
  page,
  context,
  browser,
}) => {
  await login(page, 'alice-8')
  await expect
    .poll(
      async () =>
        (await (await context.request.get('/api/v1/me')).json()).memberships
          .length,
    )
    .toBe(2)
  await page.reload()
  await page.getByRole('button', { name: /Despacho Norte DEMO/ }).click()
  await expect(
    page.getByRole('heading', { name: 'Datos del despacho' }),
  ).toBeVisible()
  await expect(
    page.getByText('La carga de logos aún no está disponible.'),
  ).toBeVisible()
  await page.screenshot({
    path: resolve(evidence, 'despacho-autorizado-1440.png'),
    animations: 'disabled',
  })
  await page.getByRole('button', { name: 'Cambiar de despacho' }).click()
  await page.getByRole('button', { name: /Despacho Sur DEMO/ }).click()
  await expect(
    page.getByRole('heading', { name: 'Datos del despacho' }),
  ).toBeVisible()
  const second = await context.newPage()
  await second.goto('/app')
  await expect(
    second.getByRole('button', { name: /Despacho Norte DEMO/ }),
  ).toBeVisible()
  await second.close()
  const me = await (await context.request.get('/api/v1/me')).json()
  conforms('CurrentIdentity', me)
  conforms(
    'AuthStatus',
    await (await context.request.get('/api/v1/auth/status')).json(),
  )
  const csrf = await (await context.request.get('/api/v1/auth/csrf')).json()
  conforms('CsrfToken', csrf)
  const tenantId = me.memberships[0].tenantId
  const headers = { 'X-CSRF-TOKEN': csrf.token }
  const authorized = await context.request.post('/api/v1/auth/tenant', {
    headers,
    data: { tenantId },
  })
  expect(authorized.status()).toBe(200)
  conforms('Membership', await authorized.json())
  const recipient = await browser.newContext({
    baseURL: 'https://127.0.0.1:4174',
    ignoreHTTPSErrors: true,
  })
  try {
    const recipientPage = await recipient.newPage()
    await login(recipientPage, 'carol-3')
    await expect(
      recipientPage.getByText('Aún no tienes despachos asignados'),
    ).toBeVisible()
    const issued = await context.request.post('/api/v1/auth/invitations', {
      headers,
      data: { tenantId, subject: subject('carol-3'), role: 'ACCOUNTANT' },
    })
    expect(issued.status()).toBe(201)
    const invitation = await issued.json()
    conforms('Invitation', invitation)
    const recipientCsrf = await (
      await recipient.request.get('/api/v1/auth/csrf')
    ).json()
    const accepted = await recipient.request.post(
      '/api/v1/auth/invitations/accept',
      {
        headers: { 'X-CSRF-TOKEN': recipientCsrf.token },
        data: { token: invitation.token },
      },
    )
    expect(accepted.status()).toBe(200)
    conforms('AcceptedInvitation', await accepted.json())
    const member = (await (await recipient.request.get('/api/v1/me')).json())
      .memberships[0]
    expect(
      (
        await context.request.post('/api/v1/auth/memberships/status', {
          headers,
          data: {
            tenantId,
            membershipId: member.membershipId,
            status: 'REVOKED',
          },
        })
      ).status(),
    ).toBe(204)
    const denied = await recipient.request.post('/api/v1/auth/tenant', {
      headers: { 'X-CSRF-TOKEN': recipientCsrf.token },
      data: { tenantId },
    })
    expect(denied.status()).toBe(403)
    conforms('ProblemDetails', await denied.json())
    const next = await (
      await context.request.post('/api/v1/auth/invitations', {
        headers,
        data: { tenantId, subject: subject('carol-4'), role: 'ASSISTANT' },
      })
    ).json()
    expect(
      (
        await context.request.post('/api/v1/auth/invitations/revoke', {
          headers,
          data: { tenantId, invitationId: next.invitationId },
        })
      ).status(),
    ).toBe(204)
    console.log(
      'OpenAPI de identidad: 8 schemas verificados sobre respuestas HTTP reales; tokens sólo en memoria.',
    )
  } finally {
    await recipient.close()
  }
})
test('state alterado produce error real seguro, responsive y recuperable', async ({
  page,
}) => {
  await page.goto('/login')
  // Wait for the page's initial CSRF session before issuing API requests through
  // its shared cookie jar. Two anonymous CSRF requests can create different
  // sessions; a late Set-Cookie must not replace the POST-approved login session.
  await expect(
    page.getByRole('button', { name: 'Continuar con acceso seguro' }),
  ).toBeVisible()
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json()
  const start = await page.request.post('/api/v1/auth/login', {
    headers: { 'X-CSRF-TOKEN': csrf.token },
    maxRedirects: 0,
  })
  expect(start.status()).toBe(303)
  const authorization = await page.request.get(start.headers().location, {
    maxRedirects: 0,
  })
  expect(authorization.status()).toBe(302)
  const manipulated = new URL(authorization.headers().location)
  manipulated.searchParams.set('state', 'untrusted-state')
  await page.goto(manipulated.toString())
  await completeProviderLogin(page, 'carol-2')
  await expect(
    page.getByRole('heading', { name: 'No pudimos verificar tu acceso' }),
  ).toBeVisible()
  for (const [width, height] of [
    [1440, 900],
    [390, 844],
  ]) {
    await page.setViewportSize({ width, height })
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true)
    await page.screenshot({
      path: resolve(evidence, `error-${width}.png`),
      animations: 'disabled',
    })
  }
})
test('CSRF inválido y UUID de otro despacho no otorgan acceso', async ({
  page,
  context,
  browser,
}) => {
  await login(page, 'bob-1')
  await expect
    .poll(
      async () =>
        (await (await context.request.get('/api/v1/me')).json()).memberships
          .length,
    )
    .toBe(1)
  // The explicitly approved internal fixture changes membership after the initial /me query.
  // Refresh the real UI after observing that commit; never rely on a stale query-cache race.
  await page.reload()
  const other = await browser.newContext({
    baseURL: 'https://127.0.0.1:4174',
    ignoreHTTPSErrors: true,
  })
  try {
    expect(
      (
        await other.request.post('/api/v1/auth/tenant', {
          data: { tenantId: '00000000-0000-4000-8000-000000000001' },
        })
      ).status(),
    ).toBe(403)
    const csrf = await (await context.request.get('/api/v1/auth/csrf')).json()
    expect(
      (
        await context.request.post('/api/v1/auth/tenant', {
          data: { tenantId: '00000000-0000-4000-8000-000000000001' },
          headers: { 'X-CSRF-TOKEN': csrf.token },
        })
      ).status(),
    ).toBe(403)
    await expect(
      page.getByRole('button', { name: /Despacho Centro DEMO/ }),
    ).toBeVisible()
  } finally {
    await other.close()
  }
})
