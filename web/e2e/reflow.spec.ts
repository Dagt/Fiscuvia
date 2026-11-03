import { expect, test } from '@playwright/test'

test('PageHeader conserva sus acciones y el ancho global con zoom 200%', async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 900 })
  for (const path of ['/desarrollo', '/desarrollo/componentes']) {
    await page.goto(path)
    await page.evaluate(() => document.fonts.ready)
    await page.evaluate(() => {
      document.body.style.zoom = '200%'
    })
    // Checking the action group catches the regression on Windows too, before its overflow reaches the viewport edge.
    const bounds = await page
      .locator('.page-header > .component-actions')
      .evaluate((element) => ({
        client: element.clientWidth,
        scroll: element.scrollWidth,
      }))
    expect(
      bounds.scroll,
      `${path}: actions must fit their own container`,
    ).toBeLessThanOrEqual(bounds.client)
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      `${path}: no global overflow`,
    ).toBe(true)
  }
})
