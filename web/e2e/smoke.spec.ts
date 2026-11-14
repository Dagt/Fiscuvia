import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test('navegación, estados, entrada directa y ausencia de solicitudes API', async ({
  page,
}) => {
  const errors: string[] = []
  const apiRequests: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/'))
      apiRequests.push(request.url())
  })
  await page.goto('/desarrollo')
  await expect(
    page.getByRole('heading', { name: 'Bienvenido a Fiscuvia' }),
  ).toBeVisible()
  await expect(page.locator('body')).toHaveCSS('font-family', /Inter Variable/)
  const menu = page.getByRole('button', { name: 'Abrir menú de navegación' })
  const hasMobileMenu = await menu.isVisible()
  if (hasMobileMenu) await menu.click()
  await expect(
    page.getByRole('button', { name: 'Contribuyentes', exact: true }),
  ).toBeDisabled()
  if (hasMobileMenu) await page.keyboard.press('Escape')
  await page.getByRole('link', { name: 'Explorar la interfaz' }).click()
  await expect(
    page.getByRole('heading', { name: 'Un espacio para empezar' }),
  ).toBeVisible()
  await page.getByRole('link', { name: 'Error recuperable' }).click()
  await page.getByRole('button', { name: 'Reintentar' }).click()
  await expect(
    page.getByRole('heading', { name: 'Demostración recuperada' }),
  ).toBeVisible()
  await expect(page.getByRole('status')).toContainText('terminó correctamente')
  await page.reload()
  await expect(
    page.getByRole('heading', { name: 'No pudimos cargar esta vista' }),
  ).toBeVisible()
  await page.getByRole('link', { name: 'Sin permisos' }).click()
  await expect(
    page.getByRole('heading', { name: 'Necesitas permisos para continuar' }),
  ).toBeVisible()
  await page.goto('/app/modulo-no-disponible')
  await expect(
    page.getByRole('heading', { name: 'No encontramos esta página' }),
  ).toBeVisible()
  await page.getByRole('link', { name: 'Volver al inicio' }).click()
  expect(errors).toEqual([])
  expect(apiRequests).toEqual([])
})

test('accesibilidad, contraste y ausencia de desbordamiento', async ({
  page,
}) => {
  for (const path of [
    '/desarrollo',
    '/desarrollo/estados?estado=vacio',
    '/desarrollo/estados?estado=error',
    '/desarrollo/estados?estado=carga',
    '/desarrollo/estados?estado=permisos',
    '/inexistente',
  ]) {
    await page.goto(path)
    await page.evaluate(() => document.fonts.ready)
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
      .analyze()
    expect(
      results.violations,
      `${path}: ${JSON.stringify(results.violations)}`,
    ).toEqual([])
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
      path,
    ).toBe(true)
  }
})

test('teclado, enlace de salto y menú móvil con foco confinado', async ({
  page,
}) => {
  await page.goto('/desarrollo')
  await page.keyboard.press('Tab')
  await expect(
    page.getByRole('link', { name: 'Saltar al contenido' }),
  ).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('main')).toBeFocused()
  const menu = page.getByRole('button', { name: 'Abrir menú de navegación' })
  if (await menu.isVisible()) {
    await menu.focus()
    await page.keyboard.press('Enter')
    const dialog = page.getByRole('dialog', { name: 'Navegación de Fiscuvia' })
    await expect(dialog).toBeVisible()
    const accessibility = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
      .analyze()
    expect(accessibility.violations).toEqual([])
    await expect(
      page.getByRole('button', { name: 'Cerrar menú de navegación' }),
    ).toBeFocused()
    for (let i = 0; i < 5; i++) {
      await page.keyboard.press('Tab')
      expect(
        await dialog.evaluate((element) =>
          element.contains(document.activeElement),
        ),
      ).toBe(true)
    }
    await page.keyboard.press('Escape')
    await expect(dialog).not.toBeVisible()
    await expect(menu).toBeFocused()
    await menu.click()
    await dialog.getByRole('link', { name: 'Estados de interfaz' }).click()
    await expect(dialog).not.toBeVisible()
    await expect(page.getByRole('main')).toBeFocused()
  } else {
    await page
      .getByRole('link', { name: 'Estados de interfaz', exact: true })
      .focus()
    await page.keyboard.press('Enter')
    await expect(
      page.getByRole('heading', { name: 'Cada estado, una respuesta clara' }),
    ).toBeVisible()
    await expect(page.getByRole('main')).toBeFocused()
  }
})
