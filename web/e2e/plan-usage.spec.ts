import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { mkdirSync } from 'node:fs'

const a = '00000000-0000-4000-8000-000000000001'
const b = '00000000-0000-4000-8000-000000000002'
const members = [a, b].map((tenantId, i) => ({
  tenantId,
  membershipId: tenantId,
  role: 'FIRM_OWNER_ADMIN',
  commercialName: `DEMO Despacho ${i === 0 ? 'Norte' : 'Sur'}`,
  tenantStatus: 'ACTIVE',
}))
const resources = [
  'USERS',
  'TAXPAYERS',
  'STORAGE_BYTES',
  'JOBS_RUNNING',
  'JOBS_PENDING',
  'JOBS_PER_PERIOD',
]
function usage(used = 1, limit = 3) {
  return resources.map((resource) => ({
    resource,
    limit: resource === 'USERS' ? limit : null,
    version: resource === 'USERS' ? 1 : null,
    validFrom: resource === 'USERS' ? '2026-11-01T00:00:00Z' : null,
    validUntil: null,
    measured: resource === 'USERS' ? used : resource === 'TAXPAYERS' ? 0 : null,
    reserved: ['USERS', 'TAXPAYERS'].includes(resource) ? 0 : null,
    available: resource === 'USERS' ? Math.max(0, limit - used) : null,
    overLimit: resource === 'USERS' ? used > limit : null,
    integration: ['USERS', 'TAXPAYERS'].includes(resource)
      ? 'MEASURED'
      : 'NOT_INTEGRATED',
  }))
}
async function fixture(page: Page, mode: string) {
  let calls = 0
  await page.route('**/api/v1/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const json = (body: unknown, status = 200) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify(body),
        headers: { 'Cache-Control': 'no-store' },
      })
    if (path.endsWith('/me'))
      return json({
        userId: a,
        memberships: members,
        privilegedMfaVerified: true,
      })
    if (path.endsWith('/csrf'))
      return json({
        headerName: 'X-CSRF-TOKEN',
        token: 'synthetic-ui-fixture-csrf-token-32-characters',
      })
    if (path.endsWith('/auth/tenant'))
      return json(
        members.find(
          (m) => m.tenantId === route.request().postDataJSON().tenantId,
        ),
      )
    if (path.endsWith('/settings'))
      return json({
        commercialName: 'DEMO Despacho',
        contactEmail: null,
        phone: null,
        timeZone: 'America/Mexico_City',
        locale: 'es-MX',
        status: 'ACTIVE',
        version: 0,
        createdAt: '2026-11-01T00:00:00Z',
        updatedAt: '2026-11-01T00:00:00Z',
        canManage: false,
        logoReference: null,
      })
    if (path.endsWith('/entitlements')) {
      calls++
      if (mode === 'denied') return json({}, 403)
      if (mode === 'expired') return json({}, 401)
      if (mode === 'error' && calls === 1) return json({}, 503)
      const used = path.includes(b)
        ? 0
        : mode === 'full'
          ? 3
          : mode === 'over'
            ? 4
            : 1
      return json(usage(used))
    }
    return json({}, 404)
  })
  await page.goto('/app')
  await page.getByRole('button', { name: /DEMO Despacho Norte/ }).click()
  await page.getByRole('button', { name: 'Plan y uso', exact: true }).click()
}
async function capture(page: Page, name: string, project: string) {
  if (!process.env.FISCUVIA_CAPTURE || !['desktop', 'mobile'].includes(project))
    return
  const dir = '../docs/ux/evidence/issue-32'
  mkdirSync(dir, { recursive: true })
  if (name === 'sin-consumo-medido')
    await page
      .getByRole('region', { name: 'Almacenamiento', exact: true })
      .scrollIntoViewIfNeeded()
  await page.screenshot({
    path: `${dir}/${name}-${project}.png`,
    fullPage: false,
    animations: 'disabled',
  })
}
for (const mode of ['ready', 'full', 'over', 'denied', 'error'])
  test(`plan y uso: ${mode}`, async ({ page }, info) => {
    await fixture(page, mode)
    await expect(
      page.getByText(
        mode === 'denied'
          ? 'Sin permiso para consultar el plan'
          : mode === 'error'
            ? 'No pudimos cargar esta vista'
            : 'Usuarios',
        { exact: true },
      ),
    ).toBeVisible()
    if (mode === 'full')
      await expect(
        page.getByText('Límite alcanzado', { exact: true }),
      ).toBeVisible()
    if (mode === 'over')
      await expect(page.getByText('Sobrecupo', { exact: true })).toBeVisible()
    const axe = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
      .analyze()
    expect(axe.violations).toEqual([])
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
    await capture(page, mode, info.project.name)
    if (mode === 'ready')
      await capture(page, 'sin-consumo-medido', info.project.name)
    if (mode === 'error') {
      await page.getByRole('button', { name: 'Reintentar' }).click()
      await expect(page.getByText('Usuarios', { exact: true })).toBeVisible()
    }
  })
test('cambiar despacho retira uso anterior', async ({ page }, info) => {
  await fixture(page, 'over')
  await expect(page.getByText('Sobrecupo', { exact: true })).toBeVisible()
  await page
    .getByRole('button', { name: 'Cambiar de despacho', exact: true })
    .click()
  await expect(page.getByText('Sobrecupo', { exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: /DEMO Despacho Sur/ }).click()
  await page.getByRole('button', { name: 'Plan y uso', exact: true }).click()
  await expect(page.getByText('Usuarios', { exact: true })).toBeVisible()
  await expect(page.getByText('Sobrecupo', { exact: true })).toHaveCount(0)
  await capture(page, 'cambio-despacho', info.project.name)
})
test('sesión expirada retira consumo', async ({ page }) => {
  await fixture(page, 'expired')
  await expect(page).toHaveURL(/estado=expirada/)
  await expect(page.getByText('Usuarios', { exact: true })).toHaveCount(0)
})
