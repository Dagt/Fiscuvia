import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, expect, it, vi } from 'vitest'
import { TaxpayerProfile } from './TaxpayerProfile'
import { NotificationProvider } from '../../shared/presentation/NotificationProvider'
import * as api from '../../shared/api/taxpayer-client'
import { ApiClientError } from '../../shared/api/client'
vi.mock('../../shared/api/taxpayer-client', () => ({
  taxpayerDetail: vi.fn(),
  taxpayerCommand: vi.fn(),
}))
const t = '00000000-0000-4000-8000-000000000001',
  b = '00000000-0000-4000-8000-000000000002'
const taxpayer: api.Taxpayer = {
  id: t,
  rfc: 'DEM000101001',
  personType: 'LEGAL_ENTITY',
  legalName: 'Contribuyente de demostración DEMO',
  tradeName: 'Nombre comercial DEMO',
  status: 'ACTIVE',
  version: 2,
  createdAt: '2026-11-14T00:00:00Z',
  updatedAt: '2026-11-14T01:00:00Z',
  regimes: [
    {
      code: '000',
      validFrom: null,
      validTo: null,
      source: 'MANUAL',
      reviewStatus: 'PENDING_REVIEW',
    },
  ],
  rfcValidation: 'RFC_LOCAL_STRUCTURE_V1',
  officialVerification: 'NOT_PERFORMED',
}
const actions: api.Presentation = {
  canCreate: false,
  createBlock: 'NOT_AUTHORIZED',
  canEdit: true,
  canArchive: true,
  canRestore: false,
  version: 2,
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.taxpayerDetail).mockResolvedValue({ taxpayer, actions })
})
function view() {
  const client = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    }),
    onUnavailable = vi.fn()
  const component = (tenant = t, id = t) => (
    <QueryClientProvider client={client}>
      <NotificationProvider>
        <MemoryRouter>
          <TaxpayerProfile
            userId={t}
            tenantId={tenant}
            membershipId={tenant}
            id={id}
            onUnavailable={onUnavailable}
          />
        </MemoryRouter>
      </NotificationProvider>
    </QueryClientProvider>
  )
  return { ...render(component()), component, onUnavailable }
}
it('presenta encabezado y datos reales, procedencia manual y regreso al directorio', async () => {
  view()
  expect(
    await screen.findByRole('heading', { level: 1, name: taxpayer.legalName }),
  ).toBeVisible()
  expect(
    screen.getByRole('link', { name: 'Volver a contribuyentes' }),
  ).toHaveAttribute('href', '/app/taxpayers')
  expect(screen.getByText('Código 000')).toBeVisible()
  expect(screen.getByText('Vigencia desconocida')).toBeVisible()
  expect(
    screen.getByText('Información registrada, sin verificación SAT'),
  ).toBeVisible()
})
it('navega secciones por teclado y no simula permisos o datos de módulos pendientes', async () => {
  view()
  const summary = await screen.findByRole('tab', { name: 'Resumen' })
  summary.focus()
  await userEvent.keyboard('{ArrowRight}')
  expect(
    screen.getByRole('tab', { name: 'Información y contactos' }),
  ).toHaveFocus()
  expect(summary).toHaveAttribute('aria-selected', 'true')
  await userEvent.keyboard('{Enter}')
  expect(
    screen.getByRole('tab', { name: 'Información y contactos' }),
  ).toHaveAttribute('aria-selected', 'true')
  expect(
    screen.getByText(
      /La consulta y gestión de contactos aún no están habilitadas/,
    ),
  ).toBeVisible()
  for (const name of [
    /Responsables y accesos/,
    /Certificados y alertas/,
    /Actividad e historial/,
  ])
    expect(screen.getByRole('tab', { name })).toBeDisabled()
  expect(
    screen.queryByText('Sin contactos registrados'),
  ).not.toBeInTheDocument()
  expect(
    screen.queryByRole('tab', { name: 'Obligaciones' }),
  ).not.toBeInTheDocument()
})
it('utiliza el formulario C01 y conserva el borrador ante conflicto de versión', async () => {
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: 'Editar información' }),
  )
  const dialog = screen.getByRole('dialog')
  expect(within(dialog).getByLabelText(/^RFC/)).toHaveAttribute('readonly')
  vi.mocked(api.taxpayerCommand).mockRejectedValue(
    new ApiClientError('STALE_VERSION', 409),
  )
  await userEvent.clear(within(dialog).getByLabelText(/^Nombre comercial/))
  await userEvent.type(
    within(dialog).getByLabelText(/^Nombre comercial/),
    'Borrador DEMO',
  )
  await userEvent.click(
    within(dialog).getByRole('button', { name: 'Guardar cambios' }),
  )
  expect(await within(dialog).findByText(/La información cambió/)).toBeVisible()
  expect(within(dialog).getByLabelText(/^Nombre comercial/)).toHaveValue(
    'Borrador DEMO',
  )
  expect(
    within(dialog).getByRole('button', {
      name: 'Actualizar datos para revisar',
    }),
  ).toBeVisible()
})
it('no presenta acciones por rol supuesto cuando el backend no las concede', async () => {
  vi.mocked(api.taxpayerDetail).mockResolvedValue({
    taxpayer,
    actions: { ...actions, canEdit: false, canArchive: false },
  })
  view()
  await screen.findByRole('heading', { level: 1 })
  expect(
    screen.queryByRole('button', { name: 'Editar información' }),
  ).not.toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: 'Archivar contribuyente' }),
  ).not.toBeInTheDocument()
})
it('presenta error recuperable y retira el contenido del recurso', async () => {
  vi.mocked(api.taxpayerDetail)
    .mockRejectedValueOnce(new ApiClientError('INTERNAL_ERROR', 500))
    .mockResolvedValue({ taxpayer, actions })
  view()
  await screen.findByRole('button', { name: 'Reintentar' })
  expect(screen.queryByText(taxpayer.rfc)).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
  expect(
    await screen.findByRole('heading', { level: 1, name: taxpayer.legalName }),
  ).toBeVisible()
})
it.each([401, 403, 404])(
  'retira el expediente ante HTTP %s sin inventar un vacío',
  async (status) => {
    vi.mocked(api.taxpayerDetail).mockRejectedValue(
      new ApiClientError(
        status === 401
          ? 'AUTHENTICATION_REQUIRED'
          : status === 403
            ? 'ACCESS_DENIED'
            : 'NOT_FOUND',
        status,
      ),
    )
    const v = view()
    expect(await screen.findByText('Expediente no disponible')).toBeVisible()
    expect(screen.queryByText(taxpayer.legalName)).not.toBeInTheDocument()
    if (status !== 404) expect(v.onUnavailable).toHaveBeenCalledWith(status)
  },
)
it('un UUID inválido no provoca una consulta protegida', async () => {
  const v = view()
  v.rerender(v.component(t, 'foreign/path'))
  await screen.findByText('Expediente no disponible')
  expect(api.taxpayerDetail).not.toHaveBeenCalledWith(
    t,
    'foreign/path',
    expect.anything(),
  )
})
it('cambiar despacho aborta y descarta la respuesta tardía del expediente anterior', async () => {
  let resolve!: (v: {
    taxpayer: api.Taxpayer
    actions: api.Presentation
  }) => void
  vi.mocked(api.taxpayerDetail)
    .mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r
        }),
    )
    .mockRejectedValue(new ApiClientError('NOT_FOUND', 404))
  const v = view()
  await waitFor(() => expect(api.taxpayerDetail).toHaveBeenCalledTimes(1))
  const signal = vi.mocked(api.taxpayerDetail).mock.calls[0]![2]!
  v.rerender(v.component(b))
  await screen.findByText('Expediente no disponible')
  expect(signal.aborted).toBe(true)
  await act(async () => resolve({ taxpayer, actions }))
  expect(screen.queryByText(taxpayer.legalName)).not.toBeInTheDocument()
})
