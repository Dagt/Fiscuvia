import { readFileSync, readdirSync, mkdirSync, writeFileSync, appendFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'
import { XMLParser, XMLValidator, XMLBuilder } from 'fast-xml-parser'

const root = fileURLToPath(new URL('../../', import.meta.url))
const parser = new XMLParser({ ignoreAttributes: false, processEntities: false })
const list = value => value === undefined ? [] : Array.isArray(value) ? value : [value]
const baseline = {
  'com.fiscuvia.platform.internal.ScaffoldRuntimeConfigurationTests': 15,
  'com.fiscuvia.ModuleBoundariesTests': 4,
  'com.fiscuvia.FiscuviaApplicationTests': 17,
  'com.fiscuvia.ApiContractsTests': 39,
  'com.fiscuvia.ApiPrimitivesTests': 3,
  'com.fiscuvia.PostgresFoundationTests': 3,
  'com.fiscuvia.FiscuviaApplicationIT': 11,
  'com.fiscuvia.FlywayFailureIT': 2,
  'com.fiscuvia.ObservabilityHttpTests': 3,
  'com.fiscuvia.HealthAvailabilityTests': 1,
  'com.fiscuvia.platform.internal.http.CorrelationFilterTests': 1,
  'com.fiscuvia.platform.internal.observability.StructuredLoggingTests': 5,
  'com.fiscuvia.platform.internal.observability.ObservabilityGuardTests': 3,
  'com.fiscuvia.platform.internal.observability.MeterPrivacyTests': 2,
  'com.fiscuvia.platform.internal.observability.TracingConfigurationTests': 2,
  'com.fiscuvia.TenantRlsTests': 20,
  'com.fiscuvia.TenantConcurrencyTests': 2,
  'com.fiscuvia.TenantHttpPrivacyTests': 2,
  'com.fiscuvia.TenantMigrationIT': 3,
  'com.fiscuvia.OidcAuthenticationTests': 22,
  'com.fiscuvia.IdentityMigrationIT': 3,
  'com.fiscuvia.ResourceAuthorizationTests': 37,
  'com.fiscuvia.AuthorizationMigrationIT': 3,
}

export function backendReports(xmls, expected = baseline) {
  let tests = 0
  const found = new Map()
  const suites = xmls.map(xml => {
    if (XMLValidator.validate(xml) !== true || /<!DOCTYPE|<!ENTITY/i.test(xml)) throw Error('XML inválido')
    const suite = parser.parse(xml).testsuite
    if (!suite) throw Error('Falta testsuite')
    const cases = list(suite.testcase)
    const count = Number(suite['@_tests'])
    if (!count || count !== cases.length) throw Error('Reporte vacío o incompleto')
    if (['failures', 'errors', 'skipped'].some(k => Number(suite['@_' + k] ?? 0) !== 0)
      || cases.some(c => ['failure', 'error', 'skipped'].some(k => k in c))) {
      throw Error('Existen pruebas fallidas u omitidas')
    }
    const name = suite['@_name']
    if (found.has(name)) throw Error('Suite duplicada')
    found.set(name, count)
    tests += count
    // Deliberately omit properties, stdout, stderr and exception bodies.
    return {
      '@_name': name, '@_tests': count, '@_failures': 0, '@_errors': 0, '@_skipped': 0,
      testcase: cases.map(c => ({ '@_name': c['@_name'], '@_classname': c['@_classname'], '@_time': c['@_time'] ?? 0 })),
    }
  })
  for (const [name, count] of Object.entries(expected)) {
    if ((found.get(name) ?? 0) < count) throw Error(`Suite ausente o reducida: ${name}`)
  }
  return { tests, xml: new XMLBuilder({ ignoreAttributes: false, format: true }).build({ testsuites: { testsuite: suites } }) }
}

export function webReports(unit, e2e, minimum = 13, minimumUnit = minimum) {
  if (unit.success !== true || unit.numTotalTests < minimumUnit || unit.numPassedTests !== unit.numTotalTests
    || unit.numFailedTests !== 0 || unit.numPendingTests !== 0 || (unit.numTodoTests ?? 0) !== 0) {
    throw Error('Vitest incompleto, fallido u omitido')
  }
  let count = 0
  function visit(suite) {
    for (const child of suite.suites ?? []) visit(child)
    for (const spec of suite.specs ?? []) for (const test of spec.tests ?? []) {
      if (test.expectedStatus !== 'passed' || test.status !== 'expected' || test.results?.length !== 1
        || test.results[0].status !== 'passed') throw Error('Playwright fallido, flaky u omitido')
      count++
    }
  }
  for (const suite of e2e.suites ?? []) visit(suite)
  if (count < minimum || (e2e.errors ?? []).length || e2e.stats?.unexpected !== 0
    || e2e.stats?.skipped !== 0 || e2e.stats?.flaky !== 0 || e2e.stats?.expected !== count) {
    throw Error('Playwright incompleto')
  }
  return { vitest: unit.numTotalTests, playwright: count, skipped: 0, failed: 0 }
}

export function completeWebReports(unit, foundation, identity) {
  const base = webReports(unit, foundation, 13, 37)
  const auth = webReports(unit, identity, 5, 37)
  return { ...base, oidcPlaywright: auth.playwright }
}

function publish(kind, details, xml) {
  const sha = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8' }).trim()
  const report = { testedSha: sha, sourceSha: process.env.SOURCE_SHA ?? sha, node: process.version, ...details }
  const dir = resolve(root, '.ci-artifacts', kind)
  mkdirSync(dir, { recursive: true })
  writeFileSync(resolve(dir, 'summary.json'), JSON.stringify(report, null, 2) + '\n')
  if (xml) writeFileSync(resolve(dir, 'junit.xml'), xml)
  console.log(JSON.stringify(report))
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY,
    `### ${kind}\n\nSHA probado: \`${sha}\`\n\n\`\`\`json\n${JSON.stringify(report, null, 2)}\n\`\`\`\n`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const kind = process.argv[2]
  if (kind === 'backend') {
    const xmls = ['surefire-reports', 'failsafe-reports'].flatMap(dir => {
      const base = resolve(root, 'backend/target', dir)
      return readdirSync(base).filter(f => /^TEST-.*\.xml$/.test(f)).map(f => readFileSync(resolve(base, f), 'utf8'))
    })
    const { tests, xml } = backendReports(xmls)
    publish(kind, { tests, skipped: 0, failed: 0 }, xml)
  } else if (kind === 'web') {
    publish(kind, completeWebReports(
      JSON.parse(readFileSync(resolve(root, '.ci-work/vitest.json'), 'utf8')),
      JSON.parse(readFileSync(resolve(root, '.ci-work/playwright.json'), 'utf8')),
      JSON.parse(readFileSync(resolve(root, '.ci-work/playwright-auth.json'), 'utf8'))))
  } else throw Error('Indicar backend o web')
}
