import { test, expect } from '@playwright/test'
import {
  owner,
  login,
  api,
  directory,
  currentTenant,
  fixture,
  create,
  open,
  close,
  lifecycle,
  patch,
  take,
  finish,
} from './taxpayer-harness'
test.afterAll(finish)
test('propietario opera directorio real: alta, búsqueda, régimen, páginas, conflicto, archivo y restauración', async ({
  page,
}) => {
  test.setTimeout(180000)
  await owner(page, 'alice-16')
  const t = await currentTenant(page)
  await directory(page)
  await expect(page.getByText('Tu cartera empieza aquí')).toBeVisible()
  await take(page, '01-vacio')
  await page
    .getByRole('button', { name: 'Nuevo contribuyente', exact: true })
    .click()
  await take(page, '02-alta')
  await page
    .getByRole('button', { name: 'Registrar contribuyente', exact: true })
    .click()
  await expect(page.getByText(/Revisa longitud/)).toBeVisible()
  await take(page, '03-formulario-invalido')
  await close(page)
  const name =
    'Servicios Profesionales de Demostración y Gestión Administrativa del Centro DEMO'
  const id = await create(page, 'DEM000101001', name)
  await take(page, '04-registro-confirmado')
  await take(page, '05-informacion-administrativa')
  await page
    .getByRole('button', { name: 'Editar información', exact: true })
    .click()
  await page.getByLabel('Nombre comercial', { exact: true }).fill('Centro DEMO')
  await take(page, '06-edicion')
  await patch(page, t, id, 0, 'Actualización concurrente DEMO')
  await page
    .getByRole('button', { name: 'Guardar cambios', exact: true })
    .click()
  await expect(page.getByText(/La información cambió/)).toBeVisible()
  await take(page, '07-conflicto-version')
  await page
    .getByRole('button', { name: 'Actualizar datos para revisar' })
    .click()
  await expect(
    page.getByRole('heading', {
      name: 'Actualización concurrente DEMO',
      exact: true,
    }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: 'Editar información', exact: true })
    .click()
  await page.getByLabel(/^Nombre o razón social/).fill(name)
  await page
    .getByRole('button', { name: 'Guardar cambios', exact: true })
    .click()
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
  await close(page)
  await take(page, '08-directorio')
  await page.getByLabel('Buscar por RFC o nombre').fill('DEM000101001')
  await expect(page.getByText('Página 1 · 1 en esta página')).toBeVisible()
  await take(page, '09-busqueda-rfc')
  await page.getByLabel('Código de régimen (manual)').fill('000')
  await expect(page.getByText('Página 1 · 1 en esta página')).toBeVisible()
  await take(page, '10-filtros')
  await page
    .getByLabel('Buscar por RFC o nombre')
    .fill('SIN COINCIDENCIAS DEMO')
  await expect(page.getByText('No encontramos coincidencias')).toBeVisible()
  await take(page, '11-sin-coincidencias')
  await page.getByRole('button', { name: 'Limpiar búsqueda y filtros' }).click()
  await open(page, name)
  await page
    .getByRole('button', { name: 'Archivar contribuyente', exact: true })
    .click()
  await take(page, '12-confirmacion-archivado')
  await page
    .getByRole('alertdialog')
    .getByRole('button', { name: 'Confirmar archivado', exact: true })
    .click()
  await expect(
    page.getByRole('button', { name: 'Restaurar contribuyente', exact: true }),
  ).toBeVisible()
  await close(page)
  await page.getByLabel('Estado', { exact: true }).selectOption('ARCHIVED')
  await expect(page.getByText(name, { exact: true })).toBeVisible()
  await take(page, '13-archivados')
  await open(page, name)
  await lifecycle(page, false)
  await expect(
    page.getByRole('button', { name: 'Archivar contribuyente', exact: true }),
  ).toBeVisible()
  await take(page, '14-restauracion-confirmada')
  await close(page)
  await page.getByLabel('Estado', { exact: true }).selectOption('ACTIVE')
  for (let i = 2; i <= 12; i++)
    expect(
      (
        await api(
          page,
          `/tenants/${t}/taxpayers`,
          {
            personType: 'LEGAL_ENTITY',
            rfc: `DEM000101${String(i).padStart(3, '0')}`,
            legalName: `Directorio Sintético ${i} DEMO`,
            tradeName: null,
            regimes: [],
          },
          { 'Idempotency-Key': crypto.randomUUID() },
        )
      ).status(),
    ).toBe(201)
  await page.getByLabel('Por página').selectOption('10')
  await page.getByRole('button', { name: 'Actualizar directorio' }).click()
  await expect(page.getByText('Página 1 · 10 en esta página')).toBeVisible()
  await page.getByRole('button', { name: 'Siguiente', exact: true }).click()
  await expect(page.getByText('Página 2 · 2 en esta página')).toBeVisible()
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
  }
})
test('cuota real, restauración rechazada, duplicado y MFA insuficiente', async ({
  page,
}) => {
  test.setTimeout(120000)
  await owner(page, 'alice-17')
  const t = await currentTenant(page)
  await directory(page)
  await create(page, 'DEM000101001', 'Cliente de cuota DEMO')
  await close(page)
  await expect(page.getByText(/No hay cupo disponible/)).toBeVisible()
  await take(page, '15-cuota-agotada')
  await open(page, 'Cliente de cuota DEMO')
  await lifecycle(page, true)
  await close(page)
  await create(page, 'DEM000101002', 'Otro cliente DEMO')
  await close(page)
  await page.getByLabel('Estado', { exact: true }).selectOption('ARCHIVED')
  await open(page, 'Cliente de cuota DEMO')
  await lifecycle(page, false, false)
  await expect(
    page.getByRole('alertdialog').getByText(/No hay cupo disponible/),
  ).toBeVisible()
  await take(page, '16-restauracion-rechazada')
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
  await close(page)
  await fixture(t, 'quota', { value: 2 })
  await page.getByRole('button', { name: 'Actualizar directorio' }).click()
  await page
    .getByRole('button', { name: 'Nuevo contribuyente', exact: true })
    .click()
  await page.getByLabel(/^RFC/).fill('DEM000101001')
  await page.getByLabel(/^Nombre o razón social/).fill('Duplicado DEMO')
  await page
    .getByRole('button', { name: 'Registrar contribuyente', exact: true })
    .click()
  await expect(page.getByText(/El RFC puede estar registrado/)).toBeVisible()
  await take(page, '17-rfc-duplicado')
  await close(page)
  const session = (await page.context().cookies()).find(
    (c) => c.name === '__Host-FiscuviaSession',
  )!.value
  await fixture(t, 'expireMfa', { session })
  await page.getByRole('button', { name: 'Actualizar directorio' }).click()
  await expect(
    page.getByText(/necesitas una verificación MFA reciente/),
  ).toBeVisible()
  await take(page, '18-mfa-insuficiente')
})
test('respuesta perdida se recupera idempotentemente y error real de transporte permite reintento', async ({
  page,
}) => {
  test.setTimeout(120000)
  await owner(page, 'alice-19')
  await directory(page)
  await page.route(
    '**/taxpayers',
    async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      const response = await route.fetch()
      expect(response.status()).toBe(201)
      await route.abort('failed')
    },
    { times: 1 },
  )
  await page
    .getByRole('button', { name: 'Nuevo contribuyente', exact: true })
    .click()
  await page.getByLabel(/^RFC/).fill('DEM000101001')
  await page
    .getByLabel(/^Nombre o razón social/)
    .fill('Respuesta recuperada DEMO')
  await page
    .getByRole('button', { name: 'Registrar contribuyente', exact: true })
    .click()
  await expect(
    page.getByRole('button', { name: 'Recuperar resultado', exact: true }),
  ).toBeVisible()
  await take(page, '19-respuesta-perdida')
  await page.getByRole('button', { name: 'Cerrar panel', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page
    .getByRole('button', { name: 'Recuperar resultado', exact: true })
    .click()
  await expect(
    page.getByRole('heading', {
      name: 'Respuesta recuperada DEMO',
      exact: true,
    }),
  ).toBeVisible()
  await close(page)
  await page.route('**/taxpayers?**', (route) => route.abort('failed'), {
    times: 1,
  })
  await page.getByRole('button', { name: 'Actualizar directorio' }).click()
  await expect(
    page.getByRole('button', { name: 'Reintentar', exact: true }),
  ).toBeVisible()
  await take(page, '20-error-recuperable')
  await page.getByRole('button', { name: 'Reintentar', exact: true }).click()
  await expect(
    page.getByText('Respuesta recuperada DEMO', { exact: true }),
  ).toBeVisible()
})
test('grants exactos, colaborador suspendido/obsoleto, auxiliar, mismo RFC y cambio de despacho sin respuesta tardía', async ({
  page,
  browser,
}) => {
  test.setTimeout(180000)
  const ctx = await browser.newContext({
      baseURL: 'https://127.0.0.1:4174',
      ignoreHTTPSErrors: true,
    }),
    assistant = await browser.newContext({
      baseURL: 'https://127.0.0.1:4174',
      ignoreHTTPSErrors: true,
    })
  try {
    const bob = await ctx.newPage(),
      carol = await assistant.newPage()
    await login(bob, 'bob-24')
    await login(carol, 'carol-24')
    await owner(page, 'alice-18')
    const t = await currentTenant(page)
    await directory(page)
    const id = await create(page, 'DEM000101001', 'Cartera privada A DEMO')
    await close(page)
    for (const [email, role, tab] of [
      ['bob-24@example.invalid', 'ACCOUNTANT', bob],
      ['carol-24@example.invalid', 'ASSISTANT', carol],
    ] as const) {
      const invitation = await api(
        page,
        `/tenants/${t}/team/invitations`,
        { email, role },
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
      await tab.reload()
      await tab.getByRole('button', { name: /Despacho Horizonte DEMO/ }).click()
      await directory(tab)
    }
    await expect(
      carol.getByText('Sin acceso al directorio de contribuyentes'),
    ).toBeVisible()
    await take(carol, '21-sin-permiso')
    await expect(bob.getByText('Tu cartera empieza aquí')).toBeVisible()
    const members = await (
        await page.request.get(`/api/v1/tenants/${t}/team/members`)
      ).json(),
      member = members.items.find(
        (m: { email: string }) => m.email === 'bob-24@example.invalid',
      )
    await fixture(t, 'grant', { member: member.membershipId, taxpayer: id })
    await bob.getByRole('button', { name: 'Actualizar directorio' }).click()
    await open(bob, 'Cartera privada A DEMO')
    await expect(
      bob.getByRole('button', { name: 'Editar información', exact: true }),
    ).toBeVisible()
    await expect(
      bob.getByRole('button', { name: 'Archivar contribuyente', exact: true }),
    ).toHaveCount(0)
    await bob
      .getByRole('button', { name: 'Editar información', exact: true })
      .click()
    await bob
      .getByLabel('Nombre comercial', { exact: true })
      .fill('Contador DEMO')
    await bob
      .getByRole('button', { name: 'Guardar cambios', exact: true })
      .click()
    await expect(bob.getByText('Contador DEMO', { exact: true })).toBeVisible()
    await close(bob)
    for (const [expectedVersion, status] of [
      [0, 'SUSPENDED'],
      [1, 'ACTIVE'],
    ] as const)
      expect(
        (
          await api(
            page,
            `/tenants/${t}/team/members/${member.membershipId}/status`,
            { expectedVersion, status },
            { 'Idempotency-Key': crypto.randomUUID() },
          )
        ).status(),
      ).toBe(200)
    await bob.getByRole('button', { name: 'Actualizar directorio' }).click()
    await expect(bob.getByText('Tu cartera empieza aquí')).toBeVisible()
    expect(
      await bob.getByText('Cartera privada A DEMO', { exact: true }).count(),
    ).toBe(0)
    let release!: () => void
    let fetched!: () => void
    const arrived = new Promise<void>((r) => {
        fetched = r
      }),
      hold = new Promise<void>((r) => {
        release = r
      })
    await page.route(
      `**/tenants/${t}/taxpayers?**`,
      async (route) => {
        const response = await route.fetch()
        fetched()
        await hold
        try {
          await route.fulfill({ response })
        } catch {
          /*aborted by context change*/
        }
      },
      { times: 1 },
    )
    await page.getByRole('button', { name: 'Actualizar directorio' }).click()
    await arrived
    await page.getByRole('button', { name: /Contexto actual:/ }).click()
    await page
      .getByRole('button', { name: /Despacho de Servicios Profesionales/ })
      .click()
    release()
    await expect(page.getByText('Tu cartera empieza aquí')).toBeVisible()
    expect(
      await page.getByText('Cartera privada A DEMO', { exact: true }).count(),
    ).toBe(0)
    await create(page, 'DEM000101001', 'Cartera privada B DEMO')
    await close(page)
    await expect(
      page.getByText('Cartera privada B DEMO', { exact: true }),
    ).toBeVisible()
    expect(
      await page.getByText('Cartera privada A DEMO', { exact: true }).count(),
    ).toBe(0)
    expect(page.url()).not.toContain('DEM000101001')
    expect(
      await page.evaluate(() =>
        Object.keys(localStorage).concat(Object.keys(sessionStorage)),
      ),
    ).toEqual([])
    await api(page, '/auth/logout', {})
    await page.getByRole('button', { name: 'Actualizar directorio' }).click()
    await expect(page).toHaveURL(/\/login\?estado=expirada/)
  } finally {
    await ctx.close()
    await assistant.close()
  }
})
