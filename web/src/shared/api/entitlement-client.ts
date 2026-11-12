import type { components } from './schema'
import { ApiClientError } from './client'
import { sessionRequest } from './session-client'
export type EntitlementUsage = components['schemas']['EntitlementUsage']
export const resources = [
  'USERS',
  'TAXPAYERS',
  'STORAGE_BYTES',
  'JOBS_RUNNING',
  'JOBS_PENDING',
  'JOBS_PER_PERIOD',
] as const
export async function tenantUsage(
  tenant: string,
  signal?: AbortSignal,
): Promise<EntitlementUsage[]> {
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
      tenant,
    )
  )
    throw new ApiClientError('INVALID_RESPONSE')
  const value = await sessionRequest(
    `/tenants/${tenant}/entitlements`,
    undefined,
    { signal },
  )
  if (
    !Array.isArray(value) ||
    value.length !== resources.length ||
    new Set(value.map((v) => v?.resource)).size !== resources.length ||
    !value.every((v) => {
      if (
        !v ||
        typeof v !== 'object' ||
        Object.keys(v).length !== 10 ||
        !resources.includes(v.resource)
      )
        return false
      if (
        !['limit', 'version', 'measured', 'reserved', 'available'].every(
          (k) => v[k] === null || (Number.isSafeInteger(v[k]) && v[k] >= 0),
        )
      )
        return false
      if (
        !['validFrom', 'validUntil'].every(
          (k) =>
            v[k] === null ||
            (typeof v[k] === 'string' && Number.isFinite(Date.parse(v[k]))),
        )
      )
        return false
      if (v.overLimit !== null && typeof v.overLimit !== 'boolean') return false
      if (!['USERS', 'TAXPAYERS'].includes(v.resource))
        return (
          v.integration === 'NOT_INTEGRATED' &&
          v.measured === null &&
          v.reserved === null &&
          v.available === null &&
          v.overLimit === null
        )
      return (
        v.integration === 'MEASURED' &&
        v.measured !== null &&
        v.reserved !== null &&
        (v.resource !== 'TAXPAYERS' || v.reserved === 0)
      )
    })
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return value as EntitlementUsage[]
}
