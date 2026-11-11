import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { PlanUsage } from './PlanUsage'
import * as api from '../../shared/api/entitlement-client'
import { ApiClientError } from '../../shared/api/client'
vi.mock('../../shared/api/entitlement-client', () => ({ tenantUsage: vi.fn() }))
const rows: [api.EntitlementUsage, api.EntitlementUsage] = [
  {
    resource: 'USERS',
    limit: 2,
    version: 1,
    validFrom: '2026-11-01T00:00:00Z',
    validUntil: null,
    measured: 1,
    reserved: 0,
    available: 1,
    overLimit: false,
    integration: 'MEASURED',
  },
  {
    resource: 'STORAGE_BYTES',
    limit: null,
    version: null,
    validFrom: null,
    validUntil: null,
    measured: null,
    reserved: null,
    available: null,
    overLimit: null,
    integration: 'NOT_INTEGRATED',
  },
]
function view(tenant = 'a') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <PlanUsage userId="u" tenantId={tenant} onUnavailable={vi.fn()} />
    </QueryClientProvider>,
  )
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(api.tenantUsage).mockResolvedValue(rows)
})
it('muestra uso autorizado y distingue recursos sin integración', async () => {
  view()
  expect(await screen.findByText('Usuarios')).toBeVisible()
  expect(screen.getByText('Sin consumo medido')).toBeVisible()
  expect(screen.queryByText('Pagar')).not.toBeInTheDocument()
})
it('retira números durante carga', () => {
  vi.mocked(api.tenantUsage).mockReturnValue(new Promise(() => {}))
  view()
  expect(screen.getByText('Consultando límites y uso')).toBeVisible()
  expect(screen.queryByText('Usuarios')).not.toBeInTheDocument()
})
it('ofrece reintento ante error recuperable', async () => {
  vi.mocked(api.tenantUsage).mockRejectedValueOnce(new Error('failure'))
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: 'Reintentar' }),
  )
  expect(await screen.findByText('Usuarios')).toBeVisible()
})
it('muestra denegación sin datos', async () => {
  vi.mocked(api.tenantUsage).mockRejectedValue(
    new ApiClientError('ACCESS_DENIED', 403),
  )
  view()
  expect(
    await screen.findByText('Sin permiso para consultar el plan'),
  ).toBeVisible()
  expect(screen.queryByText('Usuarios')).not.toBeInTheDocument()
})
it('explica límite alcanzado', async () => {
  vi.mocked(api.tenantUsage).mockResolvedValue([
    { ...rows[0], measured: 2, available: 0 },
  ])
  view()
  expect(await screen.findByText('Límite alcanzado')).toBeVisible()
})
it('conserva sobrecupo sin sugerir borrado', async () => {
  vi.mocked(api.tenantUsage).mockResolvedValue([
    { ...rows[0], measured: 3, available: 0, overLimit: true },
  ])
  view()
  expect(await screen.findByText('Sobrecupo')).toBeVisible()
  expect(screen.getByText(/Se conservan los datos existentes/)).toBeVisible()
})
