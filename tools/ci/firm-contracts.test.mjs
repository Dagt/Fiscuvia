import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validator } from './contracts.mjs'

test('Firm settings: closed command rejects owner, state, URL and unapproved fields',()=>{
  const check=validator('UpdateFirmSettings')
  const valid={expectedVersion:0,commercialName:'Despacho DEMO',timeZone:'America/Mexico_City',locale:'es-MX'}
  assert(check(valid))
  for(const extra of [{ownerId:'00000000-0000-4000-8000-000000000001'},{status:'ACTIVE'},{logoReference:'https://external.invalid/logo.svg'},{role:'FIRM_OWNER_ADMIN'}])assert(!check({...valid,...extra}))
  for(const change of [{commercialName:''},{commercialName:'x'.repeat(161)},{commercialName:'DEMO\nPRIVATE'},{timeZone:'UTC'},{locale:'en-US'},{expectedVersion:-1},{contactEmail:'invalid'}])assert(!check({...valid,...change}))
})
test('Firm settings: strict response rejects external logo and unsafe version',()=>{
  const check=validator('FirmSettings')
  const valid={commercialName:'Despacho DEMO',contactEmail:null,phone:null,timeZone:'America/Mexico_City',locale:'es-MX',status:'ACTIVE',version:0,createdAt:'2026-11-03T00:00:00Z',updatedAt:'2026-11-03T00:00:00Z',canManage:false,logoReference:null}
  assert(check(valid));assert(!check({...valid,logoReference:'external'}));assert(!check({...valid,version:1e20}));assert(!check({...valid,tenantSecret:'private'}))
})
