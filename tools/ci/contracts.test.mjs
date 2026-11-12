import { test } from 'node:test'
import assert from 'node:assert/strict'
import { contract, validator, checkHttpResponses, generatedTypes } from './contracts.mjs'

test('contrato implementado publica infraestructura, identidad y contribuyentes reales y tipos reproducibles', async () => {
  assert.deepEqual(Object.keys(contract.paths), ['/public/info','/me','/auth/status','/auth/csrf','/auth/login','/auth/logout',
    '/auth/tenant','/auth/invitations','/auth/invitations/accept','/auth/invitations/revoke','/auth/memberships/status',
    '/auth/authorize/local','/auth/callback/local','/tenants/{tenantId}/team/invitation-options','/tenants/{tenantId}/team/invitations','/tenants/{tenantId}/settings','/tenants/{tenantId}/entitlements','/tenants/{tenantId}/team/members','/tenants/{tenantId}/team/members/{memberId}/status','/tenants/{tenantId}/team/members/{memberId}/role','/tenants/{tenantId}/team/invitations/{invitationId}/revoke',
    '/tenants/{tenantId}/taxpayers','/tenants/{tenantId}/taxpayers/{taxpayerId}',
    '/tenants/{tenantId}/taxpayers/{taxpayerId}/archivals','/tenants/{tenantId}/taxpayers/{taxpayerId}/restorations'])
  assert.deepEqual(contract.security, [])
  assert.equal(contract.paths['/public/info'].get['x-implementation-status'], 'implemented')
  await generatedTypes()
})

test('identidad y membresías no admiten tokens, roles arbitrarios ni UUID inválidos', () => {
  const identity = validator('CurrentIdentity')
  const id = '00000000-0000-4000-8000-000000000001'
  const data = { userId: id, memberships: [{ tenantId: id, membershipId: id, role: 'ACCOUNTANT' }], privilegedMfaVerified: false }
  assert.equal(identity(data), true)
  assert.equal(identity({ ...data, accessToken: 'synthetic-private-token' }), false)
  assert.equal(identity({ ...data, memberships: [{ ...data.memberships[0], role: 'SUPERUSER' }] }), false)
  assert.equal(identity({ ...data, userId: 'invalid' }), false)
  assert.deepEqual(contract.paths['/me'].get.security, [{ SessionCookie: [] }])
  assert.equal(contract.components.securitySchemes.SessionCookie.name, '__Host-FiscuviaSession')
  assert.equal(contract.components.responses.Unauthorized.headers, undefined)
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
  assert.equal(checkHttpResponses(valid), 17)
  assert.throws(() => checkHttpResponses(valid.slice(1)))
  assert.throws(() => checkHttpResponses(valid.slice(0, -1)))
  const mismatch = structuredClone(valid)
  mismatch[1].body.status = 500
  mismatch[1].status = 500
  assert.throws(() => checkHttpResponses(mismatch))
})
