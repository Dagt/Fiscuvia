import type { components, paths } from './schema'
import {
  ERROR_STATUS as problemStatus,
  PLATFORM_INFO,
} from './contract-metadata'

export type PlatformInfo = components['schemas']['PlatformInfo']
type ProblemCode = components['schemas']['ProblemDetails']['code']
type ClientCode =
  ProblemCode | 'HTTP_ERROR' | 'NETWORK_ERROR' | 'INVALID_RESPONSE'

const publicInfoPath = '/public/info' satisfies keyof paths
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export class ApiClientError extends Error {
  readonly code: ClientCode
  readonly status: number | undefined
  readonly traceId: string | undefined

  constructor(code: ClientCode, status?: number, traceId?: string) {
    const message =
      code === 'NETWORK_ERROR'
        ? 'No fue posible conectar con el servidor.'
        : code === 'INVALID_RESPONSE'
          ? 'El servidor devolvió una respuesta no válida.'
          : 'No fue posible completar la solicitud.'
    super(message)
    this.name = 'ApiClientError'
    this.code = code
    this.status = status
    this.traceId = traceId
  }
}

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function decodePlatformInfo(value: unknown): PlatformInfo {
  if (
    !object(value) ||
    Object.keys(value).length !== 3 ||
    value.productName !== PLATFORM_INFO.productName ||
    value.tagline !== PLATFORM_INFO.tagline ||
    value.apiVersion !== PLATFORM_INFO.apiVersion
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return { ...PLATFORM_INFO }
}

/** Public configuration only. No URL credentials, query/fragment or arbitrary endpoint paths. */
export function resolveApiBaseUrl(
  value = '/api/v1',
  origin = globalThis.location.origin,
): string {
  try {
    const url = new URL(value, origin)
    if (
      !['http:', 'https:'].includes(url.protocol) ||
      url.username ||
      url.password ||
      url.search ||
      url.hash ||
      !url.pathname.replace(/\/$/, '').endsWith('/api/v1')
    )
      throw new Error('La URL base de la API no es válida.')
    return url.toString().replace(/\/$/, '')
  } catch {
    throw new Error('La URL base de la API no es válida.')
  }
}

function safeProblem(value: unknown, response: Response): ApiClientError {
  const header = response.headers.get('X-Correlation-Id')
  const trace = header && uuid.test(header) ? header : undefined
  if (
    trace !== undefined &&
    object(value) &&
    typeof value.code === 'string' &&
    Object.hasOwn(problemStatus, value.code) &&
    value.status === response.status
  ) {
    const code = value.code as ProblemCode // Membership checked against the exhaustive generated-code map.
    if (
      problemStatus[code] === response.status &&
      value.traceId === trace &&
      value.instance === `urn:uuid:${trace}` &&
      value.type ===
        `urn:fiscuvia:problem:${code.toLowerCase().replaceAll('_', '-')}`
    ) {
      return new ApiClientError(code, response.status, trace)
    }
  }
  return new ApiClientError('HTTP_ERROR', response.status, trace)
}

export function createApiClient(
  options: { baseUrl?: string; transport?: typeof fetch } = {},
) {
  const baseUrl = resolveApiBaseUrl(
    options.baseUrl ?? import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  )
  const transport = options.transport ?? globalThis.fetch
  return {
    async getPlatformInfo(signal?: AbortSignal): Promise<PlatformInfo> {
      signal?.throwIfAborted()
      let response: Response
      try {
        response = await transport(`${baseUrl}${publicInfoPath}`, {
          method: 'GET',
          headers: { Accept: 'application/json' },
          credentials: 'omit',
          redirect: 'error',
          cache: 'no-store',
          referrerPolicy: 'no-referrer',
          ...(signal ? { signal } : {}),
        })
      } catch {
        signal?.throwIfAborted()
        throw new ApiClientError('NETWORK_ERROR')
      }
      signal?.throwIfAborted()
      const media = response.headers.get('Content-Type')?.split(';')[0]?.trim()
      if (!response.ok) {
        let body: unknown
        if (media === 'application/problem+json') {
          try {
            body = await response.json()
          } catch {
            /* Safe generic error, without retaining the response. */
          }
        }
        signal?.throwIfAborted()
        throw safeProblem(body, response)
      }
      if (response.status !== 200 || media !== 'application/json')
        throw new ApiClientError('INVALID_RESPONSE', response.status)
      let body: unknown
      try {
        body = await response.json()
      } catch {
        signal?.throwIfAborted()
        throw new ApiClientError('INVALID_RESPONSE', response.status)
      }
      signal?.throwIfAborted()
      return decodePlatformInfo(body)
    },
  }
}
