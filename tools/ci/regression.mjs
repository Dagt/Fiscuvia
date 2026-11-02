import scenarios from './security-scenarios.mjs'

export const states = ['IMPLEMENTADO Y VERIFICADO', 'IMPLEMENTADO CON VERIFICACIÓN PENDIENTE',
  'CONTRATO DE SEGURIDAD PARA MÓDULO FUTURO', 'DECISIÓN PENDIENTE DE APROBACIÓN']
// Deliberately independent of manifest coverage: a critical row cannot silently become deferred.
const mandatory = Array.from({ length: 28 }, (_, i) => `T30-${String(i + 1).padStart(2, '0')}`)
const fields = ['id', 'coverage', 'threat', 'boundary', 'control', 'attack', 'expected', 'tests', 'evidence', 'limitation', 'residualRisk']
export function regressionReport(passedCases, testedSha, sourceSha = testedSha, manifest = scenarios) {
  if (![testedSha, sourceSha].every(sha => /^[a-f0-9]{40}$/.test(sha))) throw Error('SHA inválido')
  if (!(passedCases instanceof Set) || !Array.isArray(manifest)) throw Error('Evidencia inválida')
  const ids = new Set()
  const rows = manifest.map(row => {
    if (Object.keys(row).sort().join() !== [...fields].sort().join() || ids.has(row.id)
      || !/^(T30-\d{2}|[FPD]30-[A-Z]+)$/.test(row.id) || !states.includes(row.coverage)) throw Error('Escenario inválido')
    ids.add(row.id)
    for (const key of fields.filter(key => key !== 'tests')) {
      if (typeof row[key] !== 'string' || !row[key].length || row[key].length > 512) throw Error('Metadato inválido')
    }
    if (!Array.isArray(row.tests) || !row.tests.every(ref => /^com\.fiscuvia\.[A-Za-z.]+#[A-Za-z][A-Za-z0-9]+$/.test(ref))) throw Error('Referencia inválida')
    const implemented = row.coverage === states[0]
    if (mandatory.includes(row.id) && !implemented) throw Error('Escenario crítico diferido')
    if (implemented && (!row.tests.length || row.tests.some(ref => !passedCases.has(ref)))) throw Error(`Prueba crítica ausente: ${row.id}`)
    if (!implemented && row.tests.length) throw Error('Cobertura pendiente no puede afirmar prueba aprobada')
    // Only reviewed static fields; JUnit names, stdout, properties, responses and SQL values never enter the report.
    return { ...row, result: implemented ? 'PASS' : 'NOT VERIFIED', testedSha }
  })
  if (mandatory.some(id => !ids.has(id))) throw Error('Matriz crítica incompleta')
  return { schemaVersion: 1, testedSha, sourceSha, scenarios: rows,
    coverage: Object.fromEntries(states.map(state => [state, rows.filter(row => row.coverage === state).length])) }
}
