import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { validator } from '../../tools/ci/contracts.mjs'
import { login, owner, api, take, finishCaptures } from './team-harness'
test.afterAll(finishCaptures)
async function currentTenant(page: Page) {
  return (await (await page.request.get('/api/v1/me')).json()).memberships.find(
    (m: { commercialName: string }) =>
      m.commercialName === 'Despacho Horizonte DEMO',
  ).tenantId as string
}
async function invite(
  page: Page,
  tenant: string,
  email: string,
  role = 'ACCOUNTANT',
) {
  const response = await api(
    page,
    `/tenants/${tenant}/team/invitations`,
    { email, role },
    { 'Idempotency-Key': crypto.randomUUID() },
  )
  expect(response.status()).toBe(201)
  return (await response.json()) as { token: string; invitationId: string }
}
async function accept(page: Page, token: string) {
  expect(
    (await api(page, '/auth/invitations/accept', { token })).status(),
  ).toBe(200)
}
async function confirm(page: Page, label: string) {
  const dialog = page.getByRole('alertdialog')
  await dialog.getByRole('button', { name: label, exact: true }).click()
}
async function desktop(page: Page) {
  await page.setViewportSize({ width: 1440, height: 900 })
}
const memberRow = (page: Page, email: string) =>
  page.getByRole('row').filter({ hasText: email })
async function openMember(page: Page, email: string) {
  await desktop(page)
  await memberRow(page, email)
    .getByRole('button', { name: 'Administrar', exact: true })
    .click()
  await page.getByRole('dialog', { name: 'Administrar colaborador' }).waitFor()
}
async function command(page: Page, label: string, suffix: string) {
  const done = page.waitForResponse(
    (r) => r.request().method() === 'POST' && r.url().endsWith(suffix),
  )
  await page
    .getByRole('dialog', { name: 'Administrar colaborador' })
    .getByRole('button', { name: label, exact: true })
    .click()
  await confirm(page, label)
  const r = await done
  expect(r.status()).toBe(200)
  expect(validator('TeamCommandResult')(await r.json())).toBe(true)
  await expect(
    page.getByRole('dialog', { name: 'Administrar colaborador' }),
  ).toBeHidden()
}
test('directorio real, rol, suspensión, reactivación, revocación y cambio de despacho', async ({
  page,
  browser,
}) => {
  // Three OIDC sessions, six actual transitions and 12 axe-backed screenshots. No retries.
  test.setTimeout(180000)
  const contexts = await Promise.all([
    browser.newContext({
      baseURL: 'https://127.0.0.1:4174',
      ignoreHTTPSErrors: true,
    }),
    browser.newContext({
      baseURL: 'https://127.0.0.1:4174',
      ignoreHTTPSErrors: true,
    }),
  ])
  try {
    const bob = await contexts[0]!.newPage(),
      carol = await contexts[1]!.newPage()
    await login(bob, 'bob-22')
    await login(carol, 'carol-22')
    await owner(page, 'alice-14')
    const t = await currentTenant(page)
    await accept(bob, (await invite(page, t, 'bob-22@example.invalid')).token)
    await accept(
      carol,
      (await invite(page, t, 'carol-22@example.invalid', 'ASSISTANT')).token,
    )
    await page.getByRole('button', { name: 'Equipo', exact: true }).click()
    await expect(memberRow(page, 'bob-22@example.invalid')).toBeVisible()
    const list = await (
      await page.request.get(`/api/v1/tenants/${t}/team/members`)
    ).json()
    expect(validator('TeamMembersPage')(list)).toBe(true)
    expect(list.total).toBe(3)
    const member = list.items.find(
      (m: { email: string }) => m.email === 'bob-22@example.invalid',
    ).membershipId
    await take(page, 'equipo-colaboradores')
    for (const width of [1280, 768, 320]) {
      await page.setViewportSize({ width, height: width === 768 ? 1024 : 900 })
      expect(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
      ).toBe(true)
      expect(
        (
          await new AxeBuilder({ page })
            .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
            .analyze()
        ).violations,
      ).toHaveLength(0)
    }
    await openMember(page, 'bob-22@example.invalid')
    await page
      .getByLabel('Nuevo rol de bob-22@example.invalid')
      .selectOption('FIRM_SUPERVISOR')
    await take(page, 'equipo-cambio-rol')
    await page
      .getByRole('dialog', { name: 'Administrar colaborador' })
      .getByRole('button', { name: 'Cambiar rol', exact: true })
      .click()
    await take(page, 'equipo-confirmacion')
    const changed = page.waitForResponse(
      (r) =>
        r.request().method() === 'POST' &&
        r.url().endsWith(`/members/${member}/role`),
    )
    await confirm(page, 'Cambiar rol')
    expect((await changed).status()).toBe(200)
    await desktop(page)
    await expect(
      memberRow(page, 'bob-22@example.invalid').getByText('Supervisor', {
        exact: true,
      }),
    ).toBeVisible()
    expect(
      (await (await bob.request.get('/api/v1/me')).json()).memberships[0].role,
    ).toBe('FIRM_SUPERVISOR')
    await openMember(page, 'bob-22@example.invalid')
    await command(page, 'Suspender', `/members/${member}/status`)
    await expect(
      memberRow(page, 'bob-22@example.invalid').getByText('Suspendida', {
        exact: true,
      }),
    ).toBeVisible()
    expect((await api(bob, '/auth/tenant', { tenantId: t })).status()).toBe(403)
    await take(page, 'equipo-suspendido')
    await openMember(page, 'bob-22@example.invalid')
    await command(page, 'Reactivar', `/members/${member}/status`)
    await expect(
      memberRow(page, 'bob-22@example.invalid').getByText('Activa', {
        exact: true,
      }),
    ).toBeVisible()
    expect((await api(bob, '/auth/tenant', { tenantId: t })).status()).toBe(200)
    await openMember(page, 'bob-22@example.invalid')
    await command(page, 'Revocar acceso', `/members/${member}/status`)
    await expect(
      memberRow(page, 'bob-22@example.invalid').getByText('Acceso revocado', {
        exact: true,
      }),
    ).toBeVisible()
    await take(page, 'equipo-revocado')
    await page
      .getByRole('button', { name: 'Cambiar de despacho', exact: true })
      .click()
    await page
      .getByRole('button', { name: /Despacho de Servicios Profesionales/ })
      .click()
    await page.getByLabel('Nombre comercial', { exact: false }).waitFor()
    await page.getByRole('button', { name: 'Equipo', exact: true }).click()
    await expect(
      page.getByText('Propietario protegido', { exact: true }),
    ).toBeVisible()
    await expect(
      page.getByText('bob-22@example.invalid', { exact: true }),
    ).toHaveCount(0)
    const selected = (
      await (await page.request.get('/api/v1/me')).json()
    ).memberships.find((m: { tenantId: string }) => m.tenantId !== t).tenantId
    const forbidden = await api(
      page,
      `/tenants/${selected}/team/members/${member}/role`,
      { role: 'ASSISTANT', expectedVersion: 3 },
      { 'Idempotency-Key': crypto.randomUUID() },
    )
    expect(forbidden.status()).toBe(404)
    expect((await forbidden.text()).includes('bob-22@example.invalid')).toBe(
      false,
    )
    await take(page, 'equipo-otro-despacho')
  } finally {
    await Promise.all(contexts.map((c) => c.close()))
  }
})
test('cuota agotada real, invitaciones paginadas, revocación, vacío y error recuperable', async ({
  page,
  browser,
}) => {
  // Four actual OIDC identities and quota admission, no business response mocks.
  test.setTimeout(180000)
  const contexts = await Promise.all(
    [0, 1, 2].map(() =>
      browser.newContext({
        baseURL: 'https://127.0.0.1:4174',
        ignoreHTTPSErrors: true,
      }),
    ),
  )
  try {
    const bob = await contexts[0]!.newPage(),
      carol = await contexts[1]!.newPage(),
      pending = await contexts[2]!.newPage()
    await login(bob, 'bob-23')
    await login(carol, 'carol-23')
    await login(pending, 'bob-24')
    await owner(page, 'alice-15')
    const t = await currentTenant(page)
    await accept(bob, (await invite(page, t, 'bob-23@example.invalid')).token)
    let list = await (
      await page.request.get(`/api/v1/tenants/${t}/team/members`)
    ).json()
    const m = list.items.find(
      (r: { email: string }) => r.email === 'bob-23@example.invalid',
    ).membershipId
    await page.getByRole('button', { name: 'Equipo', exact: true }).click()
    await openMember(page, 'bob-23@example.invalid')
    await command(page, 'Suspender', `/members/${m}/status`)
    await accept(
      carol,
      (await invite(page, t, 'carol-23@example.invalid')).token,
    )
    await page
      .getByRole('button', { name: 'Actualizar lista', exact: true })
      .click()
    await openMember(page, 'bob-23@example.invalid')
    const result = page.waitForResponse(
      (r) =>
        r.request().method() === 'POST' &&
        r.url().endsWith(`/members/${m}/status`),
    )
    await page
      .getByRole('dialog', { name: 'Administrar colaborador' })
      .getByRole('button', { name: 'Reactivar', exact: true })
      .click()
    await confirm(page, 'Reactivar')
    expect((await result).status()).toBe(409)
    await expect(
      page.getByRole('alertdialog').getByText(/No hay cupo disponible/),
    ).toBeVisible()
    await take(page, 'equipo-sin-cupo')
    await page
      .getByRole('alertdialog')
      .getByRole('button', { name: 'Cancelar', exact: true })
      .click()
    await page
      .getByRole('button', { name: 'Cerrar panel', exact: true })
      .click()
    expect((await api(bob, '/auth/tenant', { tenantId: t })).status()).toBe(403)
    const invitation = await invite(page, t, 'bob-24@example.invalid')
    await page
      .getByRole('button', { name: 'Invitaciones', exact: true })
      .click()
    await desktop(page)
    await expect(memberRow(page, 'bob-24@example.invalid')).toBeVisible()
    const response = await (
      await page.request.get(
        `/api/v1/tenants/${t}/team/invitations?page=0&size=1&status=PENDING`,
      )
    ).json()
    expect(validator('TeamInvitationsPage')(response)).toBe(true)
    expect(response.total).toBe(1)
    await take(page, 'equipo-invitaciones')
    await desktop(page)
    await memberRow(page, 'bob-24@example.invalid')
      .getByRole('button', { name: 'Revocar invitación', exact: true })
      .click()
    const revoked = page.waitForResponse(
      (r) =>
        r.request().method() === 'POST' &&
        r.url().endsWith(`/invitations/${invitation.invitationId}/revoke`),
    )
    await confirm(page, 'Revocar invitación')
    expect((await revoked).status()).toBe(200)
    expect(
      (
        await api(pending, '/auth/invitations/accept', {
          token: invitation.token,
        })
      ).status(),
    ).toBe(403)
    await page.getByLabel('Estado', { exact: true }).selectOption('PENDING')
    await expect(
      page.getByText('No hay resultados', { exact: true }),
    ).toBeVisible()
    await take(page, 'equipo-vacio')
    await page.route('**/team/members?**', (route) => route.abort(), {
      times: 1,
    })
    await page
      .getByRole('button', { name: 'Colaboradores', exact: true })
      .click()
    await expect(page.getByRole('button', { name: /Reintentar/ })).toBeVisible()
    await take(page, 'equipo-error-recuperable')
    await page.getByRole('button', { name: /Reintentar/ }).click()
    await desktop(page)
    await expect(memberRow(page, 'carol-23@example.invalid')).toBeVisible()
    list = await (
      await page.request.get(`/api/v1/tenants/${t}/team/members`)
    ).json()
    expect(
      list.items.find((r: { membershipId: string }) => r.membershipId === m)
        .status,
    ).toBe('SUSPENDED')
  } finally {
    await Promise.all(contexts.map((c) => c.close()))
  }
})
