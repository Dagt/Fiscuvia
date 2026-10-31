import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  authStatus,
  authorizeTenant,
  bffBase,
  currentIdentity,
  getCsrf,
  logout,
} from './session-client'

const id = '00000000-0000-4000-8000-000000000001'
const other = '00000000-0000-4000-8000-000000000002'
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'a'.repeat(96) }
const member = { tenantId: id, membershipId: other, role: 'ACCOUNTANT' }
const reply = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
afterEach(() => vi.unstubAllGlobals())
describe('sesiones BFF', () => {
  it('restringe la URL a same-origin y al prefijo del BFF', () => {
    expect(bffBase('/api/v1')).toBe('/api/v1')
    for (const value of [
      'https://external.invalid/api/v1',
      '/other',
      '/api/v1?secret=private',
      '/api/v1#private',
    ])
      expect(() => bffBase(value)).toThrow()
  })
  it('envía cookies same-origin sin seguir redirecciones OAuth por fetch', async () => {
    const fetch = vi.fn().mockResolvedValue(reply({ loginAvailable: true }))
    vi.stubGlobal('fetch', fetch)
    expect(await authStatus()).toBe(true)
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/auth/status',
      expect.objectContaining({
        credentials: 'same-origin',
        redirect: 'error',
        cache: 'no-store',
      }),
    )
  })
  it('obtiene CSRF antes de cada mutación y nunca declara identidad o roles', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(reply(csrf))
      .mockResolvedValueOnce(reply(member))
    vi.stubGlobal('fetch', fetch)
    expect(await authorizeTenant(id)).toEqual(member)
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/v1/auth/tenant',
      expect.objectContaining({
        body: JSON.stringify({ tenantId: id }),
        headers: expect.objectContaining({ 'X-CSRF-TOKEN': csrf.token }),
      }),
    )
    expect(localStorage.length).toBe(0)
  })
  it('valida membresías devueltas por el servidor y rechaza otro tenant', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(reply(csrf))
        .mockResolvedValueOnce(reply({ ...member, tenantId: other })),
    )
    await expect(authorizeTenant(id)).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
    })
  })
  it('no acepta roles desconocidos', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        reply({
          userId: id,
          memberships: [{ ...member, role: 'SUPERUSER' }],
          privilegedMfaVerified: true,
        }),
      ),
    )
    await expect(currentIdentity()).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
    })
  })
  it('acepta identidad mínima tipada sin tokens', async () => {
    const identity = {
      userId: id,
      memberships: [member],
      privilegedMfaVerified: false,
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(reply(identity)))
    expect(await currentIdentity()).toEqual(identity)
  })
  it('mantiene los errores seguros y sólo conserva la correlación válida', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'AUTHENTICATION_REQUIRED',
            status: 401,
            detail: 'private-token',
            traceId: id,
            instance: `urn:uuid:${id}`,
            type: 'urn:fiscuvia:problem:authentication-required',
          }),
          {
            status: 401,
            headers: {
              'Content-Type': 'application/problem+json',
              'X-Correlation-Id': id,
            },
          },
        ),
      ),
    )
    await expect(currentIdentity()).rejects.toMatchObject({
      code: 'AUTHENTICATION_REQUIRED',
      status: 401,
      traceId: id,
    })
    await expect(currentIdentity()).rejects.not.toHaveProperty(
      'message',
      'private-token',
    )
  })
  it('rechaza una respuesta CSRF inválida', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          reply({ token: 'short', headerName: 'Authorization' }),
        ),
    )
    await expect(getCsrf()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('cierra la sesión por POST protegido', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(reply(csrf))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetch)
    await logout()
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/v1/auth/logout',
      expect.objectContaining({ method: 'POST', credentials: 'same-origin' }),
    )
  })
})
