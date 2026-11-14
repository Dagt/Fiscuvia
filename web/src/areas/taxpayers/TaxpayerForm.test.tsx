import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { TaxpayerForm } from './TaxpayerForm'
import { taxpayerCommand } from '../../shared/api/taxpayer-client'
import { ApiClientError } from '../../shared/api/client'
vi.mock('../../shared/api/taxpayer-client', () => ({
  taxpayerCommand: vi.fn(),
}))
const t = '00000000-0000-4000-8000-000000000001'
beforeEach(() => vi.resetAllMocks())
async function form() {
  const onSaved = vi.fn(),
    onUnavailable = vi.fn()
  const v = render(
    <TaxpayerForm tenant={t} onSaved={onSaved} onUnavailable={onUnavailable} />,
  )
  await userEvent.type(screen.getByLabelText(/^RFC/), 'DEM000101001')
  await userEvent.type(
    screen.getByLabelText(/^Nombre o razón social/),
    'Cliente DEMO',
  )
  return { ...v, onSaved, onUnavailable }
}
it('impide alta inválida, asocia errores y enfoca el primer campo', async () => {
  render(<TaxpayerForm tenant={t} onSaved={vi.fn()} onUnavailable={vi.fn()} />)
  await userEvent.click(
    screen.getByRole('button', { name: 'Registrar contribuyente' }),
  )
  expect(await screen.findByText(/Revisa longitud/)).toBeVisible()
  await waitFor(() => expect(screen.getByLabelText(/^RFC/)).toHaveFocus())
  expect(taxpayerCommand).not.toHaveBeenCalled()
})
it('envía campos cerrados con NULL y referencias manuales, confirma sólo receipt real', async () => {
  const v = await form()
  vi.mocked(taxpayerCommand).mockResolvedValue({
    targetId: t,
    version: 0,
    replayed: false,
  })
  await userEvent.click(
    screen.getByRole('button', { name: 'Agregar referencia' }),
  )
  await userEvent.type(screen.getByLabelText('Código de régimen 1'), '000')
  await userEvent.click(
    screen.getByRole('button', { name: 'Registrar contribuyente' }),
  )
  await waitFor(() =>
    expect(v.onSaved).toHaveBeenCalledWith({
      targetId: t,
      version: 0,
      replayed: false,
    }),
  )
  expect(taxpayerCommand).toHaveBeenCalledWith(
    t,
    {
      action: 'create',
      body: {
        rfc: 'DEM000101001',
        personType: 'LEGAL_ENTITY',
        legalName: 'Cliente DEMO',
        tradeName: null,
        regimes: [{ code: '000', validFrom: null, validTo: null }],
      },
    },
    expect.any(String),
    expect.any(AbortSignal),
  )
})
it('respuesta perdida bloquea cambios y recupera el mismo comando e idempotency-key', async () => {
  const v = await form()
  vi.mocked(taxpayerCommand)
    .mockRejectedValueOnce(new ApiClientError('NETWORK_ERROR'))
    .mockResolvedValueOnce({ targetId: t, version: 0, replayed: true })
  await userEvent.click(
    screen.getByRole('button', { name: 'Registrar contribuyente' }),
  )
  await screen.findByRole('button', { name: 'Recuperar resultado' })
  expect(v.onSaved).not.toHaveBeenCalled()
  expect(screen.getByLabelText(/^RFC/)).toBeDisabled()
  await userEvent.click(
    screen.getByRole('button', { name: 'Recuperar resultado' }),
  )
  await waitFor(() => expect(v.onSaved).toHaveBeenCalled())
  const [a, b] = vi.mocked(taxpayerCommand).mock.calls
  expect(a!.slice(0, 3)).toEqual(b!.slice(0, 3))
})
it.each([409, 422, 403] as const)(
  'fallo conocido %s nunca presenta éxito y permite corrección autorizada',
  async (status) => {
    const v = await form()
    vi.mocked(taxpayerCommand).mockRejectedValue(
      new ApiClientError(
        status === 409
          ? 'CONFLICT'
          : status === 422
            ? 'VALIDATION_FAILED'
            : 'ACCESS_DENIED',
        status,
      ),
    )
    await userEvent.click(
      screen.getByRole('button', { name: 'Registrar contribuyente' }),
    )
    await screen.findByRole('alert')
    expect(v.onSaved).not.toHaveBeenCalled()
    if (status === 403) expect(v.onUnavailable).toHaveBeenCalledWith(403)
  },
)

it('bloquea doble envío y descarta una confirmación posterior a abandonar el contexto', async () => {
  const v = await form()
  let resolve!: (r: {
    targetId: string
    version: number
    replayed: boolean
  }) => void
  vi.mocked(taxpayerCommand).mockImplementation(
    () =>
      new Promise((r) => {
        resolve = r
      }),
  )
  const button = screen.getByRole('button', { name: 'Registrar contribuyente' })
  await userEvent.dblClick(button)
  expect(taxpayerCommand).toHaveBeenCalledTimes(1)
  const signal = vi.mocked(taxpayerCommand).mock.calls[0]![3]!
  v.unmount()
  expect(signal.aborted).toBe(true)
  resolve({ targetId: t, version: 0, replayed: false })
  await Promise.resolve()
  expect(v.onSaved).not.toHaveBeenCalled()
})
