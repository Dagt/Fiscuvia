import type { components } from './schema'
import { ApiClientError } from './client'
import { sessionRequest } from './session-client'

export type TeamMember = components['schemas']['TeamMember']
export type TeamInvitation = components['schemas']['TeamInvitation']
export type TeamMembersPage = components['schemas']['TeamMembersPage']
export type TeamInvitationsPage = components['schemas']['TeamInvitationsPage']
export type TeamStatus = components['schemas']['TeamStatusCommand']['status']
export type TeamRole = components['schemas']['TeamRoleCommand']['role']
type Result = components['schemas']['TeamCommandResult']
export type TeamQuery = {
  page: number
  size: number
  search: string
  status: string
}
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const roles = ['FIRM_OWNER_ADMIN', 'FIRM_SUPERVISOR', 'ACCOUNTANT', 'ASSISTANT']
const states = ['ACTIVE', 'SUSPENDED', 'REVOKED']
const invitationStates = ['PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED']
const object = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)
const exact = (v: Record<string, unknown>, fields: string[]) =>
  Object.keys(v).length === fields.length &&
  fields.every((k) => Object.hasOwn(v, k))
const integer = (
  v: unknown,
  min = 0,
  max = Number.MAX_SAFE_INTEGER,
): v is number =>
  typeof v === 'number' && Number.isSafeInteger(v) && v >= min && v <= max
const date = (v: unknown) =>
  typeof v === 'string' && Number.isFinite(Date.parse(v))
const one = (v: unknown, values: string[]) =>
  typeof v === 'string' && values.includes(v)
const controls = (value: string) =>
  Array.from(value).some((c) => {
    const cp = c.codePointAt(0)!
    return cp < 32 || cp === 127
  })
const text = (v: unknown, max: number) =>
  v === null || (typeof v === 'string' && v.length <= max && !controls(v))
function id(v: unknown): v is string {
  return typeof v === 'string' && uuid.test(v)
}
function base(tenant: string) {
  if (!id(tenant)) throw new ApiClientError('INVALID_RESPONSE')
  return `/tenants/${tenant}/team`
}
function member(v: unknown): v is TeamMember {
  return (
    object(v) &&
    exact(v, [
      'membershipId',
      'displayName',
      'email',
      'roles',
      'status',
      'identityStatus',
      'version',
      'joinedAt',
      'changedAt',
      'canManage',
    ]) &&
    id(v.membershipId) &&
    text(v.displayName, 160) &&
    text(v.email, 254) &&
    Array.isArray(v.roles) &&
    v.roles.length > 0 &&
    v.roles.length <= 4 &&
    v.roles.every((r) => one(r, roles)) &&
    new Set(v.roles).size === v.roles.length &&
    one(v.status, states) &&
    one(v.identityStatus, ['ACTIVE', 'DISABLED']) &&
    integer(v.version) &&
    date(v.joinedAt) &&
    date(v.changedAt) &&
    typeof v.canManage === 'boolean' &&
    (!v.roles.includes('FIRM_OWNER_ADMIN') || !v.canManage)
  )
}
function invitation(v: unknown): v is TeamInvitation {
  return (
    object(v) &&
    exact(v, [
      'invitationId',
      'displayName',
      'email',
      'role',
      'status',
      'version',
      'createdAt',
      'expiresAt',
    ]) &&
    id(v.invitationId) &&
    text(v.displayName, 160) &&
    text(v.email, 254) &&
    one(v.role, roles.slice(1)) &&
    one(v.status, invitationStates) &&
    integer(v.version) &&
    date(v.createdAt) &&
    date(v.expiresAt)
  )
}
async function list<T>(
  tenant: string,
  area: string,
  query: TeamQuery,
  check: (v: unknown) => v is T,
  signal?: AbortSignal,
) {
  if (
    !integer(query.page, 0, 200) ||
    !integer(query.size, 1, 50) ||
    query.search.length > 80 ||
    controls(query.search) ||
    !['ALL', ...(area === 'members' ? states : invitationStates)].includes(
      query.status,
    )
  )
    throw new ApiClientError('INVALID_RESPONSE')
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
    search: query.search,
    status: query.status,
  })
  const v = await sessionRequest(
    `${base(tenant)}/${area}?${params}`,
    undefined,
    { signal },
  )
  if (
    !object(v) ||
    !exact(v, ['items', 'page', 'size', 'total']) ||
    !Array.isArray(v.items) ||
    v.items.length > query.size ||
    !v.items.every(check) ||
    v.page !== query.page ||
    v.size !== query.size ||
    !integer(v.total) ||
    v.items.length > v.total
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return {
    items: v.items as T[],
    page: query.page,
    size: query.size,
    total: v.total,
  }
}
export const teamMembers = (
  tenant: string,
  query: TeamQuery,
  signal?: AbortSignal,
): Promise<TeamMembersPage> => list(tenant, 'members', query, member, signal)
export const teamInvitations = (
  tenant: string,
  query: TeamQuery,
  signal?: AbortSignal,
): Promise<TeamInvitationsPage> =>
  list(tenant, 'invitations', query, invitation, signal)
export type TeamMutation =
  | {
      target: string
      kind: 'status'
      status: TeamStatus
      expectedVersion: number
    }
  | { target: string; kind: 'role'; role: TeamRole; expectedVersion: number }
  | { target: string; kind: 'revoke'; expectedVersion: number }
export async function mutateTeam(
  tenant: string,
  action: TeamMutation,
  key: string,
  signal?: AbortSignal,
): Promise<Result> {
  if (!id(action.target) || !id(key) || !integer(action.expectedVersion))
    throw new ApiClientError('INVALID_RESPONSE')
  const path =
    action.kind === 'revoke'
      ? `invitations/${action.target}/revoke`
      : `members/${action.target}/${action.kind}`
  const body = {
    expectedVersion: action.expectedVersion,
    ...(action.kind === 'role'
      ? { role: action.role }
      : action.kind === 'status'
        ? { status: action.status }
        : {}),
  }
  const v = await sessionRequest(`${base(tenant)}/${path}`, body, {
    signal,
    idempotencyKey: key,
  })
  if (
    !object(v) ||
    !exact(v, ['targetId', 'version', 'replayed']) ||
    v.targetId !== action.target ||
    !integer(v.version) ||
    v.version !== action.expectedVersion + 1 ||
    typeof v.replayed !== 'boolean'
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return { targetId: action.target, version: v.version, replayed: v.replayed }
}
