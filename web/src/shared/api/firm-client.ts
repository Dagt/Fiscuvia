import type { components } from './schema'
import { ApiClientError } from './client'
import { sessionRequest } from './session-client'

export type FirmSettings = components['schemas']['FirmSettings']
export type UpdateFirmSettings = components['schemas']['UpdateFirmSettings']
export const firmTimeZones = [
  'America/Mexico_City',
  'America/Cancun',
  'America/Chihuahua',
  'America/Ciudad_Juarez',
  'America/Hermosillo',
  'America/Mazatlan',
  'America/Monterrey',
  'America/Tijuana',
] as const satisfies readonly components['schemas']['FirmTimeZone'][]
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function path(tenant: string) {
  if (!uuid.test(tenant)) throw new ApiClientError('INVALID_RESPONSE')
  return `/tenants/${tenant}/settings`
}
export async function firmSettings(
  tenant: string,
  signal?: AbortSignal,
): Promise<FirmSettings> {
  const value = await sessionRequest(path(tenant), undefined, { signal })
  if (typeof value !== 'object' || value === null)
    throw new ApiClientError('INVALID_RESPONSE')
  const profile = value as Record<string, unknown>
  if (
    Object.keys(profile).length !== 11 ||
    !['commercialName', 'contactEmail', 'phone'].every(
      (k) =>
        profile[k] === null ||
        (typeof profile[k] === 'string' &&
          profile[k].length <=
            (k === 'commercialName' ? 160 : k === 'contactEmail' ? 254 : 30)),
    ) ||
    typeof profile.version !== 'number' ||
    !Number.isSafeInteger(profile.version) ||
    profile.version < 0 ||
    !firmTimeZones.includes(
      profile.timeZone as (typeof firmTimeZones)[number],
    ) ||
    profile.locale !== 'es-MX' ||
    !['ACTIVE', 'SUSPENDED'].includes(String(profile.status)) ||
    typeof profile.canManage !== 'boolean' ||
    profile.logoReference !== null ||
    !['createdAt', 'updatedAt'].every(
      (k) =>
        typeof profile[k] === 'string' &&
        Number.isFinite(Date.parse(profile[k])),
    )
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return value as FirmSettings
}
export async function updateFirmSettings(
  tenant: string,
  body: UpdateFirmSettings,
  command: string,
): Promise<number> {
  if (!uuid.test(command)) throw new ApiClientError('INVALID_RESPONSE')
  const value = await sessionRequest(path(tenant), body, {
    idempotencyKey: command,
  })
  if (
    typeof value !== 'object' ||
    value === null ||
    !('version' in value) ||
    typeof value.version !== 'number' ||
    !Number.isSafeInteger(value.version) ||
    value.version < 0
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return value.version
}
