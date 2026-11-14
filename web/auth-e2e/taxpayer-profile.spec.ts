import { test, expect, type Page } from '@playwright/test'
import {
  owner,
  login,
  api,
  directory,
  currentTenant,
  fixture,
  create,
  patch,
  lifecycle,
} from './taxpayer-harness'
import { take, finish } from './taxpayer-profile-harness'

test.afterAll(finish)
async function full(page: Page) {
  await page
    .getByRole('link', { name: 'Abrir ficha completa', exact: true })
    .click()
  await expect(
    page.getByRole('article', { name: 'Ficha del contribuyente' }),
  ).toBeVisible()
}
test('ficha real: navegación, perfil, referencias, conflicto, edición, archivo, restauración y transporte recuperable', async ({
  page,
}) => {
  test.setTimeout(180000)
  await owner(page, 'alice-20')
  const tenant = await currentTenant(page)
  await directory(page)
  const name =
    'Servicios de Administración y Asesoría del Contribuyente de Demostración del Centro DEMO'
  const id = await create(page, 'DEM000101301', name)
  await full(page)
  await expect(page).toHaveURL(new RegExp(`/app/taxpayers/${id}$`))
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
  await take(page, '01-ficha-resumen')
  await page
    .getByRole('tab', { name: 'Información y contactos', exact: true })
    .click()
  await expect(
    page.getByText(
      'La consulta y gestión de contactos aún no están habilitadas.',
      { exact: false },
    ),
  ).toBeVisible()
  for (const label of [
    'Responsables y accesos',
    'Certificados y alertas',
    'Actividad e historial',
  ])
    await expect(
      page.getByRole('tab', { name: new RegExp(label) }),
    ).toBeDisabled()
  await take(page, '02-informacion-alcance-pendiente')
  await page
    .getByRole('button', { name: 'Editar información', exact: true })
    .click()
  await page
    .getByLabel('Nombre comercial', { exact: true })
    .fill('Edición pendiente DEMO')
  await take(page, '03-formulario-administrativo')
  await patch(page, tenant, id, 0, 'Actualización concurrente de la ficha DEMO')
  await page
    .getByRole('button', { name: 'Guardar cambios', exact: true })
    .click()
  await expect(page.getByText(/La información cambió/)).toBeVisible()
  await take(page, '04-conflicto-version')
  await page
    .getByRole('button', { name: 'Actualizar datos para revisar' })
    .click()
  await expect(
    page.getByRole('heading', {
      level: 1,
      name: 'Actualización concurrente de la ficha DEMO',
    }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: 'Editar información', exact: true })
    .click()
  await page.getByLabel(/^Nombre o razón social/).fill(name)
  await page
    .getByLabel('Nombre comercial', { exact: true })
    .fill('Centro Administrativo DEMO')
  await page
    .getByRole('button', { name: 'Guardar cambios', exact: true })
    .click()
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
  await expect(
    page.getByText('Centro Administrativo DEMO', { exact: true }).first(),
  ).toBeVisible()
  await take(page, '05-edicion-confirmada')
  await page
    .getByRole('button', { name: 'Archivar contribuyente', exact: true })
    .click()
  await take(page, '06-confirmacion-archivado')
  await page
    .getByRole('button', { name: 'Confirmar archivado', exact: true })
    .click()
  await expect(
    page.getByRole('button', { name: 'Restaurar contribuyente', exact: true }),
  ).toBeVisible()
  await take(page, '07-ficha-archivada')
  await lifecycle(page, false)
  await expect(
    page.getByRole('button', { name: 'Archivar contribuyente', exact: true }),
  ).toBeVisible()
  // Abort actual transport, never fabricate a response or a successful backend command.
  await page.route(`**/tenants/${tenant}/taxpayers/${id}`, (route) =>
    route.abort('failed'),
  )
  await page.reload()
  await page.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
  await expect(
    page.getByRole('button', { name: 'Reintentar', exact: true }),
  ).toBeVisible()
  await take(page, '08-error-recuperable')
  await page.unroute(`**/tenants/${tenant}/taxpayers/${id}`)
  await page.getByRole('button', { name: 'Reintentar', exact: true }).click()
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible()
  await page
    .getByRole('button', { name: 'Editar información', exact: true })
    .click()
  await page
    .getByRole('button', { name: 'Eliminar referencia 1', exact: true })
    .click()
  await page
    .getByRole('button', { name: 'Guardar cambios', exact: true })
    .click()
  await expect(
    page.getByText('No hay referencias manuales registradas.', { exact: true }),
  ).toBeVisible()
  await take(page, '11-referencias-vacias')
  await page.getByRole('link', { name: 'Volver a contribuyentes' }).click()
  await expect(page).toHaveURL(/\/app\/taxpayers$/)
  await expect(
    page.getByRole('heading', {
      level: 1,
      name: 'Contribuyentes',
      exact: true,
    }),
  ).toBeVisible()
  await expect(page.getByText(name, { exact: true })).toBeVisible()
})

test('ficha aislada: grants exactos, contador, supervisor, auxiliar, suspensión y contexto tardío', async ({
  page,
  browser,
}) => {
  test.setTimeout(180000)
  await owner(page, 'alice-20')
  const tenant = await currentTenant(page)
  await directory(page)
  const id = await create(page, 'DEM000101302', 'Expediente privado A DEMO')
  await full(page)
  const contexts = []
  try {
    for (const [identity, role] of [
      ['bob-25', 'ACCOUNTANT'],
      ['carol-25', 'FIRM_SUPERVISOR'],
      ['bob-26', 'ASSISTANT'],
    ] as const) {
      const context = await browser.newContext({
        baseURL: 'https://127.0.0.1:4174',
        ignoreHTTPSErrors: true,
      })
      contexts.push(context)
      const tab = await context.newPage()
      await login(tab, identity)
      const invitation = await api(
        page,
        `/tenants/${tenant}/team/invitations`,
        { email: `${identity}@example.invalid`, role },
        { 'Idempotency-Key': crypto.randomUUID() },
      )
      expect(invitation.status()).toBe(201)
      expect(
        (
          await api(tab, '/auth/invitations/accept', {
            token: (await invitation.json()).token,
          })
        ).status(),
      ).toBe(200)
      await tab.goto(`/app/taxpayers/${id}`)
      await tab.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
      await expect(
        tab.getByText('Expediente no disponible', { exact: true }),
      ).toBeVisible()
      expect(
        await tab
          .getByText('Expediente privado A DEMO', { exact: true })
          .count(),
      ).toBe(0)
      if (role === 'ASSISTANT') {
        await take(tab, '09-sin-permiso')
        continue
      }
      const members = await (
        await page.request.get(`/api/v1/tenants/${tenant}/team/members`)
      ).json()
      const member = members.items.find(
        (m: { email: string }) => m.email === `${identity}@example.invalid`,
      )
      await fixture(tenant, 'grant', {
        member: member.membershipId,
        taxpayer: id,
      })
      await tab.reload()
      await tab.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
      await expect(
        tab.getByRole('heading', {
          level: 1,
          name: 'Expediente privado A DEMO',
        }),
      ).toBeVisible()
      await expect(
        tab.getByRole('button', { name: 'Editar información', exact: true }),
      ).toBeVisible()
      await expect(
        tab.getByRole('button', {
          name: 'Archivar contribuyente',
          exact: true,
        }),
      ).toHaveCount(0)
      if (role === 'ACCOUNTANT') {
        await take(tab, '10-contador-con-grant')
        for (const [expectedVersion, status] of [
          [0, 'SUSPENDED'],
          [1, 'ACTIVE'],
        ] as const)
          expect(
            (
              await api(
                page,
                `/tenants/${tenant}/team/members/${member.membershipId}/status`,
                { expectedVersion, status },
                { 'Idempotency-Key': crypto.randomUUID() },
              )
            ).status(),
          ).toBe(200)
      } else {
        expect(
          (
            await api(
              page,
              `/tenants/${tenant}/team/members/${member.membershipId}/role`,
              { expectedVersion: 0, role: 'ACCOUNTANT' },
              { 'Idempotency-Key': crypto.randomUUID() },
            )
          ).status(),
        ).toBe(200)
      }
      await tab.reload()
      await tab.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
      await expect(
        tab.getByText('Expediente no disponible', { exact: true }),
      ).toBeVisible()
    }
    let release!: () => void, fetched!: () => void
    const arrived = new Promise<void>((r) => {
        fetched = r
      }),
      hold = new Promise<void>((r) => {
        release = r
      })
    await page.getByRole('link', { name: 'Volver a contribuyentes' }).click()
    await page
      .getByRole('button', {
        name: 'Ver información de Expediente privado A DEMO',
        exact: true,
      })
      .click()
    await expect(
      page.getByRole('link', { name: 'Abrir ficha completa', exact: true }),
    ).toBeVisible()
    await page.route(
      `**/tenants/${tenant}/taxpayers/${id}`,
      async (route) => {
        const response = await route.fetch()
        fetched()
        await hold
        try {
          await route.fulfill({ response })
        } catch {
          /* context cancellation */
        }
      },
      { times: 1 },
    )
    await page
      .getByRole('link', { name: 'Abrir ficha completa', exact: true })
      .click()
    await arrived
    await page.getByRole('button', { name: /Contexto actual:/ }).click()
    await page
      .getByRole('button', { name: /Despacho de Servicios Profesionales/ })
      .click()
    release()
    await expect(page).toHaveURL(/\/app\/taxpayers$/)
    await expect(page.getByText('Tu cartera empieza aquí')).toBeVisible()
    expect(
      await page
        .getByText('Expediente privado A DEMO', { exact: true })
        .count(),
    ).toBe(0)
    await create(page, 'DEM000101302', 'Expediente privado B DEMO')
    await full(page)
    await expect(
      page.getByRole('heading', {
        level: 1,
        name: 'Expediente privado B DEMO',
      }),
    ).toBeVisible()
    expect(
      await page.evaluate(() =>
        Object.keys(localStorage).concat(Object.keys(sessionStorage)),
      ),
    ).toEqual([])
    await api(page, '/auth/logout', {})
    await page.getByRole('link', { name: 'Volver a contribuyentes' }).click()
    await expect(page).toHaveURL(/\/login\?estado=expirada/)
  } finally {
    await Promise.all(contexts.map((c) => c.close()))
  }
})
