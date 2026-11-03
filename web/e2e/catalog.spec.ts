import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test('catálogo DEMO: paginación, orden, filtros y detalle real', async ({
  page,
}) => {
  const api: string[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/'))
      api.push(request.url())
  })
  await page.goto('/desarrollo/componentes')
  await expect(
    page.getByRole('heading', { name: 'Una sola identidad visual' }),
  ).toBeVisible()
  await expect(page.getByText('1–10 de 125 · Página 1 de 13')).toBeVisible()
  await page.getByRole('button', { name: 'Siguiente', exact: true }).click()
  await expect(page.getByText('11–20 de 125 · Página 2 de 13')).toBeVisible()
  await page.getByRole('button', { name: 'Anterior', exact: true }).click()
  if (await page.getByRole('table').isVisible()) {
    await page.getByRole('button', { name: 'Valor DEMO' }).click()
    await expect(
      page.getByRole('columnheader', { name: 'Valor DEMO' }),
    ).toHaveAttribute('aria-sort', 'ascending')
  }
  await page.getByRole('searchbox').fill('DEMO-015')
  await expect(page.getByText('1–1 de 1 · Página 1 de 1')).toBeVisible()
  await page.getByRole('button', { name: 'Ver detalle de DEMO-015' }).click()
  await expect(
    page.getByRole('complementary', { name: 'Detalle de DEMO-015' }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: 'Cerrar detalle', exact: true })
    .click()
  await page.getByRole('searchbox').fill('NO-EXISTE-DEMO')
  await expect(
    page.getByRole('heading', { name: 'No hay resultados' }),
  ).toBeVisible()
  expect(api).toEqual([])
})

test('formulario accesible, confirmación y panel modal por teclado', async ({
  page,
}) => {
  await page.goto('/desarrollo/componentes')
  await page.getByRole('button', { name: 'Validar ejemplo' }).click()
  const field = page.getByLabel(
    'Nombre del ejemplo demostrativo con una etiqueta extensa',
  )
  await expect(field).toBeFocused()
  await expect(field).toHaveAttribute('aria-invalid', 'true')
  await field.fill('Referencia DEMO válida')
  await page.getByRole('button', { name: 'Validar ejemplo' }).click()
  await expect(
    page.getByRole('status').filter({ hasText: 'Validación DEMO' }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: 'Reiniciar DEMO', exact: true })
    .click()
  await expect(
    page.getByRole('button', { name: 'Cancelar', exact: true }),
  ).toBeFocused()
  const confirmation = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
    .analyze()
  expect(confirmation.violations).toEqual([])
  await page.keyboard.press('Escape')
  await expect(
    page.getByRole('button', { name: 'Reiniciar DEMO', exact: true }),
  ).toBeFocused()
  await page
    .getByRole('button', { name: 'Abrir panel lateral', exact: true })
    .click()
  const dialog = page.getByRole('dialog', { name: 'Panel lateral DEMO' })
  await expect(dialog).toBeVisible()
  for (let i = 0; i < 5; i++) {
    await page.keyboard.press('Tab')
    expect(
      await dialog.evaluate((element) =>
        element.contains(document.activeElement),
      ),
    ).toBe(true)
  }
  await page.keyboard.press('Escape')
  await expect(
    page.getByRole('button', { name: 'Abrir panel lateral', exact: true }),
  ).toBeFocused()
})

test('tabla oculta filas y conteos en estados no disponibles', async ({
  page,
}) => {
  await page.goto('/desarrollo/componentes')
  const tableSection = page.locator('#tabla')
  for (const [value, title] of [
    ['loading', 'Preparando la vista'],
    ['empty', 'No hay resultados'],
    ['error', 'No pudimos cargar esta vista'],
    ['unauthorized', 'Necesitas permisos para continuar'],
  ] as const) {
    await page.getByLabel('Estado de tabla DEMO').selectOption(value!)
    await expect(
      tableSection.getByRole('heading', { name: title }),
    ).toBeVisible()
    await expect(tableSection.getByText(/Página 1 de/)).toHaveCount(0)
    await expect(
      tableSection.getByText('DEMO-001', { exact: true }),
    ).toHaveCount(0)
  }
  await page.getByLabel('Estado de tabla DEMO').selectOption('error')
  await tableSection.getByRole('button', { name: 'Reintentar' }).click()
  await expect(
    tableSection.getByText('1–10 de 125 · Página 1 de 13'),
  ).toBeVisible()
})

test('catálogo: contraste, reflow a 320, texto ampliado y movimiento reducido', async ({
  page,
}) => {
  await page.goto('/desarrollo/componentes')
  await page.evaluate(() => document.fonts.ready)
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
    .analyze()
  expect(result.violations).toEqual([])
  expect(await page.locator('main').count()).toBe(1)
  expect(await page.locator('h1').count()).toBe(1)
  for (const width of [page.viewportSize()!.width, 320]) {
    await page.setViewportSize({ width, height: 900 })
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
      `overflow @${width}`,
    ).toBe(true)
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.evaluate(() => {
    document.documentElement.style.zoom = '2'
  })
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
    'Chromium CSS zoom 200%',
  ).toBe(true)
  await page.evaluate(() => {
    document.documentElement.style.zoom = '1'
  })
  await page.setViewportSize({ width: 320, height: 900 })
  await page.addStyleTag({
    content:
      'body { font-size: 28px; } .catalog-page p, .catalog-page label, .catalog-page button { font-size: 200%; }',
  })
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.getByRole('button', { name: 'Mostrar estado pendiente' }).click()
  await expect(
    page.getByRole('button', { name: 'Procesando DEMO…' }),
  ).toBeDisabled()
  expect(
    await page
      .locator('.loading-icon')
      .first()
      .evaluate((element) =>
        parseFloat(getComputedStyle(element).animationDuration),
      ),
  ).toBeLessThan(0.01)
})
