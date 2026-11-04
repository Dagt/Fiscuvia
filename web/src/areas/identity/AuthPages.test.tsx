import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import { AppProviders } from '../../app/AppProviders'
import * as api from '../../shared/api/session-client'
import { ApiClientError } from '../../shared/api/client'

vi.mock('../../shared/api/session-client', () => ({
  authStatus: vi.fn(),
  getCsrf: vi.fn(),
  currentIdentity: vi.fn(),
  authorizeTenant: vi.fn(),
  logout: vi.fn(),
  bffBase: () => '/api/v1',
}))
const id = '00000000-0000-4000-8000-000000000001'
beforeEach(() => {
  vi.mocked(api.authStatus).mockResolvedValue(true)
  vi.mocked(api.getCsrf).mockResolvedValue({
    headerName: 'X-CSRF-TOKEN',
    token: 'a'.repeat(96),
  })
})
function view(path: string) {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </AppProviders>,
  )
}
describe('acceso real sin datos fiscales simulados', () => {
  it('ofrece login POST con token CSRF y sin solicitar contraseña local', async () => {
    view('/login')
    const button = await screen.findByRole('button', {
      name: 'Continuar con acceso seguro',
    })
    expect(button.closest('form')).toHaveAttribute('method', 'post')
    expect(button.closest('form')).toHaveAttribute(
      'action',
      '/api/v1/auth/login',
    )
    expect(screen.queryByLabelText(/contraseña/i)).not.toBeInTheDocument()
  })
  it('bloquea login cuando no existe emisor disponible', async () => {
    vi.mocked(api.authStatus).mockResolvedValue(false)
    view('/login')
    expect(await screen.findByText('Acceso local sin configurar')).toBeVisible()
    expect(
      screen.queryByRole('button', { name: 'Continuar con acceso seguro' }),
    ).not.toBeInTheDocument()
  })
  it('explica la expiración sin reflejar detalles arbitrarios de la URL', async () => {
    view('/login?estado=expirada&detail=private-token')
    expect(await screen.findByText(/Tu sesión terminó/)).toBeVisible()
    expect(screen.queryByText('private-token')).not.toBeInTheDocument()
  })
  it('presenta un error de autenticación recuperable', async () => {
    view('/login?error=autenticacion')
    expect(
      await screen.findByRole('heading', {
        name: 'No pudimos verificar tu acceso',
      }),
    ).toBeVisible()
    expect(
      await screen.findByRole('button', {
        name: 'Continuar con acceso seguro',
      }),
    ).toBeEnabled()
  })
  it('muestra identidad verificada sin inventar membresías', async () => {
    vi.mocked(api.currentIdentity).mockResolvedValue({
      userId: id,
      memberships: [],
      privilegedMfaVerified: false,
    })
    view('/app')
    expect(
      await screen.findByText('Aún no tienes despachos asignados'),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible()
  })
  it('no establece despacho hasta recibir autorización del servidor', async () => {
    vi.mocked(api.currentIdentity).mockResolvedValue({
      userId: id,
      memberships: [{ tenantId: id, membershipId: id, role: 'ACCOUNTANT' }],
      privilegedMfaVerified: false,
    })
    vi.mocked(api.authorizeTenant).mockRejectedValue(
      new ApiClientError('ACCESS_DENIED', 403),
    )
    view('/app')
    await userEvent.click(
      await screen.findByRole('button', { name: /Nombre comercial pendiente/ }),
    )
    expect(
      await screen.findByText('Necesitas permisos para continuar'),
    ).toBeVisible()
    expect(screen.queryByText('Despacho autorizado')).not.toBeInTheDocument()
  })
  it('redirige la sesión expirada al acceso seguro', async () => {
    vi.mocked(api.currentIdentity).mockRejectedValue(
      new ApiClientError('AUTHENTICATION_REQUIRED', 401),
    )
    view('/app')
    expect(await screen.findByText(/Tu sesión terminó/)).toBeVisible()
  })
})
