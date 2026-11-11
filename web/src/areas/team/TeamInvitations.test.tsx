import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { NotificationProvider } from '../../shared/presentation/NotificationProvider'
import { ApiClientError } from '../../shared/api/client'
import * as api from '../../shared/api/team-client'
import { TeamInvitations } from './TeamInvitations'
vi.mock('../../shared/api/team-client', () => ({
  inviteOptions: vi.fn(),
  createEmailInvitation: vi.fn(),
  revokeCreatedInvitation: vi.fn(),
}))
const t = '00000000-0000-4000-8000-000000000001'
const token = 's'.repeat(43)
const result: api.Invitation = {
  invitationId: t,
  token,
  expiresAt: '2026-11-12T20:00:00Z',
  delivery: 'MANUAL',
  replayed: false,
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.inviteOptions).mockResolvedValue({
    emailInvitationsAvailable: true,
    delivery: 'MANUAL',
  })
  vi.mocked(api.createEmailInvitation).mockResolvedValue(result)
  vi.mocked(api.revokeCreatedInvitation).mockResolvedValue(undefined)
})
function view() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const unavailable = vi.fn()
  const usage = vi.fn()
  return {
    ...render(
      <QueryClientProvider client={client}>
        <NotificationProvider>
          <TeamInvitations
            userId={t}
            tenantId={t}
            onUnavailable={unavailable}
            onUsage={usage}
          />
        </NotificationProvider>
      </QueryClientProvider>,
    ),
    unavailable,
    usage,
  }
}
async function send() {
  const u = userEvent.setup()
  await u.type(
    await screen.findByLabelText(/Correo del colaborador/),
    'destino@example.invalid',
  )
  await u.click(screen.getByRole('button', { name: 'Invitar colaborador' }))
  const dialog = await screen.findByRole('alertdialog')
  await u.click(
    within(dialog).getByRole('button', { name: 'Crear invitación' }),
  )
  return u
}
it('retira el formulario durante carga', () => {
  vi.mocked(api.inviteOptions).mockReturnValue(new Promise(() => {}))
  view()
  expect(screen.getByText('Verificando acceso al equipo')).toBeInTheDocument()
  expect(
    screen.queryByLabelText(/Correo del colaborador/),
  ).not.toBeInTheDocument()
})
it('muestra el alcance real, roles limitados y consulta de cupo', async () => {
  const v = view()
  await screen.findByLabelText(/Correo del colaborador/)
  expect(
    screen.queryByRole('option', { name: /propietario/i }),
  ).not.toBeInTheDocument()
  expect(
    screen.getByText(/No se envían correos desde esta pantalla/),
  ).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Consultar cupo' }))
  expect(v.usage).toHaveBeenCalledOnce()
})
it('denegación no expone controles de invitación', async () => {
  vi.mocked(api.inviteOptions).mockRejectedValue(
    new ApiClientError('ACCESS_DENIED', 403),
  )
  view()
  await screen.findByText('Sin permiso para invitar')
  expect(
    screen.queryByLabelText(/Correo del colaborador/),
  ).not.toBeInTheDocument()
})
it('política deshabilitada no simula una invitación disponible', async () => {
  vi.mocked(api.inviteOptions).mockResolvedValue({
    emailInvitationsAvailable: false,
    delivery: 'MANUAL',
  })
  view()
  await screen.findByText('Invitaciones por correo no habilitadas')
  expect(api.createEmailInvitation).not.toHaveBeenCalled()
})
it('error recuperable reintenta la consulta real del cliente', async () => {
  vi.mocked(api.inviteOptions).mockRejectedValueOnce(
    new ApiClientError('NETWORK_ERROR'),
  )
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: /Reintentar/ }),
  )
  await screen.findByLabelText(/Correo del colaborador/)
  expect(api.inviteOptions).toHaveBeenCalledTimes(2)
})
it('muestra token sólo tras confirmar y lo retira de forma irreversible de la vista', async () => {
  view()
  const u = await send()
  await screen.findByText(token)
  expect(api.createEmailInvitation).toHaveBeenCalledTimes(1)
  await u.click(screen.getByRole('button', { name: 'Ya guardé el token' }))
  expect(screen.queryByText(token)).not.toBeInTheDocument()
  expect(screen.getByText(/El token ya no está disponible/)).toBeInTheDocument()
})
it('copiar requiere acción explícita y retira el secreto tras confirmar el portapapeles', async () => {
  view()
  const user = await send()
  await screen.findByText(token)
  const copy = vi
    .spyOn(navigator.clipboard, 'writeText')
    .mockResolvedValue(undefined)
  await user.click(screen.getByRole('button', { name: 'Copiar token' }))
  await waitFor(() => expect(copy).toHaveBeenCalledWith(token))
  expect(screen.queryByText(token)).not.toBeInTheDocument()
})
it('un replay no aparenta recuperar el token ni verificar el estado actual', async () => {
  vi.mocked(api.createEmailInvitation).mockResolvedValue({
    ...result,
    replayed: true,
    token: null,
  })
  view()
  await send()
  await screen.findByText('Invitación creada anteriormente')
  expect(screen.queryByText(token)).not.toBeInTheDocument()
  expect(screen.getByText(/no confirma el estado actual/)).toBeInTheDocument()
})
it('no publica un token ni notificación tardía después de abandonar el contexto', async () => {
  let finish!: (v: api.Invitation) => void
  vi.mocked(api.createEmailInvitation).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  const v = view()
  await send()
  await waitFor(() => expect(api.createEmailInvitation).toHaveBeenCalledOnce())
  v.unmount()
  finish(result)
  await Promise.resolve()
  expect(screen.queryByText(token)).not.toBeInTheDocument()
  expect(v.unavailable).not.toHaveBeenCalled()
})
it('sesión expirada solicita limpieza del contexto y no muestra datos', async () => {
  vi.mocked(api.inviteOptions).mockRejectedValue(
    new ApiClientError('AUTHENTICATION_REQUIRED', 401),
  )
  const v = view()
  await waitFor(() => expect(v.unavailable).toHaveBeenCalledWith(401))
  expect(
    screen.queryByLabelText(/Correo del colaborador/),
  ).not.toBeInTheDocument()
})
