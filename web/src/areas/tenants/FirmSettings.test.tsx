import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { NotificationProvider } from '../../shared/presentation/NotificationProvider'
import * as api from '../../shared/api/firm-client'
import { ApiClientError } from '../../shared/api/client'
import { FirmSettings } from './FirmSettings'
vi.mock('../../shared/api/firm-client', async (original) => ({
  ...(await original<typeof api>()),
  firmSettings: vi.fn(),
  updateFirmSettings: vi.fn(),
}))
const id = '00000000-0000-4000-8000-000000000001'
const profile: api.FirmSettings = {
  commercialName: 'Despacho de prueba',
  contactEmail: null,
  phone: null,
  timeZone: 'America/Mexico_City',
  locale: 'es-MX',
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-11-03T00:00:00Z',
  updatedAt: '2026-11-03T00:00:00Z',
  canManage: true,
  logoReference: null,
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.firmSettings).mockResolvedValue(profile)
  vi.mocked(api.updateFirmSettings).mockResolvedValue(1)
})
function view() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const unavailable = vi.fn()
  return {
    ...render(
      <QueryClientProvider client={client}>
        <NotificationProvider>
          <FirmSettings userId={id} tenantId={id} onUnavailable={unavailable} />
        </NotificationProvider>
      </QueryClientProvider>,
    ),
    client,
    unavailable,
  }
}
describe('configuración del despacho con componentes compartidos', () => {
  it('muestra datos reales y fallback sin inventar un logo', async () => {
    view()
    expect(await screen.findByLabelText('Nombre comercial *')).toHaveValue(
      'Despacho de prueba',
    )
    expect(screen.getByText(/Sin logo/)).toBeVisible()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })
  it('permanece en lectura cuando el backend no permite editar', async () => {
    vi.mocked(api.firmSettings).mockResolvedValue({
      ...profile,
      canManage: false,
    })
    view()
    expect(await screen.findByLabelText('Nombre comercial *')).toHaveAttribute(
      'readonly',
    )
    expect(
      screen.queryByRole('button', { name: 'Guardar cambios' }),
    ).not.toBeInTheDocument()
  })
  it('cancela el borrador sin enviar una mutación', async () => {
    view()
    const name = await screen.findByLabelText('Nombre comercial *')
    await userEvent.clear(name)
    await userEvent.type(name, 'Borrador')
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }))
    expect(name).toHaveValue('Despacho de prueba')
    expect(api.updateFirmSettings).not.toHaveBeenCalled()
  })
  it('confirma antes de guardar y sólo anuncia éxito tras respuesta real', async () => {
    view()
    const name = await screen.findByLabelText('Nombre comercial *')
    await userEvent.clear(name)
    await userEvent.type(name, 'Nombre actualizado')
    await userEvent.click(
      screen.getByRole('button', { name: 'Guardar cambios' }),
    )
    expect(api.updateFirmSettings).not.toHaveBeenCalled()
    await userEvent.click(
      screen.getByRole('button', { name: 'Confirmar cambios' }),
    )
    await waitFor(() => expect(api.updateFirmSettings).toHaveBeenCalledOnce())
    expect(
      await screen.findByText('Datos del despacho guardados'),
    ).toBeVisible()
  })
  it('no anuncia éxito ante conflicto y conserva un mensaje seguro', async () => {
    vi.mocked(api.updateFirmSettings).mockRejectedValue(
      new ApiClientError('STALE_VERSION', 409),
    )
    view()
    await userEvent.type(
      await screen.findByLabelText('Nombre comercial *'),
      ' nuevo',
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Guardar cambios' }),
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Confirmar cambios' }),
    )
    expect(await screen.findByText(/La información cambió/)).toBeVisible()
    expect(
      screen.queryByText('Datos del despacho guardados'),
    ).not.toBeInTheDocument()
  })
  it('retira los datos ante un 403 y notifica al contexto de sesión', async () => {
    vi.mocked(api.firmSettings).mockRejectedValue(
      new ApiClientError('ACCESS_DENIED', 403),
    )
    const { unavailable } = view()
    expect(
      await screen.findByText('Necesitas permisos para continuar'),
    ).toBeVisible()
    expect(unavailable).toHaveBeenCalledWith(403)
    expect(
      screen.queryByDisplayValue('Despacho de prueba'),
    ).not.toBeInTheDocument()
  })
  it('ofrece reintento real ante un error recuperable', async () => {
    vi.mocked(api.firmSettings).mockRejectedValueOnce(
      new ApiClientError('NETWORK_ERROR'),
    )
    view()
    await userEvent.click(
      await screen.findByRole('button', { name: 'Reintentar' }),
    )
    expect(await screen.findByLabelText('Nombre comercial *')).toHaveValue(
      'Despacho de prueba',
    )
  })
  it('no muestra configuración anterior mientras carga otro contexto', async () => {
    vi.mocked(api.firmSettings).mockImplementation(() => new Promise(() => {}))
    view()
    expect(await screen.findByText('Consultando tu despacho')).toBeVisible()
    expect(
      screen.queryByDisplayValue('Despacho de prueba'),
    ).not.toBeInTheDocument()
  })
})
