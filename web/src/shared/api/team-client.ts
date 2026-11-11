import type { components } from './schema'
import { ApiClientError } from './client'
import { sessionRequest } from './session-client'

export type Invitation = components['schemas']['LocalEmailInvitation']
export type InviteRequest = components['schemas']['LocalEmailInvitationRequest']
export type InviteOptions = components['schemas']['LocalInvitationOptions']
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
function base(tenant: string) {
  if (!uuid.test(tenant)) throw new ApiClientError('INVALID_RESPONSE')
  return `/tenants/${tenant}/team`
}
const object = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value)
export async function inviteOptions(
  tenant: string,
  signal?: AbortSignal,
): Promise<InviteOptions> {
  const v = await sessionRequest(
    `${base(tenant)}/invitation-options`,
    undefined,
    { signal },
  )
  if (
    !object(v) ||
    Object.keys(v).length !== 2 ||
    typeof v.emailInvitationsAvailable !== 'boolean' ||
    v.delivery !== 'MANUAL'
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return {
    emailInvitationsAvailable: v.emailInvitationsAvailable,
    delivery: v.delivery,
  }
}
export async function createEmailInvitation(
  tenant: string,
  data: InviteRequest,
  key: string,
  signal?: AbortSignal,
): Promise<Invitation> {
  const v = await sessionRequest(`${base(tenant)}/invitations`, data, {
    signal,
    idempotencyKey: key,
  })
  if (
    !object(v) ||
    Object.keys(v).length !== 5 ||
    typeof v.invitationId !== 'string' ||
    !uuid.test(v.invitationId) ||
    typeof v.expiresAt !== 'string' ||
    !Number.isFinite(Date.parse(v.expiresAt)) ||
    v.delivery !== 'MANUAL' ||
    typeof v.replayed !== 'boolean' ||
    (v.replayed
      ? v.token !== null
      : typeof v.token !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(v.token))
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return {
    invitationId: v.invitationId,
    token: v.token as string | null,
    expiresAt: v.expiresAt,
    delivery: v.delivery,
    replayed: v.replayed,
  }
}
export async function revokeCreatedInvitation(tenant: string, id: string) {
  base(tenant)
  if (!uuid.test(id)) throw new ApiClientError('INVALID_RESPONSE')
  await sessionRequest('/auth/invitations/revoke', {
    tenantId: tenant,
    invitationId: id,
  })
}
