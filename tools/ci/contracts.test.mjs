import { test } from 'node:test'
import assert from 'node:assert/strict'
import { contract, validator, checkHttpResponses, generatedTypes } from './contracts.mjs'

test('contrato implementado sólo publica información pública y tipos reproducibles', async () => {
  assert.deepEqual(Object.keys(contract.paths), ['/public/info'])
  assert.deepEqual(contract.security, [])
  assert.equal(contract.paths['/public/info'].get['x-implementation-status'], 'implemented')
  await generatedTypes()
})

test('schemas estrictos rechazan datos extra, tipos inválidos y UUID incorrecto', () => {
  const info = validator('PlatformInfo')
  const data = contract.paths['/public/info'].get.responses['200'].content['application/json'].example
  assert.equal(info(data), true)
  assert.equal(info({ ...data, databaseUrl: 'synthetic-private-value' }), false)
  assert.equal(info({ ...data, apiVersion: 1 }), false)
  const problem = validator('ProblemDetails')
  const example = contract.components.schemas.ProblemDetails.example
  assert.equal(problem(example), true)
  assert.equal(problem({ ...example, stackTrace: 'synthetic-private-value' }), false)
  assert.equal(problem({ ...example, traceId: 'not-a-uuid' }), false)
  assert.equal(problem({ ...example, fieldErrors: Array(21).fill(example.fieldErrors[0]) }), false)
})

test('evidencia HTTP exige todos los códigos y concordancia status/header/cuerpo', () => {
  const example = contract.components.schemas.ProblemDetails.example
  const valid = [{ scenario: 'info', status: 200, contentType: 'application/json', body:
    contract.paths['/public/info'].get.responses['200'].content['application/json'].example }]
  for (const [code, status] of Object.entries(contract['x-error-statuses'])) {
    valid.push({ scenario: code, status, contentType: 'application/problem+json', correlationId: example.traceId,
      body: { ...example, code, status, type: `urn:fiscuvia:problem:${code.toLowerCase().replaceAll('_', '-')}` } })
  }
  assert.equal(checkHttpResponses(valid), 16)
  assert.throws(() => checkHttpResponses(valid.slice(1)))
  assert.throws(() => checkHttpResponses(valid.slice(0, -1)))
  const mismatch = structuredClone(valid)
  mismatch[1].body.status = 500
  mismatch[1].status = 500
  assert.throws(() => checkHttpResponses(mismatch))
})
