import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { NotificationProvider } from '../../shared/presentation/NotificationProvider'
import { ApiClientError } from '../../shared/api/client'
import * as api from '../../shared/api/team-admin-client'
import { TeamManagement } from './TeamManagement'
vi.mock('../../shared/api/team-admin-client', () => ({
  teamMembers: vi.fn(),
  teamInvitations: vi.fn(),
  mutateTeam: vi.fn(),
}))
const t = '00000000-0000-4000-8000-000000000001',
  m = '00000000-0000-4000-8000-000000000002'
const row: api.TeamMember = {
  membershipId: m,
  displayName: 'Persona DEMO',
  email: 'demo@example.invalid',
  roles: ['ACCOUNTANT'],
  status: 'ACTIVE',
  identityStatus: 'ACTIVE',
  version: 0,
  joinedAt: '2026-11-11T20:00:00Z',
  changedAt: '2026-11-11T20:00:00Z',
  canManage: true,
}
const page = { items: [row], page: 0, size: 20, total: 1 }
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.teamMembers).mockResolvedValue(page)
  vi.mocked(api.teamInvitations).mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    total: 0,
  })
  vi.mocked(api.mutateTeam).mockResolvedValue({
    targetId: m,
    version: 1,
    replayed: false,
  })
})
function view() {
  const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    }),
    unavailable = vi.fn(),
    usage = vi.fn()
  const node = (tenant: string) => (
    <QueryClientProvider client={client}>
      <NotificationProvider>
        <TeamManagement
          userId={t}
          tenantId={tenant}
          onUnavailable={unavailable}
          onUsage={usage}
        />
      </NotificationProvider>
    </QueryClientProvider>
  )
  const rendered = render(node(t))
  return {
    ...rendered,
    client,
    unavailable,
    usage,
    switchTenant: () => rendered.rerender(node(m)),
  }
}
it('renderiza contactos, rol y estado autorizados por backend', async () => {
  view()
  await screen.findByText('demo@example.invalid')
  expect(screen.getByText('Persona DEMO')).toBeInTheDocument()
  expect(
    screen.getByText('Activa', { selector: '.status-pill' }),
  ).toBeInTheDocument()
  expect(api.teamMembers).toHaveBeenCalledWith(
    t,
    { page: 0, size: 20, search: '', status: 'ALL' },
    expect.any(AbortSignal),
  )
})
it('loading no presenta filas antiguas', async () => {
  vi.mocked(api.teamMembers).mockReturnValue(new Promise(() => {}))
  view()
  expect(
    await screen.findByText('Consultando equipo autorizado'),
  ).toBeInTheDocument()
  expect(screen.queryByText('demo@example.invalid')).not.toBeInTheDocument()
})
it('error recuperable permite reintentar sin mostrar datos', async () => {
  vi.mocked(api.teamMembers).mockRejectedValueOnce(
    new ApiClientError('NETWORK_ERROR'),
  )
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: /Reintentar/ }),
  )
  await screen.findByText('demo@example.invalid')
  expect(api.teamMembers).toHaveBeenCalledTimes(2)
})
it('403 retira contactos y muestra autorización insuficiente', async () => {
  vi.mocked(api.teamMembers).mockRejectedValue(
    new ApiClientError('ACCESS_DENIED', 403),
  )
  view()
  await screen.findByText('Sin permiso para administrar el equipo')
  expect(screen.queryByText('demo@example.invalid')).not.toBeInTheDocument()
  expect(api.mutateTeam).not.toHaveBeenCalled()
})
it('una lista realmente vacía presenta el estado vacío del sistema', async () => {
  vi.mocked(api.teamMembers).mockResolvedValue({ ...page, items: [], total: 0 })
  view()
  expect(await screen.findByText('No hay resultados')).toBeInTheDocument()
})
it('filtros se envían al backend y reinician la página', async () => {
  view()
  await screen.findByText('demo@example.invalid')
  await userEvent.selectOptions(screen.getByLabelText('Estado'), 'SUSPENDED')
  await waitFor(() =>
    expect(api.teamMembers).toHaveBeenLastCalledWith(
      t,
      { page: 0, size: 20, search: '', status: 'SUSPENDED' },
      expect.any(AbortSignal),
    ),
  )
})
it('paginación solicita sólo la siguiente página del servidor', async () => {
  const items = Array.from({ length: 20 }, (_, i) => ({
    ...row,
    membershipId: `00000000-0000-4000-8000-${String(i + 2).padStart(12, '0')}`,
    email: `demo-${i}@example.invalid`,
  }))
  vi.mocked(api.teamMembers).mockResolvedValue({ ...page, items, total: 21 })
  view()
  await screen.findByText('demo-0@example.invalid')
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await waitFor(() =>
    expect(api.teamMembers).toHaveBeenLastCalledWith(
      t,
      { page: 1, size: 20, search: '', status: 'ALL' },
      expect.any(AbortSignal),
    ),
  )
})
it('cambio de rol exige confirmación y versión del registro', async () => {
  view()
  await screen.findByText('demo@example.invalid')
  await userEvent.click(
    await screen.findByRole('button', { name: 'Administrar' }),
  )
  await userEvent.selectOptions(
    screen.getByLabelText('Nuevo rol de demo@example.invalid'),
    'ASSISTANT',
  )
  await userEvent.click(screen.getByRole('button', { name: 'Cambiar rol' }))
  expect(api.mutateTeam).not.toHaveBeenCalled()
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', {
      name: 'Cambiar rol',
    }),
  )
  await waitFor(() =>
    expect(api.mutateTeam).toHaveBeenCalledWith(
      t,
      { kind: 'role', target: m, expectedVersion: 0, role: 'ASSISTANT' },
      expect.any(String),
      expect.any(AbortSignal),
    ),
  )
  await screen.findByText('Cambio confirmado por el servidor.')
})
it('cuota agotada no produce notificación de éxito', async () => {
  vi.mocked(api.teamMembers).mockResolvedValue({
    ...page,
    items: [{ ...row, status: 'SUSPENDED' }],
  })
  vi.mocked(api.mutateTeam).mockRejectedValue(
    new ApiClientError('QUOTA_UNAVAILABLE', 409),
  )
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: 'Administrar' }),
  )
  await userEvent.click(
    await screen.findByRole('button', { name: 'Reactivar' }),
  )
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', {
      name: 'Reactivar',
    }),
  )
  await within(screen.getByRole('alertdialog')).findByText(
    /No hay cupo disponible/,
  )
  expect(
    screen.queryByText('Cambio confirmado por el servidor.'),
  ).not.toBeInTheDocument()
})
it('suspender se envía como comando protegido sólo después de confirmar', async () => {
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: 'Administrar' }),
  )
  await userEvent.click(
    await screen.findByRole('button', { name: 'Suspender' }),
  )
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', {
      name: 'Suspender',
    }),
  )
  await waitFor(() =>
    expect(api.mutateTeam).toHaveBeenCalledWith(
      t,
      { kind: 'status', target: m, status: 'SUSPENDED', expectedVersion: 0 },
      expect.any(String),
      expect.any(AbortSignal),
    ),
  )
})
it('reactivar sólo se ofrece a suspendidos con identidad activa', async () => {
  vi.mocked(api.teamMembers).mockResolvedValue({
    ...page,
    items: [{ ...row, status: 'SUSPENDED', identityStatus: 'DISABLED' }],
  })
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: 'Administrar' }),
  )
  expect(
    await screen.findByRole('button', { name: 'Reactivar' }),
  ).toBeDisabled()
})
it('owner protegido y revocado no presentan controles de edición', async () => {
  vi.mocked(api.teamMembers).mockResolvedValue({
    ...page,
    items: [
      { ...row, roles: ['FIRM_OWNER_ADMIN'], canManage: false },
      { ...row, membershipId: t, status: 'REVOKED' },
    ],
    total: 2,
  })
  view()
  await screen.findByText('Propietario protegido')
  expect(screen.getByText('Acceso revocado')).toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: 'Cambiar rol' }),
  ).not.toBeInTheDocument()
})
it('invitaciones pendientes permiten revocar sin recuperar tokens', async () => {
  vi.mocked(api.teamInvitations).mockResolvedValue({
    items: [
      {
        invitationId: m,
        displayName: null,
        email: 'destino@example.invalid',
        role: 'ACCOUNTANT',
        status: 'PENDING',
        version: 0,
        createdAt: row.joinedAt,
        expiresAt: row.joinedAt,
      },
    ],
    page: 0,
    size: 20,
    total: 1,
  })
  view()
  await screen.findByText('demo@example.invalid')
  await userEvent.click(screen.getByRole('button', { name: 'Invitaciones' }))
  await userEvent.click(
    await screen.findByRole('button', { name: 'Revocar invitación' }),
  )
  await userEvent.click(
    within(screen.getByRole('alertdialog')).getByRole('button', {
      name: 'Revocar invitación',
    }),
  )
  await waitFor(() =>
    expect(api.mutateTeam).toHaveBeenCalledWith(
      t,
      { kind: 'revoke', target: m, expectedVersion: 0 },
      expect.any(String),
      expect.any(AbortSignal),
    ),
  )
})
it('cambiar de despacho descarta filas y respuestas tardías de otra cartera', async () => {
  const v = view()
  await screen.findByText('demo@example.invalid')
  vi.mocked(api.teamMembers).mockReturnValue(new Promise(() => {}))
  v.switchTenant()
  await screen.findByText('Consultando equipo autorizado')
  expect(screen.queryByText('demo@example.invalid')).not.toBeInTheDocument()
})
it('sesión expirada solicita limpieza de la identidad', async () => {
  vi.mocked(api.teamMembers).mockRejectedValue(
    new ApiClientError('AUTHENTICATION_REQUIRED', 401),
  )
  const v = view()
  await waitFor(() => expect(v.unavailable).toHaveBeenCalledWith(401))
  expect(screen.queryByText('demo@example.invalid')).not.toBeInTheDocument()
})
