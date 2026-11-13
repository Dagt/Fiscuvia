import type { components } from './schema'
import { ApiClientError, decodeApiProblem } from './client'

export type CurrentIdentity = components['schemas']['CurrentIdentity']
export type Membership = components['schemas']['Membership']
type Csrf = components['schemas']['CsrfToken']
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const roles = ['FIRM_OWNER_ADMIN', 'FIRM_SUPERVISOR', 'ACCOUNTANT', 'ASSISTANT']
function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}
function membership(value: unknown): value is Membership {
  return (
    object(value) &&
    typeof value.tenantId === 'string' &&
    uuid.test(value.tenantId) &&
    typeof value.membershipId === 'string' &&
    uuid.test(value.membershipId) &&
    typeof value.role === 'string' &&
    roles.includes(value.role) &&
    (value.commercialName === undefined ||
      value.commercialName === null ||
      (typeof value.commercialName === 'string' &&
        value.commercialName.length <= 160)) &&
    (value.tenantStatus === undefined ||
      value.tenantStatus === 'ACTIVE' ||
      value.tenantStatus === 'SUSPENDED')
  )
}
/** Cookie-bearing calls can never be redirected to a configurable external API/IdP. */
export function bffBase(
  value = import.meta.env.VITE_API_BASE_URL || '/api/v1',
): string {
  const url = new URL(value, window.location.origin)
  if (
    url.origin !== window.location.origin ||
    url.pathname.replace(/\/$/, '') !== '/api/v1' ||
    url.search ||
    url.hash ||
    url.username ||
    url.password
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return '/api/v1'
}
export async function sessionRequest(
  path: string,
  body?: unknown,
  options: {
    signal?: AbortSignal | undefined
    idempotencyKey?: string
    method?: 'PATCH'
  } = {},
): Promise<unknown> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    headers['X-CSRF-TOKEN'] = (await getCsrf()).token
  }
  if (options.idempotencyKey)
    headers['Idempotency-Key'] = options.idempotencyKey
  let response: Response
  try {
    response = await fetch(`${bffBase()}${path}`, {
      method: body === undefined ? 'GET' : (options.method ?? 'POST'),
      credentials: 'same-origin',
      redirect: 'error',
      cache: 'no-store',
      referrerPolicy: 'no-referrer',
      signal: options.signal ?? null,
      headers,
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
  } catch {
    throw new ApiClientError('NETWORK_ERROR')
  }
  if (!response.ok) {
    let problem: unknown
    try {
      if (
        response.headers.get('Content-Type')?.split(';')[0]?.trim() ===
        'application/problem+json'
      )
        problem = await response.json()
    } catch {
      /* A proxy error need not be JSON. */
    }
    throw decodeApiProblem(problem, response)
  }
  if (response.status === 204) return undefined
  if (
    response.headers.get('Content-Type')?.split(';')[0]?.trim() !==
    'application/json'
  )
    throw new ApiClientError('INVALID_RESPONSE', response.status)
  try {
    return await response.json()
  } catch {
    throw new ApiClientError('INVALID_RESPONSE')
  }
}
const request = sessionRequest
export async function getCsrf(): Promise<Csrf> {
  const value = await request('/auth/csrf')
  if (
    !object(value) ||
    value.headerName !== 'X-CSRF-TOKEN' ||
    typeof value.token !== 'string' ||
    value.token.length < 32
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return { headerName: 'X-CSRF-TOKEN', token: value.token }
}
export async function authStatus(): Promise<boolean> {
  const value = await request('/auth/status')
  if (!object(value) || typeof value.loginAvailable !== 'boolean')
    throw new ApiClientError('INVALID_RESPONSE')
  return value.loginAvailable
}
export async function currentIdentity(): Promise<CurrentIdentity> {
  const value = await request('/me')
  if (
    !object(value) ||
    typeof value.userId !== 'string' ||
    !uuid.test(value.userId) ||
    !Array.isArray(value.memberships) ||
    !value.memberships.every(membership) ||
    typeof value.privilegedMfaVerified !== 'boolean'
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return {
    userId: value.userId,
    memberships: value.memberships,
    privilegedMfaVerified: value.privilegedMfaVerified,
  }
}
export async function authorizeTenant(tenantId: string): Promise<Membership> {
  const value = await request('/auth/tenant', { tenantId })
  if (!membership(value) || value.tenantId !== tenantId)
    throw new ApiClientError('INVALID_RESPONSE')
  return value
}
export async function logout(): Promise<void> {
  await request('/auth/logout', {})
}
