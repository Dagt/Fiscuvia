import { beforeEach, expect, it, vi } from 'vitest'
import { teamMembers, teamInvitations, mutateTeam } from './team-admin-client'
import { sessionRequest } from './session-client'
vi.mock('./session-client', () => ({ sessionRequest: vi.fn() }))
const t = '00000000-0000-4000-8000-000000000001'
const row = {
  membershipId: t,
  displayName: 'Equipo DEMO',
  email: 'equipo@example.invalid',
  roles: ['ACCOUNTANT'],
  status: 'ACTIVE',
  identityStatus: 'ACTIVE',
  version: 0,
  joinedAt: '2026-11-11T20:00:00Z',
  changedAt: '2026-11-11T20:00:00Z',
  canManage: true,
}
const query = { page: 0, size: 20, search: '', status: 'ALL' }
const page = { items: [row], page: 0, size: 20, total: 1 }
beforeEach(() => vi.clearAllMocks())
it('consulta paginada conserva URL same-origin y búsqueda codificada', async () => {
  vi.mocked(sessionRequest).mockResolvedValue(page)
  expect(
    (await teamMembers(t, { ...query, search: 'demo@example.invalid' })).total,
  ).toBe(1)
  expect(sessionRequest).toHaveBeenCalledWith(
    `/tenants/${t}/team/members?page=0&size=20&search=demo%40example.invalid&status=ALL`,
    undefined,
    { signal: undefined },
  )
})
it('contrato cerrado rechaza roles desconocidos y datos extra privados', async () => {
  for (const item of [
    { ...row, roles: ['SUPERUSER'] },
    { ...row, userId: t },
    { ...row, roles: ['FIRM_OWNER_ADMIN'], canManage: true },
  ]) {
    vi.mocked(sessionRequest).mockResolvedValue({ ...page, items: [item] })
    await expect(teamMembers(t, query)).rejects.toThrow()
  }
})
it('resultados de comando se validan contra target y siguiente versión', async () => {
  vi.mocked(sessionRequest).mockResolvedValue({
    targetId: t,
    version: 1,
    replayed: true,
  })
  expect(
    (
      await mutateTeam(
        t,
        { kind: 'status', target: t, status: 'ACTIVE', expectedVersion: 0 },
        t,
      )
    ).replayed,
  ).toBe(true)
  expect(sessionRequest).toHaveBeenCalledWith(
    `/tenants/${t}/team/members/${t}/status`,
    { status: 'ACTIVE', expectedVersion: 0 },
    { idempotencyKey: t, signal: undefined },
  )
})
it('no acepta un comando con identidad ajena o versión inesperada en la respuesta', async () => {
  for (const reply of [
    { targetId: t, version: 2, replayed: false },
    { targetId: t, version: 1, replayed: false, token: 'private' },
    { targetId: 'invalid', version: 1, replayed: false },
  ]) {
    vi.mocked(sessionRequest).mockResolvedValue(reply)
    await expect(
      mutateTeam(
        t,
        { kind: 'role', target: t, role: 'ASSISTANT', expectedVersion: 0 },
        t,
      ),
    ).rejects.toThrow()
  }
})
it('UUID y paginación inválidos no producen solicitudes', async () => {
  for (const q of [
    { ...query, page: 201 },
    { ...query, size: 51 },
    { ...query, status: 'ADMIN' },
    { ...query, search: 'a'.repeat(81) },
  ])
    await expect(teamMembers(t, q)).rejects.toThrow()
  await expect(teamInvitations('../foreign', query)).rejects.toThrow()
  expect(sessionRequest).not.toHaveBeenCalled()
})
it('listado de invitaciones rechaza tokens o hashes incluso con datos válidos', async () => {
  const item = {
    invitationId: t,
    displayName: null,
    email: null,
    role: 'ACCOUNTANT',
    status: 'PENDING',
    version: 0,
    createdAt: row.joinedAt,
    expiresAt: row.joinedAt,
  }
  vi.mocked(sessionRequest).mockResolvedValue({ ...page, items: [item] })
  expect((await teamInvitations(t, query)).items[0]?.displayName).toBeNull()
  vi.mocked(sessionRequest).mockResolvedValue({
    ...page,
    items: [{ ...item, token_hash: 'forbidden' }],
  })
  await expect(teamInvitations(t, query)).rejects.toThrow()
})
it('abort y clave idempotente se conservan al revocar una invitación', async () => {
  const signal = new AbortController().signal
  vi.mocked(sessionRequest).mockResolvedValue({
    targetId: t,
    version: 1,
    replayed: false,
  })
  await mutateTeam(
    t,
    { kind: 'revoke', target: t, expectedVersion: 0 },
    t,
    signal,
  )
  expect(sessionRequest).toHaveBeenCalledWith(
    `/tenants/${t}/team/invitations/${t}/revoke`,
    { expectedVersion: 0 },
    { signal, idempotencyKey: t },
  )
})
