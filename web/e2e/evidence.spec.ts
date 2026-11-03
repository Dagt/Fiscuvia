import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { test, expect } from '@playwright/test'

const evidenceDirectory = fileURLToPath(
  new URL('../../.ci-work/foundation-browser-evidence/', import.meta.url),
)

test('capturas reales de la aplicación de producción', async ({ page }) => {
  await mkdir(evidenceDirectory, { recursive: true })
  for (const [name, width, height] of [
    ['desktop-1440', 1440, 900],
    ['compact-1280', 1280, 800],
    ['tablet-768', 768, 1024],
    ['mobile-390', 390, 844],
  ] as const) {
    await page.setViewportSize({ width, height })
    await page.goto('/desarrollo')
    await expect(
      page.getByRole('heading', { name: 'Bienvenido a Fiscuvia' }),
    ).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    await page.screenshot({
      path: `${evidenceDirectory}/${name}.png`,
      animations: 'disabled',
    })
    if (width === 390) {
      await page.screenshot({
        path: `${evidenceDirectory}/mobile-390-full.png`,
        fullPage: true,
        animations: 'disabled',
      })
      await page
        .getByRole('button', { name: 'Abrir menú de navegación' })
        .click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await page.screenshot({
        path: `${evidenceDirectory}/mobile-menu-390.png`,
        animations: 'disabled',
      })
      await page.keyboard.press('Escape')
    }
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  for (const state of ['vacio', 'error']) {
    await page.goto(`/desarrollo/estados?estado=${state}`)
    await page.evaluate(() => document.fonts.ready)
    await page.screenshot({
      path: `${evidenceDirectory}/state-${state}-1440.png`,
      animations: 'disabled',
    })
  }
  await page.setViewportSize({ width: 320, height: 844 })
  await page.goto('/desarrollo')
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true)
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.evaluate(() => {
    document.body.style.zoom = '200%'
  })
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true)
})
