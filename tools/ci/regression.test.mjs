import test from 'node:test'
import assert from 'node:assert/strict'
import scenarios from './security-scenarios.mjs'
import { regressionReport } from './regression.mjs'
import { verifyGateTotals } from './test-gates.mjs'
const sha = 'a'.repeat(40)
const passed = () => new Set(scenarios.flatMap(row => row.tests))
test('tools gate rejects reduced, skipped, failed and missing totals', () => {
  const good = '# tests 19\n# pass 19\n# fail 0\n# cancelled 0\n# skipped 0\n# todo 0\n'
  verifyGateTotals(good)
  for (const bad of ['', good.replaceAll('19', '18'), good.replace('# skipped 0', '# skipped 1'), good.replace('# fail 0', '# fail 1')]) assert.throws(() => verifyGateTotals(bad))
})
test('regression report requires exact critical methods, not only totals', () => {
  const cases = passed(); cases.delete(scenarios[0].tests[0]); cases.add('com.fiscuvia.Other#unrelatedTest')
  assert.throws(() => regressionReport(cases, sha), /ausente/)
})
test('critical rows cannot be removed', () => {
  assert.throws(() => regressionReport(passed(), sha, sha, scenarios.slice(1)), /incompleta/)
})
test('critical rows cannot be relabeled future', () => {
  const manifest = structuredClone(scenarios); manifest[0].coverage = scenarios.at(-1).coverage; manifest[0].tests = []
  assert.throws(() => regressionReport(passed(), sha, sha, manifest), /diferido/)
})
test('future and decision rows never report PASS', () => {
  const report = regressionReport(passed(), sha)
  assert.equal(report.scenarios.filter(row => row.result === 'PASS').length, 28)
  assert.ok(report.scenarios.filter(row => !row.id.startsWith('T30-')).every(row => row.result === 'NOT VERIFIED'))
})
test('report rejects arbitrary payload, duplicate identifiers and invalid references', () => {
  for (const change of [row => { row.payload = 'DEMO-private' }, row => { row.tests = ['Bearer DEMO-private'] }, row => { row.id = scenarios[1].id }]) {
    const manifest = structuredClone(scenarios); change(manifest[0])
    assert.throws(() => regressionReport(passed(), sha, sha, manifest))
  }
})
test('report contains no external runtime output and binds both tested and source SHA', () => {
  const cases = passed(); cases.add('DEMO-private stdout token')
  const report = regressionReport(cases, sha, 'b'.repeat(40))
  assert.equal(report.sourceSha, 'b'.repeat(40)); assert.ok(report.scenarios.every(row => row.testedSha === sha))
  assert.ok(!JSON.stringify(report).includes('DEMO-private stdout token'))
  assert.throws(() => regressionReport(cases, 'pending'), /SHA/)
})
