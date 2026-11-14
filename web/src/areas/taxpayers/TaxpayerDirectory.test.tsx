import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, expect, it, vi } from 'vitest'
import { TaxpayerDirectory } from './TaxpayerDirectory'
import { NotificationProvider } from '../../shared/presentation/NotificationProvider'
import * as api from '../../shared/api/taxpayer-client'
import { ApiClientError } from '../../shared/api/client'
vi.mock('../../shared/api/taxpayer-client', () => ({
  listTaxpayers: vi.fn(),
  taxpayerPresentation: vi.fn(),
  taxpayerDetail: vi.fn(),
  taxpayerCommand: vi.fn(),
}))
const t = '00000000-0000-4000-8000-000000000001',
  other = '00000000-0000-4000-8000-000000000002'
const row: api.Taxpayer = {
  id: t,
  rfc: 'DEM000101001',
  personType: 'LEGAL_ENTITY',
  legalName: 'Cliente DEMO',
  tradeName: null,
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-11-13T00:00:00Z',
  updatedAt: '2026-11-13T00:00:00Z',
  regimes: [],
  rfcValidation: 'RFC_LOCAL_STRUCTURE_V1',
  officialVerification: 'NOT_PERFORMED',
}
const hints: api.Presentation = {
  canCreate: true,
  createBlock: 'NONE',
  canEdit: true,
  canArchive: true,
  canRestore: false,
  version: 0,
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.listTaxpayers).mockImplementation(async (_t, q) => ({
    items: [row],
    page: q.page,
    size: q.size,
    hasNext: q.page === 0,
  }))
  vi.mocked(api.taxpayerPresentation).mockResolvedValue(hints)
  vi.mocked(api.taxpayerDetail).mockResolvedValue({
    taxpayer: row,
    actions: hints,
  })
})
function view() {
  const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    }),
    onUnavailable = vi.fn()
  const component = (tenant = t) => (
    <QueryClientProvider client={client}>
      <NotificationProvider>
        <TaxpayerDirectory
          userId={t}
          tenantId={tenant}
          membershipId={tenant}
          onUnavailable={onUnavailable}
        />
      </NotificationProvider>
    </QueryClientProvider>
  )
  return { ...render(component()), client, onUnavailable, component }
}
it('muestra sólo la página recibida, procedencia manual y hasNext sin total', async () => {
  view()
  expect(await screen.findByText('Cliente DEMO')).toBeVisible()
  expect(screen.getByText('Página 1 · 1 en esta página')).toBeVisible()
  expect(screen.queryByText(/de 500/)).not.toBeInTheDocument()
  expect(
    screen.getByRole('button', { name: 'Nuevo contribuyente' }),
  ).toBeVisible()
  expect(screen.queryByLabelText('Responsable')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  expect(await screen.findByText('Página 2 · 1 en esta página')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled()
})
it('debounce envía búsqueda, preserva filtros al paginar y reinicia página al cambiarlos', async () => {
  view()
  await screen.findByText('Cliente DEMO')
  await userEvent.type(screen.getByLabelText('Buscar por RFC o nombre'), 'DEM')
  expect(api.listTaxpayers).toHaveBeenCalledTimes(1)
  await waitFor(() =>
    expect(api.listTaxpayers).toHaveBeenLastCalledWith(
      t,
      expect.objectContaining({ search: 'DEM', page: 0 }),
      expect.any(AbortSignal),
    ),
  )
  await screen.findByText('Cliente DEMO')
  await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }))
  await waitFor(() =>
    expect(api.listTaxpayers).toHaveBeenLastCalledWith(
      t,
      expect.objectContaining({ search: 'DEM', page: 1 }),
      expect.any(AbortSignal),
    ),
  )
  await userEvent.selectOptions(screen.getByLabelText('Estado'), 'ARCHIVED')
  await waitFor(() =>
    expect(api.listTaxpayers).toHaveBeenLastCalledWith(
      t,
      expect.objectContaining({ search: 'DEM', page: 0, status: 'ARCHIVED' }),
      expect.any(AbortSignal),
    ),
  )
})
it('aplica régimen y sort tipados en servidor, sin consultas por código incompleto', async () => {
  view()
  await screen.findByText('Cliente DEMO')
  await userEvent.type(
    screen.getByLabelText('Código de régimen (manual)'),
    '00',
  )
  await screen.findByText('Introduce los tres dígitos del régimen capturado.')
  expect(api.listTaxpayers).toHaveBeenCalledTimes(1)
  await userEvent.type(screen.getByLabelText('Código de régimen (manual)'), '0')
  await waitFor(() =>
    expect(api.listTaxpayers).toHaveBeenLastCalledWith(
      t,
      expect.objectContaining({ regime: '000' }),
      expect.any(AbortSignal),
    ),
  )
  await userEvent.selectOptions(
    screen.getByLabelText('Ordenar por'),
    'createdAt,desc',
  )
  await waitFor(() =>
    expect(api.listTaxpayers).toHaveBeenLastCalledWith(
      t,
      expect.objectContaining({ sort: 'createdAt,desc', page: 0 }),
      expect.any(AbortSignal),
    ),
  )
})
it.each(['MFA_REQUIRED', 'QUOTA_UNAVAILABLE', 'NOT_AUTHORIZED'] as const)(
  'oculta alta cuando backend indica %s',
  async (createBlock) => {
    vi.mocked(api.taxpayerPresentation).mockResolvedValue({
      ...hints,
      canCreate: false,
      createBlock,
    })
    view()
    await screen.findByText('Cliente DEMO')
    expect(
      screen.queryByRole('button', { name: 'Nuevo contribuyente' }),
    ).not.toBeInTheDocument()
  },
)
it('un 403 retira datos y muestra permiso, nunca una cartera vacía', async () => {
  vi.mocked(api.listTaxpayers).mockRejectedValue(
    new ApiClientError('ACCESS_DENIED', 403),
  )
  view()
  expect(
    await screen.findByText('Sin acceso al directorio de contribuyentes'),
  ).toBeVisible()
  expect(screen.queryByText('Cliente DEMO')).not.toBeInTheDocument()
  expect(screen.queryByText('Tu cartera empieza aquí')).not.toBeInTheDocument()
})

it('no conserva un alta habilitada si falla la actualización de su señal', async () => {
  view()
  await screen.findByRole('button', { name: 'Nuevo contribuyente' })
  vi.mocked(api.taxpayerPresentation).mockRejectedValue(
    new ApiClientError('INTERNAL_ERROR', 500),
  )
  await userEvent.click(
    screen.getByRole('button', { name: 'Actualizar directorio' }),
  )
  await screen.findByText('No pudimos verificar las acciones disponibles.')
  expect(
    screen.queryByRole('button', { name: 'Nuevo contribuyente' }),
  ).not.toBeInTheDocument()
  expect(await screen.findByText('Cliente DEMO')).toBeVisible()
})
it('un 401 retira el contexto mediante el flujo de sesión existente', async () => {
  vi.mocked(api.listTaxpayers).mockRejectedValue(
    new ApiClientError('AUTHENTICATION_REQUIRED', 401),
  )
  const v = view()
  await waitFor(() => expect(v.onUnavailable).toHaveBeenCalledWith(401))
})
it('cambio de despacho cancela y descarta una respuesta tardía y cierra paneles', async () => {
  let resolve!: (v: api.TaxpayerPage) => void
  vi.mocked(api.listTaxpayers)
    .mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r
        }),
    )
    .mockResolvedValue({ items: [], page: 0, size: 20, hasNext: false })
  const v = view()
  await waitFor(() => expect(api.listTaxpayers).toHaveBeenCalledTimes(1))
  const signal = vi.mocked(api.listTaxpayers).mock.calls[0]![2]!
  v.rerender(v.component(other))
  expect(await screen.findByText('Tu cartera empieza aquí')).toBeVisible()
  expect(signal.aborted).toBe(true)
  await act(async () =>
    resolve({ items: [row], page: 0, size: 20, hasNext: false }),
  )
  expect(screen.queryByText('Cliente DEMO')).not.toBeInTheDocument()
})
it('detalle sólo ofrece acciones de la señal exacta y conserva RFC inmutable al editar', async () => {
  view()
  await screen.findByText('Cliente DEMO')
  await userEvent.click(
    screen.getByRole('button', { name: 'Ver información de Cliente DEMO' }),
  )
  await userEvent.click(
    await screen.findByRole('button', { name: 'Editar información' }),
  )
  expect(screen.getByLabelText(/^RFC/)).toHaveAttribute('readonly')
  expect(screen.getByLabelText('Tipo de persona')).toBeDisabled()
})
