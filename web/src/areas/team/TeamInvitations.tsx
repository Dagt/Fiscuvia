import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Mail, ShieldCheck } from 'lucide-react'
import { ApiClientError } from '../../shared/api/client'
import {
  createEmailInvitation,
  inviteOptions,
  revokeCreatedInvitation,
  type Invitation,
  type InviteRequest,
} from '../../shared/api/team-client'
import { Button } from '../../shared/ui/button'
import { Field } from '../../shared/ui/Field'
import { ConfirmationDialog } from '../../shared/ui/Panels'
import {
  ErrorState,
  LoadingState,
  PermissionState,
} from '../../shared/ui/States'
import { StatusPill } from '../../shared/ui/StatusPill'
import { useNotification } from '../../shared/presentation/notification-context'
import './team.css'

type Props = {
  userId: string
  tenantId: string
  onUnavailable: (status: number) => void
  onUsage: () => void
}
export function TeamInvitations(props: Props) {
  const q = useQuery({
    queryKey: ['team-invitation-options', props.userId, props.tenantId],
    retry: false,
    staleTime: 0,
    queryFn: async ({ signal }) => {
      try {
        return await inviteOptions(props.tenantId, signal)
      } catch (e) {
        if (e instanceof ApiClientError && e.status === 401)
          props.onUnavailable(401)
        throw e
      }
    },
  })
  if (q.isFetching || q.isPending)
    return <LoadingState title="Verificando acceso al equipo" />
  if (q.isError)
    return q.error instanceof ApiClientError && q.error.status === 403 ? (
      <PermissionState
        title="Sin permiso para invitar"
        description="Sólo la persona propietaria con verificación de doble factor reciente puede crear invitaciones para este despacho."
      />
    ) : (
      <ErrorState onRetry={() => void q.refetch()} />
    )
  if (!q.data.emailInvitationsAvailable)
    return (
      <PermissionState
        title="Invitaciones por correo no habilitadas"
        description="Este flujo requiere un proveedor de identidad y una política de verificación autorizados. No se enviará ningún correo."
      />
    )
  return <InvitationForm key={`${props.userId}:${props.tenantId}`} {...props} />
}
function InvitationForm({ tenantId, onUnavailable, onUsage }: Props) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<InviteRequest['role']>('ACCOUNTANT')
  const [result, setResult] = useState<Invitation | null>(null)
  const [pending, setPending] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const form = useRef<HTMLFormElement>(null)
  const alive = useRef(true)
  const transport = useRef<AbortController | null>(null)
  const command = useRef<{ fingerprint: string; key: string } | null>(null)
  const notify = useNotification()
  useEffect(() => {
    alive.current = true
    function hideSecret() {
      if (document.visibilityState === 'hidden')
        setResult((v) => (v ? { ...v, token: null } : null))
    }
    document.addEventListener('visibilitychange', hideSecret)
    return () => {
      alive.current = false
      transport.current?.abort()
      document.removeEventListener('visibilitychange', hideSecret)
    }
  }, [])
  async function submit() {
    if (pending || !form.current?.reportValidity()) return
    setPending(true)
    setFailure(null)
    const data = { email, role }
    const fingerprint = JSON.stringify(data)
    if (command.current?.fingerprint !== fingerprint)
      command.current = { fingerprint, key: crypto.randomUUID() }
    transport.current = new AbortController()
    try {
      const saved = await createEmailInvitation(
        tenantId,
        data,
        command.current.key,
        transport.current.signal,
      )
      if (!alive.current) return
      setResult(saved)
      notify(
        saved.replayed
          ? 'La invitación ya estaba creada. El token no vuelve a mostrarse.'
          : 'Invitación creada. Entrega manual pendiente.',
      )
    } catch (e) {
      if (!alive.current) return
      if (e instanceof ApiClientError && e.status === 401) onUnavailable(401)
      if (e instanceof ApiClientError && e.status === 403) {
        setResult(null)
        onUnavailable(403)
      }
      setFailure(
        e instanceof ApiClientError && e.status === 403
          ? 'No fue posible crear la invitación. Revisa tu acceso, el doble factor y que la persona destinataria haya completado un acceso verificado.'
          : e instanceof ApiClientError && e.status === 422
            ? 'No fue posible generar la invitación. Verifica que el destinatario haya completado un acceso con correo verificado y que cumpla los requisitos.'
            : e instanceof ApiClientError && e.status === 409
              ? 'La solicitud entra en conflicto con una invitación existente. No se creó una invitación duplicada.'
              : 'No pudimos confirmar la creación. Puedes reintentar la misma solicitud de forma segura.',
      )
      throw new Error('La invitación no pudo confirmarse', { cause: e })
    } finally {
      if (alive.current) setPending(false)
    }
  }
  async function revoke() {
    if (!result) return
    setResult({ ...result, token: null })
    try {
      await revokeCreatedInvitation(tenantId, result.invitationId)
      if (!alive.current) return
      setResult(null)
      command.current = null
      notify('Solicitud de revocación procesada.')
    } catch (e) {
      if (!alive.current) return
      if (
        e instanceof ApiClientError &&
        (e.status === 401 || e.status === 403)
      ) {
        setResult(null)
        onUnavailable(e.status)
      }
      throw new Error('No pudimos confirmar la revocación', { cause: e })
    }
  }
  async function copyToken() {
    if (!result?.token) return
    try {
      await navigator.clipboard.writeText(result.token)
      if (!alive.current) return
      setResult((value) => (value ? { ...value, token: null } : null))
      notify('Token copiado para entrega manual.')
    } catch (error) {
      if (alive.current)
        setFailure(
          error instanceof Error
            ? 'No pudimos copiar el token. Puedes seleccionarlo y guardarlo manualmente.'
            : 'No pudimos copiar el token.',
        )
    }
  }
  return (
    <section className="team-area" aria-label="Invitaciones del equipo">
      <div className="firm-brand">
        <span className="firm-monogram">
          <Mail size={26} aria-hidden="true" />
        </span>
        <div>
          <h2>Invita a tu equipo</h2>
          <p>Acceso verificado para colaborar en tu despacho.</p>
        </div>
        <StatusPill tone="info">Entrega manual</StatusPill>
      </div>
      <div className="team-notice">
        <ShieldCheck aria-hidden="true" />
        <p>
          Disponible sólo con el proveedor local de desarrollo y pruebas. La
          persona destinataria debe haber iniciado sesión y tener su correo
          verificado. No se envían correos desde esta pantalla.
        </p>
      </div>
      <form
        className="firm-card team-form"
        ref={form}
        onSubmit={(e) => e.preventDefault()}
        aria-label="Invitar colaborador"
      >
        <div>
          <h2>Nueva invitación</h2>
          <p>
            El correo ayuda a localizar la identidad. El acceso sólo se concede
            a la identidad OIDC exacta.
          </p>
        </div>
        <Field
          label="Correo del colaborador"
          type="email"
          autoComplete="off"
          required
          maxLength={254}
          value={email}
          readOnly={pending || !!result}
          onChange={(e) => setEmail(e.target.value)}
          description="Usa el correo que la persona ya verificó en el proveedor local."
        />
        <label className="team-role">
          <span>Rol del colaborador</span>
          <select
            className="input"
            value={role}
            disabled={pending || !!result}
            onChange={(e) => setRole(e.target.value as InviteRequest['role'])}
          >
            <option value="FIRM_SUPERVISOR">Supervisor</option>
            <option value="ACCOUNTANT">Contador</option>
            <option value="ASSISTANT">Auxiliar</option>
          </select>
        </label>
        {failure && (
          <p role="alert" className="field-error">
            {failure}
          </p>
        )}
        <div className="firm-form-actions">
          <Button variant="secondary" type="button" onClick={onUsage}>
            Consultar cupo
          </Button>
          <ConfirmationDialog
            title="Crear invitación"
            description={`Se generará una invitación para ${email || 'el correo indicado'}. El token se mostrará una sola vez y requiere entrega manual.`}
            confirmLabel="Crear invitación"
            onConfirm={submit}
            trigger={
              <Button
                type="submit"
                pending={pending}
                disabled={!!result}
                onClick={(e) => {
                  if (!form.current?.reportValidity()) e.preventDefault()
                }}
              >
                Invitar colaborador
              </Button>
            }
          />
        </div>
        <p className="field-description">
          Las invitaciones pendientes no reservan plazas. El cupo se valida al
          aceptar la invitación.
        </p>
      </form>
      {result && (
        <section className="team-result" aria-label="Invitación creada">
          <h2>
            {result.replayed
              ? 'Invitación creada anteriormente'
              : 'Invitación creada'}
          </h2>
          <StatusPill tone={result.replayed ? 'neutral' : 'warning'}>
            {result.replayed
              ? 'Registro de solicitud'
              : 'Entrega manual pendiente'}
          </StatusPill>
          <>
            <p>
              No se ha enviado ningún correo. Vencimiento registrado:{' '}
              {new Intl.DateTimeFormat('es-MX', {
                dateStyle: 'medium',
                timeStyle: 'short',
              }).format(new Date(result.expiresAt))}
              .
            </p>
            {result.token ? (
              <>
                <p>
                  Guarda este token por un canal autorizado. No podrás
                  consultarlo después; se oculta al salir de esta vista o
                  cambiar de ventana.
                </p>
                <code
                  className="team-secret"
                  data-private="invitation-token"
                  aria-label="Token de invitación"
                >
                  {result.token}
                </code>
                <Button variant="secondary" onClick={() => void copyToken()}>
                  Copiar token
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => setResult({ ...result, token: null })}
                >
                  Ya guardé el token
                </Button>
              </>
            ) : (
              <p>
                El token ya no está disponible. Si no lo conservaste, revoca
                esta invitación y genera una nueva.
              </p>
            )}
            {result.replayed && (
              <p>
                Este registro no confirma el estado actual de la invitación. No
                se vuelve a consultar ni mostrar su token.
              </p>
            )}
            <ConfirmationDialog
              title="Revocar invitación"
              description="Se revocará si todavía está pendiente. Esta acción no elimina una membresía que ya se haya aceptado."
              confirmLabel="Revocar invitación"
              destructive
              onConfirm={revoke}
              trigger={<Button variant="danger">Revocar invitación</Button>}
            />
          </>
        </section>
      )}
      <aside className="team-notice">
        <p>
          Consulta colaboradores e invitaciones desde las vistas del equipo. Los
          tokens no pueden recuperarse desde el historial.
        </p>
      </aside>
    </section>
  )
}
