import { describe, expect, it, vi } from 'vitest'
import {
  ApiClientError,
  createApiClient,
  decodePlatformInfo,
  resolveApiBaseUrl,
} from './client'
import type { PlatformInfo } from './client'

const info: PlatformInfo = {
  productName: 'Fiscuvia',
  tagline: 'Todo tu despacho, una sola vía.',
  apiVersion: '1.0.0',
}
const traceId = '00000000-0000-4000-8000-000000000001'
const json = (value: unknown, status = 200, media = 'application/json') =>
  new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': media, 'X-Correlation-Id': traceId },
  })

describe('contrato y transporte público', () => {
  it('solicita sólo el endpoint tipado sin credenciales y valida JSON real', async () => {
    const transport = vi.fn<typeof fetch>().mockResolvedValue(json(info))
    await expect(
      createApiClient({ transport }).getPlatformInfo(),
    ).resolves.toEqual(info)
    expect(transport).toHaveBeenCalledExactlyOnceWith(
      expect.stringContaining('/api/v1/public/info'),
      expect.objectContaining({
        method: 'GET',
        credentials: 'omit',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
        headers: { Accept: 'application/json' },
      }),
    )
  })

  it('rechaza tipos, campos extra y cambios incompatibles antes de usar datos', () => {
    for (const value of [
      null,
      [],
      {},
      { ...info, productName: 1 },
      { ...info, apiVersion: 'v999' },
      { ...info, secret: 'synthetic-private-value' },
    ]) {
      expect(() => decodePlatformInfo(value)).toThrow(ApiClientError)
    }
  })

  it('normaliza errores RFC 9457 sin conservar detalles, valores ni cuerpos', async () => {
    const body = {
      type: 'urn:fiscuvia:problem:validation-failed',
      instance: `urn:uuid:${traceId}`,
      traceId,
      status: 422,
      code: 'VALIDATION_FAILED',
      detail: 'synthetic-private-value',
      stack: 'synthetic-private-value',
      fieldErrors: [{ field: 'secret', message: 'synthetic-private-value' }],
    }
    const transport = vi
      .fn<typeof fetch>()
      .mockResolvedValue(json(body, 422, 'application/problem+json'))
    const error = await createApiClient({ transport })
      .getPlatformInfo()
      .catch((value: unknown) => value)
    expect(error).toMatchObject({
      code: 'VALIDATION_FAILED',
      status: 422,
      traceId,
    })
    expect(JSON.stringify(error)).not.toContain('synthetic-private-value')
    expect(String(error)).not.toContain('synthetic-private-value')
  })

  it('usa HTTP_ERROR para HTML, problemas malformados o códigos/status inconsistentes', async () => {
    for (const response of [
      json({}, 500, 'text/html'),
      json(
        { code: 'constructor', status: 500 },
        500,
        'application/problem+json',
      ),
      json(
        { code: 'ACCESS_DENIED', status: 500 },
        500,
        'application/problem+json',
      ),
      new Response('{', {
        status: 500,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    ]) {
      const transport = vi.fn<typeof fetch>().mockResolvedValue(response)
      await expect(
        createApiClient({ transport }).getPlatformInfo(),
      ).rejects.toMatchObject({ code: 'HTTP_ERROR', status: 500 })
    }
  })

  it('rechaza éxito con media type, status o JSON inválidos', async () => {
    for (const response of [
      json(info, 200, 'text/html'),
      new Response(null, { status: 204 }),
      new Response('{', { headers: { 'Content-Type': 'application/json' } }),
    ]) {
      await expect(
        createApiClient({
          transport: vi.fn<typeof fetch>().mockResolvedValue(response),
        }).getPlatformInfo(),
      ).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
    }
  })

  it('no filtra errores de red ni reintenta automáticamente', async () => {
    const transport = vi
      .fn<typeof fetch>()
      .mockRejectedValue(new Error('synthetic-private-value'))
    await expect(
      createApiClient({ transport }).getPlatformInfo(),
    ).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(transport).toHaveBeenCalledTimes(1)
  })

  it('preserva cancelación antes y después del transporte', async () => {
    const controller = new AbortController()
    controller.abort()
    const transport = vi.fn<typeof fetch>()
    await expect(
      createApiClient({ transport }).getPlatformInfo(controller.signal),
    ).rejects.toMatchObject({ name: 'AbortError' })
    expect(transport).not.toHaveBeenCalled()
    const running = new AbortController()
    transport.mockImplementation(async () => {
      running.abort()
      return json(info)
    })
    await expect(
      createApiClient({ transport }).getPlatformInfo(running.signal),
    ).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('configura origen/ruta base y rechaza URLs con datos privados o protocolos ajenos', () => {
    expect(resolveApiBaseUrl('/api/v1/', 'https://app.example.test')).toBe(
      'https://app.example.test/api/v1',
    )
    expect(resolveApiBaseUrl('https://api.example.test/api/v1')).toBe(
      'https://api.example.test/api/v1',
    )
    for (const value of [
      'ftp://example.test/api/v1',
      'https://synthetic-user:synthetic-value@example.test/api/v1',
      '/api/v1?secret=value',
      '/api/v1#secret',
      '/other',
    ]) {
      expect(() => resolveApiBaseUrl(value)).toThrow()
    }
  })
})
