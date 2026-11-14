import { expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
const settings = JSON.parse(
  readFileSync(process.env.FISCUVIA_AUTH_HARNESS_FILE!, 'utf8'),
) as { password: string; otpSecret: string }
const root = resolve('..'),
  capture = process.env.FISCUVIA_PROFILE_CAPTURE === '1'
const folder = resolve(
  root,
  capture ? 'docs/ux/evidence/issue-36' : '.ci-work/profile-browser-evidence',
)
mkdirSync(folder, { recursive: true })
const shots: object[] = []
let chromium = ''
const git = (args: string[]) =>
  execFileSync('git', args, { cwd: root, encoding: 'utf8' }).trim()
export async function take(page: Page, scenario: string) {
  if (
    !['04-registro-confirmado', '14-restauracion-confirmada'].includes(
      scenario,
    ) &&
    !(await page.getByRole('dialog').count()) &&
    !(await page.getByRole('alertdialog').count())
  ) {
    const dismiss = page.getByRole('button', {
      name: 'Cerrar notificación',
      exact: true,
    })
    if (await dismiss.count()) await dismiss.click()
  }
  chromium = page.context().browser()!.version()
  for (const [width, height] of [
    [1440, 900],
    [390, 844],
  ]) {
    await page.setViewportSize({ width: width!, height: height! })
    await page.evaluate(() => document.fonts.ready)
    // Audit the settled interface; a disabled-to-enabled button may still be
    // inside its finite CSS transition after the command response arrives.
    await page.evaluate(async () => {
      await Promise.all(
        document
          .getAnimations()
          .filter((a) => a.effect?.getTiming().iterations !== Infinity)
          .map((a) => a.finished.catch(() => undefined)),
      )
    })
    const text = await page.locator('body').innerText()
    expect(
      text.includes(settings.password) || text.includes(settings.otpSecret),
    ).toBe(false)
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
    const file = `${scenario}-${width}.png`
    await page.screenshot({
      path: resolve(folder, file),
      animations: 'disabled',
    })
    shots.push({
      file,
      scenario,
      width,
      height,
      sha256: createHash('sha256')
        .update(readFileSync(resolve(folder, file)))
        .digest('hex'),
      axeViolations: 0,
      horizontalOverflow: false,
    })
  }
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
    const body = page.locator('.taxpayer-drawer .drawer-body')
    if (await body.count())
      expect(
        await body.evaluate((el) => el.scrollWidth <= el.clientWidth),
      ).toBe(true)
  }
  await page.setViewportSize({ width: 1440, height: 900 })
}
export function finish() {
  if (!capture) return
  if (
    git([
      'diff',
      '--name-only',
      'HEAD',
      '--',
      'web',
      'backend/src',
      'tools/ci/auth-browser.mjs',
    ])
  )
    throw Error('Commit application and fixtures before publishing screenshots')
  writeFileSync(
    resolve(folder, 'capture-manifest.json'),
    JSON.stringify(
      {
        sourceSha: git(['rev-parse', 'HEAD']),
        applicationTree: git(['rev-parse', 'HEAD:web']),
        backendTree: git(['rev-parse', 'HEAD:backend/src/main']),
        fixtureTree: git(['rev-parse', 'HEAD:backend/src/test']),
        chromium,
        capturedAt: new Date().toISOString(),
        additionalReflowViewports: [
          { width: 1280, height: 800 },
          { width: 768, height: 1024 },
          { width: 320, height: 844 },
        ],
        source:
          'Actual React application, PostgreSQL 18, V1–V13, real Keycloak OTP; synthetic test identities and administrative commands; no mocked success/data/permissions',
        screenshots: shots,
      },
      null,
      2,
    ) + '\n',
  )
}
