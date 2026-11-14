import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { App } from '../../app/App'
import { AppProviders } from '../../app/AppProviders'
import * as session from '../../shared/api/session-client'
import * as api from '../../shared/api/taxpayer-client'
vi.mock('../../shared/api/session-client', () => ({
  currentIdentity: vi.fn(),
  authorizeTenant: vi.fn(),
  authStatus: vi.fn(),
  getCsrf: vi.fn(),
  logout: vi.fn(),
  bffBase: () => '/api/v1',
}))
vi.mock('../../shared/api/taxpayer-client', () => ({
  taxpayerDetail: vi.fn(),
  taxpayerPresentation: vi.fn(),
  listTaxpayers: vi.fn(),
  taxpayerCommand: vi.fn(),
}))
const t = '00000000-0000-4000-8000-000000000001',
  b = '00000000-0000-4000-8000-000000000002',
  id = '00000000-0000-4000-8000-000000000003'
const members: session.Membership[] = [
  {
    tenantId: t,
    membershipId: t,
    role: 'FIRM_OWNER_ADMIN',
    commercialName: 'Despacho A DEMO',
    tenantStatus: 'ACTIVE',
  },
  {
    tenantId: b,
    membershipId: b,
    role: 'FIRM_OWNER_ADMIN',
    commercialName: 'Despacho B DEMO',
    tenantStatus: 'ACTIVE',
  },
]
const row: api.Taxpayer = {
  id,
  rfc: 'DEM000101001',
  personType: 'LEGAL_ENTITY',
  legalName: 'Expediente A DEMO',
  tradeName: null,
  status: 'ACTIVE',
  version: 0,
  createdAt: '2026-11-14T00:00:00Z',
  updatedAt: '2026-11-14T00:00:00Z',
  regimes: [],
  rfcValidation: 'RFC_LOCAL_STRUCTURE_V1',
  officialVerification: 'NOT_PERFORMED',
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(session.currentIdentity).mockResolvedValue({
    userId: t,
    memberships: members,
    privilegedMfaVerified: true,
  })
  vi.mocked(session.authorizeTenant).mockImplementation(async (selected) =>
    members.find((m) => m.tenantId === selected)!,
  )
  vi.mocked(api.taxpayerDetail).mockResolvedValue({
    taxpayer: row,
    actions: {
      canCreate: false,
      createBlock: 'NOT_AUTHORIZED',
      canEdit: true,
      canArchive: true,
      canRestore: false,
      version: 0,
    },
  })
  vi.mocked(api.taxpayerPresentation).mockResolvedValue({
    canCreate: false,
    createBlock: 'NOT_AUTHORIZED',
    canEdit: false,
    canArchive: false,
    canRestore: false,
    version: null,
  })
  vi.mocked(api.listTaxpayers).mockResolvedValue({
    items: [],
    page: 0,
    size: 20,
    hasNext: false,
  })
})
function Location() {
  return <output aria-label="Ruta actual">{useLocation().pathname}</output>
}
function view() {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[`/app/taxpayers/${id}`]}>
        <App />
        <Location />
      </MemoryRouter>
    </AppProviders>,
  )
}
it('entrada directa exige selección autorizada y conserva navegación del mismo AppShell', async () => {
  view()
  await screen.findByRole('heading', { name: 'Elige tu despacho' })
  expect(api.taxpayerDetail).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: /Despacho A DEMO/ }))
  expect(
    await screen.findByRole('heading', { level: 1, name: row.legalName }),
  ).toBeVisible()
  expect(screen.getByRole('link', { name: 'Contribuyentes' })).toHaveAttribute(
    'aria-current',
    'page',
  )
  expect(screen.getAllByRole('main')).toHaveLength(1)
  await userEvent.click(
    screen.getByRole('link', { name: 'Volver a contribuyentes' }),
  )
  expect(
    await screen.findByRole('heading', { name: 'Contribuyentes', level: 1 }),
  ).toBeVisible()
  expect(
    screen.queryByRole('heading', { name: 'Elige tu despacho' }),
  ).not.toBeInTheDocument()
})
it('cambio de despacho retira la ficha y su ruta antes de abrir otra cartera', async () => {
  view()
  await userEvent.click(
    await screen.findByRole('button', { name: /Despacho A DEMO/ }),
  )
  await screen.findByRole('heading', { level: 1, name: row.legalName })
  await userEvent.click(
    screen.getByRole('button', { name: 'Contexto actual: Despacho A DEMO' }),
  )
  await waitFor(() =>
    expect(screen.getByLabelText('Ruta actual')).toHaveTextContent(
      /^\/app\/taxpayers$/,
    ),
  )
  expect(screen.queryByText(row.rfc)).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: /Despacho B DEMO/ }))
  expect(await screen.findByText('Tu cartera empieza aquí')).toBeVisible()
  expect(api.taxpayerDetail).not.toHaveBeenCalledWith(b, id, expect.anything())
})
