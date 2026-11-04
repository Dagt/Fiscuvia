import { afterEach, describe, expect, it, vi } from 'vitest'
import { firmSettings, updateFirmSettings } from './firm-client'
const id = '00000000-0000-4000-8000-000000000001'
const profile = {
  commercialName: 'Despacho sintético',
  contactEmail: null,
  phone: null,
  timeZone: 'America/Mexico_City',
  locale: 'es-MX',
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-11-03T00:00:00Z',
  updatedAt: '2026-11-03T00:00:00Z',
  canManage: false,
  logoReference: null,
}
const reply = (body: unknown) =>
  new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
  })
afterEach(() => vi.unstubAllGlobals())
describe('contratos del despacho', () => {
  it('consulta configuración con cookie same-origin y señal de cancelación', async () => {
    const fetch = vi.fn().mockResolvedValue(reply(profile))
    vi.stubGlobal('fetch', fetch)
    const controller = new AbortController()
    expect(await firmSettings(id, controller.signal)).toEqual(profile)
    expect(fetch).toHaveBeenCalledWith(
      `/api/v1/tenants/${id}/settings`,
      expect.objectContaining({
        credentials: 'same-origin',
        cache: 'no-store',
        redirect: 'error',
        signal: controller.signal,
      }),
    )
  })
  it('rechaza UUID arbitrario antes de solicitar datos', async () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    await expect(firmSettings('../otro')).rejects.toThrow()
    expect(fetch).not.toHaveBeenCalled()
  })
  it('rechaza logo externo, versión insegura y configuración incoherente', async () => {
    const fetch = vi.fn()
    vi.stubGlobal('fetch', fetch)
    for (const bad of [
      { ...profile, logoReference: 'https://external.invalid/a.svg' },
      { ...profile, version: 1e20 },
      { ...profile, locale: 'en-US' },
      { ...profile, status: 'DELETED' },
    ]) {
      fetch.mockResolvedValueOnce(reply(bad))
      await expect(firmSettings(id)).rejects.toThrow()
    }
  })
  it('envía CSRF y conserva la clave proporcionada sin tokens locales', async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        reply({ headerName: 'X-CSRF-TOKEN', token: 'a'.repeat(96) }),
      )
      .mockResolvedValueOnce(reply({ version: 1 }))
    vi.stubGlobal('fetch', fetch)
    expect(
      await updateFirmSettings(
        id,
        {
          expectedVersion: 0,
          commercialName: 'Nuevo',
          timeZone: 'America/Mexico_City',
          locale: 'es-MX',
        },
        id,
      ),
    ).toBe(1)
    expect(fetch.mock.calls[1]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'X-CSRF-TOKEN': 'a'.repeat(96), 'Idempotency-Key': id },
    })
    expect(localStorage.length).toBe(0)
  })
  it('no acepta un éxito sin una versión válida', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValueOnce(
          reply({ headerName: 'X-CSRF-TOKEN', token: 'a'.repeat(96) }),
        )
        .mockResolvedValueOnce(reply({ ok: true })),
    )
    await expect(
      updateFirmSettings(
        id,
        {
          expectedVersion: 0,
          commercialName: 'Nuevo',
          timeZone: 'America/Mexico_City',
          locale: 'es-MX',
        },
        id,
      ),
    ).rejects.toThrow()
  })
})
