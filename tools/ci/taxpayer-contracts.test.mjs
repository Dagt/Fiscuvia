import test from 'node:test'
import assert from 'node:assert/strict'
import { checkTaxpayerHttpResponses, validator } from './contracts.mjs'

const id = '00000000-0000-4000-8000-000000000001'
const root = '/api/v1/tenants/{tenantId}/taxpayers'
const taxpayer = { id, rfc: 'DEM000101001', personType: 'LEGAL_ENTITY', legalName: 'DEMO', tradeName: null,
  status: 'ACTIVE', version: 0, createdAt: '2026-11-12T00:00:00Z', updatedAt: '2026-11-12T00:00:00Z',
  regimes: [], rfcValidation: 'RFC_LOCAL_STRUCTURE_V1', officialVerification: 'NOT_PERFORMED' }
const receipt = { targetId: id, version: 0, replayed: false }
function evidence() {
  return [
    ['get', root, { items: [taxpayer], page: 0, size: 20, hasNext: false }, 200],
    ['post', root, receipt, 201],
    ['get', `${root}/{taxpayerId}`, taxpayer, 200],
    ['patch', `${root}/{taxpayerId}`, receipt, 200],
    ['post', `${root}/{taxpayerId}/archivals`, receipt, 200],
    ['post', `${root}/{taxpayerId}/restorations`, receipt, 200],
  ].map(([method, route, body, status]) => ({ method, route, body: structuredClone(body), status,
    contentType: 'application/json', correlationId: id, cacheControl: 'no-store' }))
}
test('contrato fiscal exige evidencia de las seis operaciones y rechaza éxito con datos adicionales', () => {
  assert.equal(checkTaxpayerHttpResponses(evidence()), 6)
  assert.throws(() => checkTaxpayerHttpResponses(evidence().slice(1)))
  const values = evidence(); values[2].body.grants = ['unapproved']
  assert.throws(() => checkTaxpayerHttpResponses(values))
})
test('contrato fiscal impide confundir revisión local con SAT y exige metadatos privados', () => {
  const values = evidence(); values[2].body.officialVerification = 'VERIFIED'
  assert.throws(() => checkTaxpayerHttpResponses(values))
  const cached = evidence(); cached[0].cacheControl = 'public'
  assert.throws(() => checkTaxpayerHttpResponses(cached))
})
test('DTO fiscal cerrado conserva campos nullable explícitos y no admite estado ni actor del cliente', () => {
  const check = validator('CreateTaxpayer')
  const body = { rfc: 'DEM000101001', personType: 'LEGAL_ENTITY', legalName: 'DEMO', tradeName: null, regimes: [] }
  assert.equal(check(body), true)
  for (const extra of ['status', 'actor', 'tenantId', 'source', 'grants']) assert.equal(check({ ...body, [extra]: 'untrusted' }), false)
  const missing = { ...body }; delete missing.tradeName
  assert.equal(check(missing), false)
})
