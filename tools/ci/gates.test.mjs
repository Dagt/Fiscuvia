import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, writeFileSync, rmSync, statSync, readdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { backendReports, webReports, completeWebReports } from './reports.mjs'
import { checkLocalRefs, validate } from './openapi.mjs'
import { checkPath, checkWorkflow, createScanDirectory } from './security.mjs'

test('Snapshot: lectura y traversal para scanner sin DAC_OVERRIDE, sin abrir archivos privados', () => {
  const parent = mkdtempSync(join(tmpdir(), 'fiscuvia-permissions-'))
  const before = statSync(parent).mode
  try {
    const snapshot = createScanDirectory(parent)
    // Windows does not represent directory execute bits in fs.stat().mode.
    const readableTraversal = process.platform === 'win32' ? 0o444 : 0o555
    assert.equal(statSync(snapshot).mode & readableTraversal, readableTraversal)
    assert.equal(statSync(parent).mode, before)
    writeFileSync(join(snapshot, 'public-source.txt'), 'synthetic source')
    assert.deepEqual(readdirSync(snapshot), ['public-source.txt'])
    assert.equal(readFileSync(join(snapshot, 'public-source.txt'), 'utf8'), 'synthetic source')
  } finally { rmSync(parent, { recursive: true, force: true }) }
})

test('JUnit: exige suites completas; elimina propiedades y salida del proceso', () => {
  const xml = '<testsuite name="example" tests="1" failures="0" errors="0" skipped="0"><properties><property name="sensitive" value="synthetic-value"/></properties><testcase name="works" classname="example" time="1"/><system-out>synthetic-output</system-out></testsuite>'
  const result = backendReports([xml], { example: 1 })
  assert.equal(result.tests, 1)
  assert.doesNotMatch(result.xml, /sensitive|synthetic|properties|system-out/)
  assert.throws(() => backendReports([], { example: 1 }))
  assert.throws(() => backendReports([xml], { example: 2 }))
  assert.throws(() => backendReports([xml, xml], { example: 1 }))
  for (const attr of ['failures', 'errors', 'skipped']) assert.throws(() => backendReports([xml.replace(`${attr}="0"`, `${attr}="1"`)], {}))
  assert.throws(() => backendReports([xml.replace('<testcase name="works" classname="example" time="1"/>', '<testcase><skipped/></testcase>')], {}))
  assert.throws(() => backendReports(['<!DOCTYPE x><testsuite/>'], {}))
})

test('Web: rechaza omitidos, flaky, reintentos y resultados faltantes', () => {
  const unit = { success: true, numTotalTests: 13, numPassedTests: 13, numFailedTests: 0, numPendingTests: 0 }
  const e2e = { suites: [{ specs: [{ tests: [{ expectedStatus: 'passed', status: 'expected', results: [{ status: 'passed' }] }] }] }], stats: { expected: 1, unexpected: 0, skipped: 0, flaky: 0 } }
  assert.equal(webReports(unit, e2e, 1).playwright, 1)
  assert.throws(() => webReports({ ...unit, numPendingTests: 1 }, e2e, 1))
  assert.throws(() => webReports(unit, { ...e2e, suites: [] }, 1))
  assert.throws(() => webReports(unit, { ...e2e, stats: { ...e2e.stats, flaky: 1 } }, 1))
  const retry = structuredClone(e2e)
  retry.suites[0].specs[0].tests[0].results.push({ status: 'passed' })
  assert.throws(() => webReports(unit, retry, 1))
  const completeUnit = { ...unit, numTotalTests: 70, numPassedTests: 70 }
  const suite = n => ({ ...e2e, suites: [{ specs: [{ tests: Array.from({length:n}, () => structuredClone(e2e.suites[0].specs[0].tests[0])) }] }], stats: {...e2e.stats, expected:n} })
  assert.equal(completeWebReports(completeUnit, suite(33), suite(6)).oidcPlaywright, 6)
  assert.throws(() => completeWebReports(completeUnit, suite(33), suite(5)))
  assert.throws(() => completeWebReports(completeUnit, suite(32), suite(6)))
  assert.throws(() => completeWebReports({...completeUnit,numTotalTests:69,numPassedTests:69}, suite(33), suite(6)))
})

test('OpenAPI: documento actual válido; fixtures inválidos deben fallar', () => {
  const source = readFileSync(new URL('../../docs/api/openapi-v1-draft.yaml', import.meta.url), 'utf8')
  const dir = mkdtempSync(join(tmpdir(), 'fiscuvia-contract-'))
  try {
    for (const [name, data, valid] of [
      ['draft', source, true],
      ['broken-reference', source.replace('#/components/schemas/CurrentUser', '#/components/schemas/DoesNotExist'), false],
      ['invalid-structure', source.replace('openapi: 3.1.0', 'openapi: 9.9.9'), false],
      ['invalid-yaml', 'openapi: [', false],
    ]) {
      const path = join(dir, `${name}.yaml`)
      writeFileSync(path, data)
      let code
      try { code = validate(path) } catch { code = 1 }
      assert.equal(code === 0, valid, name)
    }
    assert.throws(() => checkLocalRefs({ $ref: 'https://example.invalid/schema' }))
    assert.throws(() => checkLocalRefs({ $ref: '../sensitive.yaml' }))
  } finally { rmSync(dir, { recursive: true, force: true }) }
})

test('Política: archivos sensibles, acciones sin SHA y permisos excesivos', () => {
  for (const path of ['infra/.env', '.env.local', 'keys/sat.cer', 'data/client.xml', 'backup.dump', 'private.key']) assert.throws(() => checkPath(path))
  checkPath('infra/.env.example')
  checkPath('backend/pom.xml')
  const workflow = readFileSync(new URL('../../.github/workflows/ci.yml', import.meta.url), 'utf8')
  checkWorkflow(workflow)
  assert.throws(() => checkWorkflow(workflow.replace('contents: read', 'contents: write')))
  assert.throws(() => checkWorkflow(workflow.replace('pull_request:', 'pull_request_target:')))
  assert.throws(() => checkWorkflow(workflow.replace(/actions\/checkout@[a-f0-9]{40}/, 'actions/checkout@v7')))
  assert.throws(() => checkWorkflow(workflow.replace('name: Backend CI', 'name: Otra cosa')))
  assert.throws(() => checkWorkflow(workflow.replace('needs: [backend, web, openapi, security]', 'needs: [backend, web, openapi]')))
})
