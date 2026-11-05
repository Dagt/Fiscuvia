import { act, renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import type { PropsWithChildren } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../../shared/api/session-client'
import { ApiClientError } from '../../shared/api/client'
import { useAuthorizedSession } from './useAuthorizedSession'

vi.mock('../../shared/api/session-client', () => ({
  currentIdentity: vi.fn(),
  authorizeTenant: vi.fn(),
  logout: vi.fn(),
}))
const first: api.Membership = {
  tenantId: '00000000-0000-4000-8000-000000000001',
  membershipId: '00000000-0000-4000-8000-000000000011',
  role: 'ACCOUNTANT',
}
const second: api.Membership = {
  tenantId: '00000000-0000-4000-8000-000000000002',
  membershipId: '00000000-0000-4000-8000-000000000012',
  role: 'ASSISTANT',
}
const identity = {
  userId: '00000000-0000-4000-8000-000000000021',
  memberships: [first, second],
  privilegedMfaVerified: false,
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.currentIdentity).mockResolvedValue(identity)
})
async function setup() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  function Wrapper({ children }: PropsWithChildren) {
    return (
      <QueryClientProvider client={client}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    )
  }
  const hook = renderHook(useAuthorizedSession, { wrapper: Wrapper })
  await waitFor(() => expect(hook.result.current.identity.isSuccess).toBe(true))
  return { ...hook, client }
}
describe('contexto visual autorizado y cache', () => {
  it('muestra el nombre fresco del servidor sin conservar el nombre previo', async () => {
    vi.mocked(api.authorizeTenant).mockResolvedValue({
      ...first,
      commercialName: 'Anterior DEMO',
    })
    const { result, client } = await setup()
    vi.mocked(api.currentIdentity).mockResolvedValue({
      ...identity,
      memberships: [{ ...first, commercialName: 'Actual DEMO' }],
    })
    await act(async () => {
      await result.current.choose(first.tenantId)
    })
    expect(result.current.selected?.commercialName).toBe('Actual DEMO')
    client.setQueryData(['firm-settings', identity.userId, first.tenantId], {
      commercialName: 'Actual DEMO',
    })
    await act(async () => {
      client.setQueryData(['current-identity'], {
        ...identity,
        memberships: [second],
      })
    })
    await waitFor(() => expect(result.current.selected).toBeNull())
    await waitFor(() =>
      expect(
        client.getQueryData(['firm-settings', identity.userId, first.tenantId]),
      ).toBeUndefined(),
    )
  })
  it('un despacho suspendido nunca obtiene contexto operativo', async () => {
    const { result, client } = await setup()
    await act(async () => {
      client.setQueryData(['current-identity'], {
        ...identity,
        memberships: [{ ...first, tenantStatus: 'SUSPENDED' }],
      })
    })
    await waitFor(() =>
      expect(result.current.identity.data?.memberships[0]?.tenantStatus).toBe(
        'SUSPENDED',
      ),
    )
    await act(async () => {
      await result.current.choose(first.tenantId)
    })
    expect(api.authorizeTenant).not.toHaveBeenCalled()
    expect(result.current.selected).toBeNull()
  })
  it('no selecciona una membresía sin la respuesta del servidor', async () => {
    let resolve!: (member: api.Membership) => void
    vi.mocked(api.authorizeTenant).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const { result } = await setup()
    let pending!: Promise<void>
    act(() => {
      pending = result.current.choose(first.tenantId)
    })
    await waitFor(() => expect(api.authorizeTenant).toHaveBeenCalledOnce())
    expect(result.current.selected).toBeNull()
    await act(async () => {
      resolve(first)
      await pending
    })
    expect(result.current.selected).toEqual(first)
  })
  it('elimina resultados del despacho anterior antes de cambiar', async () => {
    vi.mocked(api.authorizeTenant).mockResolvedValue(first)
    const { result, client } = await setup()
    client.setQueryData(
      ['protected-resources', first.tenantId],
      ['PRIVATE-CANARY'],
    )
    await act(async () => {
      await result.current.choose(first.tenantId)
    })
    expect(
      client.getQueryData(['protected-resources', first.tenantId]),
    ).toBeUndefined()
    client.setQueryData(
      ['protected-resources', first.tenantId],
      ['PRIVATE-CANARY'],
    )
    vi.mocked(api.authorizeTenant).mockResolvedValue(second)
    await act(async () => {
      await result.current.choose(second.tenantId)
    })
    expect(result.current.selected).toEqual(second)
    expect(
      client.getQueryData(['protected-resources', first.tenantId]),
    ).toBeUndefined()
  })
  it('ignora respuestas tardías después de cancelar la selección', async () => {
    let resolve!: (member: api.Membership) => void
    vi.mocked(api.authorizeTenant).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const { result } = await setup()
    let pending!: Promise<void>
    act(() => {
      pending = result.current.choose(first.tenantId)
    })
    await waitFor(() => expect(api.authorizeTenant).toHaveBeenCalledOnce())
    await act(async () => {
      await result.current.change()
    })
    await act(async () => {
      resolve(first)
      await pending
    })
    expect(result.current.selected).toBeNull()
  })
  it('no acepta UUIDs que no estén en las membresías recibidas', async () => {
    const { result } = await setup()
    await act(async () => {
      await result.current.choose('00000000-0000-4000-8000-000000000099')
    })
    expect(api.authorizeTenant).not.toHaveBeenCalled()
    expect(result.current.selected).toBeNull()
  })
  it('descarta autorización si la membresía fue revocada durante la solicitud', async () => {
    const { result } = await setup()
    vi.mocked(api.authorizeTenant).mockResolvedValue(first)
    vi.mocked(api.currentIdentity).mockResolvedValue({
      ...identity,
      memberships: [second],
    })
    await act(async () => {
      await result.current.choose(first.tenantId)
    })
    expect(result.current.selected).toBeNull()
    expect(result.current.denied).toBe(true)
  })
  it('descarta una identidad distinta aunque coincida el tenant', async () => {
    const { result } = await setup()
    vi.mocked(api.authorizeTenant).mockResolvedValue(first)
    vi.mocked(api.currentIdentity).mockResolvedValue({
      ...identity,
      userId: '00000000-0000-4000-8000-000000000022',
    })
    await act(async () => {
      await result.current.choose(first.tenantId)
    })
    expect(result.current.selected).toBeNull()
    expect(result.current.denied).toBe(true)
  })
  it('retira el contexto anterior cuando el servidor deniega el cambio', async () => {
    vi.mocked(api.authorizeTenant).mockResolvedValue(first)
    const { result } = await setup()
    await act(async () => {
      await result.current.choose(first.tenantId)
    })
    vi.mocked(api.authorizeTenant).mockRejectedValue(
      new ApiClientError('ACCESS_DENIED', 403),
    )
    await act(async () => {
      await result.current.choose(second.tenantId)
    })
    expect(result.current.selected).toBeNull()
    expect(result.current.denied).toBe(true)
  })
})
