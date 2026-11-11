import { test, expect } from '@playwright/test'
import { validator } from '../../tools/ci/contracts.mjs'
import { login, owner, api, take, finishCaptures } from './team-harness'
function conforms(name: string, value: unknown) {
  const check = validator(name)
  if (!check(value)) throw Error('HTTP contract mismatch: ' + name)
}
test.afterAll(finishCaptures)

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
    await page
      .getByRole('button', { name: 'Nueva invitaci\u00f3n', exact: true })
      .click()
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
    await expect(
      recipient.getByText('Sin permiso para administrar el equipo'),
    ).toBeVisible()
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
      .getByRole('button', { name: 'Nueva invitaci\u00f3n', exact: true })
      .click()
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
    await page
      .getByRole('button', { name: 'Nueva invitaci\u00f3n', exact: true })
      .click()
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
    .getByRole('button', { name: 'Nueva invitaci\u00f3n', exact: true })
    .click()
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
