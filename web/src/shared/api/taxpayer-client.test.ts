import { beforeEach, expect, it, vi } from 'vitest'
import { sessionRequest } from './session-client'
import {
  listTaxpayers,
  taxpayerPresentation,
  taxpayerDetail,
  taxpayerCommand,
  type TaxpayerQuery,
} from './taxpayer-client'
vi.mock('./session-client', () => ({ sessionRequest: vi.fn() }))
const t = '00000000-0000-4000-8000-000000000001'
const row = {
  id: t,
  rfc: 'DEM000101001',
  personType: 'LEGAL_ENTITY',
  legalName: 'DEMO',
  tradeName: null,
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-11-13T00:00:00Z',
  updatedAt: '2026-11-13T00:00:00Z',
  regimes: [],
  rfcValidation: 'RFC_LOCAL_STRUCTURE_V1',
  officialVerification: 'NOT_PERFORMED',
}
const query: TaxpayerQuery = {
  page: 0,
  size: 20,
  search: '',
  status: 'ACTIVE',
  sort: 'legalName,asc',
  regime: '',
}
const hints = {
  canCreate: true,
  createBlock: 'NONE',
  canEdit: false,
  canArchive: false,
  canRestore: false,
  version: null,
}
beforeEach(() => vi.resetAllMocks())
it('envía búsqueda y filtro al servidor con cancelación y sin total inventado', async () => {
  vi.mocked(sessionRequest).mockResolvedValue({
    items: [row],
    page: 0,
    size: 20,
    hasNext: true,
  })
  const signal = new AbortController().signal
  expect(
    (
      await listTaxpayers(
        t,
        { ...query, search: 'DEMO & RFC', regime: '000' },
        signal,
      )
    ).hasNext,
  ).toBe(true)
  expect(sessionRequest).toHaveBeenCalledWith(
    `/tenants/${t}/taxpayers?page=0&size=20&search=DEMO+%26+RFC&status=ACTIVE&sort=legalName%2Casc&regime=000`,
    undefined,
    { signal },
  )
})
it('rechaza respuestas abiertas, conteos no autorizados y supuesta verificación SAT', async () => {
  for (const response of [
    { items: [row], page: 0, size: 20, hasNext: false, total: 1 },
    {
      items: [{ ...row, officialVerification: 'VERIFIED' }],
      page: 0,
      size: 20,
      hasNext: false,
    },
  ]) {
    vi.mocked(sessionRequest).mockResolvedValue(response)
    await expect(listTaxpayers(t, query)).rejects.toThrow()
  }
})
it('valida whitelist, límites, régimen y tenant antes de enviar cookies', async () => {
  for (const q of [
    { ...query, sort: 'rfc,asc' },
    { ...query, page: 10001 },
    { ...query, regime: '0%' },
    { ...query, size: 101 },
  ])
    await expect(listTaxpayers(t, q as TaxpayerQuery)).rejects.toThrow()
  await expect(listTaxpayers('foreign/path', query)).rejects.toThrow()
  expect(sessionRequest).not.toHaveBeenCalled()
})
it('exige señales cerradas y no convierte el rol en autoridad', async () => {
  vi.mocked(sessionRequest).mockResolvedValue(hints)
  expect(await taxpayerPresentation(t)).toEqual(hints)
  vi.mocked(sessionRequest).mockResolvedValue({ ...hints, globalAccess: true })
  await expect(taxpayerPresentation(t)).rejects.toThrow()
})
it('no mezcla versiones de detalle y permisos obtenidos concurrentemente', async () => {
  vi.mocked(sessionRequest).mockImplementation(async (path) =>
    path.endsWith('/presentation') ? { ...hints, version: 1 } : row,
  )
  await expect(taxpayerDetail(t, t)).rejects.toMatchObject({
    code: 'STALE_VERSION',
    status: 409,
  })
})
it('edita con PATCH, CSRF compartido, versión e identificador idempotente exactos', async () => {
  const body = {
    expectedVersion: 0,
    legalName: 'DEMO nuevo',
    tradeName: null,
    regimes: [],
  }
  vi.mocked(sessionRequest).mockResolvedValue({
    targetId: t,
    version: 1,
    replayed: true,
  })
  expect(
    (await taxpayerCommand(t, { action: 'edit', id: t, body }, t)).replayed,
  ).toBe(true)
  expect(sessionRequest).toHaveBeenCalledWith(
    `/tenants/${t}/taxpayers/${t}`,
    body,
    { idempotencyKey: t, signal: undefined, method: 'PATCH' },
  )
})
it.each(['archive', 'restore'] as const)(
  'usa comando %s cerrado y rechaza target ajeno',
  async (action) => {
    const body = {
      expectedVersion: 0,
      reasonCode:
        action === 'archive' ? ('OTHER' as const) : ('CORRECTION' as const),
    }
    vi.mocked(sessionRequest).mockResolvedValue({
      targetId: '00000000-0000-4000-8000-000000000002',
      version: 1,
      replayed: false,
    })
    await expect(
      taxpayerCommand(
        t,
        { action, id: t, body } as Parameters<typeof taxpayerCommand>[1],
        t,
      ),
    ).rejects.toThrow()
  },
)
