import test from 'node:test'
import assert from 'node:assert/strict'
import { validator } from './contracts.mjs'

test('local email request is closed and cannot grant owner or accept identity claims',()=>{
 const check=validator('LocalEmailInvitationRequest')
 assert(check({email:'destino@example.invalid',role:'ACCOUNTANT'}))
 assert(!check({email:'destino@example.invalid',role:'FIRM_OWNER_ADMIN'}))
 assert(!check({email:'destino@example.invalid',role:'ASSISTANT',subject:'forged'}))
 assert(!check({email:'invalid',role:'ASSISTANT'}))
})
test('local invitation response and options expose no directory or provider token fields',()=>{
 const check=validator('LocalEmailInvitation')
 const reply={invitationId:'00000000-0000-4000-8000-000000000001',token:'a'.repeat(43),expiresAt:'2026-11-12T20:00:00Z',delivery:'MANUAL',replayed:false}
 assert(check(reply));assert(check({...reply,token:null,replayed:true}))
 assert(!check({...reply,access_token:'forbidden'}));assert(!check({...reply,delivery:'EMAIL'}));assert(!check({...reply,token:'short'}))
 const options=validator('LocalInvitationOptions');assert(options({emailInvitationsAvailable:true,delivery:'MANUAL'}));assert(!options({emailInvitationsAvailable:true,delivery:'MANUAL',members:[]}))
})
