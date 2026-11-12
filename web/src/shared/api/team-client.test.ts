import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createEmailInvitation,
  inviteOptions,
  revokeCreatedInvitation,
} from './team-client'
import { sessionRequest } from './session-client'
vi.mock('./session-client', () => ({ sessionRequest: vi.fn() }))
const t = '00000000-0000-4000-8000-000000000001'
const response = {
  invitationId: t,
  token: 'a'.repeat(43),
  expiresAt: '2026-11-12T20:00:00Z',
  delivery: 'MANUAL',
  replayed: false,
}
beforeEach(() => vi.clearAllMocks())
describe('contratos de invitación local', () => {
  it('rechaza UUID inválido antes de enviar cookies', async () => {
    await expect(inviteOptions('../otro')).rejects.toThrow()
    expect(sessionRequest).not.toHaveBeenCalled()
  })
  it('acepta sólo opciones cerradas de entrega manual', async () => {
    vi.mocked(sessionRequest).mockResolvedValue({
      emailInvitationsAvailable: true,
      delivery: 'MANUAL',
    })
    expect((await inviteOptions(t)).emailInvitationsAvailable).toBe(true)
    vi.mocked(sessionRequest).mockResolvedValue({
      emailInvitationsAvailable: true,
      delivery: 'EMAIL',
    })
    await expect(inviteOptions(t)).rejects.toThrow()
  })
  it('envía request tipado y clave al BFF same-origin existente', async () => {
    vi.mocked(sessionRequest).mockResolvedValue(response)
    expect(
      (
        await createEmailInvitation(
          t,
          { email: 'destino@example.invalid', role: 'ACCOUNTANT' },
          t,
        )
      ).token,
    ).toHaveLength(43)
    expect(sessionRequest).toHaveBeenCalledWith(
      `/tenants/${t}/team/invitations`,
      { email: 'destino@example.invalid', role: 'ACCOUNTANT' },
      { signal: undefined, idempotencyKey: t },
    )
  })
  it('rechaza secretos en replay, campos extra y primera respuesta sin token', async () => {
    for (const value of [
      { ...response, replayed: true },
      { ...response, token: null },
      { ...response, email: 'privado@example.invalid' },
    ]) {
      vi.mocked(sessionRequest).mockResolvedValue(value)
      await expect(
        createEmailInvitation(
          t,
          { email: 'destino@example.invalid', role: 'ASSISTANT' },
          t,
        ),
      ).rejects.toThrow()
    }
    vi.mocked(sessionRequest).mockResolvedValue({
      ...response,
      replayed: true,
      token: null,
    })
    expect(
      (
        await createEmailInvitation(
          t,
          { email: 'destino@example.invalid', role: 'ASSISTANT' },
          t,
        )
      ).token,
    ).toBeNull()
  })
  it('revoca por el flujo existente sin introducir otra autoridad', async () => {
    vi.mocked(sessionRequest).mockResolvedValue(undefined)
    await revokeCreatedInvitation(t, t)
    expect(sessionRequest).toHaveBeenCalledWith('/auth/invitations/revoke', {
      tenantId: t,
      invitationId: t,
    })
    await expect(revokeCreatedInvitation(t, 'invalid')).rejects.toThrow()
  })
})
