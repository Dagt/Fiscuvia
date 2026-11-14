import type { components } from './schema'
import { ApiClientError } from './client'
import { sessionRequest } from './session-client'

export type Taxpayer = components['schemas']['Taxpayer']
export type TaxpayerPage = components['schemas']['TaxpayerPage']
export type CreateTaxpayer = components['schemas']['CreateTaxpayer']
export type Profile = components['schemas']['ReplaceProfile']
export type Presentation = components['schemas']['TaxpayerPresentation']
export type Receipt = components['schemas']['CommandReceipt']
export type RegimeInput = components['schemas']['RegimeInput']
export type TaxpayerQuery = {
  search: string
  status: 'ACTIVE' | 'ARCHIVED'
  regime: string
  page: number
  size: number
  sort:
    | 'id,asc'
    | 'id,desc'
    | 'legalName,asc'
    | 'legalName,desc'
    | 'createdAt,asc'
    | 'createdAt,desc'
}
export type Command =
  | { action: 'create'; body: CreateTaxpayer }
  | { action: 'edit'; id: string; body: Profile }
  | {
      action: 'archive'
      id: string
      body: components['schemas']['ArchiveTaxpayer']
    }
  | {
      action: 'restore'
      id: string
      body: components['schemas']['RestoreTaxpayer']
    }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const object = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)
const integer = (v: unknown): v is number =>
  typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
const date = (v: unknown) =>
  typeof v === 'string' && Number.isFinite(Date.parse(v))
const exact = (v: Record<string, unknown>, keys: string[]) =>
  Object.keys(v).length === keys.length &&
  keys.every((k) => Object.hasOwn(v, k))
function id(v: string) {
  if (!uuid.test(v)) throw new ApiClientError('INVALID_RESPONSE')
  return v
}
function base(tenant: string) {
  return `/tenants/${id(tenant)}/taxpayers`
}
function taxpayer(v: unknown): v is Taxpayer {
  return (
    object(v) &&
    exact(v, [
      'id',
      'rfc',
      'personType',
      'legalName',
      'tradeName',
      'status',
      'version',
      'createdAt',
      'updatedAt',
      'regimes',
      'rfcValidation',
      'officialVerification',
    ]) &&
    typeof v.id === 'string' &&
    uuid.test(v.id) &&
    typeof v.rfc === 'string' &&
    /^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$/.test(v.rfc) &&
    ['INDIVIDUAL', 'LEGAL_ENTITY'].includes(String(v.personType)) &&
    typeof v.legalName === 'string' &&
    [...v.legalName].length <= 300 &&
    (v.tradeName === null ||
      (typeof v.tradeName === 'string' && [...v.tradeName].length <= 200)) &&
    ['ACTIVE', 'ARCHIVED'].includes(String(v.status)) &&
    integer(v.version) &&
    date(v.createdAt) &&
    date(v.updatedAt) &&
    v.rfcValidation === 'RFC_LOCAL_STRUCTURE_V1' &&
    v.officialVerification === 'NOT_PERFORMED' &&
    Array.isArray(v.regimes) &&
    v.regimes.length <= 10 &&
    v.regimes.every(
      (r) =>
        object(r) &&
        exact(r, ['code', 'validFrom', 'validTo', 'source', 'reviewStatus']) &&
        /^[0-9]{3}$/.test(String(r.code)) &&
        (r.validFrom === null || date(r.validFrom)) &&
        (r.validTo === null || date(r.validTo)) &&
        r.source === 'MANUAL' &&
        r.reviewStatus === 'PENDING_REVIEW',
    )
  )
}
export async function listTaxpayers(
  tenant: string,
  query: TaxpayerQuery,
  signal?: AbortSignal,
): Promise<TaxpayerPage> {
  if (
    !integer(query.page) ||
    query.page > 10000 ||
    !integer(query.size) ||
    query.size < 1 ||
    query.size > 100 ||
    [...query.search].length > 80 ||
    !['ACTIVE', 'ARCHIVED'].includes(query.status) ||
    !/^$|^[0-9]{3}$/.test(query.regime) ||
    ![
      'id,asc',
      'id,desc',
      'legalName,asc',
      'legalName,desc',
      'createdAt,asc',
      'createdAt,desc',
    ].includes(query.sort)
  )
    throw new ApiClientError('INVALID_PAGINATION', 400)
  const params = new URLSearchParams(
    Object.entries(query).map(([k, v]) => [k, String(v)]),
  )
  const v = await sessionRequest(`${base(tenant)}?${params}`, undefined, {
    signal,
  })
  if (
    !object(v) ||
    !exact(v, ['items', 'page', 'size', 'hasNext']) ||
    v.page !== query.page ||
    v.size !== query.size ||
    typeof v.hasNext !== 'boolean' ||
    !Array.isArray(v.items) ||
    v.items.length > query.size ||
    !v.items.every(taxpayer)
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return v as TaxpayerPage
}
export async function taxpayerPresentation(
  tenant: string,
  taxpayerId?: string,
  signal?: AbortSignal,
): Promise<Presentation> {
  const v = await sessionRequest(
    `${base(tenant)}${taxpayerId ? '/' + id(taxpayerId) : ''}/presentation`,
    undefined,
    { signal },
  )
  if (
    !object(v) ||
    !exact(v, [
      'canCreate',
      'createBlock',
      'canEdit',
      'canArchive',
      'canRestore',
      'version',
    ]) ||
    !['canCreate', 'canEdit', 'canArchive', 'canRestore'].every(
      (k) => typeof v[k] === 'boolean',
    ) ||
    !['NONE', 'NOT_AUTHORIZED', 'MFA_REQUIRED', 'QUOTA_UNAVAILABLE'].includes(
      String(v.createBlock),
    ) ||
    !(v.version === null || integer(v.version))
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return v as Presentation
}
export async function taxpayerDetail(
  tenant: string,
  taxpayerId: string,
  signal?: AbortSignal,
) {
  const [v, actions] = await Promise.all([
    sessionRequest(`${base(tenant)}/${id(taxpayerId)}`, undefined, { signal }),
    taxpayerPresentation(tenant, taxpayerId, signal),
  ])
  if (!taxpayer(v)) throw new ApiClientError('INVALID_RESPONSE')
  if (v.version !== actions.version)
    throw new ApiClientError('STALE_VERSION', 409)
  return { taxpayer: v, actions }
}
export async function taxpayerCommand(
  tenant: string,
  command: Command,
  key: string,
  signal?: AbortSignal,
): Promise<Receipt> {
  id(key)
  const path =
    base(tenant) +
    (command.action === 'create'
      ? ''
      : '/' +
        id(command.id) +
        (command.action === 'archive'
          ? '/archivals'
          : command.action === 'restore'
            ? '/restorations'
            : ''))
  const v = await sessionRequest(path, command.body, {
    idempotencyKey: key,
    signal,
    ...(command.action === 'edit' ? { method: 'PATCH' as const } : {}),
  })
  if (
    !object(v) ||
    !exact(v, ['targetId', 'version', 'replayed']) ||
    typeof v.targetId !== 'string' ||
    !uuid.test(v.targetId) ||
    !integer(v.version) ||
    typeof v.replayed !== 'boolean' ||
    v.version !==
      (command.action === 'create' ? 0 : command.body.expectedVersion + 1) ||
    (command.action !== 'create' && v.targetId !== command.id)
  )
    throw new ApiClientError('INVALID_RESPONSE')
  return v as Receipt
}
