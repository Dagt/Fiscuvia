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

test('approved team commands cannot grant owner or accept actor claims',()=>{
 const role=validator('TeamRoleCommand'), status=validator('TeamStatusCommand')
 assert(role({role:'ASSISTANT',expectedVersion:0}));assert(!role({role:'FIRM_OWNER_ADMIN',expectedVersion:0}))
 assert(!role({role:'ACCOUNTANT',expectedVersion:0,actorId:'forged'}));assert(!role({role:'ACCOUNTANT',expectedVersion:-1}))
 assert(status({status:'ACTIVE',expectedVersion:2}));assert(!status({status:'DELETED',expectedVersion:2}))
})
test('team history is typed, token-free and paginated',()=>{
 const check=validator('TeamInvitationsPage'), id='00000000-0000-4000-8000-000000000001'
 const item={invitationId:id,displayName:null,email:null,role:'ASSISTANT',status:'PENDING',version:0,createdAt:'2026-11-11T20:00:00Z',expiresAt:'2026-11-12T20:00:00Z'}
 assert(check({items:[item],page:0,size:20,total:1}));assert(!check({items:[{...item,token:'forbidden'}],page:0,size:20,total:1}))
 assert(!check({items:[],page:201,size:20,total:0}));assert(!check({items:[],page:0,size:51,total:0}))
})
